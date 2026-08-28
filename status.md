---
name: xml2txt-status
metadata:
  type: project
---

ข้อห้าม / สถานะ / ปัญหา (อ่านครั้งต่อไปแน่):

1. ข้อห้าม (CLAUDE.md + GOAL.md):
   - ห้ามแก้ xmlFastParser/
   - ห้ามแก้ GOAL.md
   - คอมไพล์แยก /compiled ไม่ใช่ source folder
   - สั้นที่สุด ภาษาไทยเป็นหลัก

2. สถานะงาน:
   - CharParser = FSM + Tree (open/close/stack/TreeNode) — regex ลบแล้ว
   - parse_bp_fixed = file: group (Invoids/Items) — base tag
   - main = split file_key → 2 ไฟล์ (Invoids + Items)
   - output = quoted CSV + rename _pending
   - register/TreeGraph/Holder/ClosingJob = ครบโครงสร้าง
   - ไม่มี thread / pipeline / jumper ตามคำสั่ง

3. ปัญหาตอนนี้:
   - traverse segment-match สุดท้ายยังไม่เป๊ะ → PY ได้ค่า (ER3-2560/7/206.52/1) แต่ไม่ครบ 58 คอลัม
   - สาเหตุ: bp.paths ต้อง walk ตามลำดับ segment → tree → leaf เพื่อได้ค่าครบ
   - close-tag nested pop แก้แล้ว (stack[-1] ก่อน search)
   - direct CharParser res = 33 tags (ID/NAME/TYPECODE/etc.) → ขาดบาง nested path-order

4. ผลเทียบ 1 xml (Lab/datas/test):
   - JAVA gold: Invoids 3 rows + Items 3 rows (ครบ)
   - PY: Invoids 1 row / Items 1 row; xml_name ถูก; quoted CSV; 2 ไฟล์ไม่ผสม
   - คู่นี้ = core ครบ; ขาดแค่ path-order alignment สุดท้าย

5. ไฟล์สำคัญ:
   - python/XML2TXT.py (charparser + traverse + bp + closing)
   - Lab/BluPrint.bp
   - Lab/compare_java/ / compare_py/ (ผลล่าสุด)
