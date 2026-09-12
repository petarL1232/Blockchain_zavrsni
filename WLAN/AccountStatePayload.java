package WLAN;

public class AccountStatePayload {

    private String address;
    private long spendableBalance;
    private long nextNonce;
    private int blockHeight;
    private String blockHash;

    public AccountStatePayload(String address, long spendableBalance, long nextNonce, int blockHeight, String blockHash) {
        this.address = address;
        this.spendableBalance = spendableBalance;
        this.nextNonce = nextNonce;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
    }

    public String getAddress() {
        return address;
    }

    public long getSpendableBalance() {
        return spendableBalance;
    }

    public long getNextNonce() {
        return nextNonce;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public String getBlockHash() {
        return blockHash;
    }
}
