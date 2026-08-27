---
name: xml2txt-py-goal
description: สเป็ค XML2TXT.py เวอร์ชัน Python (ไม่ใช้ JVM, single-thread string-FSM)
metadata:
  type: project
  version: python-only
---

# GOAL_FOR_XML2TXT_PY_VERSION.md

## เป้าหมาย
สร้าง `python/XML2TXT.py` (และ inline FSM) ที่ทำงานได้โดยไม่ต้องมี `java/xmlFastParser.jar` / JVM
- ใช้ `str` ระดับ char (ไม่ byte[])
- เปรียบเทียบ tag โดยตรง (ไม่ hash)
- Single-thread sequential (ไม่ multi-thread pipeline)
- รองรับ `.xml` + `.zip` (ที่มี `.xml` ข้างใน) ในโฟลเดอร์ต้นทาง (ไม่รวม subfolder)
- อ่าน `.bp` ตามสเป็คเดิม (entity, paths)
- Output: `[entityName]_pending.txt` → rename `[entityName]_YYYYMMDDHHmmss.txt`
- Archive/Compiled แยก (`/compiled` สำหรับ jar เก่าถ้าต้อง แต่ไม่ใช้ต่อ)

## กฎ
- ห้ามแก้ `/java/`
- **ไม่ต้อง compile / ไม่ใช้ `compiled/` / ไม่ใช้ jar**
- `python/XML2TXT.py` เป็นที่เดียว (ไม่ต้อง `lib/` เพิ่ม)
- **Stdlib only** (ไม่ import นอก `os`, `sys`, `re`, `zipfile`, `datetime`, `argparse`)
- **Tool พร้อมใช้งาน برای operator ทั่วไป** (run `python3 python/XML2TXT.py ...` ตรง)
- **Drop prefix** (ไม่เก็บ/ไม่ใช้ prefix จาก .bp/tag)
- **UpperCase tagName** (เปลี่ยน tag เป็น UPPER — หรือเปรียบเทียบแบบ upper)

## สเป็คฟีเจอร์
1. สืบค้นไฟล์: `.xml` และ `.zip` ใน `-s`; ไม่ลง subfolder
2. `.bp`: `file:Entity`, `entity:...`, `- /path/tag`
3. FSM: `S_TAG` → `S_ATTR` → `S_VALUE` → `S_CLOSE`
4. Predictive jump: ข้ามไป tag เป้าหมายตาม paths ใน `.bp`
5. .txt write: `path\tvalue1|value2...`; `_pending` → rename ตอน `ClosingJob`
6. พารามิเตอร์: `-p`, `-s`, `-d`, `-t` (t=1 fixed หรือ ignore)

## ไม่ใช้
- `xmlFastParser.jar` / JVM
- Multi-thread / pipeline slot
- Byte-level FNV-1a hash
- `zipfile` stream แบบ Java; ใช้ `zipfile` Python แบบเต็ม
