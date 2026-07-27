package XMLFastReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * XmlFastReader — Main Entry Point (Facade)
 * 
 * ARCHITECTURE ROLE:
 *   High-level API facade. Hides internal complexity (planner, dispatcher, workers,
 *   producers) behind simple register/parse/shutdown methods.
 * 
 * USAGE PATTERN:
 *   XmlFastReader reader = new XmlFastReader();
 *   reader.register("Invoice/ID", handler, "INV_ID");
 *   reader.register("Invoice/Line/Item", handler, "LINE_ITEM");
 *   ParseHandle handle = reader.parseZip(new File("data.zip"), 8);
 *   handle.onError((file, err) -> log.error(file, err));
 *   handle.await();
 *   reader.shutdown();
 * 
 * THREAD MODEL:
 *   - Main thread: register(), parse(), shutdown()
 *   - Dispatcher thread: runs producer, distributes work
 *   - N Worker threads: each runs XmlWorker → XmlRunner.parseRegion()
 * 
 * Algorithm by "Ood Kritsana Wuttisin" from Thailand
 * AI by Openrouter
 */
public final class XmlFastReader {
    
    private final XmlPlanner planner;
    private final XmlDispatcher dispatcher;
    private final List<Thread> workerThreads = new ArrayList<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    public XmlFastReader() {
        this.planner = new XmlPlanner();
        this.dispatcher = new XmlDispatcher(planner, Runtime.getRuntime().availableProcessors());
    }
    
    // ===== Registration API =====
    
    /** Register path to extract */
    public void register(String path, XmlCallback handler, Object token) {
        planner.register(path, handler, token);
    }
    
    /** Register file begin handler */
    public void onFileBegin(XmlCallback handler) {
        planner.registFileBegin(handler);
    }
    
    /** Register file end handler */
    public void onFileEnd(XmlCallback handler) {
        planner.registFileEnd(handler);
    }
    
    /** Register error handler */
    public void onError(XmlCallback handler) {
        planner.registError(handler);
    }
    
    // ===== Parse API =====
    
    /** Parse from ZIP file with specified thread count */
    public ParseHandle parseZip(File zipFile, int threads) throws IOException {
        return parse(new FileZipProducer(zipFile), threads);
    }
    
    /** Parse from InputStream with specified thread count */
    public ParseHandle parseStream(InputStream inputStream, int threads) throws IOException {
        return parse(new StreamingProducer(inputStream), threads);
    }
    
    /** Parse from single XML file with specified thread count */
    public ParseHandle parseFile(File xmlFile, int threads) throws IOException {
        return parse(new SingleFileProducer(xmlFile), threads);
    }
    
    /** Parse from producer with specified thread count */
    public ParseHandle parse(XmlProducer producer, int threads) throws IOException {
        if (shutdown.get()) throw new IllegalStateException("Reader already shutdown");
        
        // Recreate dispatcher with specified thread count
        this.dispatcher.setWorkerCount(threads);
        
        // Start worker threads
        for (int i = 0; i < threads; i++) {
            XmlWorker worker = planner.createWorker();
            Thread t = new Thread(worker, "XML-Worker-" + i);
            t.start();
            workerThreads.add(t);
        }
        
        // Set producer and start dispatcher
        dispatcher.setProducer(producer);
        dispatcher.startAsync();
        
        return dispatcher;
    }
    
    // ===== Convenience methods (default threads) =====
    
    /** Parse from ZIP file (default threads) */
    public ParseHandle parseZip(File zipFile) throws IOException {
        return parseZip(zipFile, Runtime.getRuntime().availableProcessors());
    }
    
    /** Parse from InputStream (default threads) */
    public ParseHandle parseStream(InputStream inputStream) throws IOException {
        return parseStream(inputStream, Runtime.getRuntime().availableProcessors());
    }
    
    /** Parse from single XML file (default threads) */
    public ParseHandle parseFile(File xmlFile) throws IOException {
        return parseFile(xmlFile, Runtime.getRuntime().availableProcessors());
    }
    
    // ===== Progress/Status =====
    
    /** Set progress listener */
    public void setProgressListener(ParseHandle.ProgressListener listener) {
        dispatcher.onProgress(listener);
    }
    
    /** Get current progress */
    public ParseHandle.Progress getProgress() {
        return dispatcher.getProgress();
    }
    
    // ===== Lifecycle =====
    
    /** Shutdown reader and all workers */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            dispatcher.cancel();
            for (Thread t : workerThreads) {
                t.interrupt();
            }
            for (Thread t : workerThreads) {
                try {
                    t.join(5000);
                } catch (InterruptedException ignored) {}
            }
            workerThreads.clear();
        }
    }
}