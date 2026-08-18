# TODO Checklist - XML2TXT Implementation

> **Note**: The Java version issue has been resolved by recompiling xmlFastParser.jar with Java 21 compatibility. The code can now be compiled and tested. Implementation status updated based on actual code review.

## Phase 1: Blueprint Parsing
- [x] Refactor `ReadBPFile` to return a rich `BPConfig` object
- [x] Update `BPConfig` to include:
    - [x] `List<String> fileTypes`
    - [x] `List<String> entityPaths`
    - [ ] `List<Boolean> useRootAsEntity` (field exists but not populated from .bp file - assumed always false)
    - [x] `List<ColumnSpec> columns` (with `fileTypeIndex` and `columnIndex`)
- [x] Implement `/.` shorthand resolution in `parseColumnSpec`:
    - [x] When a column path starts with `./`, replace each `./` with the preceding path segment from the previous non-empty line in the .bp file.
    - [x] Handle edge cases: no previous line, multiple `./` sequences.
- [x] Add comprehensive error handling in `ReadBPFile`:
    - [x] Validate .bp file exists and is readable
    - [x] Handle malformed lines (missing file:, entity:, or - prefix)
    - [x] Detect and report duplicate file/entity definitions
    - [x] Validate column specifications (proper @/# usage)
    - [x] Handle file I/O exceptions gracefully
- [ ] Validate parsing with both Example 1 (full paths) and Example 2 (shorthand) from GOAL.md.
- [ ] Write unit tests for `ReadBPFile` using:
    - [ ] The provided `sample.bp`
    - [ ] Additional edge‑case .bp files (empty, comments only, malformed lines)
    - [ ] Error case testing (file not found, invalid format)
    - [ ] Performance testing with large .bp files

## Phase 2: Data Structures
- [x] Define `ColumnSpec` with fields:
    - [x] `String path` (original)
    - [x] `String elementPath` (stripped of `@`/`#`)
    - [x] `String type` (`"@"`, `"#"`, or `"entity"`)
    - [x] `String attrName` (if type=`"@"`)
    - [x] `int columnIndex` (position within the fileType's column list, starting at 1)
    - [x] `int fileTypeIndex` (which `file:` block this column belongs to)
- [x] Define `ThreadContext` per parser thread:
    - [x] Thread number (`threadNo`)
    - [x] Maps `fileType -> BufferedWriter` (data files)
    - [x] Maps `fileType -> StringBuilder` (batch buffer)
    - [x] Maps `fileType -> AtomicInteger` (row counter)
    - [x] Shared log writers/buffers/counters for `read`, `success`, `fail`
    - [x] `Map<String, String[]> columnBuffers` (fileType -> array sized `columnCount+1`, index 0 reserved for source file name)
    - [x] `Map<String, Integer> columnCounts` (fileType -> total columns including source file column)
    - [x] `Map<Integer, FileInfo> fileIdMap` (hash(`fileName`) -> FileInfo)
    - [x] Methods: `registerFile`, `getFileInfo`, `initColumnBuffer`, `clearColumnBuffer`, `setColumnValue`, `buildRow`
- [x] Ensure `ThreadContext` is thread‑safe (each thread has its own instance; maps are not shared across threads).
- [x] Add resource management to `ThreadContext`:
    - [x] Implement proper initialization and cleanup methods
    - [x] Ensure all writers are closed in a cleanup method
    - [ ] Add methods to check for resource leaks
    - [x] Handle potential null pointer exceptions in map operations
- [ ] Add validation methods to data structures:
    - [ ] Validate column index ranges
    - [ ] Validate fileType/entityPath consistency
    - [ ] Check for duplicate registrations

## Phase 3: xmlBluePrint Registration
- [x] Pre‑compute `columnIndex` for each `ColumnSpec` per fileType (excluding source file column 0).
- [x] Group `ColumnSpec` by `elementPath` (stripped of `@attrName` or `#`).
- [x] Register **one** callback per unique `elementPath` with xmlBluePrint:
    - [x] Pass the corresponding `ThreadContext` as userData (`idToken` for column callbacks).
    - [x] In the callback:
        - [x] On `EV_ATTR`: extract attribute name, find matching `ColumnSpec`s in `attrMap`, if current fileType matches, set column value.
        - [x] On `EV_INNER_TEXT`: extract inner text, do the same for `innerTextCols`.
        - [x] Add error handling for null or invalid `idToken`
        - [x] Validate column index bounds before setting values
- [x] Register entity handlers:
    - [x] Use `hash(fileName)` as `idToken` when calling `pushJob`.
    - [x] Callback receives the `idToken`, looks up `FileInfo` via `ThreadContext.getFileInfo(fileId)`.
    - [x] On `EV_OPEN_TAG`: set `ctx.currentFileType`, `ctx.currentSourceFile` (column 0), and clear column buffer for that fileType.
    - [x] On `EV_CLOSE_TAG`: build row via `ctx.buildRow(fileType)`, append to batch buffer, flush when ≥1000 rows.
    - [x] Add error handling for null `FileInfo`
    - [x] Validate state before processing close tags
- [x] Register log handlers (read, success, fail) at document level:
    - [x] `EV_OPEN_TAG` at root → append file name to appropriate pending log buffer.
    - [x] `EV_CLOSE_TAG` at root (or via a completion callback) → move file name from pending to success/fail logs.
    - [ ] Ensure log buffers are flushed with same batch size.
    - [x] Add error handling for log operations (I/O exceptions)
- [ ] Implement validation checks after registration:
    - [ ] Verify all required paths are registered
    - [ ] Check for registration conflicts
    - [ ] Validate callback handler assignments

## Phase 4: File Processing Loop
- [x] Implement `listAllFilesWithZip(String sourcePath)`:
    - [x] Walk source directory recursively.
    - [x] For each `.xml` file: read bytes, create `FileEntry` with relative path.
    - [x] For each `.zip` file: open with `java.util.zip.ZipFile`, enumerate entries, for each `.xml` entry extract bytes and create `FileEntry` with relative path of the ZIP file.
    - [x] Add error handling for corrupted ZIP files
    - [x] Handle ZIP files with no XML entries
    - [x] Validate file readability before processing
- [x] In `ProcessFiles`:
    1. [x] Parse arguments (`-p`, `-s`, `-d`, `-t`)
    2. [x] Read blueprint via `ReadBPFile`.
    3. [x] Generate timestamp (`yyyyMMddHHmmss`).
    4. [x] Create `ThreadContext` instances for each thread (`0 … threadCount‑1`).
    5. [x] Initialize writers for each `fileType` and log types (pending files).
    6. [x] Register all callbacks with xmlBluePrint (entity + column + log).
    7. [x] For each `FileEntry`:
        - [x] Register the file in every `ThreadContext` (`ctx.registerFile(fileName, "", entry.relativePath)`).
        - [x] Call `xmlBluePrint.pushJob(bytes, bytes.length, fileName.getBytes(StandardCharsets.UTF_8), fileName.length())` (without idToken).
        - [x] Add error handling for `pushJob` failures (queue full, etc.)
        - [x] Note: In entity handler, attempt to set column 0 from holder's jobName (may not be reliable).
    8. [x] Call `xmlBluePrint.run()`.
    9. [x] Wait until `xmlBluePrint.isReadyToDown()`:
        - [x] Add timeout mechanism to prevent infinite waiting
        - [x] Handle interruption gracefully
    10. [x] Flush all remaining data and log buffers:
        - [x] Ensure all data is written before closing
        - [x] Handle flush failures
    11. [x] Close all writers:
        - [x] Ensure all resources are released
        - [x] Handle close exceptions
    12. [x] Rename pending → final files (append timestamp):
        - [x] Verify pending files exist before renaming
        - [x] Handle rename failures (file already exists, permissions)
    13. [x] Backup processed source files (rename to `xml<name>.bak` or `zip<name>.bak`):
        - [x] Verify source files exist before backup
        - [x] Handle backup failures gracefully
        - [x] Only rename if target does not already exist
    14. [x] Call `xmlBluePrint.shutdown()`:
        - [x] Ensure proper cleanup of xmlBluePrint resources
    15. [x] Add comprehensive error handling throughout:
        - [x] Try-catch blocks for all I/O operations
        - [x] Proper error logging and reporting
        - [ ] Resource cleanup in finally blocks
        - [ ] Graceful degradation when possible
    16. [x] Add validation checks:
        - [x] Validate source and destination paths exist/are accessible
        - [x] Validate thread count is reasonable
        - [x] Validate blueprint is properly parsed before processing

## Phase 5: Output and Logging
- [x] Verify column 0 of every data row contains `relativePath/xmlFileName.xml` (relative to source folder).
- [x] Ensure pending files are named:
    - `{fileType}_{thread:02d}_pending`
    - `read_pending`, `success_pending`, `fail_pending`
- [x] Ensure final files are renamed to:
    - `{fileType}_{thread:02d}_{yyyyMMddHHmmss}.txt`
    - `read_{yyyyMMddHHmmss}.txt`, `success_{yyyyMMddHHmmss}.txt`, `fail_{yyyyMMddHHmmss}.txt`
- [x] Confirm batch flush occurs after every 1000 rows (configurable constant).
- [x] Verify that log files contain exactly one line per processed XML file (read log), one per success, one per failure.
- [ ] Add error handling for output operations:
    - [x] Handle disk full conditions during writing
    - [x] Handle permission errors when creating/writing files
    - [ ] Ensure partial writes don't corrupt output files
    - [x] Implement retry mechanisms for transient failures
- [ ] Add logging and monitoring capabilities:
    - [ ] Log processing progress (files processed, errors encountered)
    - [x] Log performance metrics (processing time, throughput)
    - [x] Log error details with stack traces for debugging
    - [x] Implement configurable log levels (INFO, WARN, ERROR)
- [ ] Validate output file integrity:
    - [x] Check that output files are not empty when data is expected
    - [x] Verify column delimiters are consistent
    - [ ] Ensure proper line endings (platform-independent)
    - [x] Validate that column 0 contains expected file path format

## Phase 6: Backup and Rename Logic
- [x] Implement `renamePending(destPath, pendingName, finalName)` using `File.renameTo`.
- [x] Implement `renameProcessedSource(originalPath)`:
    - [x] If file ends with `.xml` → `xml<basename>.bak`
    - [x] If file ends with `.zip` → `zip<basename>.bak`
    - [x] Only rename if the target does not already exist.
- [x] Ensure backup operation occurs after successful final rename.
- [ ] Add error handling for backup and rename operations:
    - [x] Handle permission errors during rename
    - [x] Handle cases where target file already exists (implement fallback naming)
    - [ ] Ensure atomicity where possible (write to temp then rename)
    - [ ] Log all rename operations for audit trail
    - [x] Handle filesystem errors (disk full, read-only filesystem)
- [ ] Add validation for backup operations:
    - [x] Verify source file exists before attempting backup
    - [ ] Verify backup file was created successfully
    - [ ] Ensure backup file is not corrupted
    - [ ] Handle cases where backup fails but primary operation succeeded
- [ ] Implement cleanup mechanisms for failed operations:
    - [ ] Clean up pending files if processing fails
    - [ ] Remove temporary files created during processing
    - [ ] Ensure no orphaned files are left behind

## Phase 7: Testing in Lab
- [ ] Copy `Lab/sample.bp` and any needed XML/ZIP test files into `Lab/test_data` (create if missing).
- [ ] Create a minimal XML file (`Lab/test_data/sample.xml`) that matches the blueprint paths (use real values from the sample or generate dummy data).
- [ ] Optionally create a ZIP containing the XML to test ZIP handling.
- [ ] Test Cases:
    - [ ] **Single‑thread, no ZIP** – run `java -cp xmlFastParser.jar:. XML2TXT -p Lab/sample.bp -s Lab/test_data -d Lab/output -t 1`
    - [ ] **Multi‑thread** – same command with `-t 4`
    - [ ] **ZIP only** – place `sample.xml` inside `test.zip`, run with source pointing to the ZIP directory.
    - [ ] **Mixed XML + ZIP** – both plain XML and ZIP in source.
    - [ ] **Shorthand paths** – verify that Example 2 style (`/.`) works by creating a .bp that uses `/.` and checking output.
    - [ ] **Empty results** – ensure no crash when XML lacks some optional elements.
    - [ ] **Malformed .bp** – detect missing `entity:` or bad syntax and report error.
    - [ ] **Corrupted ZIP files** – handle gracefully without crashing
    - [ ] **Empty source directory** – handle gracefully
    - [ ] **Permission denied scenarios** – test error handling for inaccessible files/directories
    - [ ] **Large file processing** – test with large XML files to verify memory usage
    - [ ] **Concurrent processing stress test** – test thread safety with high thread counts
    - [ ] **Edge case file names** – test with special characters, spaces, Unicode in filenames
    - [ ] **Interrupted processing** – test Ctrl+C handling and cleanup
- [ ] After each run, verify:
    - [ ] Output files exist with correct naming.
    - [ ] Column 0 matches the source file name (relative path).
    - [ ] Expected columns are present and correctly filled.
    - [ ] Log files contain expected entries.
    - [ ] Source files have been renamed to `.bak`.
    - [ ] No temporary or orphaned files remain
    - [ ] All resources (file handles, memory) are properly released
- [ ] Use `diff` or a simple Java validator to compare actual output against a pre‑computed expected output for the known test XML.
- [ ] Implement automated test suite:
    - [ ] Create JUnit tests for core functionality
    - [ ] Add tests for error conditions and edge cases
    - [ ] Include performance benchmarks
    - [ ] Set up continuous integration testing if applicable

## Phase 8: Validation and Benchmark
- [ ] Run the program on a larger set of XML files (e.g., 100 files) to ensure stability.
- [ ] Measure throughput (files/sec) and memory usage.
- [ ] Check for any file descriptor leaks (ensure all writers closed).
- [ ] Confirm that the program can be interrupted (Ctrl+C) and cleans up resources gracefully (optional).
- [ ] Update any relevant documentation (e.g., comments in source) to reflect final implementation.
- [ ] Add comprehensive validation checks:
    - [ ] Validate all output files conform to expected format
    - [ ] Check for data consistency between input XML and output TXT
    - [ ] Verify no data loss or corruption occurred during processing
    - [ ] Ensure all expected columns are present in output
    - [ ] Validate that column 0 contains correct relative file paths
- [ ] Add specific benchmarking criteria:
    - [ ] Measure processing time for different file sizes (small, medium, large)
    - [ ] Test scaling with different thread counts (1, 2, 4, 8, etc.)
    - [ ] Measure memory usage under load
    - [ ] I/O performance metrics (read/write speeds)
    - [ ] CPU utilization during processing
- [ ] Add stress testing:
    - [ ] Process thousands of files to test stability
    - [ ] Test with very large individual XML files
    - [ ] Test with deeply nested directory structures
    - [ ] Test with ZIP files containing many XML entries
- [ ] Add regression testing:
    - [ ] Create test suite that can be run regularly
    - [ ] Include both positive and negative test cases
    - [ ] Automate validation of fixes and new features
- [ ] Add monitoring and observability:
    - [ ] Implement metrics collection (processing rates, error rates)
    - [ ] Add health check endpoints if applicable
    - [ ] Create dashboards for key metrics (if applicable to deployment context)