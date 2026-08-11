package xml2csv.dispatcher;

import XMLFastReader.XmlCallback;
import XMLFastReader.XmlEvent;
import XMLFastReader.XmlFastReader;
import XMLFastReader.ParseHandle;
import xml2csv.config.OutputConfig;
import xml2csv.config.X2CConfig;
import xml2csv.handler.MultiRowHandler;
import xml2csv.handler.SingleRowHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main dispatcher that coordinates XMLFastReader with X2C config.
 * Creates handlers for each output file and delegates events to them.
 */
public class Xml2CsvDispatcher implements XmlCallback {

    private final X2CConfig config;
    private final Path outputDir;
    private final List<XmlCallback> handlers = new ArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    
    // Success/failed file writers
    private java.io.BufferedWriter successWriter;
    private java.io.BufferedWriter failedWriter;
    private final String timestamp;

    public Xml2CsvDispatcher(X2CConfig config, Path outputDir) throws IOException {
        this.config = config;
        this.outputDir = outputDir;
        this.timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        
        // Create output directory
        Files.createDirectories(outputDir);
        
        // Initialize success/failed log writers
        initLogWriters();
        
        // Create handlers for each output config
        for (OutputConfig outputConfig : config.getOutputs()) {
            XmlCallback handler;
            if (outputConfig.isMultiRow()) {
                handler = new MultiRowHandler(outputConfig, outputDir);
            } else {
                handler = new SingleRowHandler(outputConfig, outputDir);
            }
            handlers.add(handler);
        }
    }

    private void initLogWriters() throws IOException {
        String successFile = config.getSuccessFilePattern().replace(".txt", "_" + timestamp + "_01.txt");
        String failedFile = config.getFailedFilePattern().replace(".txt", "_" + timestamp + "_01.txt");
        
        successWriter = Files.newBufferedWriter(outputDir.resolve(successFile));
        failedWriter = Files.newBufferedWriter(outputDir.resolve(failedFile));
    }

    /**
     * Process a single XML or ZIP file.
     */
    public ParseHandle processFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        
        // Create XmlFastReader with this dispatcher as callback
        XmlFastReader reader = new XmlFastReader();
        reader.setCallback(this);
        
        // Parse file
        ParseHandle handle;
        if (fileName.toLowerCase().endsWith(".zip")) {
            handle = reader.parseZip(file.toFile());
        } else {
            handle = reader.parseFile(file.toFile());
        }
        
        // Wait for completion
        handle.waitUntilDone();
        
        // Check for errors
        if (handle.hasErrors()) {
            failCount.incrementAndGet();
            failedWriter.write(fileName);
            failedWriter.newLine();
            failedWriter.flush();
        } else {
            successCount.incrementAndGet();
            successWriter.write(fileName);
            successWriter.newLine();
            successWriter.flush();
        }
        
        // Close handlers
        for (XmlCallback h : handlers) {
            if (h instanceof SingleRowHandler) {
                ((SingleRowHandler) h).close();
            } else if (h instanceof MultiRowHandler) {
                ((MultiRowHandler) h).close();
            }
        }
        
        return handle;
    }

    /**
     * Process all XML/ZIP files in a directory.
     */
    public void processDirectory(Path dir, int threads) throws IOException, InterruptedException {
        Files.walk(dir)
                .filter(p -> p.toString().endsWith(".xml") || p.toString().endsWith(".zip"))
                .forEach(p -> {
                    try {
                        processFile(p);
                    } catch (IOException e) {
                        failCount.incrementAndGet();
                        try {
                            failedWriter.write(p.getFileName().toString());
                            failedWriter.newLine();
                            failedWriter.flush();
                        } catch (IOException ex) {
                            // ignore
                        }
                    }
                });
    }

    // XmlCallback implementation - delegate to all handlers
    @Override
    public boolean handle(XmlEvent e) {
        for (XmlCallback h : handlers) {
            h.handle(e);
        }
        return true; // continue parsing
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailCount() {
        return failCount.get();
    }

    public void close() throws IOException {
        if (successWriter != null) successWriter.close();
        if (failedWriter != null) failedWriter.close();
    }
}