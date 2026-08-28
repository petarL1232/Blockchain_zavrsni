import java.util.ArrayList;
import java.util.List;

public class BlockChain_LightNodes {

    // light node čuva samo block headere
    private List<BlockHeader> headers = new ArrayList<>();
    private MerkleTree merkleTree = new MerkleTree();

    // Minimalni podaci iz headera
    public static class BlockHeader {
        public int height;
        public String previousHash;
        public String merkleRoot;
        public long timestamp;
        public String blockHash;

        public BlockHeader(int height, String previousHash, String merkleRoot, long timestamp, String blockHash) {
            this.height = height;
            this.previousHash = previousHash;
            this.merkleRoot = merkleRoot;
            this.timestamp = timestamp;
            this.blockHash = blockHash;
        }

        @Override
        public String toString() {
            return "Block #" + height + " | Hash: " + blockHash + " | Prev: " + previousHash + " | Merkle: " + merkleRoot;
        }
    }

    // dodavanje novog headera (npr. kada light node synca s full nodom)
    public void addBlockHeader(BlockHeader header) {
        headers.add(header);
    }

    // Prikaz svih headera
    public void printHeaders() {
        System.out.println("=== Light Node Blockchain Headers ===");
        for (BlockHeader h : headers) {
            System.out.println(h);
        }
    }

    // provjera transakcije pomoću Merkle proof-a
    public boolean verifyTransaction(String transaction, List<String> proof, String merkleRoot, int index) {
        return merkleTree.verifyMerkleProof(transaction, proof, merkleRoot, index);
    }

    // Dohvati zadnji header
    public BlockHeader getLastHeader() {
        return headers.isEmpty() ? null : headers.get(headers.size() - 1);
    }
}
