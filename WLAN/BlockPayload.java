package WLAN;

import java.util.List;

public class BlockPayload {

    private long index;//vjeruje
    private String previousHash;//vjeruje
    private long timestamp;//vjeruje
    private List<TransactionPayload> transactions;//vjeruje (jer je vec provjerio)
    private long nonce; //vjeruje
    private String hash; // sam provjerava
    private String merkleRoot; // sam provjerava
    private int difficulty; // sam provjerava

    public BlockPayload(long index, String previousHash, long timestamp, List<TransactionPayload> transactions,
            long nonce, String hash, String merkleRoot, int difficulty) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = timestamp;
        this.transactions = transactions;
        this.nonce = nonce;
        this.hash = hash;
        this.merkleRoot = merkleRoot;
        this.difficulty = difficulty;
    }

    public long getIndex() {
        return index;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<TransactionPayload> getTransactions() {
        return transactions;
    }

    public long getNonce() {
        return nonce;
    }

    public String getHash() {
        return hash;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public int getDifficulty() {
        return difficulty;
    }
}