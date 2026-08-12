package xml2csv;

import java.nio.file.Path;

/**
 * Represents an XML source to process - either a file or a ZIP entry.
 */
public class XmlSource {
    public enum SourceType {
        FILE,       // Regular .xml file
        ZIP_ENTRY   // .xml entry inside a .zip file
    }

    private final SourceType type;
    private final Path path;              // For FILE: absolute path to .xml file
                                          // For ZIP_ENTRY: path to .zip file
    private final String relativePath;    // Relative path from source directory
    private final String zipEntryName;    // For ZIP_ENTRY: name of entry inside zip
    private final Path tempExtractedPath; // For ZIP_ENTRY: temp extracted file path

    public XmlSource(SourceType type, Path path, String relativePath) {
        this(type, path, relativePath, null, null);
    }

    public XmlSource(SourceType type, Path path, String relativePath,
                     String zipEntryName, Path tempExtractedPath) {
        this.type = type;
        this.path = path;
        this.relativePath = relativePath;
        this.zipEntryName = zipEntryName;
        this.tempExtractedPath = tempExtractedPath;
    }

    public SourceType getType() { return type; }
    public Path getPath() { return path; }
    public String getRelativePath() { return relativePath; }
    public String getZipEntryName() { return zipEntryName; }
    public Path getTempExtractedPath() { return tempExtractedPath; }

    public boolean isFile() { return type == SourceType.FILE; }
    public boolean isZipEntry() { return type == SourceType.ZIP_ENTRY; }

    @Override
    public String toString() {
        if (isFile()) {
            return "XmlSource[FILE, rel=" + relativePath + "]";
        } else {
            return "XmlSource[ZIP_ENTRY, zip=" + path + ", entry=" + zipEntryName + "]";
        }
    }
}