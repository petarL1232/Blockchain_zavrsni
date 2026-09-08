//ovdje će biti implementiran virtualni kompjuter koji će sudjelovati u mreži

import java.security.PublicKey;
import java.util.List;

public class Computer implements Runnable {

    public enum NodeType {
        FULL,
        LIGHT,
        MINER
    }

    private NodeType uloga;
    private String address;
    private BlockChain blockchain;
    public volatile boolean running = true;

    private BlockChain_LightNodes lightBlockchain;

    public Computer(NodeType uloga, String address, BlockChain blockchain) {
        this.uloga = uloga;
        this.address = address;
        this.blockchain = blockchain;

        if (uloga == NodeType.LIGHT) {
            lightBlockchain = new BlockChain_LightNodes();
            syncBlockchain();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                switch (uloga) {
                    case MINER:
                        if (blockchain.hasPendingTransactions()) {
                            System.out.println("[" + address + "] is waiting for mining round puff");
                            blockchain.minePendingTransactions(address); // metoda koja mine i dodaje blok
                        } else {
                            System.out.println("[" + address + "] no transactions/blocks to mine, Im waiting ):");
                        }
                        break;

                    case FULL:
                        System.out.println("[" + address + "] Provjera i sinkronizacija cijelog blockchaina...");
                        syncBlockchain();
                        break;

                    case LIGHT:
                        System.out.println("[" + address + "] Sinkroniziram samo zaglavlja blokova...");
                        syncBlockchain();
                        break;
                }

                // spavanje između iteracija — da ne bude prebrzo
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                System.out.println("Node " + address + " zaustavljen.");
                running = false;
            }
        }
    }

    /*
     * public void startAutoMining(String minerAddress) {
     * Thread miningThread = new Thread(() -> {
     * while (true) {
     * try {
     * if (blockchain.hasPendingTransactions()) {
     * System.out.println("Čekam 30 sekundi prije rudarenja...):");
     * Thread.sleep(30000); // čekaj 30 sekundi
     * 
     * System.out.println("Pokrećem rudarenje...");
     * blockchain.minePendingTransactions(minerAddress);
     * } else {
     * System.out.println("Nema transakcija, preskačem rudarenje");
     * Thread.sleep(5000); // provjeri opet za 5 sekundi
     * }
     * } catch (InterruptedException e) {
     * System.out.println("Auto-mining zaustavljen");
     * break;
     * }
     * }
     * });
     * 
     * miningThread.start();
     * }/*
     */

    public void stopNode() {
        running = false;
    }

    public void syncBlockchain() {
        if (uloga == NodeType.LIGHT) {
            for (Block block : blockchain.getChain()) {
                if (!receiveBlockHeader(block, block.getDifficulty())) {
                    System.out.println("LIGHT node odbio header bloka: " + block.index);
                    return;
                }
            }

            System.out.println("LIGHT node sinkronizirao headere.");
            return;
        } else {
            // TODO
            System.out.println("Full/Mining node: sinkronizira cijeli blockchain.");
        }
    }

    public boolean validateTransaction(Transactions tx, Block block, List<String> proof, int txIndex) {
        if (uloga == NodeType.LIGHT) { // dakle, on samo provjerava lažu li mu full nodeovi preko merkle roota
            BlockChain_LightNodes.BlockHeader header = lightBlockchain.getHeader(block.index);
            if (header == null) {
                System.out.println("LIGHT node nema header ovog bloka.");
                return false;
            }

            if (!header.blockHash.equals(block.hash) || !header.merkleRoot.equals(block.getMerkleRoot())) {

                System.out.println("Blok se ne podudara sa spremljenim headerom.");
                return false;
            }

            String hashTx = tx.getHash();

            MerkleTree merkleTree = new MerkleTree();

            return merkleTree.verifyMerkleProof(hashTx,proof,header.merkleRoot,txIndex);

        } else {
            // Provjera balansa
            // ovo se sada state-aware provjerava u BlockChain klasi preko temporaryBalances
            /*if (senderWallet.getBalance() < tx.getAmount()) {
                System.out.println("Posiljatelj nema dovoljno sredstava: " + tx.getSender());
                return false;
            }*/
            // Provjera potpisa
            // String data = tx.getSender() + tx.getReceiver() + tx.getAmount();
            // adresa, minimalni iznos i potpis se provjeravaju na jednom mjestu
            return ConsensusRules.isRegularTransactionValid(tx,blockchain.getPublicWalletRegistry());
        }
    }

    public boolean validateTransaction(Transactions tx) {
        if (uloga == NodeType.LIGHT) { // dakle, on samo provjerava lažu li mu full nodeovi preko merkle roota
            System.out.println("OVO SE NIJE TREBALO AKTIVIRATI!");
        } else {
            // Provjera balansa
            // ovo se sada state-aware provjerava u BlockChain klasi preko temporaryBalances
            /*if (senderWallet.getBalance() < tx.getAmount()) {
                System.out.println("Posiljatelj nema dovoljno sredstava: " + tx.getSender());
                return false;
            }*/

            // Provjera potpisa
            // String data = tx.getSender() + tx.getReceiver() + tx.getAmount();
            // adresa, minimalni iznos i potpis se provjeravaju na jednom mjestu
            return ConsensusRules.isRegularTransactionValid(tx,blockchain.getPublicWalletRegistry());
        }
        return true;
    }

    public void receiveTransaction(Transactions tx) {
        /*try {
            if (tx.verifySignature()) {
                System.out.println("EPIC [" + address + "] Transakcija valjana, dodajem u mempool.");
                blockchain.addPendingTransaction(tx);
            } else {
                System.out.println("!!!![" + address + "] Potpis transakcije nije valjan!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        if(blockchain.addPendingTransaction(tx) && tx.verifySignature()) {
            System.out.println("EPIC [" + address + "] Transakcija valjana, dodajem u mempool.");
        }
        else {
            System.out.println("!!!![" + address + "] Potpis transakcije nije valjan!");
        }   
    }

    public boolean receiveBlockHeader(
            Block block,
            int difficulty) {

        if (uloga != NodeType.LIGHT) {
            return false;
        }

        BlockChain_LightNodes.BlockHeader header = new BlockChain_LightNodes.BlockHeader(
                block.index, block.previousHash, block.getMerkleRoot(), block.timestamp, block.hash, block.nonce,
                difficulty);

        return lightBlockchain.addBlockHeader(header);
    }

    public NodeType getType() {
        return uloga;
    }

    public String getAddress() {
        return address;
    }
    /* */
}
