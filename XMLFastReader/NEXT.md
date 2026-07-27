# โน้ตคุยต่อคราวหน้า (snapshot)

> **อ่านคู่กับ `PLAN.md` เสมอ** — ไฟล์นั้นคือแผนฉบับเต็ม โน้ตนี้เป็นแค่จุดตั้งต้น

## เราคุยถึงไหนแล้ว
- ปรับ `SampleUse.java` → ทำเป็น **open-source XML parser lib** เน้นเร็ว + ไฟล์จำนวนมาก
- โครง 3 คลาส: (1) interface callback (2) state + คิวงาน (3) registry + handler พื้นฐาน
- **ตัดสินใจแล้ว** (ดูรายละเอียดใน PLAN.md):
  - คิวงานถือ **region descriptor `{start, end, filename}`** ชี้ช่วงบน shared read-only buffer (ไม่ใช่ InputStream)
  - **linearize ต่อไฟล์** (buffer เชิงเส้นต่อเทรด ขยายได้ reuse) → zero-copy offset+length เสมอ
  - callback ส่ง zero-copy `{bufferRef, offset, length, charset, filename}` (reuse, บริโภคในฟังก์ชัน)
  - topology: ZipFile/memory-map + inflate ต่อเทรด + SPSC N คิว = N parser
  - คิวเริ่มต้น 512 (2^9), config ได้, วัดจริงก่อนปรับ
  - byte reading + bit flags `TOC[flags][byte]` + UTF-8 skip 4 เคส
  - เทียบ child ด้วย byte (length ก่อน แล้ว content), ตัด namespace prefix
  - log: lib ไม่บริหารเอง, reject แจ้งแค่ชื่อไฟล์, เทรดไม่หยุด

## ความคืบหน้าโค้ด
- [x] **XmlEvent.java** — เขียนแล้ว (holder ล้วน public; event 7 แบบ; filename char[]+asString; attrName/value 3 ระดับ raw/CharSequence view แยก 2 ตัว/asString reuse char[]; charset=Charset object; reset())
- [x] **XmlCallback.java** — เขียนแล้ว (public interface; `boolean handle(XmlEvent e)`; false=soft reject เฉพาะ event ปกติ, FILE_*/ERROR คืนค่าไม่มีผล)
- [x] **XmlRunner.java (Class 2 — state + คิว) — เสร็จสำหรับเฟสนี้**
  - คิว SPSC ring buffer `int[512]` `begin/end/fileName` + `push/pull/getRemain`; wP/rP volatile; **แก้ pointer bug** `(wP++)`→`(wP+1)` mask (เดิม wP/rP ไม่เคลื่อน)
  - `xmlState` (byte) + 6 bit setter (IN_TAG/IN_DQUOTE/IN_SQUOTE/CLOSE_TAG/SPECIAL + **SPECIAL_COMMENT** bit5 ตาม PLAN 6.2/6.6)
  - skip___ (PLAN 6.5): `skipName`(byte[32],capped)/`skipNameLen`/`skipDepth` + `enterSkip()`/`isSkipClose()` — ข้ามกิ่งที่ไม่ลงทะเบียน (ชื่อ>32 ตัดไม่สน)
  - `currNode`(XmlNode)/`encoding`(Charset)/`linear`(byte[8192]) + `ensureLinear(int need)` (high-water)
  - `planner` ref + `setPlanner()` + `resetState()` (รีเซ็ตต่อไฟล์)
  - ⚠️ คอมไพล์ไม่ได้จนกว่า `XmlPlanner` + field `root` จะมี (resetState เรียก `planner.root`)
  - parse loop / linear position — รอคลาส XmlPlanner (PLAN 10)
- [x] **XmlNode.java** — สร้างแล้ว (PLAN 7.1/7.2): name byte[]+nameLen, handler/token, `findChild(buf,off,len)` เทียบ byte, `addChild()` build-phase
- [ ] XmlPlanner (register/registFileBegin/registFileEnd/registError + graph/trie + TOC engine + field `root`)

## สรุปที่ตัดสินแล้วรอบนี้ (XmlEvent + error)
- ชื่อคลาส `XmlEvent` / `XmlPlanner`
- register: `register(path,handler,token)` + `registFileBegin/registFileEnd/registError(handler,token)`
- event 7: FILE_BEGIN/TAG/ATTR/INNER/END/FILE_END/ERROR
- bufferRef = **linearize buffer ต่อเทรด** (parser set ครั้งเดียวตอน bind)
- filename 2 ระดับ (char[]+asString, decode ครั้งเดียว/ไฟล์); attrName/value 3 ระดับ; CharSequence view แยก 2 ตัว; asString reuse char[] คืน String ใหม่
- charset = `java.nio.charset.Charset` object ระดับเดียว
- entity/CDATA = ไม่แปล ถือเป็น text; `<!--`→skip ถึง `-->`, `<!`อื่น→skip ถึง `>`
- return false (event ปกติ) = fail ระดับข้อมูล ข้ามไฟล์; FILE_*/ERROR return ไม่มีผล

## ข้อตัดสินเพิ่มรอบนี้ (XmlRunner / สถาปัตย์)
- **XmlRunner เป็นเจ้าของ parse loop** (ชื่อก็คือ runner) → state private ใช้ในคลาสได้, เรียก `XmlPlanner` แค่ดู graph/TOC (ทางเลือก ก)
- **root อยู่ใน `XmlPlanner`** (tree เดียว แชร์อ่านข้ามเทรด, read-only ตาม 11.6) → `XmlRunner` มีแค่ `currNode` (working pointer), รีเซ็ต = `planner.root` ทุกไฟล์
- **bit flag 6 ตัว** (PLAN 6.2/6.6): IN_TAG/IN_DQUOTE/IN_SQUOTE/CLOSE_TAG/SPECIAL + **SPECIAL_COMMENT** (bit5) ใน `xmlState` (byte); bit6–7 สงวน
- **ข้ามกิ่งที่ไม่ลงทะเบียน (PLAN 6.5)**: เก็บชื่อแท็กแรก (capped 32 ไบต์, ส่วนเกินไม่สน) + `skipDepth` นับชั้นชื่อซ้ำ → ทนทานกว่า `childLevel` เก่า
- **แก้ pointer bug**: `(wP++)`→`(wP+1)` mask (เดิม ring ติดตาย)
- queue descriptor = `{begin,end,fileName}` ตาม PLAN 4.2

## ข้อตัดสินเพิ่มรอบนี้ (TOC + SPECIAL + self-close + EOF)
- **รูปแบบ TOC ใหม่ `TOC[flags][byte].call`** (PLAN 6.6) — แทน `toc1-4` ของ SampleUse เก่า:
  - `flags` = 6 bit flags ของ XmlRunner (`xmlState & 0x3F` → **64 แถว** รวม `SPECIAL_COMMENT` bit5); `byte` = **raw byte 8-bit** (`buffer[p] & 0xFF` → **256 คอลัมน์**); `TOC[64][256]` = 16384 cell
  - cell = **internal `callback`** (แยกจาก `XmlCallback` user-facing: `boolean handle(XmlEvent)`) — ตัวนี้แตะ `XmlRunner` โดยตรง; คืน false = หยุด parse ไฟล์
  - แมป old→new: `TOC_2≈IN_TAG`, `TOC_3≈IN_DQUOTE`, `TOC_4≈IN_SQUOTE`, `TOC_1≈` ก้าวแรกหลัง `<`
- **SPECIAL substate** → เพิ่ม `SPECIAL_COMMENT` (bit5): `<!--` skip ถึง `-->` (ตัวแปร `sawDash` จำ `--` ใน loop), `<!` อื่น skip ถึง `>`; ไม่แยก `<?` (ตามแผน 11.4 ปล่อยตามเดิม)
- **self-close `/>`** → ไม่ต้อง flag ใหม่: ลงไปใน node (หา handler) แล้ว emit `END` + ขึ้นกลับทันที (currNode โมเดลเดิม)
- **EOF / flush** (PLAN 11.8) → จุดจบ = **แท็กปิดของ root** (ชื่อรู้จาก tree); ถึง `end` โดยไม่เจอ root close → เข้า try/catch ข้ามไฟล์ (8.5.3)
- **เจ้าของ shared buffer** → อยู่ใน `XmlPlanner` (ออบเจ็กต์เดียว แชร์อ่านข้ามเทรด เหมือนกราฟ/tree); `XmlRunner` ถือแค่ `linear` ต่อเทรด + state, **ไม่ถือ shared buffer ใหญ่**; คิว `begin/end` คือ offset เข้าหน้า shared buffer ของ planner → อ่านร่วม read-only ไม่ต้อง lock (PLAN 4.2/11.7)
- เหลือเขียน: ตาราง `TOC[64][256]` (internal `callback` แยกจาก `XmlCallback`) + parse loop ใน XmlRunner — เรียก `planner.TOC[xmlState&0x3F][buf[p]&0xFF].call(runner)` (raw byte ไม่ต้องจำแนก class)

## หัวข้อค้างที่ต้องตัดสินใจต่อ (จาก PLAN.md ข้อ 11)
- [x] 11.2 lazy decode — เบสเคส: parser ไม่ decode เลย ส่งแค่ `{offset,size}` (byte) สำหรับ ชื่อแท็ก/attr/value/inner → user ประกอบ String เองด้วย `charset` จากหัวไฟล์ (PLAN 11.2); buffer ต่อเทรด bind ครั้งเดียว ต่อ event ส่งแค่ offset+size; XmlEvent รองรับโมเดลนี้แล้ว (buffer+*Off+*Len)
- [x] 11.3 entity references → ไม่ decode ส่ง raw (text ล้วน)
- [x] 11.4 SPECIAL substate → เพิ่ม `SPECIAL_COMMENT` (bit5, PLAN 6.6); `<!--`→`-->`, `<!`อื่น→`>`, `<?` ปล่อยตามเดิม
- [x] 11.5 ข้ามกิ่งที่ไม่ลงทะเบียน → PLAN 6.5 (name-keyed depth counter: skipName/skipNameLen/skipDepth แทน childLevel เก่า); ยังเหลือพอร์ต inner text collection  itself
- [ ] 11.6 registry read-only หลัง build (แยกเฟส register/run)
- [x] 11.8 EOF / flush ปิดท้าย → แท็กปิด root (ชื่อจาก tree), ถึง `end` โดยไม่เจอ → ข้ามไฟล์ (8.5.3)
- [ ] 11.9 attribute surfacing (raw byte vs decoded, รูปแบบ)
- [ ] 11.10 license / package / ชื่อ API
- [ ] สรุปตัวเลข hysteresis คิว (4.8.6) + limit parser ต่อ provider (รอวัด)
- [x] รูปแบบ TOC core → `TOC[flags][byte].call` (PLAN 6.6) `[64][256]` แทน toc1-4 เก่า; internal `callback` แยกจาก `XmlCallback`; raw byte ไม่ต้องจำแนก class

## ข้อควรจำ (กฏจาก CLAUDE.md)
ตอบสั้น / ไม่ทำสิ่งที่ไม่ได้ขอ / ทำตามคำสั่งตรงๆ / ห้ามอธิบายเกินจำเป็น / คิดไม่เกิน 10 ครั้ง

## เริ่มคราวหน้าอย่างไร
พิมพ์ประมาณว่า: "อ่าน PLAN.md กับ NEXT.md แล้วคุยต่อจากหัวข้อค้าง"
