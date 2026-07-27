# แผนงาน: ปรับปรุง XMLFastReader เป็น Open-Source Library

> สรุปจากการหารือ — ฉบับครบถ้วน ไม่ตกหล่น

---

## 1. เป้าหมายรวม
- ปรับปรุงอัลกอริทึมจาก `SampleUse.java` (โค้ดเก่า เขียน 3 วัน) ให้สมบูรณ์ขึ้น แล้วทำเป็น **open-source library**
- เน้น **ความเร็วสูง** และ **ถอดข้อมูลจากไฟล์จำนวนมาก** (อ้างอิงของเดิม ~1 ล้านไฟล์/10 นาที ≈ 2 ไฟล์/ms)
- คงจุดเด่นเดิม: ไต่ graph/tree ของ path ที่ลงทะเบียน → ตรวจ child แบบ O(1) แทนการ match ทั้ง path

---

## 2. ขอบเขต (Scope)

### 2.1 หลักการแยกส่วน
- แยก **ส่วนอ่าน/parse** ออกจาก **ส่วนแปลงข้อมูล (business logic)**
- core = XML parser ล้วนๆ **ไม่มี business logic**
- ไม่ทำแบบเดิมที่อ่านไฟล์ + แปลงข้อมูลเสร็จในตัวเอง

### 2.2 จะเขียน 3 ส่วนวันนี้
1. **Interface** สำหรับ callback (คงแนวคิด `callback` เดิม)
2. **Class เก็บ state + คิวงาน**
3. **Class เก็บการลงทะเบียนเส้นทาง (path) + handler พื้นฐานของ XML**

### 2.3 ยังไม่เขียนตอนนี้
- ตัวจัดการ/ขับคิว (consumer, ตัวสร้างเทรดแจกคิวงาน, threading)
- handler ที่ถอดข้อมูลตาม callback (เป็นส่วนของ user)

### 2.4 หน้าที่ของ API ตอนนี้
- มี **คิวงาน** ให้ยัด **งาน (region descriptor)** เข้ามา — `{startPoint, endPoint, filename}` ชี้ช่วงบน buffer ที่แชร์ (ดูข้อ 4.2 / 11.7)
- มีฟังก์ชัน `register()` ให้ user ลงทะเบียน path + handler
- อ่าน XML แล้ว **call กลับ** ไปยัง handler ที่ลงทะเบียนไว้

---

## 3. Class 1 — Interface (callback)

### 3.1 parameter object `XmlEvent` — **ตัดสินใจแล้ว**
- ชื่อคลาส: **`XmlEvent`**
- คง interface สำหรับ callback ไว้ ให้ user implement handler เพื่อรับการเรียกกลับ
- เป็น **mutable holder ล้วน** — parser `push` (set), user `get` เท่านั้น ไม่มี business logic; visibility **public** ทั้งหมด
- **1 instance ต่อ 1 parser thread (reuse)** — ห้ามแชร์ข้ามเทรด, ห้าม user เก็บ reference ไว้ใช้นอก callback
- **องค์ประกอบ:**
  1. **filename** — ชื่อไฟล์ XML (จาก region descriptor 4.2)
  2. **charset** — `java.nio.charset.Charset` จาก header (6.3) ใช้เวลาแปลง string
  3. **event** — (ดู 3.1.3)
  4. **token** (Object) — ฝากตอน register แล้วคืนคืนนี้ทุกครั้ง (ดู 7.0)
  5. **attribute name** — ชื่อ attribute (event `ATTR`); ถ้าไม่มี → `null`
  6. **value** — ค่า: เป็น **attribute value** ใน event `ATTR` และเป็น **innerText** ใน event `INNER`; ถ้าไม่มี → `null`
- **pointer (offset) เป็น `int`** — ไฟล์ละเทรด <2GB พอ; ใช้คู่กับ length ภายใน (ไม่เปิดเผยเป็น field แยก แต่ซ่อนใน 3 ระดับ)
- **event มี 7 แบบ:**
  1. `FILE_BEGIN` — เริ่มไฟล์ (ผ่าน `registFileBegin`, ดู 7.0); แจ้ง filename
  2. `TAG` — พบแท็กที่ลงทะเบียน (ไม่ใส่ attrName/value)
  3. `ATTR` — พบ attribute (ใส่ attribute name + value)
  4. `INNER` — พบ innerText chunk (ใส่ value; attribute name = `null`); **อาจ fire หลายครั้งต่อ element เดียว** (text node ระหว่าง child tags) — parser ไม่ concat, user concat เอง
  5. `END` — สิ้นสุดแท็ก
  6. `FILE_END` — จบไฟล์ (ผ่าน `registFileEnd`, ดู 7.0); แจ้ง filename
  7. `ERROR` — ไฟล์เสีย (ผ่าน `registError`; แจ้งแค่ filename; value = `null` — ตาม 8.5.2)
- **zero-copy contract:** object + buffer ถูก reuse/เขียนทับ → ผู้ใช้ต้อง **บริโภคภายใน callback** ถ้าจะเก็บต่อ ต้อง copy เอง
- **จุดประสงค์ของ token:** ให้ผู้ใช้ใช้ handler ตัวเดียวร่วมกันทุกแท็กได้ (register หลาย path ชี้ handler เดียว) แล้วใช้ token แยก state/identify ภายใน handler — เหมาะกับโปรแกรมที่จำนวนคอลัมน์ไม่แน่นอน

### 3.2 การอ่านข้อมูลออกจาก XmlEvent — **ตัดสินใจแล้ว**
- **attribute name / value → 3 ระดับ** (อยู่บน linearize buffer ต่อเทรด):
  1. **raw region** — offset + length บน linearize buffer (zero-copy) ให้ user เทียบ byte/scan เอง
  2. **`CharSequence` view** — custom, reusable, ชี้ buffer+offset+length → ไม่จอง `String` ใหม่ (compare/scan); **แยก buffer 2 ตัว** สำหรับ attrName กับ value (กันทับกันตอน event `ATTR` user เรียกทั้งคู่)
  3. **`asString()`** — decode เป็น `String` แท้ด้วย **charset**; **reuse char[] ภายใน แต่คืน String ใหม่เสมอ** (user เก็บค่าไว้ Object/array ต้องได้ String อิสระ — alloc 1 ครั้ง หลีกเลี่ยงไม่ได้; char[] decode ถือไว้ reuse ข้าม call)
     - ทางเลือก user: เก็บ `(offset,length)` แล้ว `asString()` ทีเดียวตอน `FILE_END` (linearize buffer อยู่รอดทั้งไฟล์) เพื่อหน่วง allocation
- **filename → 2 ระดับ (char[] + `asString()`)** — ไม่ได้อยู่ใน linearize buffer, set **ครั้งเดียวต่อไฟล์** (ตอน `FILE_BEGIN`) → decode ลง **char[] ที่ XmlEvent ถือไว้** (reuse ข้ามไฟล์, grow ตาม high-water); ตัดปัญหา filename ชี้คนละ buffer
- **charset → `java.nio.charset.Charset` object (ระดับเดียว)** — เป็น object แปลงแล้ว ใช้ตอน decode ของ field อื่น
- เข้ากับ lazy decode (11.2)

### 3.2.1 Inner Text Handling — **ตัดสินใจแล้ว: Streaming chunks, ไม่ concat**
- **หลักการ:** inner text ใน XML มาเป็นชิ้นๆ (text node ระหว่าง child tags) ตามธรรมชาติ — parser **ไม่ concat ให้** ส่ง event `INNER` ทุกครั้งที่เจอ text chunk
- **เหตุผล:** concat ต้อง allocate buffer, copy, grow → ช้า, GC pressure, complex (ต้องจัดการ child tag nesting, CDATA, entity refs)
- **User pattern:** handler รับ `INNER` หลายครั้งต่อ element เดียว → user concat เองถ้าต้องการ (StringBuilder, เก็บ offset/length array, หรือ `asString()` ทีละ chunk)
- **Zero-copy:** parser ส่งแค่ `{offset, length}` บน linear buffer → user ตัดสินใจ decode/concat เอง
- **ตัวอย่าง:** `<A>text1<B/>text2</A>` → `INNER("text1")`, `TAG(B)`, `INNER("text2")`, `END(A)`

### 3.3 `reset()` — **ตัดสินใจแล้ว**
- มีเมธอด `reset()` ล้าง field ทั้งหมด เพื่อ reuse ซ้ำได้เรื่อยๆ ทั้งระหว่าง event และระหว่างไฟล์
- parser เรียกก่อนตั้งค่า event ใหม่ (หรือก่อนไฟล์ถัดไปตาม 8.5.3)

---

## 4. Class 2 — State + คิวงาน

### 4.1 การเก็บ state ต่อการ parse
- แยก state ออกจาก engine → **engine ใช้ซ้ำได้ / รองรับ multi-thread**
- engine (class 3) แชร์แบบ read-only, state (class 2) สร้างแยกต่อ thread/ต่อไฟล์
- เก็บ:
  - **stack** เดิม → แทนที่ด้วย **bit flags** (ดูข้อ 6)
  - **ตำแหน่งปัจจุบันบนกราฟ** (`currNode`)
  - **encoding** ที่อ่านได้จาก header (ดูข้อ 5.2 / 6.2)

### 4.2 คิวงาน (ring buffer ของ region descriptor) — **ตัดสินใจแล้ว**
- **เปลี่ยนจากถือ `InputStream` → ถือ region descriptor: `{startPoint, endPoint, filename}`**
  - ชี้ช่วง byte บน **buffer ที่แชร์ read-only** (ทั้ง ZIP โหลดเข้า memory หรือ memory-mapped file — ดู 11.7)
  - `startPoint`/`endPoint` = ขอบเขตของ entry นั้นบน buffer, `filename` = แนบไว้เป็น metadata (ใช้ตอน callback และตอน reject ไฟล์เสีย)
  - **เจ้าของ shared buffer = `XmlPlanner`** (ออบเจ็กต์เดียว แชร์อ่านข้ามเทรด เหมือนกราฟ/tree — **ไม่ใช่ per-runner**): `begin/end` ในคิวคือ **offset เข้า buffer นี้**; runner แต่ละตัวถือแค่ `linear` ต่อเทรด (เป้าหมาย inflate) + state ของตัวเอง ไม่ถือ shared buffer ใหญ่ (ดู 11.7)
- **ข้อดีของการเปลี่ยนนี้ (ตัดปัญหาไปเยอะ):**
  - ไม่ต้องถือ `InputStream` เปิดค้างต่อ slot → **หมดเพดานเรื่อง FD/handle + inflater buffer 32–64KB ต่อ slot** (ยกเลิกข้อจำกัด 4.8.4 เดิม)
  - slot เล็กมาก (แค่ 2 ตัวเลข + reference ชื่อไฟล์) → คิวลึกได้ถูกลง
  - หลายเทรดชี้อ่านคนละช่วงบน buffer เดียวได้ (read-only, ไม่ต้อง lock)
- เปลี่ยนจากรับทีละตัว → เก็บเป็น **คิว** ป้อนงานเข้ามาคอยได้
- เหตุผลเดิมยังคง: throughput สูง การ lock/sleep รอทรัพยากรแพงเกินไป → ใช้คิวลด contention
- โครงสร้าง:
  - array ปกติ ขนาด **2^n**
  - เลื่อน pointer แบบ mask: `pointer = (pointer+1) & (size-1)` เช่น n=3 → size 8, mask `0b0111`
- **ข้อดีของการเลื่อนแบบ mask:**
  - ไม่มี branch (`if pointer==size`) → ไม่มี branch misprediction
  - ใช้ bitwise AND ตัวเดียว เร็วกว่า modulo `%`
  - wrap อัตโนมัติ ไม่มีทาง index เกินขอบ array
  - โค้ดสั้น คงที่ O(1) เหมาะกับ hot loop

### 4.3 read / write pointer
- ฝั่งอ่านเลื่อน: `rP = (rP+1) & (size-1)`
- ฝั่งเขียนเลื่อน: `wP = (wP+1) & (size-1)`
- แยก `readPointer` (rP) กับ `writePointer` (wP)

### 4.4 การนับจำนวนงานค้างในคิว
- สูตร: `(wP - rP) & (size-1)`
- สูตรที่จะใช้จริง (กันเครื่องหมายลบ ชัดเจนกว่า): `0b0111 & ((0b1000 | wP) - rP)`
  - set high bit ก่อน ทำให้ผลลบเป็นบวกเสมอ ไม่ต้องพึ่ง two's complement
  - ตัวอย่าง: wP=6,rP=2 → 4 ; wP=1,rP=6 → 3
- ข้อจำกัด: นับได้สูงสุด size-1 (เว้น 1 ช่องแยก "เต็ม" กับ "ว่าง")

### 4.5 SPSC lock-free (single-producer / single-consumer)
- producer เขียนเฉพาะ `wP`, consumer เขียนเฉพาะ `rP` → ไม่มีการเขียนทับตัวแปรเดียวกัน
- ต่างฝ่ายแค่ "อ่าน" pointer ของอีกฝ่าย ถึงได้ค่าเก่า (stale) ก็แค่ประเมินแบบอนุรักษ์นิยม (เห็นค้างน้อยกว่าจริง / ว่างน้อยกว่าจริง) → ไม่พัง ไม่กระโดดถอยหลัง ไม่เขียนทับผิด
- **ข้อควรระวัง:** ใน Java ต้องกัน reordering/visibility (เช่น `volatile` บน wP/rP)

### 4.6 Object reuse / pooling
- แต่ละช่องคิวมี object (region descriptor `{startPoint, endPoint, filename}`) ประจำอยู่แล้ว
- producer หยิบ object ที่ช่อง `wP` มา **reuse** เขียนค่า start/end/filename ของ entry ใหม่ทับของเดิม แทนการ `new` → ลด GC/allocation
- **guard:** เติมเมื่อคิวเหลือน้อย (ตรวจ `(wP-rP)&mask`) — เหลือน้อย = consumer (rP) ตามชน wP ติดๆ → ช่องหน้า wP ว่างเยอะ เขียนทับได้โดยไม่ทับงานที่ยัง parse ไม่เสร็จ
- ต้องเช็คว่ายังไม่เต็ม (ไม่ไล่ทับ rP) ก่อนเลื่อน wP

### 4.7 Reuse ได้ / ไม่ได้ (ตอนทำ thread dispatcher ภายหลัง)
- **Reuse ได้:** region descriptor ประจำช่อง (เขียน start/end/filename ทับ), linearize buffer ต่อเทรด, inflater ต่อเทรด, object เก็บ state (flags/ตำแหน่งกราฟ/encoding)
- **ไม่มี `InputStream` เปิดค้างในคิวแล้ว** (เปลี่ยนเป็น region descriptor ตาม 4.2) → ปัญหา reuse InputStream ที่เคยเป็นข้อจำกัดหมดไป
- การ inflate/อ่าน entry จริงเกิดฝั่ง **parser thread** จาก region ที่ชี้ ไม่ใช่ฝั่ง producer

### 4.8 ขนาดคิว (queue size)
- แม้เราพูดถึง "parser" เป็นหลัก แต่ **คิวงานอยู่ใน class นี้** จึงต้องกำหนดขนาดคิวด้วย
- ต้องเป็น **2^n** (เพื่อใช้ mask `& (size-1)` ตามข้อ 4.2–4.4)

#### 4.8.1 สมมติฐานการใช้งาน: ผู้ใช้ป้อนจาก ZIP / stream
- ตั้งสมมติฐานว่า **ผู้ใช้ใช้กับ ZIP** เพราะเป็นโซลูชันรับส่งไฟล์จำนวนมากตามธรรมชาติ
- ต่อให้ใช้กระบวนการอื่น ก็ต้องแปลงเป็น `InputStream` ถึงจะใช้ API เราได้อยู่ดี
- **ถ้าเป็นไฟล์จริงจาก OS**: producer จะเป็นคอขวด (open syscall + seek + AV บน Windows) → ทำได้แค่ thread น้อยๆ หรือ 1:1 เพราะ parser ใหม่ควรเร็วกว่าเดิม จึงไม่โฟกัสเคสนี้
- ควรรองรับ **InputStream จาก ZIP entry เป็น first-class** ในคิว ไม่บังคับเปิดไฟล์จริงต่อไฟล์

#### 4.8.2 แก้สมมติฐาน baseline (ทำไม 2 ไฟล์/ms ไม่ใช่ค่าอ้างอิงของ parser)
- ตัวเลขเดิม ~1.67–2 ไฟล์/ms มาจากโค้ดเก่าที่ทำ **unzip + parse + transform + เขียน log** รวมกัน
- parser ใหม่ (ไม่ transform / ไม่ log ในตัว) น่าจะเร็วกว่านั้นในส่วน parse (คาดหวัง ~4 ไฟล์/ms แต่ **ยังไม่วัดจริง**)
- ZIP vs ไฟล์จริง OS: ZIP เร็วกว่ามาก (handle เดียว, sequential I/O, ไม่มี open/seek/AV ต่อไฟล์) แลกกับ CPU inflate เล็กน้อย → ยิ่งย้ำว่า baseline ต้องวัดใหม่บนเงื่อนไข ZIP

#### 4.8.3 ตรรกะเลือกความลึก + sleep ของ producer
- การ **sleep เป็นไปไม่ได้ในฝั่ง parser** (เร็วระดับ <1 ms/ไฟล์) แต่ **ตัวแจกงาน (producer) sleep ได้**
- ปัญหา: `sleep(n)` ตื่นไม่ตรงเวลา ปัดขึ้นไปที่ timer tick ถัดไป
  - Windows ปกติ ~15.6 ms (64 Hz)
  - หยาบสุด/legacy ~54.9 ms (18.2 Hz ≈ 1/18 s)
- ไฟล์ที่ consumer กินต่อ 1 tick 55 ms (เผื่อหยาบสุด): 1.67/ms→~92, 2/ms→~110, 4/ms→~220
- คิวต้องลึกพอ = **floor (กัน starve ≥1 tick) + band (batch ต่อการตื่น ≥1 tick) + headroom ≈ 2–3 เท่าของไฟล์/tick**
  - ที่ ~220/tick → ~440–660 → ปัดเป็น 2^n
- **256 (2^8) ตื้นไป** — แค่ ~1 tick, producer แทบไม่ได้หลับ (ตื่นเติมนิดเดียวเกือบทุก tick)

#### 4.8.4 ~~เพดานจากจำนวน stream เปิดค้าง~~ — **ยกเลิกแล้วหลังเปลี่ยนเป็น region descriptor (4.2)**
- เดิม: กังวลว่าทุก slot ถือ InputStream เปิดค้าง (Inflater 32–64KB + FD) → จำกัดความลึกคิว
- **หลังเปลี่ยน slot เป็น `{startPoint, endPoint, filename}`** → slot ไม่ถือ stream/handle อีกต่อไป, เพดานนี้หายไป
- ความลึกคิวจึงถูกจำกัดแค่ด้วยตรรกะ sleep/tick (4.8.3) และ memory ของ descriptor เล็กๆ เท่านั้น → **ขยายลึกได้ถูกลงมาก** ถ้าจำเป็น (แต่ค่าเริ่มต้น 512 ยังพอ, ดู 4.8.5)
- หมายเหตุ: inflater buffer ย้ายไปเป็น **per parser-thread** (ไม่ใช่ per-slot) → จำนวน inflater = จำนวนเทรด ไม่ใช่ความลึกคิว

#### 4.8.5 ข้อสรุป (ค่าเริ่มต้น)
- **เริ่มต้นที่ 512 (2^9)** — ให้ ~2–3 tick, producer หลับได้จริง, เติม batch ทีละร้อยกว่าไฟล์
- ทำเป็น **ค่าที่ config ได้** ตอนสร้าง instance
- **ต้องวัดจริงก่อน fix ค่า** 2 อย่าง: (1) อัตรา drain จริงของ parser ใหม่ (2) จำนวน stream เปิดค้างที่ระบบรับไหว
- **หลังทดลองแล้วค่อยปรับจูนใหม่**

#### 4.8.6 ตรรกะเติมงานของ producer (hysteresis) — ยังไม่สรุปตัวเลข
- แนวคิด: เติมเมื่อคิวต่ำกว่า threshold จนถึง limit (hysteresis) — *ถูกทาง* แต่ค่าคงที่ยังต้องปรับ
- ข้อควรระวังที่คุยกันไว้:
  - **band อย่าแคบเกิน** (เช่น 220→254 บนคิว 256 กว้างแค่ 34) ไม่งั้น producer ตื่นถี่ เสียการ batch → band ควรกว้างขึ้นตามคิว 512
  - **เว้น headroom จากขอบ** อย่าเขียนจนเต็ม (count max = size−1) กัน off-by-one ไปชน rP
  - ค่าคงที่ (floor/threshold/limit) ควร **derive จากอัตราที่วัดได้** ไม่ hardcode ผูกกับสมมติฐานเดียว
  - เมื่อทุกคิว ≥ threshold **ต้อง sleep จริง** อย่า busy-spin แย่ง CPU ของ parser
- **จำนวน parser ต่อ 1 provider**: ต้องมี limit แต่ **ยังไม่มีตัวเลข** ว่ารองรับได้กี่ตัว → รอวัด

---

## 5. การอ่านไฟล์แบบ byte

### 5.1 เปลี่ยนจาก char → byte ดิบ
- ของเดิมอ่านแบบ char (`InputStreamReader`) เพราะรีบ
- ใหม่: อ่าน **byte ดิบ**
  - เก็บ encoding จาก XML header ไว้แปลงทีหลัง (lazy decode)
  - เร็วกว่า (ไม่ decode ทุก byte)
  - ตาราง TOC ยังขนาด **256** พอดีกับ 1 byte

### 5.2 การจัดการ byte สูง (UTF-8 multi-byte)
- ไม่รองรับ legacy 8-bit encoding แล้ว (แทบไม่มีใครใช้)
- handle ตาม leading byte:
  | pattern | ความหมาย | การจัดการ |
  |---|---|---|
  | `0b10xxxxxx` | continuation byte | ข้าม byte นี้ |
  | `0b110xxxxx` | นำ 2-byte char | ข้ามอีก 1 |
  | `0b1110xxxx` | นำ 3-byte char | ข้ามอีก 2 |
  | `0b11110xxx` | นำ 4-byte char | ข้ามอีก 3 |
- ทำ handler ครบทุกกรณี **เผื่อ port ไปภาษาอื่นในอนาคต** (แม้บางเคสอาจไม่เกิดจริง)
- markup ทั้งหมด (`< > = " '`) เป็น ASCII < 0x80 → ไม่ชนกับ continuation byte

### 5.3 ปัญหา partial multi-byte ตรงขอบ buffer
- ตอนอ่าน chunk byte ดิบ byte ท้ายอาจเป็น "กลางตัวอักษร"
- ใช้ buffer ขนาด **2^n byte** และอ่านแบบระบุ **offset + length**: `is.read(buf, off, len)`
  - เก็บ byte ที่ค้างไว้ แล้วอ่าน chunk ถัดไป **ต่อท้าย** byte ที่ค้าง (offset = จำนวน byte ค้าง) → ตัวอักษรที่ขาดครึ่งประกอบครบ
- **ตัดสินใจตอนอ่านเข้ามา ไม่ใช่ตอนตีความ:**
  - ทันทีที่ได้ chunk ใหม่ ตรวจ byte ท้ายว่าเป็นกลางตัวอักษรไหม
  - ถ้าใช่ → เลื่อน end boundary ถอยกลับมา กันส่วนค้างไว้รอ chunk หน้า
  - loop ตีความวิ่งบนช่วงที่ "ตัวอักษรครบ" เสมอ → **ไม่มี branch เช็ค partial ใน hot loop**
  - ภาระตรวจขอบ = ครั้งเดียวต่อ chunk ไม่ใช่ทุก byte

### 5.4 เครื่องมือที่ใช้
- ใช้ **`java.io.InputStream`** ตรงๆ (ตัวที่อยู่ในคิว)
  - เมธอดหลัก: `int read(byte[] b, int off, int len)`
  - คืนจำนวน byte จริง และ `-1` เมื่อ EOF → คุม loop
- **ไม่ใช้:** `InputStreamReader` (decode char), `BufferedInputStream` (เราจัดการ buffer เอง)
- ทางเลือกอนาคต: `FileChannel` + `ByteBuffer` (direct/memory-map) แต่ตอนนี้คิวถือ `InputStream`

---

## 6. State machine ด้วย bit flags (แทน stack)

### 6.1 แนวคิด
- แทน stack ด้วย **bit flags** — แต่ละ bit = สถานะหนึ่ง รวมเป็นตัวแปร `flags`
- decision table 2 มิติ: **`TOC[flags][buffer[p]]`**
  - `buffer` = ที่เก็บ bytes (คือ `byte[]` ตัวใหญ่ shared buffer ของ planner, ดู 4.2/11.7), `p` = ตำแหน่งที่กำลังอ่าน → **`buffer[p]` คือ "ไบต์ปัจจุบัน 1 ตัว" ที่เรากำลังอ่านอยู่**
  - **แทนที่จะเอาไบต์นี้ไป `if (b=='<') … else if (b=='>') …` เปรียบเทียบเป็นโซ่** เรา **เอาไปชี้ index `TOC[flags][buffer[p]]` หา handler แล้ว `.call()` เลย** — lookup ครั้งเดียว ไร้ branch chain
  - jump ไป handler ถูกต้องในก้าวเดียว ไม่ต้อง push/pop/เช็ค top ของ stack

### 6.2 ชุด bit flags (draft)
| bit | ชื่อ | ความหมาย (=1) |
|---|---|---|
| 0 | `IN_TAG` | อยู่ระหว่าง `<` … `>` ; ถ้า 0 = อยู่ใน text content |
| 1 | `IN_DQUOTE` | อยู่ในค่า attribute `"…"` |
| 2 | `IN_SQUOTE` | อยู่ในค่า attribute `'…'` |
| 3 | `CLOSE_TAG` | tag ปัจจุบันเป็น `</…` |
| 4 | `SPECIAL` | อยู่ใน comment/CDATA/PI (`<!… <?…`) ที่ต้อง skip จนจบ |
| 5 | `SPECIAL_COMMENT` | `SPECIAL` ที่เป็น comment `<!--` (skip ถึง `-->` ไม่ใช่ `>`) |

- 6 bit = 64 แถว (bit 0–5; 6–7 ว่างสงวน) → `TOC[64][256]` = 16384 entry
- จุดต้องตัดสินใจ: DQUOTE/SQUOTE/SPECIAL เกือบ mutually exclusive (bit อิสระ = index เร็วแต่เปลืองแถว), UTF-8 skip จะใช้ counter แยกหรือทำเป็น flag, self-close `/>` เป็นเหตุการณ์ชั่วขณะ (อาจไม่ต้องเป็น bit ถาวร)

### 6.3 ข้อยกเว้น: encoding จาก header
- ตอนเจอ header PI (`<?xml … encoding="…"?>`) แม้อยู่โหมด SPECIAL/skip **ต้องดึงค่า encoding ออกมา** เก็บใน object state (class 2)
- ใช้ decode และ **ส่งต่อให้ผู้ใช้** ตอน output

### 6.4 ตัวอักษรโครงสร้าง XML ที่ต้องพิจารณา
| ตัวอักษร | ความหมาย |
|---|---|
| `<` | เริ่ม tag |
| `>` | จบ tag |
| `/` | ปิด tag (`</`) หรือ self-close (`/>`) |
| `?` | PI / prolog (`<?xml…?>`) |
| `!` | comment / CDATA / DOCTYPE (`<!`) |
| `=` | คั่น attribute กับค่า |
| `"` | ค่า attribute (double quote) |
| `'` | ค่า attribute (single quote) |
| space / `\t` | คั่น tagname กับ attribute |
| `\r` / `\n` | newline / whitespace |
| default | ส่วนของ tagname หรือ text content |
- รวม 4 เคส UTF-8 high-byte (ข้อ 5.2)

### 6.5 การข้ามกิ่งที่ไม่ได้ลงทะเบียน (skip unregistered subtree) — **ตัดสินแล้ว**
- **ปัญหาของวิธีเก่า:** โค้ดเก่าใช้ `childLevel` นับชั้นที่ดิ่งเข้าไปในกิ่งที่ไม่สนใจ แล้วนับถอยกลับเป็นตัวเลข → เปราะบาง ถูกหลอกได้ (เช่น child tag ปิดไม่ครบ นับเพี้ยน)
- **วิธีใหม่ (name-keyed depth counter):**
  - พบแท็กแรกที่อยู่นอกกิ่งที่สนใจ (ไม่มีใน registered child ของ `currNode`) → **เก็บชื่อแท็กนั้น** ไว้ ตั้งตัวนับ = 1
  - ลงลึกกี่ชั้นก็ไม่สน **เว้นแค่ชื่อที่เก็บไว้** — เจอเปิดแท็กชื่อเดียวกันเพิ่ม → ตัวนับ +1 (เจอชื่ออื่น ignore หมด)
  - พบแท็กปิดชื่อตรงกับที่เก็บไว้ → ตัวนับ −1
  - ตัวนับ = 0 → กลับมาบนกิ่งที่สนใจ (เริ่มจับ inner text / กิ่งลงทะเบียนได้อีกครั้ง)
- **ทนทานกว่า:** กิ่งในที่เสีย / ปิดไม่ครบ ไม่กระทบตัวนับเลย (นับสนแค่ชื่อที่เก็บไว้) → จับ sync หลุดยาก
- **ข้อควรระวัง:** ถ้าแท็กนอกที่เก็บไว้เองปิดไม่ครบ จะไม่กลับมา (รับได้ — โครงสร้างเสียระดับนั้นเท่ากับไฟล์เสียอยู่ดี)
- **state ที่เพิ่มใน Class 2 / XmlRunner (แทน `childLevel` เก่า):**
  - `skipName` (byte[]) + `skipNameLen` (int) — ชื่อแท็กที่เก็บไว้ (เทียบ byte ตรงๆ ไม่จอง String)
    - `skipNameLen` ใช้ **`int`** เหมือน length ตัวอื่นในโค้ดbase (PLAN 3.1 offset เป็น int) — ชื่อแท็ก XML แทบไม่เกินร้อยกว่าบyte อยู่แล้ว int เพียงพอสบาย, กันกรณีผิดปกติด้วย
    - `skipName` ใช้ byte[] เล็ก reusable (ชื่อสั้น) ขยายตาม high-water ถ้าเจอชื่อยาวผิดปกติ
  - `skipDepth` (int) — ตัวนับ

### 6.6 รูปแบบ TOC ใหม่: `TOC[flags][byte].call` (แทน `toc1-4` ของ SampleUse เก่า) — **ตัดสินใจแล้ว**
- **เก่า (SampleUse.java):** แยก 4 array `TOC_1..TOC_4` แล้วสลับไปมาตาม state — แต่ละ cell คือ `callback.call(Object[])`:
  - `TOC_1` — พบ `<` แล้ว กำลังจำแนกว่าตามด้วย tag/close(`/`)/PI(`?`)/comment(`!`)/ignored
  - `TOC_2` — อยู่ใน `<tag` (เทียบชื่อ/หา `>` `/` space quote newline)
  - `TOC_3` — อยู่ใน `"…"` (หา `"`/`'`/`>`)
  - `TOC_4` — อยู่ใน `'…'` (หา `"`/`'`/`>`)
  - จุดอ่อน: ต้องสลับ 4 array ตามมือ, แถวละ 256 (raw byte), กว้างเกิน
- **ใหม่:** รวมเป็นตารางเดียว `TOC[flags][byte]` โดย:
  - `flags` = bit flags ของ XmlRunner (6.2: `IN_TAG`/`IN_DQUOTE`/`IN_SQUOTE`/`CLOSE_TAG`/`SPECIAL` + `SPECIAL_COMMENT` bit 5) → **แถว** 6 bit = **64 แถว** (`xmlState & 0x3F`)
  - `byte` = **ไบต์ดิบ 8-bit ที่กำลังอ่าน** จาก `byte[]` ตัวใหญ่ (shared buffer) ที่ตำแหน่ง `p` → **คอลัมน์ = 256** (`buffer[p] & 0xFF`) — ใช้ raw byte ตรงๆ ไม่จำแนกเป็น class (เข้ากับ 6.1 `TOC[flags][buffer[p]]`)
  - แต่ละ cell = **callback** ที่เรียกผ่าน `.call` (รูปแบบเดียวกับเก่า แต่รวมในตารางเดียว, ไร้ branch เลือก TOC)
  - **ขนาด:** `TOC[64][256]` = 16384 cell
- **การแมป old→new:** `TOC_2`≈`IN_TAG`, `TOC_3`≈`IN_DQUOTE`, `TOC_4`≈`IN_SQUOTE`, `TOC_1`≈ก้าวแรกหลัง `<` (set `IN_TAG`/`CLOSE_TAG`/`SPECIAL` แล้ววิ่งต่อ) — ครบใน flag เดียว (รวม `SPECIAL_COMMENT` เป็นแถวที่ 6)
- **ผล:** ไม่ต้องสลับ 4 array, คอลัมน์คือ raw byte 256 (ไม่ต้องจำแนก class แยก), แถว 64 ครอบคลุม flag 6 bit
- **ค้าง:** ต้องเติม cell ตาม (flags,byte) ทีละ cell (parse loop เรียก `planner.TOC[xmlState & 0x3F][buffer[p] & 0xFF].call(runner)`)

---

## 7. Class `XmlPlanner` — การลงทะเบียน path + graph + handler พื้นฐาน

### 7.0 signature การลงทะเบียน — **ตัดสินใจแล้ว**
- `XmlPlanner` รับการลงทะเบียนทั้งหมด หน้าตาเดียวกัน (Object handler + Object token) ต่างกันแค่มี/ไม่มี path:
  1. **`register(path, handler, token)`** — ลงทะเบียน path ปกติ
     - `path` string — เส้นทางที่สนใจ
     - `handler` (Object) — ตัวรับ callback
     - `token` (Object) — ฝากไว้ คืนกลับใน `XmlEvent` ทุกครั้ง (ดู 3.1)
  2. **`registFileBegin(handler, token)`** — ลงทะเบียน callback ตอน **เริ่มไฟล์** (event `FILE_BEGIN`); ไม่มี path
  3. **`registFileEnd(handler, token)`** — ลงทะเบียน callback ตอน **จบบไฟล์** (event `FILE_END`); ไม่มี path
  4. **`registError(handler, token)`** — ลงทะเบียน callback ตอน **ไฟล์เสีย** (event `ERROR`); ไม่มี path
- ทั้ง 4 ตัว: ไม่มี path = ไม่ต่อกราฟ (ไม่มี child ให้ไต่) = ถูก call ที่จุดชีวิตไฟล์ (begin/end/error) ครั้งละรอบ
- token ใน 2/3/4 ใช้แยก state เหมือน `register` ปกติ

### 7.1 กลไก graph (จุดเด่นเดิม)
- เก็บ path ที่ลงทะเบียนเป็น graph/tree ของ Node
- ตอน parse ไต่ tree ทีละ node ตาม tag ที่อ่านเจอ → cost คงที่ต่อ tag ไม่ขึ้นกับจำนวน path ที่ลงทะเบียน
- ตรวจ child: `currNode.child.containsKey(tagname)` แบบ O(1)

### 7.2 โครงสร้าง child ต่อ node
- ธรรมชาติงาน XML ปริมาณมาก: จำนวน field กว้าง (5 ถึงหลายพัน), บางโดเมน nesting ลึก (FpML, OOXML, XBRL)
- แต่ user ลงทะเบียนเฉพาะ path ที่สนใจ → **registered child ต่อ node มักมีน้อย**
- **ข้อสรุปการเลือก:**
  - HashMap: อ่านง่าย, ความเร็วไม่แย่ (build ครั้งเดียว), lookup amortized O(1) — **ยอมรับได้**
  - แต่ lookup เกิดใน hot loop (ทุก tag) ไม่ใช่แค่ตอน construct
  - "ข้อมูลเรียงดีใน memory" ช่วย HashMap ได้ไม่มาก เพราะภายในเป็น array ของ reference (`HashMap.Node`) → chase pointer หลายทอด, cache miss ได้
  - ต้นทุนที่ใหญ่จริง = **การสร้าง String substring + คำนวณ hashCode ต่อ tag** → หลีกเลี่ยงได้ด้วยการ **เทียบ byte ตรงๆ** (tagname เป็น a-z อยู่แล้ว)
  - JDK ไม่มี Map สำหรับ byte key ที่เร็วกว่า (`byte[]` key เทียบตาม reference ไม่ใช่ค่า, `TreeMap` = O(log n), ไม่มี trie ในตัว) → ต้องทำเอง
- **แนวทางที่พอดี (ไม่ over-engineer):**
  - ต่อ node เก็บ registered child ไม่กี่ตัว → **เทียบเชิงเส้น: length ก่อน แล้ว byte ให้ครบ**
  - เป็น trie แบบไม่ลงลึกเกินจำเป็น — ขอแค่หา child ถูกตัว **ไม่จับโหนดที่ชื่อคล้ายกัน**
  - ต้อง match ให้ครบ ไม่ใช่แค่ prefix หรือ hash ตรง (กัน `Name` vs `NameB`, hash ชนกัน)
  - (ทางเลือกเสริม: rolling hash ระหว่างสแกน tagname, byte แรก → dispatch array เล็ก)

### 7.3 namespace prefix
- prefix (`rsm:`, `ram:`) = namespace prefix — เป็นแค่ alias ผูกกับ URI, ตัวมันเองไม่มีความหมายตายตัว
- ในทางปฏิบัติ user ไม่สนใจ, ไฟล์มาตรฐานเดียวกันใช้ prefix เดิมเสมอ
- **ตัดสินใจ: ตัด prefix ทิ้ง เทียบ local name อย่างเดียว** (ของเดิมก็ทำอยู่แล้ว `split(":")` เอาตัวหลังสุด)
- เผื่อ option เทียบ namespace เข้มงวดไว้ทีหลัง ถ้าจำเป็น

---

## 8. Scope exclusions — เพิกเฉยทุกอย่างที่ลิงก์ออกนอกไฟล์
มุ่งเน้นถอดข้อมูล **ภายในไฟล์** เน้นเร็ว + ปริมาณมาก จะ **ไม่ทำ:**
- External DTD / `<!DOCTYPE … SYSTEM "…">`
- การ resolve namespace URI (`xmlns`)
- External entity / `SYSTEM` / `PUBLIC` references (กัน XXE ในตัว)
- XInclude, external schema (XSD/RelaxNG) validation
- DTD entity expansion ที่อ้างไฟล์ภายนอก

**ผลพลอยได้:** ปลอดภัยจาก XXE โดยธรรมชาติ, ไม่มี I/O แฝงระหว่าง parse → เร็วและคาดเดาได้

---

## 8.5 ระบบ log / การจัดการไฟล์เสีย

### 8.5.1 lib ไม่บริหาร log เอง
- lib **ไม่เป็นเจ้าของ** ไฟล์ log / path / rotation และ **ไม่ `System.out.println`** (ของเก่าทำ — ไม่เหมาะกับ lib)
- โค้ดเก่าใน `Import()` เขียน `_log.setMsg(e.toString())` + rethrow → ทำให้ loop หยุด (ไม่เอา)
- แทนที่ด้วย: **แจ้งกลับ caller ผ่าน API/callback** ให้ผู้ใช้ตัดสินใจ log เอง

### 8.5.2 ไม่แจ้งสาเหตุที่เสีย (ตัดสินใจแล้ว)
- **แจ้งแค่ชื่อไฟล์/stream id ที่ถูก reject ก็พอ** — ไม่แจ้งตำแหน่งบรรทัด/สาเหตุ
- เหตุผล:
  - หน้าที่เราคือทำงานให้เสร็จ **เร็วและมากที่สุด**
  - ไฟล์เสียต้องส่งให้ operator ยืนยันแล้วตีกลับอยู่ดี ไม่ว่าเราแจ้งอะไร
  - สาเหตุที่เรา detect อาจไม่ตรงกับสาเหตุจริง (copy ไม่สำเร็จ / unzip ไม่ได้ / เนื้อในถูกแทรกสอด ฯลฯ)
  - ของเก่าที่แจ้งตำแหน่งบรรทัด + สาเหตุ **ทำให้ความเร็วตกลงมาก** (ต้อง build string) → ไม่เอา
- สาเหตุคร่าวๆ เป็น "nice to have" **ได้เฉพาะเมื่อไม่กระทบความเร็ว** ห้ามกลายเป็นภาระแบบเดิม
- หลีกเลี่ยงการสร้าง String อธิบาย error ใน hot path

### 8.5.3 reject ไฟล์เสียโดยเทรดไม่หยุด
- ครอบการ parse แต่ละไฟล์ด้วย **try/catch ภายใน consumer loop** (ไม่ rethrow ขึ้นไปฆ่า loop)
- เมื่อพัง:
  1. แจ้งกลับ caller เพียง **identity ของไฟล์ (ชื่อ/stream id)**
  2. **reset state ให้สะอาด** ก่อนไฟล์ถัดไป (flags/ตำแหน่งกราฟ/encoding/buffer) กันสถานะค้างไปเปื้อนไฟล์ถัดไป
  3. close stream, เลื่อน `rP`, `continue` ไฟล์ถัดไป
- เทรดไม่ตาย ทำงานต่อเนื่อง; exception เกิดเฉพาะไฟล์เสีย (ไม่บ่อย) → ต้นทุนยอมรับได้
- **callback (event ปกติ `TAG/ATTR/INNER/END`) คืน `boolean`**: คืน `false` = ผู้ใช้ตัดสินว่าไฟล์นี้ **fail ระดับโครงสร้างข้อมูล → ข้ามไปไฟล์ถัดไปทันที** (soft reject, ไม่ต้องโยน exception) — ทำเหมือนไฟล์เสีย: reset state + continue
- fail ระดับ syntax/parser (โครงสร้าง XML เสีย) ก็ข้ามไฟล์ถัดไปเช่นกัน (try/catch ข้างบน) → ทั้งสองทางไปจบที่ **ข้ามไฟล์** เหมือนกัน
- `FILE_BEGIN/FILE_END/ERROR` — return ไม่มีผลต่อการ abort (ERROR ไฟล์ตายแล้ว, FILE_* เป็นจุดแจ้งชีวิตไฟล์)
- แยกระดับ: lib reject เฉพาะ **error ระดับ parse (โครงสร้างเสีย)**; business validation (E0xxx เดิม) เป็นของ handler ผู้ใช้ (คืน `false` เพื่อข้าม)

---

## 9. สรุปโครงสร้าง 3 คลาส (ภาพรวม)
1. **`callback` (interface) + `XmlEvent`** — hook สำหรับ user รับ event (`FILE_BEGIN/TAG/ATTR/INNER/END/FILE_END/ERROR`); ส่ง `XmlEvent` เดียว reuse zero-copy `{filename, charset, event, token, attrName, value}` (ดู 3.1); text อ่าน 3 ระดับ raw region / `CharSequence` view / `asString()` (ดู 3.2); มี `reset()` (3.3); token ฝากตอน register คืนกลับทุก call
2. **State + Queue (class)** — state ต่อการ parse (flags, ตำแหน่งกราฟ, encoding) + ring buffer SPSC ของ **region descriptor `{start,end,filename}`** (2^n, mask, object reuse) + **linearize buffer เชิงเส้นต่อเทรด** (ขยายได้, reuse, offset+length zero-copy — ดู 11.1)
3. **`XmlPlanner` (Registry + Handlers)** — graph/trie ของ path (`register(path,handler,token)`) + ลงทะเบียนชีวิตไฟล์ (`registFileBegin`/`registFileEnd`/`registError` — handler+token ไม่มี path, ดู 7.0), ตัวจัดการ event พื้นฐานของ XML (byte reading, TOC[flags][byte], UTF-8 skip), เทียบ child ด้วย byte (length+content), ตัด prefix

---

## 10. งานที่ "ยังไม่ทำ" (ไว้เฟสถัดไป)
- ตัวสร้างเทรด + ตัวขับ/แจกคิวงาน (consumer, threading)
- handler ถอดข้อมูลตาม callback (ส่วนของ user)
- (พิจารณาภายหลัง) NIO `FileChannel`/`ByteBuffer`, option namespace เข้มงวด

---

## 11. ประเด็นค้าง / ต้องพิจารณาเพิ่ม (จากการทบทวน)
รายการนี้เป็นสิ่งที่ยังไม่ได้ตัดสินใจ หรือที่ควรระวังตอนลงมือ:

1. **Token คร่อม chunk boundary — ✅ ตัดสินใจแล้ว: linearize ต่อไฟล์ (แนว ก)**
   - แต่ละ entry ถูก inflate/อ่านเข้า **buffer เชิงเส้นตัวเดียวต่อเทรด ที่ขยายได้ + reuse ข้ามไฟล์** (โตเท่า high-water mark)
   - ผล: **ไม่มี wrap, ไม่ต้อง scratch buffer, offset+length zero-copy ใช้ได้เสมอ** ทุก callback
   - จัดการปัญหา partial multi-byte (5.3) และ inner text ยาว (เคส book/บทความ) ไปพร้อมกัน
   - เหตุผลที่ไม่ใช้ ring เป็น parse-buffer: ค่าที่ยาวกว่า buffer จะถูกเขียนทับส่วนต้นก่อนถึง end tag → ข้อมูลถูกทำลาย
   - ต้นทุน: ถือทั้งไฟล์ใน memory ต่อเทรด — ไฟล์ invoice เล็กระดับ KB ยอมรับได้; book-XML โตครั้งเดียว
   - **หมายเหตุ:** ring ยังใช้กับ **คิวงาน** (4.2) เท่านั้น ไม่ใช่ parse-buffer

2. **Lazy decode — เบสเคสการทำงาน (ตัดสินแล้ว)** — parser **ไม่ decode เลย** ส่งแค่ `{offset, size}` (byte, zero-copy) สำหรับ **ชื่อแท็ก / ชื่อ attr / ค่า attr / inner text** ให้ **handler ของ user ประกอบเป็น String เอง** ด้วย `charset` ที่ parser อ่านจากหัวไฟล์ (`<?xml … encoding=?>` ตาม 6.3) แล้วใส่ใน `XmlEvent` ให้
   - นี่คือที่มาของ lazy decode: การแปลง byte→String เกิด **เฉพาะตอน user เรียก `asString()` ใน handler** (หรือใช้ `CharSequence` view / raw region ตาม 3.2) ไม่ใช่ใน hot loop ของ parser
   - ได้เปรียบความเร็ว: ข้าม element ที่ไม่ลงทะเบียนทั้งหมดไปโดยไม่เสียเวลา decode; user ลงทะเบียนไม่กี่ field → decode น้อยมาก
   - `buffer` คือ linearize ต่อเทรด เอาไป bind ครั้งเดียว (PLAN 3.2 / 11.1) → handler รู้ buffer อยู่แล้ว ต่อ event จึงส่งแค่ `offset+size` (ไม่ต้องส่ง buffer มาทุก event) — เข้ากับ `XmlEvent` ที่มี `buffer`+`*Off`+`*Len` อยู่แล้ว
   - entity/CDATA (11.3) ส่ง raw byte ตามเดียวกัน user ตัดสินแปลเองหรือไม่
   - เข้ากับ 3.2 (raw / CharSequence view / asString ใช้ charset เดียว) และ 6.3 (ดึง encoding จาก header เก็บใน state)

3. **Entity references ในไฟล์** — `&amp; &lt; &gt; &quot; &apos;` และ numeric `&#..;` → **ตัดสินใจแล้ว: ไม่ decode ส่ง raw** ข้อมูลถือเป็น text ล้วน `asString()` ทำแค่ charset decode ของ raw bytes ไม่แปล entity (เข้ากับหลัก "ไม่แปลความหมาย")

4. **CDATA / comment / DOCTYPE — skip ทั้งหมด** — **ตัดสินใจแล้ว: 2 โหมด skip**
   - **`<!--`** → skip จนถึง **`-->`** (multi-byte terminator: ต้องมี sub-state จับ `--` แล้ว `>`)
   - **`<!` อื่นๆ** (CDATA `<![CDATA[`, DOCTYPE ฯลฯ) → skip จนถึง **`>`** ตัวแรก
   - ไม่แยกแยะเนื้อใน, ไม่แปลความหมาย → เนื้อใน CDATA **ไม่ถูกถอด** — ตรงกับวัตถุประสงค์ "ถอดเฉพาะที่ลงทะเบียน"
   - state machine: `<!` → เข้าโหมด SPECIAL, แยกว่าตามด้วย `--` หรือไม่ เพื่อเลือก terminator (ดูข้อ 6)

5. **การเก็บ inner text ข้าม child tag** — **ตัดสินใจแล้ว: Streaming chunks, ไม่ concat**
   - Parser ส่ง event `INNER` ทุกครั้งที่เจอ text node (raw offset+length บน linear buffer)
   - User handler รับ chunks หลายครั้งต่อ element → concat เองถ้าต้องการ (StringBuilder, offset array, หรือ `asString()` ต่อ chunk)
   - ไม่มี `innerBeginPoint`/`innerText` state ใน parser → ลด complexity, GC, allocation
   - เข้ากับ lazy decode (11.2): user decode เฉพาะ chunk ที่สนใจ

6. **Registry เป็น read-only หลัง build (thread-safety)** — class 3 build ครั้งเดียวแล้วแชร์อ่านข้ามเทรด → ต้องบังคับ **register ให้เสร็จก่อนเริ่ม parse** (แยกเฟส register / run) ห้าม register ระหว่าง parse

7. **Topology คิว vs parser — ✅ ตัดสินใจแนวทางแล้ว: shared read-only buffer + region descriptor**
   - **แหล่งข้อมูลแชร์:** โหลด ZIP ทั้งไฟล์เข้า buffer เดียว (read-only) หรือ **memory-mapped file** (`FileChannel.map` → `MappedByteBuffer`) สำหรับ ZIP ใหญ่
     - memory-map = OS จัดการ page เอง, ไม่ติดลิมิต `byte[]` ~2GB (ใช้ long offset), ไม่โหลดขึ้น heap ก้อนใหญ่, หลายเทรดอ่านคนละ region ได้
     - **เจ้าของ shared buffer = `XmlPlanner`** (ออบเจ็กต์เดียว แชร์อ่านข้ามเทรด เหมือนกราฟ/tree — ไม่ใช่ per-runner): runner แต่ละตัวถือแค่ `linear` ต่อเทรด (เป้าหมาย inflate) + state ของตัวเอง, **ไม่ถือ shared buffer ใหญ่**; `begin/end` ในคิวคือ offset เข้าหน้า shared buffer ของ planner → อ่านร่วมกันแบบ read-only **ไม่ต้อง lock**
   - **producer:** อ่าน central directory ครั้งเดียว → แจก entry เป็น region descriptor `{start,end,filename}` ลงคิว
   - **parser thread (N ตัว):** แต่ละตัวหยิบ descriptor, **inflate เข้า linearize buffer ของตัวเอง**, parse, ถือ state ของตัวเอง — แชร์เฉพาะ buffer ที่อ่านอย่างเดียว **ไม่ต้อง lock**
   - SPSC: 1 producer + 1 consumer ต่อคิว → N parser = N คิว (producer เดียว round-robin เติม หรือหลาย producer)
   - **เงื่อนไข/ข้อจำกัดการแยกหลายเทรด:**
     - ได้เมื่อ ZIP เป็น **random access (`ZipFile`/mmap)**; ถ้าเป็น `ZipInputStream` (stream ล้วน) แยกไม่ได้ เพราะ inflate ตีบที่เทรดเดียว
     - **ต้องรองรับ ZIP64** (เกิน 65,535 entry / >4GB) — เคส 10M ไฟล์
     - สเกลตาม **จำนวน CPU core** (inflate+parse เป็น CPU-bound = จุดที่ multi-thread ช่วยจริง)
     - central directory ของ 10M entry ใหญ่ (หลักร้อย MB) มีต้นทุนอ่าน/ถือ
     - ดิสก์: NVMe/SSD สบาย, HDD จะ seek ชนกัน (แต่เป็นไฟล์เดียว)
   - **inflater เป็น per parser-thread** (ไม่ใช่ per queue-slot)

8. **EOF / flush ปิดท้าย** — ของเก่าเติม `<` เป็น sentinel เพื่อ flush record สุดท้าย เวอร์ชัน byte ต้องมีกลไก terminator/flush เทียบเท่า ตอนจบ stream

9. **Attribute surfacing** — ของเก่า split ด้วย `~` และ `=` แล้วส่งเป็น event `$attr` → กำหนดว่าจะส่ง attribute เป็น raw byte หรือ decoded และรูปแบบใด

10. **License / package / ชื่อ API** — เป็น open source ต้องเลือก license และตั้งชื่อ package/คลาสสาธารณะ (ไว้ทีหลัง)
