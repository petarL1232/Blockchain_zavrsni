import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;

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
            return Block.calculateHeaderHash(height, previousHash, timestamp, merkleRoot, nonce, difficulty);
        }

    }

    // dodavanje novog headera (npr. kada light node synca s full nodom)
    public synchronized boolean addBlockHeader(BlockHeader header) {
        if (header == null
                || header.height < 0
                || header.previousHash == null
                || header.merkleRoot == null
                || header.blockHash == null) {
            return false;
        }

        if ((header.height == 0 && header.difficulty != 0)
                || (header.height > 0
                && (header.difficulty < 1 || header.difficulty > 64))) {
            return false;
        }

        if (header.height < headers.size()) {
            return headers.get(header.height).blockHash.equals(header.blockHash);
        }

        if (header.height != headers.size()) {
            return false;
        }

        if (!ConsensusRules.isHeaderHashValid(header.height,header.previousHash,header.timestamp,header.merkleRoot,header.nonce,header.blockHash,header.difficulty)) {
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
            if (header.height > 1
                    && Math.abs(header.difficulty - previousHeader.difficulty) > 1) {
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

    public synchronized BlockHeader getHeader(String blockHash) {
        if (blockHash == null || blockHash.isBlank()) {
            return null;
        }

        for (BlockHeader header : headers) {
            if (blockHash.equals(header.blockHash)) {
                return header;
            }
        }

        return null;
    }

    public synchronized ArrayList<BlockHeader> getHeadersSnapshot() {
        return new ArrayList<>(headers);
    }

    public synchronized int size() {
        return headers.size();
    }

    public synchronized BigInteger getCumulativeWork() {
        return calculateCumulativeWork(headers);
    }

    public static BigInteger calculateCumulativeWork(List<BlockHeader> blockHeaders) {
        BigInteger cumulativeWork = BigInteger.ZERO;

        if (blockHeaders == null) {
            return cumulativeWork;
        }

        for (int i = 1; i < blockHeaders.size(); i++) {
            int blockDifficulty = blockHeaders.get(i).difficulty;

            if (blockDifficulty < 1 || blockDifficulty > 64) {
                throw new IllegalArgumentException("Block header ima neispravan difficulty.");
            }

            cumulativeWork = cumulativeWork.add(
                    BigInteger.ONE.shiftLeft(blockDifficulty * 4));
        }

        return cumulativeWork;
    }

    public synchronized boolean replaceHeadersIfStronger(List<BlockHeader> candidateHeaders) {
        if (candidateHeaders == null || candidateHeaders.isEmpty()) {
            return false;
        }

        if (!headers.isEmpty()
                && !headers.get(0).blockHash.equals(candidateHeaders.get(0).blockHash)) {
            return false;
        }

        BlockChain_LightNodes candidateBlockchain = new BlockChain_LightNodes();

        for (BlockHeader header : candidateHeaders) {
            if (!candidateBlockchain.addBlockHeader(header)) {
                return false;
            }
        }

        if (candidateBlockchain.getCumulativeWork().compareTo(getCumulativeWork()) <= 0) {
            return false;
        }

        headers = candidateBlockchain.getHeadersSnapshot();
        return true;
    }

    public synchronized boolean isChainValid() {
        if (headers.isEmpty()) {
            return false;
        }

        BlockChain_LightNodes validationBlockchain = new BlockChain_LightNodes();

        for (BlockHeader header : headers) {
            if (!validationBlockchain.addBlockHeader(header)) {
                return false;
            }
        }

        return true;
    }

    // Prikaz svih headera
    public synchronized void printHeaders() {
        System.out.println("=== Light Node Blockchain Headers ===");
        for (BlockHeader h : headers) {
            System.out.println(h);
        }
    }

    // provjera transakcije pomoću Merkle proof-a
    public synchronized boolean verifyTransaction(String transaction, List<String> proof, String merkleRoot, int index) {
        if (transaction == null
                || proof == null
                || merkleRoot == null
                || index < 0) {
            return false;
        }

        return merkleTree.verifyMerkleProof(transaction, proof, merkleRoot, index);
    }

    // Dohvati zadnji header
    public synchronized BlockHeader getLastHeader() {
        return headers.isEmpty() ? null : headers.get(headers.size() - 1);
    }

}
