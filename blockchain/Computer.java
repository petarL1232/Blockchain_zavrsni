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
    public boolean running = true;

    public Computer(NodeType uloga, String address, BlockChain blockchain) {
        this.uloga = uloga;
        this.address = address;
        this.blockchain = blockchain;
    }

    @Override
    public void run() {
        while (running) {
            try {
                switch (uloga) {
                    case MINER:
                        if (blockchain.hasPendingTransactions()) {
                            System.out.println("[" + address + "] is mining a new block");
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
            // TODO
            System.out.println("Light node: sinkronizira samo zaglavlja blokova i Merkle root.");
        } else {
            // TODO
            System.out.println("Full/Mining node: sinkronizira cijeli blockchain.");
        }
    }

    public boolean validateTransaction(Transactions tx, Block block, List<String> proof, int txIndex) {
        if (uloga == NodeType.LIGHT) { // dakle, on samo provjerava lažu li mu full nodeovi preko merkle roota
            String hashTx = tx.getHash();

            MerkleTree merkleTree = new MerkleTree();

            boolean valid = merkleTree.verifyMerkleProof(
                    hashTx,
                    proof,
                    block.getMerkleRoot(),
                    txIndex);

            return valid;

        } else {
            Wallet senderWallet = blockchain.getWalletRegistry().get(tx.getSender());

            if (senderWallet == null) {
                System.out.println("Nepoznata adresa: " + tx.getSender());
                return false;
            }

            // Provjera balansa
            if (senderWallet.getBalance() < tx.getAmount()) {
                System.out.println("Posiljatelj nema dovoljno sredstava: " + tx.getSender());
                return false;
            }
            if(tx.getAmount() < 0.0001) {
                System.out.println("Posiljatelj upisao negativan ili nedovoljan iznos: " + tx.getSender());
                return false;
            }

            // Provjera potpisa
            String data = tx.getSender() + tx.getReceiver() + tx.getAmount();
            if (!senderWallet.verifySignature(data, tx.getSignature())) {
                System.out.println("Neispravan potpis transakcije od: " + tx.getSender());
                return false;
            }
        }
        return true;
    }

    public boolean validateTransaction(Transactions tx) {
        if (uloga == NodeType.LIGHT) { // dakle, on samo provjerava lažu li mu full nodeovi preko merkle roota
            System.out.println("OVO SE NIJE TREBALO AKTIVIRATI!");

        } else {
            Wallet senderWallet = blockchain.getWalletRegistry().get(tx.getSender());

            if (senderWallet == null) {
                System.out.println("Nepoznata adresa: " + tx.getSender());
                return false;
            }
            if(tx.getAmount() < 0.0001) {
                System.out.println("Posiljatelj upisao negativan ili nedovoljan iznos: " + tx.getSender());
                return false;
            }

            // Provjera balansa
            if (senderWallet.getBalance() < tx.getAmount()) {
                System.out.println("Posiljatelj nema dovoljno sredstava: " + tx.getSender());
                return false;
            }

            // Provjera potpisa
            String data = tx.getSender() + tx.getReceiver() + tx.getAmount();
            if (!senderWallet.verifySignature(data, tx.getSignature())) {
                System.out.println("Neispravan potpis transakcije od: " + tx.getSender());
                return false;
            }
        }
        return true;
    }


    public void receiveTransaction(Transactions tx, PublicKey senderKey) {
        try {
            if (tx.verifySignature(senderKey)) {
                System.out.println("✅ [" + address + "] Transakcija valjana, dodajem u mempool.");
                blockchain.addPendingTransaction(tx);
            } else {
                System.out.println("❌ [" + address + "] Potpis transakcije nije valjan!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public NodeType getType() {
        return uloga;
    }

    public String getAddress() {
        return address;
    }
    /* */
}
