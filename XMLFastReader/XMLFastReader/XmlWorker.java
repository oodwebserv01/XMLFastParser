package XMLFastReader;

import java.util.concurrent.locks.LockSupport;

/**
 * XmlWorker — Parser Thread (Runnable)
 * 
 * ARCHITECTURE ROLE:
 *   Thread execution unit. Each worker runs in its own thread, owns one XmlRunner,
 *   and continuously pulls work from its runner's queue.
 * 
 * LIFECYCLE:
 *   1. Created by XmlPlanner.createWorker() — binds to shared planner
 *   2. Started via Thread.start() — enters run() loop
 *   3. Loop: pull() → parseRegion() → resetState() → repeat
 *   4. Shutdown via shutdown() → running=false → unpark
 * 
 * WORK PROCESSING:
 *   - Pulls region descriptor {begin, end, filename} from runner's SPSC queue
 *   - Calls runner.parseRegion() which runs the full parse loop
 *   - On exception: fires ERROR event via global handler, then resetState()
 *   - On success: resetState() prepares for next file
 * 
 * SYNCHRONIZATION:
 *   - LockSupport.parkNanos(1ms) when queue empty (low latency, no busy-spin)
 *   - No locks — queue is SPSC (single producer via dispatcher, single consumer)
 *   - XmlRunner state is thread-confined
 * 
 * THREAD NAMING:
 *   Threads named "XML-Worker-N" by XmlDispatcher for debugging/profiling
 */
public final class XmlWorker implements Runnable {

    private final XmlRunner runner;
    private final XmlEvent event = new XmlEvent();
    private volatile boolean running = true;

    /** สร้าง worker ผูกกับ planner (shared read-only) */
    public XmlWorker(XmlPlanner planner) {
        this.runner = new XmlRunner();
        runner.setPlanner(planner);
    }

    /** เข้าถึง runner เพื่อ push งาน (producer เรียก) */
    public XmlRunner getRunner() {
        return runner;
    }

    @Override
    public void run() {
        while (running) {
            int slot = runner.pull();
            if (slot >= 0) {
                int begin = runner.begin[slot];
                int end = runner.end[slot];
                String fn = runner.fileName[slot];
                try {
                    runner.parseRegion(begin, end, fn, event);
                } catch (Exception e) {
                    // เรียก global error handler ถ้ามี
                    XmlCallback eh = runner.planner.getErrorHandler();
                    if (eh != null) {
                        event.reset();
                        event.event = XmlEvent.Event.ERROR;
                        event.token = null;
                        // filename อยู่ใน event จาก FILE_BEGIN แล้ว
                        eh.handle(event);
                    }
                } finally {
                    // Reset state สำหรับไฟล์ถัดไป
                    runner.resetState();
                }
            } else {
                // Queue ว่าง — park รอ producer push
                LockSupport.parkNanos(1_000_000); // 1ms
            }
        }
    }

    /** สั่งหยุด worker (เรียกจาก main thread) */
    public void shutdown() {
        running = false;
        // Unpark ถ้ากำลัง park อยู่
        Thread.currentThread().interrupt(); // Not needed, just for safety
    }
}