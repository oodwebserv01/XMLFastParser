package xml2csv;

import java.nio.file.Path;

/**
 * XML2CSV - Main entry point.
 * Converts XML files to CSV using xmlFastParser.
 *
 * Usage:
 *   java -jar XML2CSV.jar -p <bp-file> [-s <source-dir>] [-d <dest-dir>] [-t <threads>]
 */
public class XML2CSV {

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        try {
            // Parse command line arguments
            CliArgs cliArgs = CliArgs.parse(args);

            if (cliArgs.isShowHelp()) {
                cliArgs.printUsage();
                System.exit(0);
            }

            cliArgs.validate();

            System.out.println("=== XML2CSV Started ===");
            System.out.println("Blueprint: " + cliArgs.getBpFilePath());
            System.out.println("Source:    " + cliArgs.getSourceDir());
            System.out.println("Dest:      " + cliArgs.getDestDir());
            System.out.println("Threads:   " + cliArgs.getThreadCount());
            System.out.println();

            // Parse .bp file
            System.out.println("Parsing blueprint file...");
            BPParser bpParser = new BPParser();
            BPFileConfig config = bpParser.parse(cliArgs.getBpFilePath().toFile());
            System.out.println("Config: " + config);
            for (BPCsvOutput output : config.getOutputs()) {
                System.out.println("  " + output);
                for (BPEntity entity : output.getEntities()) {
                    System.out.println("    Entity: " + entity);
                    for (BPField field : entity.getFields()) {
                        System.out.println("      " + field);
                    }
                }
            }
            System.out.println();

            // Initialize components
            LogManager logManager = new LogManager(cliArgs.getDestDir());
            logManager.initialize();

            CSVWriterManager csvManager = new CSVWriterManager(cliArgs.getDestDir());
            csvManager.initialize(config, cliArgs.getThreadCount());

            BackupRenamer backupRenamer = new BackupRenamer();

            XmlProcessor processor = new XmlProcessor(config, csvManager, logManager, backupRenamer, cliArgs.getThreadCount());
            processor.initialize();
            processor.registerPaths();  // Ensure paths are registered for parsing

            // Scan source files
            System.out.println("Scanning source directory...");
            FileScanner scanner = new FileScanner(cliArgs.getSourceDir());
            var sources = scanner.scanAll();
            System.out.println("Found " + sources.size() + " XML source(s) to process");
            System.out.println();

            // Process each source
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < sources.size(); i++) {
                XmlSource source = sources.get(i);
                System.out.printf("[%d/%d] Processing: %s%n", i + 1, sources.size(), source.getRelativePath());

                try {
                    processor.processSource(source);
                    logManager.logSuccess(source.getRelativePath());
                    successCount++;
                } catch (Exception e) {
                    String error = e.getMessage();
                    logManager.logFail(source.getRelativePath(), error);
                    System.err.println("  FAILED: " + error);
                    failCount++;
                }
            }

            // Wait for all processing to complete
            System.out.println("\nWaiting for processing to complete...");
            processor.waitAndShutdown(false);

            // Finalize outputs
            System.out.println("Finalizing CSV files...");
            csvManager.finalizeAll();

            System.out.println("Finalizing log files...");
            logManager.finalizeLogs();

            // Rename processed source files to .bak
            System.out.println("Renaming processed files to .bak...");
            for (XmlSource source : sources) {
                if (source.isFile()) {
                    backupRenamer.renameToBackup(source.getPath());
                } else if (source.isZipEntry()) {
                    // For ZIP entries, rename the ZIP file itself
                    backupRenamer.renameZipToBackup(source.getPath());
                }
            }

            // Cleanup temp files
            scanner.cleanupTemp();

            // Print summary
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("\n=== XML2CSV Completed ===");
            System.out.println("Total:   " + sources.size());
            System.out.println("Success: " + successCount);
            System.out.println("Failed:  " + failCount);
            System.out.println("Time:    " + (elapsed / 1000.0) + " seconds");

            System.exit(failCount > 0 ? 1 : 0);

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            new CliArgs().printUsage();
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }
}