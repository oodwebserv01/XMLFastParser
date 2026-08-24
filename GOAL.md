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
    
    ภาพรวม /*-- SUDO CODE from here --*/

        // เฟส พาร์ดาต้า 
        void MainLoop() {
            XmlPackage xml;   
            XmlToken xmlToken;

            // เฟสย่อย เติมไปป์ไลน์ด้วย โทเคนที่สร้างเตรียมไว้
                while ( ( xmlRead < AllXmlToken.length )  && (null != xml = source.getXml() ) ) {

                    // นำ xmlToken มาจากที่เก็บเมื่อตอนสร้าง แล้วบรรจุด้วย xmlPackage จาก SourceHandler
                        AllXmlToken[xmlRead].xml = xml;

                    // ส่ง xmlToken<XmlPackage> เข้าคิวงานของ xmlFastParser.pushJob 
                        parser.pushJob(xml.byteBuffer ,xml.size ,AllXmlToken[xmlRead]);
                        this.xmlRead++;
                }
            
            // เฟสย่อย รียูสโทเคนเก่า ด้วยโทเค่นที่คืนมาจากพาร์เซอร์ 
                while (null != xml = source.getXml() ) {

                    // รอ รับ xmlToken<doneXmlPackage> มาจาก xmlFastParser.pullJob หลับครั้งละ 0.5 ms ระหว่างรอ
                        while (null == xmlToken = parser.pullJob() ) LockSupport.parkNanos(500_000L);
                        this.xmlReturn++;

                    //ส่ง doneXmlPackage ให้ SourceHandler.closeXml 
                        source.closeXml(xmlToken.xml);

                    //บรรจุ xmlToken<doneXmlPackage> ด้วย XmlPackage(ใหม่) จาก SourceHandler --> xmlToken<XmlPackage>
                        xmlToken.xml = xml;

                    //ส่ง xmlToken<XmlPackage> เข้าคิวงานของ xmlFastParser.pushJob
                        parser.pushJob(xml.byteBuffer ,xml.size ,xmlToken);
                        this.xmlRead++;                        
                }

            // เฟสย่อย เคลียร์ไปปไลน์
                ลูปเมือ จำนวนที่PULL < จำนวนที่PUSH
                    รอ รับ xmlToken<doneXmlPackage> มาจาก xmlFastParser.pullJob หลับครั้งละ 0.1 ms ระหว่างรอ
                    ส่ง doneXmlPackage ให้ SourceHandler.closeXml 
        }

        // เฟส เตรียมตัว 
        void BootUp() {
            // reset counter
                this.xmlRead = 0;
                this.xmlReturn = 0;

            xmlFastParser parser = new xmlFastParser();    

            // tunning parser
                default C_PipeLineDeep = 2 
                    /*
                        PipeLineDeep เป็นค่า N ของ (2^N)-1  
                        setPipeLineDeep(2) : RealSize = 3
                        setPipeLineDeep(3) : RealSize = 7
                        setPipeLineDeep(4) : RealSize = 15
                        setPipeLineDeep(5) : RealSize = 31
                    */
                parser.setPipeLineDeep(C_PipeLineDeep)

            // Parse CLI
                default pathBP      = "BluePrint.bp"
                default numThreads  = 1
                default pathSource  = "."
                default pathDest    = "."
                ถ้า args พบ "-p" เปลี่ยนค่า pathBP
                ถ้า args พบ "-t" เปลี่ยนค่า numThreads
                ถ้า args พบ "-s" เปลี่ยนค่า pathSource
                ถ้า args พบ "-d" เปลี่ยนค่า pathDest

            /*
                โครงสร้าง xmlToken
                    XmlPackage xml
                    String buffOP
            */    

            // Prepare AllXmlToken     
                totalSizePipeLine = parser.getPipeLineTotalSlot();
                AllXmlToken = new xmlToken [ totalSizePipeLine ]
                ลูป i = totalSizePipeLine-1; i>=0; i--  
                    AllXmlToken[ i ] = new xmlToken

            // Parse .BP
                HashMap< Long, Object > AllTokenEntity
                ลูป ทุก Line จาก .bp 
                    trim( Line )
                    ถ้า Line == "-"<path>#
                        trim <path>
                        idColumn = xmlFastParser.hash(<path>)
                        if null == tokenColumn = tokenEntity.AllTokebColumn.get(idColumn)
                            new tokenColumn
                            tokenEntity.AllTokebColumn.add( idColumn, tokenColumn )
                            tokenColumn.entity = tokenEntity
                            tokenColumn.path = <path>
                            tokenColumn.inner  = -1
                            tokenColumn.AllAttr = new HashMap< Long, int>
                            
                        tokenColumn.inner = tokenEntity.totalColumn
                        tokenEntity.totalColumn++

                    if ( Line == "-"<path>@<attr> ) {
                        trim <path>
                        idColumn = xmlFastParser.hash(<path>)
                        trim <attr>
                        idAttr = xmlFastParser.hash(<attr>)
                        if ( null == tokenColumn = tokenEntity.AllTokebColumn.get(idColumn) ) {
                            new tokenColumn
                            tokenEntity.AllTokebColumn.add( idColumn, tokenColumn )
                            tokenColumn.entity = tokenEntity
                            tokenColumn.path = <path>
                            tokenColumn.inner  = -1
                            tokenColumn.AllAttr = new HashMap< Long, int>
                        }
                        if null == columh = tokenColumn.AllAttr.get(idAttr) {
                            tokenColumn.AllAttr.add( idAttr, tokenEntity.totalColumn )
                        }   
                        tokenEntity.totalColumn++
                    }
                    ถ้า Line == "entity":<path>    
                        tokenEntity.path = <path>

                    ถ้า Line == "file":<fileName>
                        ถ้า null != tokenEntity
                            tokenEntity.Factory = new StringBuffer [numThreads] [tokenEntity.totalColumn]
                            ลูป i = numThreads-1; i>=0; i--  
                                ลูป j = tokenEntity.totalColumn-1; i>=0; i--  
                                    tokenEntity.Factory  [i] [j] = new StringBuffer

                            tokenEntity.Stock = new StringBuffer [numThreads] 
                            ลูป i = numThreads-1; i>=0; i--  
                                tokenEntity.Stock [i] = new StringBuffer

                        trim <fileName> 
                        nameEntity = <fileName>
                        idEntity = xmlFastParser.hash(nameEntity)
                        new tokenEntity
                        AllTokenEntity.add( idEntity, tokenEntity )
                        tokenEntity.fileName = nameEntity
                        tokenEntity.totalColumn = 0
                        tokenEntity.AllTokebColumn = new HashMap< Long, Object>

            /*
                HD_Root ถูกปลุกเมื่อเริ่มต้น xml ใหม่ และเมื่อสิ้นสุดxml ,ทำหน้าที่แทน entity สำหรับข้อมูลที่มีเพียง 1 row/xml

                
                HD_Error ถูกปลุกเมื่อพบความผิดพลาดที่ร้ายแรงไม่สามารถผ่อนปรนได้ ไม่ว่าจะ return true หรือ false พารืเซอร์ก็จะเริ่มงานถัดไปทันที


                HD_Entity ถูกปลุกเมื่อพบแท็กตามพาทที่ลงทะเบียนไว้ เป็นสัญาณว่า เริ่ม/สิ้นสุด ข้อมูล 1 row
            
                
                HD_Column ถูกปลุกเมื่อพบแท็กตามพาทที่ลงทะเบียนไว้ เพื่อให้ผู้ใช้ เก็บ/ประมวลผล ข้อมูลที่ต้องการ

                

            */                        

            // ลงทะเบียน xmlStructure
                parser.rootRegist(HD_Root ,AllTokenEntity);
                parser.errorRegist(HD_Error ,AllTokenEntity);
                forEach tokenEntity in AllTokenEntity {
                    if ( 1 <= tokenEntity.path.length ) {
                        parser.regist(tokenEntity.path ,HD_Entity ,tokenEntity);
                    }
                    forEach tokenColumn in tokenEntity.AllTokebColumn {
                        parser.regist(tokenEntity.path ,HD_Column ,tokenColumn);
                    }
                }            

            // สร้าง SourceHandler
                this.source = new SourceHandler( pathSource )
                    /*
                        SourceHandler คือ Object ทำหน้าที่ บริหารจัดการไฟล์ข้อมูลต้นทาง อันได้แก่ .xml และ .zip

                        โครงสร้าง XmlPackage
                            byteBuffer = <data from file>
                            size = <byteBuffer.length>
                            idFile = <hash(ชื่อไฟล์)>
                            name = <ชื่อไฟล์>

                        โครงสร้าง zipHandler 
                            idFile = <hash(ชื่อไฟล์)> 
                            name = <fileName>
                            canRead = (default) true;
                            countRead = (default) 0;
                            countReturn = (default) 0;
                            zipReader = <ตัวอ่านไฟล์.zip ชอง่ java>

                            zipHandler.read(XmlPackage) {
                                เปิด zip ไฟล์ 
                            }
                            

                        เมื่อสร้าง SourceHandler(<โฟลเดอร์>) {
                            ที่ <โฟลเดอร์> สร้าง txtFile ชื่อ xml2txt.log 
                            AllXml = new HashMap<Long, Object>
                            AllZip = new HashMap<Long, Object>
                        }

                        SourceHandler.getXml() {
                            xml = new XmlPackage
                            ลูป 
                                ถ้ามี zipHandler && zipHandler.canRead  
                                    ใช้  zipHandler.zipReader เปิดไฟล์ถัดไปเรื่อยๆ จนพบ <xmlFile.xml>
                                        byte[] buff
                                        if null != buff = zipHandler.zipReader.อ่านไฟล์(<xmlFile.xml>)
                                            xml.byteBuffer = buff
                                            xml.size = buff.length
                                            xml.idFile = zipHandler.idFile;
                                            xml.name = zipHandler.name + "/" + <xmlFile.xml>
                                            zipHandler.countRead++
                                            return xml
                                        continue    
                                    ถ้า ใน zip ไม่มี <xmlFile.xml> อีกแล้ว หรือ อ่านไม่ได้อีกแล้ว 
                                        zipHandler.canRead = false
                                        continue

                                หา .zip หรือ .xml จาก <โฟลเดอร์> ที่ยังไม่มีใน xml2txt.log

                                    ถ้าหมดแล้ว 
                                        return null

                                    ถ้า .xml 
                                        byte[] buff
                                        if null != buff = อ่านไฟล์(<fileName>)
                                            xml.byteBuffer = buff
                                            xml.size = buff.length
                                            xml.idFile = xmlFastParser.hash(<fileName>)
                                            AllXml.add(idFile, <fileName>) 
                                            xml.name = <fileName>
                                            เขียน xml2txt.log
                                                "<fileName> Open <YYYYMMDDHHmmss วันเวลาปัจจุบัน>"
                                            return xml
                                        continue    

                                    ถ้า .zip 
                                        เปิด zip ไฟล์ // สร้างและเปิด <ตัวอ่านไฟล์.zip ชอง่ java>
                                            ถ้าเปิดไม่ได้ 
                                                เขียน xml2txt.log
                                                    "<fileName> UnOpenAble <YYYYMMDDHHmmss วันเวลาปัจจุบัน>"
                                                continue
                                            ถ้าเปิดได้ // สร้าง <ตัวอ่านไฟล์.zip ชอง่ java> และเปิดได้สำเร้จ 
                                                idFile = xmlFastParser.hash(<fileName>)
                                                new zipHandler    
                                                AllZip.add( idFile, zipHandler )
                                                zipHandler.zipReader = <ตัวอ่านไฟล์.zip ชอง่ java>
                                                zipHandler.idFile = idFile
                                                zipHandler.name = <fileName>
                                                zipHandler.canRead = true;
                                                zipHandler.countRead = 0;
                                                zipHandler.countReturn = 0;
                                                continue
                        }

                        source.closeXml(XmlPackage) {

                            // handle closing logic of xmlFile
                                String fName;
                                if (null != fname = AllXml.get(XmlPackage.name) ) {
                                    เขียน xml2txt.log
                                        "<fileName> Close <YYYYMMDDHHmmss วันเวลาปัจจุบัน>"
                                }
                            
                            // handle closing logic of zipfile
                                if (null != zipHandler = AllZip.get(XmlPackage.idFile)) {
                                    zipHandler.countReturn++;
                                    
                                    // ปิด zipFile ถ้า return หมดแล้ว
                                    if (zipHandler.countReturn == zipHandler.countRead) {
                                        zipHandler.zipReader.close(); // ปิด zip reader
                                        zipHandler.zipReader = null;

                                        // ลบ zipHandler ออกจาก AllZip
                                            AllZip.remove(xmlFastParser.hash(XmlPackage.idFile));

                                        // ลบ zipHandler ออกจาก memory
                                            zipHandler = null;

                                        // เขียน log
                                            เขียน xml2txt.log
                                            "<zipFileName> Close <YYYYMMDDHHmmss วันเวลาปัจจุบัน>"

                                    }
                                }
                        }
                    */
        }