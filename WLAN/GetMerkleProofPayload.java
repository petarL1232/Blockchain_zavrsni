package WLAN;

public class GetMerkleProofPayload {

    private String blockHash;
    private String transactionId;

    public GetMerkleProofPayload(String blockHash, String transactionId) {
        this.blockHash = blockHash;
        this.transactionId = transactionId;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public String getTransactionId() {
        return transactionId;
    }
}