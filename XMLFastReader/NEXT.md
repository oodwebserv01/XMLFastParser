# Next Session — จุดเริ่มต้น

> อ่านคู่กับ `PLAN.md` เสมอ

## เริ่มต้นคราวหน้า
```
อ่าน PLAN.md แล้วเริ่ม Phase 1: Entity reference policy (pass-through raw)
```

## Phase 1: Parser Completeness (Critical) — เริ่มที่นี่
1. ~~**CDATA handling** — `XmlRunner.java` parse loop ต้อง detect `<![CDATA[` และ skip ถึง `]]>`~~ ✅ **Done**
2. **Entity references** — ตัดสินใจ: pass-through raw (PLAN 11.3) → document behavior
3. **Skip unregistered branches** — inner text ของ branch ที่ skip ต้องไม่ leak
4. **EOF / synthetic END** — flush stack, fire `FILE_END` event
5. **XML Declaration → charset** — `onPiEnd()` parse encoding → set `XmlEvent.charset`
6. ~~**HTML Comment handling**~~ ✅ **Done**
7. ~~**DOCTYPE handling**~~ ✅ **Done**

## ไฟล์หลักที่ต้องแก้
- `XmlRunner.java` — parse loop, state machine
- `XmlPlanner.java` — TOC population (เพิ่ม CDATA state)
- `XmlEvent.java` — เพิ่ม field `errorMessage` สำหรับ ERROR event (ถ้าตัดสินใจ)

## ทดสอบด้วย
- `test/sample/InvImportSuperXML.java` — register paths, parse ZIP
- เขียน unit test ใหม่สำหรับ CDATA, entity, EOF cases