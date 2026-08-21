
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.util.concurrent.locks.LockSupport;


public class xmlBluePrintHolder {

    // ==========================================
    // Interface
    // ==========================================

    // use when user access to error xml
    public byte[] getByteBuffer(){
        if (xmlState <= xmlBluePrint.S_ROOT_BEGIN) return null;
        return jobStart[inprogress] ;
    }

    // use when user want error location
    public int getErrorLocation(){
        return pointer;
    }

    // use when user handle event , this is token user deposit when pushJob
    public Object getTokenFile() {
        return tokenFile[inprogress] ;
    }

    // use when user handle event , require for some algorithm that handle each thread separate
    public int getThreadNO() {
        return this.threadNo;
    }

    // ==========================================
    // Engine
    // ==========================================

    // For ignore tag tracking
    public long ignoreTagHash;           // Hash of tag being ignored
    public int ignoreDepth;              // Nesting depth of ignore tag
    // ThreadContext associated with this holder (set by xmlBluePrint during worker initialization)

    // VarHandle for StoreLoad barrier (array element visibility) - JDK 9+
    // Use static VarHandle.fullFence() method directly

    /*
        nextPush    inprogress   nextPull    readyToDown
        1                  0                1              1                           0 job    0 done  3 free   <start>

        2                  0                1              1                           1 job    0 done  2 free
        3                  0                1              1                           2 job    0 done  1 free
        0                  0                1              1                           3 job    0 done  0 free

        0                  1                1              0                           2 job    0 done  0 free
        0                  1                1              1                           2 job    1 done  0 free
        0                  2                1              0                           1 job    1 done  0 free
        0                  2                1              1                           1 job    2 done  0 free
        0                  3                1              0                           0 job    2 done  0 free
        0                  3                1              1                           0 job    3 done  0 free

        0                  3                2              1                           0 job    2 done  2 free
        0                  3                3              1                           0 job    1 done  3 free
        0                  3                0              1                           0 job    0 done  3 free

     */

    // Check if there is space in the job queue
    int jobQueSpace() {
        return (nextPull - nextPush - 1 ) & jobQueMask;
    };

    // Check if jobs in queue
    int jobsInQue() {
        return (nextPush - inprogress -1) & jobQueMask;
    };

    // Check job already done in queue
    int doneInQue(){
        return (inprogress - nextPull + ready2down) & jobQueMask;
    };

    // Add new job to job queue
    boolean pushJob(byte[] start, int length, Object tokenFile) {
        if (0 != jobQueSpace()) {
            int _nextPush = (nextPush + 1) & jobQueMask;
            this.tokenFile[_nextPush] = tokenFile;
            jobStart[_nextPush] = start;
            jobLength[_nextPush] = length;
            // StoreLoad barrier: ensure element writes visible before nextPush update
            VarHandle.fullFence();
            nextPush = _nextPush;
            LockSupport.unpark(myThread);
            return true;
        } else {
            return false;
        }
    };

    // pull the doned job out of queue
    Object pullJob() {
        if (0 != doneInQue()) {
            Object tokenFile = this.tokenFile[nextPull];
            // StoreLoad barrier: ensure element writes visible before nextPush update
            VarHandle.fullFence();
            nextPull = (nextPull + 1) & jobQueMask;
            LockSupport.unpark(myThread);
            return tokenFile;
        } else {
            return null;
        }
    };

    // Get job from job queue
    boolean nextJob() {
        if (0!=jobsInQue() && !bluePrint.forceShutdown) {
            boolean wasRootClosed = rootClosed;
            ready2down = 0;
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

            // Emit root close event if the previous job did not see a root closing tag.
            // This guarantees that rootHandler receives EV_CLOSE_TAG for every job,
            // even when the root open handler returned false or an error occurred before
            // reaching the actual </root>.
            if (!wasRootClosed && rootHandler != null) {
                rootHandler.call(rootToken, this,
                        bluePrint.EV_CLOSE_TAG, 0, 0, 0, 0);
            }
            rootClosed = false;  // Reset root closed flag for new job

            inprogress = (inprogress + 1) & jobQueMask;
            return true;
        } else {
            pointer = jobLength[this.inprogress];
            ready2down = 1;
            return false;
        }
    }

    xmlBluePrintHolder(int Nof2Power) {
        // Ensure exponent at least 2 (queue size >= 4)
        int size = 1 << ( Nof2Power>1? Nof2Power: 2 ); // 2^Nof2Power
        this.jobQueSize = size;
        this.jobQueMask = size - 1;
        this.tokenFile = new Object[size];
        this.jobStart = new byte[size][];
        this.jobLength = new int[size];
    }


    /**
     * Get the target distance log for the current job.
     * Returns arrays of (distance, targetTagHash) pairs.
     */
    long[] getLogDistances() {
        long[] result = new long[logSize];
        System.arraycopy(logDistances, 0, result, 0, logSize);
        return result;
    }

    long[] getLogHashes() {
        long[] result = new long[logSize];
        System.arraycopy(logHashes, 0, result, 0, logSize);
        return result;
    }

    int getLogSize() {
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
    xmlBluePrint bluePrint = null;
    xmlBluePrintNode currentNode = null; // current brach
    Thread myThread = null; volatile int threadNo = -1;
    boolean ready2down = 1;
    boolean rootClosed = false;  // Track if root close tag was processed
    
    private int jobQueSize;
    private int jobQueMask;

    /* -- Ring Type Job Queue size 2^N -- */
    volatile Object[] tokenFile;
    volatile byte[][] jobStart;
    volatile int[] jobLength;
    volatile int nextPush = 1;
    volatile int inprogress = 0;
    volatile int nextPull =1 ;

}
