package xml2csv.parser;

import xml2csv.config.FieldMapping;
import xml2csv.config.OutputConfig;
import xml2csv.config.X2CConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for .x2c configuration files (indentation-based format)
 * Supports three formats: full, shorthand, and mixed
 */
public class X2CParser {

    private static final Pattern SUCCESS_PATTERN = Pattern.compile("^success:\\s*(.+)$");
    private static final Pattern FAILED_PATTERN = Pattern.compile("^failed:\\s*(.+)$");
    private static final Pattern OUTPUT_FILE_PATTERN = Pattern.compile("^([a-zA-Z0-9_\\-]+\\.txt)\\s*$");
    private static final Pattern BODY_PATH_PATTERN = Pattern.compile("^\\s*body:\\s*(.+)$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^(\\s*/+)([^$@\\s]+)([$@])?(\\w*)?\\s*$");

    private final List<String> lines = new ArrayList<>();
    private int lineIndex = 0;
    private String currentBasePath = "";
    private final List<String> pathStack = new ArrayList<>(); // stack of base paths by depth

    public X2CConfig parse(Reader reader) throws IOException {
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return parse();
    }

    public X2CConfig parse(List<String> lines) {
        this.lines.clear();
        this.lines.addAll(lines);
        this.lineIndex = 0;
        this.currentBasePath = "";
        this.pathStack.clear();
        return parse();
    }

    private X2CConfig parse() {
        X2CConfig config = new X2CConfig();
        OutputConfig currentOutput = null;

        while (lineIndex < lines.size()) {
            String rawLine = lines.get(lineIndex++);
            String line = rawLine.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue; // skip empty lines and comments
            }

            // success: pattern
            Matcher m = SUCCESS_PATTERN.matcher(line);
            if (m.matches()) {
                config.setSuccessFilePattern(m.group(1).trim());
                continue;
            }

            // failed: pattern
            m = FAILED_PATTERN.matcher(line);
            if (m.matches()) {
                config.setFailedFilePattern(m.group(1).trim());
                continue;
            }

            // output file (e.g., entity.txt, product.txt)
            m = OUTPUT_FILE_PATTERN.matcher(line);
            if (m.matches()) {
                if (currentOutput != null) {
                    config.addOutput(currentOutput);
                }
                currentOutput = new OutputConfig();
                currentOutput.setFileName(m.group(1));
                // reset path stack for new output
                pathStack.clear();
                currentBasePath = "";
                continue;
            }

            if (currentOutput == null) {
                throw new ParseException("Field/body path found before output file declaration at line " + lineIndex);
            }

            // body: path (multi-row indicator)
            m = BODY_PATH_PATTERN.matcher(rawLine);
            if (m.matches()) {
                String bodyPath = m.group(1).trim();
                currentOutput.setBodyPath(resolvePath(bodyPath));
                // push body path as new base for subsequent fields
                pushBasePath(currentOutput.getBodyPath());
                continue;
            }

            // field path with optional $ (inner text) or @ (attribute)
            m = FIELD_PATTERN.matcher(rawLine);
            if (m.matches()) {
                String slashes = m.group(1);      // leading slashes
                String pathPart = m.group(2);     // path component
                String suffix = m.group(3);       // $ or @
                String attrName = m.group(4);     // attribute name (if @)

                int depth = slashes.length();
                String resolvedPath = resolvePath(pathPart, depth);

                FieldMapping field = new FieldMapping();
                field.setRawPath(rawLine.trim());
                field.setPath(resolvedPath);
                field.setColumnIndex(currentOutput.getFields().size());

                if ("$".equals(suffix)) {
                    field.setType(FieldMapping.ExtractType.INNER_TEXT);
                } else if ("@".equals(suffix)) {
                    field.setType(FieldMapping.ExtractType.ATTRIBUTE);
                    field.setAttributeName(attrName.isEmpty() ? "" : attrName);
                } else {
                    field.setType(FieldMapping.ExtractType.INNER_TEXT); // default
                }

                currentOutput.addField(field);

                // update base path stack
                pushBasePath(resolvedPath);
                continue;
            }

            throw new ParseException("Unrecognized line at " + lineIndex + ": " + rawLine);
        }

        if (currentOutput != null) {
            config.addOutput(currentOutput);
        }

        return config;
    }

    /**
     * Resolve a path component using shorthand notation.
     * - depth = 1 (single /): absolute path from root
     * - depth > 1: relative to base path at (depth - 2) level
     */
    private String resolvePath(String pathComponent, int depth) {
        if (depth == 1) {
            // Absolute path
            currentBasePath = "/" + pathComponent;
            ensureStackSize(1);
            pathStack.set(0, currentBasePath);
            return currentBasePath;
        } else {
            // Relative path: depth N means use base at index (N-2)
            int baseIndex = depth - 2;
            if (baseIndex < 0 || baseIndex >= pathStack.size()) {
                throw new ParseException("Invalid shorthand depth " + depth + " at line " + lineIndex +
                        " (stack size: " + pathStack.size() + ")");
            }
            String base = pathStack.get(baseIndex);
            String resolved = base + "/" + pathComponent;
            ensureStackSize(depth);
            pathStack.set(depth - 1, resolved);
            currentBasePath = resolved;
            return resolved;
        }
    }

    private String resolvePath(String fullPath) {
        // For body: path - treat as absolute
        return fullPath.startsWith("/") ? fullPath : "/" + fullPath;
    }

    private void pushBasePath(String path) {
        int depth = countPathDepth(path);
        ensureStackSize(depth);
        if (depth > 0 && depth <= pathStack.size()) {
            pathStack.set(depth - 1, path);
        }
        currentBasePath = path;
    }

    private int countPathDepth(String path) {
        int count = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') count++;
        }
        return count;
    }

    private void ensureStackSize(int size) {
        while (pathStack.size() < size) {
            pathStack.add("");
        }
    }

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}