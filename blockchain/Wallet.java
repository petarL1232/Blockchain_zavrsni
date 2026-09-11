import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

// ovo je zapravo private wallet ne davati nikome osim korisniku
public class Wallet {
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String address;

    private PublicWallet publicWallet;

    public Wallet() {
        KeyPair keyPair = Cryptography.generateKeyPairForAddress();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        address = Cryptography.generateAddress(this.publicKey);
        this.publicWallet = new PublicWallet(address, Cryptography.publicKeyToString(this.publicKey));
    }
    public Wallet(String privateKeyString,String publicKeyString) {
        // treba bit jako oprezan jer ovaj konstruktor user moze direktno pozivati
        if(privateKeyString == null || privateKeyString.isBlank() || publicKeyString == null || publicKeyString.isBlank()) {
            throw new IllegalArgumentException("Private i public key moraju postojati.");
        }
        try {
            this.privateKey = Cryptography.stringToPrivateKey(privateKeyString);
            this.publicKey = Cryptography.stringToPublicKey(publicKeyString);

            String testData = "MATHOS_WALLET_DATABASE_TEST";
            String testSignature = Cryptography.signData(testData,this.privateKey);

            if(!Cryptography.verifySignature(testData,testSignature,this.publicKey)) {
                throw new IllegalArgumentException("Private i public key ne pripadaju istom walletu.");
            }

            this.address = Cryptography.generateAddress(this.publicKey);
            this.publicWallet = new PublicWallet(
                    this.address,
                    Cryptography.publicKeyToString(this.publicKey)
            );

        } catch(IllegalArgumentException e) {
            throw e;
        } catch(Exception e) {
            throw new IllegalArgumentException("Wallet nije moguce ucitati iz baze.",e);
        }
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

    // dodao sam duplo metode i ovdje da se može i preko public i private walleta
    // raditi iste stvari
    // naravno to blockchain mora potvrditi prije svega tako da je ovo samo tehnička
    // stvar da ne moram mjenjati cijeli kod dodavanjem privatewalleta/publicwalleta
    public long getBalance() {
        return publicWallet.getBalance();
    }

    public void increaseBalance(long amount) {
        publicWallet.increaseBalance(amount);
    }

    public void decreaseBalance(long amount) {
        publicWallet.decreaseBalance(amount);
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

    public PublicWallet getPublicWallet() {
        return publicWallet;
    }

    public String getPublicKeyString() {
        return Cryptography.publicKeyToString(publicKey);
    }

    public Transactions createTransaction(String receiver, long amount, long nonce) {
        String publicKeyString = getPublicKeyString();
        String data = Transactions.buildSigningData(address,publicKeyString,receiver,amount,nonce);
        String signature = signData(data);

        return new Transactions(address,publicKeyString,receiver,amount,signature,nonce);
    }

    public String getPrivateKeyString() {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }
}
/*
 * public void main(String[] args) throws Exception {
 * Wallet wallet = new Wallet();
 * System.out.println("Adresa: " + wallet.getAddress());
 * String message = "Ovo je transakcija";
 * String sig = wallet.signData(message);
 * System.out.println("Potpis validan? " + wallet.verifySignature(message,
 * sig));
 * }
 */
