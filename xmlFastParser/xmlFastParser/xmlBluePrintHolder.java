package xmlFastParser;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.util.concurrent.locks.LockSupport;


class xmlBluePrintHolder {

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
    private static final VarHandle FULL_FENCE = MethodHandles.fullFenceVarHandle();

    // Add new job to job queue
    boolean pushJob(byte[] start, int length, byte[] name, int nameLength) {
        if (0 != ((cp - pp - 1) & jobQueMask)) {
            int _pp = (pp + 1) & jobQueMask;
            jobName[_pp] = name;
            jobNameLength[_pp] = nameLength;
            jobStart[_pp] = start;
            jobLength[_pp] = length;
            // StoreLoad barrier: ensure element writes visible before pp update
            FULL_FENCE.fullFence();
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
        if (cp != pp) {
            charset = java.nio.charset.StandardCharsets.UTF_8;
            fHeaderCharset = false;            
            encodReading = false;
            pointer = 0;
            currentNode = null;
            xmlState = xmlBluePrint.S_HEADER;
            skipName = 0; skipDept = 0;
            tagName = 0; tagNameEnd = 0;
            attrName = 0; attrEnd = 0;
            value = 0; valEnd = 0; 
            hRootName = 0; hTagName = 0;
            quote = false; dquote = false;

            cp = (cp + 1) & jobQueMask;
            return true;
        } else {
            pointer = jobLength[cp];
            return false;
        }
    }

    /* -- XML STATE RELATE -- */
    int pointer = 0; // index ของ byte ที่กำลังอ่าน 
    int xmlState = xmlBluePrint.S_HEADER; // state of parser

    long skipName = 0;  // unregist blanch currently on
    int skipDept = 0; // dept of unrefist blanch with same name
    int tagName = 0, tagNameEnd = 0; // tagName of current tag
    int attrName = 0, attrEnd = 0; // last attribute name  
    int value = 0, valEnd = 0; // last value or innerText

    long hRootName = 0; // need for identify end of xml 
    long hTagName = 0; // current tag name in hash
    boolean quote = false; // if attribute value start with qoute 
    boolean dquote = false; // if attribute value start with double qoute 
    boolean encodReading = false; // 
    byte[] encodeBuff = new byte[15]; // " UTF-8 "
    boolean fHeaderCharset = false;
    public Charset charset = java.nio.charset.StandardCharsets.UTF_8; // เก็บ charset ของ xmlFile;


    /* -- CONSTRUCTION RELATE -- */    
    xmlBluePrintNode currentNode = null; // current brach
    Thread myThread = null;
    boolean ready2down = true;
    
    private final int jobQueSize = 0x200; // ขนาดของคิวงานที่สามารถเก็บได้ 
    private final int jobQueMask = jobQueSize-1;

    /* -- Ring Type Job Queue size 2^n -- */
    volatile byte[][] jobName = new byte[jobQueSize][]; // byte[] ชื่อไฟล์
    volatile int[] jobNameLength = new int[jobQueSize]; // ขนาดชื่อไฟล์
    volatile byte[][] jobStart = new byte[jobQueSize][]; // byte[] แต่ละงาน
    volatile int[] jobLength = new int[jobQueSize]; // ขนาดของงาน
    volatile int cp = 0;
    volatile int pp = 0;

    xmlBluePrint bluePrint = null;
}
