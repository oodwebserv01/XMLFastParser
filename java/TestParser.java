import java.io.*;
import java.nio.file.Files;

public class TestParser {
    public static void main(String[] args) throws Exception {
        // Read XML file
        byte[] xmlBytes = Files.readAllBytes(new File("Lab/datas/test/TP010553713651140061000002725.xml").toPath());
        System.out.println("XML size: " + xmlBytes.length);

        // Create parser
        xmlBluePrint parser = new xmlBluePrint();
        parser.setPipeLineDeep(2);
        parser.setThreadCount(1);

        // Simple handler to track events
        xmlBluePrintCall testHandler = (idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd) -> {
            String eventName = "";
            switch(event) {
                case xmlBluePrint.EV_OPEN_TAG: eventName = "OPEN_TAG"; break;
                case xmlBluePrint.EV_CLOSE_TAG: eventName = "CLOSE_TAG"; break;
                case xmlBluePrint.EV_ATTR: eventName = "ATTR"; break;
                case xmlBluePrint.EV_INNER_TEXT: eventName = "INNER_TEXT"; break;
            }
            byte[] buf = holder.jobStart[holder.inprogress];
            String tagName = "";
            if (nameBegin > 0 && nameEnd > nameBegin && nameEnd <= buf.length) {
                tagName = new String(buf, nameBegin, nameEnd - nameBegin);
            }
            String value = "";
            if (valueBegin > 0 && valueEnd > valueBegin && valueEnd <= buf.length) {
                value = new String(buf, valueBegin, valueEnd - valueBegin);
            }
            System.err.println("[HANDLER] event=" + eventName + " tag=" + tagName + " value=" + (value.length()>50?value.substring(0,50)+"...":value));
            return true;
        };

        parser.rootRegist(testHandler, null);
        parser.errorRegist(testHandler, null);

        // Register a simple path - just the root element
        parser.regist("/rsm:TaxInvoice_CrossIndustryInvoice", testHandler, null);
        parser.regist("/rsm:TaxInvoice_CrossIndustryInvoice/rsm:SupplyChainTradeTransaction", testHandler, null);

        // Start parser
        if (!parser.run()) {
            System.err.println("Failed to start parser");
            System.exit(1);
        }
        System.err.println("Parser started");

        // Push job
        Object token = new Object();
        System.err.println("Pushing job...");
        boolean pushed = parser.pushJob(xmlBytes, xmlBytes.length, token);
        System.err.println("Push result: " + pushed);

        // Pull job
        System.err.println("Pulling job...");
        int attempts = 0;
        while (attempts < 100) {
            Object result = parser.pullJob();
            if (result != null) {
                System.err.println("Job completed, got token: " + result);
                break;
            }
            Thread.sleep(100);
            attempts++;
        }

        if (attempts >= 100) {
            System.err.println("Timeout waiting for job completion");
        }

        // Shutdown
        parser.shutdown(false);
        System.err.println("Parser shutdown complete");
    }
}