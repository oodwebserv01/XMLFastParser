import java.util.HashMap;

/**
 * TokenColumn - Represents a column in the output (attribute or innerText)
 * Contains path to XML element, column index for innerText, and attribute mappings
 */
public class TokenColumn {
    public TokenEntity entity;                    // Parent entity
    public String path;                           // XML path (e.g., "/Transaction/Invoid@SN")
    public int inner = -1;                        // Column index for innerText (#)
    public HashMap<Long, Integer> AllAttr;        // Map: attrNameHash -> columnIndex

    public TokenColumn() {
        this.AllAttr = new HashMap<>();
        this.inner = -1;
        this.entity = null;
        this.path = null;
    }

    public TokenColumn(TokenEntity entity, String path) {
        this();
        this.entity = entity;
        this.path = path;
    }

    public TokenEntity getEntity() {
        return entity;
    }

    public void setEntity(TokenEntity entity) {
        this.entity = entity;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getInner() {
        return inner;
    }

    public void setInner(int inner) {
        this.inner = inner;
    }

    public HashMap<Long, Integer> getAllAttr() {
        return AllAttr;
    }

    public void addAttribute(long attrHash, int columnIndex) {
        this.AllAttr.put(attrHash, columnIndex);
    }

    public Integer getAttributeColumn(long attrHash) {
        return this.AllAttr.get(attrHash);
    }

    @Override
    public String toString() {
        return "TokenColumn{path='" + path + "', inner=" + inner + ", attrs=" + AllAttr.size() + "}";
    }
}