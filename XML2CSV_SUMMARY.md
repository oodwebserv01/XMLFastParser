# XML2CSV — Discussion Summary (2026-07-27)

> สรุปจากการคุยกันวันนี้ — เก็บไว้อ้างอิงครั้งต่อไป

---

## 🎯 Project Goal

สร้าง **XML2CSV** — เครื่องมือแปลง XML จำนวนมาก (ZIP/XML) → Text/CSV files
- ใช้ **XMLFastReader core** ที่มีอยู่แล้ว
- User-defined mapping config 
- Output: data files + success/fail lists

---

## 📁 Project Structure

```
/home/wise0136/myProject/XMLFastReader/
├── XMLFastReader/          # Core library (existing)
│   └── XMLFastReader/      # Java sources
├── test/sample/            # Legacy examples (InvImportFolder, InvImportSuperXML)
└── XML2CSV/                # NEW - to be created
    ├── src/main/java/xml2csv/
    ├── src/main/resources/examples/
    ├── pom.xml
    └── README.md
```

---

## 🔧 Core Library Status (XMLFastReader)

| Class | Status | Notes |
|-------|--------|-------|
| `XmlFastReader` | ✅ Done | Facade API |
| `XmlPlanner` | ✅ Done | Trie, TOC[128][256], shared buffer |
| `XmlEvent` | ✅ Done | 7 events, 3-level access, zero-copy |
| `XmlCallback` | ✅ Done | Interface |
| `XmlDispatcher` | ✅ Done | SPSC ring buffer |
| `XmlWorker` | ✅ Done | Per-thread worker |
| `XmlRunner` | ✅ Done | Parse loop, state machine |
| Producers | ✅ Done | File, ZIP, Stream, Bytes, URL |
| `ParseHandle` | ✅ Done | Progress, stats, error handling |

**Critical gaps in core** (Phase 1):
- CDATA handling : ไม่สนใจข้อมูลกลุ่มนี้ข้ามทั้งหมด.
- Entity references (pass-through raw) : เราจะแปลง charsetกันในขั้นตอนนี้ ก่อนเขียนลงไฟล์ ใช้ charset จาก XML Declaration .
- Skip unregistered branches (inner text leak) : เราสนใจเฉพาะข้อมุลที่ลงทะเบียนไว้
- EOF / synthetic END events

---

## 📋 XML2CSV Scope (Agreed)

### 1. **CLI interface design**
   ```bash
   xml2csv --con mapping.x2c --in folder --out folder --threads N 
- in folder can contain `.xml` and `.zip` or file name of .xml or .zip
- out folder create if not exists
- --threads ถ้าไม่ได้ระบุ ให้มีค่าเป็น 1

### 2. Config Format 

#### โครงสร้างข้อมูล: `.x2c` (Indentation-based)
```ตัวอย่าง ข้อมูลใน .x2c

success:list_sucess.txt
failed:list_fail.txt

entity.txt
 /invoidRoot/saler/salerID$
 /invoidRoot/saler@busType

product.txt
 body:/invoidRoot/items/item
 /invoidRoot/items/item@itemID
 /invoidRoot/items/item/name$
 /invoidRoot/items/item/qty$
 /invoidRoot/items/item/price$

````คำอธิบาย 

success:list_sucess.txt <== success: นำหน้า มีได้ครั้งเดียวต่อ1ไฟล์ .x2c หมายถึง รายชื่อไฟล์ที่แปลงสำเร็จจะถูกบันทึกไว้ใน list_sucess_YYYYMMDDhhmmss_xx.txt โดย YYYYMMDDhhmmss คือ ปีเดิอนวันชั่วโมงนาทีวินาทีที่สร้างไฟล์ ส่วน xx เริ่มต้นด้วย 01 หากมีไฟล์ชื่อดังกล่าวอยุ่แล้วจะเปลี่ยนเป็น 02 และ 03 ไปเรื่อยๆตามลำดับ
failed:list_fail.txt <== failed: นำหน้า มีได้ครั้งเดียวต่อ1ไฟล์ .x2c หมายถึง รายชื่อไฟล์ที่แปลงไม่สำเร็จจะถูกบันทึกไว้ใน list_fail_YYYYMMDDhhmmss_xx.txt

entity.txt <== ผลลัพธ์เก็บในไฟล์ชื่อ entity_YYYYMMDDhhmmss_xx.txt  
 /invoidRoot/saler/salerID$ <== ลงทะเบียนพาท /invoidRoot/saler/salerID ใช้ $ ตามหลังหมายถึง อ่านเอา innerText
 /invoidRoot/saler@busType <== ลงทะเบียนพาท /invoidRoot/saler ใช้ @ ตามหลังหมายถึง อ่านเอา attribute ชื่อ busType 

product.txt <== ผลลัพธ์เก็บในไฟล์ชื่อ product_YYYYMMDDhhmmss_xx.txt 
 body:/invoidRoot/items/item <== body: นำหน้า มีได้ครั้งเดียวต่อ1ไฟล์ผลลัพธ์ หมายถึง พาท /invoidRoot/items/item นี้คือ 1 row ถ้าใน .xml มี แท็กนี้พ่าทนี้ หลายครั้ง แต่ละครั้งคือ 1 บรรทัดข้อมูล
 /invoidRoot/items/item@itemID <== ลงทะเบียนพาท /invoidRoot/items/item ใช้ @ ตามหลังหมายถึง อ่านเอา attribute ชื่อ itemID
 /invoidRoot/items/item/name$ <== ลงทะเบียนพาท /invoidRoot/items/item/name ใช้ $ ตามหลังหมายถึง อ่านเอา innerText
 /invoidRoot/items/item/qty$ <== ลงทะเบียนพาท /invoidRoot/items/item/qty ใช้ $ ตามหลังหมายถึง อ่านเอา innerText
 /invoidRoot/items/item/price$ <== ลงทะเบียนพาท /invoidRoot/items/item/price ใช้ $ ตามหลังหมายถึง อ่านเอา innerText

```ตัวอย่าง รูปแบบอย่างย่อ ข้อมูลใน .x2c 

````รูปแบบเต็ม 
success:list_sucess.txt
failed:list_fail.txt

entity.txt
 /invoidRoot/saler/salerID$
 /invoidRoot/saler@busType

product.txt
 body:/invoidRoot/items/item
 /invoidRoot/items/item@itemID
 /invoidRoot/items/item/name$
 /invoidRoot/items/item/qty$
 /invoidRoot/items/item/price$

````รูปแบบย่อ 
success:list_sucess.txt
failed:list_fail.txt

entity.txt
 /invoidRoot
  //saler
   ///salerID$
  //saler@busType

product.txt
 body:/invoidRoot/items/item
 /invoidRoot
  //items
   ///item@itemID
    ////name$
    ////qty$
    ////price$

````คำอธิบาย
การใช้ "/" หมายถึงเหมือนพาทข้างบน อันที่ มี จำนวนของ / น้อยกว่า 1 ขั้น เช่น    //saler@busType ในตัวอย่างข้างบน 
มีค่าเท่ากับ /invoidRoot/saler@busType เพราะ บรรทัดที่มี / เดียวก่น //saler@busType คือ /invoidRoot

````รูปแบบผสม
success:list_sucess.txt
failed:list_fail.txt

entity.txt
 /invoidRoot/saler/salerID$
  //saler@busType

product.txt
 body:/invoidRoot/items/item
  ///@itemID
  ////name$
  ////qty$
  ////price$

````คำอธิบาย
  //saler@busType มี 2 / เป็นพาทชั้นที่2 จึงทำการใช้งานพาทชั้นแรกของการลงทะเบียน่อนหน้านี้คือ "/invoidRoot" ซึ่งมาจาก /invoidRoot/saler/salerID$ 

  ///@itemID  มี 3 / เป็นพาทชั้นที่3 จึงทำการใช้งานพาทสองชั้นแรกของการลงทะเบียน่อนหน้านี้คือ "/invoidRoot/items/item" ซึ่งมาจาก body:/invoidRoot/items/item 

---


## 🔄 Pending Decisions (Next Sessions)

2. **Handler generation strategy**
   - Reflection-based dynamic handler
   - Code generation (Java source)
   - Interpreter pattern

3. **Error handling detail**
   - What info in failed:file(.csv/.txt)? : ชื่อไฟล์ .xml ที่ error ส่วนสาเหตุ ถ้าบอกได้ก็ไนซ์ แต่ถ้าทำให้ความเร็วลดลงก็ไม่ต้องบอก
   - Partial row handling

4. **Performance tuning**
   - Buffer sizes
   - Thread pool sizing
   - Memory mapping for large ZIPs

5. **Build system**
   - Maven vs Gradle
   - Shade/fat jar
   - Native image (GraalVM)?

6. **Testing strategy**
   - Unit tests for parser
   - Integration tests with sample data
   - Benchmark vs legacy

7. **Documentation**
   - README.md
   - .x2c syntax guide
   
---

## 📝 Legacy Reference (test/sample)

### InvImportSuperXML — 60+ registered paths
- **Header**: Seller/Buyer info, addresses, contacts, document metadata
- **Settlement**: Amounts, VAT, taxes, allowances, charges
- **Line Items**: Product, quantity, price, discounts, line totals
- **Logic**: Complex (schemeID parsing, VAT type logic, validation)

---

## 🎯 Critical Design Decision: Root Tag Handling

**Problem in legacy:** `InvImportSuperXML` hardcodes root tag detection (`_CrossIndustryInvoice`) and registers paths from root. Users **refuse to specify root tag name** in config — root tag can be anything.

**Solution for XML2CSV:** Registration graph starts from **first-level child elements** (first branch), NOT from root.

### Config implication:
```x2c
# User writes paths starting from first child, e.g.:
/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/...
```
The parser **ignores the root element entirely** — it finds the first child of root and treats that as the registration root.

### Core library impact (XmlPlanner/XmlDispatcher):
- Trie/TOC building: skip root level, start from depth 1
- Dispatcher: when `START_ELEMENT` at depth 1 → treat as registration entry point
- No need to match root tag name in config or code

> **Next session:** Implement this in XmlPlanner registration logic + .x2c parser

### InvImportFolder — CLI entry
- Args: source, input-folder, output-folder, log-file, entity-file, product-file
- Processes ZIP/XML recursively
- Writes `.txt` + `.ERR`/`.wrn` + log

---

## 🚀 Next Steps (Priority Order)

1. **Create XML2CSV module structure**
2. **Implement .x2c parser** (recursive descent, ~100 lines)
3. **Config classes**
4. **MultiRowHandler / SingleRowHandler** (XmlCallback implementations)
5. **Dispatcher integration** (route events to handlers)
6. **CSV Writers + success/failed lists**
7. **CLI wiring**
8. **Test with InvImportSuperXML mapping**

---

## 💡 Key Insights

- **XMLFastReader core is solid** — just needs Phase 1 fixes
- **Config must be explicit** — no implicit ancestor rules
- **Output order matters** — user controls column order
- **Multi-row = body:path** — clear semantics
- **Dual format** — human writes .x2c, CI uses JSON
- **No validation** — extract only, downstream handles quality

---

> **Next session start:** "อ่าน SUMMARY.md แล้วเริ่มสร้าง XML2CSV module + .x2c parser"