package WLAN;

public class TransactionPayload {

    private String transactionId; //Transactions.getHash()
    private String sender;
    private String senderPublicKey;
    private String receiver;
    private long amount;
    private String signature;
    private String transactionType;
    //NAPOMENA isvalid se ne šalje zato što primatelj to mora sam provjeriti ne vjerovati nekome.
    public TransactionPayload(String transactionId, String sender, String senderPublicKey, String receiver, long amount,
            String signature, String transactionType) {
        this.transactionId = transactionId;
        this.sender = sender;
        this.senderPublicKey = senderPublicKey;
        this.receiver = receiver;
        this.amount = amount;
        this.signature = signature;
        this.transactionType = transactionType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSender() {
        return sender;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public String getReceiver() {
        return receiver;
    }

    public long getAmount() {
        return amount;
    }

    public String getSignature() {
        return signature;
    }

    public String getTransactionType() {
        return transactionType;
    }
}