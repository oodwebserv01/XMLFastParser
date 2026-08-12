package xml2csv;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line arguments container for XML2CSV.
 */
public class CliArgs {
    private Path bpFilePath;      // -p (required)
    private Path sourceDir;       // -s (default: current directory)
    private Path destDir;         // -d (default: current directory)
    private int threadCount;      // -t (default: 1)
    private boolean showHelp;     // --help, -h

    public CliArgs() {
        this.sourceDir = Paths.get(".").toAbsolutePath().normalize();
        this.destDir = Paths.get(".").toAbsolutePath().normalize();
        this.threadCount = 1;
        this.showHelp = false;
    }

    public static CliArgs parse(String[] args) {
        CliArgs cliArgs = new CliArgs();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "-p":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for -p");
                    }
                    cliArgs.bpFilePath = Paths.get(args[++i]).toAbsolutePath().normalize();
                    break;

                case "-s":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for -s");
                    }
                    cliArgs.sourceDir = Paths.get(args[++i]).toAbsolutePath().normalize();
                    break;

                case "-d":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for -d");
                    }
                    cliArgs.destDir = Paths.get(args[++i]).toAbsolutePath().normalize();
                    break;

                case "-t":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for -t");
                    }
                    try {
                        cliArgs.threadCount = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid thread count: " + args[i]);
                    }
                    break;

                case "--help":
                case "-h":
                    cliArgs.showHelp = true;
                    break;

                default:
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    // Ignore positional args (not used)
                    break;
            }
        }

        return cliArgs;
    }

    public void validate() {
        if (showHelp) return;

        if (bpFilePath == null) {
            throw new IllegalArgumentException("Required parameter -p (blueprint file) is missing");
        }

        if (!bpFilePath.toFile().exists()) {
            throw new IllegalArgumentException("Blueprint file not found: " + bpFilePath);
        }

        if (!sourceDir.toFile().exists()) {
            throw new IllegalArgumentException("Source directory not found: " + sourceDir);
        }

        if (!sourceDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Source path is not a directory: " + sourceDir);
        }

        if (threadCount <= 0) {
            throw new IllegalArgumentException("Thread count must be > 0: " + threadCount);
        }

        // Create dest dir if not exists
        destDir.toFile().mkdirs();
    }

    public void printUsage() {
        System.out.println("XML2CSV - Convert XML files to CSV using xmlFastParser");
        System.out.println();
        System.out.println("Usage: java -jar XML2CSV.jar -p <bp-file> [-s <source-dir>] [-d <dest-dir>] [-t <threads>]");
        System.out.println();
        System.out.println("Parameters:");
        System.out.println("  -p <path>    Blueprint file (.bp) - REQUIRED");
        System.out.println("  -s <path>    Source directory to scan for .xml and .zip files (default: current directory)");
        System.out.println("  -d <path>    Destination directory for CSV output (default: current directory)");
        System.out.println("  -t <number>  Number of worker threads (default: 1)");
        System.out.println("  --help, -h   Show this help message");
        System.out.println();
        System.out.println("Output files (per thread):");
        System.out.println("  <fileName>_Thread<NO>_YYYYMMDDHHmmss.csv    Data CSV");
        System.out.println("  read_YYYYMMDDHHmmss.txt                 List of processed files");
        System.out.println("  success_YYYYMMDDHHmmss.txt              Successfully processed files");
        System.out.println("  fail_YYYYMMDDHHmmss.txt                 Failed files with errors");
        System.out.println();
        System.out.println("Processed files are renamed to .bak:");
        System.out.println("  FileName.xml      -> xmlFileName.bak");
        System.out.println("  ZipFile.zip       -> zipZipFile.bak");
    }

    // Getters
    public Path getBpFilePath() { return bpFilePath; }
    public Path getSourceDir() { return sourceDir; }
    public Path getDestDir() { return destDir; }
    public int getThreadCount() { return threadCount; }
    public boolean isShowHelp() { return showHelp; }

    @Override
    public String toString() {
        return String.format("CliArgs[bp=%s, source=%s, dest=%s, threads=%d, help=%b]",
                bpFilePath, sourceDir, destDir, threadCount, showHelp);
    }
}