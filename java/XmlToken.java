import java.util.HashMap;

/**
 * XmlToken - Pipeline token that carries XmlPackage through the parser
 * Contains buffers for output data (buffOP) and error message (msgError)
 */
public class XmlToken {
    public XmlPackage xml;
    public HashMap<Long, String> buffOP;
    public String msgError;

    public XmlToken() {
        this.buffOP = new HashMap<>();
        this.msgError = null;
        this.xml = null;
    }

    public XmlToken(XmlPackage xml) {
        this();
        this.xml = xml;
    }

    public XmlPackage getXml() {
        return xml;
    }

    public void setXml(XmlPackage xml) {
        this.xml = xml;
    }

    public HashMap<Long, String> getBuffOP() {
        return buffOP;
    }

    public void setBuffOP(HashMap<Long, String> buffOP) {
        this.buffOP = buffOP;
    }

    public String getMsgError() {
        return msgError;
    }

    public void setMsgError(String msgError) {
        this.msgError = msgError;
    }

    public void clear() {
        this.buffOP.clear();
        this.msgError = null;
        // xml is reused, don't clear
    }

    @Override
    public String toString() {
        return "XmlToken{xml=" + xml + ", buffOP.size=" + buffOP.size() + ", msgError=" + msgError + "}";
    }
}