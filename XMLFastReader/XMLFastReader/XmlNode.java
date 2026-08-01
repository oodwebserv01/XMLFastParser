package XMLFastReader;

/**
 * XmlNode — Trie Node for Registered Path Graph (PLAN 7.1 / 7.2)
 * 
 * ARCHITECTURE ROLE:
 *   Immutable (after build) trie node stored in XmlPlanner. Shared read-only across all threads.
 *   Represents a single path segment (local name, prefix stripped).
 * 
 * STRUCTURE:
 *   - name/nameLen: Local name as byte[] (prefix stripped, e.g., "ns:Invoice" → "Invoice")
 *   - handler/token: Callback and user token for this exact path (null = intermediate node)
 *   - parent: Pointer to parent node (for END event traversal back up the trie)
 *   - children: Linear array of child nodes (typically small — only registered paths)
 * 
 * LOOKUP ALGORITHM (Hot Path):
 *   findChild(buf, off, len) — Linear scan of children:
 *     1. Compare nameLen first (fast reject)
 *     2. Byte-by-byte comparison (no String allocation, no hash)
 *     3. Returns matching child or null
 *   O(children) where children = registered paths at this level (typically < 10)
 * 
 * BUILD PHASE:
 *   addChild() called during registration only. After all register() calls,
 *   trie becomes effectively immutable (read-only during parsing).
 * 
 * THREAD SAFETY:
 *   - Effectively immutable after registration phase
 *   - Read-only access from multiple parser threads simultaneously
 *   - No synchronization needed
 */
final class XmlNode {

    /** local name (prefix ตัดแล้ว) — เทียบ byte ตรงๆ ไม่จอง String ต่อ tag (PLAN 7.2) */
    final byte[] name;
    final int nameLen;

    /** handler + token ตอน path นี้ครบ (null ถ้ายังไม่ลงทะเบียนที่โหนดนี้) */
    Object handler;
    Object token;

    /** parent node (for END event traversal) */
    XmlNode parent;

    /** registered children — เทียบเชิงเส้น (PLAN 7.2) */
    private XmlNode[] children = new XmlNode[0];

    XmlNode(byte[] name, int nameLen) {
        this.name = name;
        this.nameLen = nameLen;
    }

    /** หา child ที่ local name ตรงกับ buf[off..off+len]; คืน null ถ้าไม่มี (hot path)
     * รองรับ wildcard: child ที่มี nameLen == 1 และ name[0] == '*' จะ match ทุกชื่อ */
    XmlNode findChild(byte[] buf, int off, int len) {
        XmlNode[] c = children;
        XmlNode wildcardChild = null;
        
        for (int i = 0; i < c.length; i++) {
            XmlNode n = c[i];
            // Check for wildcard node (name = "*")
            if (n.nameLen == 1 && n.name[0] == '*') {
                wildcardChild = n;
                continue;
            }
            if (n.nameLen != len) continue;
            int j = 0;
            while (j < len && n.name[j] == buf[off + j]) j++;
            if (j == len) return n;
        }
        
        // Return wildcard child if no exact match found
        return wildcardChild;
    }

    /** เพิ่ม child (build phase เท่านั้น — PLAN 11.6 registry read-only หลัง build) */
    void addChild(XmlNode child) {
        child.parent = this;
        XmlNode[] c = children;
        XmlNode[] n = new XmlNode[c.length + 1];
        System.arraycopy(c, 0, n, 0, c.length);
        n[c.length] = child;
        children = n;
    }
}
