import java.security.*;
import java.util.Base64;


public class Transactions {
    private String sender; // adresa
    private String receiver; // adresa
    private double amount;
    private String signature; // digitalni potpis u Base64
    private boolean isValid;
    private String senderPublicKey;

    public enum TransactionType {
        REGULAR,
        SYSTEM
    }

    private TransactionType type;

    public Transactions(String sender, String senderPublicKey, String receiver, double amount, String signature) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.signature = signature;
        this.isValid = false;
        this.type = TransactionType.REGULAR;
        this.senderPublicKey = senderPublicKey;

    }

    private Transactions(String receiver, double amount) {
        this.sender = "COINBASE";
        this.receiver = receiver;
        this.amount = amount;
        this.signature = null;
        this.isValid = true;
        this.type = TransactionType.SYSTEM;
        this.senderPublicKey = null; // nije potrebno to je system to ce svi potvrditi i sloziti se da zaslužuje nagradu osoba koja iskopa hopefully xD
    }
    public static Transactions createSystemTransaction(String receiver, double amount) {
        return new Transactions(receiver, amount);
    }


    public boolean isSystemTransaction() {
        return type == TransactionType.SYSTEM;
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
    public String getSenderPublicKey() {
        return senderPublicKey;
    }
    /*public boolean verifySignature(PublicKey senderKey) {
        if (isSystemTransaction()) {
            return false;
        }
        String data = sender + receiver + amount;
        try {
            return Cryptography.verifySignature(data, signature, senderKey);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }*/

    public void signTransaction(PrivateKey senderKey) {
        if (isSystemTransaction()) {
            return;
        }
        String data = getSigningData();
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
        String data = sender
                + senderPublicKey
                + receiver
                + amount
                + signature
                + type;

        return Cryptography.applySHA256(data);
    }

    public static String buildSigningData(String sender, String senderPublicKey,String receiver, double amount) {
        return sender + senderPublicKey + receiver + amount;
    }
    public String getSigningData() {
        return buildSigningData(sender, senderPublicKey, receiver, amount);
    }

    public boolean verifySignature() {
        if (isSystemTransaction() || senderPublicKey == null || signature == null) {
            return false;
        }

        try {
            PublicKey publicKey = Cryptography.stringToPublicKey(senderPublicKey);
            String calculatedAddress = Cryptography.generateAddress(publicKey);

            if (!calculatedAddress.equals(sender)) {
                System.out.println("javni kljuc ne pripada adresi posiljatelja.");
                return false;
            }

            return Cryptography.verifySignature(getSigningData(),signature,publicKey);

        } catch (Exception e) {
            System.out.println("Neispravan javni kljuc ili potpis / greška se dogodila");
            return false;
        }
    }

}
