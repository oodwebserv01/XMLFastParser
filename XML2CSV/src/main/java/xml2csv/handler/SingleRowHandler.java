package xml2csv.handler;

import XMLFastReader.XmlCallback;
import XMLFastReader.XmlEvent;
import xml2csv.config.FieldMapping;
import xml2csv.config.OutputConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler for single-row output (entity.txt) — one row per XML file
 */
public class SingleRowHandler implements XmlCallback {

    private final OutputConfig outputConfig;
    private final Path outputDir;
    private final String timestamp;
    private final AtomicInteger fileCounter = new AtomicInteger(0);
    
    private BufferedWriter writer;
    private Path currentOutputFile;
    private String[] currentRow;
    private String currentFileName;
    
    // Map from resolved path -> FieldMapping for quick lookup
    private final Map<String, FieldMapping> pathToField = new HashMap<>();
    
    // Track current element path during parsing
    private final StringBuilder currentPath = new StringBuilder();
    private int currentDepth = 0;

    public SingleRowHandler(OutputConfig outputConfig, Path outputDir) {
        this.outputConfig = outputConfig;
        this.outputDir = outputDir;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        this.currentRow = new String[outputConfig.getFields().size()];
        
        // Build path lookup
        for (FieldMapping fm : outputConfig.getFields()) {
            pathToField.put(fm.getPath(), fm);
        }
    }

    @Override
    public boolean handle(XmlEvent e) {
        switch (e.event) {
            case FILE_BEGIN:
                onFileBegin(e);
                break;
            case TAG:
                onTag(e);
                break;
            case ATTR:
                onAttr(e);
                break;
            case INNER:
                onInner(e);
                break;
            case END:
                onEnd(e);
                break;
            case FILE_END:
                onFileEnd(e);
                break;
            case ERROR:
                onError(e);
                break;
        }
        return true; // continue parsing
    }

    private void onFileBegin(XmlEvent e) {
        currentFileName = e.filenameAsString();
        // Reset row
        for (int i = 0; i < currentRow.length; i++) {
            currentRow[i] = "";
        }
        currentDepth = 0;
        currentPath.setLength(0);
    }

    private void onTag(XmlEvent e) {
        String localName = e.valueAsString(); // tag name is in value for TAG event
        if (localName == null) return;
        
        if (currentDepth == 0) {
            currentPath.setLength(0);
            currentPath.append('/').append(localName);
        } else {
            currentPath.append('/').append(localName);
        }
        currentDepth++;
    }

    private void onAttr(XmlEvent e) {
        String attrName = e.attrNameAsString();
        String attrValue = e.valueAsString();
        
        // Build path with attribute: /path/to/element@attrName
        String pathWithAttr = currentPath + "@" + attrName;
        
        FieldMapping fm = pathToField.get(pathWithAttr);
        if (fm != null && fm.getType() == FieldMapping.ExtractType.ATTRIBUTE) {
            currentRow[fm.getColumnIndex()] = escapeCsv(attrValue);
        }
    }

    private void onInner(XmlEvent e) {
        String text = e.valueAsString();
        if (text == null || text.isEmpty()) return;
        
        FieldMapping fm = pathToField.get(currentPath.toString());
        if (fm != null && fm.getType() == FieldMapping.ExtractType.INNER_TEXT) {
            // Append to existing (INNER may fire multiple times)
            String existing = currentRow[fm.getColumnIndex()];
            currentRow[fm.getColumnIndex()] = existing + text;
        }
    }

    private void onEnd(XmlEvent e) {
        currentDepth--;
        // Remove last path component
        int lastSlash = currentPath.lastIndexOf("/");
        if (lastSlash > 0) {
            currentPath.setLength(lastSlash);
        } else {
            currentPath.setLength(0);
        }
    }

    private void onFileEnd(XmlEvent e) {
        writeRow();
    }

    private void onError(XmlEvent e) {
        // Error handling - could write to failed list
    }

    private void writeRow() {
        try {
            ensureWriter();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < currentRow.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(currentRow[i]);
            }
            sb.append('\n');
            writer.write(sb.toString());
            fileCounter.incrementAndGet();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write CSV row", ex);
        }
    }

    private void ensureWriter() throws IOException {
        if (writer == null) {
            String baseName = outputConfig.getFileName().replace(".txt", "");
            String fileName = String.format("%s_%s_%02d.txt", baseName, timestamp, fileCounter.get() + 1);
            currentOutputFile = outputDir.resolve(fileName);
            writer = Files.newBufferedWriter(currentOutputFile);
            
            // Write header
            StringBuilder header = new StringBuilder();
            List<FieldMapping> fields = outputConfig.getFields();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) header.append(',');
                header.append(fields.get(i).getPath());
            }
            header.append('\n');
            writer.write(header.toString());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {}
    }

    public int getRowCount() {
        return fileCounter.get();
    }

    public Path getOutputFile() {
        return currentOutputFile;
    }
}