package xmlFastParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Tree node for registered XML paths.
 * Each node represents a tag in the registered path tree.
 * 
 * Example: regist("/Lvl01/Lvl02/Lvl03A", handlerA, tokenA)
 * Creates: root → Lvl01 → Lvl02 → Lvl03A (handler=handlerA, token=tokenA)
 */
public class xmlBluePrintNode {
    
    // Tag identification
    public long tagHash;           // 64-bit hash of tag name
    
    // Handler & token for this node (null for intermediate path nodes)
    public xmlBluePrintCall handler;
    public Object idToken;
    
    // Children map: tagHash → child node
    public Map<Long, xmlBluePrintNode> children = new HashMap<>();
    
    // Parent reference (for close tag handling)
    public xmlBluePrintNode parent;
     
    // Whether this node is a TARGET (registered via regist() with handler)
    public boolean isTarget;
    
    // Whether this node is a CHILD of a target (registered under a target path)
    public boolean isChildOfTarget;

    public CELL HD_CLOSINGTAG;
    
    /**
     * Constructor for root node.
     */
    public xmlBluePrintNode() {
        this.tagHash = 0;
        this.handler = null;
        this.idToken = null;
        this.parent = null;
        this.isTarget = false;
        this.isChildOfTarget = false;
        this.HD_CLOSINGTAG = null;
    }
    
    /**
     * Constructor for path nodes.
     */
    public xmlBluePrintNode(xmlBluePrintNode parent,  
                            long tagHash, xmlBluePrintCall handler, Object idToken,
                            boolean isTarget, boolean isChildOfTarget,CELL hd_close) {
        this.parent = parent;
        this.tagHash = tagHash;
        this.handler = handler;
        this.idToken = idToken;
        this.isTarget = isTarget;
        this.isChildOfTarget = isChildOfTarget;
        this.HD_CLOSINGTAG = hd_close;
    }
    
    /**
     * Add or get child node.
     */
    public xmlBluePrintNode getOrCreateChild( 
                                              long tagHash, xmlBluePrintCall handler, 
                                              Object idToken, boolean isTarget, 
                                              boolean isChildOfTarget,CELL hd_close) {
        xmlBluePrintNode child = children.get(tagHash);
        if (child == null) {
            child = new xmlBluePrintNode(this, tagHash, 
                                         handler, idToken, isTarget, isChildOfTarget, hd_close);
            children.put(tagHash, child);
        }
        return child;
    }
    
    /**
     * Get child by hash.
     */
    public xmlBluePrintNode getChild(long tagHash) {
        return children.get(tagHash);
    }
    
    /**
     * Check if has child with hash.
     */
    public boolean hasChild(long tagHash) {
        return children.containsKey(tagHash);
    }
}