public class PublicWallet {
    
    private String address;
    private String publicKey;
    private double balance;

    PublicWallet(String address, String publicKey) {
        this.address = address;
        this.publicKey = publicKey;
        this.balance = 0.0;
    }

    public String getAddress() {
        return address;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public double getBalance() {
        return balance;
    }

    public void increaseBalance(double amount) {
        balance = balance + amount;
    }

    public void decreaseBalance(double amount) {
        balance = balance - amount;
    }


}
