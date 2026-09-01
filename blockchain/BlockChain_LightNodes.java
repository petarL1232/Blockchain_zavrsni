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
        public long nonce;
        public int difficulty;

        public BlockHeader(int height, String previousHash, String merkleRoot, long timestamp, String blockHash, long nonce, int difficulty) {
            this.height = height;
            this.previousHash = previousHash;
            this.merkleRoot = merkleRoot;
            this.timestamp = timestamp;
            this.blockHash = blockHash;
            this.nonce = nonce;
            this.difficulty = difficulty;
        }

        @Override
        public String toString() {
            return "Block #" + height + " | Hash: " + blockHash + " | Prev: " + previousHash + " | Merkle: "
                    + merkleRoot;
        }

        public String calculateHash() {
            return Block.calculateHeaderHash(height, previousHash, timestamp, merkleRoot, nonce);
        }

    }

    // dodavanje novog headera (npr. kada light node synca s full nodom)
    public synchronized boolean addBlockHeader(BlockHeader header) {
        if (header.height < headers.size()) {
            return headers.get(header.height).blockHash.equals(header.blockHash);
        }

        if (header.height != headers.size()) {
            return false;
        }

        if (!ConsensusRules.isHeaderHashValid(header.height,header.previousHash,header.timestamp,header.merkleRoot,header.nonce,header.blockHash)) {
            return false;
        }

        if (header.height == 0) {
            if (!header.previousHash.equals("0")) {
                return false;
            }
        } else {
            BlockHeader previousHeader = headers.get(headers.size() - 1);
            if (!ConsensusRules.isHashLinkedTo(header.previousHash,previousHeader.blockHash)) {
                return false;
            }
            if (!ConsensusRules.isProofOfWorkValid(header.blockHash,header.difficulty)) {
                return false;
            }
        }
        // ako sve provjere prođu dodaj header na kraju
        headers.add(header);
        return true;
    }

    public synchronized BlockHeader getHeader(int height) {
        if (height < 0 || height >= headers.size()) {
            return null;
        }

        return headers.get(height);
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
