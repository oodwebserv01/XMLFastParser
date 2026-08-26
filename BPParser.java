import java.io.*;
import java.util.HashMap;

/**
 * BPParser - Parses .bp (Blueprint) file into TokenEntity/TokenColumn structures
 *
 * .bp format:
 *   file:EntityName
 *   entity:/xpath
 *   - /xpath/to/element#           (innerText column)
 *   - /xpath/to/element@attrName   (attribute column)
 */
public class BPParser {

    /**
     * Full path hash function (FNV-1a) - hashes each segment's local name only (skips prefix)
     * This matches xmlBluePrint.hash() behavior for consistency with parser's node tree
     */
    private static long fullPathHash(byte[] buff, int length) {
        long h = 0xCBF29CE484222325L; // FNV offset basis

        // Parse path segments separated by '/'
        int segmentStart = 0;
        for (int i = 0; i <= length; i++) {
            if (i == length || buff[i] == '/') {
                // Hash this segment's local name only (skip prefix like xmlBluePrint.hash)
                int hashStart = segmentStart;
                for (int j = segmentStart; j < i; j++) {
                    byte v = buff[j];
                    if (v == '}') {
                        hashStart = j + 1;
                        break;
                    }
                    if (v == ':') {
                        hashStart = j + 1;
                        break;
                    }
                }
                for (int j = hashStart; j < i; j++) {
                    byte v = buff[j];
                    if (v == '>' || v == '/' || v == ' ' || v == '\t' || v == '\n' || v == '\r') break;
                    h ^= (v & 0xFF);
                    h *= 0x100000001B3L;
                }
                segmentStart = i + 1;
            }
        }
        return h;
    }

    /**
     * Parse .bp file and return map of TokenEntity by hash
     * @param bpFilePath Path to .bp file
     * @param numThreads Number of threads (for buffer allocation)
     * @return HashMap<Long, TokenEntity> keyed by xmlBluePrint.hash(fileName)
     */
    public static HashMap<Long, TokenEntity> parseBP(String bpFilePath, int numThreads) throws IOException {
        HashMap<Long, TokenEntity> allTokenEntity = new HashMap<>();
        TokenEntity currentEntity = null;
        String currentLine = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(bpFilePath))) {
            while ((currentLine = reader.readLine()) != null) {
                currentLine = currentLine.trim();

                // Skip empty lines and comments
                if (currentLine.isEmpty() || currentLine.startsWith("#")) {
                    continue;
                }

                // file:EntityName - start new entity
                if (currentLine.startsWith("file:")) {
                    // Save previous entity if exists
                    if (currentEntity != null) {
                        if (currentEntity.totalColumn > 0) {
                            currentEntity.initBuffers(numThreads);
                            long entityHash = xmlBluePrint.hash(currentEntity.fileName.getBytes(), currentEntity.fileName.getBytes().length);
                            allTokenEntity.put(entityHash, currentEntity);
                        }
                    }

                    // Create new entity
                    String fileName = currentLine.substring(5).trim();
                    currentEntity = new TokenEntity(fileName, null); // path will be set by entity: line
                }
                // entity:/xpath - set entity path
                else if (currentLine.startsWith("entity:")) {
                    if (currentEntity != null) {
                        String path = currentLine.substring(7).trim();
                        currentEntity.path = path;
                    }
                }
                // - /path# or - /path@attr - column definition
                else if (currentLine.startsWith("-")) {
                    if (currentEntity == null) {
                        continue; // No entity defined yet
                    }

                    // Parse column line: - /path# or - /path@attr
                    String colDef = currentLine.substring(1).trim(); // Remove leading "-"

                    int attrIndex = colDef.indexOf('@');
                    int innerIndex = colDef.indexOf('#');

                    if (innerIndex >= 0 && (attrIndex < 0 || innerIndex < attrIndex)) {
                        // InnerText column: - /path#
                        String path = colDef.substring(0, innerIndex).trim();
                        byte[] pathBytes = path.getBytes();
                        long pathHash = fullPathHash(pathBytes, pathBytes.length);

                        TokenColumn column = currentEntity.getColumn(pathHash);
                        if (column == null) {
                            column = new TokenColumn(currentEntity, path);
                            currentEntity.addColumn(pathHash, column);
                        }
                        // Assign column index for innerText
                        column.inner = currentEntity.totalColumn;
                        currentEntity.totalColumn++;
                    }
                    else if (attrIndex >= 0) {
                        // Attribute column: - /path@attr
                        String path = colDef.substring(0, attrIndex).trim();
                        String attrName = colDef.substring(attrIndex + 1).trim();
                        byte[] pathBytes = path.getBytes();
                        long pathHash = fullPathHash(pathBytes, pathBytes.length);
                        long attrHash = xmlBluePrint.hash(attrName.getBytes(), attrName.getBytes().length);

                        TokenColumn column = currentEntity.getColumn(pathHash);
                        if (column == null) {
                            column = new TokenColumn(currentEntity, path);
                            currentEntity.addColumn(pathHash, column);
                        }
                        // Add attribute to existing column (reuse column for same path)
                        column.addAttribute(attrHash, currentEntity.totalColumn);
                        currentEntity.totalColumn++;
                    }
                }
            }
        }

        // Save last entity
        if (currentEntity != null && currentEntity.totalColumn > 0) {
            currentEntity.initBuffers(numThreads);
            long entityHash = xmlBluePrint.hash(currentEntity.fileName.getBytes(), currentEntity.fileName.getBytes().length);
            allTokenEntity.put(entityHash, currentEntity);
        }

        return allTokenEntity;
    }
}