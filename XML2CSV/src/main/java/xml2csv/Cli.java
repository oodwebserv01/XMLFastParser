package xml2csv;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import xml2csv.config.X2CConfig;
import xml2csv.dispatcher.Xml2CsvDispatcher;
import xml2csv.parser.X2CParser;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "xml2csv", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Convert XML/ZIP files to CSV using .x2c mapping configuration")
public class Cli implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file or directory (.xml, .zip, or folder)")
    Path input;

    @Option(names = {"-c", "--config"}, description = "Path to .x2c mapping config file", required = true)
    Path config;

    @Option(names = {"-o", "--output"}, description = "Output directory (default: ./output)")
    Path output = Paths.get("output");

    @Option(names = {"-t", "--threads"}, description = "Number of worker threads (default: CPU cores)")
    int threads = Runtime.getRuntime().availableProcessors();

    @Override
    public Integer call() throws Exception {
        System.out.println("XML2CSV v1.0.0");
        System.out.println("Input: " + input);
        System.out.println("Config: " + config);
        System.out.println("Output: " + output);
        System.out.println("Threads: " + threads);

        // Parse .x2c config
        X2CParser parser = new X2CParser();
        X2CConfig x2cConfig;
        try (FileReader reader = new FileReader(config.toFile())) {
            x2cConfig = parser.parse(reader);
        }
        System.out.println("Parsed config: " + x2cConfig.getOutputs().size() + " output(s)");

        // Create dispatcher
        Xml2CsvDispatcher dispatcher = new Xml2CsvDispatcher(x2cConfig, output);

        // Process input
        if (Files.isDirectory(input)) {
            dispatcher.processDirectory(input, threads);
        } else {
            dispatcher.processFile(input);
        }

        dispatcher.close();

        System.out.println("Done. Success: " + dispatcher.getSuccessCount() + ", Failed: " + dispatcher.getFailCount());
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Cli()).execute(args);
        System.exit(exitCode);
    }
}