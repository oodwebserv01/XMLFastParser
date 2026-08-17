# TODO Checklist - XML2TXT Implementation

## Phase 1: Blueprint Parsing
- [ ] Refactor `ReadBPFile` to return a rich `BPConfig` object
- [ ] Update `BPConfig` to include:
    - `List<String> fileTypes`
    - `List<String> entityPaths`
    - `List<Boolean> useRootAsEntity`
    - `List<ColumnSpec> columns` (with `fileTypeIndex` and `columnIndex`)
- [ ] Implement `/.` shorthand resolution in `parseColumnSpec`:
    - When a column path starts with `./`, replace each `./` with the preceding path segment from the previous non-empty line in the .bp file.
    - Handle edge cases: no previous line, multiple `./` sequences.
- [ ] Validate parsing with both Example 1 (full paths) and Example 2 (shorthand) from GOAL.md.
- [ ] Write unit tests for `ReadBPFile` using the provided `sample.bp` and additional edge‑case .bp files.

## Phase 2: Data Structures
- [ ] Define `ColumnSpec` with fields:
    - `String path` (original)
    - `String elementPath` (stripped of `@`/`#`)
    - `String type` (`"@"`, `"#"`, or `"entity"`)
    - `String attrName` (if type=`"@"`)
    - `int columnIndex` (position within the fileType's column list, starting at 1)
    - `int fileTypeIndex` (which `file:` block this column belongs to)
- [ ] Define `ThreadContext` per parser thread:
    - Thread number (`threadNo`)
    - Maps `fileType -> BufferedWriter` (data files)
    - Maps `fileType -> StringBuilder` (batch buffer)
    - Maps `fileType -> AtomicInteger` (row counter)
    - Shared log writers/buffers/counters for `read`, `success`, `fail`
    - `Map<String, String[]> columnBuffers` (fileType -> array sized `columnCount+1`, index 0 reserved for source file name)
    - `Map<String, Integer> columnCounts` (fileType -> total columns including source file column)
    - `Map<Integer, FileInfo> fileIdMap` (hash(fileName) -> FileInfo)
    - Methods: `registerFile`, `getFileInfo`, `initColumnBuffer`, `clearColumnBuffer`, `setColumnValue`, `buildRow`
- [ ] Ensure `ThreadContext` is thread‑safe (each thread has its own instance; maps are not shared across threads).

## Phase 3: xmlBluePrint Registration
- [ ] Pre‑compute `columnIndex` for each `ColumnSpec` per fileType (excluding source file column 0).
- [ ] Group `ColumnSpec` by `elementPath` (stripped of `@attrName` or `#`).
    - For each group, build `Map<String, List<ColumnSpec>> attrMap` and `List<ColumnSpec> innerTextCols`.
- [ ] Register **one** callback per unique `elementPath` with xmlBluePrint:
    - Pass the corresponding `ThreadContext` as userData (`idToken` for column callbacks).
    - In the callback:
        - On `EV_ATTR`: extract attribute name, find matching `ColumnSpec`s in `attrMap`, if current fileType matches, set column value.
        - On `EV_INNER_TEXT`: extract inner text, do the same for `innerTextCols`.
- [ ] Register entity handlers:
    - Use `hash(fileName)` as `idToken` when calling `pushJob`.
    - Callback receives the `idToken`, looks up `FileInfo` via `ThreadContext.getFileInfo(fileId)`.
    - On `EV_OPEN_TAG`: set `ctx.currentFileType`, `ctx.currentSourceFile` (column 0), and clear column buffer for that fileType.
    - On `EV_CLOSE_TAG`: build row via `ctx.buildRow(fileType)`, append to batch buffer, flush when ≥1000 rows.
- [ ] Register log handlers (read, success, fail) at document level:
    - `EV_OPEN_TAG` at root → append file name to appropriate pending log buffer.
    - `EV_CLOSE_TAG` at root (or via a completion callback) → move file name from pending to success/fail logs.
    - Ensure log buffers are flushed with same batch size.

## Phase 4: File Processing Loop
- [ ] Implement `FileEntry` class:
    - `byte[] bytes` (file content)
    - `String fileName` (original name, used for column 0)
    - `String originalPath` (full path, used for backup rename)
- [ ] Implement `listAllFilesWithZip(String sourcePath)`:
    - Walk source directory recursively.
    - For each `.xml` file: read bytes, create `FileEntry`.
    - For each `.zip` file: open with `java.util.zip.ZipFile`, enumerate entries, for each `.xml` entry extract bytes and create `FileEntry`.
- [ ] In `ProcessFiles`:
    1. Parse arguments (`-p`, `-s`, `-d`, `-t`).
    2. Read blueprint via `ReadBPFile`.
    3. Generate timestamp (`yyyyMMddHHmmss`).
    4. Create `ThreadContext` instances for each thread (0 … threadCount‑1).
    5. Initialize writers for each fileType and log types (pending files).
    6. Register all callbacks with xmlBluePrint (entity + column + log).
    7. For each `FileEntry`:
        - Compute `fileId = fileName.hashCode()`.
        - Register the file in every `ThreadContext` (`ctx.registerFile(fileName, fileType, entityPath)`).
        - Call `xmlBluePrint.pushJob(bytes, bytes.length, fileName.getBytes(), fileName.length(), fileId)`.
    8. Call `xmlBluePrint.run()`.
    9. Wait until `xmlBluePrint.isReadyToDown()`.
    10. Flush all remaining data and log buffers.
    11. Close all writers.
    12. Rename pending → final files (append timestamp).
    13. Backup processed source files (rename to `xml<name>.bak` or `zip<name>.bak`).
    14. Call `xmlBluePrint.shutdown()`.

## Phase 5: Output and Logging
- [ ] Verify column 0 of every data row contains `relativePath/xmlFileName.xml` (relative to source folder).
- [ ] Ensure pending files are named:
    - `{fileType}_{thread:02d}_pending`
    - `read_pending`, `success_pending`, `fail_pending`
- [ ] Ensure final files are renamed to:
    - `{fileType}_{thread:02d}_{yyyyMMddHHmmss}.txt`
    - `read_{yyyyMMddHHmmss}.txt`, etc.
- [ ] Confirm batch flush occurs after every 1000 rows (configurable constant).
- [ ] Verify that log files contain exactly one line per processed XML file (read log), one per success, one per failure.

## Phase 6: Backup and Rename Logic
- [ ] Implement `renamePending(destPath, pendingName, finalName)` using `File.renameTo`.
- [ ] Implement `renameProcessedSource(originalPath)`:
    - If file ends with `.xml` → `xml<basename>.bak`
    - If file ends with `.zip` → `zip<basename>.bak`
    - Only rename if the target does not already exist.
- [ ] Ensure backup operation occurs after successful final rename.

## Phase 7: Testing in Lab
- [ ] Copy `Lab/sample.bp` and any needed XML/ZIP test files into `Lab/test_data` (create if missing).
- [ ] Create a minimal XML file (`Lab/test_data/sample.xml`) that matches the blueprint paths (use real values from the sample or generate dummy data).
- [ ] Optionally create a ZIP containing the XML to test ZIP handling.
- [ ] Test Cases:
    1. **Single‑thread, no ZIP** – run `java -cp xmlFastParser.jar:. XML2TXT -p Lab/sample.bp -s Lab/test_data -d Lab/output -t 1`
    2. **Multi‑thread** – same command with `-t 4`
    3. **ZIP only** – place `sample.xml` inside `test.zip`, run with source pointing to the ZIP directory.
    4. **Mixed XML + ZIP** – both plain XML and ZIP in source.
    5. **Shorthand paths** – verify that Example 2 style (`/.`) works by creating a .bp that uses `/.` and checking output.
    6. **Empty results** – ensure no crash when XML lacks some optional elements.
    7. **Malformed .bp** – detect missing `entity:` or bad syntax and report error.
- [ ] After each run, verify:
    - Output files exist with correct naming.
    - Column 0 matches the source file name (relative path).
    - Expected columns are present and correctly filled.
    - Log files contain expected entries.
    - Source files have been renamed to `.bak`.
- [ ] Use `diff` or a simple Java validator to compare actual output against a pre‑computed expected output for the known test XML.

## Phase 8: Validation and Benchmark
- [ ] Run the program on a larger set of XML files (e.g., 100 files) to ensure stability.
- [ ] Measure throughput (files/sec) and memory usage.
- [ ] Check for any file descriptor leaks (ensure all writers closed).
- [ ] Confirm that the program can be interrupted (Ctrl+C) and cleans up resources gracefully (optional).
- [ ] Update any relevant documentation (e.g., comments in source) to reflect final implementation.