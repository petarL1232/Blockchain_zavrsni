import WLAN.BlockPayload;
import WLAN.HeaderPayload;
import WLAN.TransactionPayload;

import java.util.*;

public final class NetworkMapper { // final !!
    private NetworkMapper() {
    }

    /*
     * public NetworkMapper() {
     * 
     * }
     */
    public static TransactionPayload transactionToPayload(Transactions transaction) {

        if (transaction == null) {
            throw new IllegalArgumentException("Ne smije biti null transakcija puff");
            // return null; ili cemo ovo... ovisno o gui kako bude bio
        }

        String transactionType;

        if (transaction.isSystemTransaction()) {
            transactionType = "SYSTEM";
        } else {
            transactionType = "REGULAR";
        }

        return new TransactionPayload(transaction.getHash(), transaction.getSender(), transaction.getSenderPublicKey(),
                transaction.getReceiver(), transaction.getAmount(), transaction.getSignature(), transaction.getNonce(), transactionType);

    }

    public static Transactions payloadToTransactions(TransactionPayload transactionPayload) {
        if (transactionPayload == null) {
            throw new IllegalArgumentException("Ne smije biti null payload puff");
            // return null; ili cemo ovo... ovisno o gui kako bude bio
        }

        Transactions transaction;

        if (("REGULAR".equals(transactionPayload.getTransactionType()))) {
            transaction = new Transactions(transactionPayload.getSender(), transactionPayload.getSenderPublicKey(),
                    transactionPayload.getReceiver(), transactionPayload.getAmount(),
                    transactionPayload.getSignature(), transactionPayload.getNonce());
        } else if ("SYSTEM".equals(transactionPayload.getTransactionType())) {
            transaction = Transactions.createSystemTransaction(transactionPayload.getReceiver(),
                    transactionPayload.getAmount(), transactionPayload.getNonce()); // ovdje mozda umjesto amount staviti konstantu za system reward.. ipak ne
        } else {
            throw new IllegalArgumentException("NEpoznati transaction type");
        }

        if (!transaction.getHash().equals(transactionPayload.getTransactionId())) {
            throw new IllegalArgumentException("Transaction ID ne odgovara sadržaju transakcije.");
        }

        return transaction;
    }

    public static BlockPayload blockToPayload(Block block) {

        if (block == null) {
            throw new IllegalArgumentException("Ne smije biti null block puff");
            // return null; ili cemo ovo... ovisno o gui kako bude bio
        }
        List<TransactionPayload> transactionsPayloads = new ArrayList<>();

        for (Transactions transaction : block.getTransactions()) {
            transactionsPayloads.add(transactionToPayload(transaction));
        }

        return new BlockPayload(block.index, block.previousHash, block.timestamp, transactionsPayloads, block.nonce,
                block.hash, block.merkleRoot, block.getDifficulty());

    }

    public static Block payloadToBlock(BlockPayload blockPayload) {
        if (blockPayload == null) {
            throw new IllegalArgumentException("Ne smije biti null blockpayload puff");
            // return null; ili cemo ovo... ovisno o gui kako bude bio
        }

        List<Transactions> transactions = new ArrayList<>();

        for (TransactionPayload transactionPayload : blockPayload.getTransactions()) {
            transactions.add(payloadToTransactions(transactionPayload));
        }

        Block block = new Block(
                blockPayload.getIndex(),
                blockPayload.getPreviousHash(),
                blockPayload.getTimestamp(),
                transactions,
                blockPayload.getNonce(),
                blockPayload.getDifficulty());

        // tu su vrijednosti koje je peer stvarno poslao. Blockchain ih nakon toga mora
        // samostalno provjeriti.
        block.merkleRoot = blockPayload.getMerkleRoot();
        block.hash = blockPayload.getHash();

        return block;

    }

    public static HeaderPayload blockToHeaderPayload(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Ne smije biti null block puff");
        }

        return new HeaderPayload(
                block.index,
                block.previousHash,
                block.getMerkleRoot(),
                block.timestamp,
                block.hash,
                block.nonce,
                block.getDifficulty());
    }

    public static HeaderPayload headerToPayload(BlockChain_LightNodes.BlockHeader header) {
        if (header == null) {
            throw new IllegalArgumentException("Ne smije biti null header puff");
        }

        return new HeaderPayload(
                header.height,
                header.previousHash,
                header.merkleRoot,
                header.timestamp,
                header.blockHash,
                header.nonce,
                header.difficulty);
    }

    public static BlockChain_LightNodes.BlockHeader payloadToHeader(HeaderPayload headerPayload) {
        if (headerPayload == null) {
            throw new IllegalArgumentException("Ne smije biti null header payload puff");
        }

        return new BlockChain_LightNodes.BlockHeader(
                headerPayload.getHeight(),
                headerPayload.getPreviousHash(),
                headerPayload.getMerkleRoot(),
                headerPayload.getTimestamp(),
                headerPayload.getBlockHash(),
                headerPayload.getNonce(),
                headerPayload.getDifficulty());
    }

    public static BlockChain_LightNodes.BlockHeader blockPayloadToHeader(BlockPayload blockPayload) {
        if (blockPayload == null) {
            throw new IllegalArgumentException("Ne smije biti null block payload puff");
        }

        return new BlockChain_LightNodes.BlockHeader(
                blockPayload.getIndex(),
                blockPayload.getPreviousHash(),
                blockPayload.getMerkleRoot(),
                blockPayload.getTimestamp(),
                blockPayload.getHash(),
                blockPayload.getNonce(),
                blockPayload.getDifficulty());
    }
}
