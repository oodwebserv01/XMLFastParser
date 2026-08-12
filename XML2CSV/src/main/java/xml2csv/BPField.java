package xml2csv;

public class BPField {
    public enum FieldType {
        ATTRIBUTE,    // @ - attribute value
        INNER_TEXT    // # - inner text of element
    }

    private final String path;       // Full path (e.g., /Transaction/Invoid@SN)
    private final FieldType type;    // ATTRIBUTE or INNER_TEXT
    private final String fieldName;  // Attribute name (for @) or element name (for #)
    private final String fullPath;   // Resolved full path without @ or #
    private final int columnIndex;   // Column index in CSV (0-based, 0 = xmlFilePath)

    public BPField(String path, FieldType type, String fieldName, String fullPath, int columnIndex) {
        this.path = path;
        this.type = type;
        this.fieldName = fieldName;
        this.fullPath = fullPath;
        this.columnIndex = columnIndex;
    }

    public String getPath() { return path; }
    public FieldType getType() { return type; }
    public String getFieldName() { return fieldName; }
    public String getFullPath() { return fullPath; }
    public int getColumnIndex() { return columnIndex; }

    @Override
    public String toString() {
        return String.format("BPField[type=%s, path=%s, fieldName=%s, fullPath=%s, col=%d]",
                type, path, fieldName, fullPath, columnIndex);
    }
}