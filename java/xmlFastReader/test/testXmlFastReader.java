package xmlFastReader;
import java.nio.file.*;
import java.util.*;

public class testXmlFastReader {
    public static void main(String[] args) throws Exception {
        /* ----- Create Instance of XmlFastReader as reader ----- */
        XmlFastReader reader = new XmlFastReader();

        /* ----- Assign .BP to reader ----- */
        reader.setBP("/home/wise0136/NetBeansProjects/Lab/BluePrint.bp");
        String file = "/home/wise0136/NetBeansProjects/Lab/Case_1/TP099400018234140061000002564.xml";
        byte[] job = Files.readAllBytes(Paths.get(file));

        /* ----- Assign Job to reader then get HashMAP<String, Array[][] of String> as return ----- */
        HashMap<String, String[][]> ret = reader.parse(job,file);
        
        /* ----- Use datas as you want ----- */
        ret.forEach((key,val)->{
            try{
                writeEntity(key,val,"/home/wise0136/NetBeansProjects/xmlFastReader/xmlFastReader/target");
            }catch(Exception e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        });
        
        System.out.println("testXmlFastReader เสร็จ — ret keys: " + ret.keySet());

        /* ----- Shutdown the reader ----- */
        reader.shutdown();
    }
    
    static void writeEntity(String file, String[][] rows, String path) throws Exception {
        String out = path+"/"+file+".txt";
        if (rows == null)  return; 
        String allRow = "";
        for (int r = 0; r< rows.length; r++) {
            String row = "";
            for (int c = 0 ; c< rows[r].length; c++) {
                row += ",\""+rows[r][c] +"\"";
            }
            allRow += row.substring(1)+"\n";
        }
        Files.write(Paths.get(out), allRow.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("เขียน " + out + " (" + rows.length+ " rows)");
    }
}
