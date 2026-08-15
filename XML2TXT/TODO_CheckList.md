ขั้นตอนการดำเนินงาน
*** งานที่ทำแล้วให้เปลี่ยน [_] เป็น [/] 

## 1. ทำสถาปัตยกรรมให้ชัดเจน

[/] 1.1 วนลูปจนกว่าจะได้รูปแบบของงานที่ชัดเจน อย่าลืมว่าต้องเป็นแบบ .java ไฟล์เดียวเป็น command line application สร้าง DetailArchitecture.txt เพื่อบรรยาย ลักษณะของ องค์ประกอบต่างๆว่ามีกลไกลที่สำคัญคืออะไรบ้าง มีฟังก์ชั่นหลักๆ ชื่ออะไรบ้าง ทำหน้าที่อะไร แก้ไขตรงนี้ซ้ำจนดีที่สุด

[/] 1.2 สร้างลิสต์รายชื่อฟังก์ชั่นออกมา อธิบายใน DetailFunctionDescription.txt โดยบรรยาย 1. หลักการ/อัลกอริทึ่ม 2. input/output ที่ควรจะเป็น ใช้ในการทดสอบ ของแต่ละฟังก์ชั่น 

[/] 1.3 สร้าง โฟลฺเดอร์ชื่อ Lab และ exclude จาก git

[/] 1.4 สร้าง class ชื่อ LabRat ใน Lab

[/] 1.5 เติมท้าย todo.md ด้วย ชั้นตอนงาน 
    - สร้างฟังก์ช่น ใน LabRat
    - ทดสอบและแก้ไข การทำงานของฟังก์ชั่น 
    - copy ฟังก์ชั่น มายัง xml2txt
    - ลบฟังก์ชั่น ออกจาก LabRat
    ทุกฟังก์ชั่นงานจาก DetailFunctionDescription จะต้องเพิ่ม 4 ขั้นตอนตามข้างบนนี้ 

*************************************

## 2. สร้างฟังก์ชั่นใน LabRat และทดสอบ

[/] 2.1 ListAllFile(folder) - Recursive file listing for .xml and .zip
[/] 2.2 ParseArguments(args) - Command-line argument parsing (-p, -s, -d, -t)
[/] 2.3 ReadBPFile(pathBP) - Parse .bp configuration file
[/] 2.4 RegisterPathsWithXmlBluePrint(config) - Register paths with xmlBluePrint (TODO: needs xmlBluePrint integration)
[/] 2.5 ProcessFiles(sourcePath, destPath, threadCount, config) - Main file processing (TODO: needs xmlBluePrint integration)
[/] 2.6 WriteOutputFile(threadNo, fileId, data) - Write extracted data to output file
[/] 2.7 GenerateOutputFilename(threadNo, fileType, timestamp) - Generate standardized output filename
[/] 2.8 GeneratePendingFilename(fileType, threadNo) - Generate pending filename
[/] 2.9 RenameProcessedFile(originalPath) - Rename processed file to prevent duplicate processing
[/] 2.10 Test functions (TestListAllFile, TestParseArguments, TestReadBPFile, TestIntegration)

*************************************

## 3. Copy ฟังก์ชั่นจาก LabRat ไปยัง XML2TXT.java

[/] 3.1 Copy ListAllFile function
[/] 3.2 Copy ParseArguments function and Config class
[/] 3.3 Copy ReadBPFile function and BPConfig, ColumnSpec classes
[/] 3.4 Copy utility functions (GenerateOutputFilename, GeneratePendingFilename, RenameProcessedFile)
[ ] 3.5 Implement RegisterPathsWithXmlBluePrint (requires xmlBluePrint library)
[ ] 3.6 Implement ProcessFiles (requires xmlBluePrint library)
[ ] 3.7 Implement WriteOutputFile (requires xmlBluePrint integration)
[ ] 3.8 Implement main() with full workflow

*************************************

## 4. ทดสอบและแก้ไข XML2TXT.java

[ ] 4.1 Compile XML2TXT.java
[ ] 4.2 Test with sample.bp and sample.xml
[ ] 4.3 Verify output files generated correctly
[ ] 4.4 Test file renaming (xmlFileName.bak, zipXMLZipFile.bak)
[ ] 4.5 Test multi-threading with -t parameter

*************************************

## 5. ลบฟังก์ชั่นออกจาก LabRat.java (หลังจาก copy เสร็จแล้ว)

[ ] 5.1 Remove ListAllFile from LabRat
[ ] 5.2 Remove ParseArguments and Config from LabRat
[ ] 5.3 Remove ReadBPFile, BPConfig, ColumnSpec from LabRat
[ ] 5.4 Remove utility functions from LabRat
[ ] 5.5 Remove test functions from LabRat

*************************************





