package xml2csv.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single output file configuration (entity.txt, product.txt, etc.)
 */
public class OutputConfig {
    private String fileName;           // e.g., "entity.txt"
    private String bodyPath;           // for multi-row: the repeating element path (with body: prefix)
    private final List<FieldMapping> fields = new ArrayList<>();
    private boolean isMultiRow = false;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getBodyPath() {
        return bodyPath;
    }

    public void setBodyPath(String bodyPath) {
        this.bodyPath = bodyPath;
        this.isMultiRow = bodyPath != null && !bodyPath.isEmpty();
    }

    public List<FieldMapping> getFields() {
        return fields;
    }

    public void addField(FieldMapping field) {
        fields.add(field);
    }

    public boolean isMultiRow() {
        return isMultiRow;
    }

    @Override
    public String toString() {
        return "OutputConfig{" +
                "fileName='" + fileName + '\'' +
                ", bodyPath='" + bodyPath + '\'' +
                ", fields=" + fields +
                ", isMultiRow=" + isMultiRow +
                '}';
    }
}