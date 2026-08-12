package xml2csv;

import java.util.ArrayList;
import java.util.List;

public class BPCsvOutput {
    private final String fileName;              // e.g., Invoids, Items
    private final List<BPEntity> entities = new ArrayList<>();
    private BPEntity currentEntity;             // Currently active entity

    public BPCsvOutput(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() { return fileName; }
    public List<BPEntity> getEntities() { return entities; }
    public BPEntity getCurrentEntity() { return currentEntity; }

    public void addEntity(BPEntity entity) {
        entities.add(entity);
        this.currentEntity = entity;
    }

    public void setCurrentEntity(BPEntity entity) {
        this.currentEntity = entity;
    }

    public List<BPField> getAllFields() {
        List<BPField> all = new ArrayList<>();
        for (BPEntity entity : entities) {
            all.addAll(entity.getFields());
        }
        return all;
    }

    public int getTotalFieldCount() {
        return entities.stream().mapToInt(BPEntity::getFieldCount).sum();
    }

    @Override
    public String toString() {
        return String.format("BPCsvOutput[fileName=%s, entities=%d, totalFields=%d]",
                fileName, entities.size(), getTotalFieldCount());
    }
}