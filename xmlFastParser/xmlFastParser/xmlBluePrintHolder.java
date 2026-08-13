package xmlFastParser;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.util.concurrent.locks.LockSupport;


public class xmlBluePrintHolder {

    public byte[] getByteBuffer(){
        if (xmlState <= xmlBluePrint.S_ROOT_OPEN) return null;
        return jobStart[cp] ;
    }

    public int getErrorLocation(){
        return pointer;
    }

    public byte[] getXmlName() {
        return jobName[cp] ;
    }

    public int getXmlNameLength() {
        return jobNameLength[cp] ;
    }

    // VarHandle for StoreLoad barrier (array element visibility) - JDK 9+
// Use static VarHandle.fullFence() method directly

// Add new job to job queue
    boolean pushJob(byte[] start, int length, byte[] name, int nameLength) {
        if (0 != ((cp - pp - 1) & jobQueMask)) {
            int _pp = (pp + 1) & jobQueMask;
            jobName[_pp] = name;
            jobNameLength[_pp] = nameLength;
            jobStart[_pp] = start;
            jobLength[_pp] = length;
            // StoreLoad barrier: ensure element writes visible before pp update
            VarHandle.fullFence();
            pp = _pp;
            LockSupport.unpark(myThread);
            return true;
        } else {
            return false;
        }
    }

    // Check if there is space in the job queue
    int jobQueSpace() {
        return (cp - pp - 1) & jobQueMask;
    }

    // Get job from job queue
    boolean nextJob() {
        if (cp != pp && !xmlBluePrint.forceShutdown) {
            ready2down = false;
            charset = java.nio.charset.StandardCharsets.UTF_8;
            fHeaderCharset = false;
            pointer = 0;
            currentNode = null;
            xmlState = xmlBluePrint.S_HEADER;
            skipDepth = 0;
            skipName = 0;
            tagName = 0; tagNameEnd = 0;
            attrName = 0; attrEnd = 0;
            value = 0; valEnd = 0;
            hRootName = 0; hTagName = 0;

            // Reset log state
            logSize = 0;
            rootOffset = -1;
            lastTargetOffset = -1;
            predictedLog = null;
            predictedIndex = 0;
            predictionActive = false;
            predictionValid = true;
            rootClosed = false;  // Reset root closed flag

            cp = (cp + 1) & jobQueMask;
            return true;
        } else {
            pointer = jobLength[this.cp];
            ready2down = true;
            return false;
        }
    }

    /**
     * Get the target distance log for the current job.
     * Returns arrays of (distance, targetTagHash) pairs.
     */
    public long[] getLogDistances() {
        long[] result = new long[logSize];
        System.arraycopy(logDistances, 0, result, 0, logSize);
        return result;
    }

    public long[] getLogHashes() {
        long[] result = new long[logSize];
        System.arraycopy(logHashes, 0, result, 0, logSize);
        return result;
    }

    public int getLogSize() {
        return logSize;
    }

    /* -- XML STATE RELATE -- */
    int pointer = 0; // index ของ byte ที่กำลังอ่าน
    int xmlState = xmlBluePrint.S_HEADER; // state of parser

    // Simple skip for unregistered branches: track first unregistered tag name + depth
    long skipName = 0;  // hash of first unregistered tag
    int skipDepth = 0;  // nesting depth inside that unregistered branch

    int tagName = 0, tagNameEnd = 0; // tagName of current tag
    int attrName = 0, attrEnd = 0; // last attribute name  
    int value = 0, valEnd = 0; // last value or innerText

    long hRootName = 0; // need for identify end of xml
    long hTagName = 0; // current tag name in hash
    byte[] encodeBuff = new byte[64]; // charset name buffer (was 15)
    boolean fHeaderCharset = false;
    public Charset charset = java.nio.charset.StandardCharsets.UTF_8; // เก็บ charset ของ xmlFile;
    // ============================================================
    // Target Distance Logging & Predictive Shortcuts
    // ============================================================
    private static final int MAX_LOG_TARGETS = 256;
    long[] logDistances = new long[MAX_LOG_TARGETS];
    long[] logHashes = new long[MAX_LOG_TARGETS];
    int logSize = 0;
    int rootOffset = -1;       // byte offset of '<root'
    int lastTargetOffset = -1; // byte offset of last target's '<'

    // Prediction state
    xmlBluePrint.LogEntry predictedLog = null;
    int predictedIndex = 0;
    boolean predictionActive = false;
    boolean predictionValid = true;


    /* -- CONSTRUCTION RELATE -- */    
    xmlBluePrintNode currentNode = null; // current brach
    Thread myThread = null; int threadNo = -1;
    boolean ready2down = true;
    boolean rootClosed = false;  // Track if root close tag was processed
    
    public int getThreadNO() {
        return this.threadNo;
    }

    private final int jobQueSize = 0x200; // ขนาดของคิวงานที่สามารถเก็บได้ 
    private final int jobQueMask = jobQueSize-1;

    /* -- Ring Type Job Queue size 2^n -- */
    volatile byte[][] jobName = new byte[jobQueSize][]; // byte[] ชื่อไฟล์
    volatile int[] jobNameLength = new int[jobQueSize]; // ขนาดชื่อไฟล์
    volatile byte[][] jobStart = new byte[jobQueSize][]; // byte[] แต่ละงาน
    volatile int[] jobLength = new int[jobQueSize]; // ขนาดของงาน
    volatile int cp = 0;
    volatile int pp = 0;
}
