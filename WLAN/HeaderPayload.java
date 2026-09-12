package WLAN;

public class HeaderPayload {

    private int height;
    private String previousHash;
    private String merkleRoot;
    private long timestamp;
    private String blockHash;
    private long nonce;
    private int difficulty;

    public HeaderPayload(int height, String previousHash, String merkleRoot, long timestamp, String blockHash, long nonce, int difficulty) {
        this.height = height;
        this.previousHash = previousHash;
        this.merkleRoot = merkleRoot;
        this.timestamp = timestamp;
        this.blockHash = blockHash;
        this.nonce = nonce;
        this.difficulty = difficulty;
    }

    public int getHeight() {
        return height;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public long getNonce() {
        return nonce;
    }

    public int getDifficulty() {
        return difficulty;
    }
}
