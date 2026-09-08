import java.io.*;
import java.util.*;
import java.util.zip.*;
import xmlFastParser.xmlBluePrint;

/**
 * SourceHandler - Manages source files (.xml and .zip containing .xml)
 * Iterates through folder, handles zip entries, provides XmlPackage objects
 */
public class SourceHandler {
    private File folder;
    private File[] files;
    private int fileIndex = 0;
    private PrintWriter logWriter;
    private HashMap<Long, ZipHandler> allZip = new HashMap<>();
    private ZipHandler currentZipHandler = null;

    // ZipHandler inner class
    public static class ZipHandler {
        public long idFile;
        public String name;
        public boolean canRead = true;
        public int countRead = 0;      // Total XML entries in zip
        public int countReturn = 0;    // Number of entries returned
        public ZipFile zipReader;
        private Enumeration<? extends ZipEntry> entries;

        public ZipHandler(ZipFile zipFile, long idFile, String name) throws IOException {
            this.zipReader = zipFile;
            this.idFile = idFile;
            this.name = name;
            this.entries = zipFile.entries();
            // Pre-count XML entries
            this.countRead = countXmlEntries(zipFile);
            // Reset enumeration for reading
            this.entries = zipFile.entries();
        }

        private int countXmlEntries(ZipFile zipFile) {
            int count = 0;
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
                    count++;
                }
            }
            return count;
        }

        public ZipEntry nextXmlEntry() {
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
                    return entry;
                }
            }
            return null;
        }
    }

    public SourceHandler(String folderPath) throws IOException {
        this.folder = new File(folderPath);
        if (!this.folder.exists() || !this.folder.isDirectory()) {
            throw new IllegalArgumentException("Source folder does not exist: " + folderPath);
        }

        // List files (.xml and .zip only, no subdirectories)
        this.files = this.folder.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return f.isFile() && (name.endsWith(".xml") || name.endsWith(".zip"));
        });

        if (this.files == null) {
            this.files = new File[0];
        }

        // Create log file in source folder
        File logFile = new File(this.folder, "xml2txt.log");
        this.logWriter = new PrintWriter(new FileWriter(logFile, true)); // append mode
    }

    /**
     * Get next XmlPackage from source (direct .xml or from .zip)
     * @return XmlPackage or null if no more files
     */
    public XmlPackage getXml() {
        XmlPackage xml = new XmlPackage();

        while (true) {
            // If currently reading from a zip, continue reading entries
            if (currentZipHandler != null && currentZipHandler.canRead) {
                ZipEntry entry = currentZipHandler.nextXmlEntry();
                if (entry != null) {
                    try {
                        InputStream is = currentZipHandler.zipReader.getInputStream(entry);
                        byte[] buffer = readAllBytes(is);
                        is.close();

                        if (buffer != null && buffer.length > 0) {
                            xml.byteBuffer = buffer;
                            xml.size = buffer.length;
                            xml.idFile = currentZipHandler.idFile;
                            xml.name = currentZipHandler.name + "/" + entry.getName();

                            // logWrite(xml.name + " Open " + timestamp()); // skip zip entry log
                            return xml;
                        }
                    } catch (IOException e) {
                        logWrite(currentZipHandler.name + " ReadError " + timestamp() + " " + e.getMessage());
                        continue;
                    }
                } else {
                    // No more XML entries in this zip
                    currentZipHandler.canRead = false;
                    try {
                        currentZipHandler.zipReader.close();
                    } catch (IOException e) {
                        // ignore
                    }
                    currentZipHandler = null;
                    continue;
                }
            }

            // No current zip or zip exhausted, get next file from folder
            if (fileIndex >= files.length) {
                // All files processed
                return null;
            }

            File file = files[fileIndex++];
            String fileName = file.getName();
            String lowerName = fileName.toLowerCase();

            if (lowerName.endsWith(".xml")) {
                // Direct XML file
                try {
                    byte[] buffer = readFile(file);
                    if (buffer != null && buffer.length > 0) {
                        xml.byteBuffer = buffer;
                        xml.size = buffer.length;
                        xml.idFile = xmlBluePrint.hash(fileName.getBytes(), fileName.getBytes().length);
                        xml.name = fileName;
                        logWrite(xml.name + " Open " + timestamp());
                        return xml;
                    }
                } catch (IOException e) {
                    logWrite(fileName + " ReadError " + timestamp() + " " + e.getMessage());
                    continue;
                }
            }
            else if (lowerName.endsWith(".zip")) {
                // ZIP file - open and prepare to read entries
                ZipFile zipFile = null;
                try {
                    zipFile = new ZipFile(file);
                    long idFile = xmlBluePrint.hash(fileName.getBytes(), fileName.getBytes().length);
                    currentZipHandler = new ZipHandler(zipFile, idFile, fileName);
                    allZip.put(idFile, currentZipHandler);
                    logWrite(fileName + " Open " + timestamp());
                    continue; // Loop will process first entry
                } catch (IOException e) {
                    logWrite(fileName + " UnOpenAble " + timestamp() + " " + e.getMessage());
                    try {
                        if (zipFile != null) zipFile.close();
                    } catch (IOException ex) {}
                    continue;
                }
            }
        }
    }

    /**
     * Close XML package and handle zip cleanup
     */
    public void closeXml(XmlPackage xmlPkg) {
        if (xmlPkg == null || xmlPkg.name == null) return;

        if (xmlPkg.name != null && !xmlPkg.name.contains("/")) {
            logWrite(xmlPkg.name + " Close " + timestamp());
        }

        // Check if this was from a zip
        if (xmlPkg.idFile != 0) {
            ZipHandler zh = allZip.get(xmlPkg.idFile);
            if (zh != null) {
                zh.countReturn++;
                // If all entries returned, close zip
                if (zh.countReturn >= zh.countRead) {
                    try {
                        if (zh.zipReader != null) {
                            zh.zipReader.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                    zh.zipReader = null;
                    allZip.remove(xmlPkg.idFile);
                    logWrite(zh.name + " Close " + timestamp());
                }
            }
        }
    }

    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readAllBytes(fis);
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private void logWrite(String msg) {
        if (logWriter != null) {
            logWriter.println(msg);
            logWriter.flush();
        }
    }

    public void closeLog() {
        if (logWriter != null) {
            logWriter.close();
            logWriter = null;
        }
    }

    private String timestamp() {
        return new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }
}