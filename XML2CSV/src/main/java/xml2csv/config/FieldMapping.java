package xml2csv.config;

/**
 * Represents a single field mapping: path + extraction type ($ for inner text, @ for attribute)
 */
public class FieldMapping {
    private String path;           // full resolved path (e.g., /rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:Name)
    private String rawPath;        // original path from config (may be shorthand)
    private ExtractType type;      // INNER_TEXT ($) or ATTRIBUTE (@)
    private String attributeName;  // for ATTRIBUTE type: the attribute name after @
    private int columnIndex;       // output column order (0-based)

    public enum ExtractType {
        INNER_TEXT,   // $ suffix
        ATTRIBUTE     // @ suffix
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public ExtractType getType() {
        return type;
    }

    public void setType(ExtractType type) {
        this.type = type;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    public void setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
    }

    @Override
    public String toString() {
        return "FieldMapping{" +
                "path='" + path + '\'' +
                ", rawPath='" + rawPath + '\'' +
                ", type=" + type +
                ", attributeName='" + attributeName + '\'' +
                ", columnIndex=" + columnIndex +
                '}';
    }
}