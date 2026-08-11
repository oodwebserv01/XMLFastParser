package xml2csv.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration parsed from .x2c file
 */
public class X2CConfig {
    private String successFilePattern = "list_success.txt";
    private String failedFilePattern = "list_fail.txt";
    private final List<OutputConfig> outputs = new ArrayList<>();

    public String getSuccessFilePattern() {
        return successFilePattern;
    }

    public void setSuccessFilePattern(String successFilePattern) {
        this.successFilePattern = successFilePattern;
    }

    public String getFailedFilePattern() {
        return failedFilePattern;
    }

    public void setFailedFilePattern(String failedFilePattern) {
        this.failedFilePattern = failedFilePattern;
    }

    public List<OutputConfig> getOutputs() {
        return outputs;
    }

    public void addOutput(OutputConfig output) {
        outputs.add(output);
    }

    @Override
    public String toString() {
        return "X2CConfig{" +
                "successFilePattern='" + successFilePattern + '\'' +
                ", failedFilePattern='" + failedFilePattern + '\'' +
                ", outputs=" + outputs +
                '}';
    }
}