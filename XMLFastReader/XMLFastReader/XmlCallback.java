package XMLFastReader;

/**
 * XmlCallback — User Event Handler Interface (PLAN 3.1 / 8.5.3)
 * 
 * ARCHITECTURE ROLE:
 *   User implements this to receive parse events. One handler can serve multiple
 *   registered paths — token in XmlEvent distinguishes which path fired.
 * 
 * CONTRACT:
 *   - Called from parser thread (XmlWorker) — must be fast, non-blocking
 *   - XmlEvent is REUSED — consume data WITHIN this call (buffer overwritten next event)
 *   - Return false from TAG/ATTR/INNER/END to soft-reject file (skip to next file)
 *   - FILE_BEGIN/FILE_END/ERROR return value ignored
 * 
 * THREAD SAFETY:
 *   - Single-threaded per handler instance (one worker thread calls it)
 *   - If shared across paths, use token to distinguish state
 * 
 * TYPICAL PATTERN:
 *   public boolean handle(XmlEvent e) {
 *       switch (e.event) {
 *           case TAG:   // start element
 *               MyToken t = (MyToken) e.token;
 *               t.startElement(e);
 *               break;
 *           case ATTR:  // attribute
 *               String name = e.attrNameAsString();
 *               String val = e.valueAsString();
 *               break;
 *           case INNER: // text content
 *               String text = e.valueAsString();
 *               break;
 *           case END:   // end element
 *               break;
 *       }
 *       return true; // continue parsing
 *   }
 * 
 * Algorithm by "Ood Kritsana Wuttisin" from Thailand
 * AI by Openrouter
 */
public interface XmlCallback {

    /**
     * @param e XmlEvent (reuse) — อ่านค่าภายใน call เท่านั้น
     * @return event ปกติ (TAG/ATTR/INNER/END): false = ข้ามไฟล์นี้ (soft reject);
     *         FILE_BEGIN/FILE_END/ERROR: คืนค่าไม่มีผล
     */
    boolean handle(XmlEvent e);
}
