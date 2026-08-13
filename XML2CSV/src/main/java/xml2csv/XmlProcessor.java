package xml2csv;

import xmlFastParser.xmlBluePrint;
import xmlFastParser.xmlBluePrintCall;
import xmlFastParser.xmlBluePrintHolder;
import xmlFastParser.xmlBluePrintNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XML Processor that wraps xmlBluePrint for multi-entity CSV extraction.
 * Handles routing events to correct CSV writers based on entity path.
 */
public class XmlProcessor implements xmlBluePrintCall {

    private final BPFileConfig config;
    private final CSVWriterManager csvManager;
    private final LogManager logManager;
    private final BackupRenamer backupRenamer;
    private final int threadCount;

    // Runtime state
    private xmlBluePrint blueprint;
    private final Map<String, EntityContext> entityContexts = new ConcurrentHashMap<>();
    private final Map<Long, String> targetHashToOutputFile = new ConcurrentHashMap<>();
    private final Map<Long, String> targetHashToEntityPath = new ConcurrentHashMap<>();

    // Current processing context (thread-local via holder)
    private static final ThreadLocal<ProcessingContext> threadContext = ThreadLocal.withInitial(ProcessingContext::new);

    public XmlProcessor(BPFileConfig config, CSVWriterManager csvManager,
                        LogManager logManager, BackupRenamer backupRenamer, int threadCount) {
        this.config = config;
        this.csvManager = csvManager;
        this.logManager = logManager;
        this.backupRenamer = backupRenamer;
        this.threadCount = threadCount;
    }

    /**
     * Initialize xmlBluePrint with all registered paths.
     */
    public void initialize() {
        // Create blueprint instance
        this.blueprint = new xmlBluePrint();

        // Register root handler
        blueprint.rootRegist(this, "ROOT_HANDLER");
        System.out.println("DEBUG: Called rootRegist");

        // Register error handler
        blueprint.errorRegist(this, "ERROR_HANDLER");

        // Set thread count
        blueprint.setThreadCount(threadCount);
    }

    /**
     * Register all paths with xmlBluePrint.
     * Must be called after initialize() and before processing.
     */
    public void registerPaths() {
        for (BPCsvOutput output : config.getOutputs()) {
            for (BPEntity entity : output.getEntities()) {
                String entityPath = entity.getEntityPath();

                // Register the entity path as target
                // The handler will be this XmlProcessor (xmlBluePrintCall)
                // The token identifies output file and entity path
                String token = output.getFileName() + "|" + entityPath;

                blueprint.regist(entityPath, this, token);

                // Also register all field paths as children of the target
                for (BPField field : entity.getFields()) {
                    String fieldPath = field.getFullPath();
                    // Field paths are children of the entity target
                    // We don't need separate handlers for fields - we capture values
                    // in the target's handler based on path matching
                }

                // Map target hash for quick lookup during parsing
                // We'll compute the hash of the last segment (target tag name)
                String targetTag = getLastSegment(entityPath);
                byte[] tagBytes = targetTag.getBytes();
                long tagHash = xmlBluePrint.hash(tagBytes, tagBytes.length);
                targetHashToOutputFile.put(tagHash, output.getFileName());
                targetHashToEntityPath.put(tagHash, entityPath);
            }
        }
    }

    /**
     * Process a single XML source.
     */
    public void processSource(XmlSource source) throws IOException {
        byte[] data;
        String fileName = source.getRelativePath();

        if (source.isFile()) {
            data = java.nio.file.Files.readAllBytes(source.getPath());
        } else {
            // ZIP entry - read from temp extracted file
            data = java.nio.file.Files.readAllBytes(source.getTempExtractedPath());
            fileName = source.getRelativePath(); // Includes zip path + entry name
        }

        // Log read
        logManager.logRead(fileName);
        System.out.println("DEBUG: Completed processSource for file=" + fileName);

        // Submit job to blueprint
        byte[] nameBytes = fileName.getBytes();
        boolean submitted = false;
        System.out.println("DEBUG: About to submit job, data length=" + data.length);
        int retries = 0;

        while (!submitted && retries < 100) {
            submitted = blueprint.pushJob(data, data.length, nameBytes, nameBytes.length);
            System.out.println("DEBUG: pushJob returned=" + submitted);
            if (!submitted) {
                blueprint.run();
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                retries++;
            }
        }

        if (!submitted) {
            throw new IOException("Failed to submit job after retries: " + fileName);
        }
    }

    /**
     * Wait for all jobs to complete and shutdown.
     */
    public void waitAndShutdown() {
        System.out.println("DEBUG: waitAndShutdown called");
        System.out.println("DEBUG: About to call blueprint.run() in waitAndShutdown");
        blueprint.run(); // Ensure all jobs are distributed
        System.out.println("DEBUG: Called blueprint.run() in waitAndShutdown");
        System.out.println("DEBUG: About to call blueprint.run() in waitAndShutdown");
        System.out.println("DEBUG: Called blueprint.run() in waitAndShutdown");
        System.out.println("DEBUG: Called blueprint.run() in waitAndShutdown");
        System.out.println("DEBUG: Called blueprint.run() in waitAndShutdown");

        // Wait for queue to drain
        while (blueprint.jobQueSpace() < (blueprint.jobQueSize - 1)) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Graceful shutdown
        blueprint.shutdown(false);
    }

    /**
     * Get the xmlBluePrint instance for queue status checks.
     */
    public xmlBluePrint getBlueprint() {
        return blueprint;
    }

    // ============================================================
    // xmlBluePrintCall implementation
    // ============================================================

    @Override
    public boolean call(Object idToken, xmlBluePrintHolder holder, int event,
                        int nameBegin, int nameEnd, int valueBegin, int valueEnd) {

        // Handle different tokens
        if ("ROOT_HANDLER".equals(idToken)) {
            return handleRootEvent(holder, event);
        }
        else if ("ERROR_HANDLER".equals(idToken)) {
            return handleErrorEvent(holder, event, nameBegin, nameEnd, valueBegin, valueEnd);
        }
        else {
            // Entity target handler - token format: "outputFileName|entityPath"
            return handleEntityEvent(idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd);
        }
    }

    private boolean handleRootEvent(xmlBluePrintHolder holder, int event) {
        // Root open/close - we can use for initialization if needed
        return true;
    }

    private boolean handleErrorEvent(xmlBluePrintHolder holder, int event,
                                     int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        String errorMsg = "XML Parse Error: event=" + event + ", pos=" + holder.getErrorLocation();
        System.err.println(errorMsg);
        return true; // Continue processing
    }

    private boolean handleEntityEvent(Object idToken, xmlBluePrintHolder holder, int event,
                                      int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        String token = (String) idToken;
        String[] parts = token.split("\\|", 2);
        if (parts.length != 2) {
            return true;
        }

        String outputFileName = parts[0];
        String entityPath = parts[1];

        EntityContext ctx = entityContexts.get(entityPath);
        if (ctx == null) {
            return true;
        }

        ProcessingContext pctx = threadContext.get();
        int threadNo = holder.getThreadNO();

        switch (event) {
            case xmlBluePrint.EV_OPEN_TAG: // 1
                return handleOpenTag(ctx, pctx, holder, outputFileName, threadNo, nameBegin, nameEnd);

            case xmlBluePrint.EV_ATTR: // 3
                return handleAttribute(ctx, pctx, holder, outputFileName, threadNo, nameBegin, nameEnd, valueBegin, valueEnd);

            case xmlBluePrint.EV_INNER_TEXT: // 4
                return handleInnerText(ctx, pctx, holder, outputFileName, threadNo, nameBegin, nameEnd, valueBegin, valueEnd);

            case xmlBluePrint.EV_CLOSE_TAG: // 2
                return handleCloseTag(ctx, pctx, holder, outputFileName, threadNo);
        }

        return true;
    }

    private boolean handleOpenTag(EntityContext ctx, ProcessingContext pctx, xmlBluePrintHolder holder,
                                  String outputFileName, int threadNo, int nameBegin, int nameEnd) {
        // Initialize new row data
        pctx.currentRow = new ArrayList<>(Collections.nCopies(ctx.getFieldCount(), ""));
        pctx.currentXmlFilePath = holder.getXmlName() != null ?
                new String(holder.getXmlName(), 0, holder.getXmlNameLength()) : "unknown";
        pctx.expectedFieldIndex = 0;

        // Get the xmlFilePath (relative path)
        String xmlFilePath = pctx.currentXmlFilePath;

        // The first column is xmlFilePath, we'll write it at close tag
        return true;
    }

    private boolean handleAttribute(EntityContext ctx, ProcessingContext pctx, xmlBluePrintHolder holder,
                                    String outputFileName, int threadNo,
                                    int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        if (pctx.currentRow == null) return true;

        // Extract attribute name and value
        byte[] data = holder.getByteBuffer();
        if (data == null) return true;

        String attrName = new String(data, nameBegin, nameEnd - nameBegin);
        String attrValue = new String(data, valueBegin, valueEnd - valueBegin);

        // Find matching field in this entity
        for (int i = 0; i < ctx.fields.size(); i++) {
            BPField field = ctx.fields.get(i);
            if (field.getType() == BPField.FieldType.ATTRIBUTE && field.getFieldName().equals(attrName)) {
                // Found matching attribute field
                if (i < pctx.currentRow.size()) {
                    pctx.currentRow.set(i, attrValue);
                }
                break;
            }
        }

        return true;
    }

    private boolean handleInnerText(EntityContext ctx, ProcessingContext pctx, xmlBluePrintHolder holder,
                                    String outputFileName, int threadNo,
                                    int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        if (pctx.currentRow == null) return true;

        // Extract element name and inner text
        byte[] data = holder.getByteBuffer();
        if (data == null) return true;

        String elementName = new String(data, nameBegin, nameEnd - nameBegin);
        String innerText = new String(data, valueBegin, valueEnd - valueBegin);

        // Find matching field in this entity
        for (int i = 0; i < ctx.fields.size(); i++) {
            BPField field = ctx.fields.get(i);
            if (field.getType() == BPField.FieldType.INNER_TEXT && field.getFieldName().equals(elementName)) {
                // Found matching inner text field
                if (i < pctx.currentRow.size()) {
                    pctx.currentRow.set(i, innerText);
                }
                break;
            }
        }

        return true;
    }

    private boolean handleCloseTag(EntityContext ctx, ProcessingContext pctx, xmlBluePrintHolder holder,
                                   String outputFileName, int threadNo) {
        if (pctx.currentRow == null) return true;

        // Write row to CSV
        csvManager.writeRow(outputFileName, threadNo, pctx.currentXmlFilePath, pctx.currentRow);

        // Clear row for next entity occurrence
        pctx.currentRow = null;

        return true;
    }

    // ============================================================
    // Helper classes
    // ============================================================

    private static class EntityContext {
        final String outputFileName;
        final String entityPath;
        final List<BPField> fields;
        final int fieldCount;

        EntityContext(String outputFileName, String entityPath) {
            this.outputFileName = outputFileName;
            this.entityPath = entityPath;
            // Fields will be populated from config
            this.fields = new ArrayList<>();
            this.fieldCount = 0;
        }

        void setFields(List<BPField> fields) {
            this.fields.clear();
            this.fields.addAll(fields);
        }

        int getFieldCount() { return fields.size(); }
    }

    private static class ProcessingContext {
        List<String> currentRow;
        String currentXmlFilePath;
        int expectedFieldIndex;
    }

    private String getLastSegment(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
}