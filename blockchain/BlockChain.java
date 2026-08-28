import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockChain {
    private ArrayList<Block> chain;
    private ArrayList<Computer> validatorNodes;
    private Map<String, Wallet> walletRegistry; // adresa i wallet par
    private ArrayList<String> adresa_walleta; // samo poslagane adrese iz prethodne mape da se zna tko je prvi
    private List<Transactions> transactionPool;
    private List<Transactions> currentMiningBatch;
    public int difficulty;
    public boolean miningInProgress;
    private long lastMineTime = 0;
    private Wallet systemWallet;

    public BlockChain() {
        this.chain = new ArrayList<>();
        // kreiranje prvog bloka (genesis :puff:)
        chain.add(createGenesisBlock());
        validatorNodes = new ArrayList<>();
        this.difficulty = 4;
        walletRegistry = new HashMap<>();
        transactionPool = new ArrayList<>();
        adresa_walleta = new ArrayList<>();
        currentMiningBatch = new ArrayList<>();
        miningInProgress = false;

        systemWallet = new Wallet();
        walletRegistry.put(systemWallet.getAddress(), systemWallet);
        adresa_walleta.add(systemWallet.getAddress());
        systemWallet.increaseBalance(1000000);
        System.out.println("System wallet kreiran: " + systemWallet.getAddress());
    }

    public Wallet getSystemWallet() {
        return systemWallet;
    }

    public ArrayList<String> getAdreseWalleta() {
        return adresa_walleta;
    }

    private Block createGenesisBlock() {
        List<Transactions> prvaTransakcija = new ArrayList<>();
        Block genesisBlock = new Block(0, "0", System.currentTimeMillis(), prvaTransakcija, 0);
        return genesisBlock;
    }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    public void addBlock(Block newBlock) {
        if (isBlockValid(newBlock)) {
            newBlock.setPreviousHash(getLatestBlock().hash);
            newBlock.calculateBlockHash();
            chain.add(newBlock);

            // i sada treba još sve AŽURIRATI oduzeti i dodati money
            // sada smo sigurni da nitko ne krade money

            for (Transactions tx : newBlock.getTransactions()) {
                Wallet senderWallet = walletRegistry.get(tx.getSender());
                Wallet receiverWallet = walletRegistry.get(tx.getReceiver());

                if (senderWallet != null) {
                    double fee = calculate_fee(tx.getAmount());
                    senderWallet.decreaseBalance(tx.getAmount() + fee);
                    for (Computer c : validatorNodes) {
                        if (validatorNodes.size() != 0) {
                            double fee_per_computer = fee / validatorNodes.size();
                            Wallet recWallet = walletRegistry.get(c.getAddress());
                            recWallet.increaseBalance(fee_per_computer);
                        }
                    }
                }
                if (receiverWallet != null) {
                    receiverWallet.increaseBalance(tx.getAmount());
                }
            }
        } else {
            System.out.println("Block ne valja!");
        }
    }

    private double calculate_fee(double amount) {
        return amount * 0.001; // 0.1% je fee na iznos koji se šalje, npr ako se šalje 100 plaća se 100.1
    }

    private boolean isBlockValid(Block newBlock) {
        // 4 provjere radimo

        // je li ulančano uopće
        if (!newBlock.previousHash.equals(getLatestBlock().hash)) {
            System.out.println("nije ulančano");
            return false;
        }

        // je li osoba izračunala dobro hash ili je dala neki random sa puno nula
        if (!newBlock.calculateBlockHash().equals(newBlock.hash)) {
            System.out.println("random hash");
            return false;
        }

        // 3. provjera PoW (laže li o broju nula)
        String target = new String(new char[difficulty]).replace('\0', '0');
        if (!newBlock.hash.substring(0, difficulty).equals(target)) {
            System.out.println("nije pow");
            return false;
        }
        // 4. Provjera jesu li transakcije dobre po zadnji puta.
        if (!transactionsCheck(newBlock)) {
            System.out.println("transakcije ne valjaju ipak ):");
            return false;
        }
        return true;

        /*
         * for (Transactions tx : newBlock.getTransactions()) {
         * if (!tx.verifySignature()) {
         * return false;
         * }
         * }
         */
    }

    public Wallet registerWallet() {
        // na indexu 0 je system
        Wallet wallet = new Wallet();
        walletRegistry.put(wallet.getAddress(), wallet);
        System.out.println("Novi wallet registriran: " + wallet.getAddress());
        adresa_walleta.add(wallet.getAddress());
        return wallet;
    }

    public Map<String, Wallet> getWalletRegistry() {
        return walletRegistry;
    }

    private boolean transactionsCheck(Block newBlock) { // provjerava se jesu li transakcije dobro odrađene u ovome
                                                        // blocku prije nego što se izloži za mine
        for (Transactions tx : newBlock.getTransactions()) {
            Wallet senderWallet = walletRegistry.get(tx.getSender());

            if (senderWallet == null) {
                System.out.println("Nepoznata adresa: " + tx.getSender());
                return false;
            }

            // Provjera balansa
            if (senderWallet.getBalance() < tx.getAmount()+calculate_fee(tx.getAmount())) {
                System.out.println("Posiljatelj nema dovoljno sredstava: " + tx.getSender());
                return false;
            }
            // je li negativno (min koliko se moze poslati)
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

    public boolean isChainValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            // provjera jesu li točno izračunati hashevi hasha
            if (!current.hash.equals(current.calculateBlockHash())) {
                System.out.println("Hash bloka " + i + " nije valjan.");
                return false;
            }

            // provjera hash veze (povezanost)
            if (!current.getPreviousHash().equals(previous.hash)) {
                System.out.println("Previous hash bloka " + i + " nije valjan.");
                return false;
            }
        }
        return true;
    }

    public ArrayList<Block> getChain() {
        return chain;
    }

    public void addValidatorNode(Computer validatorNode) {
        validatorNodes.add(validatorNode);
    }

    public ArrayList<Computer> getValidatorNodes() {
        return validatorNodes;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getDifficulty() {
        return this.difficulty;
    }

    public void while_active() {
        while (true) {
            // Do something

        }
    }

    public boolean hasPendingTransactions() {
        return !transactionPool.isEmpty();
    }

    public boolean IsMiningInProgress() {
        return miningInProgress;
    }

    // ideja je da se kopa koliko god treba, a da se prije rudarenja čeka 30 sekundi
    // da ljudi pošalju svoje transakcije
    public void minePendingTransactions(String minerAddress) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMineTime < 30000) { // 30 sekundi = 30000 ms
            long remaining = (30000 - (currentTime - lastMineTime)) / 1000;
            System.out.println("Jos " + remaining + " sekundi do iduceg rudarenja.");
            return; // prekidamo rudarenje jer još nije vrijeme
        }

        lastMineTime = currentTime; // postavljamo vrijeme rudarenja
        miningInProgress = true;

        currentMiningBatch = new ArrayList<>(transactionPool);

        Transactions rewardTx = new Transactions(
                getSystemWallet().getAddress(),
                minerAddress,
                10.0,
                getSystemWallet()
                        .signData(getSystemWallet().getAddress() + minerAddress + "10.0")); // 10 BTC nagrada ako se
                                                                                            // uspješno majna

        // addPendingTransaction(rewardTx);
        currentMiningBatch.add(rewardTx);

        getWalletRegistry().get(adresa_walleta.get(0))
                .signData(adresa_walleta.get(0) + minerAddress + "10.0");

        // prije nego se napravi block izbacujemo sve transakcije koje su nepotvrđene
        ArrayList<Transactions> approvedTransactions = new ArrayList<>();
        ArrayList<Transactions> unapprovedTransactions = new ArrayList<>();
        for (Transactions tx : currentMiningBatch) {
            if (isTransactionApproved_Full(tx)) {
                approvedTransactions.add(tx);
            } else {
                unapprovedTransactions.add(tx);
                System.out.println(" Transakcija odbijena: " + tx.getSender() + " -> " + tx.getReceiver() + " ("
                        + tx.getAmount() + ")");
            }
        }

        Block newBlock = new Block(getLatestBlock().index + 1, getLatestBlock().hash,
                System.currentTimeMillis(),
                approvedTransactions, 0);

        newBlock.mineBlock(difficulty);

        if (isBlockValid(newBlock)) {
            addBlock(newBlock);
            System.out.println("Blok iskopan on ga iskopao: " + minerAddress);

            int indexTx = 0;
            for (Transactions tx : approvedTransactions) {
                MerkleTree mt = new MerkleTree();
                if (!isTransactionApproved_Light(tx, newBlock, mt.getMerkleProof(newBlock.getTransactionsToStringHashs(), indexTx), indexTx)) {

                    System.out.println("Light nodovi odbacili transakciju: " + tx.getSender() +
                            " -> " + tx.getReceiver() + " (" + tx.getAmount() + ")");
                }
                else {
                    System.out.println("Light nodovi prihvatili transakciju: " + tx.getSender() +
                            " -> " + tx.getReceiver() + " (" + tx.getAmount() + ")");
                }
                indexTx++;
            }

            // isprazni pending transakcije
            transactionPool.removeAll(currentMiningBatch);
            transactionPool.removeAll(unapprovedTransactions);
            currentMiningBatch.clear();
            miningInProgress = false;
        } else {
            System.out.println("Block nije validan");
        }
        transactionPool.removeAll(unapprovedTransactions);
    }

    private boolean isTransactionApproved_Full(Transactions tx) {
        if (validatorNodes.isEmpty()) {
            System.out.println("nema validatora, ne moze se potvrditi");
            return false; // nema validatora, ne može se potvrditi
        }

        int approvals = 0;
        int ukupno = 0;

        for (Computer validator : validatorNodes) { // svatko za sebe provjeri je li dobro
            if(validator.getType() == Computer.NodeType.MINER || validator.getType() == Computer.NodeType.FULL) {
                if (validator.validateTransaction(tx)) {
                approvals++;
            }
            ukupno++;
            }
        }

        // mora biti >= 2/3 validatora
        return approvals >= Math.ceil(ukupno * (2.0 / 3.0));
    }

    private boolean isTransactionApproved_Light(Transactions tx, Block block, List<String> proof, int txIndex) {
        if (validatorNodes.isEmpty()) {
            System.out.println("nema validatora, ne moze se potvrditi");
            return false; // nema validatora, ne može se potvrditi
        }

        int approvals = 0;
        int ukupno = 0;
        
        for (Computer validator : validatorNodes) { // svatko za sebe provjeri je li dobro
            if(validator.getType() == Computer.NodeType.LIGHT) {
                if (validator.validateTransaction(tx,block,proof,txIndex)) {
                approvals++;
            }
            ukupno++;
            }
        }

        // mora biti >= 2/3 validatora
        return approvals >= Math.ceil(ukupno * (2.0 / 3.0));
    }

    public void addPendingTransaction(Transactions tx) {
        System.out.println("dodana transakcija");
        transactionPool.add(tx);
    }

    public void printBlockchain() {
        System.out.println("\n Trenutni blockchain:");
        for (int i = 0; i < chain.size(); i++) {
            Block block = chain.get(i);
            System.out.println("--------- Blok #" + i + " ---------");
            System.out.println("Podaci: " + block.data);
            System.out.println("Hash: " + block.hash);
            System.out.println("Prethodni hash: " + block.previousHash);
            System.out.println("Nonce: " + block.nonce);
            System.out.println("Vrijeme: " + block.timestamp);
        }
    }

    public void printAllWallets() {
        /*System.out.println("\nSvi walleti:");
        getWalletRegistry().forEach(
                (address, wallet) -> System.out.println("Adresa: " + address + " | Balance: " + wallet.getBalance()));*/
        System.out.println("\nSvi walleti:");
        for(String address : adresa_walleta) {
            System.out.println("Adresa: "+address+" | Balance: "+getWalletRegistry().get(address).getBalance());
        }
    }

    public void printAllWallets_DETAL() {
        System.out.println("\nSvi walleti DETAL:");
        for(String address : adresa_walleta) {
            System.out.println("Adresa: "+address+" | Balance: "+getWalletRegistry().get(address).getBalance()+" | Public key hash: "
            +Cryptography.applySHA256(getWalletRegistry().get(address).getPublicKey().toString()) + " | Private key hash: " + Cryptography.applySHA256(getWalletRegistry().get(address).getPrivateKey().toString()));
        }
    }
}
