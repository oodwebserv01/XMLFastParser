package XMLFastReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XmlDispatcher — Work Coordinator & Producer-Consumer Bridge
 * 
 * ARCHITECTURE ROLE:
 *   Central coordinator running in its own thread. Bridges producers (XML sources)
 *   and consumers (XmlWorkers). Manages thread lifecycle, work distribution,
 *   and progress tracking.
 * 
 * RESPONSIBILITIES:
 *   1. Worker Pool Management — Creates N XmlWorkers (each with XmlRunner + Thread)
 *   2. Producer Integration — Accepts XmlProducer, runs it in dispatcher thread
 *   3. Work Distribution — Round-robin to least-busy worker (queue depth)
 *   4. Progress Tracking — Atomic counters for files/bytes, progress callbacks
 *   5. Lifecycle Control — startAsync(), await(), cancel(), shutdown
 *   6. Error Aggregation — Routes producer/worker errors to ParseHandle.ErrorHandler
 * 
 * THREAD MODEL:
 *   - Dispatcher Thread: Runs producer.produce(this), pushes work via pushWork()
 *   - Worker Threads (N): Each pulls from its XmlRunner queue, calls parseRegion()
 *   - Main Thread: Calls startAsync(), await(), getStats(), cancel()
 * 
 * WORK DISTRIBUTION STRATEGY:
 *   pushWork() selects worker with smallest queue (getRemain()).
 *   Simple, effective for balanced workloads. O(N) per push where N = worker count.
 * 
 * BACKPRESSURE:
 *   - pushWork() returns false if all queues full (returns to producer to retry)
 *   - Producer should retry with small delay or park
 *   - Queue size 512 per worker provides substantial buffering
 * 
 * GRACEFUL SHUTDOWN:
 *   cancel() → sets cancelled flag → interrupts all threads → pushes poison pills
 *   Workers drain queues, then exit. Dispatcher joins all threads, counts down latch.
 */
final class XmlDispatcher implements Runnable, ParseHandle {
    
    private final XmlPlanner planner;
    private int workerCount;
    private final List<XmlWorker> workers = new ArrayList<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicLong filesProcessed = new AtomicLong(0);
    private final AtomicLong filesSucceeded = new AtomicLong(0);
    private final AtomicLong filesFailed = new AtomicLong(0);
    private final AtomicLong bytesProcessed = new AtomicLong(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final long startTime = System.currentTimeMillis();
    
    private XmlProducer producer;
    private ParseHandle.ErrorHandler errorHandler;
    private ParseHandle.ProgressListener progressListener;
    private Thread dispatcherThread;
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    
    XmlDispatcher(XmlPlanner planner, int workerCount) {
        this.planner = planner;
        this.workerCount = workerCount;
        
        // Create workers
        for (int i = 0; i < workerCount; i++) {
            XmlWorker worker = planner.createWorker();
            workers.add(worker);
        }
    }
    
    void setWorkerCount(int workerCount) {
        // Only allow changing before start
        if (!running.get()) {
            this.workerCount = workerCount;
        }
    }
    
    void setProducer(XmlProducer producer) {
        this.producer = producer;
    }
    
    @Override
    public ParseHandle onError(ParseHandle.ErrorHandler handler) {
        this.errorHandler = handler;
        return this;
    }
    
    @Override
    public ParseHandle onProgress(ParseHandle.ProgressListener listener) {
        this.progressListener = listener;
        return this;
    }
    
    @Override
    public ParseHandle startAsync() {
        if (running.compareAndSet(false, true)) {
            // Start worker threads
            for (int i = 0; i < workers.size(); i++) {
                XmlWorker worker = workers.get(i);
                Thread t = new Thread(worker, "XML-Worker-" + i);
                t.start();
                workerThreads.add(t);
            }
            
            // Start dispatcher thread
            dispatcherThread = new Thread(this, "XML-Dispatcher");
            dispatcherThread.start();
        }
        return this;
    }
    
    @Override
    public void await() throws InterruptedException {
        completionLatch.await();
    }
    
    @Override
    public void cancel() {
        cancelled.set(true);
        // Interrupt all threads
        for (Thread t : workerThreads) {
            t.interrupt();
        }
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
        // Shutdown workers
        for (XmlWorker w : workers) {
            w.shutdown();
        }
    }
    
    @Override
    public ParseHandle.Stats getStats() {
        return new ParseHandle.Stats(
            filesProcessed.get(),
            filesSucceeded.get(),
            filesFailed.get(),
            bytesProcessed.get(),
            System.currentTimeMillis() - startTime,
            workerCount
        );
    }
    
    @Override
    public boolean isDone() {
        return !running.get() || cancelled.get();
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    @Override
    public boolean hasErrors() {
        return filesFailed.get() > 0;
    }
    
    @Override
    public void run() {
        try {
            if (producer != null) {
                producer.produce(this);
            }
        } catch (Exception e) {
            // Producer error
            if (errorHandler != null) {
                errorHandler.handle("producer", e);
            }
        } finally {
            // Signal workers to shutdown by pushing poison pills
            for (XmlWorker worker : workers) {
                worker.getRunner().push(-1, -1, null); // Poison pill
            }
            
            // Wait for workers to finish
            for (Thread t : workerThreads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {}
            }
            
            running.set(false);
            completionLatch.countDown();
        }
    }
    
    // Called by producer to push work
    boolean pushWork(int begin, int end, String filename) {
        if (cancelled.get()) return false;
        
        // Round-robin to least busy worker
        XmlWorker bestWorker = null;
        int minQueue = Integer.MAX_VALUE;
        
        for (XmlWorker worker : workers) {
            int queueSize = worker.getRunner().getRemain();
            if (queueSize < minQueue) {
                minQueue = queueSize;
                bestWorker = worker;
                if (queueSize == 0) break;
            }
        }
        
        if (bestWorker != null) {
            // push() now blocks until space available (backpressure)
            return bestWorker.getRunner().push(begin, end, filename);
        }
        return false;
    }
    
    // Called by worker when file completes
    void onFileComplete(int begin, int end, boolean success, Exception error) {
        filesProcessed.incrementAndGet();
        bytesProcessed.addAndGet(end - begin);
        
        if (success) {
            filesSucceeded.incrementAndGet();
        } else {
            filesFailed.incrementAndGet();
            if (errorHandler != null && error != null) {
                errorHandler.handle("file", error);
            }
        }
        
        // Report progress
        if (progressListener != null) {
            progressListener.onProgress(new ParseHandle.Progress(
                filesProcessed.get(),
                producer != null ? producer.getTotalFiles() : -1,
                bytesProcessed.get(),
                producer != null ? producer.getTotalBytes() : -1,
                activeWorkers.get(),
                System.currentTimeMillis() - startTime
            ));
        }
    }
    
    void workerStarted() {
        activeWorkers.incrementAndGet();
    }
    
    void workerFinished() {
        activeWorkers.decrementAndGet();
    }
    
    void onProducerError(Exception e) {
        if (errorHandler != null) {
            errorHandler.handle("producer", e);
        }
    }
    
    void onProducerDone() {
        // Producer finished, workers will drain queue
    }
    
    ParseHandle.Progress getProgress() {
        return new ParseHandle.Progress(
            filesProcessed.get(),
            producer != null ? producer.getTotalFiles() : -1,
            bytesProcessed.get(),
            producer != null ? producer.getTotalBytes() : -1,
            activeWorkers.get(),
            System.currentTimeMillis() - startTime
        );
    }
}