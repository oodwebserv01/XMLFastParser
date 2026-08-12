package xml2csv;

import java.util.ArrayList;
import java.util.List;

public class BPFileConfig {
    private final List<BPCsvOutput> outputs = new ArrayList<>();
    private BPCsvOutput currentOutput;

    public List<BPCsvOutput> getOutputs() { return outputs; }
    public BPCsvOutput getCurrentOutput() { return currentOutput; }

    public void addOutput(BPCsvOutput output) {
        outputs.add(output);
        this.currentOutput = output;
    }

    public void setCurrentOutput(BPCsvOutput output) {
        this.currentOutput = output;
    }

    public List<BPField> getAllFields() {
        List<BPField> all = new ArrayList<>();
        for (BPCsvOutput output : outputs) {
            all.addAll(output.getAllFields());
        }
        return all;
    }

    public List<String> getAllEntityPaths() {
        List<String> paths = new ArrayList<>();
        for (BPCsvOutput output : outputs) {
            for (BPEntity entity : output.getEntities()) {
                paths.add(entity.getEntityPath());
            }
        }
        return paths;
    }

    @Override
    public String toString() {
        return String.format("BPFileConfig[outputs=%d, totalFields=%d]",
                outputs.size(), getAllFields().size());
    }
}