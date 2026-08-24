วัตถุประสงค์
    - XML2TXT.java เป็นโปรแกรม เขียนด้วย java เขียนขึ้นเพื่อเป็นตัวอย่างการใช้งาน xmlBluePrint ใน แพ็กเกจ xmlFastReader
    - ทำหน้าที่ หา .xml และ .zip(ที่มี .xml อยู่ข้างใน) ในโฟล์เดอร์ที่ระบุ ไม่รวมในโฟล์เดอร์ย่อยภายใน 
    - แปลง ข้อมูล xml ที่หาพบ ไปเป็น .txt 

พารามิเตอร์ 
    - "-p Path/File.bp " ระบุด้วย -p ใช้ระบุ ไฟล์สกุล .bp ภายในระบุ paths ของ xml ที่ต้องการเก็บข้อมูลใส่ .txt
    - "-s path " ระบุโฟล์เดอร์ต้นทาง ให้ค้นหา .xml ถ้าไม่ระบุ จะหาที่โฟลฺ์เดอร์ปัจจุบัน
    - "-d path " ระบุโฟล์เดอร์ปลายทาง ถ้าไม่ระบุ จะเขียนที่โฟล์เดอร์ปัจจุบัน
        -- [entityName]_YYYYMMDDHHmmss.txt ข้อมูลที่ต้องการ สร้างเป็น [entityName]_pending เขียนเสร็จแล้ว เปลี่ยน"pending"ด้วยวันเวลาปัจจุบัน 
    - "-t number " ระบุจำนวนเทรด ถ้าไม่ระบุ ใช้ค่าเริ่มต้นของ xmlBluePrint (1) 

โครงสร้างของ .bp
    ตัวอย่าง 1

        file:Invoids
        entity:/Transaction
        - /Transaction/Invoid@SN
        - /Transaction/Invoid/Saler/PersonID#
        - /Transaction/Invoid/Buyer/PersonID#
        - /Transaction/Invoid/Items@Count
        - /Transaction/Invoid/Summary#

        file:Items
        entity:/Transaction/Invoid/Items/Item
        - /Transaction/Invoid/Items/Item@ID
        - /Transaction/Invoid/Items/Item/Name#
        - /Transaction/Invoid/Items/Item/Quantity#
        - /Transaction/Invoid/Items/Item/UnitPrice#
        - /Transaction/Invoid/Items/Item/TotalPrice#


    อธิบายตัวอย่าง 1

        ในตัวอย่างนี้ 1 ไฟล์ .xml จะสร้าง .txt สองแบบ คือ Invoids_ThreadNo_YYYYMMDDHHmmss.txt และ Items_[ThreadNo]_YYYYMMDDHHmmss.txt

        เขียนข้อมูลออกที่ไฟล์ Invoids_[ThreadNo]_YYYYMMDDHHmmss.txt ==> file:Invoids
        ทุกๆกิ่งที่เลเวล1 ที่ชื่อ Transaction ตามพาทที่ระบุ คือ 1 row ==>   entity:/Transaction
            - หาก ไม่มีการระบุ entity: นั่นหมายถึง รูท คือ entity

        column ที่ 1 เก็บ relativePath/xmlFileName.xml (บังคับ)

        column ที่ 2 ระบุด้วย @ เก็บ value ของ attribute ชื่อ SN ==>    - /Transaction/Invoid@SN
        column ที่ 3 ระบุด้วย # เก็บ innerText ของ PersonID ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Saler/PersonID#
        column ที่ 4 ระบุด้วย # เก็บ innerText ของ PersonID ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Buyer/PersonID#
        column ที่ 5 ระบุด้วย @ เก็บ value ของ attribute ชื่อ Count ==>   - /Transaction/Invoid/Items@Count
        column ที่ 6 ระบุด้วย # เก็บ innerText ของ Summary ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Summary#


        เขียนข้อมูลออกที่ไฟล์ Items_ThreadNo_YYYYMMDDHHmmss.txt ==> file:Items
        ทุกๆกิ่งที่เลเวล4 ที่ชื่อ Item ตามพาทที่ระบุ คือ 1 row ==>   entity:/Transaction/Invoid/Items/Item

        column ที่ 1 เก็บ relativePath/xmlFileName.xml (บังคับ)
                
        column ที่ 2 ระบุด้วย @ เก็บ value ของ attribute ชื่อ ID ==>    - /Transaction/Invoid/Items/Item@ID
        column ที่ 3 ระบุด้วย # เก็บ innerText ของ Name ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Items/Item/Name#
        column ที่ 4 ระบุด้วย # เก็บ innerText ของ Quantity ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Items/Item/Quantity#
        column ที่ 5 ระบุด้วย # เก็บ innerText ของ UnitPrice ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Items/Item/UnitPrice#
        column ที่ 6 ระบุด้วย # เก็บ innerText ของ TotalPrice ตามพาทที่ระบุ ==>   - /Transaction/Invoid/Items/Item/TotalPrice#    

สถาปัตยกรรม 

    การทำงานจะแบ่งออกเป็น 3 เฟส คือ 
        - เตรียมตัว หรือ "BootUp" สร้างชิ้นส่วนของตัวเองขึ้นมาประกอบให้ครบ
        - พาร์ดาต้า หรือ "MainLoop" ทำหน้าที่ิ่านข้อมูลจากไฟล์ xml และเขียน entityFile ทั้งหลาย
        - ชัดดาวน์ หรือ "ClosingJob" คืนทรัพยากรณ์และเปลี่ยนชื่อ (ทุกไฟล์)[entityName]_pending.txt เป็น [entityName]_YYYYMMDDHHmmss.txt
    
    ภาพรวม

        // เฟส พาร์ดาต้า
        void MainLoop() {
            // เฟสย่อย เติมไปป์ไลน์ด้วย โทเคนที่สร้างเตรียมไว้
            ลูปเมื่อ allXmlToken ยังไช้ไม่หมด และ sourceHandler.getXml ให้ xmlPackage มา
                เอา xmlToken<ว่าง> มาจาก allXmlToken 
                บรรจุ xmlToken<ว่าง> ด้วย xmlPackage จาก sourceHandler --> xmlToken<xmlPackage> 
                ส่ง xmlToken<xmlPackage> เข้าคิวงานของ xmlFastParser.pushJob
            
            // เฟสย่อย รียูสโทเคนเก่า ด้วยโทเค่นที่คืนมาจากพาร์เซอร์ 
            ลูปเมื่อ sourceHandler.getXml ให้ xmlPackage(ใหม่) มา
                รอ รับ xmlToken<doneXmlPackage> มาจาก xmlFastParser.pullJob หลับครั้งละ 0.1 ms ระหว่างรอ
                ส่ง doneXmlPackage ให้ sourceHandler.closeXml 
                บรรจุ xmlToken<doneXmlPackage> ด้วย xmlPackage(ใหม่) จาก sourceHandler --> xmlToken<xmlPackage>
                ส่ง xmlToken<xmlPackage> เข้าคิวงานของ xmlFastParser.pushJob

            // เฟสย่อย เคลียร์ไปปไลน์
            ลูปเมือ จำนวนที่PULL < จำนวนที่PUSH
                รอ รับ xmlToken<doneXmlPackage> มาจาก xmlFastParser.pullJob หลับครั้งละ 0.1 ms ระหว่างรอ
                ส่ง doneXmlPackage ให้ sourceHandler.closeXml 
        }

        // เฟส เตรียมตัว
        void BootUp () {
            // tunning parser
            xmlFastParser.

            // Parse CLI
            ถ้าพบ "-p" เปลี่ยนค่า pathBP
            ถ้าพบ "-t" เปลี่ยนค่า numThreads
            ถ้าพบ "-s" เปลี่ยนค่า pathSource
            ถ้าพบ "-d" เปลี่ยนค่า pathDest


        }