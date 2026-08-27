import java.io.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.text.SimpleDateFormat;

/**
 * XML2TXT - Convert XML files to tab-separated text using xmlBluePrint library
 *
 * Usage: java XML2TXT -p <blueprint.bp> -s <source_folder> -d <dest_folder> -t <threads>
 *
 * Phases:
 *  1. BootUp: Initialize parser, parse blueprint, register handlers, prepare writers
 *  2. MainLoop: Process XML files through parser pipeline
 *  3. ClosingJob: Close writers, rename _pending files, shutdown parser
 */
public class XML2TXT {

    // ============================================================
    // Configuration (from CLI args)
    // ============================================================
    private String pathBP = "BluePrint.bp";
    private int numThreads = 1;
    private int pipeN = 2;
    private String pathSource = ".";
    private String pathDest = ".";

    // ============================================================
    // Core Components
    // ============================================================
    private xmlBluePrint parser;
    private SourceHandler source;
    private HashMap<Long, TokenEntity> allTokenEntity;
    private TokenRoot tokenRoot;
    private HashMap<Long, PrintWriter> allEntityOutput;
    private PrintWriter errorLogWriter;
    private XmlToken[] allXmlToken;
    private int xmlRead = 0;
    private int xmlReturn = 0;

    // Pipeline config
    private int C_PipeLineDeep = 2;

    public static void main(String[] args) {
        XML2TXT app = new XML2TXT();
        app.parseArgs(args);
        app.run();
    }

    // ============================================================
    // CLI Argument Parsing
    // ============================================================
    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p":
                    if (i + 1 < args.length) pathBP = args[++i];
                    break;
                case "-t":
                    if (i + 1 < args.length) numThreads = Integer.parseInt(args[++i]);
                    break;
                case "-N":
                    if (i + 1 < args.length) pipeN = Integer.parseInt(args[++i]);
                    if (pipeN < 2) pipeN = 2;
                    if (pipeN > 16) pipeN = 16;
                    C_PipeLineDeep = pipeN;
                    break;
                case "-s":
                    if (i + 1 < args.length) pathSource = args[++i];
                    break;
                case "-d":
                    if (i + 1 < args.length) pathDest = args[++i];
                    break;
            }
        }
        System.out.println("Config: bp=" + pathBP + ", threads=" + numThreads + ", src=" + pathSource + ", dest=" + pathDest);
    }

    // ============================================================
    // Main Entry Point
    // ============================================================
    public void run() {
        try {
            bootUp();
            mainLoop();
            closingJob();
        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ============================================================
    // Phase 1: BootUp - Initialize everything
    // ============================================================
    private void bootUp() throws Exception {
        // Reset counters
        xmlRead = 0;
        xmlReturn = 0;

        // Create parser
        parser = new xmlBluePrint();

        // Tune parser
        parser.setPipeLineDeep(C_PipeLineDeep);

        // Set thread count
        parser.setThreadCount(numThreads);

        // Parse .bp file
        System.out.println("Parsing blueprint: " + pathBP);
        allTokenEntity = BPParser.parseBP(pathBP, numThreads);
        System.out.println("Parsed " + allTokenEntity.size() + " entities");

        // Create TokenRoot
        tokenRoot = new TokenRoot(Thread.currentThread(), allTokenEntity);

        // Create handlers
        // (Using EventHandlers class static instances)

        // Register handlers with parser
        parser.rootRegist(EventHandlers.HD_Root, tokenRoot);
        parser.errorRegist(EventHandlers.HD_Error, tokenRoot);

        for (TokenEntity entity : allTokenEntity.values()) {
            if (entity.path != null && entity.path.length() > 0) {
                parser.regist(entity.path, EventHandlers.HD_Entity, entity);
            }
            for (TokenColumn column : entity.AllTokebColumn.values()) {
                // Register column handler at its specific path, not entity path
                if (column.path != null && column.path.length() > 0) {
                    parser.regist(column.path, EventHandlers.HD_Column, column);
                }
            }
        }

        // Create SourceHandler
        System.out.println("Initializing source handler: " + pathSource);
        source = new SourceHandler(pathSource);

        // Prepare allEntityOutput - create _pending.txt files in dest folder
        allEntityOutput = new HashMap<>();
        File destDir = new File(pathDest);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        for (TokenEntity entity : allTokenEntity.values()) {
            File pendingFile = new File(destDir, entity.fileName + "_pending.txt");
            PrintWriter writer = new PrintWriter(new FileWriter(pendingFile));
            allEntityOutput.put(xmlBluePrint.hash(entity.fileName.getBytes(), entity.fileName.getBytes().length), writer);
        }

        // Prepare error log writer
        File errFile = new File(destDir, "xml2txt.err");
        errorLogWriter = new PrintWriter(new FileWriter(errFile, true)); // append mode

        // Prepare pipeline tokens
        int totalSizePipeLine = parser.getPipeLineTotalSlot();
        allXmlToken = new XmlToken[totalSizePipeLine];
        for (int i = 0; i < totalSizePipeLine; i++) {
            allXmlToken[i] = new XmlToken();
        }

        // Start parser
        if (!parser.run()) {
            throw new RuntimeException("Failed to start parser (root handler not registered)");
        }

        System.out.println("BootUp complete. Pipeline slots: " + totalSizePipeLine + ", Threads: " + numThreads);
    }

    // ============================================================
    // Phase 2: MainLoop - Process XML files
    // ============================================================
    private void mainLoop() throws Exception {
        XmlPackage xml;
        XmlToken xmlToken;

        // Phase 2a: Fill pipeline with initial tokens
        System.out.println("Phase 2a: Filling pipeline...");
        while (xmlRead < allXmlToken.length && (xml = source.getXml()) != null) {
            allXmlToken[xmlRead].xml = xml;
            parser.pushJob(xml.byteBuffer, xml.size, allXmlToken[xmlRead]);
            xmlRead++;
        }
        System.out.println("Initial pipeline fill: " + xmlRead + " jobs");

        // Phase 2b: Reuse tokens - process remaining files
        System.out.println("Phase 2b: Processing files...");
        String prevFile = null;
        while ((xml = source.getXml()) != null) {
            // New physical file -> new bar line
            if (prevFile != null && (xml.name == null || !prevFile.equals(xml.name))) {
                // file changed
            }
            prevFile = xml.name;
            // Wait for completed job
            while ((xmlToken = (XmlToken) parser.pullJob()) == null) {
                LockSupport.parkNanos(10_000_000L); // 10ms
            }
            xmlReturn++;

            // Flush data to output files
            flushXmlToken(xmlToken);

            // Close source XML
            source.closeXml(xmlToken.xml);

            // Reuse token with new XML
            xmlToken.xml = xml;
            parser.pushJob(xml.byteBuffer, xml.size, xmlToken);
            xmlRead++;
        }

        // Phase 2c: Drain pipeline - process remaining jobs
        System.out.println("Phase 2c: Draining pipeline...");
        while (xmlReturn < xmlRead) {
            while ((xmlToken = (XmlToken) parser.pullJob()) == null) {
                LockSupport.parkNanos(10_000_000L); // 10ms
            }
            xmlReturn++;

            // Flush data
            flushXmlToken(xmlToken);

            // Close source XML
            source.closeXml(xmlToken.xml);
        }

        System.out.println();
        System.out.println("MainLoop complete. Total read: " + xmlRead + ", returned: " + xmlReturn);
    }

    private int flushBatchCount = 0;

    private void flushXmlToken(XmlToken xmlToken) {
        if (xmlToken.msgError != null) {
            errorLogWriter.println(xmlToken.msgError);
            // batch error log flush every 1000 too if desired
        } else {
            for (java.util.Map.Entry<Long, String> entry : xmlToken.buffOP.entrySet()) {
                PrintWriter writer = allEntityOutput.get(entry.getKey());
                if (writer != null) {
                    writer.println(entry.getValue());
                    // Batch disk flush: only every 1000 xml, plus final at close
                    flushBatchCount++;
                    if (flushBatchCount % 250 == 0) {
                        writer.flush();
                    }
// bar removed
                }
            }
        }
        xmlToken.msgError = null;
        xmlToken.buffOP.clear();
    }

    // ============================================================
    // Phase 3: ClosingJob - Cleanup
    // ============================================================
    private void closingJob() {
        System.out.println("ClosingJob: Finalizing...");

        // 1. Close all entity output writers
        for (PrintWriter writer : allEntityOutput.values()) {
            writer.close();
        }
        allEntityOutput.clear();

        // 2. Flush any remaining batched output (<1000) before rename
        for (PrintWriter writer : allEntityOutput.values()) {
            writer.flush();
        }

        // 3. Rename _pending.txt to _YYYYMMDDHHmmss.txt
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File destDir = new File(pathDest);

        for (TokenEntity entity : allTokenEntity.values()) {
            File oldFile = new File(destDir, entity.fileName + "_pending.txt");
            File newFile = new File(destDir, entity.fileName + "_" + timestamp + ".txt");
            if (oldFile.exists()) {
                if (oldFile.renameTo(newFile)) {
                    System.out.println("Renamed: " + oldFile.getName() + " -> " + newFile.getName());
                } else {
// rename error removed
                }
            }
        }

        // 3. Close error log
        if (errorLogWriter != null) {
            errorLogWriter.close();
        }

        // 4. Shutdown parser
        if (parser != null) {
            parser.shutdown(true);
            parser = null;
        }

        System.out.println("ClosingJob complete.");
    }
}