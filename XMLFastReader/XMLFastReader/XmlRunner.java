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

    /** state machine bit flags (PLAN 6.2) — ต่อเทรด */
    private byte xmlState;

    /** ตำแหน่งอ่านบน buffer (linear/source) — ชี้ข้อมูลที่กำลังอ่าน (parse loop); package-private ให้ TOC cell เรียกได้ */
    int pointer;

    /** Reusable XmlEvent instance (per runner) */
    XmlEvent event = new XmlEvent();

    private static final int BIT_IN_TAG     = 1 << 0;  // 0b00001 อยู่ระหว่าง < … >
    private static final int BIT_IN_DQUOTE  = 1 << 1;  // 0b00010 อยู่ในค่า attr "..."
    private static final int BIT_IN_SQUOTE  = 1 << 2;  // 0b00100 อยู่ในค่า attr '...'
    private static final int BIT_CLOSE_TAG  = 1 << 3;  // 0b01000 tag ปัจจุบันเป็น </…
    private static final int BIT_SPECIAL    = 1 << 4;  // 0b10000 comment/CDATA/PI <!… <?…
    private static final int BIT_SPECIAL_COMMENT = 1 << 5; // 0b100000 SPECIAL ที่เป็น comment 
    private static final int BIT_TARGET = 1 << 6; // 0b1000000 tag ปัจจุบันเป็นแท็กที่ user ลงทะเบียนไว้  
    // `<!--` (skip ถึง `-->` ไม่ใช่ `>`)
    // bit 7 ว่าง (สงวน)

    void setInTag(boolean v)    { if (v) xmlState |= BIT_IN_TAG;    else xmlState &= ~BIT_IN_TAG; }
    void setInDQuote(boolean v)  { if (v) xmlState |= BIT_IN_DQUOTE;  else xmlState &= ~BIT_IN_DQUOTE; }
    void setInSQuote(boolean v)  { if (v) xmlState |= BIT_IN_SQUOTE;  else xmlState &= ~BIT_IN_SQUOTE; }
    void setCloseTag(boolean v)  { if (v) xmlState |= BIT_CLOSE_TAG;  else xmlState &= ~BIT_CLOSE_TAG; }
    void setSpecial(boolean v)   { if (v) xmlState |= BIT_SPECIAL;   else xmlState &= ~BIT_SPECIAL; }
    void setSpecialComment(boolean v) { if (v) xmlState |= BIT_SPECIAL_COMMENT; else xmlState &= ~BIT_SPECIAL_COMMENT; }
    void setTarget(boolean v)      { if (v) xmlState |= BIT_TARGET;      else xmlState &= ~BIT_TARGET; }

    /** ใส่งานเข้าคิว; คืน false ถ้าเต็ม (เว้น 1 ช่องแยก เต็ม/ว่าง) */
    public boolean push(int b, int e, String fn) {
        if (getRemain() == MASK) return false;
        begin[wP] = b;
        end[wP] = e;
        fileName[wP] = fn;
        wP = (wP + 1) & MASK;
        return true;
    }

    /** ดึงงานถัดไป; คืน slot index หรือ -1 ถ้าว่าง (ข้อมูลใน array ยังไม่ถูกลบ) */
    public int pull() {
        if (rP == wP) return -1;
        int idx = rP;
        rP = (rP + 1) & MASK;
        return idx;
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

    /** ผูกกราฟต้นเดียว (XmlPlanner — shared, read-only ตาม 11.6) */
    XmlPlanner planner;
    void setPlanner(XmlPlanner p) { this.planner = p; }

    /** รีเซ็ต state ต่อไฟล์ (PLAN 8.5.3): flags/skip/currNode/encoding ล้าง; linear เก็บ reuse */
    void resetState() {
        xmlState = 0;
        skipNameLen = 0;
        skipDepth = 0;
        currNode = (planner != null) ? planner.root : null;
        encoding = null;
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

        // FILE_BEGIN
        XmlCallback fbh = planner.getFileBeginHandler();
        if (fbh != null) {
            event.event = XmlEvent.Event.FILE_BEGIN;
            event.pushFilename(src, begin, end - begin, encoding); // filename from region
            fbh.handle(event);
        }

        // Parse loop
        int p = begin;
        while (p < end) {
            byte b = src[p];
            int flags = xmlState & 0x7F; // 7 bits
            
            // Special handling for comment/CDATA/PI end sequences
            if ((xmlState & BIT_SPECIAL) != 0) {
                if ((xmlState & BIT_SPECIAL_COMMENT) != 0) {
                    // Inside <!-- ... --> check for -->
                    if (b == '-' && p + 2 < end && src[p + 1] == '-' && src[p + 2] == '>') {
                        // Found --> end of comment
                        p += 3; // skip -->
                        pointer = p;
                        setSpecial(false);
                        setSpecialComment(false);
                        setInTag(false);
                        continue;
                    }
                } else if (p + 2 < end && src[p] == ']' && src[p + 1] == ']' && src[p + 2] == '>') {
                    // Inside <![CDATA[ ... ]]> check for ]]>
                    p += 3; // skip ]]>
                    pointer = p;
                    setSpecial(false);
                    setInTag(false);
                    continue;
                } else if (b == '?' && p + 1 < end && src[p + 1] == '>') {
                    // Inside <? ... ?> check for ?>
                    onPiEnd(); // Parse encoding if XML declaration
                    p += 2; // skip ?>
                    pointer = p;
                    setSpecial(false);
                    setInTag(false);
                    continue;
                }
            }
            
            planner.TOC[flags][b & 0xFF].call(this);
            p++;
            pointer = p;
        }

        // FILE_END
        XmlCallback feh = planner.getFileEndHandler();
        if (feh != null) {
            event.event = XmlEvent.Event.FILE_END;
            feh.handle(event);
        }
    }

    // ===== TOC Callback Helpers (called from planner.TOC[flags][byte].call()) =====

    /** Handle '<' — enter tag */
    void onLt() {
        if ((xmlState & BIT_SPECIAL) != 0) return; // inside comment/CDATA/PI, ignore
        // End any pending inner text
        endInnerText();
        setInTag(true);
        setCloseTag(false);
        setSpecial(false);
        setSpecialComment(false);
        setTarget(false);
    }

    /** Handle '/' — could be close tag or self-close */
    void onSlash() {
        if ((xmlState & BIT_IN_TAG) != 0) {
            if ((xmlState & BIT_CLOSE_TAG) == 0 && pointer > 0 && planner.getSource()[pointer - 1] == '<') {
                // "</" — close tag
                setCloseTag(true);
            } else if ((xmlState & BIT_IN_DQUOTE) == 0 && (xmlState & BIT_IN_SQUOTE) == 0) {
                // "/" inside tag — potential self-close, check next char in onGt()
            }
        }
    }

    /** Handle '>' — exit tag */
    void onGt() {
        if ((xmlState & BIT_SPECIAL) != 0) {
            // End of PI or DOCTYPE
            if ((xmlState & BIT_SPECIAL_COMMENT) != 0) {
                // Comment ends with "-->", handled separately
            } else {
                setSpecial(false);
            }
            return;
        }

        if ((xmlState & BIT_IN_TAG) != 0) {
            // Check self-closing "/>"
            boolean selfClose = false;
            byte[] src = planner.getSource();
            if (pointer >= 2 && src[pointer - 2] == '/' && src[pointer - 1] != '"' && src[pointer - 1] != '\'') {
                selfClose = true;
            }

            if ((xmlState & BIT_CLOSE_TAG) != 0) {
                // End tag: </name>
                handleEndTag();
            } else {
                // Start tag: <name ...>
                handleStartTag(selfClose);
            }
            setInTag(false);
            setInDQuote(false);
            setInSQuote(false);
            setCloseTag(false);
            // Start inner text after start tag (unless self-closing)
            if (!selfClose && (xmlState & BIT_TARGET) != 0) {
                startInnerText();
            }
        }
    }

    /** Handle '?' — PI start/end */
    void onQuestion() {
        if ((xmlState & BIT_IN_TAG) != 0 && pointer == 1) {
            // "<?" at start of tag
            setSpecial(true);
        } else if ((xmlState & BIT_SPECIAL) != 0 && (xmlState & BIT_IN_TAG) != 0) {
            // "?>" end of PI
            onPiEnd(); // Parse encoding if XML declaration
            setSpecial(false);
            setInTag(false);
        }
    }

    /** Handle '!' — comment/CDATA/DOCTYPE */
    void onBang() {
        if ((xmlState & BIT_IN_TAG) != 0 && pointer == 1) {
            // "<!" at start of tag
            setSpecial(true);
            // Check for comment "<!--" or CDATA "<![CDATA["
            // Handled in parse loop by peeking ahead
            byte[] src = planner.getSource();
            if (pointer + 3 < src.length && src[pointer] == '-' && src[pointer + 1] == '-' && src[pointer + 2] == '-') {
                // "<!--" comment
                setSpecialComment(true);
            } else if (pointer + 7 < src.length && src[pointer] == '[' && src[pointer + 1] == 'C' && src[pointer + 2] == 'D' && 
                       src[pointer + 3] == 'A' && src[pointer + 4] == 'T' && src[pointer + 5] == 'A' && src[pointer + 6] == '[') {
                // "<![CDATA[" - CDATA section
                // SPECIAL is set, SPECIAL_COMMENT is false
            }
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
        if ((xmlState & BIT_IN_TAG) != 0) {
            if ((xmlState & BIT_IN_DQUOTE) != 0) {
                setInDQuote(false); // end attribute value
                endAttrValue();
            } else if ((xmlState & BIT_IN_SQUOTE) == 0) {
                setInDQuote(true); // start attribute value
                startAttrValue();
            }
        }
    }

    /** Handle ''' — single quote */
    void onSQuote() {
        if ((xmlState & BIT_IN_TAG) != 0) {
            if ((xmlState & BIT_IN_SQUOTE) != 0) {
                setInSQuote(false);
                endAttrValue();
            } else if ((xmlState & BIT_IN_DQUOTE) == 0) {
                setInSQuote(true);
                startAttrValue();
            }
        }
    }

    /** Handle '=' — attribute name/value separator */
    void onEquals() {
        if ((xmlState & BIT_IN_TAG) != 0 && inAttrName) {
            endAttrName();
        }
    }

    /** Handle whitespace in tag */
    void onSpace() {
        if ((xmlState & BIT_IN_TAG) != 0) {
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

        if ((xmlState & BIT_IN_TAG) != 0) {
            if ((xmlState & BIT_CLOSE_TAG) != 0) {
                // In close tag name — accumulate
            } else if (inAttrName) {
                accumulateAttrName(b);
            } else if (inAttrValue) {
                accumulateAttrValue(b);
            } else if ((xmlState & BIT_IN_DQUOTE) == 0 && (xmlState & BIT_IN_SQUOTE) == 0) {
                // In tag name or attribute name (before '=')
                // Tag name handled by findTagNameEnd()
            }
        } else {
            // Outside tag — inner text content
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
                setTarget(true);
                fireTagEvent(child);
            }
        } else {
            // Not registered — enter skip mode
            enterSkip(linear, 0, tagNameLen);
            setTarget(false);
        }

        if (selfClose) {
            // Self-closing: fire END immediately
            if ((xmlState & BIT_TARGET) != 0 && currNode.handler != null) {
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
        if ((xmlState & BIT_TARGET) != 0 && currNode.handler != null) {
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
        if (inAttrValue && (xmlState & BIT_TARGET) != 0 && currNode.handler != null) {
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
        if (textStart >= 0 && textLen > 0 && (xmlState & BIT_TARGET) != 0 && currNode.handler != null) {
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