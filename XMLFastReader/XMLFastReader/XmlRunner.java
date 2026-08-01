package XMLFastReader;

/**
 * XmlRunner — Per-Thread Parser State + Work Queue (PLAN 4.2 / Class 2)
 * 
 * ARCHITECTURE ROLE:
 *   Mutable state object owned by a single parser thread. Each XmlWorker owns one XmlRunner.
 *   Not thread-safe — designed for single-threaded access within its worker thread.
 * 
 * RESPONSIBILITIES:
 *   1. SPSC Work Queue — Ring buffer (size 512) of region descriptors {begin, end, filename}.
 *      Producer pushes, consumer pulls. Lock-free via volatile head/tail pointers.
 *   2. State Machine — 7-bit flags (IN_TAG, IN_DQUOTE, IN_SQUOTE, CLOSE_TAG, SPECIAL,
 *      SPECIAL_COMMENT, TARGET) drive the TOC decision table.
 *   3. Trie Navigation — currNode tracks position in shared XmlPlanner trie.
 *   4. Linear Buffer — Per-thread expandable byte[] for accumulating tag names,
 *      attribute names/values, and inner text (zero-copy offset/length).
 *   5. Parse Loop — Main hot loop: read byte → TOC[flags][byte].call(this).
 *   6. Event Emission — Fires TAG, ATTR, INNER, END events via reusable XmlEvent.
 *   7. Skip Logic — Name-keyed depth counter for unregistered subtrees.
 *   8. Encoding Detection — Parses XML declaration (<?xml encoding="..."?>) on PI end.
 * 
 * KEY DESIGN DECISIONS:
 *   - Region descriptors instead of InputStreams in queue (no FD/inflater per slot)
 *   - Bit flags instead of stack (O(1) state transitions, no allocation)
 *   - Linear buffer per thread (grows to file size, reused across files)
 *   - Reusable XmlEvent instance (zero allocation in hot path)
 *   - Parent pointer in XmlNode for END event traversal
 * 
 * STATE RESET:
 *   resetState() called between files — clears flags, skip state, currNode, encoding,
 *   attribute/text accumulators. Linear buffer retained for reuse.
 */
public final class XmlRunner {

    private static final int SIZE = 512;          // 2^9 (PLAN 4.8.5)
    private static final int MASK = SIZE - 1;

    /** region descriptor: ขอบเขต byte บน shared buffer + ชื่อไฟล์ (PLAN 4.2) */
    public final int[] begin = new int[SIZE];
    public final int[] end = new int[SIZE];
    public final String[] fileName = new String[SIZE];

    // SPSC: producer เขียน wP เดียว, consumer เขียน rP เดียว
    // volatile กัน visibility / reordering (PLAN 4.5)
    private volatile int wP;
    private volatile int rP;

    /** State machine enum (PLAN 6.2) — 14 states replacing 7-bit flags */
    private enum State {
        // Outside of any tag
        OUTSIDE_TAG_BEFORE_ROOT(0),      // 0: out side of tag & before root tag
        OUTSIDE_TAG_INSIDE_ROOT(6),      // 6: out side of tag & inside root tag
        
        // Inside tag, outside root element
        IN_TAG_FIRST_BYTE_OUTSIDE_ROOT(1),  // 1: intag but out side of root tag & first byte after <
        IN_TAG_OUTSIDE_ROOT(2),             // 2: intag but out side of root tag
        IN_TAG_HEADER_CHARSET(3),           // 3: intag & header for charset
        IN_TAG_CDATA_OUTSIDE_ROOT(4),       // 4: intag CDATA & out side root tag, ignore this tag just looking for > (actually ]]>)
        IN_TAG_HTML_COMMENT_OUTSIDE_ROOT(5), // 5: intag HTML_Comment & out side root tag, just looking for -->
        IN_TAG_DOCTYPE_OUTSIDE_ROOT(13),    // 13: intag DOCTYPE & out side root tag, just looking for >
        
        // Inside tag, inside root element
        IN_TAG_FIRST_BYTE_INSIDE_ROOT(7),   // 7: intag & inside root tag & first byte after <
        IN_TAG_INSIDE_ROOT(8),              // 8: intag & inside root tag
        IN_TAG_CDATA_INSIDE_ROOT(9),        // 9: intag CDATA inside root tag, ignore this tag just looking for > (actually ]]>)
        IN_TAG_HTML_COMMENT_INSIDE_ROOT(10), // 10: intag HTML_Comment inside root tag, just looking for -->
        IN_TAG_DOCTYPE_INSIDE_ROOT(14),     // 14: intag DOCTYPE inside root tag, just looking for >
        
        // Target tag states
        IN_TARGET_TAG(11),                  // 11: in target tag
        IN_TARGET_INNER(12);                // 12: inner side target tag (innerText & child tags)

        final int id;
        State(int id) { this.id = id; }
        
        static State fromId(int id) {
            for (State s : values()) if (s.id == id) return s;
            return OUTSIDE_TAG_BEFORE_ROOT;
        }
    }

    /** Current parser state */
    private State state = State.OUTSIDE_TAG_BEFORE_ROOT;

    /** ตำแหน่งอ่านบน buffer (linear/source) — ชี้ข้อมูลที่กำลังอ่าน (parse loop); package-private ให้ TOC cell เรียกได้ */
    int pointer;

    /** Reusable XmlEvent instance (per runner) */
    XmlEvent event = new XmlEvent();

    // Lock for queue synchronization (backpressure support)
    private final Object queueLock = new Object();

    // State transition helpers
    void setState(State s) { this.state = s; }
    State getState() { return state; }
    
    boolean isInTag() {
        return state.ordinal() >= State.IN_TAG_FIRST_BYTE_OUTSIDE_ROOT.ordinal() 
            && state.ordinal() <= State.IN_TAG_DOCTYPE_INSIDE_ROOT.ordinal();
    }
    
    boolean isInsideRoot() {
        return state == State.OUTSIDE_TAG_INSIDE_ROOT 
            || state.ordinal() >= State.IN_TAG_FIRST_BYTE_INSIDE_ROOT.ordinal();
    }
    
    boolean isInTargetTag() {
        return state == State.IN_TARGET_TAG || state == State.IN_TARGET_INNER;
    }
    
    boolean isInCData() {
        return state == State.IN_TAG_CDATA_OUTSIDE_ROOT || state == State.IN_TAG_CDATA_INSIDE_ROOT;
    }
    
    boolean isInHtmlComment() {
        return state == State.IN_TAG_HTML_COMMENT_OUTSIDE_ROOT || state == State.IN_TAG_HTML_COMMENT_INSIDE_ROOT;
    }
    
    boolean isInDoctype() {
        return state == State.IN_TAG_DOCTYPE_OUTSIDE_ROOT || state == State.IN_TAG_DOCTYPE_INSIDE_ROOT;
    }
    
    boolean isInHeader() {
        return state == State.IN_TAG_HEADER_CHARSET;
    }
    
    boolean isFirstByteAfterLt() {
        return state == State.IN_TAG_FIRST_BYTE_OUTSIDE_ROOT || state == State.IN_TAG_FIRST_BYTE_INSIDE_ROOT;
    }

    /** ใส่งานเข้าคิว; คืน false ถ้าเต็ม (เว้น 1 ช่องแยก เต็ม/ว่าง) */
    public boolean push(int b, int e, String fn) {
        synchronized (queueLock) {
            while (getRemain() == MASK) {
                try {
                    queueLock.wait(); // Wait for space
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            begin[wP] = b;
            end[wP] = e;
            fileName[wP] = fn;
            wP = (wP + 1) & MASK;
            queueLock.notifyAll(); // Notify waiting consumer
            return true;
        }
    }

    /** ดึงงานถัดไป; คืน slot index หรือ -1 ถ้าว่าง (ข้อมูลใน array ยังไม่ถูกลบ) */
    public int pull() {
        synchronized (queueLock) {
            while (rP == wP) {
                try {
                    queueLock.wait(); // Wait for work
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            int idx = rP;
            rP = (rP + 1) & MASK;
            queueLock.notifyAll(); // Notify waiting producer
            return idx;
        }
    }

    /** จำนวนงานค้างในคิว: (wP - rP) & MASK */
    public int getRemain() {
        return (wP - rP) & MASK;
    }

    /** ข้ามกิ่งที่ไม่ได้ลงทะเบียน (PLAN 6.5) — ต่อเทรด */
    private byte[] skipName = new byte[32];  // reusable; ตัดเก็บแค่ 32 ตัวแรก (ชื่อจริงแทบไม่เกิน)
    private int skipNameLen;                 // ความยาวชื่อแท็กที่เก็บไว้ (capped ที่ skipName.length)
    private int skipDepth;                   // ตัวนับชั้นของชื่อนั้น

    /** เข้ากิ่งที่ไม่ลงทะเบียน: เก็บชื่อ (ตัดเกิน 32 ไม่สน) + ตั้งตัวนับ=1 (PLAN 6.5) */
    void enterSkip(byte[] buf, int off, int len) {
        int n = (len < skipName.length) ? len : skipName.length;
        System.arraycopy(buf, off, skipName, 0, n);
        skipNameLen = n;
        skipDepth = 1;
    }

    /** เทียบชื่อแท็กปิดกับที่เก็บไว้ (capped 32) — ตรงคืน true */
    boolean isSkipClose(byte[] buf, int off, int len) {
        if (len != skipNameLen) return false;
        for (int i = 0; i < len; i++) if (skipName[i] != buf[off + i]) return false;
        return true;
    }

    /** ตำแหน่งบนกราฟ (PLAN 4.1) — node ปัจจุบันที่ไต่อยู่ */
    XmlNode currNode;

    /** encoding จาก XML header (PLAN 3.1 / 6.3) — Charset object ระดับเดียว */
    java.nio.charset.Charset encoding;

    /** linearize buffer ต่อเทรด (PLAN 11.1) — โตได้ + reuse ข้ามไฟล์ */
    byte[] linear = new byte[8192]; // high-water grow; โตเท่าไฟล์ใน memory

    /** Current region begin offset (for linear buffer indexing) */
    int regionBegin;
    int regionEnd;  // region end for bounds checking in callbacks

    /** Attribute parsing state */
    int attrNameStart = -1;   // offset in linear where attr name starts
    int attrNameLen = 0;      // length of attr name
    int attrValueStart = -1;  // offset in linear where attr value starts
    int attrValueLen = 0;     // length of attr value
    boolean inAttrName = false;  // currently reading attribute name
    boolean inAttrValue = false; // currently reading attribute value
    boolean attrValueQuoted = false; // true if value is quoted (always true in valid XML)

    /** Inner text state */
    int textStart = -1;       // offset in linear where text content starts
    int textLen = 0;          // length of accumulated text

    /** Track if current PI is XML declaration (first PI only) */
    private boolean firstPI = true;

    /** ผูกกราฟต้นเดียว (XmlPlanner — shared, read-only ตาม 11.6) */
    XmlPlanner planner;
    void setPlanner(XmlPlanner p) { this.planner = p; }

    /** รีเซ็ต state ต่อไฟล์ (PLAN 8.5.3): flags/skip/currNode/encoding ล้าง; linear เก็บ reuse */
    void resetState() {
        state = State.OUTSIDE_TAG_BEFORE_ROOT;
        skipNameLen = 0;
        skipDepth = 0;
        currNode = (planner != null) ? planner.root : null;
        encoding = null;
        firstPI = true; // Reset PI tracking for new file
        // Reset attribute state
        attrNameStart = -1;
        attrNameLen = 0;
        attrValueStart = -1;
        attrValueLen = 0;
        inAttrName = false;
        inAttrValue = false;
        attrValueQuoted = false;
        // Reset inner text state
        textStart = -1;
        textLen = 0;
    }

    /** ขยาย linearize buffer ตาม high-water (PLAN 11.1) — คืน buffer ที่โตพอ */
    byte[] ensureLinear(int need) {
        int n = linear.length;
        while (n < need) n <<= 1;          // double จนพอ
        if (n != linear.length) linear = new byte[n];
        return linear;
    }

    // ===== Parse Loop (PLAN 6.6 / 8.5) =====

    /** Parse XML region from shared buffer [begin, end) */
    public void parseRegion(int begin, int end, String fileName, XmlEvent event) throws Exception {
        byte[] src = planner.getSource();
        if (src == null) throw new IllegalStateException("Planner source buffer not set");

        // Bind event to this runner's linear buffer
        event.setBuffer(linear);
        event.charset = encoding; // will be updated from XML header

        // Set region begin for linear buffer indexing
        regionBegin = begin;
        regionEnd = end;

        // FILE_BEGIN
        XmlCallback fbh = planner.getFileBeginHandler();
        if (fbh != null) {
            event.event = XmlEvent.Event.FILE_BEGIN;
            event.pushFilename(src, begin, end - begin, encoding); // filename from region
            fbh.handle(event);
        }

        // Parse loop - use pointer as primary position tracker
        pointer = begin;
        while (pointer < end) {
            byte b = src[pointer];
            
            // Special handling for comment/CDATA/PI end sequences
            if (isInCData()) {
                // Inside <![CDATA[ ... ]]> check for ]]>
                if (b == ']' && pointer + 2 < regionEnd && src[pointer + 1] == ']' && src[pointer + 2] == '>') {
                    // Found ]]> end of CDATA
                    pointer += 3; // skip ]]>
                    // Exit CDATA state - go back to appropriate state
                    if (isInsideRoot()) {
                        setState(State.OUTSIDE_TAG_INSIDE_ROOT);
                    } else {
                        setState(State.OUTSIDE_TAG_BEFORE_ROOT);
                    }
                    continue;
                }
            } else if (isInHtmlComment()) {
                // Inside <!-- ... --> check for -->
                if (b == '-' && pointer + 2 < regionEnd && src[pointer + 1] == '-' && src[pointer + 2] == '>') {
                    // Found --> end of comment
                    pointer += 3; // skip -->
                    // Exit comment state - go back to appropriate state
                    if (isInsideRoot()) {
                        setState(State.OUTSIDE_TAG_INSIDE_ROOT);
                    } else {
                        setState(State.OUTSIDE_TAG_BEFORE_ROOT);
                    }
                    continue;
                }
            } else if (isInDoctype()) {
                // Inside <!DOCTYPE ... > check for >
                if (b == '>') {
                    // Found > end of DOCTYPE
                    pointer++; // skip >
                    // Exit DOCTYPE state - go back to appropriate state
                    if (isInsideRoot()) {
                        setState(State.OUTSIDE_TAG_INSIDE_ROOT);
                    } else {
                        setState(State.OUTSIDE_TAG_BEFORE_ROOT);
                    }
                    continue;
                }
            } else if (isInHeader()) {
                // Inside <? ... ?> check for ?>
                if (b == '?' && pointer + 1 < regionEnd && src[pointer + 1] == '>') {
                    // Found ?> end of PI
                    onPiEnd(); // Parse encoding if XML declaration
                    pointer += 2; // skip ?>
                    // Exit header state - go back to appropriate state
                    if (isInsideRoot()) {
                        setState(State.OUTSIDE_TAG_INSIDE_ROOT);
                    } else {
                        setState(State.OUTSIDE_TAG_BEFORE_ROOT);
                    }
                    continue;
                }
            }
            
            // Call TOC callback for current state and byte
            planner.TOC[state.id][b & 0xFF].call(this);
            
            // Check for comment/CDATA/DOCTYPE start after '<!' (handled in onBang via TOC)
            if (isFirstByteAfterLt() && b == '!') {
                // We're at the '!' after '<', check what follows
                int remaining = regionEnd - pointer - 1; // -1 because we're at '!'
                if (remaining >= 2 && src[pointer + 1] == '-' && src[pointer + 2] == '-') {
                    // "<!--" comment (2 dashes after !)
                    if (isInsideRoot()) {
                        setState(State.IN_TAG_HTML_COMMENT_INSIDE_ROOT);
                    } else {
                        setState(State.IN_TAG_HTML_COMMENT_OUTSIDE_ROOT);
                    }
                } else if (remaining >= 7 && src[pointer + 1] == '[' && src[pointer + 2] == 'C' && src[pointer + 3] == 'D' && 
                           src[pointer + 4] == 'A' && src[pointer + 5] == 'T' && src[pointer + 6] == 'A' && src[pointer + 7] == '[') {
                    // "<![CDATA[" - CDATA section
                    if (isInsideRoot()) {
                        setState(State.IN_TAG_CDATA_INSIDE_ROOT);
                    } else {
                        setState(State.IN_TAG_CDATA_OUTSIDE_ROOT);
                    }
                } else if (remaining >= 7 && src[pointer + 1] == 'D' && src[pointer + 2] == 'O' && src[pointer + 3] == 'C' && 
                           src[pointer + 4] == 'T' && src[pointer + 5] == 'Y' && src[pointer + 6] == 'P' && src[pointer + 7] == 'E') {
                    // "<!DOCTYPE" - DOCTYPE declaration, skip until '>'
                    if (isInsideRoot()) {
                        setState(State.IN_TAG_DOCTYPE_INSIDE_ROOT);
                    } else {
                        setState(State.IN_TAG_DOCTYPE_OUTSIDE_ROOT);
                    }
                }
            }
            
            pointer++;
        }

        // FILE_END
        XmlCallback feh = planner.getFileEndHandler();
        if (feh != null) {
            event.event = XmlEvent.Event.FILE_END;
            feh.handle(event);
        }
    }

    // ===== TOC Callback Helpers (called from planner.TOC[state][byte].call()) =====

    /** Handle '<' — enter tag */
    void onLt() {
        if (isInCData() || isInHtmlComment() || isInDoctype() || isInHeader()) return; // inside comment/CDATA/DOCTYPE/PI, ignore
        // End any pending inner text
        endInnerText();
        
        // Transition to first byte after '<' state
        if (isInsideRoot()) {
            setState(State.IN_TAG_FIRST_BYTE_INSIDE_ROOT);
        } else {
            setState(State.IN_TAG_FIRST_BYTE_OUTSIDE_ROOT);
        }
        // Reset tag-specific flags
        inAttrName = false;
        inAttrValue = false;
        attrValueQuoted = false;
    }

    /** Handle '/' — could be close tag or self-close */
    void onSlash() {
        if (isFirstByteAfterLt()) {
            // "</" — close tag
            if (isInsideRoot()) {
                setState(State.IN_TAG_INSIDE_ROOT);
            } else {
                setState(State.IN_TAG_OUTSIDE_ROOT);
            }
            // Mark as close tag - we'll handle this in onGt
            // We need a way to track this - let's use a field
            // For now, we'll check in onGt if the previous char was '/'
        } else if (!inAttrName && !inAttrValue) {
            // "/" inside tag — potential self-close, check next char in onGt()
        }
    }

    /** Handle '>' — exit tag */
    void onGt() {
        if (isInCData() || isInHtmlComment() || isInHeader()) {
            // These are handled in parse loop directly
            return;
        }

        if (isInTag()) {
            // Check self-closing "/>"
            boolean selfClose = false;
            byte[] src = planner.getSource();
            if (pointer >= 2 && src[pointer - 2] == '/' && src[pointer - 1] != '"' && src[pointer - 1] != '\'') {
                selfClose = true;
            }

            // Check if we're in a close tag (previous char was '/' right after '<')
            boolean isCloseTag = false;
            if (pointer >= 2 && src[pointer - 2] == '/' && src[pointer - 1] != '"' && src[pointer - 1] != '\'') {
                // Check if the '/' was right after '<'
                if (pointer >= 3 && src[pointer - 3] == '<') {
                    isCloseTag = true;
                }
            }

            if (isCloseTag) {
                // End tag: </name>
                handleEndTag();
            } else {
                // Start tag: <name ...>
                handleStartTag(selfClose);
            }
            
            // Exit tag state
            if (isInsideRoot()) {
                if (!selfClose && isInTargetTag()) {
                    setState(State.IN_TARGET_INNER);
                    startInnerText();
                } else {
                    setState(State.OUTSIDE_TAG_INSIDE_ROOT);
                }
            } else {
                setState(State.OUTSIDE_TAG_BEFORE_ROOT);
            }
            
            // Reset attribute state
            inAttrName = false;
            inAttrValue = false;
            attrValueQuoted = false;
        }
    }

    /** Handle '?' — PI start/end */
    void onQuestion() {
        byte[] src = planner.getSource();
        if (isFirstByteAfterLt()) {
            // "<?" at start of tag - check if it's "<?xml"
            if (pointer + 3 < regionEnd && src[pointer] == 'x' && src[pointer + 1] == 'm' && src[pointer + 2] == 'l') {
                setState(State.IN_TAG_HEADER_CHARSET);
                firstPI = true;  // This is XML declaration
            } else {
                setState(State.IN_TAG_HEADER_CHARSET);
                firstPI = false; // Other PI (xml-stylesheet, etc.)
            }
        } else if (isInHeader()) {
            // "?>" end of PI - handled in parse loop
        }
    }

    /** Handle '!' — comment/CDATA/DOCTYPE */
    void onBang() {
        if (isFirstByteAfterLt()) {
            // "<!" at start of tag
            // Check for comment "<!--" or CDATA "<![CDATA[" in parse loop
        }
    }

    /** Parse encoding from XML declaration (<?xml ... encoding="..."?>) */
    void onPiEnd() {
        // Linear buffer contains: xml version="1.0" encoding="UTF-8"
        // Find encoding attribute
        int len = pointer - regionBegin;
        if (len <= 5) return; // Too short for "xml"
        
        // Check if starts with "xml"
        if (linear[0] != 'x' || linear[1] != 'm' || linear[2] != 'l') return;
        
        // Scan for encoding="..." or encoding='...'
        for (int i = 3; i < len - 8; i++) {
            // Look for "encoding"
            if (linear[i] == 'e' && linear[i+1] == 'n' && linear[i+2] == 'c' && 
                linear[i+3] == 'o' && linear[i+4] == 'd' && linear[i+5] == 'i' && 
                linear[i+6] == 'n' && linear[i+7] == 'g') {
                
                // Skip whitespace and '='
                int j = i + 8;
                while (j < len && (linear[j] == ' ' || linear[j] == '\t' || linear[j] == '\n' || linear[j] == '\r')) j++;
                if (j < len && linear[j] == '=') j++;
                while (j < len && (linear[j] == ' ' || linear[j] == '\t' || linear[j] == '\n' || linear[j] == '\r')) j++;
                
                // Expect quote
                if (j >= len) return;
                byte quote = linear[j];
                if (quote != '"' && quote != '\'') return;
                j++;
                
                // Read encoding name
                int encStart = j;
                while (j < len && linear[j] != quote) j++;
                if (j >= len) return;
                int encLen = j - encStart;
                if (encLen <= 0) return;
                
                // Convert to string and set charset
                try {
                    String encName = new String(linear, encStart, encLen, java.nio.charset.StandardCharsets.US_ASCII);
                    encoding = java.nio.charset.Charset.forName(encName);
                } catch (Exception e) {
                    // Invalid encoding, keep default (null = UTF-8)
                }
                return;
            }
        }
    }

    /** Handle '"' — double quote */
    void onDQuote() {
        if (isInTag() && !isInCData() && !isInHtmlComment() && !isInHeader()) {
            if (inAttrValue && attrValueQuoted) {
                // End attribute value
                inAttrValue = false;
                attrValueQuoted = false;
                endAttrValue();
            } else if (!inAttrName && !inAttrValue) {
                // Start attribute value
                inAttrValue = true;
                attrValueQuoted = true;
                startAttrValue();
            }
        }
    }

    /** Handle ''' — single quote */
    void onSQuote() {
        if (isInTag() && !isInCData() && !isInHtmlComment() && !isInHeader()) {
            if (inAttrValue && attrValueQuoted) {
                inAttrValue = false;
                attrValueQuoted = false;
                endAttrValue();
            } else if (!inAttrName && !inAttrValue) {
                inAttrValue = true;
                attrValueQuoted = true;
                startAttrValue();
            }
        }
    }

    /** Handle '=' — attribute name/value separator */
    void onEquals() {
        if (isInTag() && !isInCData() && !isInHtmlComment() && !isInHeader() && inAttrName) {
            endAttrName();
        }
    }

    /** Handle whitespace in tag */
    void onSpace() {
        if (isInTag() && !isInCData() && !isInHtmlComment() && !isInHeader()) {
            if (inAttrName) {
                endAttrName();
            }
            // Whitespace separates attributes
        }
    }

    /** Handle regular character (tag name, attribute name, text content) */
    void onChar(byte b) {
        int linearPos = pointer - regionBegin;
        ensureLinear(linearPos + 2);
        linear[linearPos] = b;

        if (isInTag() && !isInCData() && !isInHtmlComment() && !isInHeader()) {
            if (inAttrName) {
                accumulateAttrName(b);
            } else if (inAttrValue) {
                accumulateAttrValue(b);
            }
            // Tag name handled by findTagNameEnd() in handleStartTag/handleEndTag
        } else if (state == State.IN_TARGET_INNER) {
            // Inside target tag - accumulate inner text
            if (textStart >= 0) {
                accumulateInnerText(b);
            }
        } else if (state == State.OUTSIDE_TAG_INSIDE_ROOT) {
            // Outside tag but inside root - could be text between tags
            if (textStart >= 0) {
                accumulateInnerText(b);
            }
        }
    }

    // ===== Tag Handling =====

    /** Process start tag: <name ...> or <name .../> */
    void handleStartTag(boolean selfClose) {
        // Tag name is in linear[0..tagNameLen)
        int tagNameLen = findTagNameEnd();
        if (tagNameLen <= 0) return;

        // Look up in trie
        XmlNode child = currNode.findChild(linear, 0, tagNameLen);
        if (child != null) {
            currNode = child;
            if (child.handler != null) {
                // Enter target tag state
                setState(State.IN_TARGET_TAG);
                fireTagEvent(child);
            }
        } else {
            // Not registered — enter skip mode
            enterSkip(linear, 0, tagNameLen);
            // Stay in appropriate tag state
        }

        if (selfClose) {
            // Self-closing: fire END immediately
            if (isInTargetTag() && currNode.handler != null) {
                fireEndEvent(currNode);
            }
            currNode = currNode.parent != null ? currNode.parent : currNode; // go back up
        }
    }

    /** Process end tag: </name> */
    void handleEndTag() {
        int tagNameLen = findTagNameEnd();
        if (tagNameLen <= 0) return;

        if (skipDepth > 0) {
            // In skip mode — check if this closes the skipped tag
            if (isSkipClose(linear, 0, tagNameLen)) {
                if (--skipDepth == 0) {
                    // Back to registered tree
                    // currNode already points to correct parent
                }
            }
            return;
        }

        // Normal registered tag
        if (isInTargetTag() && currNode.handler != null) {
            fireEndEvent(currNode);
        }
        // Move back up
        if (currNode.nameLen > 0) { // not root
            currNode = currNode.parent != null ? currNode.parent : currNode;
        }
    }

    /** Find end of tag name in linear buffer (stops at space, /, >) */
    int findTagNameEnd() {
        int len = 0;
        while (len < pointer - regionBegin) {
            byte b = linear[len];
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '/' || b == '>') break;
            len++;
        }
        return len;
    }

    /** Fire TAG event for registered node */
    void fireTagEvent(XmlNode node) {
        XmlCallback cb = (XmlCallback) node.handler;
        if (cb == null) return;
        event.reset();
        event.event = XmlEvent.Event.TAG;
        event.token = node.token;
        cb.handle(event);
    }

    /** Fire END event for registered node */
    void fireEndEvent(XmlNode node) {
        XmlCallback cb = (XmlCallback) node.handler;
        if (cb == null) return;
        event.reset();
        event.event = XmlEvent.Event.END;
        event.token = node.token;
        cb.handle(event);
    }

    // ===== Attribute Handling =====

    /** Start reading attribute name (after whitespace in tag) */
    void startAttrName() {
        inAttrName = true;
        attrNameStart = pointer - regionBegin;
        attrNameLen = 0;
    }

    /** Accumulate attribute name character */
    void accumulateAttrName(byte b) {
        if (inAttrName) {
            attrNameLen++;
        }
    }

    /** End attribute name (hit '=') */
    void endAttrName() {
        inAttrName = false;
    }

    /** Start reading attribute value (after '=' and quote) */
    void startAttrValue() {
        inAttrValue = true;
        attrValueQuoted = true;
        attrValueStart = pointer - regionBegin;
        attrValueLen = 0;
    }

    /** Accumulate attribute value character */
    void accumulateAttrValue(byte b) {
        if (inAttrValue) {
            attrValueLen++;
        }
    }

    /** End attribute value (hit closing quote) */
    void endAttrValue() {
        if (inAttrValue && isInTargetTag() && currNode.handler != null) {
            fireAttrEvent();
        }
        inAttrValue = false;
        attrValueQuoted = false;
        attrValueStart = -1;
        attrValueLen = 0;
    }

    /** Fire ATTR event for registered node */
    void fireAttrEvent() {
        XmlCallback cb = (XmlCallback) currNode.handler;
        if (cb == null) return;
        event.reset();
        event.event = XmlEvent.Event.ATTR;
        event.token = currNode.token;
        event.pushAttrName(attrNameStart, attrNameLen);
        event.pushValue(attrValueStart, attrValueLen);
        cb.handle(event);
    }

    // ===== Inner Text Handling =====

    /** Start accumulating inner text (after '>' of start tag, or after '>' of end tag) */
    void startInnerText() {
        textStart = pointer - regionBegin;
        textLen = 0;
    }

    /** Accumulate inner text character */
    void accumulateInnerText(byte b) {
        if (textStart >= 0) {
            textLen++;
        }
    }

    /** End inner text (hit '<' of next tag) */
    void endInnerText() {
        if (textStart >= 0 && textLen > 0 && isInTargetTag() && currNode.handler != null) {
            fireInnerEvent();
        }
        textStart = -1;
        textLen = 0;
    }

    /** Fire INNER event for registered node */
    void fireInnerEvent() {
        XmlCallback cb = (XmlCallback) currNode.handler;
        if (cb == null) return;
        event.reset();
        event.event = XmlEvent.Event.INNER;
        event.token = currNode.token;
        event.pushValue(textStart, textLen);
        cb.handle(event);
    }
}