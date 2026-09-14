// Version 01.01.00 — safeMode + NO_Predict&Jump (upgraded jump from 01.00.00)
// Benchmark: 145 sec / 1M XML (1 thread, HDD ceiling)

import java.util.concurrent.locks.LockSupport;
import xmlFastParser.xmlBluePrint;
import xmlFastParser.xmlBluePrintCall;

/**
 * EventHandlers - Contains the 4 xmlBluePrintCall implementations
 * HD_Root: Handles root open/close events
 * HD_Error: Handles parser errors
 * HD_Entity: Handles entity close tag (row assembly)
 * HD_Column: Handles column data (innerText/attribute)
 */
public class EventHandlers {

    // ============================================================
    // HD_Root - Root handler (open/close of XML document)
    // ============================================================
    public static final xmlBluePrintCall HD_Root = (idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd) -> {
            TokenRoot tokenRoot = (TokenRoot) idToken;

            if (event == xmlBluePrint.EV_CLOSE_TAG) {
                // XML document ended - flush all entity data to xmlToken
                XmlToken xmlToken = (XmlToken) holder.getTokenFile();

                // Guard: xmlToken or xmlToken.xml might be null (e.g., forced close at job start)
                if (xmlToken == null || xmlToken.xml == null || xmlToken.xml.name == null) {
                    return true;
                }

                String fileName = xmlToken.xml.name;

                for (java.util.Map.Entry<Long, TokenEntity> entry : tokenRoot.AllTokenEntity.entrySet()) {
                    Long key = entry.getKey();
                    TokenEntity tokenEntity = entry.getValue();

                    if (!tokenEntity.isEmpty) {
                        // Build row from Factory buffers
                        StringBuilder row = new StringBuilder();
                        row.append("\"").append(fileName).append("\"");

                        StringBuffer[] rowBuffer = tokenEntity.Factory[holder.getThreadNO()];
                        for (int i = 0; i < rowBuffer.length; i++) {
                            row.append(",\"").append(rowBuffer[i]).append("\"");
                            rowBuffer[i].setLength(0); // Clear for next row
                        }

                        // Append to Stock for this thread
                        tokenEntity.Stock[holder.getThreadNO()].append(row.toString()).append("\n");
                        tokenEntity.isEmpty = true;
                    }

                    // Move Stock data to xmlToken.buffOP only if has content (counter/length)
                    StringBuffer stockBuf = tokenEntity.Stock[holder.getThreadNO()];
                    if (stockBuf.length() > 0) {
                        xmlToken.buffOP.put(key, stockBuf.toString());
                    }

                    // Clear Stock for this thread
                    stockBuf.setLength(0);
                }
            }
            else if (event == xmlBluePrint.EV_OPEN_TAG) {
                // Wake up main thread when parser starts new job
                LockSupport.unpark(tokenRoot.thread);
            }
            return true;
        
    };

    // ============================================================
    // HD_Error - Error handler
    // ============================================================
    public static final xmlBluePrintCall HD_Error = (idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd) -> {
            TokenRoot tokenRoot = (TokenRoot) idToken;
            XmlToken xmlToken = (XmlToken) holder.getTokenFile();
            String fileName = xmlToken.xml.name;

            String err;
            switch (event) {
                case xmlBluePrint.EV_UNKNOWN_ERROR:
                    err = "UNKNOWN_ERROR";
                    break;
                case xmlBluePrint.EV_EOF_IN_ROOT:
                    err = "EOF_IN_ROOT";
                    break;
                case xmlBluePrint.EV_EXPECTED_END:
                    err = "EXPECTED_END";
                    break;
                case xmlBluePrint.EV_END_NE_BEGIN:
                    err = "END_NE_BEGIN";
                    break;
                default:
                    err = "ERROR_" + event;
                    break;
            }
            
            // EV_RESET_IN_SAFEMODE : mean there a tag with same location & name as target but difference parent, parser wanna restart in safeMode we just need to reset datas(the data may be wrong)
            if (xmlBluePrint.EV_RESET_IN_SAFEMODE != event) {
                // here it's not EV_RESET_IN_SAFEMODE so this is real ERROR
                // Set error message with location and timestamp
                xmlToken.msgError = err + " : byte NO = " + holder.getErrorLocation() + " : file = " + fileName + " " + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
            }

            // Clear all entity buffers
            for (java.util.Map.Entry<Long, TokenEntity> entry : tokenRoot.AllTokenEntity.entrySet()) {
                TokenEntity tokenEntity = entry.getValue();
                if (!tokenEntity.isEmpty) {
                    StringBuffer[] rowBuffer = tokenEntity.Factory[holder.getThreadNO()];
                    for (int i = 0; i < rowBuffer.length; i++) {
                        rowBuffer[i].setLength(0);
                    }
                    tokenEntity.Stock[holder.getThreadNO()].setLength(0);
                    tokenEntity.isEmpty = true;
                }
            }
            
            // if Return "TRUE" : mean parser can continue with the current XML 
            return xmlBluePrint.EV_RESET_IN_SAFEMODE == event; 
        
    };

    // ============================================================
    // HD_Entity - Entity handler (close tag = end of row)
    // ============================================================
    public static final xmlBluePrintCall HD_Entity = (idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd) -> {
            if (event == xmlBluePrint.EV_CLOSE_TAG) {
                TokenEntity tokenEntity = (TokenEntity) idToken;
                if (tokenEntity.isEmpty) return true; // Skip assembly if no data

                XmlToken xmlToken = (XmlToken) holder.getTokenFile();
                String fileName = xmlToken.xml.name;

                // Build row from Factory buffers
                StringBuilder row = new StringBuilder();
                row.append("\"").append(fileName).append("\"");

                StringBuffer[] rowBuffer = tokenEntity.Factory[holder.getThreadNO()];
                for (int i = 0; i < rowBuffer.length; i++) {
                    row.append(",\"").append(rowBuffer[i]).append("\"");
                    rowBuffer[i].setLength(0); // Clear for next row
                }

                // Append to Stock for this thread
                tokenEntity.Stock[holder.getThreadNO()].append(row.toString()).append("\n");
                tokenEntity.isEmpty = true;
            }
            return true;
    };

    // ============================================================
    // HD_Column - Column handler (innerText/attribute data)
    // ============================================================
    public static final xmlBluePrintCall HD_Column = (idToken, holder, event, nameBegin, nameEnd, valueBegin, valueEnd) -> {
            TokenColumn tokenColumn = (TokenColumn) idToken;
            StringBuffer[] rowBuffer = tokenColumn.entity.Factory[holder.getThreadNO()];

            if (event == xmlBluePrint.EV_INNER_TEXT) {
                // Inner text content (#)
                byte[] buf = holder.getByteBuffer();
                String value = new String(buf, valueBegin, valueEnd - valueBegin).trim();
                rowBuffer[tokenColumn.inner].append(value);
                tokenColumn.entity.isEmpty = false; // Mark row as having data
            }
            else if (event == xmlBluePrint.EV_ATTR) {
                // Attribute value (@)
                byte[] buf = holder.getByteBuffer();
                long attrHash = xmlBluePrint.hash(buf, nameBegin, nameEnd);
                Integer colIndex = tokenColumn.AllAttr.get(attrHash);
                if (colIndex != null) {
                    String value = new String(buf, valueBegin, valueEnd - valueBegin).trim();
                    rowBuffer[colIndex].append(value);
                    tokenColumn.entity.isEmpty = false; // Mark row as having data
                }
            }
            return true;
    };
}
