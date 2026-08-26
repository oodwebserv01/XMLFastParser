import java.util.HashMap;

/**
 * TokenRoot - Root token passed to parser's rootRegist and errorRegist
 * Contains reference to main thread and all TokenEntity map
 */
public class TokenRoot {
    public Thread thread;                                    // Main thread (for unpark)
    public HashMap<Long, TokenEntity> AllTokenEntity;        // All entities by hash

    public TokenRoot() {
        this.AllTokenEntity = new HashMap<>();
        this.thread = null;
    }

    public TokenRoot(Thread thread, HashMap<Long, TokenEntity> allTokenEntity) {
        this.thread = thread;
        this.AllTokenEntity = allTokenEntity;
    }

    public Thread getThread() {
        return thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public HashMap<Long, TokenEntity> getAllTokenEntity() {
        return AllTokenEntity;
    }

    public void setAllTokenEntity(HashMap<Long, TokenEntity> allTokenEntity) {
        this.AllTokenEntity = allTokenEntity;
    }

    public void addEntity(long hash, TokenEntity entity) {
        this.AllTokenEntity.put(hash, entity);
    }

    public TokenEntity getEntity(long hash) {
        return this.AllTokenEntity.get(hash);
    }

    @Override
    public String toString() {
        return "TokenRoot{thread=" + (thread != null ? thread.getName() : "null") + ", entities=" + AllTokenEntity.size() + "}";
    }
}