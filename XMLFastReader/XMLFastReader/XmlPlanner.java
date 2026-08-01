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

    /** TOC[state][byte] — decision table 2 มิติ (PLAN 6.1 / 6.6)
     *  แถว = 15 states (XmlRunner.State enum), คอลัมน์ = raw byte 8-bit (256)
     *  cell ยังว่าง (null) รอเติมทีละ cell ตาม state machine */
    final callback[][] TOC = new callback[15][256];

    /** default cell — ข้าม byte นี้ (ignore/skip) แล้วต่าไป; call() คืนทันที */
    static final callback IGNORE = new callback() {
        public boolean call(XmlRunner r) { return true; }
    };

    /** สร้าง TOC — ตั้งทุกช่องชี้มาที่ IGNORE (default = ข้าม) แล้วค่อยเติม cell เฉพาะทีหลัง */
    public XmlPlanner() {
        for (int s = 0; s < TOC.length; s++)
            for (int b = 0; b < TOC[0].length; b++)
                TOC[s][b] = IGNORE;
        populateTOC(); // เติม state machine handlers
    }

    // ===== TOC Population: Basic XML State Machine (PLAN 6.6) =====

    /** เติม TOC cells สำหรับ XML parsing state machine */
    private void populateTOC() {
        // State IDs from XmlRunner.State enum:
        // 0: OUTSIDE_TAG_BEFORE_ROOT
        // 1: IN_TAG_FIRST_BYTE_OUTSIDE_ROOT
        // 2: IN_TAG_OUTSIDE_ROOT
        // 3: IN_TAG_HEADER_CHARSET
        // 4: IN_TAG_CDATA_OUTSIDE_ROOT
        // 5: IN_TAG_HTML_COMMENT_OUTSIDE_ROOT
        // 6: OUTSIDE_TAG_INSIDE_ROOT
        // 7: IN_TAG_FIRST_BYTE_INSIDE_ROOT
        // 8: IN_TAG_INSIDE_ROOT
        // 9: IN_TAG_CDATA_INSIDE_ROOT
        // 10: IN_TAG_HTML_COMMENT_INSIDE_ROOT
        // 11: IN_TARGET_TAG
        // 12: IN_TARGET_INNER
        // 13: IN_TAG_DOCTYPE_OUTSIDE_ROOT
        // 14: IN_TAG_DOCTYPE_INSIDE_ROOT

        // Helper: set cell for a given state
        java.util.function.BiConsumer<Integer, callback> set = (state, cb) -> {
            for (int b = 0; b < 256; b++) TOC[state][b] = cb;
        };

        // ===== State 0: OUTSIDE_TAG_BEFORE_ROOT - Looking for '<' =====
        // Default: consume as text content (INNER) - but we're before root so just skip
        for (int b = 0; b < 256; b++) TOC[0][b] = IGNORE;
        TOC[0]['<'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onLt();
                return true;
            }
        };

        // ===== State 1: IN_TAG_FIRST_BYTE_OUTSIDE_ROOT - First byte after '<' outside root =====
        // This is where we see '/', '?', '!', or tag name start
        callback firstByteOutside = new callback() {
            public boolean call(XmlRunner r) {
                // Accumulate tag name character
                byte[] src = r.planner.getSource();
                r.linear[r.pointer - r.regionBegin] = src[r.pointer++];
                return true;
            }
        };
        for (int b = 0; b < 256; b++) TOC[1][b] = firstByteOutside;
        
        // Transitions from first byte:
        TOC[1]['/'] = new callback() { // </ → close tag
            public boolean call(XmlRunner r) {
                // This is a close tag, but we're outside root - just skip
                return true;
            }
        };
        TOC[1]['?'] = new callback() { // <? → PI
            public boolean call(XmlRunner r) {
                r.onQuestion();
                return true;
            }
        };
        TOC[1]['!'] = new callback() { // <! → comment/CDATA/DOCTYPE
            public boolean call(XmlRunner r) {
                r.onBang();
                return true;
            }
        };
        TOC[1]['>'] = new callback() { // <> → empty tag (invalid but handle)
            public boolean call(XmlRunner r) {
                r.onGt();
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
        TOC[1][' '] = attrNameStart;
        TOC[1]['\t'] = attrNameStart;
        TOC[1]['\n'] = attrNameStart;
        TOC[1]['\r'] = attrNameStart;

        // '=' ends attribute name
        TOC[1]['='] = new callback() {
            public boolean call(XmlRunner r) {
                r.onEquals();
                return true;
            }
        };

        // ===== State 2: IN_TAG_OUTSIDE_ROOT - Parsing tag name/attributes outside root =====
        callback inTagOutside = new callback() {
            public boolean call(XmlRunner r) {
                byte[] src = r.planner.getSource();
                r.linear[r.pointer - r.regionBegin] = src[r.pointer++];
                return true;
            }
        };
        for (int b = 0; b < 256; b++) TOC[2][b] = inTagOutside;
        
        TOC[2]['>'] = new callback() { // End of tag
            public boolean call(XmlRunner r) {
                r.onGt();
                return true;
            }
        };
        TOC[2][' '] = attrNameStart;
        TOC[2]['\t'] = attrNameStart;
        TOC[2]['\n'] = attrNameStart;
        TOC[2]['\r'] = attrNameStart;
        TOC[2]['='] = new callback() {
            public boolean call(XmlRunner r) {
                r.onEquals();
                return true;
            }
        };
        TOC[2]['"'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onDQuote();
                return true;
            }
        };
        TOC[2]['\''] = new callback() {
            public boolean call(XmlRunner r) {
                r.onSQuote();
                return true;
            }
        };

        // ===== State 3: IN_TAG_HEADER_CHARSET - Inside <?xml ... ?> =====
        for (int b = 0; b < 256; b++) TOC[3][b] = IGNORE; // Just skip until ?>
        TOC[3]['?'] = new callback() { // Potential end of PI
            public boolean call(XmlRunner r) {
                // Check next char for '>' in parse loop
                return true;
            }
        };

        // ===== State 4: IN_TAG_CDATA_OUTSIDE_ROOT - Inside <![CDATA[ ... ]]> outside root =====
        for (int b = 0; b < 256; b++) TOC[4][b] = IGNORE; // Just skip until ]]>
        // Handled in parse loop

        // ===== State 5: IN_TAG_HTML_COMMENT_OUTSIDE_ROOT - Inside <!-- ... --> outside root =====
        for (int b = 0; b < 256; b++) TOC[5][b] = IGNORE; // Just skip until -->
        // Handled in parse loop

        // ===== State 6: OUTSIDE_TAG_INSIDE_ROOT - Outside tag but inside root element =====
        for (int b = 0; b < 256; b++) TOC[6][b] = IGNORE; // Accumulate as inner text in parse loop
        TOC[6]['<'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onLt();
                return true;
            }
        };

        // ===== State 7: IN_TAG_FIRST_BYTE_INSIDE_ROOT - First byte after '<' inside root =====
        callback firstByteInside = new callback() {
            public boolean call(XmlRunner r) {
                byte[] src = r.planner.getSource();
                r.linear[r.pointer - r.regionBegin] = src[r.pointer++];
                return true;
            }
        };
        for (int b = 0; b < 256; b++) TOC[7][b] = firstByteInside;
        
        TOC[7]['/'] = new callback() { // </ → close tag
            public boolean call(XmlRunner r) {
                // Close tag - will be handled in onGt
                return true;
            }
        };
        TOC[7]['?'] = new callback() { // <? → PI
            public boolean call(XmlRunner r) {
                r.onQuestion();
                return true;
            }
        };
        TOC[7]['!'] = new callback() { // <! → comment/CDATA/DOCTYPE
            public boolean call(XmlRunner r) {
                r.onBang();
                return true;
            }
        };
        TOC[7]['>'] = new callback() { // <> → empty tag
            public boolean call(XmlRunner r) {
                r.onGt();
                return true;
            }
        };
        TOC[7][' '] = attrNameStart;
        TOC[7]['\t'] = attrNameStart;
        TOC[7]['\n'] = attrNameStart;
        TOC[7]['\r'] = attrNameStart;
        TOC[7]['='] = new callback() {
            public boolean call(XmlRunner r) {
                r.onEquals();
                return true;
            }
        };

        // ===== State 8: IN_TAG_INSIDE_ROOT - Parsing tag name/attributes inside root =====
        callback inTagInside = new callback() {
            public boolean call(XmlRunner r) {
                byte[] src = r.planner.getSource();
                r.linear[r.pointer - r.regionBegin] = src[r.pointer++];
                return true;
            }
        };
        for (int b = 0; b < 256; b++) TOC[8][b] = inTagInside;
        
        TOC[8]['>'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onGt();
                return true;
            }
        };
        TOC[8][' '] = attrNameStart;
        TOC[8]['\t'] = attrNameStart;
        TOC[8]['\n'] = attrNameStart;
        TOC[8]['\r'] = attrNameStart;
        TOC[8]['='] = new callback() {
            public boolean call(XmlRunner r) {
                r.onEquals();
                return true;
            }
        };
        TOC[8]['"'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onDQuote();
                return true;
            }
        };
        TOC[8]['\''] = new callback() {
            public boolean call(XmlRunner r) {
                r.onSQuote();
                return true;
            }
        };

        // ===== State 9: IN_TAG_CDATA_INSIDE_ROOT - Inside <![CDATA[ ... ]]> inside root =====
        for (int b = 0; b < 256; b++) TOC[9][b] = IGNORE; // Just skip until ]]>
        // Handled in parse loop

        // ===== State 10: IN_TAG_HTML_COMMENT_INSIDE_ROOT - Inside <!-- ... --> inside root =====
        for (int b = 0; b < 256; b++) TOC[10][b] = IGNORE; // Just skip until -->
        // Handled in parse loop

        // ===== State 13: IN_TAG_DOCTYPE_OUTSIDE_ROOT - Inside <!DOCTYPE ... > outside root =====
        for (int b = 0; b < 256; b++) TOC[13][b] = IGNORE; // Just skip until >
        // Handled in parse loop

        // ===== State 14: IN_TAG_DOCTYPE_INSIDE_ROOT - Inside <!DOCTYPE ... > inside root =====
        for (int b = 0; b < 256; b++) TOC[14][b] = IGNORE; // Just skip until >
        // Handled in parse loop

        // ===== State 11: IN_TARGET_TAG - Inside a registered target tag =====
        callback inTargetTag = new callback() {
            public boolean call(XmlRunner r) {
                byte[] src = r.planner.getSource();
                r.linear[r.pointer - r.regionBegin] = src[r.pointer++];
                return true;
            }
        };
        for (int b = 0; b < 256; b++) TOC[11][b] = inTargetTag;
        
        TOC[11]['>'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onGt();
                return true;
            }
        };
        TOC[11][' '] = attrNameStart;
        TOC[11]['\t'] = attrNameStart;
        TOC[11]['\n'] = attrNameStart;
        TOC[11]['\r'] = attrNameStart;
        TOC[11]['='] = new callback() {
            public boolean call(XmlRunner r) {
                r.onEquals();
                return true;
            }
        };
        TOC[11]['"'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onDQuote();
                return true;
            }
        };
        TOC[11]['\''] = new callback() {
            public boolean call(XmlRunner r) {
                r.onSQuote();
                return true;
            }
        };

        // ===== State 12: IN_TARGET_INNER - Inside target tag content (innerText & child tags) =====
        for (int b = 0; b < 256; b++) TOC[12][b] = IGNORE; // Accumulate as inner text in parse loop
        TOC[12]['<'] = new callback() {
            public boolean call(XmlRunner r) {
                r.onLt();
                return true;
            }
        };

        // ===== UTF-8 MULTI-BYTE HANDLING =====
        // Continuation bytes (0x80-0xBF) → skip in tag name/text
        for (int s = 0; s < TOC.length; s++) {
            for (int b = 0x80; b <= 0xBF; b++) {
                if (TOC[s][b] == IGNORE) {
                    TOC[s][b] = new callback() {
                        public boolean call(XmlRunner r) {
                            // Skip continuation byte
                            return true;
                        }
                    };
                }
            }
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

    public void register(String path, XmlCallback handler, Object token) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path empty");
        if (handler == null) throw new IllegalArgumentException("handler null");

        String[] segments = path.split("/");
        XmlNode curr = root;

        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            int colon = seg.indexOf(':');
            String local = (colon >= 0) ? seg.substring(colon + 1) : seg;
            
            byte[] nameBytes;
            if ("*".equals(local)) {
                nameBytes = new byte[] { '*' };
            } else {
                nameBytes = local.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            XmlNode child = curr.findChild(nameBytes, 0, nameBytes.length);
            if (child == null) {
                child = new XmlNode(nameBytes, nameBytes.length);
                curr.addChild(child);
            }
            curr = child;
        }

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
