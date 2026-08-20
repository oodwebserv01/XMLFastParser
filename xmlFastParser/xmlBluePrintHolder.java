
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.util.concurrent.locks.LockSupport;


public class xmlBluePrintHolder {

    public byte[] getByteBuffer(){
        if (xmlState <= xmlBluePrint.S_ROOT_BEGIN) return null;
        return jobStart[cp] ;
    }

    public int getErrorLocation(){
        return pointer;
    }

    public Object getTokenFile() {
        return tokenFile[cp] ;
    }


    // ==========================================
    // ROUTING CONTEXT (Populated by xmlBluePrint)
    // ==========================================

    // All target hashes for fast lookup during parsing
    public java.util.Set<Long> targetHashes;

    // Current target hash being processed
    public long currentTargetHash;

    // Children of current target for path matching
    public java.util.Map<Long, xmlBluePrintNode> currentChildren;

    // Current target node (for handler/token access)
    public xmlBluePrintNode currentTargetNode;

    // Current child node (for child handler/token access)
    public xmlBluePrintNode currentChildNode;

    // For ignore tag tracking
    public long ignoreTagHash;           // Hash of tag being ignored
    public int ignoreDepth;              // Nesting depth of ignore tag
    // ThreadContext associated with this holder (set by xmlBluePrint during worker initialization)

    // ==========================================
    // HANDLERS & TOKENS
    // ==========================================

    public Object rootIdToken;
    public xmlBluePrintCall rootHandler;
    public xmlBluePrintCall errorHandler;

    public Object errorToken;

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    public boolean isTargetRegistered(long hash) {
        return targetHashes != null && targetHashes.contains(hash);
    }

    public xmlBluePrintNode getChildNode(long hash) {
        return currentChildren != null ? currentChildren.get(hash) : null;
    }

    public String getCurrentPath() {
        if (currentTargetNode == null) return null;
        StringBuilder path = new StringBuilder();
        xmlBluePrintNode node = currentTargetNode;
        while (node != null && node.parent != null && node.parent.tagHash != 0) {
            path.insert(0, "/" + node.tagHash);
            node = node.parent;
        }
        return path.toString();
    }

    // VarHandle for StoreLoad barrier (array element visibility) - JDK 9+
// Use static VarHandle.fullFence() method directly

// Add new job to job queue
    boolean pushJob(byte[] start, int length, Object tokenFile) {
        if (0 != ((cp - pp - 1) & jobQueMask)) {
            int _pp = (pp + 1) & jobQueMask;
            this.tokenFile[_pp] = tokenFile;
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
        if (cp != pp && !bluePrint.forceShutdown) {
            boolean wasRootClosed = rootClosed;
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

            // Emit root close event if the previous job did not see a root closing tag.
            // This guarantees that rootHandler receives EV_CLOSE_TAG for every job,
            // even when the root open handler returned false or an error occurred before
            // reaching the actual </root>.
            if (!wasRootClosed && rootHandler != null) {
                rootHandler.call(rootIdToken, this,
                        bluePrint.EV_CLOSE_TAG, 0, 0, 0, 0);
            }
            rootClosed = false;  // Reset root closed flag for new job

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
    xmlBluePrint bluePrint = null;
    xmlBluePrintNode currentNode = null; // current brach
    Thread myThread = null; volatile int threadNo = -1;
    boolean ready2down = true;
    boolean rootClosed = false;  // Track if root close tag was processed
    
    public int getThreadNO() {
        return this.threadNo;
    }

    private int jobQueSize;
    private int jobQueMask;

    /* -- Ring Type Job Queue size 2^N -- */
    volatile Object[] tokenFile;
    volatile byte[][] jobStart;
    volatile int[] jobLength;
    volatile int cp;
    volatile int pp;

    public xmlBluePrintHolder(int Nof2Power) {
        // Ensure exponent at least 1 (queue size >= 2)
        if (Nof2Power < 1) Nof2Power = 1;
        int size = 1 << Nof2Power; // 2^Nof2Power
        this.jobQueSize = size;
        this.jobQueMask = size - 1;
        this.tokenFile = new Object[size];
        this.jobStart = new byte[size][];
        this.jobLength = new int[size];
    }
    
}
