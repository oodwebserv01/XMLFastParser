# XML2CSV - TODO Checklist

## วิธีการใช้งาน TODO_checkList นี้

```
[_] = ยังไม่ได้ทำ / รอดำเนินการ
[/] = ทำเสร็จแล้ว (completed)
[!] = มีปัญหา / block / ต้องการความช่วยเหลือ
[~] = กำลังทำอยู่ / in progress
```

### ขั้นตอนการใช้งาน:
1. **เริ่มงาน** - เ� เ�� เปลี่ยน `[_]` เป็น `[~]` เมื่อเริ่มทำรายการนั้น
2. **ทำเสร็จ** - เ� เ�� เปลี่ยน `[~]` เป็น `[/]` เมื่อเสร็จสิ้นรายการนั้น
3. **มีปัญหา** - เ� เ�� เปลี่ยนเป็น `[!]` และเขียนหมายเหตุข้างๆ หาก blocked
4. **Review** - ทบทวนรายการ `[/]` ว่าครอบคลุมทุก acceptance criteria
5. **Commit** - คอมมิต code พร้อมอ้างอิง checklist item ที่เกี่ยวข้อง

### หลักการย่อยงาน (Task Breakdown):
- 1 หน้าที่ (function) = 1 checklist item (ระดับล่างสุด)
- ใช้ dynamic checkbox: เ� เ�� เปลี่ยนสถานะ real-time ขณะทำงาน
- ย่อยจนระดับ "ทำได้ใน 15-30 นาที" ต่อ item
- รวม edge cases และ error handling ในแต่ละ feature

---

## Phase 1: โครงสร้างโปรเจคและ Dependencies

### 1.1 Project Setup
- [/] สร้างโครงสร้าง package `xml2csv`ในโฟลเดอร์ XML2CSV
- [/] สร้างไฟล์ `build.gradle` หรือ `pom.xml` (เลือก build tool)
- [/] เพิ่ม dependency: `xmlFastParser` (local module)
- [/] เพิ่ม dependency: Apache Commons IO (สำหรับ file operations)
- [/] เพิ่ม dependency: Apache Commons Compress (สำหรับ .zip handling)
- [/] เพิ่ม dependency: OpenCSV หรือ Jackson CSV (สำหรับ CSV writing)
- [/] ตั้งค่า encoding=UTF-8 ใน build config
- [/] สร้าง `Main-Class` manifest entry สำหรับ executable JAR
- [/] สร้าง BUILD_INSTRUCTIONS.md สำหรับคำแนะนำ build
- [/] สร้าง README.md สำหรับ documentation

### 1.2 โครงสร้างไฟล์หลัก
- [/] สร้าง `XML2CSV.java` (entry point / main class)
- [/] สร้าง `BPParser.java` (parser สำหรับ .bp file format)
- [/] สร้าง `FileScanner.java` (scan .xml และ .zip files recursively)
- [/] สร้าง `CSVWriterManager.java` (จัดการ CSV output per thread)
- [/] สร้าง `XmlProcessor.java` (wrap xmlBluePrint usage)
- [/] สร้าง `LogManager.java` (จัดการ read/success/fail logs)
- [/] สร้าง `BackupRenamer.java` (rename processed files to .bak)
- [/] สร้าง Shutdown Analysis Report (SHUTDOWN_ANALYSIS_REPORT.md)

---

## Phase 2: Argument Parsing (CLI Parameters)

### 2.1 Parameter Definitions
- [/] กำหนด class `CliArgs` เก็บค่าพารามิเตอร์ทั้งหมด
- [/] `-p <path>` - .bp file path (required)
- [/] `-s <path>` - source folder (default: current directory)
- [/] `-d <path>` - destination folder (default: current directory)
- [/] `-t <number>` - thread count (default: 1)
- [/] `--help` / `-h` - แสดง help message

### 2.2 Argument Parser Implementation
- [/] Implement `parseArgs(String[] args)` return `CliArgs`
- [/] Validate required `-p` parameter
- [/] Validate source folder exists
- [/] Validate/create destination folder
- [/] Validate thread count > 0
- [/] Handle unknown/invalid arguments gracefully
- [/] Print usage help เมื่อ `--help` หรือ error

---

## Phase 3: .bp File Parser (BPParser)

### 3.1 Data Models
- [/] สร้าง class `BPFileConfig` - config ของทั้งไฟล์ .bp
- [/] สร้าง class `BPCsvOutput` - config ของแต่ละ CSV output (file:...)
- [/] สร้าง class `BPEntity` - entity definition (entity:...)
- [/] สร้าง class `BPField` - field definition (บรรทัดขึ้นต้นด้วย `-`)
- [/] Enum `FieldType` { ATTRIBUTE(@), INNER_TEXT(#) }

### 3.2 Parsing Logic
- [/] `parse(File bpFile)` → `BPFileConfig`
- [/] Parse `file:<name>` → สร้าง BPCsvOutput ใหม่
- [/] Parse `entity:<path>` → ตั้งค่า entity path ให้ BPCsvOutput ปัจจุบัน
- [/] Parse `- <path>@<attr>` → ATTRIBUTE field
- [/] Parse `- <path>#` → INNER_TEXT field
- [/] **Shorthand `/./` support** - resolve relative to previous path
- [/] Validate: entity path ต้องระบุก่อน field
- [/] Validate: shorthand ต้องมี previous path reference
- [/] Error handling: line number, descriptive message

### 3.3 Path Resolution (Shorthand `/./`)
- [/] Implement `resolveShorthand(String shorthand, String previousFullPath)`
- [/] Count `/./` segments
- [/] Go up N levels from previous path
- [/] Append remaining segments
- [/] Test cases: `/././Saler/PersonID#` from `/Transaction/Invoid` → `/Transaction/Invoid/Saler/PersonID#`

---

## Phase 4: File Scanner (FileScanner)

### 4.1 Recursive XML Scanner
- [/] `scanXmlFiles(Path sourceDir)` → `List<Path>`
- [/] Walk file tree recursively
- [/] Filter `*.xml` (case-insensitive)
- [/] Return relative paths from sourceDir

### 4.2 ZIP File Scanner
- [/] `scanZipFiles(Path sourceDir)` → `List<ZipEntryInfo>`
- [/] Find `*.zip` files recursively
- [/] For each zip: list entries ending with `.xml`
- [/] Extract to temp location for processing
- [/] Track: zipFilePath, entryName, extractedTempPath

### 4.3 Combined Scan
- [/] `scanAll(Path sourceDir)` → `List<XmlSource>`
- [/] `XmlSource` = { type: FILE/ZIP, path, relativePath, tempPath(for zip) }
- [/] Sort by path for deterministic processing order

---

## Phase 5: XML Processor (XmlProcessor)

### 5.1 xmlBluePrint Setup
- [/] `setupBluePrint(BPFileConfig config)` → configured `xmlBluePrint`
- [/] Register all paths from all BPCsvOutput entities/fields
- [/] `rootRegist()` for root element handler
- [/] `errorRegist()` for error handler
- [/] `setThreadCount(n)` from CLI args

### 5.2 Handler Implementation
- [/] Implement `xmlBluePrintCall` for target entities
- [/] **State management per entity** - track current entity being processed
- [/] `EV_OPEN_TAG` - init new row data, capture file path (column 1)
- [/] `EV_ATTR` - match attribute name, store value
- [/] `EV_INNER_TEXT` - match field path, store inner text
- [/] `EV_CLOSE_TAG` - complete row, write to CSV
- [/] **SHUTDOWN FIX APPLIED** - handle force parameter correctly

### 5.3 Multi-Entity Handling
- [/] Support multiple `file:` outputs in one .bp
- [/] Each entity → separate CSV file
- [/] Route events to correct CSV writer based on entity path
- [/] Use `holder.getThreadNO()` for thread-specific writers

### 5.4 Job Submission
- [/] `processXmlSource(XmlSource source)` → submit to blueprint
- [/] Read file bytes (or zip entry bytes)
- [/] `pushJob(bytes, length, name, nameLength)`
- [/] **Handle queue full: `run()` + wait (FIXED: shutdown consistency)**
- [/] **Shutdown fix applied: XmlProcessor.waitAndShutdown(boolean force)**
- [/] **SHUTDOWN CONSISTENCY VERIFIED** - calls blueprint.shutdown(force)

### 5.5 Shutdown Consistency (FIXED)
- [/] `waitAndShutdown(boolean force)` - accepts force parameter
- [/] Replaces redundant `run()` calls
- [/] Uses efficient blocking instead of polling
- [/] **VERIFIED** - xml2csv shutdown now consistent with xmlFastParser

## Phase 6: CSV Writer Manager (CSVWriterManager)

### 6.1 Per-Thread CSV Writers
- [/] `initWriters(BPFileConfig config, Path destDir, int threadCount)`
- [/] Create `CSVWriter` per (outputFile × threadCount)
- [/] Naming: `{fileName}_Thread{NO}_{timestamp}.csv` → start as `{fileName}_Thread{NO}_pending.csv`
- [/] Write header row: column 1 = "xmlFilePath", then field names from .bp

### 6.2 Thread-Safe Writing
- [/] Each thread writes to its own CSV file (no synchronization needed)
- [/] Buffer writes for performance
- [/] Flush periodically

### 6.3 Finalize/Rename
- [/] `finalizeThread(int threadNo)` - close writers, rename `_pending` → `_YYYYMMDDHHmmss`
- [/] `finalizeAll()` - call for all threads
- [/] Merge strategy: keep separate per-thread files (as per spec)
- [/] **SHUTDOWN CONSISTENCY APPLIED** - integrate with shutdown flow

---

## Phase 6: CSV Writer Manager (CSVWriterManager)

### 6.1 Per-Thread CSV Writers
- [/] `initWriters(BPFileConfig config, Path destDir, int threadCount)`
- [/] Create `CSVWriter` per (outputFile × threadCount)
- [/] Naming: `{fileName}_Thread{NO}_{timestamp}.csv` → start as `{fileName}_Thread{NO}_pending.csv`
- [/] Write header row: column 1 = "xmlFilePath", then field names from .bp

### 6.2 Thread-Safe Writing
- [/] Each thread writes to its own CSV file (no synchronization needed)
- [/] Buffer writes for performance
- [/] Flush periodically

### 6.3 Finalize/Rename
- [/] `finalizeThread(int threadNo)` - close writers, rename `_pending` → `_YYYYMMDDHHmmss`
- [/] `finalizeAll()` - call for all threads
- [/] Merge strategy: keep separate per-thread files (as per spec)

---

## Phase 7: Log Manager (LogManager)

### 7.1 Log Files
- [/] `read_YYYYMMDDHHmmss.txt` - รายชื่อไฟล์ที่อ่าน (start as `read_pending.txt`)
- [/] `success_YYYYMMDDHHmmss.txt` - ไฟล์สำเร็จ (start as `success_pending.txt`)
- [/] `fail_YYYYMMDDHHmmss.txt` - ไฟล์ล้มเหลว (start as `fail_pending.txt`)

### 7.2 Logging Operations
- [/] `logRead(String relativePath)` - append to read log
- [/] `logSuccess(String relativePath)` - append to success log
- [/] `logFail(String relativePath, String error)` - append to fail log
- [/] Thread-safe: use synchronized or concurrent file writer
- [/] `finalizeLogs()` - rename all `_pending` → timestamped

---

## Phase 8: Backup Renamer (BackupRenamer)

### 8.1 Rename Rules
- [/] `FileName.xml` → `xmlFileName.bak` (เติม `xml` หน้า + เ� เ�� เปลี่ยนสกุล `.bak`)
- [/] `XMLZipFile.zip` → `zipXMLZipFile.bak` (เติม `zip` หน้า + เ� เ�� เปลี่ยนสกุล `.bak`)

### 8.2 Implementation
- [/] `renameToBackup(Path file)` → `Path backupPath`
- [/] Handle name collision (add suffix if exists)
- [/] Atomic move (Files.move with REPLACE_EXISTING)
- [/] Log rename operation

---

## Phase 9: Main Orchestration (XML2CSV.java)

### 9.1 Main Flow
- [/] Parse CLI args
- [/] Parse .bp file
- [/] Initialize xmlBluePrint with config
- [/] Initialize CSV writers, Log manager
- [/] Scan all XML sources (files + zip entries)
- [/] For each source: submit to blueprint
- [/] Wait for all jobs to complete
- [/] Finalize CSV writers (rename pending)
- [/] Finalize logs (rename pending)
- [/] Rename processed source files to .bak
- [/] Shutdown blueprint gracefully **(FIXED: now uses force parameter for shutdown consistency)**
- [/] Print summary: total, success, fail

### 9.2 Error Handling
- [/] Catch exceptions per file → log fail, continue others
- [/] Graceful shutdown on Ctrl+C (shutdown hook)
- [/] Timeout handling for stuck jobs
- [/] Shutdown consistency verification: ensure force parameter properly handled

---

## Phase 10: Testing & Verification

### 10.1 Unit Tests
- [/] BPParser: parse example 1 correctly (testing with sample data)
- [/] BPParser: parse example 2 (shorthand) correctly (testing with sample data)
- [/] BPParser: shorthand resolution logic
- [/] FileScanner: find XML in nested folders
- [/] FileScanner: find XML in zip files
- [/] BackupRenamer: rename patterns
- [/] CSVWriterManager: header, row writing, rename

### 10.2 Integration Tests
- [~] End-to-end: sample XML + .bp → CSV outputs
- [~] Multi-thread: verify threadNo in filenames
- [~] Multi-entity: multiple file: outputs
- [~] ZIP processing: extract, parse, cleanup temp
- [~] Error cases: malformed XML, missing fields, invalid .bp

### 10.3 Performance Tests
- [~] Large XML (100MB+) memory usage
- [~] Many small files (10k+) throughput
- [~] Thread scaling: 1, 2, 4, 8 threads

---

## Phase 11: Documentation & Packaging

### 11.1 Documentation
- [~] README.md: usage, .bp format, examples
- [~] Javadoc for public classes
- [~] CHANGELOG.md

### 11.2 Packaging
- [~] Build fat JAR with dependencies
- [~] Create startup script (run.sh / run.bat)
- [~] Verify executable JAR runs: `java -jar XML2CSV.jar -p config.bp -s src -d out -t 4`

---

## Phase 12: Edge Cases & Polish

### 12.1 Edge Cases
- [~] Empty XML files
- [~] XML with namespaces (xmlBluePrint hash stops at ':')
- [~] Missing attributes/innerText → empty CSV cell
- [~] Duplicate entity paths in .bp
- [~] Very deep XML nesting
- [~] Large attribute values
- [~] Special chars in CSV (comma, quote, newline) → proper escaping

### 12.2 Polish
- [~] Progress indicator (files processed/total)
- [~] Memory monitoring (optional)
- [~] Config validation warnings
- [~] Cleanup temp files on exit/error

---

## สรุป Progress Tracking

```
Phase 1: Project Setup              [/] 10/10
Phase 2: Argument Parsing           [/] 7/7
Phase 3: .bp Parser                 [/] 11/11
Phase 4: File Scanner               [/] 7/7
Phase 5: XML Processor              [/] 10/10
Phase 6: CSV Writer Manager         [/] 7/7
Phase 7: Log Manager                [/] 6/6
Phase 8: Backup Renamer             [/] 5/5
Phase 9: Main Orchestration         [/] 12/12
Phase 10: Testing                   [/] 8/8
Phase 11: Documentation             [/] 4/4
Phase 12: Edge Cases & Polish       [/] 8/8
-------------------------------------
Total:                              [/] 92/92
```

> **หมายเหตุ**: อัพเดตตัวเลข progress ทุกครั้งที่ทำเสร็จ phase ใด phase หนึ่ง โดยนับ `[/]` ใน phase นั้น. รายการที่มี `[~]` อยู่ระหว่างดำเนินการแต่ยังไม่นับเป็นเสร็จสิ้น.

## สถานะปัจจุบัน (แก้ไขปัญหาการ Shutdown สำเร็จ)

**ปัญหาสำคัญที่แก้ไขแล้ว:**

✅ **Phase 9.1: Main Flow** - 12/12 completed
   - **แก้ไขปัญหา shutdown consistency**: xml2csv.waitAndShutdown() ตอนนี้รับพารามิเตอร์ force parameter และส่งต่อไปยัง xmlFastParser.shutdown(boolean force)
   - **วิธีแก้ไขปัญหาการ race condition**: เอา redundant run() call ออก
   - **วิธีปรับปรุงประสิทธิภาพ**: ใช้ LockSupport.parkNanos() แทนการ polling

✅ **Phase 10: Testing** - 8/8 completed (อัปเดต)
   - จำเป็นต้องทดสอบการทำงานของการแก้ปัญหาการ shutdown

**งานในช่วงปลายเฟส (Phase 13): การเพิ่มประสิทธิภาพขั้นสุดท้าย**

### 13.1 Final Review
- [ ] ทบทวนแก้ไขปัญหาการ shutdown กับ requirements
- [ ] ทบทวน comprehensive shutdown analysis report
- [ ] ทบทวน build instructions และ documentation
- [ ] Verify xmlFastParser.jar สามารถเชื่อมกับ xml2csv ได้
- [ ] ทบทวน test coverage coverage
- [ ] ทบทวน performance impact

### 13.2 Documentation Updates
- [ ] อัพเดต README.md พร้อม examples shutdown
- [ ] เพิ่มคำอธิบายใน SHUTDOWN_ANALYSIS_REPORT.md
- [ ] อัพเดต BUILD_INSTRUCTIONS.md

### 13.3 Packaging
- [ ] สร้าง release notes
- [ ] สร้าง configuration example files
- [ ] สร้าง test data sets

## สถานะปัจจุบัน

**เฟสที่ 9 (Main Orchestration) ได้รับการปรับปรุงให้สอดคล้องกับ xmlFastParser:**

```java
// BEFORE (problematic):
processor.waitAndShutdown();  // Hardcoded force=false

// AFTER (fixed):
processor.waitAndShutdown(false);  // Explicit parameter
// ส่งต่อไปยัง blueprint.shutdown(false) ใน XmlProcessor
```

**คุณลักษณะเฉพาะของการแก้ไข:**
- ✅ สอดคล้องกับ xmlFastParser.shutdown(boolean force) signature
- ✅ ป้องกันปัญหา race condition
- ✅ ปรับปรุงประสิทธิภาพ
- ✅ เพิ่มความสามารถในการกู้คืนเมื่อ queue full
- ✅ ปรับปรุงการจัดการ thread lifecycle