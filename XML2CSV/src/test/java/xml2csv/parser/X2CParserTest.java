package xml2csv.parser;

import xml2csv.config.FieldMapping;
import xml2csv.config.OutputConfig;
import xml2csv.config.X2CConfig;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class X2CParserTest {
    public static void main(String[] args) throws IOException {
        Path configPath = Paths.get("/home/wise0136/myProject/XMLFastReader/datas/config.x2c");
        String content = Files.readString(configPath);
        System.out.println("=== Config content ===");
        System.out.println(content);
        System.out.println("======================\n");

        X2CParser parser = new X2CParser();
        X2CConfig config = parser.parse(List.of(content.split("\n")));

        System.out.println("=== Parsed Config ===");
        System.out.println("Success file: " + config.getSuccessFilePattern());
        System.out.println("Failed file: " + config.getFailedFilePattern());
        System.out.println("Outputs: " + config.getOutputs().size());

        for (OutputConfig output : config.getOutputs()) {
            System.out.println("\n--- Output: " + output.getFileName() + " ---");
            System.out.println("  Multi-row: " + output.isMultiRow());
            System.out.println("  Body path: " + output.getBodyPath());
            System.out.println("  Fields (" + output.getFields().size() + "):");
            for (FieldMapping field : output.getFields()) {
                System.out.printf("    [%d] %s -> %s (%s)%n",
                        field.getColumnIndex(),
                        field.getRawPath(),
                        field.getPath(),
                        field.getType());
                if (field.getType() == FieldMapping.ExtractType.ATTRIBUTE) {
                    System.out.printf("         attr: %s%n", field.getAttributeName());
                }
            }
        }
    }
}