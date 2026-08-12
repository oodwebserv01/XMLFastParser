package xml2csv;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Scans source directory for .xml files and .xml entries in .zip files.
 */
public class FileScanner {

    private final Path sourceDir;
    private final Path tempDir;

    public FileScanner(Path sourceDir) {
        this.sourceDir = sourceDir.toAbsolutePath().normalize();
        this.tempDir = sourceDir.resolve(".xml2csv_temp").toAbsolutePath().normalize();
    }

    /**
     * Scan all XML sources (files + zip entries) in the source directory.
     * @return Sorted list of XmlSource objects
     */
    public List<XmlSource> scanAll() throws IOException {
        List<XmlSource> sources = new ArrayList<>();

        // Scan regular XML files
        sources.addAll(scanXmlFiles());

        // Scan XML entries in ZIP files
        sources.addAll(scanZipFiles());

        // Sort by relative path for deterministic processing order
        sources.sort(Comparator.comparing(XmlSource::getRelativePath));

        return sources;
    }

    /**
     * Scan for .xml files recursively.
     */
    private List<XmlSource> scanXmlFiles() throws IOException {
        List<XmlSource> sources = new ArrayList<>();

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isXmlFile(file)) {
                    Path relative = sourceDir.relativize(file);
                    sources.add(new XmlSource(XmlSource.SourceType.FILE, file, relative.toString()));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // Log but continue scanning
                System.err.println("Warning: Cannot access file " + file + ": " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        return sources;
    }

    /**
     * Scan for .xml entries in .zip files recursively.
     */
    private List<XmlSource> scanZipFiles() throws IOException {
        List<XmlSource> sources = new ArrayList<>();

        // Ensure temp directory exists
        Files.createDirectories(tempDir);

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isZipFile(file)) {
                    processZipFile(file, sources);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return sources;
    }

    private void processZipFile(Path zipPath, List<XmlSource> sources) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Path relativeZipPath = sourceDir.relativize(zipPath);
            String zipRelativePath = relativeZipPath.toString();

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (!entry.isDirectory() && isXmlEntry(entry.getName())) {
                    // Extract to temp location for processing
                    Path tempEntryPath = extractZipEntry(zipFile, entry, zipRelativePath);

                    // Create relative path: zipRelativePath/entryName
                    String entryRelativePath = zipRelativePath + "/" + entry.getName();

                    sources.add(new XmlSource(
                            XmlSource.SourceType.ZIP_ENTRY,
                            zipPath,
                            entryRelativePath,
                            entry.getName(),
                            tempEntryPath
                    ));
                }
            }
        } catch (ZipException e) {
            System.err.println("Warning: Cannot read ZIP file " + zipPath + ": " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Warning: Error processing ZIP file " + zipPath + ": " + e.getMessage());
        }
    }

    private Path extractZipEntry(ZipFile zipFile, ZipArchiveEntry entry, String zipRelativePath) throws IOException {
        // Create temp path: .xml2csv_temp/zipRelativePath/entryName
        // Sanitize zipRelativePath for use as directory name
        String safeZipPath = zipRelativePath.replace("/", "_").replace("\\", "_");
        Path entryDir = tempDir.resolve(safeZipPath);
        Files.createDirectories(entryDir);

        Path entryPath = entryDir.resolve(entry.getName());

        // Extract entry
        try (var inputStream = zipFile.getInputStream(entry)) {
            Files.copy(inputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return entryPath;
    }

    private boolean isXmlFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".xml");
    }

    private boolean isZipFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".zip");
    }

    private boolean isXmlEntry(String entryName) {
        String name = entryName.toLowerCase();
        return name.endsWith(".xml");
    }

    /**
     * Clean up temporary extracted files.
     */
    public void cleanupTemp() {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}