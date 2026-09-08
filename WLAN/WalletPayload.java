package WLAN;

public class WalletPayload {
    private String address;
    private String publicKey;
    private long initialBalance;

    public WalletPayload(String address, String publicKey, long initialBalance) {
        this.address = address;
        this.publicKey = publicKey;
        this.initialBalance = initialBalance;
    }

    public String getAddress() {
        return address;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

}
