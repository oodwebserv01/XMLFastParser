package XMLFastReader;

import java.io.IOException;

/**
 * XmlPlanner — Registry + Handlers + Shared Source Buffer (PLAN 7 / Class 3)
 * 
 * ARCHITECTURE ROLE:
 *   Immutable, thread-safe configuration object shared across all parser threads.
 *   Built once during registration phase, then frozen for read-only access during parsing.
 * 
 * RESPONSIBILITIES:
 *   1. Path Registration (Trie/Graph) — register(path, handler, token) builds a trie of
 *      registered XML paths. Each node stores local name (prefix stripped), handler, token,
 *      and children. Lookup is O(1) per tag via byte-by-byte comparison.
 *   2. TOC (Table of Contents) Engine — Decision table TOC[flags][byte] for state machine.
 *      128 flag combinations × 256 byte values = 32,768 cells. Each cell holds a callback
 *      that executes state transitions. Built once in constructor via populateTOC().
 *   3. Shared Source Buffer — Holds the large byte[] from ZIP/stream that all runners
 *      read from (read-only, no locking needed).
 *   4. Global Handlers — File-level callbacks: FILE_BEGIN, FILE_END, ERROR.
 *   5. Worker Factory — createWorker() produces per-thread XmlWorker instances.
 * 
 * THREAD SAFETY:
 *   - All fields effectively immutable after construction
 *   - Trie nodes, TOC, source buffer shared read-only across threads
 *   - Registration methods NOT thread-safe (call before starting workers)
 * 
 * USAGE:
 *   XmlPlanner planner = new XmlPlanner();
 *   planner.register("Invoice/ID", handler, token);
 *   planner.parse(inputStream, 8);  // starts 8 worker threads
 */
public final class XmlPlanner {

    /** shared read-only buffer — byte[] ใหญ่จาก ZIP/stream หลายไฟล์ XML (PLAN 4.2 / 11.7)
     *  runner แต่ละตัวอ่านร่วมกันแบบ read-only ไม่ต้อง lock; ถูก set ครั้งเดียวก่อนเริ่ม parse */
    private byte[] source;

    /** graph root — node เริ่มต้น (XmlRunner.resetState เรียก planner.root)
     *  ตั้งชื่อว่างรอระบุชื่อแท็ก root จริงตอนเจอทีหลัง (container) */
    public final XmlNode root = new XmlNode(new byte[0], 0);

    /** ตั้ง shared source buffer (producer เรียกครั้งเดียว) */
    public void setSource(byte[] src) { this.source = src; }

    /** อ่าน shared source buffer (runner เรียกตอน inflate/parse) */
    public byte[] getSource() { return source; }

    /** internal action ของ TOC cell — เรียกจาก parse loop ตอนเจอ (flags, byte) (PLAN 6.6)
     *  แยกจาก XmlCallback (user-facing: boolean handle(XmlEvent)) — ตัวนี้แตะ runner โดยตรง
     *  คืน false = หยุด parse ไฟล์นี้ (user soft-reject หรือ error) */
    static interface callback {
        boolean call(XmlRunner r) throws Exception;
    }

    /** TOC[flags][byte] — decision table 2 มิติ (PLAN 6.1 / 6.6)
     *  แถว = 6 flag bit (xmlState & 0x3F → 64), คอลัมน์ = raw byte 8-bit (256)
     *  cell ยังว่าง (null) รอเติมทีละ cell ตาม state machine */
    final callback[][] TOC = new callback[128][256];

    /** default cell — ข้าม byte นี้ (ignore/skip) แล้วต่าไป; call() คืนทันที */
    static final callback IGNORE = new callback() {
        public boolean call(XmlRunner r) { return true; }
    };

    /** สร้าง TOC — ตั้งทุกช่องชี้มาที่ IGNORE (default = ข้าม) แล้วค่อยเติม cell เฉพาะทีหลัง */
    public XmlPlanner() {
        for (int f = 0; f < TOC.length; f++)
            for (int b = 0; b < TOC[0].length; b++)
                TOC[f][b] = IGNORE;
        populateTOC(); // เติม state machine handlers
    }

    // ===== TOC Population: Basic XML State Machine (PLAN 6.6) =====

    /** เติม TOC cells สำหรับ XML parsing state machine */
    private void populateTOC() {
        // ===== FLAG BIT DEFINITIONS (must match XmlRunner) =====
        final int IN_TAG           = 1 << 0; // 0x01
        final int IN_DQUOTE        = 1 << 1; // 0x02
        final int IN_SQUOTE        = 1 << 2; // 0x04
        final int CLOSE_TAG        = 1 << 3; // 0x08
        final int SPECIAL          = 1 << 4; // 0x10
        final int SPECIAL_COMMENT  = 1 << 5; // 0x20
        final int TARGET           = 1 << 6; // 0x40

        // Helper: set cell for a given flag combination
        java.util.function.BiConsumer<Integer, callback> set = (flags, cb) -> {
            for (int b = 0; b < 256; b++) TOC[flags][b] = cb;
        };

        // ===== 1. OUTSIDE TAG (flags=0): Looking for '<' =====
        // Default: consume as text content (INNER)
        // Actually, outside tag we just advance pointer. Text handling is done in parse loop.
        // For TOC, we only care about '<' transition.
        for (int b = 0; b < 256; b++) TOC[0][b] = IGNORE;
        TOC[0]['<'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onLt();
                return true;
            }
        };

        // ===== 2. IN_TAG (flags=IN_TAG): Parsing tag name =====
        int inTag = IN_TAG;
        // Default: accumulate tag name
        callback tagNameAccum = new callback() {
            public boolean call(XmlRunner r) {
                byte[] src = r.planner.getSource();
                r.linear[r.pointer - r.regionBegin] = src[r.pointer++];
                return true;
            }
        };
        for (int b = 0; b < 256; b++) TOC[inTag][b] = tagNameAccum;

        // Transitions from IN_TAG:
        TOC[inTag]['/'] = new callback() { // </ → close tag
            public boolean call(XmlRunner r) {
                r.setCloseTag(true);
                return true;
            }
        };
        TOC[inTag]['?'] = new callback() { // <? → PI
            public boolean call(XmlRunner r) {
                r.setSpecial(true);
                return true;
            }
        };
        TOC[inTag]['!'] = new callback() { // <! → comment/CDATA/DOCTYPE
            public boolean call(XmlRunner r) {
                r.setSpecial(true);
                return true;
            }
        };
        TOC[inTag]['>'] = new callback() { // <tag> → end of start tag
            public boolean call(XmlRunner r) {
                r.setInTag(false);
                // Check if tag name matches registered path
                // This will be handled by parse loop after TOC returns
                return true;
            }
        };
        // Space, tab, newline, carriage return → end of tag name, enter attribute parsing
        callback attrNameStart = new callback() {
            public boolean call(XmlRunner r) {
                r.startAttrName();
                return true;
            }
        };
        TOC[inTag][' '] = attrNameStart;
        TOC[inTag]['\t'] = attrNameStart;
        TOC[inTag]['\n'] = attrNameStart;
        TOC[inTag]['\r'] = attrNameStart;

        // '=' ends attribute name
        TOC[inTag]['='] = new callback() {
            public boolean call(XmlRunner r) {
                r.onEquals();
                return true;
            }
        };

        // ===== 3. IN_TAG + CLOSE_TAG (flags=IN_TAG|CLOSE_TAG): Parsing close tag name =====
        int inCloseTag = IN_TAG | CLOSE_TAG;
        for (int b = 0; b < 256; b++) TOC[inCloseTag][b] = tagNameAccum;
        TOC[inCloseTag]['>'] = new callback() { // </tag> → end of close tag
            public boolean call(XmlRunner r) {
                r.setInTag(false);
                r.setCloseTag(false);
                return true;
            }
        };
        TOC[inCloseTag][' '] = attrNameStart;
        TOC[inCloseTag]['\t'] = attrNameStart;
        TOC[inCloseTag]['\n'] = attrNameStart;
        TOC[inCloseTag]['\r'] = attrNameStart;

        // ===== 4. IN_TAG + SPECIAL (flags=IN_TAG|SPECIAL): After <! or <? =====
        int inSpecial = IN_TAG | SPECIAL;
        // Default: skip until '>' or '?>'
        for (int b = 0; b < 256; b++) TOC[inSpecial][b] = IGNORE;
        TOC[inSpecial]['-'] = new callback() { // <!-- → comment
            public boolean call(XmlRunner r) {
                // Check next char for second '-'
                r.setSpecialComment(true);
                return true;
            }
        };
        TOC[inSpecial]['['] = new callback() { // <![CDATA[ or <![DOCTYPE
            public boolean call(XmlRunner r) {
                // Will check for "CDATA[" in parse loop
                return true;
            }
        };
        TOC[inSpecial]['>'] = new callback() { // <?...> or <!> → end of PI/special
            public boolean call(XmlRunner r) {
                r.onPiEnd(); // Parse encoding if XML declaration
                r.setInTag(false);
                r.setSpecial(false);
                return true;
            }
        };
        TOC[inSpecial]['?'] = new callback() { // <?...?> → end of PI
            public boolean call(XmlRunner r) {
                // Check next char for '>'
                return true;
            }
        };

        // ===== 5. IN_TAG + SPECIAL + SPECIAL_COMMENT (flags=IN_TAG|SPECIAL|SPECIAL_COMMENT): Inside <!-- ... --> =====
        int inComment = IN_TAG | SPECIAL | SPECIAL_COMMENT;
        for (int b = 0; b < 256; b++) TOC[inComment][b] = IGNORE;
        TOC[inComment]['-'] = new callback() { // -- → potential end
            public boolean call(XmlRunner r) {
                // Check next char for '>' in parse loop
                return true;
            }
        };

        // ===== 5b. CDATA section: <![CDATA[ ... ]]> =====
        // We'll handle CDATA end in parse loop by checking for "]]>"
        // State: IN_TAG | SPECIAL (but not SPECIAL_COMMENT)

        // ===== 6. IN_TAG + IN_DQUOTE / IN_SQUOTE: Attribute value inside tag =====
        int inTagDQuote = IN_TAG | IN_DQUOTE;
        for (int b = 0; b < 256; b++) TOC[inTagDQuote][b] = IGNORE;
        TOC[inTagDQuote]['"'] = new callback() {
            public boolean call(XmlRunner r) {
                r.setInDQuote(false);
                return true;
            }
        };

        int inTagSQuote = IN_TAG | IN_SQUOTE;
        for (int b = 0; b < 256; b++) TOC[inTagSQuote][b] = IGNORE;
        TOC[inTagSQuote]['\''] = new callback() {
            public boolean call(XmlRunner r) {
                r.setInSQuote(false);
                return true;
            }
        };

        // ===== 7. IN_DQUOTE (flags=IN_DQUOTE): Inside "..." (outside tag) =====
        int inDQuote = IN_DQUOTE;
        for (int b = 0; b < 256; b++) TOC[inDQuote][b] = IGNORE; // Accumulate in parse loop
        TOC[inDQuote]['"'] = new callback() { // End of double-quoted value
            public boolean call(XmlRunner r) {
                r.setInDQuote(false);
                return true;
            }
        };

        // ===== 8. IN_SQUOTE (flags=IN_SQUOTE): Inside '...' (outside tag) =====
        int inSQuote = IN_SQUOTE;
        for (int b = 0; b < 256; b++) TOC[inSQuote][b] = IGNORE;
        TOC[inSQuote]['\''] = new callback() { // End of single-quoted value
            public boolean call(XmlRunner r) {
                r.setInSQuote(false);
                return true;
            }
        };

        // ===== 9. OUTSIDE TAG (flags=0): Text content =====
        // Default: accumulate as inner text
        for (int b = 0; b < 256; b++) TOC[0][b] = IGNORE;
        TOC[0]['<'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onLt();
                return true;
            }
        };

        // ===== 10. COMMENT END: IN_TAG|SPECIAL|SPECIAL_COMMENT looking for --> =====
        // Already defined above as inComment (line ~187)

        // ===== 11. CDATA END: IN_TAG|SPECIAL looking for ]]> =====
        // Handled in parse loop by peeking ahead

        // ===== 12. SELF-CLOSING TAG: '/>' inside tag =====
        // Handled in parse loop when seeing '/' followed by '>'

        // ===== 13. UTF-8 MULTI-BYTE HANDLING =====
        // Continuation bytes (0x80-0xBF) → skip in tag name/text
        for (int flags = 0; flags < TOC.length; flags++) {
            for (int b = 0x80; b <= 0xBF; b++) {
                if (TOC[flags][b] == IGNORE) {
                    TOC[flags][b] = new callback() {
                        public boolean call(XmlRunner r) {
                            // Skip continuation byte
                            return true;
                        }
                    };
                }
            }
            // Leading bytes: skip appropriate number of following bytes
            // 0xC0-0xDF: 2-byte sequence (skip 1 more)
            // 0xE0-0xEF: 3-byte sequence (skip 2 more)
            // 0xF0-0xF7: 4-byte sequence (skip 3 more)
            // Handled in parse loop, not TOC
        }
    }

    // ===== Registration API (PLAN 7.0) =====

    /** Global handler: เรียกตอนเริ่มไฟล์ (FILE_BEGIN) */
    private XmlCallback fileBeginHandler;
    /** Global handler: เรียกตอนจบไฟล์ (FILE_END) */
    private XmlCallback fileEndHandler;
    /** Global handler: เรียกตอนไฟล์เสีย (ERROR) */
    private XmlCallback errorHandler;

    public void registFileBegin(XmlCallback h) { this.fileBeginHandler = h; }
    public void registFileEnd(XmlCallback h) { this.fileEndHandler = h; }
    public void registError(XmlCallback h) { this.errorHandler = h; }

    /** ดึง global handlers (parser เรียก) */
    XmlCallback getFileBeginHandler() { return fileBeginHandler; }
    XmlCallback getFileEndHandler() { return fileEndHandler; }
    XmlCallback getErrorHandler() { return errorHandler; }

    /**
     * ลงทะเบียน path + handler + token
     * @param path   เช่น "ns:Parent/ns:Child/ns:Target" (แยกด้วย '/', ตัด prefix ก่อน ':' ออก)
     * @param handler XmlCallback ที่จะได้รับ event TAG/ATTR/INNER/END
     * @param token   object ใดๆ ที่จะคืนผ่าน XmlEvent.token ทุกครั้งที่ handler ถูกเรียก
     */
    public void register(String path, XmlCallback handler, Object token) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path empty");
        if (handler == null) throw new IllegalArgumentException("handler null");

        String[] segments = path.split("/");
        XmlNode curr = root;

        for (String seg : segments) {
            if (seg.isEmpty()) continue; // กัน "//" หรือ "/" นำหน้า
            // ตัด prefix (ns:name -> name)
            int colon = seg.indexOf(':');
            String local = (colon >= 0) ? seg.substring(colon + 1) : seg;
            byte[] nameBytes = local.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // หา child เดิม
            XmlNode child = curr.findChild(nameBytes, 0, nameBytes.length);
            if (child == null) {
                child = new XmlNode(nameBytes, nameBytes.length);
                curr.addChild(child);
            }
            curr = child;
        }

        // leaf node: ผูก handler + token
        curr.handler = handler;
        curr.token = token;
    }

    /**
     * สร้าง Worker thread ใหม่ผูกกับ planner นี้
     * @return XmlWorker พร้อมรันใน thread แยก
     */
    public XmlWorker createWorker() {
        return new XmlWorker(this);
    }

    // ===== Public Parse API (User-facing) =====

    /**
     * ประมวลผล XML จาก InputStream (generic - รองรับทุกแหล่งที่มา)
     * @param inputStream สตรีม XML (single file) หรือ ZIP stream
     * @param threadCount จำนวน worker threads
     * @return ParseHandle สำหรับควบคุม/รอผล
     */
    public ParseHandle parse(java.io.InputStream inputStream, int threadCount) {
        XmlDispatcher dispatcher = new XmlDispatcher(this, threadCount);
        
        // Detect if ZIP stream by checking magic bytes
        try {
            inputStream.mark(4);
            byte[] magic = new byte[4];
            int read = inputStream.read(magic);
            inputStream.reset();
            
            boolean isZip = read == 4 && magic[0] == 'P' && magic[1] == 'K' && magic[2] == 3 && magic[3] == 4;
            
            if (isZip) {
                dispatcher.setProducer(new ZipXmlProducer(inputStream));
            } else {
                dispatcher.setProducer(new SingleXmlProducer(inputStream, "stream.xml"));
            }
        } catch (Exception e) {
            // Fallback to single XML
            try {
                dispatcher.setProducer(new SingleXmlProducer(inputStream, "stream.xml"));
            } catch (IOException ioe) {
                throw new IllegalArgumentException("Invalid input stream", ioe);
            }
        }
        
        return dispatcher.startAsync();
    }

    /**
     * ประมวลผลจากไฟล์ ZIP บนดิสก์
     * @param zipFile ไฟล์ ZIP
     * @param threadCount จำนวน worker threads
     * @return ParseHandle
     */
    public ParseHandle parseZip(java.io.File zipFile, int threadCount) {
        XmlDispatcher dispatcher = new XmlDispatcher(this, threadCount);
        dispatcher.setProducer(new FileXmlProducer(zipFile));
        return dispatcher.startAsync();
    }

    /**
     * ประมวลผลจากไฟล์ XML เดียว
     * @param xmlFile ไฟล์ XML
     * @param threadCount จำนวน worker threads
     * @return ParseHandle
     */
    public ParseHandle parseFile(java.io.File xmlFile, int threadCount) {
        XmlDispatcher dispatcher = new XmlDispatcher(this, threadCount);
        dispatcher.setProducer(new FileXmlProducer(xmlFile));
        return dispatcher.startAsync();
    }

    /**
     * ประมวลผลจาก byte[] ที่อยู่ใน memory แล้ว
     * @param xmlBytes ข้อมูล XML
     * @param threadCount จำนวน worker threads
     * @return ParseHandle
     */
    public ParseHandle parseBytes(byte[] xmlBytes, int threadCount) {
        XmlDispatcher dispatcher = new XmlDispatcher(this, threadCount);
        try {
            dispatcher.setProducer(new SingleXmlProducer(
                new java.io.ByteArrayInputStream(xmlBytes), "bytes.xml"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid byte array", e);
        }
        return dispatcher.startAsync();
    }

    /**
     * ประมวลผลจาก URL (HTTP/S3/etc)
     * @param url URL ของ XML หรือ ZIP
     * @param threadCount จำนวน worker threads
     * @return ParseHandle
     */
    public ParseHandle parseUrl(java.net.URL url, int threadCount) {
        XmlDispatcher dispatcher = new XmlDispatcher(this, threadCount);
        try {
            java.io.InputStream is = url.openStream();
            dispatcher.setProducer(new SingleXmlProducer(is, url.getFile()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot open URL: " + url, e);
        }
        return dispatcher.startAsync();
    }
}
