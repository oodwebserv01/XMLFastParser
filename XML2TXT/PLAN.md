# XML2TXT Implementation Plan (Aligned with GOAL.md)

> **Mapping to GOAL.md Requirements:**
> - **Source discovery**: Recursive folder scan + ZIP extraction (Sec. "ZIP Handling")
> - **Output files**: `{fileType}_{thread:02d}_{timestamp}.txt` + `read_`, `success_`, `fail_` logs (Sec. "Output Files")
> - **Backup rename**: `FileName.xml`→`xmlFileName.bak`, `XMLZipFile.zip`→`zipXMLZipFile.bak` (Sec. "Batch Flush Helpers" → `renameProcessedSource`)
> - **Parameters**: `-p`, `-s`, `-d`, `-t` (implemented in `XML2TXT.java:ParseArguments`)
> - **Blueprint (.bp)**: `file:`, `entity:`, `@attr`, `#text`, `/.` shorthand (Sec. "Blueprint (.bp) Structure")

## Architecture: Multi-Entity Output + Batch Write + Pending Pattern

### Core Flow
```
1. Parse Arguments & Blueprint
2. List XML/ZIP files from source (recursive + zip contents)
3. FOR each file:
     a. Read file bytes (extract from ZIP if needed)
     b. Submit to xmlBluePrint job queue
4. Register ALL entity handlers + column handlers with xmlBluePrint
   - Each file: entity → separate output file + writer + batch buffer
   - Plus: read_, success_, fail_ log writers
5. Run xmlBluePrint (multi-threaded)
6. Wait for completion
7. Flush all remaining batch buffers
8. Close all writers
9. Rename pending → final (with timestamp)
10. Backup processed source files (xmlXXX.bak / zipXXX.bak)
11. Shutdown xmlBluePrint
```

---

## Blueprint (.bp) Structure

### Parsed BPConfig
```java
class BPConfig {
    List<String> fileTypes = new ArrayList<>();      // "Invoids", "Items"
    List<String> entityPaths = new ArrayList<>();    // "/Transaction", "/Transaction/Invoid/Items/Item", or "" (empty = root)
    List<Boolean> useRootAsEntity = new ArrayList<>(); // true if no entity: specified
    List<ColumnSpec> columns = new ArrayList<>();    // All column specs with fileType index
    
    // ColumnSpec: path, type("@"|"#"), attrName, columnIndex, fileTypeIndex
    // fileTypeIndex links column to which file: block it belongs to
}
```

### Entity Resolution: No entity: Specified = Root Element
If a `file:` block has **no `entity:` line**, it means:
- **1 row per XML file** (the entire document is one record)
- **Root element is the entity** → register handler at root level
- Columns are relative to root (or absolute paths from root)

```java
// In ReadBPFile - parsing logic:
String currentFileType = null;
String currentEntityPath = null;
boolean hasEntity = false;

while (scanner.hasNextLine()) {
    String line = scanner.nextLine().trim();
    
    if (line.startsWith("file:")) {
        // Save previous fileType's entity info
        if (currentFileType != null) {
            bpConfig.fileTypes.add(currentFileType);
            bpConfig.entityPaths.add(currentEntityPath != null ? currentEntityPath : "");
            bpConfig.useRootAsEntity.add(!hasEntity);
        }
        // Start new fileType
        currentFileType = line.substring(5).trim();
        currentEntityPath = null;
        hasEntity = false;
        
    } else if (line.startsWith("entity:")) {
        currentEntityPath = line.substring(7).trim();
        hasEntity = true;
        
    } else if (line.startsWith("-")) {
        // Parse column spec with current fileTypeIndex
    }
}
// Don't forget last fileType
if (currentFileType != null) {
    bpConfig.fileTypes.add(currentFileType);
    bpConfig.entityPaths.add(currentEntityPath != null ? currentEntityPath : "");
    bpConfig.useRootAsEntity.add(!hasEntity);
}
```

### Callback Registration for Root Entity
```java
// For each fileType:
String entityPath = bpConfig.entityPaths.get(i);
boolean useRoot = bpConfig.useRootAsEntity.get(i);

if (useRoot) {
    // Register at root level - use empty path or special root handler
    // xmlBluePrint may have rootRegist() or use "/" as root path
    String rootPath = "/"; // or "" depending on xmlBluePrint API
    
    xmlBluePrint.regist(rootPath, new xmlBluePrintCall() {
        @Override
        public boolean call(Object idToken, xmlBluePrintHolder holder, 
                            int event, int nameBegin, int nameEnd, 
                            int valueBegin, int valueEnd) {
            ThreadContext ctx = (ThreadContext) idToken;
            String fileType = bpConfig.fileTypes.get(i);
            
            if (event == 1) { // EV_OPEN_TAG at root
                // Start row - column 1 = source file
                ctx.currentRow.setLength(0);
                ctx.currentRow.append(ctx.currentSourceFile).append('\t');
                ctx.currentFileType = fileType;
                return true;
            } else if (event == 2) { // EV_CLOSE_TAG at root
                // End of document - write row
                String row = ctx.currentRow.toString();
                if (row.endsWith("\t")) row = row.substring(0, row.length() - 1);
                
                StringBuilder batchBuf = ctx.dataBatchBuffers.get(fileType);
                AtomicInteger counter = ctx.dataRowCounters.get(fileType);
                batchBuf.append(row).append('\n');
                if (counter.incrementAndGet() >= 1000) {
                    flushDataBuffer(ctx, fileType);
                }
                return true;
            }
            return true;
        }
    }, threadContext);
    
} else {
    // Normal entity handler at specific path
    xmlBluePrint.regist(entityPath, entityHandler, threadContext);
}
```

### Column Paths for Root Entity
When `useRootAsEntity = true`, column paths in `.bp` are interpreted as:
- **Absolute from root**: `/Transaction/Invoid@SN` (full path)
- **Relative to root**: `Transaction/Invoid@SN` (no leading `/`)
- **Short-hand `/.`**: Still resolves from previous full path

```java
// In parseColumnSpec - when fileType uses root entity:
String columnPath = col.path;
if (bpConfig.useRootAsEntity.get(fileTypeIndex)) {
    // Ensure path starts with '/' for absolute from root
    if (!columnPath.startsWith("/") && !columnPath.startsWith("./")) {
        columnPath = "/" + columnPath;
    }
}
```

### Path Resolution (Short-hand `/.`)
```java
// Example 2:
// file:Invoids
// entity:/Transaction
// - /Transaction/Invoid@SN
// - /././Saler/PersonID#   --> resolves to /Transaction/Invoid/Saler/PersonID#

// Algorithm: Split previous full path by '/', replace each `/.` with parent segment
// Track: lastFullPath = "/Transaction/Invoid"
// For "/././Saler/PersonID#":
//   parts = ["", ".", ".", "Saler", "PersonID#"]
//   result = lastFullPath + "/Saler/PersonID#"
```

---

## Output Files (Per Thread)

### Data Files (per fileType)
| Stage | Pattern | Example |
|-------|---------|---------|
| Pending | `{fileType}_{thread:02d}_pending` | `Invoids_00_pending` |
| Final | `{fileType}_{thread:02d}_{yyyyMMddHHmmss}.txt` | `Invoids_00_20260817143022.txt` |

### Log Files (shared across fileTypes)
| Type | Pending | Final |
|------|---------|-------|
| read | `read_pending` | `read_YYYYMMDDHHmmss.txt` |
| success | `success_pending` | `success_YYYYMMDDHHmmss.txt` |
| fail | `fail_pending` | `fail_YYYYMMDDHHmmss.txt` |

### Column 1 (Mandatory)
Every data row starts with: `relativePath/xmlFileName.xml`

---

## ThreadContext (Per Parser Thread)

```java
class ThreadContext {
    final int threadNo;
    
    // Data file writers (one per fileType)
    final Map<String, BufferedWriter> dataWriters = new HashMap<>();
    final Map<String, StringBuilder> dataBatchBuffers = new HashMap<>();
    final Map<String, AtomicInteger> dataRowCounters = new HashMap<>();
    
    // Log writers (shared)
    final BufferedWriter readWriter;
    final StringBuilder readBatchBuffer;
    final AtomicInteger readRowCounter;
    
    final BufferedWriter successWriter;
    final StringBuilder successBatchBuffer;
    final AtomicInteger successRowCounter;
    
    final BufferedWriter failWriter;
    final StringBuilder failBatchBuffer;
    final AtomicInteger failRowCounter;
    
    // Column buffers per fileType: fileType -> String[columnCount]
    // Column 0 = source file (mandatory), columns 1..N = bp columns
    final Map<String, String[]> columnBuffers = new HashMap<>();
    final Map<String, Integer> columnCounts = new HashMap<>(); // fileType -> total columns (including col 0)
    
    // Track which fileType's entity is currently open (for nested entities)
    String currentFileType = null;
    String currentSourceFile = null;
    
    static final int BATCH_SIZE = 1000;
    
    // File ID mapping: hash(fileName) -> fileInfo (for fast lookup in callbacks)
    // Used as idToken when registering entity handlers
    final Map<Integer, FileInfo> fileIdMap = new HashMap<>();
    
    static class FileInfo {
        final int fileId;           // hash(fileName)
        final String fileName;      // Original file name (for column 0)
        final String fileType;      // "Invoids", "Items", etc.
        final String entityPath;    // Entity path for this fileType
        
        FileInfo(int fileId, String fileName, String fileType, String entityPath) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileType = fileType;
            this.entityPath = entityPath;
        }
    }
    
    // Register a file and get its hash-based ID
    int registerFile(String fileName, String fileType, String entityPath) {
        int fileId = fileName.hashCode(); // or custom hash for better distribution
        fileIdMap.put(fileId, new FileInfo(fileId, fileName, fileType, entityPath));
        return fileId;
    }
    
    // Get FileInfo from hash ID (O(1) lookup)
    FileInfo getFileInfo(int fileId) {
        return fileIdMap.get(fileId);
    }
    
    // Initialize column buffers for a fileType
    void initColumnBuffer(String fileType, int numColumns) {
        columnCounts.put(fileType, numColumns + 1); // +1 for column 0 (source file)
        columnBuffers.put(fileType, new String[numColumns + 1]);
    }
    
    // Clear buffer for specific fileType (on EV_OPEN_TAG of entity)
    void clearColumnBuffer(String fileType) {
        String[] buf = columnBuffers.get(fileType);
        if (buf != null) {
            Arrays.fill(buf, null);
            buf[0] = currentSourceFile; // Column 0 = source file
        }
    }
    
    // Set column value by index
    void setColumnValue(String fileType, int columnIndex, String value) {
        String[] buf = columnBuffers.get(fileType);
        if (buf != null && columnIndex >= 0 && columnIndex < buf.length) {
            buf[columnIndex] = value;
        }
    }
    
    // Build row string from buffer (on EV_CLOSE_TAG of entity)
    String buildRow(String fileType) {
        String[] buf = columnBuffers.get(fileType);
        if (buf == null) return "";
        
        StringBuilder sb = new StringBuilder(512);
        for (int i = 0; i < buf.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(buf[i] != null ? buf[i] : "");
        }
        return sb.toString();
    }
}
```

---

## Callback Registration Strategy

### 1. Entity Handlers - Using hash(fileName) as idToken

```java
// Pre-calculate column count per fileType
Map<String, Integer> columnsPerFileType = new HashMap<>();
for (ColumnSpec col : bpConfig.columns) {
    String ft = bpConfig.fileTypes.get(col.fileTypeIndex);
    columnsPerFileType.merge(ft, 1, Integer::max);
}

// For each fileType in BPConfig:
for (int i = 0; i < bpConfig.fileTypes.size(); i++) {
    String fileType = bpConfig.fileTypes.get(i);
    String entityPath = bpConfig.entityPaths.get(i);
    boolean useRoot = bpConfig.useRootAsEntity.get(i);
    int numCols = columnsPerFileType.getOrDefault(fileType, 0);
    
    // Initialize column buffer in each ThreadContext
    for (ThreadContext ctx : contexts.values()) {
        ctx.initColumnBuffer(fileType, numCols);
    }
    
    String registPath = useRoot ? "/" : entityPath;
    
    // Register entity handler with fileId as idToken
    // xmlBluePrint will call this callback with idToken = fileId (hash of fileName)
    xmlBluePrint.regist(registPath, new xmlBluePrintCall() {
        @Override
        public boolean call(Object idToken, xmlBluePrintHolder holder, 
                            int event, int nameBegin, int nameEnd, 
                            int valueBegin, int valueEnd) {
            // idToken is Integer fileId (hash of fileName)
            int fileId = (Integer) idToken;
            ThreadContext ctx = getThreadContext(); // Get current thread's context
            
            // O(1) lookup: fileId -> FileInfo
            ThreadContext.FileInfo fInfo = ctx.getFileInfo(fileId);
            if (fInfo == null) return true; // Unknown file
            
            String fileType = fInfo.fileType;
            
            if (event == 1) { // EV_OPEN_TAG - entity start
                // Clear ONLY this fileType's column buffer
                ctx.clearColumnBuffer(fileType);
                ctx.currentFileType = fileType;
                ctx.currentSourceFile = fInfo.fileName; // Column 0 = source file
                // clearColumnBuffer already sets buf[0] = currentSourceFile
                return true;
                
            } else if (event == 2) { // EV_CLOSE_TAG - entity end
                // Build row from column buffer
                String row = ctx.buildRow(fileType);
                if (!row.isEmpty()) {
                    StringBuilder batchBuf = ctx.dataBatchBuffers.get(fileType);
                    AtomicInteger counter = ctx.dataRowCounters.get(fileType);
                    batchBuf.append(row).append('\n');
                    if (counter.incrementAndGet() >= 1000) {
                        flushDataBuffer(ctx, fileType);
                    }
                }
                ctx.currentFileType = null;
                return true;
            }
            return true;
        }
    }, null); // idToken will be set per-file at pushJob time (see below)
}
```

### PushJob: Pass fileId as idToken
```java
// When submitting each file to xmlBluePrint:
for (FileEntry entry : fileEntries) {
    // Register file in ALL thread contexts (each thread gets its own copy)
    // Actually: register once, but pushJob needs to know which fileId to pass
    
    // Better: Register file in a shared map, pushJob passes fileId
    int fileId = entry.fileName.hashCode(); // Consistent hash
    
    // Register fileInfo in each thread context
    for (ThreadContext ctx : contexts.values()) {
        ctx.registerFile(entry.fileName, fileType, entityPath); // Need to know fileType for this file
    }
    
    // pushJob with fileId as idToken
    xmlBluePrint.pushJob(entry.bytes, entry.bytes.length,
                        entry.fileName.getBytes(), entry.fileName.length(),
                        fileId); // idToken = hash(fileName)
}
```

### ColumnSpec with Fixed ColumnIndex
```java
class ColumnSpec {
    String path;           // Original: "/Transaction/Invoid@SN"
    String elementPath;    // Extracted: "/Transaction/Invoid"
    String type;           // "@" | "#" | "entity"
    String attrName;       // "SN" (if type="@")
    int columnIndex;       // Fixed index within fileType (0=source, 1=first bp col)
    int fileTypeIndex;     // Which file: block this belongs to
}
```

### Column Index Assignment (during registration setup)
```java
// Count columns per fileType (excluding source file column 0)
Map<String, Integer> colCountPerFileType = new HashMap<>();
for (ColumnSpec col : bpConfig.columns) {
    String ft = bpConfig.fileTypes.get(col.fileTypeIndex);
    int idx = colCountPerFileType.getOrDefault(ft, 0);
    col.columnIndex = idx + 1; // +1 because column 0 = source file
    colCountPerFileType.put(ft, idx + 1);
}
```

### 2. Column Handlers - GROUPED BY ELEMENT PATH + Column Array Buffer

**xmlFastParser registers callbacks by element path ONLY (tagName), not by attribute/innerText.**
Multiple `.bp` columns can target the SAME element with different attributes/innerText.

**We must group columns by element path and register ONE callback per unique element.**
Each ColumnSpec has a fixed `columnIndex` within its fileType.

```java
// Step 1: Group columns by element path (strip @attrName or #)
Map<String, List<ColumnSpec>> columnsByElement = new HashMap<>();
for (ColumnSpec col : bpConfig.columns) {
    String elementPath = extractElementPath(col.path);
    columnsByElement.computeIfAbsent(elementPath, k -> new ArrayList<>()).add(col);
}

// Step 2: Register ONE callback per unique element path
for (Map.Entry<String, List<ColumnSpec>> entry : columnsByElement.entrySet()) {
    String elementPath = entry.getKey();
    List<ColumnSpec> cols = entry.getValue();
    
    // Build attribute name -> List<ColumnSpec> map for this element
    Map<String, List<ColumnSpec>> attrMap = new HashMap<>();
    List<ColumnSpec> innerTextCols = new ArrayList<>();
    
    for (ColumnSpec col : cols) {
        if ("@".equals(col.type)) {
            attrMap.computeIfAbsent(col.attrName, k -> new ArrayList<>()).add(col);
        } else if ("#".equals(col.type)) {
            innerTextCols.add(col);
        }
    }
    
    // Register single callback for this element
    xmlBluePrint.regist(elementPath, new xmlBluePrintCall() {
        @Override
        public boolean call(Object idToken, xmlBluePrintHolder holder, 
                            int event, int nameBegin, int nameEnd, 
                            int valueBegin, int valueEnd) {
            ThreadContext ctx = (ThreadContext) idToken;
            
            if (event == 3) { // EV_ATTR - attribute event
                // Extract attribute name from buffer
                String attrName = extractAttrName(holder, nameBegin, nameEnd);
                
                // Find matching column specs for this attribute
                List<ColumnSpec> matchingCols = attrMap.get(attrName);
                if (matchingCols != null) {
                    String value = extractValue(holder, valueBegin, valueEnd);
                    for (ColumnSpec col : matchingCols) {
                        String fileType = bpConfig.fileTypes.get(col.fileTypeIndex);
                        // Only set value if this fileType's entity is currently open
                        if (fileType.equals(ctx.currentFileType)) {
                            // Set value at fixed column index (thread-safe per fileType)
                            ctx.setColumnValue(fileType, col.columnIndex, value);
                        }
                    }
                }
                return true;
                
            } else if (event == 4) { // EV_INNER_TEXT - inner text event
                String value = extractValue(holder, valueBegin, valueEnd);
                for (ColumnSpec col : innerTextCols) {
                    String fileType = bpConfig.fileTypes.get(col.fileTypeIndex);
                    if (fileType.equals(ctx.currentFileType)) {
                        ctx.setColumnValue(fileType, col.columnIndex, value);
                    }
                }
                return true;
            }
            return true;
        }
    }, threadContext); // Pass ThreadContext as userData
}
```

### Helper: Extract Element Path from Column Spec
```java
String extractElementPath(String columnPath) {
    int atIdx = columnPath.lastIndexOf('@');
    int hashIdx = columnPath.lastIndexOf('#');
    
    if (atIdx > 0) {
        return columnPath.substring(0, atIdx);
    } else if (hashIdx > 0) {
        return columnPath.substring(0, hashIdx);
    }
    return columnPath;
}
```

### ColumnSpec with Fixed ColumnIndex
```java
class ColumnSpec {
    String path;           // Original: "/Transaction/Invoid@SN"
    String elementPath;    // Extracted: "/Transaction/Invoid"  
    String type;           // "@" | "#" | "entity"
    String attrName;       // "SN" (if type="@")
    int columnIndex;       // Fixed index within fileType (1=first bp col, 0=source file)
    int fileTypeIndex;     // Which file: block this belongs to
    
    boolean isAttribute() { return "@".equals(type); }
    boolean isInnerText() { return "#".equals(type); }
}
```

### Column Index Assignment (during registration setup)
```java
// Count columns per fileType (excluding source file column 0)
Map<String, Integer> colCountPerFileType = new HashMap<>();
for (ColumnSpec col : bpConfig.columns) {
    String ft = bpConfig.fileTypes.get(col.fileTypeIndex);
    int idx = colCountPerFileType.getOrDefault(ft, 0);
    col.columnIndex = idx + 1; // +1 because column 0 = source file
    colCountPerFileType.put(ft, idx + 1);
}
```

### How FileId Flows Through Callbacks

| Callback | idToken (userData) | Purpose |
|----------|-------------------|---------|
| **Entity Handler** | `Integer fileId` (hash of fileName) | Identify which file's entity is opening/closing |
| **Column Handlers** | `ThreadContext` (shared) | Access column buffers via `ctx.currentFileType` |

**Flow:**
```
pushJob(fileBytes, ..., fileId) 
    → xmlBluePrint parses XML
    → EV_OPEN_TAG(entity) fires with idToken=fileId
    → Entity Handler: fileId → FileInfo → sets ctx.currentFileType, ctx.currentSourceFile
    → EV_ATTR/EV_INNER_TEXT fire with idToken=ThreadContext
    → Column Handlers: check ctx.currentFileType → write to correct buffer
    → EV_CLOSE_TAG(entity) fires with idToken=fileId
    → Entity Handler: buildRow(ctx.currentFileType) → flush to batch buffer
```

### Why This Design Works
| Feature | Benefit |
|---------|---------|
| **Fixed columnIndex** | Columns can arrive in ANY order (attr before innerText, or vice versa) |
| **Per-fileType String[]** | Multiple entities (fileTypes) can be nested; each has independent buffer |
| **Clear on EV_OPEN_TAG** | Only clears the fileType that started, doesn't affect parent/child entities |
| **Build on EV_CLOSE_TAG** | Converts array → tab-delimited string in correct column order |
| **Grouped by element** | Single callback per element, HashMap lookup for attrName → O(1) |

### Example: Invoids fileType (6 columns including source)
```
columnIndex:  0         1           2              3              4          5
             source    @SN       Saler/PersonID#  Buyer/PersonID#  @Count    Summary#
             
EV_OPEN_TAG(/Transaction)      → clear buffer[Invoids], buffer[0]=sourceFile
EV_ATTR(@SN)                   → buffer[1] = "SN123"
EV_INNER_TEXT(Saler/PersonID)  → buffer[2] = "PID001"
EV_INNER_TEXT(Buyer/PersonID)  → buffer[3] = "PID002"
EV_ATTR(@Count)                → buffer[4] = "5"
EV_INNER_TEXT(Summary)         → buffer[5] = "Total: 1000"
EV_CLOSE_TAG(/Transaction)     → buildRow() → "source.xml\tSN123\tPID001\tPID002\t5\tTotal: 1000"
```

### Performance Comparison
| Approach | Registrations | Element Visits | Column Order Handling |
|----------|---------------|----------------|----------------------|
| Per-column append | N | N × events | Fragile (depends on callback order) |
| **Grouped + Column Array** | M (unique elements) | **M × events** | **Robust (fixed index)** |

### 3. Log Handlers (read, success, fail)
```java
// Register file-level handlers for logging
// EV_OPEN_TAG at root level → log to read_pending
// On successful file completion → log to success_pending
// On error → log to fail_pending

// These can use xmlBluePrint.rootRegist() or regist at document level
```

---

## ProcessFiles Implementation

```java
void ProcessFiles(String sourcePath, String destPath, int threadCount, BPConfig bpConfig) {
    // 1. List all files (including inside ZIPs)
    List<FileEntry> fileEntries = listAllFilesWithZip(sourcePath);
    
    // 2. Create timestamp for this run
    String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    String fileType0 = bpConfig.fileTypes.get(0); // e.g., "Invoids"
    
    // 3. Create ThreadContext per parser thread
    Map<Integer, ThreadContext> contexts = new ConcurrentHashMap<>();
    for (int t = 0; t < threadCount; t++) {
        ThreadContext ctx = new ThreadContext(t);
        
        // Data file writers (per fileType)
        for (String ft : bpConfig.fileTypes) {
            String pending = ft + "_" + String.format("%02d", t) + "_pending";
            File f = new File(destPath, pending);
            BufferedWriter w = new BufferedWriter(new FileWriter(f), 65536);
            ctx.dataWriters.put(ft, w);
            ctx.dataBatchBuffers.put(ft, new StringBuilder(1024 * 1024));
            ctx.dataRowCounters.put(ft, new AtomicInteger(0));
        }
        
        // Log writers (shared)
        ctx.readWriter = createWriter(destPath, "read_pending");
        ctx.readBatchBuffer = new StringBuilder(65536);
        ctx.readRowCounter = new AtomicInteger(0);
        
        ctx.successWriter = createWriter(destPath, "success_pending");
        ctx.successBatchBuffer = new StringBuilder(65536);
        ctx.successRowCounter = new AtomicInteger(0);
        
        ctx.failWriter = createWriter(destPath, "fail_pending");
        ctx.failBatchBuffer = new StringBuilder(65536);
        ctx.failRowCounter = new AtomicInteger(0);
        
        contexts.put(t, ctx);
    }
    
    // 4. Register all callbacks (entity + column + log)
    //    Pass all ThreadContexts - xmlBluePrint will route to correct one
    RegisterPathsWithXmlBluePrint(bpConfig, threadCount, contexts);
    
    // 5. Submit all files to job queue
    for (FileEntry entry : fileEntries) {
        // Set source file in context for column 1
        // xmlBluePrint will call callbacks with correct ThreadContext
        xmlBluePrint.pushJob(entry.bytes, entry.bytes.length,
                            entry.fileName.getBytes(), entry.fileName.length());
    }
    
    // 6. Run
    xmlBluePrint.run();
    
    // 7. Wait completion
    while (!xmlBluePrint.isReadyToDown()) {
        Thread.sleep(100);
    }
    
    // 8. Flush all remaining buffers
    for (ThreadContext ctx : contexts.values()) {
        for (String ft : bpConfig.fileTypes) {
            flushDataBuffer(ctx, ft);
        }
        flushLogBuffer(ctx.readWriter, ctx.readBatchBuffer);
        flushLogBuffer(ctx.successWriter, ctx.successBatchBuffer);
        flushLogBuffer(ctx.failWriter, ctx.failBatchBuffer);
    }
    
    // 9. Close all writers
    for (ThreadContext ctx : contexts.values()) {
        for (BufferedWriter w : ctx.dataWriters.values()) w.close();
        ctx.readWriter.close();
        ctx.successWriter.close();
        ctx.failWriter.close();
    }
    
    // 10. Rename pending → final
    for (ThreadContext ctx : contexts.values()) {
        for (String ft : bpConfig.fileTypes) {
            renamePending(destPath, ft + "_" + String.format("%02d", ctx.threadNo) + "_pending",
                         ft + "_" + String.format("%02d", ctx.threadNo) + "_" + timestamp + ".txt");
        }
        renamePending(destPath, "read_pending", "read_" + timestamp + ".txt");
        renamePending(destPath, "success_pending", "success_" + timestamp + ".txt");
        renamePending(destPath, "fail_pending", "fail_" + timestamp + ".txt");
    }
    
    // 11. Backup source files
    for (FileEntry entry : fileEntries) {
        renameProcessedSource(entry.originalPath);
    }
    
    // 12. Shutdown
    xmlBluePrint.shutdown();
}
```

---

## ZIP Handling

```java
class FileEntry {
    byte[] bytes;
    String fileName;      // Original name (for column 1)
    String originalPath;  // For backup rename
}

List<FileEntry> listAllFilesWithZip(String sourcePath) {
    List<FileEntry> result = new ArrayList<>();
    Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {
        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".xml")) {
                result.add(new FileEntry(readBytes(file), file.getFileName().toString(), file.toString()));
            } else if (name.endsWith(".zip")) {
                // Extract XML entries from ZIP
                try (ZipFile zf = new ZipFile(file.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry ze = entries.nextElement();
                        if (ze.getName().toLowerCase().endsWith(".xml")) {
                            byte[] data = readZipEntry(zf, ze);
                            result.add(new FileEntry(data, ze.getName(), file.toString()));
                        }
                    }
                }
            }
            return FileVisitResult.CONTINUE;
        }
    });
    return result;
}
```

---

## Batch Flush Helpers

```java
void flushDataBuffer(ThreadContext ctx, String fileType) {
    StringBuilder buf = ctx.dataBatchBuffers.get(fileType);
    if (buf.length() > 0) {
        try {
            ctx.dataWriters.get(fileType).write(buf.toString());
            ctx.dataWriters.get(fileType).flush();
            buf.setLength(0);
            ctx.dataRowCounters.get(fileType).set(0);
        } catch (IOException e) {
            System.err.println("Flush error " + fileType + " thread " + ctx.threadNo + ": " + e);
        }
    }
}

void flushLogBuffer(BufferedWriter writer, StringBuilder buf) {
    if (buf.length() > 0) {
        try {
            writer.write(buf.toString());
            writer.flush();
            buf.setLength(0);
        } catch (IOException e) {
            System.err.println("Log flush error: " + e);
        }
    }
}

void renamePending(String destPath, String pendingName, String finalName) {
    File p = new File(destPath, pendingName);
    File f = new File(destPath, finalName);
    if (p.exists()) p.renameTo(f);
}

void renameProcessedSource(String originalPath) {
    File f = new File(originalPath);
    String name = f.getName();
    String newName;
    if (name.toLowerCase().endsWith(".xml")) {
        newName = "xml" + name.substring(0, name.length() - 4) + ".bak";
    } else if (name.toLowerCase().endsWith(".zip")) {
        newName = "zip" + name.substring(0, name.length() - 4) + ".bak";
    } else return;
    
    File newFile = new File(f.getParent(), newName);
    if (!newFile.exists()) f.renameTo(newFile);
}
```

---

## TODO Checklist
- [ ] Update `BPConfig` to track fileType per column (fileTypeIndex)
- [ ] Implement `/.` path resolution in `ReadBPFile`/`parseColumnSpec`
- [ ] Update `RegisterPathsWithXmlBluePrint` to register per-fileType entity handlers
- [ ] Implement `ThreadContext` class with multi-fileType buffers
- [ ] Implement `ProcessFiles` with ZIP extraction + multi-output
- [ ] Add log handlers (read, success, fail)
- [ ] Test with sample.bp (both Example 1 and 2)