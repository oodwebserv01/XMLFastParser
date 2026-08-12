# XMLFastParser Architecture Plan

## Design Principle: Separation of Concerns

```
┌─────────────────────────────────────────────────────────────────┐
│                        xmlBluePrint                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    XML LOGIC (ALL)                       │   │
│  │  • State Machine (FSM)                                   │   │
│  │  • TOC Table (Transition Table)                          │   │
│  │  • Byte Parsing & Tokenization                           │   │
│  │  • Tag/Attribute/InnerText Recognition                   │   │
│  │  • Tree Path Matching (hash-based)                       │   │
│  │  • Handler Dispatch (xmlBluePrintCall)                   │   │
│  │  • UTF-8 Decoding / Charset Handling                     │   │
│  │  • Root/Target/Child/Ignore Tag Routing                  │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ calls handler methods
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      xmlBluePrintHolder                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              NON-XML LOGIC ONLY (Minimal)                │   │
│  │  • Job Queue Management (circular buffer)               │   │
│  │  • Thread Pool Coordination                             │   │
│  │  • Memory/Buffer Management                             │   │
│  │  • Current Job Pointer/State                            │   │
│  │  • Charset Storage (parsed from XML decl)               │   │
│  │  • Target/Child Hash Sets (for fast lookup)             │   │
│  │  • Current Target/Child Context (for routing)           │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Important Clarifications

### 1. Path Registration Starts from First Child of Root (Not Root Itself)

The registered paths **do not include the root element**. Registration begins at the first level **under** the root tag.

```java
// XML: <root><a><b><c>value</c></b></a></root>

// Register path to <c> - starts from 'a' (first child of root), NOT 'root'
regist("/a/b/c", handler, token);  // Correct: path from first child
// regist("/root/a/b/c", ...)       // Wrong: root is not part of registered path
```

The root tag is handled separately via `rootRegist(rootHandler, rootToken)`.

### 2. Unregistered Branch Handling (No Depth Limit, Tag-Name Based Unwind)

Unregistered branches are **not limited to 32 levels**. Instead:

- On first unregistered tag encountered: record its **tag name hash** + set `skipDepth = 1`
- On subsequent `<tag>` with **same hash**: increment `skipDepth`
- On `</tag>` with **same hash**: decrement `skipDepth`
- When `skipDepth` returns to 0: exit skip mode, resume registered branch parsing

**Key properties:**
- No hard depth limit (unbounded nesting)
- Does **not** validate XML syntax in unregistered branches
- Only tracks the **first unregistered tag name** (by hash)
- Guarantees correct unwind when same tag name appears 2nd/3rd time
- Malformed XML in unregistered branches is ignored (no error callbacks)

---

## xmlBluePrint Responsibilities (ALL XML Logic)

### 1. State Machine Constants
```java
// States (0-23)
OUTSIDE_ROOT, ROOT_LT, ROOT_PI, ROOT_XML_DECL, ROOT_BANG, 
ROOT_COMMENT, ROOT_BANG_OTHER, ROOT_TAG_START, ROOT_TAG,
TARGET_OPEN, TARGET_INNER, TARGET_CLOSE,
INSIDE_ROOT,
PI_SKIP, BANG_SKIP, COMMENT_SKIP, CDATA_SKIP,
CLOSE_TAG,
CHILD_OPEN, CHILD_INNER, CHILD_CLOSE,
IGNORE_TAG_OPEN, IGNORE_TAG_INNER, IGNORE_TAG_CLOSE
```

### 2. TOC Table (Transition Table)
```java
xmlBluePrintCall[][] TOC = new xmlBluePrintCall[24][256];
// TOC[state][byte] → handler that processes byte, updates holder, returns nextState
```

### 3. Handler Implementations (per state/byte)
Each handler does:
- Parse byte(s) from `holder.jobStart[holder.cp][holder.pointer]`
- Update `holder.pointer`, `holder.xmlState`, context fields
- Call user handler (`xmlBluePrintCall`) for events
- Return next state (or encode in holder.xmlState)

### 4. Path Registration & Tree
```java
Node root;  // Tree root
void rootRegist(handler, token);
void regist(path, handler, token);  // "/a/b/c" → tree nodes with hash
```

### 5. Hash-Based Matching
```java
long hash(byte[] name);  // 64-bit hash
Set<Long> targetHashes;  // All registered target tag hashes
Map<Long, Node> childMap; // currentTarget.children
```

### 6. Charset/Encoding
```java
// Parse at ROOT_XML_DECL state
Charset detectCharset(byte[] xmlDecl);  // Returns StandardCharsets.UTF_8 etc.
```

---

## xmlBluePrintHolder Responsibilities (NON-XML Only)

### 1. Job Queue (Circular Buffer)
```java
static final int QUEUE_SIZE = 0x2000; // 2^13 = 8192
byte[][] jobData = new byte[QUEUE_SIZE][];
int[] jobLength = new int[QUEUE_SIZE];
byte[][] jobName = new byte[QUEUE_SIZE][];
int[] jobNameLength = new int[QUEUE_SIZE];

int cp = 0;  // consumer pointer (current job)
int pp = 0;  // producer pointer (next job)

boolean pushJob(byte[] data, int len, byte[] name, int nameLen);
boolean nextJob();
int queSpace();
```

### 2. Thread Pool Coordination
```java
ExecutorService executor;
int threadCount;
void setThreadCount(int n);
void run();  // Submit jobs to executor
```

### 3. Current Job Context
```java
byte[] currentData() { return jobData[cp]; }
int currentLength() { return jobLength[cp]; }
byte[] currentName() { return jobName[cp]; }
int currentNameLength() { return jobNameLength[cp]; }
int pointer;  // Current byte offset in currentData
```

### 4. XML State (Minimal - just current state)
```java
int xmlState = OUTSIDE_ROOT;  // Current FSM state
```

### 5. Routing Context (Populated by xmlBluePrint, read for routing)
```java
// Set by xmlBluePrint when entering INSIDE_ROOT
long currentTargetHash;           // Hash of current target tag
Set<Long> targetHashes;           // All target hashes (for fast check)
Map<Long, Node> currentChildren;  // Children of current target
Node currentTargetNode;           // Current target node (for handler/token)
Node currentChildNode;            // Current child node (for handler/token)

// For ignore tag tracking
long ignoreTagHash;               // Hash of tag being ignored
int ignoreDepth;                  // Nesting depth of ignore tag
```

### 6. Charset Storage
```java
Charset charset = StandardCharsets.UTF_8;  // Parsed from XML decl
```

### 7. User Data Pass-through
```java
Object rootIdToken;
xmlBluePrintCall rootHandler;
xmlBluePrintCall errorHandler;
```

---

## Interaction Flow

```
Thread Pool Worker:
┌────────────────────────────────────────────────────────────┐
│ while (holder.pointer < holder.currentLength()) {          │
│     int state = holder.xmlState;                           │
│     byte b = holder.currentData()[holder.pointer];         │
│     xmlBluePrintCall handler = xmlBluePrint.TOC[state][b]; │
│     handler.call(idToken, holder, event, name, nLen,       │
│                  value, vLen);  // Updates holder, state   │
│ }                                                          │
└────────────────────────────────────────────────────────────┘
```

### Handler Contract
```java
// xmlBluePrintCall implementations in xmlBluePrint
// Each handler:
// 1. Parses bytes from holder.currentData() at holder.pointer
// 2. Advances holder.pointer
// 3. Updates holder.xmlState (next state)
// 4. Updates holder routing context (currentTargetHash, etc.)
// 5. Calls user callback (onTargetOpen, onAttribute, etc.)
// 6. Returns boolean (true = continue, false = stop/error)
```

---

## File Structure

```
xmlFastParser/
├── xmlBluePrint.java          # ALL XML logic (FSM, TOC, parsing, handlers)
├── xmlBluePrintHolder.java    # Queue, thread pool, context (NO XML parsing)
├── xmlBluePrintCall.java      # Callback interface
├── xmlBluePrintNode.java      # Tree node (hash, handler, token, children)
└── ARCHITECTURE_PLAN.md       # This file
```

---

## Migration Checklist

### xmlBluePrint.java - ADD
- [ ] TOC table initialization with all state/byte handlers
- [ ] State handler methods (private, one per state/byte group)
- [ ] Path registration (rootRegist, regist)
- [ ] Tree building (Node class, hash map children)
- [ ] Hash computation
- [ ] Charset detection from XML declaration
- [ ] Main processing loop (called by holder.run())

### xmlBluePrintHolder.java - KEEP/REFACTOR
- [x] Job queue (circular buffer)
- [x] Thread pool management
- [x] Current job data accessors
- [x] xmlState field
- [ ] ADD: Routing context fields (targetHashes, currentTargetHash, currentChildren, etc.)
- [ ] ADD: Charset field
- [ ] ADD: Root/error handler/token fields
- [ ] REMOVE: Any XML parsing logic (if any exists)

### xmlBluePrintCall.java - EXTEND
- [ ] Add new event methods (onTargetOpen, onTargetInnerText, onTargetClose, onChildOpen, onChildInnerText, onChildClose, onAttribute, onRootBegin, onError)

---

## Benefits

| Aspect | Benefit |
|--------|---------|
| **Testability** | xmlBluePrint pure logic → unit testable without threads/queues |
| **Maintainability** | XML parsing in one place, queue logic in another |
| **Performance** | TOC table = O(1) byte dispatch, hash = O(1) path matching |
| **Extensibility** | New states/handlers only touch xmlBluePrint |
| **Thread Safety** | Holder = per-thread data, Blueprint = shared readonly (after init) |