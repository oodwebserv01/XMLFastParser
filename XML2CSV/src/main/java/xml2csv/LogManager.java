package xml2csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages log files: read, success, fail.
 * Files start as *_pending.txt and are renamed to timestamped names on finalize.
 */
public class LogManager {

    private final Path destDir;
    private final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private String timestamp;

    // Pending file paths
    private Path readPendingPath;
    private Path successPendingPath;
    private Path failPendingPath;

    // Writers
    private BufferedWriter readWriter;
    private BufferedWriter successWriter;
    private BufferedWriter failWriter;

    // Thread safety
    private final ReentrantLock lock = new ReentrantLock();

    public LogManager(Path destDir) {
        this.destDir = destDir.toAbsolutePath().normalize();
        this.timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
    }

    /**
     * Initialize log writers (creates *_pending.txt files).
     */
    public void initialize() throws IOException {
        lock.lock();
        try {
            readPendingPath = destDir.resolve("read_pending.txt");
            successPendingPath = destDir.resolve("success_pending.txt");
            failPendingPath = destDir.resolve("fail_pending.txt");

            readWriter = Files.newBufferedWriter(readPendingPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            successWriter = Files.newBufferedWriter(successPendingPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            failWriter = Files.newBufferedWriter(failPendingPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Log a file that was read/processed.
     */
    public void logRead(String relativePath) {
        writeLocked(readWriter, relativePath);
    }

    /**
     * Log a successfully processed file.
     */
    public void logSuccess(String relativePath) {
        writeLocked(successWriter, relativePath);
    }

    /**
     * Log a failed file with error message.
     */
    public void logFail(String relativePath, String error) {
        String message = relativePath + " | " + error;
        writeLocked(failWriter, message);
    }

    private void writeLocked(BufferedWriter writer, String line) {
        lock.lock();
        try {
            if (writer != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Error writing to log: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finalize logs: close writers and rename *_pending.txt to timestamped names.
     */
    public void finalizeLogs() {
        lock.lock();
        try {
            // Close writers
            closeQuietly(readWriter);
            closeQuietly(successWriter);
            closeQuietly(failWriter);
            readWriter = successWriter = failWriter = null;

            // Rename pending files to timestamped names
            renamePending(readPendingPath, "read");
            renamePending(successPendingPath, "success");
            renamePending(failPendingPath, "fail");

        } finally {
            lock.unlock();
        }
    }

    private void renamePending(Path pendingPath, String prefix) {
        if (pendingPath == null || !Files.exists(pendingPath)) {
            return;
        }

        String timestampedName = prefix + "_" + timestamp + ".txt";
        Path targetPath = destDir.resolve(timestampedName);

        // Handle collision
        int counter = 1;
        while (Files.exists(targetPath)) {
            targetPath = destDir.resolve(prefix + "_" + timestamp + "_" + counter + ".txt");
            counter++;
        }

        try {
            Files.move(pendingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error renaming log file " + pendingPath + ": " + e.getMessage());
        }
    }

    private void closeQuietly(BufferedWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Get the timestamp used for this session.
     */
    public String getTimestamp() {
        return timestamp;
    }
}