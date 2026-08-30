import java.security.MessageDigest;
import java.security.Timestamp;
import java.sql.Time;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Block {
    public int index;
    public String previousHash;
    public long timestamp;
    public List<Transactions> data;
    public long nonce;
    public String hash;
    public String merkleRoot; // za Light nodove ovo je nužno dodati

    public Block(int index, String previousHash, long timestamp, List<Transactions> data, int nonce) {
        this.index = index; // koji je to blok po redu, trebat će za određivanje brzine mininga
        this.previousHash = previousHash; // opća svrha blockchaina
        this.timestamp = timestamp; // vrijeme u ms od 1970
        this.data = data; // podaci koji se čuvaju u bloku u ovome slučaju transakcije
        this.nonce = nonce; // slučajni br koji se podešava služi za mine
        this.merkleRoot = calculateMerkleRoot();
        this.hash = calculateBlockHash(); // hash ovoga
    }

    public String calculateBlockHash() {
        String input = (index + "|") + (previousHash + "|") + (timestamp + "|") + (transactionsToString() + "|") + (merkleRoot + "|")
                + (nonce + ""); // popraviti transactionsToString
        return Cryptography.applySHA256(input);

    }

    public long mineBlock(int difficulty) {
        String target = "0".repeat(difficulty);
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateBlockHash();
        }
        System.out.println("!!! Blok iskopan! Nonce: " + nonce + " Hash: " + hash);
        return nonce;
    }

    public void setPreviousHash(String BlockHash) {

        this.previousHash = BlockHash;
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method
        // 'setPreviousHash'");
    }

    public Object getPreviousHash() {
        return this.previousHash;
    }

    public void addTransaction() {
        // TODO Auto-generated method stub
    }

    public List<Transactions> getTransactions() {
        return this.data;
    }

    public String transactionsToString() {
        StringBuilder sb = new StringBuilder();
        for (Transactions t : data) {
            sb.append(t.getSender())
                    .append(t.getReceiver())
                    .append(t.getAmount());
        }
        return sb.toString();
    }

    public String calculateMerkleRoot() {
        MerkleTree mt = new MerkleTree();
        List<String> transactionsStringHashs = new ArrayList<>();
        for (Transactions t : getTransactions()) {
            transactionsStringHashs.add(t.getHash());
        }
        return mt.getMerkleRoot(transactionsStringHashs);
        // TODO Auto-generated method stub
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public List<String> getTransactionsToStringHashs() {
        List<String> transactionsStringHashs = new ArrayList<>();

        for (Transactions t : getTransactions()) {
            transactionsStringHashs.add(t.getHash());
        }

        return transactionsStringHashs;
    }

}
