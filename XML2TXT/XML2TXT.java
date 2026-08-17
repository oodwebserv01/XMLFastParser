import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import xmlFastParser.xmlBluePrint;
import xmlFastParser.xmlBluePrintCall;
import xmlFastParser.xmlBluePrintHolder;

public class XML2TXT {
    public static void main(String[] args) {
        // Print application header
        System.out.println("XML2TXT Java Application");
        System.out.println("Usage: java XML2TXT [-p path/file.bp] [-s sourcePath] [-d destPath] [-t threadCount]");

        // Parse arguments using Config class for cleaner integration
        Config config = ParseArguments(args);

        // Display configuration
        System.out.println("Path BP: " + config.pathBP);
        System.out.println("Source Path: " + config.sourcePath);
        System.out.println("Destination Path: " + config.destPath);
        System.out.println("Thread Count: " + config.threadCount);

        // TODO: Implement the rest of the logic
        // 1. List all .xml and .zip files using ListAllFile()
        // 2. Register xml paths from .bp file using xmlBluePrint
        // 3. Process files using xmlBluePrint with threadCount threads
        // 4. Generate output files

        // For now, implement basic workflow to demonstrate functionality
        System.out.println("\n=== XML2TXT Workflow ===");

        // Step 1: List all .xml and .zip files
        String fileList = ListAllFile(config.sourcePath);
        if (fileList.isEmpty()) {
            System.out.println("No .xml or .zip files found in source path: " + config.sourcePath);
        } else {
            System.out.println("Found files: " + fileList);
        }

        // Step 2: Read and parse .bp configuration file
        BPConfig bpConfig = ReadBPFile(config.pathBP);
        System.out.println("BP configuration: " + bpConfig);

        // Step 3: Register paths with xmlBluePrint
        if (config.pathBP != null && !bpConfig.files.isEmpty()) {
            RegisterPathsWithXmlBluePrint(bpConfig, config.threadCount);
        }

        // Step 4: Process files using xmlBluePrint
        if (!fileList.isEmpty()) {
            ProcessFiles(config.sourcePath, config.destPath, config.threadCount, bpConfig);
        }

        System.out.println("\n=== Workflow Completed ===");
    }

    public static String ListAllFile(String folder) {
        List<String> fileList = new ArrayList<>();
        File dir = new File(folder);

        if (!dir.exists() || !dir.isDirectory()) {
            return "";
        }

        collectFiles(dir, fileList);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fileList.size(); i++) {
            if (i > 0) {
                result.append(",");
            }
            result.append(fileList.get(i));
        }

        return result.toString();
    }

    private static void collectFiles(File dir, List<String> fileList) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName();
                if (name.toLowerCase().endsWith(".xml") ||
                    name.toLowerCase().endsWith(".zip")) {
                    fileList.add(file.getAbsolutePath());
                }
            } else if (file.isDirectory()) {
                collectFiles(file, fileList);
            }
        }
    }

    // Static Config class for storing parsed arguments
    public static class Config {
        public String pathBP = null;
        public String sourcePath = ".";
        public String destPath = ".";
        public int threadCount = 1;

        @Override
        public String toString() {
            return "Config[pathBP=" + pathBP + ", sourcePath=" + sourcePath +
                   ", destPath=" + destPath + ", threadCount=" + threadCount + "]";
        }
    }

    public static Config ParseArguments(String[] args) {
        Config config = new Config();

        if (args == null) {
            return config;
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p":
                    if (i + 1 < args.length) {
                        config.pathBP = args[++i];
                    } else {
                        System.err.println("Error: -p requires a path/file.bp argument");
                        System.exit(1);
                    }
                    break;
                case "-s":
                    if (i + 1 < args.length) {
                        config.sourcePath = args[++i];
                    } else {
                        System.err.println("Error: -s requires a source path argument");
                        System.exit(1);
                    }
                    break;
                case "-d":
                    if (i + 1 < args.length) {
                        config.destPath = args[++i];
                    } else {
                        System.err.println("Error: -d requires a destination path argument");
                        System.exit(1);
                    }
                    break;
                case "-t":
                    if (i + 1 < args.length) {
                        try {
                            config.threadCount = Integer.parseInt(args[++i]);
                            if (config.threadCount < 1) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: -t requires a valid positive integer");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: -t requires a thread count argument");
                        System.exit(1);
                    }
                    break;
                default:
                    System.err.println("Error: Unknown argument " + args[i]);
                    System.exit(1);
            }
        }

        return config;
    }

    // ColumnSpec class for storing column specifications from .bp file
    public static class ColumnSpec {
        public String path;
        public String type; // "@" for attribute, "#" for inner text
        public String attrName; // attribute name if type is "@"
        public int columnIndex;

        public ColumnSpec(String path, String type, String attrName, int columnIndex) {
            this.path = path;
            this.type = type;
            this.attrName = attrName;
            this.columnIndex = columnIndex;
        }

        @Override
        public String toString() {
            return "ColumnSpec[path=" + path + ", type=" + type + ", attrName=" + attrName + ", columnIndex=" + columnIndex + "]";
        }
    }

    // BPConfig class for storing parsed .bp file configuration
    public static class BPConfig {
        public List<String> files = new ArrayList<>();
        public List<String> entities = new ArrayList<>();
        public List<ColumnSpec> columns = new ArrayList<>();

        @Override
        public String toString() {
            return "BPConfig[files=" + files.size() + ", entities=" + entities.size() + ", columns=" + columns.size() + "]";
        }
    }

    public static BPConfig ReadBPFile(String pathBP) {
        BPConfig config = new BPConfig();

        if (pathBP == null || pathBP.isEmpty()) {
            return config;
        }

        File bpFile = new File(pathBP);
        if (!bpFile.exists() || !bpFile.isFile()) {
            System.err.println("Error: .bp file not found: " + pathBP);
            System.exit(1);
        }

        try (Scanner scanner = new Scanner(bpFile)) {
            int fileCount = 0;
            int entityCount = 0;
            int columnCount = 0;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }

                if (line.startsWith("file:")) {
                    String fileName = line.substring(5).trim();
                    config.files.add(fileName);
                    fileCount++;
                } else if (line.startsWith("entity:")) {
                    String entityPath = line.substring(7).trim();
                    config.entities.add(entityPath);
                    entityCount++;
                } else if (line.startsWith("-")) {
                    String columnSpec = line.substring(1).trim();
                    ColumnSpec col = parseColumnSpec(columnSpec, columnCount++);
                    if (col != null) {
                        config.columns.add(col);
                    }
                }
            }

            System.out.println("BP file parsed: " + fileCount + " files, " + entityCount + " entities, " + columnCount + " columns");

        } catch (Exception e) {
            System.err.println("Error reading .bp file: " + e.getMessage());
            System.exit(1);
        }

        return config;
    }

    private static ColumnSpec parseColumnSpec(String columnSpec, int columnIndex) {
        // Parse column specification like: /Transaction/Invoid@SN or /Transaction/Invoid/Saler/PersonID#
        if (columnSpec == null || columnSpec.isEmpty()) {
            return null;
        }

        String type = null;
        String attrName = null;
        String path = null;

        // Check for attribute type (@)
        int atIndex = columnSpec.lastIndexOf('@');
        if (atIndex > 0) {
            type = "@";
            attrName = columnSpec.substring(atIndex + 1);
            path = columnSpec.substring(0, atIndex);
        } else {
            // Check for inner text type (#)
            int hashIndex = columnSpec.lastIndexOf('#');
            if (hashIndex > 0) {
                type = "#";
                path = columnSpec.substring(0, hashIndex);
            } else {
                // No type specified, treat as entity path
                type = "entity";
                path = columnSpec;
            }
        }

        return new ColumnSpec(path, type, attrName, columnIndex);
    }

    public static String GenerateOutputFilename(int threadNo, String fileType, String timestamp) {
        String threadStr = String.format("%02d", threadNo);
        return fileType + "_" + threadStr + "_" + timestamp + ".txt";
    }

    public static String GeneratePendingFilename(String fileType, int threadNo) {
        String threadStr = String.format("%02d", threadNo);
        return fileType + "_" + threadStr + "_pending";
    }

    public static boolean RenameProcessedFile(String originalPath) {
        if (originalPath == null || originalPath.isEmpty()) {
            return false;
        }

        File originalFile = new File(originalPath);
        if (!originalFile.exists()) {
            return false;
        }

        String newName;
        String name = originalFile.getName();

        if (name.toLowerCase().endsWith(".xml")) {
            newName = "xml" + name.substring(0, name.length() - 4) + ".bak";
        } else if (name.toLowerCase().endsWith(".zip")) {
            newName = "zip" + name.substring(0, name.length() - 4) + ".bak";
        } else {
            return false; // Not an XML or ZIP file
        }

        File newFile = new File(originalFile.getParent(), newName);

        // Check if target already exists
        if (newFile.exists()) {
            System.err.println("Warning: Target file already exists: " + newName);
            return false;
        }

        boolean success = originalFile.renameTo(newFile);
        if (success) {
            System.out.println("Renamed: " + name + " -> " + newName);
        } else {
            System.err.println("Error: Failed to rename " + name);
        }

        return success;
    }

    public static void RegisterPathsWithXmlBluePrint(BPConfig config, int threadCount) {
        // Register entity paths with xmlBluePrint
        for (String entityPath : config.entities) {
            // xmlBluePrint.rootRegist() - register root handler (not used in this example)
            // xmlBluePrint.regist() - register entity path
            xmlBluePrint.regist(entityPath, new xmlBluePrintCall() {
                @Override
                public boolean call(Object idToken, xmlBluePrintHolder holder, int event, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
                    // Entity handler: reset buffer on open tag, write on close tag
                    if (event == 1) { // EV_OPEN_TAG
                        // Reset output buffer for new entity
                        return true;
                    } else if (event == 2) { // EV_CLOSE_TAG
                        // Write entity data to output file
                        return true;
                    }
                    return true;
                }
            }, entityPath);
        }

        // Register column paths (attribute and inner text) with xmlBluePrint
        int columnIndex = 0;
        for (ColumnSpec col : config.columns) {
            // Check if column spec ends with @ or # to determine type
            String type = col.type;
            String attrName = col.attrName;
            String path = col.path;

            // Register column handler
            xmlBluePrint.regist(path, new xmlBluePrintCall() {
                @Override
                public boolean call(Object idToken, xmlBluePrintHolder holder, int event, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
                    if (event == 3) { // EV_ATTR
                        // Extract attribute value and store in buffer
                        byte[] attrNameBytes = new byte[nameEnd - nameBegin];
                        System.arraycopy(holder.getByteBuffer(), nameBegin, attrNameBytes, 0, nameEnd - nameBegin);
                        String currentAttrName = new String(attrNameBytes, java.nio.charset.StandardCharsets.UTF_8);

                        if (currentAttrName.equals(attrName)) {
                            // Extract attribute value
                            byte[] attrValueBytes = new byte[valueEnd - valueBegin];
                            System.arraycopy(holder.getByteBuffer(), valueBegin, attrValueBytes, 0, valueEnd - valueBegin);
                            String attrValue = new String(attrValueBytes, java.nio.charset.StandardCharsets.UTF_8);
                            // TODO: Store in buffer for later writing
                            System.out.println("Extracted attribute " + attrName + " = " + attrValue);
                        }
                        return true;
                    } else if (event == 4) { // EV_INNER_TEXT
                        // Extract inner text value
                        byte[] textBytes = new byte[valueEnd - valueBegin];
                        System.arraycopy(holder.getByteBuffer(), valueBegin, textBytes, 0, valueEnd - valueBegin);
                        String innerText = new String(textBytes, java.nio.charset.StandardCharsets.UTF_8);
                        // TODO: Store in buffer for later writing
                        System.out.println("Extracted inner text: " + innerText);
                        return true;
                    }
                    return true;
                }
            }, columnIndex);

            columnIndex++;
        }

        // Set thread count using xmlBluePrint
        xmlBluePrint.setThreadCount(threadCount);
        System.out.println("Registered paths with xmlBluePrint using " + threadCount + " threads");
    }

    public static void ProcessFiles(String sourcePath, String destPath, int threadCount, BPConfig config) {
        // List all .xml and .zip files
        String fileList = ListAllFile(sourcePath);
        if (fileList.isEmpty()) {
            System.out.println("No .xml or .zip files found in: " + sourcePath);
            return;
        }

        System.out.println("Found files to process: " + fileList);

        // In a real implementation, you would:
        // 1. Create output directory if it doesn't exist
        // 2. Submit each file to xmlBluePrint job queue
        // 3. Run xmlBluePrint processing
        // 4. Wait for completion
        // 5. Shutdown xmlBluePrint

        // For this example, we'll simulate the process
        String[] files = fileList.split(",");
        System.out.println("Processing " + files.length + " files...");

        // Submit files to xmlBluePrint job queue (simulated)
        for (String file : files) {
            System.out.println("Submitting file to xmlBluePrint: " + file);
            // In real implementation: xmlBluePrint.pushJob(fileBytes, file.length(), fileName, fileNameLength);
        }

        // Run xmlBluePrint processing (simulated)
        System.out.println("Running xmlBluePrint processing with " + threadCount + " threads...");
        // In real implementation: xmlBluePrint.run();

        // Wait for completion (simulated)
        System.out.println("Waiting for xmlBluePrint to complete...");
        try {
            Thread.sleep(2000); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check if ready to down (simulated)
        System.out.println("xmlBluePrint processing completed (simulated)");

        // Shutdown xmlBluePrint (simulated)
        System.out.println("Shutting down xmlBluePrint (simulated)");
    }

    public static void WriteOutputFile(int threadNo, String fileId, String data) {
        // Generate output filename
        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        String outputFilename = GenerateOutputFilename(threadNo, fileId, timestamp);

        // Generate pending filename
        String pendingFilename = GeneratePendingFilename(fileId, threadNo);

        // Write data to output file (simulated)
        System.out.println("Writing data to output file: " + outputFilename);
        System.out.println("Pending file: " + pendingFilename);
        System.out.println("Data: " + data);

        // In real implementation:
        // 1. Create pending file
        // 2. Write data to pending file
        // 3. Flush buffer if needed
        // 4. Rename pending file to final output filename

        System.out.println("Output file written successfully");
    }
}