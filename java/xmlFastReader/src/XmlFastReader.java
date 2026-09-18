package xmlFastReader;
import xmlFastParser.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;

public class XmlFastReader {
    private xmlBluePrint parser;
    private boolean bpSet = false;
    public void setBP(String bpFile) throws Exception {
        parser = new xmlBluePrint(true); parser.setPipeLineDeep(2); parser.setThreadCount(1);
        HashMap<String, TokenEntity> all = BPParser.parseBP(bpFile, 1);
        TokenRoot root = new TokenRoot(Thread.currentThread(), all);
        parser.rootRegist(EventHandlers.HD_Root, root);
        parser.errorRegist(EventHandlers.HD_Error, root);
        for (TokenEntity ent : all.values()) {
            if (ent.path != null && ent.path.length() > 0) parser.regist(ent.path, EventHandlers.HD_Entity, ent);
            for (TokenColumn col : ent.AllTokebColumn.values()) {
                if (col.path != null && col.path.length() > 0) parser.regist(col.path, EventHandlers.HD_Column, col);
            }
        }
        if (!parser.run()) throw new RuntimeException("Parser start failed");
        bpSet = true;
    }
    public HashMap<String, String[][]> parse(byte[] job,String name) throws Exception {
        if (!bpSet) throw new IllegalStateException("Call setBP() first");
        XmlToken token = new XmlToken();
        token.xml = new XmlPackage(); 
        token.xml.byteBuffer = job; 
        token.xml.size = job.length; 
        token.xml.name = name;
        parser.pushJob(job, job.length, token);
        XmlToken t;
        while (null == (t = (XmlToken)parser.pullJob()) ) LockSupport.parkNanos(200_000L);     
        HashMap<String, String[][]> ret = new HashMap();
        t.buffOP.forEach((key, val)->{
            String[] rowS = val.split("~");
            String[][] rows = new String[rowS.length][];
            for (int i = 0; i< rowS.length; i++){
                rows[i] = rowS[i].split("`");
            }
            ret.put(key, rows);
        });
        return ret;
    }
    public void shutdown(){
        if (null!= parser) parser.shutdown(true);
        parser = null;
    }
}
