import java.util.Base64;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

public class Cryptography {


    //bitno je da vraća duljinu izlaza od 64 char
    // secure hash algorithm
    // https://en.wikipedia.org/wiki/SHA-2
    public static String applySHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b); //pretvara byte u pozitivni broj 0-255
                if (hex.length() == 1) hexString.append('0'); //ako je broj duljine 1, dodajemo 0 na pocetak
                hexString.append(hex);
            }

            return hexString.toString(); // na kraju svi se hex djelovi spajaju i vraća se string koji se išćita iz toga
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    // Digital signature algorithm
    // ECDSA algoritam koristi eliptičke krivulje, a DSA koristi problem diskretnog logaritma i grupe
    // ECDSA je standard pa ću koristiti njega
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC"); // EC algoritam
        keyGen.initialize(256);
        return keyGen.generateKeyPair();
    }

    public static String signData(String data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes("UTF-8"));
        byte[] signedBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signedBytes);
    }

    // Provjera potpisa javnim ključem
    public static boolean verifySignature(String data, String signatureStr, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes("UTF-8"));
        byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
        return signature.verify(signatureBytes);
    }

    public static KeyPair generateKeyPairForAddress() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC"); // služit će i za potpisivanje
            //ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1"); // ovo je Bitcoin standard secp256r1
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1"); // ovo zbog jednostavnosti uzimam jer radi u čistoj Javi
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            return keyPair;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // pretvara javni ključ u blockchain adresu
    public static String generateAddress(PublicKey publicKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] firstHash = sha256.digest(publicKey.getEncoded());

            // drugi SHA-256 (simulira RIPEMD160) kojega Java ne podržava, a netrivijalan je i beskoristan za ovaj projekt zato što ima većih problema
            byte[] secondHash = sha256.digest(firstHash);

            // Uzmemo prvih 20 bajtova
            byte[] shortened = new byte[20];
            for (int i = 0; i < 20; i++) {
                shortened[i] = secondHash[i];
            }

            // Base58Check se korisit u Bitcoinu, ali može i ovako samo ćemo morati imati = na kraju ):
            return Base64.getEncoder().encodeToString(shortened);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // gdb --version
}
