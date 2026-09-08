public class PublicWallet {
    
    private String address;
    private String publicKey;
    private long balance;

    PublicWallet(String address, String publicKey) {
        this.address = address;
        this.publicKey = publicKey;
        this.balance = 0L;
    }

    public String getAddress() {
        return address;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public synchronized long getBalance() {
        return balance;
    }

    public synchronized void increaseBalance(long amount) {
        balance = Math.addExact(balance, amount);
    }

    public synchronized void decreaseBalance(long amount) {
        balance = Math.subtractExact(balance, amount);
    }
    synchronized void setBalance(long balance) {
        this.balance = balance;
    }


}
