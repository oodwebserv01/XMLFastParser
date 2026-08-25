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

                    // flush data of each entity to designed files
                        if (null != xmlToken.msgError) {
                            errorLogWriter.println(xmlToken.msgError);
                        } else {
                            foreach key to buff in xmlToken.buffOP {
                                allEntityOutput.get(key).println(buff.toString());
                            }
                        }
                        xmlToken.msgError = null;
                        allEntityOutput.clear();

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
                    // รอ รับ xmlToken<doneXmlPackage> มาจาก xmlFastParser.pullJob หลับครั้งละ 0.5 ms ระหว่างรอ
                        while (null == xmlToken = parser.pullJob() ) LockSupport.parkNanos(500_000L);
                        this.xmlReturn++;

                    // flush data of each entity to designed files
                        if (null != xmlToken.msgError) {
                            errorLogWriter.println(xmlToken.msgError);
                        } else {
                            foreach key to buff in xmlToken.buffOP {
                                allEntityOutput.get(key).println(buff.toString());
                            }
                        }
                        xmlToken.msgError = null;
                        allEntityOutput.clear();


                    //ส่ง doneXmlPackage ให้ SourceHandler.closeXml 
                        source.closeXml(xmlToken.xml);
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
                โครงสร้าง TokenEntity
                    HashMap< Long, Object > AllTokebColumn; 
                    String fileName; // ชื่อไฟล์ Output ของ entity  
                    int totalColumn; // จำนวนคอลัมน์ 
                    HashMap< Long, Object > AllTokebColumn; // tokebColumn ทั้งหมดของ entity
                    boolean isEmpty;

                โครงสร้าง TokenColumn
                    TokenEntity entity; // TokenEntity 
                    String path; // xmlPath
                    int inner; // col_index of innerText
                    HashMap< Long, Int > AllAttr; // Map of col_index for each Attr

            */  

            // Parse .BP
                HashMap< Long, Object > AllTokenEntity
                TokenEntity tokenEntity = null;
                ลูป (true) {
                    ลูป ( null != Line = อ่านบรรทัดจาก(.bp) )  {
                        trim( Line )
                        ถ้า Line == "-"<path>#
                            trim <path>
                            idColumn = xmlBluePrint.hash(<path>)
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
                            idColumn = xmlBluePrint.hash(<path>)
                            trim <attr>
                            idAttr = xmlBluePrint.hash(<attr>)
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

                        ถ้า Line == "file":<fileName> break;
                    }
                    ถ้า null != tokenEntity {
                        tokenEntity.Factory = new StringBuffer [numThreads] [tokenEntity.totalColumn]
                        ลูป i = numThreads-1; i>=0; i--  {
                            ลูป j = tokenEntity.totalColumn-1; i>=0; i--  {
                                tokenEntity.Factory  [i] [j] = new StringBuffer
                            }
                        }
                        tokenEntity.Stock = new StringBuffer [numThreads] 
                        ลูป i = numThreads-1; i>=0; i--  {
                            tokenEntity.Stock [i] = new StringBuffer
                        }
                    }
                    if (null != Line) {
                        trim <fileName> 
                        nameEntity = <fileName>
                        idEntity = xmlBluePrint.hash(nameEntity)
                        tokenEntity = new TokenEntity
                        AllTokenEntity.add( idEntity, tokenEntity )
                        tokenEntity.fileName = nameEntity
                        tokenEntity.totalColumn = 0
                        tokenEntity.AllTokebColumn = new HashMap< Long, Object>
                        tokenEntity.isEmpty = true;
                        cotinue;
                    } 
                    break;
                }

            /*
                โครงสร้าง xmlToken
                    XmlPackage xml;
                    HashMap< Long ,String > buffOP; // buffOP[allEntity]
                    String msgError;
            */    

            // Prepare AllXmlToken     
                totalSizePipeLine = parser.getPipeLineTotalSlot();
                AllXmlToken = new xmlToken [ totalSizePipeLine ]
                ลูป (i = totalSizePipeLine-1; i>=0; i--)  {
                    AllXmlToken[ i ] = new xmlToken
                }

            /*
                โครงสร้าง tokenRoot
                    thread; -- Thread.currentThread
                    AllTokenEntity; -- AllTokenEntity

            */
            // สร้าง tokenRoot
                tokenRoot = new tokenRoot
                tokenRoot.thread = Thread.currentThread();
                tokenRoot.AllTokenEntity = AllTokenEntity;

            /*
                HD_Root ถูกปลุกเมื่อเริ่มต้น xml ใหม่ และเมื่อสิ้นสุดxml ,ทำหน้าที่แทน entity สำหรับข้อมูลที่มีเพียง 1 row/xml
                static final xmlBluePrintHolder HD_Root = new xmlBluePrintHolder() {
                    @Override
                    public boolean call(Object idToken, xmlBluePrintHolder holder, int event, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
                        rootToken = idToken
                        if ( xmlBluePrint.EV_CLOSE_TAG = event ) {

                            // 1. ดึงข้อมูล fileName จาก holder
                            xmlToken = holder.getTokenFile();
                            fileName = xmlToken.xml.name;

                            foreach key to tokenEntity in tokenRoot.AllTokenEntity { // 2. รับ tokenEntity ทีละตัว
                                if (!tokenEntity.isEmpty) {
                                
                                    // 3. สร้างสตริงข้อมูลครบถ้วน
                                    StringBuilder row = new StringBuilder();
                                    row.append("\"").append(fileName).append("\"");
                                    
                                    // 4. เพิ่มคอลัมน์ข้อมูลจาก rowBuffer
                                    String[] rowBuffer = tokenEntity.Factory[holder.getThreadNO]; // ดึง buffer ตามเธรด
                                    for (int i = 0; i < rowBuffer.length; i++) {
                                        row.append(",\"").append(rowBuffer[i]).append("\"");
                                        rowBuffer[i].setLength(0); //Clear buffer for next row
                                    }
                                    
                                    // 5. เก็บลง tokenEntity.Stock (สำหรับเธรดนี้)
                                    tokenEntity.Stock[holder.getThreadNO].append(row.toString()).append("\n");

                                    tokenEntity.isEmpty = true;

                                }

                                // 6. ฝากข้อมูลส่งออก ไปกับ xmlToken , xmlToken กำลังจะออกไปจาก parser
                                xmlToken.buffOP.add( key, tokenEntity.Stock[holder.getThreadNO].toString() );

                                // 7. เคลียร์ tokenEntity.Stock[holder.getThreadNO]
                                tokenEntity.Stock[holder.getThreadNO].setLength(0);
                            }
                        } else if ( xmlBluePrint.EV_OPEN_TAG = event ) {
                            // ปลุกเมนเธรด เมื่อ parser เริ่มงานใหม่ ,เพราเมนเธรดอาจจะหลับเพื่อรอ pullJob อยู่  
                            LockSupport.unpark(rootToken.thread);
                        }
                        return true;                        
                    }
                }

                
                // HD_Error ถูกปลุกเมื่อพบความผิดพลาดที่ร้ายแรงไม่สามารถผ่อนปรนได้ ไม่ว่าจะ return true หรือ false พารืเซอร์ก็จะเริ่มงานถัดไปทันที
                static final xmlBluePrintHolder HD_Error = new xmlBluePrintHolder() {
                    @Override
                    public boolean call(Object idToken, xmlBluePrintHolder holder, int event, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
                        xmlToken = holder.getTokenFile();
                        fileName = xmlToken.xml.name;    
                        String err;
                        select 
                            case xmlBluePrint.EV_UNKNOWN_ERROR 
                                err = "UNKNOWN_ERROR";
                            case xmlBluePrint.EV_EOF_IN_ROOT 
                                err = "EOF_IN_ROOT";
                            case xmlBluePrint.EV_EXPECTED_END
                                err = "EXPECTED_END";
                            case xmlBluePrint.EV_END_NE_BEGIN 
                                err = "END_NE_BEGIN";
                        end

                        xmlToken.msgError = err + " : byte NO = " + holder.pointer  + " : file = " + fileName + <YYYYMMDDHHmmss>; // วันเวลาปัจจุบัน              

                        foreach key to tokenEntity in tokenRoot.AllTokenEntity { // รับ tokenEntity ทีละตัว
                            if (!tokenEntity.isEmpty) {
                                
                                // ล้าง ข้อมูลจาก rowBuffer
                                String[] rowBuffer = tokenEntity.Factory[holder.getThreadNO]; // ดึง buffer ตามเธรด
                                for (int i = 0; i < rowBuffer.length; i++) {
                                    rowBuffer[i].setLength(0); //Clear buffer for next row
                                }
                                
                                // ล้าง tokenEntity.Stock (สำหรับเธรดนี้)
                                tokenEntity.Stock[holder.getThreadNO].setLength(0);

                                tokenEntity.isEmpty = true;

                            }

                        return false;
                    }
                }                


                HD_Entity ถูกปลุกเมื่อพบแท็กตามพาทที่ลงทะเบียนไว้ เป็นสัญาณว่า เริ่ม/สิ้นสุด ข้อมูล 1 row
                // HD_Entity implementation (inferred)
                static final xmlBluePrintHolder HD_Entity = new xmlBluePrintHolder() {
                    @Override
                    public boolean call(Object idToken, xmlBluePrintHolder holder, int event, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
                        if ( xmlBluePrint.EV_CLOSE_TAG = event ) {
                            tokenEntity = (TokenEntity) idToken;  // 1. รับ tokenEntity ที่ฝากไว้
                            
                            if (!tokenEntity.isEmpty) {
                                // 2. ดึงข้อมูล fileName จาก holder
                                xmlToken = holder.getTokenFile();
                                fileName = xmlToken.xml.name;
                                
                                // 3. สร้างสตริงข้อมูลครบถ้วน
                                StringBuilder row = new StringBuilder();
                                row.append("\"").append(fileName).append("\"");
                                
                                // 4. เพิ่มคอลัมน์ข้อมูลจาก rowBuffer
                                String[] rowBuffer = tokenEntity.Factory[holder.getThreadNO]; // ดึง buffer ตามเธรด
                                for (int i = 0; i < rowBuffer.length; i++) {
                                    row.append(",\"").append(rowBuffer[i]).append("\"");
                                    rowBuffer[i].setLength(0); //Clear buffer for next row
                                }
                                
                                // 5. เก็บลง tokenEntity.Stock (สำหรับเธรดนี้)
                                tokenEntity.Stock[holder.getThreadNO].append(row.toString()).append("\n");

                                tokenEntity.isEmpty = true;
                            }
                        }
                        return true;
                    }
                };                  
                
                HD_Column ถูกปลุกเมื่อพบแท็กตามพาทที่ลงทะเบียนไว้ เพื่อให้ผู้ใช้ เก็บ/ประมวลผล ข้อมูลที่ต้องการ
                static final xmlBluePrintHolder HD_Column = new xmlBluePrintHolder() {
                    @Override
                    public boolean call(Object idToken, xmlBluePrintHolder holder, int event, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
                        tokenColumn = idToken
                        factory = tokenColumn.tokenEntity.Factory 
                        rowBuffer = factory[holder.getThreadNO] 
                        if ( xmlBluePrint.EV_INNER_TEXT = event ) {
                            rowBuffer[tokenColumn.inner].add( String(holder.getByteBuffer() ,valueBegin ,valueEnd-valueBegin) )
                            tokenColumn.entity.isEmpty = true;
                        } else if ( xmlBluePrint.EV_ATTR = event ) {
                            idAttr = xmlBluPrint.hash(holder.getByteBuffer() ,nameBegin ,nameEnd )
                            rowBuffer[tokenColumn.AllAttr.get(idAttr)].add( String(holder.getByteBuffer() ,valueBegin ,valueEnd-valueBegin) )
                            tokenColumn.entity.isEmpty = true;
                        }
                        return true;
                    }
                }
            */    

            // เตรียม allEntityOutput       

                // 1. สร้าง Output Writer สำหแต่ละ Entity
                //    - ใช้ tokenEntity.fileName เป็นชื่อไฟล์พื้นฐาน
                //    - เขียนลงไฟล์ชื่อ EntityName_ThreadNo_YYYYMMDDHHmmss.txt

                HashMap< Long ,PrintWriter > allEntityOutput = new HashMap();
  
                foreach key to tokenEntity in AllTokenEntity {
                    // สร้าง Writer สำห Entity นี้
                    // เขียนลงไฟล์ชื่อ: tokenEntity.fileName + "_pending.txt" ก่อน
                    outputWriter = new PrintWriter(new FileWriter(tokenEntity.fileName + "_pending.txt"));
                    
                    // เก็บทุก outputWriter ไว้ใน allEntityOutput
                    allEntityOutput.add( key ,outputWriter );
                }

            // เตรียม ErrorLog_Writer
                // 1. สร้าง ErrorLogWriter สำหบันทึกข้อผิดพลาด
                //    - เขียนลงไฟล์ชื่อ "xml2txt.log" (เหมือนที่ SourceHandler สร้างไว้แล้ว)
                //    - บันทึกข้อความ error ที่เกิดขึ้นระหว่างการประมวลผล
                errorLogWriter = new PrintWriter(new FileWriter("xml2txt.err", true)); // true = append mode

            // ลงทะเบียน xmlStructure
                parser.rootRegist(HD_Root ,tokenRoot);
                parser.errorRegist(HD_Error ,tokenRoot);
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
                            this.folderReader = เปิด <โฟลเดอร์> 
                            ที่ <โฟลเดอร์> สร้าง txtFile ชื่อ xml2txt.log 
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

                                อ่านชื่อไฟล์ถัดไปจาก this.folderReader จนกว่าจะพบ .zip หรือ .xml จาก  

                                    ถ้าหมดแล้ว 
                                        ปิด this.folderReader
                                        return null

                                    ถ้า .xml 
                                        byte[] buff
                                        if null != buff = อ่านไฟล์(<fileName>)
                                            xml.byteBuffer = buff
                                            xml.size = buff.length
                                            xml.idFile = xmlBluePrint.hash(<fileName>)
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
                                                idFile = xmlBluePrint.hash(<fileName>)
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

                            // handle closing logic of xmlFile (ใช้ xml.name โดยตรง ครอบคลุมทั้งไฟล์ตรงและใน ZIP)
                                เขียน xml2txt.log
                                    "<XmlPackage.name> Close <YYYYMMDDHHmmss วันเวลาปัจจุบัน>"
                            
                            // handle closing logic of zipfile
                                if (null != zipHandler = AllZip.get(XmlPackage.idFile)) {
                                    zipHandler.countReturn++;
                                    
                                    // ปิด zipFile ถ้า return หมดแล้ว
                                    if (zipHandler.countReturn == zipHandler.countRead) {
                                        zipHandler.zipReader.close(); // ปิด zip reader
                                        zipHandler.zipReader = null;

                                        // ลบ zipHandler ออกจาก AllZip
                                            AllZip.remove(xmlBluePrint.hash(XmlPackage.idFile));

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

        // เฟส ชัดดาวน์ หรือ "ClosingJob" คืนทรัพยากรณ์และเปลี่ยนชื่อ (ทุกไฟล์)
        void ClosingJob() {

            // 1. ปิด FileWriter ของทุก Entity
                foreach key to outputWriter in allEntityOutput {
                    outputWriter.close();
                }
                allEntityOutput.clear();

            // 2. เปลี่ยนชื่อไฟล์จาก _pending เป็น _YYYYMMDDHHmmss
                foreach key to tokenEntity in AllTokenEntity {
                    if (tokenEntity.fileName != null) {
                        String oldFile = tokenEntity.fileName + "_pending.txt";
                        String newFile = tokenEntity.fileName + "_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".txt";
                        // โลจิกการเปลี่ยนชื่อไฟล์ (อาจใช้ Files.move หรือ Runtime.exec("mv ..."))
                        // ข้ามขั้นตอนนี้ไปก่อน เนื่องจากขึ้นอยู่กับ Environment
                    }
                }

            // 3. ปิดและบันทึก ErrorLog
                errorLogWriter.close();

            // 4. shutdown parser 
                parser.shutdown(false);    
                parser = null;

            // สิ้นสุด ClosingJob
        }
