package xmlFastParser;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class xmlBluePrint {
/*
Usage Instructions
 - Register all paths you want to | XML tag paths you want to extract data from
    rootRegist(xmlBluePrintCallR, idTokenR);
    errorRegist(xmlBluePrintCallE, idTokenE);
    regist("/firstLevelBlanch/secondLevelBlanch/thirdLevelBlanch/tagNameA", xmlBluePrintCall1, idTokenX);
    regist("/firstLevelBlanch/secondLevelBlanch/tagNameB", xmlBluePrintCall2, idTokenY);

 - After registering all paths, set the number of threads to use for data processing
    setThreadCount(n);   

 - Check if the job queue has space available. If there is space, you can submit jobs

    // get first xml byte[] => src 
    while (0 < remainingJob) {
        if (0 == xmlBluePrint.jobQueSpace()) {
            xmlBluePrint.run();
            sleep(50);
        } else {
            xmlBluePrint.pushJob(src.pointer, src.length, src.xmlName, src.xmlNameLength);
        }
    }
    xmlBluePrint.run();
    
 - Waiting for events
    -- Implement xmlBluePrintCall to receive
      --- idToken {Object you specified during registration, used to identify which path this event belongs to}
      --- xmlBluePrintHolder { Contains data for the job being processed }
      --- event { 0 = unknown_error, 1 = openTag, 2 = closeTag, 3 = attribute, 4 = innerText }
      --- attributeName { byte[] name of the attribute of the found tag }
      --- attributeNameLength { int length of the attribute name }
      --- attributeValue { byte[] value of the found attribute OR innerText of the found tag } 
      --- attributeValueLength { int length of the attribute value OR innerText of the found tag }

 */
   public static void regist(String path, xmlBluePrintCall handler, Object token) {
       // Check if path is null, empty, or doesn't start with '/'
       if (path == null || path.isEmpty() || path.charAt(0) != '/') {
           throw new IllegalArgumentException("Path must start with '/'");
       }
       
       // Split path by '/', skip empty first element
       String[] segments = path.substring(1).split("/");
       // Check if path has at least one segment after splitting
       if (segments.length == 0) {
           throw new IllegalArgumentException("Path must have at least one segment");
       }
       
       xmlBluePrintNode current = root;
       
       for (int i = 0; i < segments.length; i++) {
           String segment = segments[i];
           // Check for empty segment in path (consecutive slashes or trailing slash)
           if (segment.isEmpty()) {
               throw new IllegalArgumentException("Empty segment in path: " + path);
           }
           
           // Compute hash for this segment
           byte[] segBytes = segment.getBytes();
           long tagHash = hash(segBytes, segBytes.length);
           
           // Last segment = TARGET, others = path nodes
           boolean isTarget = (i == segments.length - 1);
           
           // CHILD_OF_TARGET if parent is a target
           boolean isChildOfTarget = current.isTarget;
           
           current = current.getOrCreateChild(tagHash, 
                                               isTarget ? handler : null,
                                               isTarget ? token : null,
                                               isTarget,
                                               isChildOfTarget, HD_CLOSINGTAG);
       }
   };

   public static void setThreadCount(int c) {
      threadCount = c;
      holders = new xmlBluePrintHolder[c];
      workerThreads = new Thread[c];
   };

   static final boolean isSpace(byte x){
      return (' '==x || '\r'==x || '\n'==x || '\t'==x);
   };

   static final long hash(byte[] buff, int length) { // FNV_1a
      return hash(buff, 0, length);
   };
   
   static final long hash(byte[] buff, int begin, int end) { // FNV_1a
      long h = 0xCBF29CE484222325L; // FNV offset basis
      for (int i = begin; i < end; i++) {
         byte v = buff[i];
         if (v == ':' || v == '}') break;
         h ^= (v & 0xFF);
         h *= 0x100000001B3L;      // FNV prime
      }
      // SplitMix64 finalizer
      h ^= h >>> 33;
      h *= 0xFF51AFD7ED558CCDL;
      h ^= h >>> 33;
      h *= 0xC4CEB9FE1A85EC53L;
      h ^= h >>> 33;
      return h;
   };

   static xmlBluePrintNode root = new xmlBluePrintNode(); // root of structure Tree
   static xmlBluePrintCall rootHandler = null;
   static Object rootToken = null;

   public static void rootRegist(xmlBluePrintCall handler, Object token) {
       rootHandler = handler;
       rootToken = token;
       root.HD_CLOSINGTAG = HD_CLOSINGROOT;
   };

   // Error handler and token (set via errorRegist)
   private static xmlBluePrintCall errorHandler = null;
   private static Object errorToken = null;

   public static void errorRegist(xmlBluePrintCall handler, Object token) {
       errorHandler = handler;
       errorToken = token;
   };

   // set number of worker thread 
   private static int threadCount = 0;
   private static Thread[] workerThreads = null;
   private static Thread distributorThread = null;
   private static xmlBluePrintHolder[] holders = null;
   static volatile boolean running = false; // Used to start only once
   static volatile boolean paused = false; // Used to freeze job
   static volatile boolean shuttingdown = false; // Used to notify shutting down
   static volatile boolean forceShutdown = false; // Used to force shutdown

   public void run(){
      // Check if not already running to prevent multiple starts
      if (!running) {
         root.handler = rootHandler;
         root.parent = root;
         root.isChildOfTarget = false;
         root.isTarget = false;
         root.idToken = rootToken;
         // Validate thread count is positive
         if (threadCount <= 0) {
            throw new IllegalStateException("threadCount must be > 0, call setThreadCount() first");
         }

         // set state as Running 
         running = true; paused = false; 

         for (int i = 0; i < this.threadCount; i++) {
            this.holders[i] = new xmlBluePrintHolder();
            this.holders[i].bluePrint = this;
            final int holderIndex = i;
            this.workerThreads[i] = new Thread(() -> {
               xmlBluePrintHolder holder = holders[holderIndex];
               holder.myThread = Thread.currentThread();

               // wait until job come
               while (!shuttingdown && (holder.cp == holder.pp)) LockSupport.parkNanos(5_000_000L);

               while (!shuttingdown ) {
                  // Handle pause - park until resumed
                  while (!shuttingdown && paused) LockSupport.parkNanos(50_000_000L);

                  // Process entire XML document ( nextJob via handler on <loot/> )
                  //--- this is core of algorithm PLAN_A
                  holder.pointer = 0;
                  holder.ready2down = false;
                  while (holder.pointer < holder.jobLength[holder.cp]) {
                     TOC[holder.xmlState][holder.jobStart[holder.cp][holder.pointer]]
                        .call(holder);
                  }

                  // This area access when EOF before </root> and when Shutting down 
                  if (!shuttingdown && (holder.cp != holder.pp)) {
                     // EOF reached without </root> -> error
                     this.errorHandler.call(this.errorToken, holder, EV_EOF_IN_ROOT, 0, 0, 0, 0);

                     while (!shuttingdown && !holder.nextJob()) LockSupport.parkNanos(5_000_000L);
                  }
                  holder.ready2down = shuttingdown;
               }

            }, "xml-worker-" + i);
            this.workerThreads[i].start();
         }

         this.distributorThread = new Thread(this::jobDistributor, "xml-distributor");
         this.distributorThread.start();         
      }
   };

   /**
    * Pause processing - workers finish current job then park.
    * Distributor stops distributing but stays alive.
    */
   public void pause() {
      paused = true;
      // Wake up distributor so it can check paused flag
      if (distributorThread != null) {
         LockSupport.unpark(distributorThread);
      }
   };

   /**
    * Resume processing after pause.
    */
   public void resume() {
      paused = false;
      // Wake up all threads
      if (distributorThread != null) {
         LockSupport.unpark(distributorThread);
      }
      if (workerThreads != null) {
         for (Thread t : workerThreads) {
            if (t != null) LockSupport.unpark(t);
         }
      }
   };

   /**
    * Graceful shutdown - waits for all queued jobs to complete before stopping threads.
    */
   public void shutdown(boolean force) {
      forceShutdown |= force;
      if (shuttingdown) return; // already shuttingdown
      shuttingdown = true;
      
      // Wake up distributor to exit its wait loop
      if (distributorThread != null) {
         LockSupport.unpark(distributorThread);
      }
      
      // Wake up all worker threads
      if (workerThreads != null) {
         for (Thread t : workerThreads) {
            if (t != null) LockSupport.unpark(t);
         }
      }

      // Wait for main queue to drain
      while (!forceShutdown && cp != pp) LockSupport.parkNanos(1_000_000L);
      // Join distributor
      if (distributorThread != null) {
         try { distributorThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      }



      // wait for all workers done
      boolean ready2down,jobDone,ready;
      do {
         ready2down = true; jobDone=true;
         for (int i = this.holders.length -1; i>=0; i--) {
            ready2down &= this.holders[i].ready2down;
            jobDone &= (this.holders[i].cp == this.holders[i].pp);
         }
         ready = ready2down && (forceShutdown || jobDone);
         if (!ready) LockSupport.parkNanos(50_000_000L);
      } while (!ready);
      // Join all workers
      if (workerThreads != null) {
         for (Thread t : workerThreads) {
            if (t != null) {
               try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
         }
      }
      
      // Clean up
      distributorThread = null;
      workerThreads = null;
      holders = null;
      running = false;
   };

   private final int jobQueSize = 0x4000;
   private final int jobQueMask = jobQueSize-1;
       /* Ring Type Job Queue size 2^14 */
   private volatile byte[][] jobName = new byte[jobQueSize][]; // byte[] ชื่อไฟล์
   private volatile int[] jobNameLength = new int[jobQueSize]; // ขนาดชื่อไฟล์
   private volatile byte[][] jobStart = new byte[jobQueSize][]; // byte[] แต่ละงาน
   private volatile int[] jobLength = new int[jobQueSize]; // ขนาดของงาน
   private volatile int cp = 0; // ชี้ งานรออ่าน
   private volatile int pp = 0; // ชี้ที่ว่างต่อไป รองานเข้า

   // VarHandle for StoreLoad barrier (array element visibility) - JDK 9+
   private static final VarHandle FULL_FENCE = MethodHandles.fullFenceVarHandle();

   // Check is there space in Queue
   int jobQueSpace() {
      return (cp - pp - 1) & jobQueMask;
   };   

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
         
         if (distributorThread != null) {
            LockSupport.unpark(distributorThread);
         }
         return true;
      } else {
         return false;
      }
   };

   boolean nextJob() {
      int next = (cp + 1) & jobQueMask;
      if (next != pp) {
         cp = next;
         return true;
      } else {
         return false;
      }
   };

   private void jobDistributor() {
      
      // wait until job come
      while (cp==pp) LockSupport.parkNanos(10_000_000L);

      int HolderIndex = 0;
      // Distribute job at current cp
      int pushed = 0;
      // Now cp points to first valid job
      while ( !(shuttingdown && cp == pp) && !forceShutdown) {

         // Handle pause - wait until resumed
         while (paused) LockSupport.parkNanos(50_000_000L);

         if (holders[HolderIndex].pushJob(jobStart[cp], jobLength[cp], jobName[cp], jobNameLength[cp])) {
            pushed++;
            
            // wait until at least 1 job
            while (!shuttingdown && !nextJob()) LockSupport.parkNanos(50_000_000L); 
         }
         HolderIndex = (HolderIndex + 1 ) % threadCount;
         
         if (0==HolderIndex && 0==pushed) {
            // all holders full
            if (!shuttingdown) LockSupport.parkNanos(10_000_000L);  
         } else {
            pushed = 0; // reset counter;
         }
      }      
   };

   // ============================================================
   // State Constants
   // ============================================================
   public static final int xmlState_MAX = 53;

   // OUTSIDE ROOT (0-7)
   public static final int S_HEADER                = 0;
   public static final int S_HEAD_LT               = 1;
   public static final int S_HEADER_BANG           = 2;
   public static final int S_HEADER_PI             = 3;   
   public static final int S_HEADER_CHAR           = 4;   
   public static final int S_HEADER_DASH           = 5;
   public static final int S_HEADER_2DASH          = 6;
   public static final int S_HEADER_MINUS_DASH     = 7;  
   public static final int S_HEADER_MINUS_2DASH    = 8;  
   public static final int S_HEADER_X              = 9;
   public static final int S_HEADER_XM             = 10;
   public static final int S_HEADER_XML             = 11;   
   public static final int S_HEADER_ATTR             = 12;   
   public static final int S_HEADER_E             = 13;  
   public static final int S_HEADER_C             = 14;  
   public static final int S_EN             = 15;  
   public static final int S_ENC             = 16;  
   public static final int S_ENCO             = 17;  
   public static final int S_ENCOD             = 18; 
   public static final int S_ENCODI            = 19; 
   public static final int S_ENCODIN           = 20; 
   public static final int S_ENCODING          = 21; 
   public static final int S_ENC_VALUE         = 22; 
   public static final int S_ENC_QUOTE         = 23; 
   public static final int S_ENC_DQUOTE        = 24; 
   public static final int S_ENC_CHARSET       = 25;
   public static final int S_CHR_C            = 26; 
   public static final int S_CHR_CH           = 27; 
   public static final int S_CHR_CHA          = 28; 
   public static final int S_CHR_CHAR         = 29;  
   public static final int S_CHR_CHARSE        = 30; 
   public static final int S_CHR_CHARSET       = 31; 
   public static final int S_HEAD_CHAR       = 32; 
   public static final int S_ROOT_OPEN       = 33; 
   public static final int S_NEXT_XML       = 34; 
   public static final int S_TARGET_INNER     = 35; 
   public static final int S_TARGET_ATTR     = 36; 
   public static final int S_VAL_ATTR     = 37;    
   public static final int S_QUOTE_VALUE     = 38;   
   public static final int S_DQUOTE_VALUE     = 39;   
   public static final int S_VAL_VALUE     = 40;  
   public static final int S_LT     = 41;  
   public static final int S_BANG     = 42;  
   public static final int S_DASH     = 43;  
   public static final int S_2DASH     = 44;  
   public static final int S_MINUS_DASH     = 45;  
   public static final int S_MINUS_2DASH     = 46;  
   public static final int S_PI     = 47;  
   public static final int S_TAG_OPEN     = 48;  
   public static final int S_ATTR     = 49;  
   public static final int S_INNER     = 50;  
   public static final int S_SLASH     = 51;  
   public static final int S_GT_ONLY     = 52;  
   public static final int S_UNREGIST_BRANCH     = 53;  

   // CHILD TAG (18-20)
   public static final int CHILD_OPEN         = 18;
   public static final int CHILD_INNER        = 19;
   public static final int CHILD_CLOSE        = 20;

   // Event types (สำหรับ handler interface)
   public static final int EV_OPEN_TAG    = 1;
   public static final int EV_CLOSE_TAG   = 2;
   public static final int EV_ATTR        = 3;
   public static final int EV_INNER_TEXT  = 4;
  
   public static final int EV_UNKNOWN_ERROR = 0;
   public static final int EV_EOF_IN_ROOT = 1001;
   public static final int EV_LT_IN_TAG = 1002;
   public static final int EV_END_NE_BEGIN = 1003;

   /*--- TOC Handlling--- */

   private static void HD_NEXT_XML(xmlBluePrintHolder holder) {
      while (!shuttingdown && !holder.nextJob()) LockSupport.parkNanos(5_000_000L);      
   };

   private static final CELL HD_CLOSINGROOT = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         root.handler.call(rootToken,holder,EV_CLOSE_TAG,0,0,0,0);
         HD_NEXT_XML(holder);
      }
   };   

   private static final CELL HD_CLOSINGTAG = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         if (holder.currentNode.isTarget) {
            if (! holder.currentNode.handler.call(holder.currentNode.idToken,holder,EV_CLOSE_TAG,holder.tagName,holder.tagNameEnd,0,0)) {
               HD_NEXT_XML(holder);
               return ;
            }
         }
         holder.currentNode = holder.currentNode.parent;
         holder.xmlState = (holder.currentNode.isTarget)? S_TARGET_INNER: S_INNER;
         holder.value = holder.pointer;
      }
   };      

   public CELL[][] TOC = new CELL[xmlState_MAX][256];

   // Singleton ignore handler - default for all TOC entries
   private static final CELL HD_SAMESTATE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
          holder.pointer++;
          return ;
      }
   };

   private static final CELL SKIP2_HANDLER = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
          holder.pointer += 2;
          return ;
      }
   };

   private static final CELL SKIP3_HANDLER = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
          holder.pointer += 3;
          return ;
      }
   };

   private static final CELL SKIP4_HANDLER = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
          holder.pointer += 4;
          return ;
      }
   };

   private static final CELL HD_HEADER = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER;
         return ;
      }
   };     

   private static final CELL HD_HEAD_LT = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEAD_LT;
         return ;
      }
   };

   private static final CELL HD_HEADER_BANG = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_BANG;
         return ;
      }
   };      

   private static final CELL HD_HEADER_DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_DASH;
         return ;
      }
   };  

   private static final CELL HD_HEADER_2DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_2DASH;
         return ;
      }
   };     

   private static final CELL HD_HEADER_MINUS_DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_MINUS_DASH;
         return ;
      }
   };        

   private static final CELL HD_HEADER_MINUS_2DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_MINUS_2DASH;
         return ;
      }
   };       
   
   private static final CELL HD_HEADER_PI = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_PI;
         return ;
      }
   };     

   private static final CELL HD_HEADER_X = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_X;
         return ;
      }
   };    

   private static final CELL HD_HEADER_XM = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_XM;
         return ;
      }
   };

   private static final CELL HD_HEADER_XML = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_XML;
         return ;
      }
   };   

   private static final CELL HD_HEADER_ATTR = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_XML;
         return ;
      }
   };      

   private static final CELL HD_HEADER_E = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_HEADER_E;
         return ;
      }
   };      

   private static final CELL HD_EN = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_EN;
         return ;
      }
   };   

   private static final CELL HD_ENC = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENC;
         return ;
      }
   };  

   private static final CELL HD_ENCO = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENCO;
         return ;
      }
   };  

   private static final CELL HD_ENCOD = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENCOD;
         return ;
      }
   };  

   private static final CELL HD_ENCODI = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENCODI;
         return ;
      }
   };  

   private static final CELL HD_ENCODIN = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENCODIN;
         return ;
      }
   };  

   private static final CELL HD_ENCODING = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENCODING;
         return ;
      }
   };  

   private static final CELL HD_ENC_VALUE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENC_VALUE;
         return ;
      }
   };  

   private static final CELL HD_ENC_QUOTE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENC_QUOTE;
         holder.value = holder.pointer;
         return ;
      }
   };  

   private static final CELL HD_ENC_Q_VALUE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = ++holder.pointer; 

         // Extract charset value from jobStart[cp] between value and valEnd-1
         byte[] buf = holder.jobStart[holder.cp];
         int valueStart = holder.value;
         int valueEnd = holder.valEnd - 1; // exclusive end

         // let trim befor anything
         while (valueStart<=valueEnd && isSpace(buf[valueStart]))valueStart++;
         while (valueStart<=valueEnd && isSpace(buf[valueEnd]))valueEnd--;

         if (valueEnd > valueStart) {
            // Extract the raw value bytes
            int rawLen = valueEnd - valueStart;
            System.arraycopy(buf, valueStart, holder.encodeBuff, 0, rawLen);
            
            // Convert to string for processing
            String finalValue = new String(holder.encodeBuff,0,rawLen);
            
            // Try to create Charset object
            try {
               holder.charset = java.nio.charset.Charset.forName(finalValue);
               holder.fHeaderCharset = true;
            } catch (Exception e) {
               // Invalid charset, keep default (null)
            }
         }         
         holder.xmlState = S_HEADER;
         return ;
      }
   };  

   private static final CELL HD_ENC_DQUOTE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENC_DQUOTE;
         holder.value = holder.pointer;
         return ;
      }
   };  

   private static final CELL HD_ENC_CHARSET = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_ENC_CHARSET;
         holder.value = holder.pointer-1;
         return ;
      }
   };  

   // เริ่มต้นเส้นทาง charset จาก S_HEADER_ATTR
   private static final CELL HD_CHR_C = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_CHR_C;
         return ;
      }
   };

   private static final CELL HD_CHR_CH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_CHR_CH;
         return ;
      }
   };

   private static final CELL HD_CHR_CHA = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_CHR_CHA;
         return ;
      }
   };

   private static final CELL HD_CHR_CHAR = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_CHR_CHAR;
         return ;
      }
   };

   private static final CELL HD_CHR_CHARSE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_CHR_CHARSE;
         return ;
      }
   };

   private static final CELL HD_CHR_CHARSET = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_CHR_CHARSET;
         return ;
      }
   };   

   private static final CELL HD_HEAD_CHAR = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.tagName = holder.pointer++;
         holder.xmlState = S_HEAD_CHAR;
         return ;
      }
   };   

   private static final CELL HD_CHK_ROOT = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         if ('/' == holder.jobStart[holder.cp][holder.pointer-1]) {
            holder.pointer++;
            holder.xmlState = S_HEADER;
         } else {
            holder.pointer = holder.tagName+1;
            holder.xmlState = S_ROOT_OPEN;
         }
         return ;
      }
   };   

   private static final CELL HD_ROOT_OPEN = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.tagNameEnd = holder.pointer++;
         holder.hRootName = hash(holder.jobStart[holder.cp],holder.tagName,holder.tagNameEnd);
         holder.currentNode = root;
         if (! rootHandler.call(rootToken,holder,EV_OPEN_TAG,holder.tagName,holder.tagNameEnd,0,0)) {
            HD_NEXT_XML(holder);
            return ;
         }
         if ('>' == holder.jobStart[holder.cp][holder.pointer-1]) { //  rootTag closing here
            holder.xmlState = S_TARGET_INNER;
            holder.value = holder.pointer;
         } else { // rootTag attr here
            holder.xmlState = S_TARGET_ATTR;
            holder.valEnd = holder.value = holder.attrName = holder.pointer;
         } 
         return ;
      }
   };   

   private static final CELL HD_NOVAL_ATTR = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.attrEnd = holder.pointer++;
         boolean isLastAttr = ('>' == holder.jobStart[holder.cp][holder.pointer-1]);
         boolean isSelfClose = (isLastAttr && '/' == holder.jobStart[holder.cp][holder.pointer-2]);
         if (isSelfClose) {
            holder.attrEnd--;
         } 

         if (holder.attrEnd > holder.attrName) {
            // callback to user
            if (!holder.currentNode.handler.call(holder.currentNode.idToken, holder, EV_ATTR, holder.attrName, holder.attrEnd, 0, 0)){
               HD_NEXT_XML(holder);
               return;
            }
         }

         // case this is self ending tag
         if (isSelfClose) { 
            if (! holder.currentNode.handler.call(holder.currentNode.idToken, holder, EV_CLOSE_TAG, 0, 0, 0, 0)) {
               HD_NEXT_XML(holder);
               return;
            } else {
               holder.currentNode = holder.currentNode.parent;
               holder.xmlState = S_TARGET_INNER;   
               holder.value = holder.pointer;
               return;   
            } 
         }

         // case this is last attribute
         if (isLastAttr) { 
            holder.xmlState = S_TARGET_INNER;
            holder.value = holder.pointer;
            return;
         }

         // case continue to read next attr
         holder.xmlState = S_TARGET_ATTR;
         holder.attrName = holder.pointer;
         return ;
      }
   };

   private static final CELL HD_VAL_ATTR = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.attrEnd = holder.pointer++;
         holder.xmlState = S_VAL_ATTR;
         holder.value = holder.pointer;
         return;
      }
   };

   private static final CELL HD_VAL_QUOTE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = holder.value = ++holder.pointer;
         holder.xmlState = S_QUOTE_VALUE;
         return;
      }
   };

   private static final CELL HD_VAL_DQUOTE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = holder.value = ++holder.pointer;
         holder.xmlState = S_DQUOTE_VALUE;
         return;
      }
   };

   private static final CELL HD_VAL_VALUE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = holder.value = ++holder.pointer;
         holder.xmlState = S_VAL_VALUE;
         return;
      }
   };

   private static final CELL HD_END_VALUE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = holder.pointer++;
         boolean isLastAttr = ('>' == holder.jobStart[holder.cp][holder.pointer-1]);
         boolean isSelfClose = (isLastAttr && '/' == holder.jobStart[holder.cp][holder.pointer-2]);

         // case self close tag : remove '/' from last char
         if (isSelfClose) holder.valEnd--; 

         if (holder.attrEnd > holder.attrName) {
            // callback to user
            if (! holder.currentNode.handler.call(holder.currentNode.idToken, holder, EV_ATTR, holder.attrName, holder.attrEnd, holder.value, holder.valEnd)){
               HD_NEXT_XML(holder);
               return;
            }
         }

         // case this is self ending tag
         if (isSelfClose) { 
            if (! holder.currentNode.handler.call(holder.currentNode.idToken, holder, EV_CLOSE_TAG, 0, 0, 0, 0)) {
               HD_NEXT_XML(holder);
               return;
            } else {
               holder.currentNode = holder.currentNode.parent;
               holder.xmlState = S_TARGET_INNER;    
               holder.value = holder.pointer;
               return;   
            } 
         }

         // case this is last attribute
         if (isLastAttr) { 
            holder.xmlState = S_TARGET_INNER;
            holder.value = holder.pointer;
            return;
         }

         // case continue to read next attr
         holder.xmlState = S_TARGET_ATTR;
         holder.attrName = holder.pointer;
         return ;
      }
   };

   private static final CELL HD_TARGET_LT = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = holder.pointer++;
         if (! holder.currentNode.handler.call(holder.currentNode.idToken, holder, EV_INNER_TEXT, 0, 0, holder.value, holder.valEnd)) {
            HD_NEXT_XML(holder);
            return;
         }             
         holder.xmlState = S_LT;
         return;
      }
   };

   private static final CELL HD_INNER = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         if (holder.currentNode.isTarget) {
            holder.value = holder.pointer++;
            if ('<' == holder.jobStart[holder.cp][holder.pointer-2]) holder.value--;
            holder.xmlState = S_TARGET_INNER;
         } else {
            holder.pointer++;         
            holder.xmlState = S_INNER;   
         }
         return;
      }
   };

   private static final CELL HD_BANG = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_BANG;
         return ;
      }
   };      

   private static final CELL HD_DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_DASH;
         return ;
      }
   };  

   private static final CELL HD_2DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_2DASH;
         return ;
      }
   };     

   private static final CELL HD_MINUS_DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_MINUS_DASH;
         return ;
      }
   };        

   private static final CELL HD_MINUS_2DASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_MINUS_2DASH;
         return ;
      }
   };       

   private static final CELL HD_PI = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++;
         holder.xmlState = S_PI;
         return ;
      }
   };       

   private static final CELL HD_CHAR = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.tagName = holder.pointer++;
         holder.xmlState = S_TAG_OPEN;
         return ;
      }
   };     

   private static final CELL HD_OPENTAG = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.tagNameEnd = holder.pointer++;
         boolean isClosed = ('>'==holder.jobStart[holder.cp][holder.pointer-1]);
         boolean isSelfClosed = ('/'==holder.jobStart[holder.cp][holder.pointer-2]);
         if (isSelfClosed) holder.tagNameEnd--;

         long hTagName = hash(holder.jobStart[holder.cp], holder.tagName, holder.tagNameEnd);
         
         if (holder.skipDept > 0) {
            if ( hTagName == holder.skipName) holder.skipDept++;
            holder.xmlState = S_UNREGIST_BRANCH;
            return;
         }

         if (holder.currentNode.hasChild(hTagName)) {
            // case registed Node
            holder.currentNode = holder.currentNode.getChild(hTagName);
            
            // Send OPEN_TAG callback for target nodes
            if (holder.currentNode.isTarget ) {
               if (!holder.currentNode.handler.call(
                  holder.currentNode.idToken, holder, 
                  EV_OPEN_TAG, 
                  holder.tagName, holder.tagNameEnd, 
                  0, 0)) {
                  HD_NEXT_XML(holder);
                  return;
               }
            }
            
            if (isClosed) {
               // case tag closed with >
               if (holder.currentNode.isTarget) {
                  // case target tag need to cap inner
                  holder.xmlState = S_TARGET_INNER;
                  holder.value = holder.pointer;
               } else {
                  // case non target tag
                  holder.xmlState = S_INNER;
               }
            } else if (isSelfClosed) {
               // case self-closed tag />
               // Send CLOSE_TAG callback for target nodes
               if (holder.currentNode.isTarget) {
                  if (!holder.currentNode.handler.call(
                     holder.currentNode.idToken, holder, 
                     EV_CLOSE_TAG, 
                     0, 0, 0, 0)) {
                     HD_NEXT_XML(holder);
                     return;
                  }
               }
               // Move back to parent
               holder.currentNode = holder.currentNode.parent;
               // Set appropriate state
               if (holder.currentNode.isTarget) {
                  holder.xmlState = S_TARGET_INNER;
                  holder.value = holder.pointer;
               } else {
                  holder.xmlState = S_INNER;
               }
            } else {
               // case tag has attributes (ends with space or other)
               if (holder.currentNode.isTarget) {
                  // case target tag need to cap attrs
                  holder.xmlState = S_TARGET_ATTR;
                  holder.attrName = holder.pointer; // Start of attribute name
                  holder.valEnd = holder.value = holder.attrName;
               } else {
                  // case non target tag just looking for >
                  holder.xmlState = S_ATTR;
               }
            }
         } else {
            // case unregisted node
            // Store hash in skipName and increment skipDept
            holder.skipName = hTagName;
            holder.skipDept++;
            holder.xmlState = S_UNREGIST_BRANCH;
         }
         return ;
      }
   };

   private static final CELL HD_TAG_CLOSE = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.pointer++; // Move past the '>' character
         boolean isSelfClosed = ('/'==holder.jobStart[holder.cp][holder.pointer-2]);
         if (isSelfClosed) {
            holder.currentNode = holder.currentNode.parent;
            if (holder.currentNode.isTarget) {
               holder.xmlState = S_TARGET_INNER;
               holder.value = holder.pointer;
            } else {
               holder.xmlState = S_INNER;
            }
            return;
         } 

         holder.xmlState = S_INNER;
         return;
      }
   };

   private static final CELL HD_LT = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.valEnd = holder.pointer++;
         holder.xmlState = S_LT;
         return;
      }
   };

   private static final CELL HD_SLASH = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.tagName = ++holder.pointer;
         holder.xmlState = S_SLASH;
         holder.hTagName = 0;
         return;
      }
   };

   private static final CELL HD_ENDTAG_NAME = new CELL() {
      @Override
      public void call(xmlBluePrintHolder holder) {
         holder.tagNameEnd = holder.pointer++;

         if (0 == holder.hTagName) holder.hTagName = hash(holder.jobStart[holder.cp],holder.tagName,holder.tagNameEnd);

         if ('>' != holder.jobStart[holder.cp][holder.pointer-1]) {
            holder.xmlState = S_GT_ONLY;
            return;
         }

         // unregist branch
         if  (holder.skipDept > 0) {
            if (holder.hTagName == holder.skipName) holder.skipDept--;
            if (holder.skipDept == 0) {
               if (holder.currentNode.isChildOfTarget) {
                  holder.xmlState =  S_TARGET_INNER;
                  holder.value = holder.pointer;
               }  else {
                  holder.xmlState = S_INNER;
               }            
            } else {
               holder.xmlState = S_UNREGIST_BRANCH;
            }
            return;
         } else {
            if (holder.hTagName != holder.currentNode.tagHash) {
               // ERROR EndTag not match to BeginTag
               if (! errorHandler.call(errorToken, holder, EV_END_NE_BEGIN, 0, 0, 0, 0)){
                  HD_NEXT_XML(holder);
                  return;
               }
            }
            holder.currentNode.HD_CLOSINGTAG.call(holder);

         }
         return;
      }
   };

   /*---- TOC Filling ---- */

   public xmlBluePrint() {

      // set all to HD_SAMESTATE
      for (int i = TOC.length - 1; i >= 0; i--) {
         for (int j = 255; j >= 0; j--) {
            TOC[i][j] = HD_SAMESTATE;
         }
      }

      // UTF-8 2-byte sequence leading bytes: 0xC0-0xDF (110xxxxx)
      // Skip leading + continuation byte together
      for (int i = TOC.length - 1; i >= 0; i--) {
         for (int j = 0xC0; j <= 0xDF; j++) {
            TOC[i][j] = SKIP2_HANDLER;
         }
      }

      // UTF-8 3-byte sequence leading bytes: 0xE0-0xEF (1110xxxx)
      // Skip leading + 2 continuation bytes
      for (int i = TOC.length - 1; i >= 0; i--) {
         for (int j = 0xE0; j <= 0xEF; j++) {
            TOC[i][j] = SKIP3_HANDLER;
         }
      }

      // UTF-8 4-byte sequence leading bytes: 0xF0-0xF7 (11110xxx)
      // Skip leading + 3 continuation bytes
      for (int i = TOC.length - 1; i >= 0; i--) {
         for (int j = 0xF0; j <= 0xF7; j++) {
            TOC[i][j] = SKIP4_HANDLER;
         }
      }

      /* 
         S_HEADER : expected (< : HD_HEADER_LT)
         default : HD_SAMESTATE
       */
      TOC[S_HEADER]['<'] = HD_HEAD_LT;

      /* 
         S_HEAD_LT : < : expected (! : HD_HEADER_BANG) 
            (? : HD_HEAD_PI) 
            (_a-zA-Z : HD_HEAD_CHAR)
         anything else : HD_HEADER
      */
      for (int j = 255; j >= 0; j--) TOC[S_HEAD_LT][j] = HD_HEADER;
      TOC[S_HEAD_LT]['!'] = HD_HEADER_BANG;      
      TOC[S_HEAD_LT]['?'] = HD_HEADER_PI;    
      TOC[S_HEAD_LT]['_'] = HD_HEAD_CHAR;     
      for (int j = 'z'; j >= 'a'; j--) TOC[S_HEAD_LT][j] = HD_HEAD_CHAR;
      for (int j = 'Z'; j >= 'Z'; j--) TOC[S_HEAD_LT][j] = HD_HEAD_CHAR;

      /*
         S_HEADER_BANG : <! : expected (- : HD_HEADER_DASH)
         anything else : HD_HEADER
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_BANG][j] = HD_HEADER;
      TOC[S_HEADER_BANG]['-'] = HD_HEADER_DASH;

      /*
         S_HEADER_DASH : <!- : expected (- : HD_HEADER_2DASH)
         anything else : HD_HEADER
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_DASH][j] = HD_HEADER;
      TOC[S_HEADER_DASH]['-'] = HD_HEADER_2DASH;

      /*
         S_HEADER_2DASH : <!-- : expected (- : HD_HEADER_MINUS_DASH)
         anything else : HD_SAMESTATE
       */
      TOC[S_HEADER_2DASH]['-'] = HD_HEADER_MINUS_DASH;

      /*
         S_HEADER_MINUS_DASH : _- : expected (- : HD_HEADER_MINUS_2DASH)
         anything else : HD_HEADER_2DASH
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_MINUS_DASH][j] = HD_HEADER_2DASH;
      TOC[S_HEADER_MINUS_DASH]['-'] = HD_HEADER_MINUS_2DASH;

      /*
         S_HEADER_MINUS_2DASH : _-- : expected (> : HD_HEADER)
         anything else : HD_HEADER_2DASH
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_MINUS_2DASH][j] = HD_HEADER_2DASH;
      TOC[S_HEADER_MINUS_2DASH]['>'] = HD_HEADER;

      /*
         S_HEADER_PI : <? : expected (x : HD_HEADER_X)
         anything else : HD_HEADER
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_PI][j] = HD_HEADER;
      TOC[S_HEADER_PI]['x'] = HD_HEADER_X;
      TOC[S_HEADER_PI]['X'] = HD_HEADER_X;

      /*
         S_HEADER_X : <?x : expected (m : HD_HEADER_XM)
         anything else : HD_HEADER
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_X][j] = HD_HEADER;
      TOC[S_HEADER_X]['m'] = HD_HEADER_XM;
      TOC[S_HEADER_X]['M'] = HD_HEADER_XM;

      /*
         S_HEADER_XM : <?xm : expected (l : HD_HEADER_XML)
         anything else : HD_HEADER
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_XM][j] = HD_HEADER;
      TOC[S_HEADER_XM]['l'] = HD_HEADER_XML;
      TOC[S_HEADER_XM]['L'] = HD_HEADER_XML;

      /*
         S_HEADER_XML : <?xml : expected (space : HD_HEADER_ATTR)
         anything else : HD_HEADER
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_XML][j] = HD_HEADER;
      TOC[S_HEADER_XML][' '] = HD_HEADER_ATTR;
      TOC[S_HEADER_XML]['\t'] = HD_HEADER_ATTR;
      TOC[S_HEADER_XML]['\r'] = HD_HEADER_ATTR;
      TOC[S_HEADER_XML]['\n'] = HD_HEADER_ATTR;

      /*
         S_HEADER_ATTR : <?xml[space] : expected (nN : HD_HEADER_N)
            (cC : HD_HEADER_C)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_ATTR][j] = HD_HEADER_XML;
      TOC[S_HEADER_ATTR]['>'] = HD_HEADER;
      TOC[S_HEADER_ATTR]['e'] = HD_HEADER_E;
      TOC[S_HEADER_ATTR]['E'] = HD_HEADER_E;
      TOC[S_HEADER_ATTR]['c'] = HD_CHR_C;
      TOC[S_HEADER_ATTR]['C'] = HD_CHR_C;

      /* encoding
         S_HEADER_E : E : expected (nN : HD_EN)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_HEADER_E][j] = HD_HEADER_XML;
      TOC[S_HEADER_E]['>'] = HD_HEADER;
      TOC[S_HEADER_E]['n'] = HD_EN;
      TOC[S_HEADER_E]['N'] = HD_EN;

      /* encoding
         S_EN : EN : expected (cC : HD_ENC)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_EN][j] = HD_HEADER_XML;
      TOC[S_EN]['>'] = HD_HEADER;
      TOC[S_EN]['c'] = HD_ENC;
      TOC[S_EN]['C'] = HD_ENC;

      /* encoding
         S_ENC : ENC : expected (oO : HD_ENCO)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENC][j] = HD_HEADER_XML;
      TOC[S_ENC]['>'] = HD_HEADER;
      TOC[S_ENC]['o'] = HD_ENCO;
      TOC[S_ENC]['O'] = HD_ENCO;

      /* encoding
         S_ENCO : ENCO : expected (dD : HD_ENCOD)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENCO][j] = HD_HEADER_XML;
      TOC[S_ENCO]['>'] = HD_HEADER;
      TOC[S_ENCO]['d'] = HD_ENCOD;
      TOC[S_ENCO]['D'] = HD_ENCOD;

      /* encoding
         S_ENCOD : ENCOD : expected (iI : HD_ENCODI)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENCOD][j] = HD_HEADER_XML;
      TOC[S_ENCOD]['>'] = HD_HEADER;
      TOC[S_ENCOD]['i'] = HD_ENCODI;
      TOC[S_ENCOD]['I'] = HD_ENCODI;

      /* encoding
         S_ENCODI : ENCODI : expected (nN : HD_ENCODIN)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENCODI][j] = HD_HEADER_XML;
      TOC[S_ENCODI]['>'] = HD_HEADER;
      TOC[S_ENCODI]['n'] = HD_ENCODIN;
      TOC[S_ENCODI]['N'] = HD_ENCODIN;

      /* encoding
         S_ENCODIN : ENCODIN : expected (gG : HD_ENCODING)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENCODIN][j] = HD_HEADER_XML;
      TOC[S_ENCODIN]['>'] = HD_HEADER;
      TOC[S_ENCODIN]['g'] = HD_ENCODING;
      TOC[S_ENCODIN]['G'] = HD_ENCODING;

      /* 
         S_ENCODING : ENCODING : expected (= : HD_ENCODING)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENCODING][j] = HD_HEADER_XML;
      TOC[S_ENCODING]['>'] = HD_HEADER;
      TOC[S_ENCODING]['='] = HD_ENC_VALUE;

      /* 
         S_ENC_VALUE : reading charset : expected ( ' : HD_ENC_QUOTE)
            ( " : HD_ENC_DQUOTE)
            ( _a-zA-Z : HD_ENC_CHARSET)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_ENC_VALUE][j] = HD_HEADER_XML;
      TOC[S_ENC_VALUE]['>'] = HD_HEADER;
      TOC[S_ENC_VALUE]['\''] = HD_ENC_QUOTE;
      TOC[S_ENC_VALUE]['"'] = HD_ENC_DQUOTE;
      TOC[S_ENC_VALUE]['_'] = HD_ENC_CHARSET;      
      for (int j = 'Z'; j >= 'A'; j--) TOC[S_ENC_VALUE][j] = HD_ENC_CHARSET;
      for (int j = 'z'; j >= 'a'; j--) TOC[S_ENC_VALUE][j] = HD_ENC_CHARSET;

      /* 
         S_ENC_QUOTE : reading 'charset : expected ( ' : HD_ENC_Q_VALUE)
            ( > : HD_HEADER)
         anything else : HD_SAMESTATE
       */
      TOC[S_ENC_QUOTE]['>'] = HD_HEADER;      
      TOC[S_ENC_QUOTE]['\''] = HD_ENC_Q_VALUE;   

      /* 
         S_ENC_DQUOTE : reading "charset : expected ( " : HD_ENC_Q_VALUE)
            ( > : HD_HEADER)
         anything else : HD_SAMESTATE
       */
      TOC[S_ENC_DQUOTE]['>'] = HD_HEADER;      
      TOC[S_ENC_DQUOTE]['"'] = HD_ENC_Q_VALUE;   

      /* 
         S_ENC_CHARSET : reading charset : expected ( space : HD_ENC_Q_VALUE)
            ( ' : HD_ENC_Q_VALUE)
            ( " : HD_ENC_Q_VALUE)
            ( > : HD_HEADER)
         anything else : HD_SAMESTATE
       */
      TOC[S_ENC_CHARSET]['>'] = HD_HEADER;      
      TOC[S_ENC_CHARSET][' '] = HD_ENC_Q_VALUE;   
      TOC[S_ENC_CHARSET]['\t'] = HD_ENC_Q_VALUE; 
      TOC[S_ENC_CHARSET]['\n'] = HD_ENC_Q_VALUE;       
      TOC[S_ENC_CHARSET]['\r'] = HD_ENC_Q_VALUE; 
      TOC[S_ENC_CHARSET]['\''] = HD_ENC_Q_VALUE; 
      TOC[S_ENC_CHARSET]['"'] = HD_ENC_Q_VALUE; 



      /* 
         S_HEADER_ATTR : <?xml[space] : expected (nN : HD_HEADER_N)
            (cC : HD_HEADER_C)   // <-- เริ่มต้นเส้นทาง charset ที่นี่
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      // ... (โค้ดเดิมสำหรับ S_HEADER_ATTR ที่มีอยู่แล้ว) ...

      /* charset
         S_CHR_C : C : expected (hH : HD_CHR_CH)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_CHR_C][j] = HD_HEADER_XML;
      TOC[S_CHR_C]['>'] = HD_HEADER;
      TOC[S_CHR_C]['h'] = HD_CHR_CH;
      TOC[S_CHR_C]['H'] = HD_CHR_CH;

      /* charset
         S_CHR_CH : CH : expected (aA : HD_CHR_CHA)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_CHR_CH][j] = HD_HEADER_XML;
      TOC[S_CHR_CH]['>'] = HD_HEADER;
      TOC[S_CHR_CH]['a'] = HD_CHR_CHA;
      TOC[S_CHR_CH]['A'] = HD_CHR_CHA;

      /* charset
         S_CHR_CHA : CHA : expected (rR : HD_CHR_CHAR)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_CHR_CHA][j] = HD_HEADER_XML;
      TOC[S_CHR_CHA]['>'] = HD_HEADER;
      TOC[S_CHR_CHA]['r'] = HD_CHR_CHAR;
      TOC[S_CHR_CHA]['R'] = HD_CHR_CHAR;

      /* charset
         S_CHR_CHAR : CHAR : expected (sS : HD_CHR_CHARSE)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_CHR_CHAR][j] = HD_HEADER_XML;
      TOC[S_CHR_CHAR]['>'] = HD_HEADER;
      TOC[S_CHR_CHAR]['s'] = HD_CHR_CHARSE;
      TOC[S_CHR_CHAR]['S'] = HD_CHR_CHARSE;

      /* charset
         S_CHR_CHARSE : CHARSE : expected (tT : HD_CHR_CHARSET)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_CHR_CHARSE][j] = HD_HEADER_XML;
      TOC[S_CHR_CHARSE]['>'] = HD_HEADER;
      TOC[S_CHR_CHARSE]['t'] = HD_CHR_CHARSET;
      TOC[S_CHR_CHARSE]['T'] = HD_CHR_CHARSET;

      /* charset
         S_CHR_CHARSET : CHARSET : expected (= : HD_ENC_VALUE)
            ( > : HD_HEADER)
         anything else : HD_HEADER_XML
       */
      for (int j = 255; j >= 0; j--) TOC[S_CHR_CHARSET][j] = HD_HEADER_XML;
      TOC[S_CHR_CHARSET]['>'] = HD_HEADER;
      TOC[S_CHR_CHARSET]['='] = HD_ENC_VALUE;  // ชี้ไปยังการประมวลผลค่าเดียวกับ encoding

      /* 
         S_HEAD_CHAR : <[_a-zA-Z] : expected ( > : HD_CHK_ROOT)
         anything else : HD_SAMESTATE
       */
      TOC[S_HEAD_CHAR]['>'] = HD_CHK_ROOT; // check if this tag is root

      /* 
         S_ROOT_OPEN : <ROOT : expected ( space : HD_ROOT_OPEN)
            ( > : HD_ROOT_OPEN)
         anything else : HD_SAMESTATE
       */
      TOC[S_ROOT_OPEN][' '] = HD_ROOT_OPEN;
      TOC[S_ROOT_OPEN]['>'] = HD_ROOT_OPEN;

      /* 
         S_TARGET_ATTR : <tag : expected ( space : HD_NOVAL_ATTR)
            ( > : HD_NOVAL_ATTR)
            ( = : HD_VAL_ATTR)
         anything else : HD_SAMESTATE
       */
      TOC[S_TARGET_ATTR][' '] = HD_NOVAL_ATTR;
      TOC[S_TARGET_ATTR]['\t'] = HD_NOVAL_ATTR;
      TOC[S_TARGET_ATTR]['\r'] = HD_NOVAL_ATTR;
      TOC[S_TARGET_ATTR]['\n'] = HD_NOVAL_ATTR;
      TOC[S_TARGET_ATTR]['>'] = HD_NOVAL_ATTR;      
      TOC[S_TARGET_ATTR]['='] = HD_VAL_ATTR;

      /* 
         S_VAL_ATTR : attr= : expected ( ' : HD_VAL_QUOTE)
            ( " : HD_VAL_DQUOTE)
            ( space : HD_VAL_VALUE)
            ( > : HD_VAL_VALUE)
         anything else : HD_VAL_VALUE
       */
      for (int j = 255; j >= 0; j--) TOC[S_VAL_ATTR][j] = HD_VAL_VALUE;
      TOC[S_VAL_ATTR]['\''] = HD_VAL_QUOTE;
      TOC[S_VAL_ATTR]['"'] = HD_VAL_DQUOTE;
      TOC[S_VAL_ATTR]['>'] = HD_END_VALUE; // send but empty
      TOC[S_VAL_ATTR][' '] = HD_END_VALUE; // send but empty
      TOC[S_VAL_ATTR]['\t'] = HD_END_VALUE; // send but empty
      TOC[S_VAL_ATTR]['\n'] = HD_END_VALUE; // send but empty
      TOC[S_VAL_ATTR]['\r'] = HD_END_VALUE; // send but empty

      /* 
         S_VAL_VALUE : attr=value : expected ( " : HD_END_VALUE)
            ( space : HD_END_VALUE)
            ( > : HD_END_VALUE)
         anything else : HD_SAMESTATE
       */
      TOC[S_VAL_ATTR]['"'] = HD_END_VALUE; // forget " at openning
      TOC[S_VAL_ATTR][' '] = HD_END_VALUE; 
      TOC[S_VAL_ATTR]['\t'] = HD_END_VALUE; 
      TOC[S_VAL_ATTR]['\n'] = HD_END_VALUE; 
      TOC[S_VAL_ATTR]['\r'] = HD_END_VALUE; 
      TOC[S_VAL_ATTR]['>'] = HD_END_VALUE; 

      /* 
         S_QUOTE_VALUE : attr='value : expected ( ' : HD_END_VALUE)
            ( > : HD_END_VALUE)
         anything else : HD_SAMESTATE
       */
      TOC[S_QUOTE_VALUE]['\''] = HD_END_VALUE; 
      TOC[S_QUOTE_VALUE]['>'] = HD_END_VALUE; 

      /* 
         S_DQUOTE_VALUE : attr='value : expected ( " : HD_END_VALUE)
            ( > : HD_END_VALUE)
         anything else : HD_SAMESTATE
       */
      TOC[S_QUOTE_VALUE]['"'] = HD_END_VALUE; 
      TOC[S_QUOTE_VALUE]['>'] = HD_END_VALUE; 

       /* 
         S_TARGET_INNER : <target> : expected ( < : HD_LT )
         anything else : HD_SAMESTATE
       */        
      TOC[S_TARGET_INNER]['<'] = HD_TARGET_LT; 

      /* 
         S_LT : < : expected (! : HD_BANG) 
            (? : HD_PI) 
            (/ : HD_SLASH) 
            (_a-zA-Z : HD_CHAR)
         anything else : HD_INNER
      */
      for (int j = 255; j >= 0; j--) TOC[S_LT][j] = HD_INNER;
      TOC[S_LT]['!'] = HD_BANG;      
      TOC[S_LT]['?'] = HD_PI;    
      TOC[S_LT]['/'] = HD_SLASH;          
      TOC[S_LT]['_'] = HD_CHAR;     
      for (int j = 'z'; j >= 'a'; j--) TOC[S_LT][j] = HD_CHAR;
      for (int j = 'Z'; j >= 'Z'; j--) TOC[S_LT][j] = HD_CHAR;

      /*
         S_BANG : <! : expected (- : HD_DASH)
         anything else : HD_TAG_IGNORE
       */
      for (int j = 255; j >= 0; j--) TOC[S_BANG][j] = HD_PI;
      TOC[S_BANG]['-'] = HD_DASH;

      /*
         S_DASH : <!- : expected (- : HD_2DASH)
         anything else : HD_TAG_IGNORE
       */
      for (int j = 255; j >= 0; j--) TOC[S_DASH][j] = HD_PI;
      TOC[S_DASH]['-'] = HD_2DASH;

      /*
         S_2DASH : <!-- : expected (- : HD_MINUS_DASH)
         anything else : HD_SAMESTATE
       */
      TOC[S_2DASH]['-'] = HD_MINUS_DASH;

      /*
         S_MINUS_DASH : _- : expected (- : HD_MINUS_2DASH)
         anything else : HD_2DASH
       */
      for (int j = 255; j >= 0; j--) TOC[S_MINUS_DASH][j] = HD_2DASH;
      TOC[S_MINUS_DASH]['-'] = HD_MINUS_2DASH;

      /*
         S_MINUS_2DASH : _-- : expected (> : HD_INNER)
         anything else : HD_2DASH
       */
      for (int j = 255; j >= 0; j--) TOC[S_MINUS_2DASH][j] = HD_2DASH;
      TOC[S_MINUS_2DASH]['>'] = HD_INNER;

      /*
         S_PI : <? : expected (> : HD_INNER)
         anything else : HD_SAMESTATE
       */
      for (int j = 255; j >= 0; j--) TOC[S_PI][j] = HD_SAMESTATE;
      TOC[S_PI]['>'] = HD_INNER;

      /*
         S_TAG_OPEN : <tagName : expected (> : HD_OPENTAG)
            (space : HD_OPENTAG)
         anything else : HD_SAMESTATE
       */
      TOC[S_TAG_OPEN]['>'] = HD_OPENTAG;      
      TOC[S_TAG_OPEN][' '] = HD_OPENTAG;    
      TOC[S_TAG_OPEN]['\t'] = HD_OPENTAG;    
      TOC[S_TAG_OPEN]['\r'] = HD_OPENTAG;    
      TOC[S_TAG_OPEN]['\n'] = HD_OPENTAG;    

      /*
         S_ATTR : <tag(space) : expected (> : HD_TAG_CLOSE)
         anything else : HD_SAMESTATE
       */
      TOC[S_ATTR]['>'] = HD_TAG_CLOSE;

      /*
         S_INNER : <tag> : expected (< : HD_TAG_CLOSE)
         anything else : HD_SAMESTATE
       */
      TOC[S_INNER]['<'] = HD_LT;      

      /*
         S_SLASH : </ : expected (> : HD_ENDTAG_NAME)
            (space : HD_ENDTAG_NAME)
         anything else : HD_SAMESTATE
       */
      TOC[S_SLASH]['>'] = HD_ENDTAG_NAME;      
      TOC[S_SLASH][' '] = HD_ENDTAG_NAME;  
      TOC[S_SLASH]['\t'] = HD_ENDTAG_NAME;  
      TOC[S_SLASH]['\r'] = HD_ENDTAG_NAME;  
      TOC[S_SLASH]['\n'] = HD_ENDTAG_NAME;  

      /*
         S_GT_ONLY : </tag(space) : expected (> : HD_ENDTAG_NAME)
         anything else : HD_SAMESTATE
       */
      TOC[S_GT_ONLY]['>'] = HD_ENDTAG_NAME;     

      /*
         S_UNREGIST_BRANCH : unregisted branch : expected (< : HD_LT)
         anything else : HD_SAMESTATE
       */
      TOC[S_UNREGIST_BRANCH]['<'] = HD_LT;     

   }
};



