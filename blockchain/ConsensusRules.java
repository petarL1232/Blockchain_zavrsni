import java.util.Map;

public final class ConsensusRules {
    public static final long MINING_REWARD = 10L * Money.UNITS_PER_COIN;
    public static final long MIN_TRANSACTION_AMOUNT = 10_000L;

    private ConsensusRules() {
    }

    public static long calculateFee(long amount) {
        return Math.max(1L, amount / 1000L);
    }

    public static boolean isRegularTransactionValid(Transactions tx, Map<String, PublicWallet> walletRegistry) {
        if(tx == null || tx.isSystemTransaction()) {
            return false;
        }
        if(!walletRegistry.containsKey(tx.getSender())) {
            return false;
        }
        if(tx.getAmount() < MIN_TRANSACTION_AMOUNT) {
            return false;
        }
        return tx.verifySignature();
    }

    public static boolean isCoinbaseValid(Transactions tx, int transactionIndex, Map<String, PublicWallet> walletRegistry) {
        if(tx == null || !tx.isSystemTransaction()) {
            return false;
        }
        if(transactionIndex != 0 || tx.getAmount() != MINING_REWARD) {
            return false;
        }
        if(tx.getSignature() != null) {
            return false;
        }
        return walletRegistry.containsKey(tx.getReceiver());
    }

    public static boolean isBlockLinkedTo(Block block, Block previousBlock) {
        return block != null && previousBlock != null && isHashLinkedTo(block.previousHash,previousBlock.hash);
    }

    public static boolean isHashLinkedTo(String previousHash, String previousBlockHash) {
        return previousBlockHash != null && previousBlockHash.equals(previousHash);
    }

    public static boolean isMerkleRootValid(Block block) {
        return block != null && block.calculateMerkleRoot().equals(block.getMerkleRoot());
    }

    public static boolean isBlockHashValid(Block block) {
        return block != null && isHeaderHashValid(block.index,block.previousHash,block.timestamp,block.getMerkleRoot(),block.nonce,block.hash);
    }

    public static boolean isHeaderHashValid(int index, String previousHash, long timestamp, String merkleRoot, long nonce, String hash) {
        return hash != null && hash.equals(Block.calculateHeaderHash(index,previousHash,timestamp,merkleRoot,nonce));
    }

    public static boolean isProofOfWorkValid(Block block, int difficulty) {
        return block != null && isProofOfWorkValid(block.hash,difficulty);
    }

    public static boolean isProofOfWorkValid(String hash, int difficulty) {
        if(difficulty < 0 || difficulty > 64 || hash == null) {
            return false;
        }
        return hash.startsWith("0".repeat(difficulty));
    }

    public static boolean hasEnoughApprovals(int approvals, int validators) {
        if(validators == 0) {
            return false;
        }
        int requiredApprovals = (2 * validators + 2) / 3;
        return approvals >= requiredApprovals;
    }
}
