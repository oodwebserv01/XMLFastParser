# แผนงาน: XMLFastReader — สถานะปัจจุบันและขั้นตอนต่อไป

> สรุปจากโค้ดที่มีอยู่จริง (implemented) + PLAN เดิมที่ยังค้าง

---

## 1. สถานะโค้ดปัจจุบัน (Implemented)

### 1.1 Core Classes — **เสร็จสมบูรณ์**
| คลาส | สถานะ | หมายเหตุ |
|------|-------|---------|
| `XmlFastReader.java` | ✅ Done | Facade API: `register()`, `parseZip()/parseFile()/parseStream()`, `shutdown()` |
| `XmlPlanner.java` | ✅ Done | Path trie (XmlNode), TOC[128][256] state machine, shared source buffer, global handlers, worker factory |
| `XmlEvent.java` | ✅ Done | 7 event types, 3-level access (raw/ByteView/String), zero-copy, reusable per thread |
| `XmlCallback.java` | ✅ Done | Interface `boolean handle(XmlEvent)` |
| `XmlNode.java` | ✅ Done | Trie node: name bytes, children array, handler, token |
| `XmlDispatcher.java` | ✅ Done | SPSC ring buffer (power-of-2), producer/consumer, worker thread management |
| `XmlWorker.java` | ✅ Done | Per-thread worker, holds XmlRunner |
| `XmlRunner.java` | ✅ Done | Parse loop, TOC-driven, linearize buffer, state flags |

### 1.2 Producers — **เสร็จสมบูรณ์**
| คลาส | สถานะ | หมายเหตุ |
|------|-------|---------|
| `XmlProducer.java` | ✅ Done | Interface |
| `FileXmlProducer.java` | ✅ Done | Single file / ZIP file (memory-mapped) |
| `SingleXmlProducer.java` | ✅ Done | Single XML from InputStream |
| `StreamingProducer.java` | ✅ Done | Streaming XML from InputStream |
| `ZipXmlProducer.java` | ✅ Done | ZIP from InputStream |
| `FileZipProducer.java` | ✅ Done | ZIP from File |
| `SingleFileProducer.java` | ✅ Done | Single file producer |

### 1.3 ParseHandle — **เสร็จสมบูรณ์**
- `ParseHandle.java` (inner classes: `Progress`, `ProgressListener`, `Stats`, `ErrorHandler`)

---

## 2. สิ่งที่ยังขาด / ต้องทำต่อ (จาก PLAN เดิม + Code Review)

### 2.1 Critical — Parser Core (XmlRunner.java)
| # | งาน | สถานะ | รายละเอียด |
|---|-----|-------|-----------|
| 1 | **CDATA handling** | ❌ ยังไม่ทำ | TOC มี state SPECIAL แต่ parse loop ยังไม่ handle `<![CDATA[...]]>` |
| 2 | **Entity references** | ❌ ยังไม่ทำ | `<`, `>`, `&`, `"`, `&apos;`, numeric `&#...;` — ตัดสินใจ PLAN 11.3: ส่ง raw ไม่ decode |
| 3 | **Skip unregistered branches** | ⚠️ บางส่วน | มี `skipName/skipDepth` logic แต่ inner text collection ยังไม่ครบ (PLAN 11.5) |
| 4 | **EOF handling / root close** | ❌ ยังไม่ทำ | PLAN 11.8: ปิดท้ายไฟล์, synthetic END events |
| 5 | **XML Declaration parsing** | ⚠️ บางส่วน | TOC มี `onPiEnd()` แต่ encoding detection ยังไม่เชื่อมกับ charset ใน XmlEvent |

### 2.2 Important — API & Usability
| # | งาน | สถานะ | รายละเอียด |
|---|-----|-------|-----------|
| 6 | **Registry freeze (read-only after build)** | ❌ ยังไม่ทำ | PLAN 11.6: แยก phase register/run, lock planner |
| 7 | **Attribute surfacing API** | ❌ ยังไม่ทำ | PLAN 11.9: raw byte vs decoded, format ต้องแน่ใจ |
| 8 | **Error handling refinement** | ⚠️ บางส่วน | ERROR event ส่ง filename อย่างเดียว — ต้องเพิ่ม error detail |
| 9 | **Progress/Stats API** | ✅ มีคร่าวๆ | `ParseHandle.Progress`, `ProgressListener` — ต้อง review ให้ครบ |

### 2.3 Polish — Library Release
| # | งาน | สถานะ | รายละเอียด |
|---|-----|-------|-----------|
| 10 | **Package name / module-info.java** | ❌ ยังไม่ทำ | PLAN 11.10: `module-info.java`, package naming |
| 11 | **License header / LICENSE file** | ❌ ยังไม่ทำ | MIT/Apache-2.0 |
| 12 | **README.md / Documentation** | ❌ ยังไม่ทำ | Quick start, API overview, examples |
| 13 | **Build config (Maven/Gradle)** | ❌ ยังไม่ทำ | `pom.xml` หรือ `build.gradle` |
| 14 | **Unit tests** | ❌ ยังไม่ทำ | Test XML parsing, edge cases, performance |
| 15 | **Benchmark harness** | ❌ ยังไม่ทำ | วัด throughput vs baseline |

---

## 3. ลำดับความสำคัญแนะนำ (Next Steps)

### Phase 1: Parser Completeness (Critical)
1. **CDATA handling** ใน `XmlRunner.parseLoop()`
2. **Entity reference policy** — ตัดสินใจ: pass-through raw (ตาม PLAN 11.3) → document ไว้
3. **Skip unregistered branches** — ให้ inner text ของ branch ที่ skip ไม่ leak มา
4. **EOF / synthetic END** — flush stack, fire `FILE_END`
5. **XML Declaration → charset** — เชื่อม `onPiEnd()` กับ `XmlEvent.charset`

### Phase 2: API Hardening
6. **Registry freeze** — `planner.freeze()` / builder pattern
7. **Error detail in ERROR event** — เพิ่ม field `errorMessage` หรือ `exception` ใน XmlEvent
8. **Attribute API finalize** — ตรวจ `ByteView`, `asString()`, raw offset/len

### Phase 3: Library Release Prep
9. `module-info.java`, package `xmlfastreader` (lowercase)
10. `pom.xml` (Maven Central ready)
11. `LICENSE`, `README.md`, `CHANGELOG.md`
12. Unit tests (JUnit 5)
13. Benchmark (JMH)

---

## 4. หมายเหตุจาก Code Review ครั้งนี้

### XmlRunner.parseLoop() — จุดสำคัญที่ต้องดู
- TOC-driven loop: `while (ptr < end) { callback = TOC[flags][byte]; callback.call(this); }`
- State flags: `IN_TAG(1)`, `IN_DQUOTE(2)`, `IN_SQUOTE(4)`, `CLOSE_TAG(8)`, `SPECIAL(16)`, `SPECIAL_COMMENT(32)`, `TARGET(64)`
- Linear buffer: `byte[] linear` ต่อ thread, `int pointer` = write position
- Tag matching: หลังอ่าน tag name ให้ lookup ใน `currNode.children` (O(1) via byte compare)

### XmlPlanner.TOC — 128×256 = 32,768 cells
- สร้างใน constructor → `populateTOC()`
- Cell type: internal `callback` interface (ต่างจาก `XmlCallback` user-facing)
- Default = `IGNORE` (consume byte, advance pointer)

### XmlDispatcher — SPSC Ring Buffer
- `queueSize` = power of 2 (default 512)
- `RegionDescriptor` = `{start, end, filename}` ชี้ shared buffer
- Producer: `FileXmlProducer` / `ZipXmlProducer` โหลด ZIP ทั้งไฟล์เข้า `byte[] source` แล้วสร้าง descriptor
- Consumer: worker threads ดึง descriptor → inflate entry → parse

---

## 5. ไฟล์ตัวอย่าง (test/sample) — ใช้เป็น Integration Test
- `InvImportFolder.java` — main entry, process folder of ZIP/XML
- `InvImportSuperXML.java` — business logic callbacks, 60+ registered paths
- ใช้ทดสอบ end-to-end: register paths → parse ZIP → verify output

---

> **Next Action**: เริ่ม Phase 1 — CDATA handling ใน `XmlRunner.java` (parse loop)