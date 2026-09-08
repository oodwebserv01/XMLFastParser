import java.util.HashMap;

/**
 * TokenEntity - Represents an output entity (file) defined in .bp
 * Contains all columns, per-thread buffers (Factory, Stock) for data assembly
 */
public class TokenEntity {
    public HashMap<Long, TokenColumn> AllTokebColumn;   // All columns for this entity
    public String fileName;                              // Output file base name (e.g., "Invoids")
    public int totalColumn = 0;                          // Total number of columns
    public boolean isEmpty = true;                       // Flag: current row has data

    // Per-thread buffers (initialized via initBuffers)
    public StringBuffer[][] Factory;  // [threadNo][columnIndex] - row assembly buffers
    public StringBuffer[] Stock;      // [threadNo] - accumulated rows for flush

    public String path;                  // Entity XPath (e.g., "/Transaction")

    public TokenEntity() {
        this.AllTokebColumn = new HashMap<>();
        this.fileName = null;
        this.path = null;
        this.totalColumn = 0;
        this.isEmpty = true;
        this.Factory = null;
        this.Stock = null;
    }

    public TokenEntity(String fileName, String path) {
        this();
        this.fileName = fileName;
        this.path = path;
    }

    /**
     * Initialize per-thread buffers
     * @param numThreads Number of parser threads
     */
    public void initBuffers(int numThreads) {
        this.Factory = new StringBuffer[numThreads][this.totalColumn];
        for (int t = 0; t < numThreads; t++) {
            for (int c = 0; c < this.totalColumn; c++) {
                this.Factory[t][c] = new StringBuffer();
            }
        }
        this.Stock = new StringBuffer[numThreads];
        for (int t = 0; t < numThreads; t++) {
            this.Stock[t] = new StringBuffer();
        }
    }

    public void addColumn(long pathHash, TokenColumn column) {
        this.AllTokebColumn.put(pathHash, column);
        column.setEntity(this);
    }

    public TokenColumn getColumn(long pathHash) {
        return this.AllTokebColumn.get(pathHash);
    }

    public void clearRowBuffer(int threadNo) {
        if (this.Factory != null && threadNo < this.Factory.length) {
            for (int c = 0; c < this.Factory[threadNo].length; c++) {
                this.Factory[threadNo][c].setLength(0);
            }
        }
        if (this.Stock != null && threadNo < this.Stock.length) {
            this.Stock[threadNo].setLength(0);
        }
        this.isEmpty = true;
    }

    @Override
    public String toString() {
        return "TokenEntity{fileName='" + fileName + "', path='" + path + "', columns=" + totalColumn + ", threads=" + (Factory != null ? Factory.length : 0) + "}";
    }
}