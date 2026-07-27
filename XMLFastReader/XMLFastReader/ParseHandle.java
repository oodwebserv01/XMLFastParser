package XMLFastReader;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ParseHandle — Control interface for parsing operations
 * Fluent API for configuring and controlling parse execution
 */
public interface ParseHandle {
    
    /** Register error handler (fluent) */
    ParseHandle onError(ErrorHandler handler);
    
    /** Register progress listener (fluent) */
    ParseHandle onProgress(ProgressListener listener);
    
    /** Start parsing asynchronously (non-blocking) */
    ParseHandle startAsync();
    
    /** Wait for completion (blocking) */
    void await() throws InterruptedException;
    
    /** Cancel parsing */
    void cancel();
    
    /** Get parsing statistics */
    Stats getStats();
    
    /** Check if parsing is complete */
    boolean isDone();
    
    /** Check if parsing was cancelled */
    boolean isCancelled();
    
    /** Check if parsing had errors */
    boolean hasErrors();
    
    // ===== Functional Interfaces =====
    
    @FunctionalInterface
    interface ErrorHandler {
        void handle(String filename, Exception error);
    }
    
    @FunctionalInterface
    interface ProgressListener {
        void onProgress(Progress progress);
    }
    
    // ===== Data Classes =====
    
    class Progress {
        public final long filesProcessed;
        public final long filesTotal;
        public final long bytesProcessed;
        public final long bytesTotal;
        public final int activeThreads;
        public final long elapsedMs;
        
        public Progress(long filesProcessed, long filesTotal, long bytesProcessed, 
                       long bytesTotal, int activeThreads, long elapsedMs) {
            this.filesProcessed = filesProcessed;
            this.filesTotal = filesTotal;
            this.bytesProcessed = bytesProcessed;
            this.bytesTotal = bytesTotal;
            this.activeThreads = activeThreads;
            this.elapsedMs = elapsedMs;
        }
        
        public double getPercentComplete() {
            return filesTotal > 0 ? (filesProcessed * 100.0 / filesTotal) : 0;
        }
        
        public double getThroughputFilesPerSec() {
            return elapsedMs > 0 ? (filesProcessed * 1000.0 / elapsedMs) : 0;
        }
    }
    
    class Stats {
        public final long filesProcessed;
        public final long filesSucceeded;
        public final long filesFailed;
        public final long bytesProcessed;
        public final long elapsedMs;
        public final int threadCount;
        
        public Stats(long filesProcessed, long filesSucceeded, long filesFailed,
                    long bytesProcessed, long elapsedMs, int threadCount) {
            this.filesProcessed = filesProcessed;
            this.filesSucceeded = filesSucceeded;
            this.filesFailed = filesFailed;
            this.bytesProcessed = bytesProcessed;
            this.elapsedMs = elapsedMs;
            this.threadCount = threadCount;
        }
    }
}