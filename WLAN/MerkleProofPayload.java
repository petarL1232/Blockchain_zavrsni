package WLAN;

import java.util.List;

public class MerkleProofPayload {

    private String blockHash;
    private String transactionId;
    private int transactionIndex;
    private List<String> siblingHashes;
    private String merkleRoot;

    public MerkleProofPayload(String blockHash, String transactionId, int transactionIndex, List<String> siblingHashes, String merkleRoot) {
        this.blockHash = blockHash;
        this.transactionId = transactionId;
        this.transactionIndex = transactionIndex;
        this.siblingHashes = siblingHashes;
        this.merkleRoot = merkleRoot;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public int getTransactionIndex() {
        return transactionIndex;
    }

    public List<String> getSiblingHashes() {
        return siblingHashes;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }
}