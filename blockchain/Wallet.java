import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class Wallet {
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String address;
    private double balance;
    public Wallet() {
        KeyPair keyPair = Cryptography.generateKeyPairForAddress();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        address = Cryptography.generateAddress(this.publicKey);
        this.balance = 0.0;
    }

    public String getAddress() {
        return address;
    }
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
    public PublicKey getPublicKey() {
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
    public String signData(String message) {
        try {
            return Cryptography.signData(message, privateKey);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return message;
    }
    public boolean verifySignature(String data, String signatureStr) {

        try {
            return Cryptography.verifySignature(data, signatureStr, publicKey);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }
}
/*public void main(String[] args) throws Exception {
    Wallet wallet = new Wallet();
    System.out.println("Adresa: " + wallet.getAddress());
    String message = "Ovo je transakcija";
    String sig = wallet.signData(message);
    System.out.println("Potpis validan? " + wallet.verifySignature(message, sig));
}*/