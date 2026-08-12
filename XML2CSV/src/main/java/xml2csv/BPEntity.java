package xml2csv;

import java.util.ArrayList;
import java.util.List;

public class BPEntity {
    private final String entityPath;           // e.g., /Transaction or /Transaction/Invoid/Items/Item
    private final List<BPField> fields = new ArrayList<>();
    private String previousFullPath = "";      // For shorthand /./ resolution

    public BPEntity(String entityPath) {
        this.entityPath = entityPath;
    }

    public String getEntityPath() { return entityPath; }
    public List<BPField> getFields() { return fields; }
    public String getPreviousFullPath() { return previousFullPath; }
    public void setPreviousFullPath(String path) { this.previousFullPath = path; }

    public void addField(BPField field) {
        fields.add(field);
    }

    public int getFieldCount() { return fields.size(); }

    @Override
    public String toString() {
        return String.format("BPEntity[path=%s, fields=%d]", entityPath, fields.size());
    }
}