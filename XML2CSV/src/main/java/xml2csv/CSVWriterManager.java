package xml2csv;

import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages CSV writers per output file and per thread.
 * Each thread writes to its own CSV file (no synchronization needed).
 * Files start as *_pending.csv and are renamed on finalize.
 */
public class CSVWriterManager {

    private final Path destDir;
    private final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private String timestamp;

    // Key: outputFileName + "_" + threadNo
    private final Map<String, CSVWriter> writers = new ConcurrentHashMap<>();
    private final Map<String, Path> pendingPaths = new ConcurrentHashMap<>();
    private final Map<String, List<String>> headers = new ConcurrentHashMap<>();

    public CSVWriterManager(Path destDir) {
        this.destDir = destDir.toAbsolutePath().normalize();
        this.timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
    }

    /**
     * Initialize writers for all outputs and threads.
     * @param config BPFileConfig with output definitions
     * @param threadCount Number of threads
     */
    public void initialize(BPFileConfig config, int threadCount) throws IOException {
        for (BPCsvOutput output : config.getOutputs()) {
            String fileName = output.getFileName();
            List<String> header = buildHeader(output);

            // Store header for this output
            headers.put(fileName, header);

            // Create writer for each thread
            for (int threadNo = 0; threadNo < threadCount; threadNo++) {
                String key = fileName + "_" + threadNo;
                Path pendingPath = destDir.resolve(fileName + "_Thread" + threadNo + "_pending.csv");

                BufferedWriter bw = Files.newBufferedWriter(pendingPath, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                CSVWriter writer = new CSVWriter(bw,
                        ICSVWriter.DEFAULT_SEPARATOR,
                        ICSVWriter.DEFAULT_QUOTE_CHARACTER,
                        ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        ICSVWriter.DEFAULT_LINE_END);

                // Write header
                writer.writeNext(header.toArray(new String[0]));
                writer.flush();

                writers.put(key, writer);
                pendingPaths.put(key, pendingPath);
            }
        }
    }

    private List<String> buildHeader(BPCsvOutput output) {
        List<String> header = new ArrayList<>();
        header.add("xmlFilePath"); // Column 1: always the XML file path

        // Add field names in order
        for (BPEntity entity : output.getEntities()) {
            for (BPField field : entity.getFields()) {
                header.add(field.getFieldName());
            }
        }

        return header;
    }

    /**
     * Get CSV writer for specific output file and thread.
     */
    public CSVWriter getWriter(String outputFileName, int threadNo) {
        return writers.get(outputFileName + "_" + threadNo);
    }

    /**
     * Write a row to the appropriate CSV file.
     * @param outputFileName The file: name from .bp
     * @param threadNo Thread number (from holder.getThreadNO())
     * @param xmlFilePath Relative path of XML file
     * @param values Field values in order matching header (excluding xmlFilePath)
     */
    public void writeRow(String outputFileName, int threadNo, String xmlFilePath, List<String> values) {
        CSVWriter writer = getWriter(outputFileName, threadNo);
        if (writer == null) {
            System.err.println("Warning: No writer for " + outputFileName + " thread " + threadNo);
            return;
        }

        List<String> row = new ArrayList<>(values.size() + 1);
        row.add(xmlFilePath);
        row.addAll(values);

        try {
            writer.writeNext(row.toArray(new String[0]));
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error writing CSV row: " + e.getMessage());
        }
    }

    /**
     * Finalize all writers: close and rename *_pending.csv to timestamped names.
     */
    public void finalizeAll() {
        for (Map.Entry<String, CSVWriter> entry : writers.entrySet()) {
            String key = entry.getKey();
            CSVWriter writer = entry.getValue();
            Path pendingPath = pendingPaths.get(key);

            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing writer " + key + ": " + e.getMessage());
            }

            // Rename pending file
            if (pendingPath != null && Files.exists(pendingPath)) {
                renamePending(pendingPath, key);
            }
        }
        writers.clear();
        pendingPaths.clear();
    }

    /**
     * Finalize writers for a specific thread.
     */
    public void finalizeThread(int threadNo) {
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, CSVWriter> entry : writers.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_" + threadNo)) {
                CSVWriter writer = entry.getValue();
                Path pendingPath = pendingPaths.get(key);

                try {
                    if (writer != null) {
                        writer.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error closing writer " + key + ": " + e.getMessage());
                }

                if (pendingPath != null && Files.exists(pendingPath)) {
                    renamePending(pendingPath, key);
                }

                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            writers.remove(key);
            pendingPaths.remove(key);
        }
    }

    private void renamePending(Path pendingPath, String key) {
        // key format: fileName_threadNo
        int lastUnderscore = key.lastIndexOf('_');
        String fileName = key.substring(0, lastUnderscore);

        String timestampedName = fileName + "_Thread" + key.substring(lastUnderscore + 1) + "_" + timestamp + ".csv";
        Path targetPath = destDir.resolve(timestampedName);

        int counter = 1;
        while (Files.exists(targetPath)) {
            targetPath = destDir.resolve(fileName + "_Thread" + key.substring(lastUnderscore + 1) + "_" + timestamp + "_" + counter + ".csv");
            counter++;
        }

        try {
            Files.move(pendingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error renaming CSV file " + pendingPath + ": " + e.getMessage());
        }
    }

    public String getTimestamp() {
        return timestamp;
    }
}