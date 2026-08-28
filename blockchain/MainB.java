import java.security.KeyPair;
import java.security.MessageDigest;

public class MainB {
    public static void main(String[] args) throws Exception {

        Cryptography c = new Cryptography();
        System.out.println(c.applySHA256("blockdwad11"));
        System.out.println("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb".length());

        KeyPair keyPair = Cryptography.generateKeyPair();
        //System.out.println(keyPair.getPrivate().hashCode());
        //System.out.println(keyPair.getPublic().hashCode());

        String string = "Ovo pisem ja i samo ja!!!";
        String potpis = Cryptography.signData(string, keyPair.getPrivate());

        System.out.println("Potpis: "+potpis);
        System.out.println("Provjera s pravim public kljucem: " + Cryptography.verifySignature(string, potpis, keyPair.getPublic()));

        KeyPair keyPairFejkara = Cryptography.generateKeyPair();
        System.out.println("Provjera s Oliverovim public kljucem: " + Cryptography.verifySignature(string, potpis, keyPairFejkara.getPublic()));
        



    }
}
