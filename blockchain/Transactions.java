import java.security.*;
import java.util.Base64;

public class Transactions {
    private String sender; // adresa
    private String receiver; // adresa
    private double amount;
    private String signature; // digitalni potpis u Base64
    private boolean isValid;

    public Transactions(String sender, String receiver, double amount, String signature) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.signature = signature;
        this.isValid = false;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public double getAmount() {
        return amount;
    }

    public String getSignature() {
        return signature;
    }

    public boolean getValidity() {
        return isValid;
    }

    public boolean verifySignature(PublicKey senderKey) {
        String data = sender + receiver + amount;
        try {
            return Cryptography.verifySignature(data, signature, senderKey);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    public void signTransaction(PrivateKey senderKey) {
        String data = sender + receiver + amount;
        try {
            this.signature = Cryptography.signData(data, senderKey);
            this.isValid = true;
        } catch (Exception e) {
            e.printStackTrace();
            this.isValid = false;
        }
    }

    // ovo dodajem zbog toga što je potrebno za light node
    public String getHash() {

        String data = sender + receiver + amount + signature;
        return Cryptography.applySHA256(data);
    }

}
