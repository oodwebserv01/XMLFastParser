package xml2csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for .bp (blueprint) configuration files.
 * Format:
 *   file:OutputName
 *   entity:/Path/To/Entity
 *   - /Path/To/Field@AttributeName
 *   - /Path/To/Field#InnerText
 *   - /././RelativePath@AttributeName  (shorthand)
 */
public class BPParser {

    private static final Pattern FILE_PATTERN = Pattern.compile("^file:\\s*(.+)$");
    private static final Pattern ENTITY_PATTERN = Pattern.compile("^entity:\\s*(.+)$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^-\\s*(.+)$");

    private int lineNumber = 0;
    private String previousFullPath = "";

    public BPFileConfig parse(File bpFile) throws IOException {
        BPFileConfig config = new BPFileConfig();
        lineNumber = 0;
        previousFullPath = "";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(bpFile), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                parseLine(line, config);
            }
        }

        validateConfig(config);
        return config;
    }

    private void parseLine(String line, BPFileConfig config) {
        Matcher fileMatcher = FILE_PATTERN.matcher(line);
        Matcher entityMatcher = ENTITY_PATTERN.matcher(line);
        Matcher fieldMatcher = FIELD_PATTERN.matcher(line);

        if (fileMatcher.matches()) {
            // file:OutputName
            String fileName = fileMatcher.group(1).trim();
            if (fileName.isEmpty()) {
                throw new BPParseException(lineNumber, "file: name cannot be empty");
            }
            BPCsvOutput output = new BPCsvOutput(fileName);
            config.addOutput(output);
            previousFullPath = ""; // Reset for new output
        }
        else if (entityMatcher.matches()) {
            // entity:/Path/To/Entity
            if (config.getCurrentOutput() == null) {
                throw new BPParseException(lineNumber, "entity: must be preceded by file:");
            }
            String entityPath = entityMatcher.group(1).trim();
            if (entityPath.isEmpty()) {
                throw new BPParseException(lineNumber, "entity: path cannot be empty");
            }
            if (!entityPath.startsWith("/")) {
                throw new BPParseException(lineNumber, "entity: path must start with '/'");
            }
            BPEntity entity = new BPEntity(entityPath);
            config.getCurrentOutput().addEntity(entity);
            previousFullPath = entityPath;
        }
        else if (fieldMatcher.matches()) {
            // - /Path/To/Field@Attr or - /Path/To/Field# or - /././Relative@Attr
            if (config.getCurrentOutput() == null || config.getCurrentOutput().getCurrentEntity() == null) {
                throw new BPParseException(lineNumber, "field must be preceded by entity:");
            }
            String fieldSpec = fieldMatcher.group(1).trim();
            parseField(fieldSpec, config);
        }
        else {
            throw new BPParseException(lineNumber, "Unrecognized line format: " + line);
        }
    }

    private void parseField(String fieldSpec, BPFileConfig config) {
        BPEntity currentEntity = config.getCurrentOutput().getCurrentEntity();

        // Determine type and extract path + name
        BPField.FieldType type;
        String pathPart;
        String fieldName;

        int atIndex = fieldSpec.lastIndexOf('@');
        int hashIndex = fieldSpec.lastIndexOf('#');

        if (atIndex > 0 && (hashIndex < 0 || atIndex > hashIndex)) {
            // Attribute: @
            type = BPField.FieldType.ATTRIBUTE;
            pathPart = fieldSpec.substring(0, atIndex).trim();
            fieldName = fieldSpec.substring(atIndex + 1).trim();
            if (fieldName.isEmpty()) {
                throw new BPParseException(lineNumber, "Attribute name cannot be empty: " + fieldSpec);
            }
        }
        else if (hashIndex > 0) {
            // Inner text: #
            type = BPField.FieldType.INNER_TEXT;
            pathPart = fieldSpec.substring(0, hashIndex).trim();
            // fieldName is the last segment of the path
            fieldName = extractLastSegment(pathPart);
            if (fieldName.isEmpty()) {
                throw new BPParseException(lineNumber, "Cannot determine field name from path: " + pathPart);
            }
        }
        else {
            throw new BPParseException(lineNumber, "Field must end with @AttributeName or #: " + fieldSpec);
        }

        // Resolve shorthand /./ if present
        String resolvedPath = resolveShorthand(pathPart);

        // Validate path starts with /
        if (!resolvedPath.startsWith("/")) {
            throw new BPParseException(lineNumber, "Field path must start with '/': " + resolvedPath);
        }

        // Create field with column index (0 = xmlFilePath, 1+ = fields)
        int columnIndex = currentEntity.getFields().size() + 1;
        BPField field = new BPField(fieldSpec, type, fieldName, resolvedPath, columnIndex);
        currentEntity.addField(field);

        // Update previousFullPath for next shorthand
        previousFullPath = resolvedPath;
    }

    private String extractLastSegment(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    /**
     * Resolve shorthand /././path to full path based on previousFullPath.
     * Each /./ goes up one level from previousFullPath.
     */
    private String resolveShorthand(String pathPart) {
        if (!pathPart.startsWith("/./")) {
            return pathPart;
        }

        // Count /./ segments
        int shorthandCount = 0;
        int idx = 0;
        while (idx < pathPart.length()) {
            if (pathPart.startsWith("/./", idx)) {
                shorthandCount++;
                idx += 3;
            } else {
                break;
            }
        }

        // Remaining path after shorthand
        String remaining = pathPart.substring(idx);
        if (remaining.isEmpty()) {
            throw new BPParseException(lineNumber, "Shorthand /./ must be followed by a path segment");
        }

        if (previousFullPath.isEmpty()) {
            throw new BPParseException(lineNumber, "Shorthand /./ used but no previous path to reference");
        }

        // Go up shorthandCount levels from previousFullPath
        String basePath = previousFullPath;
        for (int i = 0; i < shorthandCount; i++) {
            int lastSlash = basePath.lastIndexOf('/');
            if (lastSlash <= 0) { // At root or empty
                throw new BPParseException(lineNumber, "Cannot go up " + shorthandCount + " levels from: " + previousFullPath);
            }
            basePath = basePath.substring(0, lastSlash);
        }

        // Append remaining (ensure it starts with /)
        if (!remaining.startsWith("/")) {
            remaining = "/" + remaining;
        }
        return basePath + remaining;
    }

    private void validateConfig(BPFileConfig config) {
        if (config.getOutputs().isEmpty()) {
            throw new BPParseException(0, "No file: output defined in .bp file");
        }

        for (BPCsvOutput output : config.getOutputs()) {
            if (output.getEntities().isEmpty()) {
                throw new BPParseException(0, "Output '" + output.getFileName() + "' has no entity: defined");
            }
            for (BPEntity entity : output.getEntities()) {
                if (entity.getFields().isEmpty()) {
                    throw new BPParseException(0, "Entity '" + entity.getEntityPath() + "' has no fields defined");
                }
            }
        }
    }

    public static class BPParseException extends RuntimeException {
        private final int lineNumber;

        public BPParseException(int lineNumber, String message) {
            super("Line " + lineNumber + ": " + message);
            this.lineNumber = lineNumber;
        }

        public int getLineNumber() { return lineNumber; }
    }
}