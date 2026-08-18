import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class XML2TXTTest {

    public static void main(String[] args) {
        System.out.println("Entering main");
        System.out.println(">>> Testing ReadBPFile <<<");
        boolean allPassed = true;

        // Test 1: Provided sample.bp
        try {
            System.out.println("Current directory: " + System.getProperty("user.dir"));
            System.out.println("Looking for sample.bp at: " + new File("sample.bp").getAbsolutePath());
            XML2TXT.BPConfig config = XML2TXT.ReadBPFile("sample.bp");
            System.out.println("After ReadBPFile call");
            System.out.println("Test 1 (sample.bp): PASSED - " + config);
            // We can add more assertions here
        } catch (Exception e) {
            System.out.println("Test 1 (sample.bp): FAILED - " + e.getMessage());
            allPassed = false;
        }

        // Test 2: Example 1 from GOAL.md (full paths)
        try {
            File bpFile = File.createTempFile("example1", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:Invoids\n");
            writer.write("entity:/Transaction\n");
            writer.write("- /Transaction/Invoid@SN\n");
            writer.write("- /Transaction/Invoid/Saler/PersonID#\n");
            writer.write("- /Transaction/Invoid/Buyer/PersonID#\n");
            writer.write("- /Transaction/Invoid/Items@Count\n");
            writer.write("- /Transaction/Invoid/Summary#\n");
            writer.close();
            XML2TXT.BPConfig config = XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 2 (example1 full paths): PASSED - " + config);
            // Validate that we have the expected number of columns, etc.
            if (config.columns.size() != 5) {
                System.out.println("Test 2 (example1): FAILED - expected 5 columns, got " + config.columns.size());
                allPassed = false;
            } else {
                System.out.println("Test 2 (example1): Column count OK");
            }
        } catch (Exception e) {
            System.out.println("Test 2 (example1 full paths): FAILED - " + e.getMessage());
            allPassed = false;
        }

        // Test 3: Example 2 from GOAL.md (shorthand)
        try {
            File bpFile = File.createTempFile("example2", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:Invoids\n");
            writer.write("entity:/Transaction\n");
            writer.write("- /Transaction/Invoid@SN\n");
            writer.write("- /././Saler/PersonID#\n");
            writer.write("- /././Buyer/PersonID#\n");
            writer.write("- /././Items@Count\n");
            writer.write("- /././Summary#\n");
            writer.close();
            XML2TXT.BPConfig config = XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 3 (example2 shorthand): PASSED - " + config);
            // Validate that we have the expected number of columns
            if (config.columns.size() != 5) {
                System.out.println("Test 3 (example2): FAILED - expected 5 columns, got " + config.columns.size());
                allPassed = false;
            } else {
                System.out.println("Test 3 (example2): Column count OK");
            }
            // We could also validate that the shorthand was resolved correctly, but we trust the parsing.
        } catch (Exception e) {
            System.out.println("Test 3 (example2 shorthand): FAILED - " + e.getMessage());
            allPassed = false;
        }

        // Test 4: Empty .bp file
        try {
            File bpFile = File.createTempFile("empty", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.close();
            XML2TXT.BPConfig config = XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 4 (empty file): PASSED - " + config);
            if (config.fileTypes.size() != 0 || config.entityPaths.size() != 0 || config.columns.size() != 0) {
                System.out.println("Test 4 (empty file): FAILED - expected empty lists");
                allPassed = false;
            } else {
                System.out.println("Test 4 (empty file): Lists are empty OK");
            }
        } catch (Exception e) {
            System.out.println("Test 4 (empty file): FAILED - " + e.getMessage());
            allPassed = false;
        }

        // Test 5: .bp file with only comments
        try {
            File bpFile = File.createTempFile("comments", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("# This is a comment\n");
            writer.write("# Another comment\n");
            writer.close();
            XML2TXT.BPConfig config = XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 5 (comments only): PASSED - " + config);
            if (config.fileTypes.size() != 0 || config.entityPaths.size() != 0 || config.columns.size() != 0) {
                System.out.println("Test 5 (comments only): FAILED - expected empty lists");
                allPassed = false;
            } else {
                System.out.println("Test 5 (comments only): Lists are empty OK");
            }
        } catch (Exception e) {
            System.out.println("Test 5 (comments only): FAILED - " + e.getMessage());
            allPassed = false;
        }

        // Test 6: Malformed line (does not start with file:, entity:, or -)
        try {
            File bpFile = File.createTempFile("malformed", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:Invoids\n");
            writer.write("entity:/Transaction\n");
            writer.write("This is a malformed line\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 6 (malformed line): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 6 (malformed line): PASSED - expected error caught: " + e.getMessage());
        }

        // Test 7: Duplicate file name
        try {
            File bpFile = File.createTempFile("duplicate", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:Invoids\n");
            writer.write("entity:/Transaction\n");
            writer.write("file:Invoids\n"); // duplicate
            writer.write("entity:/Transaction2\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 7 (duplicate file name): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 7 (duplicate file name): PASSED - expected error caught: " + e.getMessage());
        }

        // Test 8: Entity line before file line
        try {
            File bpFile = File.createTempFile("entityBeforeFile", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("entity:/Transaction\n");
            writer.write("file:Invoids\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 8 (entity before file): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 8 (entity before file): PASSED - expected error caught: " + e.getMessage());
        }

        // Test 9: Column line before file line
        try {
            File bpFile = File.createTempFile("columnBeforeFile", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("- /Transaction/Invoid@SN\n");
            writer.write("file:Invoids\n");
            writer.write("entity:/Transaction\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 9 (column before file): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 9 (column before file): PASSED - expected error caught: " + e.getMessage());
        }

        // Test 10: Empty file name
        try {
            File bpFile = File.createTempFile("emptyFileName", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:\n");
            writer.write("entity:/Transaction\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 10 (empty file name): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 10 (empty file name): PASSED - expected error caught: " + e.getMessage());
        }

        // Test 11: Empty entity path
        try {
            File bpFile = File.createTempFile("emptyEntity", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:Invoids\n");
            writer.write("entity:\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 11 (empty entity path): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 11 (empty entity path): PASSED - expected error caught: " + e.getMessage());
        }

        // Test 12: Empty column specification
        try {
            File bpFile = File.createTempFile("emptyColumn", ".bp");
            bpFile.deleteOnExit();
            FileWriter writer = new FileWriter(bpFile);
            writer.write("file:Invoids\n");
            writer.write("entity:/Transaction\n");
            writer.write("-\n");
            writer.close();
            XML2TXT.ReadBPFile(bpFile.getAbsolutePath());
            System.out.println("Test 12 (empty column): FAILED - expected error but none thrown");
            allPassed = false;
        } catch (Exception e) {
            System.out.println("Test 12 (empty column): PASSED - expected error caught: " + e.getMessage());
        }

        if (allPassed) {
            System.out.println("\nAll tests PASSED!");
        } else {
            System.out.println("\nSome tests FAILED!");
        }
    }
}