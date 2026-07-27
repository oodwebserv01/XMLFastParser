package XMLFastReader;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * XmlEvent — Reusable Event Parameter Object (PLAN 3.1/3.2/3.3)
 * 
 * ARCHITECTURE ROLE:
 *   Mutable holder passed to user callbacks for each parse event.
 *   One instance per parser thread (reused) — NEVER shared across threads.
 *   Parser pushes (sets fields), user gets (reads fields).
 * 
 * ZERO-COPY CONTRACT:
 *   - Buffer + offsets/lengths reused across events
 *   - User MUST consume data WITHIN callback (buffer overwritten on next event)
 *   - If user needs to retain data: copy it (String, byte[], etc.)
 * 
 * THREE-LEVEL ACCESS (for attrName/value):
 *   1. Raw region: offset + length (zero-copy, user scans bytes directly)
 *   2. CharSequence view: ByteView (reusable, no String allocation, compare/scan)
 *   3. String: asString() — decodes with charset, allocates new String
 * 
 * EVENT TYPES (7):
 *   FILE_BEGIN — file start (filename set)
 *   TAG        — registered start tag (token set)
 *   ATTR       — attribute (attrName + value set)
 *   INNER      — inner text chunk (value set, may fire multiple per element)
 *   END        — registered end tag (token set)
 *   FILE_END   — file end (filename set)
 *   ERROR      — parse error (filename set)
 * 
 * Algorithm by "Ood Kritsana Wuttisin" from Thailand
 * AI by Openrouter
 */
public final class XmlEvent {

    /** เหตุการณ์ 7 แบบ (PLAN 3.1) */
    public enum Event {
        FILE_BEGIN, TAG, ATTR, INNER, END, FILE_END, ERROR
    }

    // ---- fields (public — set โดย parser, get โดย user) ----

    /** linearize buffer ต่อเทรด — parser set ครั้งเดียวตอน bind (PLAN bufferRef) */
    public byte[] buffer;

    /** charset จาก header (PLAN 3.1.2) — ใช้ตอน asString() */
    public Charset charset;

    /** เหตุการณ์ปัจจุบัน */
    public Event event;

    /** token ที่ฝากไว้ตอน register คืนกลับทุก call (PLAN 3.1.4 / 7.0) */
    public Object token;

    // attribute name (raw region บน buffer); len < 0 = ไม่มี → null
    public int attrNameOff;
    public int attrNameLen = -1;

    // value (attr value ใน ATTR / innerText ใน INNER); len < 0 = ไม่มี → null
    public int valueOff;
    public int valueLen = -1;

    // filename — decode ครั้งเดียว/ไฟล์ ลง char[] ที่ถือไว้ (PLAN 3.2)
    public char[] filenameChars;
    public int filenameLen;

    // reusable CharSequence view แยก 2 ตัว (PLAN 3.2) กันทับกันตอน ATTR
    public final ByteView attrNameView = new ByteView();
    public final ByteView valueView = new ByteView();

    // reusable char[] สำหรับ decode ของ asString() (reuse ข้าม call, คืน String ใหม่)
    private char[] decodeBuf = new char[64];
    private CharsetDecoder decoder;
    private Charset decoderCharset;

    // ---- push (parser) ----

    /** bind linearize buffer (เรียกครั้งเดียวตอนผูก XmlEvent เข้าเทรด) */
    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    /** ตั้ง filename โดย decode ครั้งเดียวลง char[] (เรียกตอน FILE_BEGIN) */
    public void pushFilename(byte[] src, int off, int len, Charset cs) {
        ensureDecoder(cs);
        int cap = (int) (len * decoder.maxCharsPerByte()) + 1;
        if (filenameChars == null || filenameChars.length < cap) filenameChars = new char[cap];
        decoder.reset();
        CharBuffer out = CharBuffer.wrap(filenameChars);
        decoder.decode(ByteBuffer.wrap(src, off, len), out, true);
        decoder.flush(out);
        filenameLen = out.position();
    }

    public void pushAttrName(int off, int len) {
        this.attrNameOff = off;
        this.attrNameLen = len;
    }

    public void pushValue(int off, int len) {
        this.valueOff = off;
        this.valueLen = len;
    }

    // ---- get: attribute name (3 ระดับ) ----

    /** level 1 — raw region (offset/length) ผ่าน field attrNameOff/attrNameLen */

    /** level 2 — CharSequence view (reuse); null ถ้าไม่มี */
    public CharSequence attrName() {
        if (attrNameLen < 0) return null;
        attrNameView.set(buffer, attrNameOff, attrNameLen);
        return attrNameView;
    }

    /** level 3 — String แท้ (alloc ใหม่); null ถ้าไม่มี */
    public String attrNameAsString() {
        if (attrNameLen < 0) return null;
        return decodeToString(buffer, attrNameOff, attrNameLen, charset);
    }

    // ---- get: value (3 ระดับ) ----

    /** level 2 — CharSequence view (reuse); null ถ้าไม่มี */
    public CharSequence value() {
        if (valueLen < 0) return null;
        valueView.set(buffer, valueOff, valueLen);
        return valueView;
    }

    /** level 3 — String แท้ (alloc ใหม่); null ถ้าไม่มี */
    public String valueAsString() {
        if (valueLen < 0) return null;
        return decodeToString(buffer, valueOff, valueLen, charset);
    }

    // ---- get: filename (2 ระดับ: char[] + asString) ----

    /** level 3 — String แท้ (alloc ใหม่) */
    public String filenameAsString() {
        return new String(filenameChars, 0, filenameLen);
    }

    // ---- reset ----

    /** ล้าง field เพื่อ reuse (PLAN 3.3) — buffer/decoder/char[] คงไว้ reuse */
    public void reset() {
        event = null;
        token = null;
        charset = null;
        attrNameOff = 0;
        attrNameLen = -1;
        valueOff = 0;
        valueLen = -1;
        filenameLen = 0;
    }

    // ---- decode helper (reuse char[]) ----

    private void ensureDecoder(Charset cs) {
        if (cs != decoderCharset) {
            decoder = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            decoderCharset = cs;
        }
    }

    private String decodeToString(byte[] b, int off, int len, Charset cs) {
        if (len == 0) return "";
        ensureDecoder(cs);
        int cap = (int) (len * decoder.maxCharsPerByte()) + 1;
        if (decodeBuf.length < cap) decodeBuf = new char[cap];
        decoder.reset();
        CharBuffer out = CharBuffer.wrap(decodeBuf);
        decoder.decode(ByteBuffer.wrap(b, off, len), out, true);
        decoder.flush(out);
        return new String(decodeBuf, 0, out.position());
    }

    /**
     * ByteView — CharSequence view แบบ byte-level (reuse) ชี้ buffer+offset+length
     * ไม่จอง String ใหม่ ใช้ compare/scan (byte = char, Latin-1 style)
     * ถ้าต้อง text จริงตาม charset ให้ใช้ *AsString()
     */
    public static final class ByteView implements CharSequence {
        private byte[] buf;
        private int off;
        private int len;

        void set(byte[] buf, int off, int len) {
            this.buf = buf;
            this.off = off;
            this.len = len;
        }

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int index) {
            return (char) (buf[off + index] & 0xFF);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            ByteView v = new ByteView();
            v.set(buf, off + start, end - start);
            return v;
        }

        @Override
        public String toString() {
            char[] c = new char[len];
            for (int i = 0; i < len; i++) c[i] = (char) (buf[off + i] & 0xFF);
            return new String(c);
        }
    }
}
