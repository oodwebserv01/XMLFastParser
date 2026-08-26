
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree node for registered XML paths.
 * Each node represents a tag in the registered path tree.
 *
 * Example: regist("/Lvl01/Lvl02/Lvl03A", handlerA, tokenA)
 * Creates: root → Lvl01 → Lvl02 → Lvl03A (handler=handlerA, token=tokenA)
 *
 * Supports multiple handlers per node - all handlers are called in registration order.
 * If any handler returns false, parsing skips to next XML file.
 */
public class xmlBluePrintNode {

    // Tag identification
    public long tagHash;           // 64-bit hash of tag name

    // Multiple handlers & tokens for this node (null for intermediate path nodes)
    // Each entry is a HandlerToken pair
    public List<HandlerToken> handlers = new ArrayList<>();

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
     * Handler-Token pair for multiple handler support.
     */
    public static class HandlerToken {
        public final xmlBluePrintCall handler;
        public final Object idToken;

        public HandlerToken(xmlBluePrintCall handler, Object idToken) {
            this.handler = handler;
            this.idToken = idToken;
        }
    }

    /**
     * Add a handler to this node.
     * @return this node for chaining
     */
    public xmlBluePrintNode addHandler(xmlBluePrintCall handler, Object idToken) {
        if (handler != null) {
            handlers.add(new HandlerToken(handler, idToken));
            this.isTarget = true;
        }
        return this;
    }

    /**
     * Call all registered handlers for an event.
     * @param holder The parser holder
     * @param event The event type
     * @param nameBegin Name begin position
     * @param nameEnd Name end position
     * @param valueBegin Value begin position
     * @param valueEnd Value end position
     * @return false if any handler returns false (signal to skip to next XML)
     */
    public boolean callHandlers(xmlBluePrintHolder holder, int event,
                                  int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        for (HandlerToken ht : handlers) {
            if (!ht.handler.call(ht.idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd)) {
                return false; // Signal to skip to next XML
            }
        }
        return true;
    }

    /**
     * Constructor for root node.
     */
    public xmlBluePrintNode() {
        this.tagHash = 0;
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
                            boolean isTarget, boolean isChildOfTarget, CELL hd_close) {
        this.parent = parent;
        this.tagHash = tagHash;
        this.isTarget = isTarget;
        this.isChildOfTarget = isChildOfTarget;
        this.HD_CLOSINGTAG = hd_close;
        if (handler != null) {
            handlers.add(new HandlerToken(handler, idToken));
        }
    }

    /**
     * Add or get child node.
     */
    public xmlBluePrintNode getOrCreateChild(
                                              long tagHash, xmlBluePrintCall handler,
                                              Object idToken, boolean isTarget,
                                              boolean isChildOfTarget, CELL hd_close) {
        xmlBluePrintNode child = children.get(tagHash);
        if (child == null) {
            child = new xmlBluePrintNode(this, tagHash,
                                         handler, idToken, isTarget, isChildOfTarget, hd_close);
            children.put(tagHash, child);
        } else if (handler != null) {
            // Node exists, add handler to existing node
            child.addHandler(handler, idToken);
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