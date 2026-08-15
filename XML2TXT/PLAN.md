วัตถุประสงค์
    - XML2TXT.java เป็นโปรแกรม เขียนด้วย java เขียนขึ้นเพื่อเป็นตัวอย่างการใช้งาน xmlBluePrint ใน แพ็กเกจ xmlFastReader
    - ทำหน้าที่ หา .xml ในโฟล์เดอร์ที่ระบุ รวมถึงในโฟล์เดอร์ย่อยภายใน และภายใน .zip ด้วย 
    - แปลง ข้อมูล จาก .xml ที่หาพบ ไปเป็น .txt
    - ป้องกันการทำงานซ้ำ บนข้อมูลชุดเดิม หลังจากแปลงไฟล์แล้ว ชื่อไฟล์จะถูกเปลี่ยน 
        -- FileName.xml เปลี่ยนเป็น xmlFileName.bak เติม xml ไปข้างหน้า และ เปลี่ยนสกุลเป็น .bak
        -- XMLZipFile.zip เปลี่ยนเป็น zipXMLZipFile.bak เติม zip ไปข้างหน้า และ เปลี่ยนสกุลเป็น .bak

พารามิเตอร์ 
    - "-p Path/File.bp " ระบุด้วย -p ใช้ระบุ ไฟล์สกุล .bp ภายในระบุ paths ของ xml ที่ต้องการเก็บข้อมูลใส่ .txt
    - "-s path " ระบุโฟล์เดอร์ต้นทาง ให้ค้นหา .xml ถ้าไม่ระบุ จะหาที่โฟลฺ์เดอร์ปัจจุบัน
    - "-d path " ระบุโฟล์เดอร์ปลายทาง ถ้าไม่ระบุ จะเขียนที่โฟล์เดอร์ปัจจุบัน
        -- ????????_[ThreadNO]_[YYYYMMDDHHmmss].txt ข้อมูลที่ต้องการ สร้างเป็น ????????_[ThreadNO]_pending เขียนเสร็จแล้ว เปลี่ยนเชื่อด้วยวันเวลาปัจจุบัน [ThreadNO] หมายถึงเลขที่ลำดับของเทรด ที่ทำการ callBack เช่น Invoid_0_20260815125233.txt 
        -- read_YYYYMMDDHHmmss.txt รายชื่อของไฟล์ที่อ่าน สร้างเป็น read_pending เขียนเสร็จแล้ว เปลี่ยนเชื่อด้วยวันเวลาปัจจุบัน 
        -- success_YYYYMMDDHHmmss.txt ไฟล์ที่สำเร็จ สร้างเป็น success_pending เขียนเสร็จแล้ว เปลี่ยนเชื่อด้วยวันเวลาปัจจุบัน 
        -- fail_YYYYMMDDHHmmss.txt ไฟลืที่ล้มเหลว สร้างเป็น fail_pending เขียนเสร็จแล้ว เปลี่ยนเชื่อด้วยวันเวลาปัจจุบัน 
    - "-t number " ระบุจำนวนเทรด ถ้าไม่ระบุ ใช้ค่าเริ่มต้นของ xmlBluePrint (1) 

โครงสร้างของ .bp
    หมายเหตุ : หาก ผู้ใช้ไม่ระบุ "entity:" นั่นหมายถึง ไฟล์นั้น 1 row คือ <root> ~ </root>

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

        ในตัวอย่างนี้ 1 ไฟล์ .xml จะสร้าง .txt สองแบบ คือ Invoids_ThreadNo_YYYYMMDDHHmmss.txt และ Items_ThreadNo_YYYYMMDDHHmmss.txt

        เขียนข้อมูลออกที่ไฟล์ Invoids_ThreadNo_YYYYMMDDHHmmss.txt ==> file:Invoids
        ทุกๆกิ่งที่เลเวล1 ที่ชื่อ Transaction ตามพาทที่ระบุ คือ 1 row ==>   entity:/Transaction

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



    ตัวอย่าง 2

        file:Invoids
        entity:/Transaction
        - /Transaction/Invoid@SN
        - /././Saler/PersonID#
        - /././Buyer/PersonID#
        - /././Items@Count
        - /././Summary#

        file:Items
        entity:/Transaction/Invoid/Items/Item
        - /Transaction/Invoid/Items/Item@ID
        - /././././Name#
        - /././././Quantity#
        - /././././UnitPrice#
        - /././././TotalPrice#

    อธิบายตัวอย่าง 2
        ความหมายของ ตัวอย่าง 2 เหมือนกับตัวอย่าง 1 แต่เป็นรูปแบบอย่างย่อ 
        คือ ใช้ /. แทน /BranchName โดยจะลอกค่า BranchName มาจากบรรทัดบนที่มรการระบุไว้ 
        หาก ไม่มีการระบุไว้ก่อนหน้าเลย ก็จะ error

สถาปัตยกรรม
    - ให้เขียนเป็นแบบ old-scrool command-line app ทุกอย่างจบในไฟล์เดียว เพื่อให้อ่านง่าย เหมาะแก่การเป็นตัวอย่าง
    - สร้าง ฟังก์ชั่น ListAllFile(folder) ทำงานแบบ recurcive สร้าง String รายชื่อไฟล์(.xml และ .zip) ตย. "path/file1[,]path/file2[,]path/file3" ใช้ [,] คั่นระหว่างไฟล์ จะได้ split เป็น array ง่าย
    - วน ลูป ป้อนไฟล์เข้า คิวงานของ xmlBluePrint จนกว่าจะหมด sleep สั้นๆ ถ้า คิวงานเต็ม
    - หลัง จากป้อนงานจนหมด รอ จนกว่า xmlBluPrint.isReadyToDown แล้วจึงสั่ง shutDown sleep สั้นๆ ระหว่างรอ
    - การจัดการ พาราิเตอร์ ใช้ วิธี 
        -- ถ้า  arg[0] = "-p" arg[1] จะต้องเป็น path/file.mp ถ้าไม่ใช้ error 
        -- ขณะ กำลังเก็บ พารามิเตอร์ arg[i] เข้าตัวแปร 
            // want to keep path/file.mp in this.pathMP
            if (null != this.pathMP) { 
                {error handler}; 
            } else {
                this.pathMP = arg[i];
            }
        -- -p , -s , -d , -t สามารถ สลับการมาก่อนหลังกันได้ 
    - การนำ xmlPath จาก .mp  ไปลงทะเบียนที่ xmlBluprint 
        -- อ่าน จากหลังมาหน้า เพื่อ เช็ค # และ @(เก็บ attrName ไว้ใช้ด้วย)
        -- split "/tag1/tag2/tag3/tag4" เป็น ["","tag1","tag2","tag3","tag4"]
        -- ถ้า มี "." อยู่ภายในอาเรย์ มันเกิดจาก "/././tag"  ให้ copy tagname  จากแถวก่อนหน้า มาแทนที่
        -- ต่อสตริงกลับ ขณะลงทะเบียน ใน xmlBluePrint
    - การ handleCallBack     