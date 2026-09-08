/**
 * XmlPackage - Data container for XML file content
 * Used by SourceHandler to pass XML data to parser
 */
public class XmlPackage {
    public byte[] byteBuffer;
    public int size;
    public long idFile;
    public String name;

    public XmlPackage() {
    }

    public XmlPackage(byte[] byteBuffer, int size, long idFile, String name) {
        this.byteBuffer = byteBuffer;
        this.size = size;
        this.idFile = idFile;
        this.name = name;
    }

    public byte[] getByteBuffer() {
        return byteBuffer;
    }

    public void setByteBuffer(byte[] byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getIdFile() {
        return idFile;
    }

    public void setIdFile(long idFile) {
        this.idFile = idFile;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "XmlPackage{name='" + name + "', size=" + size + ", idFile=" + idFile + "}";
    }
}