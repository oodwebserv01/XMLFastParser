package xml2csv;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renames processed files to .bak format:
 * - FileName.xml -> xmlFileName.bak
 * - ZipFile.zip -> zipZipFile.bak
 */
public class BackupRenamer {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Rename a processed file to .bak format.
     * @param filePath Path to the file to rename
     * @return Path to the renamed .bak file, or null if failed
     */
    public Path renameToBackup(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }

        String fileName = filePath.getFileName().toString();
        String backupName;

        if (fileName.toLowerCase().endsWith(".xml")) {
            // FileName.xml -> xmlFileName.bak
            String baseName = fileName.substring(0, fileName.length() - 4); // remove .xml
            backupName = "xml" + baseName + ".bak";
        }
        else if (fileName.toLowerCase().endsWith(".zip")) {
            // ZipFile.zip -> zipZipFile.bak
            String baseName = fileName.substring(0, fileName.length() - 4); // remove .zip
            backupName = "zip" + baseName + ".bak";
        }
        else {
            // Unknown extension, add timestamp to avoid collision
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            backupName = fileName + "_" + timestamp + ".bak";
        }

        Path backupPath = filePath.getParent().resolve(backupName);

        // Handle name collision
        backupPath = resolveCollision(backupPath);

        try {
            Files.move(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            return backupPath;
        } catch (IOException e) {
            System.err.println("Error renaming " + filePath + " to backup: " + e.getMessage());
            return null;
        }
    }

    /**
     * Rename a ZIP file to backup after processing its entries.
     * The zip file itself is renamed to .bak.
     */
    public Path renameZipToBackup(Path zipPath) {
        return renameToBackup(zipPath);
    }

    /**
     * Resolve filename collision by appending timestamp.
     */
    private Path resolveCollision(Path path) {
        if (!Files.exists(path)) {
            return path;
        }

        String fileName = path.getFileName().toString();
        String baseName;
        String extension = "";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path parent = path.getParent();
        Path newPath;

        do {
            String newName = baseName + "_" + timestamp + extension;
            newPath = parent.resolve(newName);
            timestamp = LocalDateTime.now().format(TIMESTAMP_FMT); // New timestamp for next iteration
        } while (Files.exists(newPath));

        return newPath;
    }
}