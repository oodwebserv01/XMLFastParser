package xml2csv.handler;

import XMLFastReader.XmlCallback;
import XMLFastReader.XmlEvent;
import XMLFastReader.XmlNode;
import xml2csv.config.FieldMapping;
import xml2csv.config.OutputConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base handler for writing CSV output.
 * Handles both single-row (entity) and multi-row (product) outputs.
 */
public abstract class CsvOutputHandler implements XmlCallback {

    protected final OutputConfig outputConfig;
    protected final Path outputDir;
    protected final String timestamp;
    protected final String runId;
    
    protected BufferedWriter writer;
    protected final List<String> columnHeaders = new ArrayList<>();
    protected final StringBuilder rowBuffer = new StringBuilder();
    protected final AtomicInteger rowCount = new AtomicInteger(0);
    
    // For multi-row: track current row data
    protected final List<String> currentRow = new ArrayList<>();
    protected boolean inBodyElement = false;
    protected int bodyDepth = 0;

    public CsvOutputHandler(OutputConfig outputConfig, Path outputDir) {
        this.outputConfig = outputConfig;
        this.outputDir = outputDir;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        this.runId = String.format("%02d", 1); // TODO: increment if file exists
        
        // Build column headers from field mappings (in columnIndex order)
        FieldMapping[] sortedFields = outputConfig.getFields().stream()
                .sorted((a, b) -> Integer.compare(a.getColumnIndex(), b.getColumnIndex()))
                .toArray(FieldMapping[]::new);
        for (FieldMapping fm : sortedFields) {
            columnHeaders.add(fm.getPath()); // Use path as header for now
        }
    }

    /**
     * Initialize the output file and write header.
     */
    public void initialize() throws IOException {
        String baseName = outputConfig.getFileName().replace(".txt", "");
        String fileName = String.format("%s_%s_%s.txt", baseName, timestamp, runId);
        Path outputPath = outputDir.resolve(fileName);
        
        writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
        
        // Write header
        writer.write(String.join(",", columnHeaders));
        writer.newLine();
        writer.flush();
    }

    /**
     * Called when parsing starts for a file (FILE_BEGIN event).
     */
    @Override
    public void onFileBegin(XmlEvent event) {
        // Reset per-file state
        rowCount.set(0);
        currentRow.clear();
        inBodyElement = false;
        bodyDepth = 0;
    }

    /**
     * Called when parsing ends for a file (FILE_END event).
     */
    @Override
    public void onFileEnd(XmlEvent event) {
        flushRow();
        close();
    }

    /**
     * Called on error.
     */
    @Override
    public void onError(XmlEvent event) {
        close();
    }

    /**
     * Main callback entry point - delegates to specific event handlers.
     */
    @Override
    public boolean handle(XmlEvent e) {
        switch (e.event) {
            case FILE_BEGIN:
                onFileBegin(e);
                break;
            case FILE_END:
                onFileEnd(e);
                break;
            case ERROR:
                onError(e);
                break;
            case TAG:
                onStartElement(e);
                break;
            case END:
                onEndElement(e);
                break;
            case ATTR:
                onAttribute(e);
                break;
            case INNER:
                onInnerText(e);
                break;
        }
        return true; // continue parsing
    }

    /**
     * Called when a start element is encountered.
     */
    public void onStartElement(XmlEvent event) {
        // Override in subclasses
    }

    /**
     * Called when an end element is encountered.
     */
    public void onEndElement(XmlEvent event) {
        // Override in subclasses
    }

    /**
     * Called when an attribute is encountered.
     */
    public void onAttribute(XmlEvent event) {
        // Override in subclasses
    }

    /**
     * Called when inner text is encountered.
     */
    public void onInnerText(XmlEvent event) {
        // Override in subclasses
    }

    /**
     * Write current row to CSV.
     */
    protected void flushRow() {
        if (currentRow.isEmpty()) return;
        
        try {
            // Ensure row has correct number of columns
            while (currentRow.size() < columnHeaders.size()) {
                currentRow.add("");
            }
            
            writer.write(String.join(",", currentRow));
            writer.newLine();
            writer.flush();
            rowCount.incrementAndGet();
            currentRow.clear();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV row", e);
        }
    }

    /**
     * Add a field value to current row at the correct column index.
     */
    protected void setFieldValue(int columnIndex, String value) {
        while (currentRow.size() <= columnIndex) {
            currentRow.add("");
        }
        currentRow.set(columnIndex, escapeCsv(value));
    }

    /**
     * Escape value for CSV (wrap in quotes if contains comma, quote, newline).
     */
    protected String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Close the writer.
     */
    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public int getRowCount() {
        return rowCount.get();
    }

    public Path getOutputPath() {
        String baseName = outputConfig.getFileName().replace(".txt", "");
        String fileName = String.format("%s_%s_%s.txt", baseName, timestamp, runId);
        return outputDir.resolve(fileName);
    }
}