import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigInteger;
import java.util.LinkedHashMap; // za fork

public class BlockChain {
    private ArrayList<Block> chain;
    private ArrayList<Computer> validatorNodes;

    private Map<String, PublicWallet> publicWalletRegistry; // adresa i wallet par
    private Map<String, Wallet> privateWalletRegistry;
    private Map<String, Long> initialBalances; // služi samo da se može potvrditi da je cijeli lanac valjan (teoretski
                                               // nebitno na blockchain)
    private Map<String, Long> nextNonces;

    private ArrayList<String> adresa_walleta; // samo poslagane adrese iz prethodne mape da se zna tko je prvi
    private List<Transactions> transactionPool;
    // private List<Transactions> currentMiningBatch; nije thread safe pa ne
    // koristimo vise

    public int difficulty;
    // public boolean miningInProgress; nije thread safe pa ne koristimo vise
    // private long lastMineTime = 0; nije thread safe pa ne koristimo vise
    // private Wallet systemWallet;
    // dvije varijabe za thread-safety
    private final Object transactionPoolLock = new Object(); // thre3adovi nece moci istovremeno promjeniti
                                                             // transactionPool
    private final AtomicInteger activeMiners = new AtomicInteger(0); // Integer koji se safely mijenja između više
                                                                     // threadova

    private static final int GENESIS_INDEX = 0;
    private static final String GENESIS_PREVIOUS_HASH = "0";
    private static final long GENESIS_TIMESTAMP = 0L;
    private static final int GENESIS_NONCE = 0;

    private static class ReplayState {
        private Map<String, Long> balances;
        private Map<String, Long> nonces;

        private ReplayState(Map<String, Long> balances, Map<String, Long> nonces) {
            this.balances = balances;
            this.nonces = nonces;
        }
    }

    public BlockChain() {
        this.chain = new ArrayList<>();
        // kreiranje prvog bloka (genesis :puff:)
        chain.add(createGenesisBlock());
        validatorNodes = new ArrayList<>();
        this.difficulty = 4;
        publicWalletRegistry = new HashMap<>();
        transactionPool = new ArrayList<>();
        adresa_walleta = new ArrayList<>();

        privateWalletRegistry = new HashMap<>(); // ovo se treba pobrinuti da nema nitko pristup u metodama koje će se
                                                 // pozivati
        initialBalances = new HashMap<>();
        nextNonces = new HashMap<>();

        /*
         * systemWallet = new Wallet();
         * walletRegistry.put(systemWallet.getAddress(), systemWallet);
         * adresa_walleta.add(systemWallet.getAddress());
         * systemWallet.increaseBalance(1000000);
         * System.out.println("System wallet kreiran: " + systemWallet.getAddress());
         */
    }

    /*
     * public Wallet getSystemWallet() {
     * return systemWallet;
     * }
     */

    public ArrayList<String> getAdreseWalleta() {
        return adresa_walleta;
    }

    public Map<String, Wallet> getPrivateWalletRegistry() {
        return privateWalletRegistry;
    }

    private Block createGenesisBlock() {
        List<Transactions> prvaTransakcija = new ArrayList<>();
        Block genesisBlock = new Block(GENESIS_INDEX, GENESIS_PREVIOUS_HASH, GENESIS_TIMESTAMP, prvaTransakcija,
                GENESIS_NONCE, 0);
        return genesisBlock;
    }

    private void addBlock(Block newBlock) {
        if (isBlockValid(newBlock)) {
            // newBlock.setPreviousHash(getLatestBlock().hash);
            // newBlock.calculateBlockHash();
            chain.add(newBlock);

            // i sada treba još sve AŽURIRATI oduzeti i dodati money
            // sada smo sigurni da nitko ne krade money

            for (Transactions tx : newBlock.getTransactions()) {
                PublicWallet receiverWallet = publicWalletRegistry.get(tx.getReceiver());
                if (tx.isSystemTransaction()) {
                    receiverWallet.increaseBalance(tx.getAmount());
                    continue;
                }
                PublicWallet senderWallet = publicWalletRegistry.get(tx.getSender());

                if (senderWallet != null) {
                    long fee = calculate_fee(tx.getAmount());
                    senderWallet.decreaseBalance(Math.addExact(tx.getAmount(), fee));
                    for (int i = 0; i < validatorNodes.size(); i++) {
                        if (validatorNodes.size() != 0) {
                            long fee_per_computer = calculateValidatorFeeShare(fee, i);
                            Computer c = validatorNodes.get(i);
                            PublicWallet recWallet = publicWalletRegistry.get(c.getAddress());
                            recWallet.increaseBalance(fee_per_computer);
                        }
                    }
                }
                if (receiverWallet != null) {
                    receiverWallet.increaseBalance(tx.getAmount());
                }
                nextNonces.put(tx.getSender(), Math.incrementExact(nextNonces.get(tx.getSender())));
            }
        } else {
            System.out.println("Block ne valja!");
        }
    }

    /*
     * public synchronized boolean receiveBlock(Block receivedBlock) {
     * 
     * int chainSizeBefore = chain.size();
     * 
     * addBlock(receivedBlock);
     * 
     * return chain.size() == chainSizeBefore + 1;
     * }
     */
    public synchronized boolean receiveBlock(Block receivedBlock) {

        int chainSizeBefore = chain.size();

        addBlock(receivedBlock);

        boolean blockAccepted = chain.size() == chainSizeBefore + 1;

        if (blockAccepted) {
            refreshTransactionPoolAfterBlock(receivedBlock);
        }

        return blockAccepted;
    }

    private void refreshTransactionPoolAfterBlock(Block receivedBlock) {

        synchronized (transactionPoolLock) {
            Set<String> confirmedTransactionIds = new HashSet<>(); // ne dopušta duplikate zbog toga je korisno

            for (Transactions transaction : receivedBlock.getTransactions()) {
                if (!transaction.isSystemTransaction()) {
                    confirmedTransactionIds.add(transaction.getHash());
                }
            }
            transactionPool.removeIf(transaction -> confirmedTransactionIds.contains(transaction.getHash()));

            Map<String, Long> temporaryBalances = createBalanceSnapshot(); // dict
            Map<String, Long> temporaryNonces = createNonceSnapshot();
            ArrayList<Transactions> validPendingTransactions = new ArrayList<>();

            for (Transactions transaction : transactionPool) {

                if (isTransactionValidForMempool(transaction)
                        && applyTransactionToTemporaryState(transaction, temporaryBalances, temporaryNonces)) {

                    validPendingTransactions.add(transaction);
                } else {
                    System.out.println("Transakcija izbačena iz mempoola nakon novog bloka: " + transaction.getHash());
                }
            }
            transactionPool.clear();
            transactionPool.addAll(validPendingTransactions);
        }
    }

    public synchronized boolean hasBlockHash(String blockHash) {

        if (blockHash == null) {
            return false;
        }

        for (Block block : chain) {
            if (blockHash.equals(block.hash)) {
                return true;
            }
        }

        return false;
    }

    private synchronized boolean tryAddMinedBlock(Block newBlock, String parentHash, int miningDifficulty,
            List<Transactions> miningBatch) {
        // Netko je već dodao blok na parent na kojem je rudarenje započelo
        if (!getLatestBlock().hash.equals(parentHash)) {
            return false;
        }
        // Difficulty je promijenjen dok je miner rudario
        if (difficulty != miningDifficulty) {
            return false;
        }
        int previousChainSize = chain.size();
        addBlock(newBlock);
        // addBlock nije prihvatio blok
        if (chain.size() == previousChainSize) {
            return false;
        }
        // Samo pobjednik uklanja transakcije iz poola
        synchronized (transactionPoolLock) {
            transactionPool.removeAll(miningBatch);
        }
        return true;
    }

    private long calculate_fee(long amount) {
        return ConsensusRules.calculateFee(amount); // 0.1% je fee na iznos koji se šalje, npr ako se šalje 100 plaća se
                                                    // 100.1
    }

    private long calculateValidatorFeeShare(long fee, int validatorIndex) {
        long feePerComputer = fee / validatorNodes.size();
        long remainder = fee % validatorNodes.size();
        return feePerComputer + (validatorIndex < remainder ? 1L : 0L);
    }

    private boolean isBlockValid(Block newBlock) {
        // 6 provjera radimo

        // 1. je li ulančano uopće
        if (!ConsensusRules.isBlockLinkedTo(newBlock, getLatestBlock())) {
            System.out.println("nije ulančano");
            return false;
        }

        // 2. je li merkle root dobar
        if (!ConsensusRules.isMerkleRootValid(newBlock)) {
            System.out.println("Merkle root ne odgovara transakcijama u bloku.");
            return false;
        }

        // 3. je li osoba izračunala dobro hash ili je dala neki random sa puno nula
        if (!ConsensusRules.isBlockHashValid(newBlock)) {
            System.out.println("random hash");
            return false;
        }

        // 6. difficulty jel laže
        if (newBlock.getDifficulty() != difficulty) {
            System.out.println("Difficulty bloka nije očekivani network difficulty.");
            return false;
        }

        // 4. provjera PoW (laže li o broju nula)
        if (!ConsensusRules.isProofOfWorkValid(newBlock, difficulty)) {
            System.out.println("nije pow");
            return false;
        }
        // 5. Provjera jesu li transakcije dobre po zadnji puta.
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

    public synchronized void addInitialBalance(String address, long amount) { // nova metoda da se dodaje money
        if (chain.size() != 1) {
            throw new IllegalStateException("Pocetni balance moze se dodati samo prije prvog izrudarenog bloka.");
        }
        if (amount < 0L) {
            throw new IllegalArgumentException("Pocetni balance ne smije biti negativan.");
        }

        PublicWallet wallet = publicWalletRegistry.get(address);

        if (wallet == null) {
            throw new IllegalArgumentException("Wallet s ovom adresom ne postoji.");
        }

        wallet.increaseBalance(amount);

        if (initialBalances.containsKey(address)) { // ovaj dio je malo zeznuti i fixat cemo ga kada budemo ovo
                                                    // prebacivali u BigDecimal i BigInteger e
            initialBalances.put(address, Math.addExact(initialBalances.get(address), amount));
        } else {
            initialBalances.put(address, amount);
        }
    }

    public synchronized Wallet registerWallet() {
        Wallet wallet = new Wallet();
        privateWalletRegistry.put(wallet.getAddress(), wallet);
        publicWalletRegistry.put(wallet.getAddress(), wallet.getPublicWallet());
        initialBalances.put(wallet.getAddress(), 0L);
        nextNonces.put(wallet.getAddress(), 0L);
        System.out.println("Novi wallet registriran: " + wallet.getAddress());
        adresa_walleta.add(wallet.getAddress());

        return wallet;
    }

    public Map<String, PublicWallet> getPublicWalletRegistry() {
        return publicWalletRegistry;
    }

    private Map<String, Long> createBalanceSnapshot() {
        Map<String, Long> temporaryBalances = new HashMap<>();

        for (Map.Entry<String, PublicWallet> entry : publicWalletRegistry.entrySet())
            temporaryBalances.put(entry.getKey(), entry.getValue().getBalance());

        return temporaryBalances;
    }

    private Map<String, Long> createNonceSnapshot() {
        return new HashMap<>(nextNonces);
    }

    private Map<String, Long> createInitialNonceSnapshot() {
        Map<String, Long> initialNonces = new HashMap<>();

        for (String address : publicWalletRegistry.keySet()) {
            initialNonces.put(address, 0L);
        }

        return initialNonces;
    }

    public synchronized Transactions createTransaction(Wallet senderWallet, String receiver, long amount) {
        if (senderWallet == null || privateWalletRegistry.get(senderWallet.getAddress()) != senderWallet) {
            throw new IllegalArgumentException("Wallet nije registriran na ovome blockchainu.");
        }

        Map<String, Long> temporaryBalances = createBalanceSnapshot();
        Map<String, Long> temporaryNonces = createNonceSnapshot();

        synchronized (transactionPoolLock) {
            for (Transactions pendingTx : transactionPool) {
                if (!applyTransactionToTemporaryState(pendingTx, temporaryBalances, temporaryNonces)) {
                    throw new IllegalStateException("Postojeci mempool nije valjan.");
                }
            }
        }

        Long nonce = temporaryNonces.get(senderWallet.getAddress());

        if (nonce == null) {
            throw new IllegalArgumentException("Wallet nema transaction nonce.");
        }

        return senderWallet.createTransaction(receiver, amount, nonce);
    }

    // služi da bismo mogli razmjeniti novce na siguran način preventira double
    // spend puff
    // vraca true ako transakciju treba dodati u pool, a inače vraća false
    private boolean applyTransactionToTemporaryState(Transactions tx, Map<String, Long> temporaryBalances,
            Map<String, Long> temporaryNonces) {

        if (tx.isSystemTransaction()) {
            return false;
        }

        long fee = calculate_fee(tx.getAmount());
        long totalAmount;
        Long senderBalance = temporaryBalances.get(tx.getSender());
        Long expectedNonce = temporaryNonces.get(tx.getSender());

        if (expectedNonce == null || tx.getNonce() != expectedNonce) {
            System.out.println("Pogresan transaction nonce. Ocekivan: " + expectedNonce + ", primljen: " + tx.getNonce());
            return false;
        }

        try {
            totalAmount = Math.addExact(tx.getAmount(), fee);
        } catch (ArithmeticException e) {
            System.out.println("Iznos transakcije je prevelik: " + tx.getSender());
            return false;
        }

        if (senderBalance == null || senderBalance < totalAmount) {
            System.out.println("Nema dovoljno sredstava nakon prethodnih transakcija u bloku: " + tx.getSender());
            return false;
        }

        Map<String, Long> updatedBalances = new HashMap<>(temporaryBalances);
        Map<String, Long> updatedNonces = new HashMap<>(temporaryNonces);

        try {
            updatedBalances.put(tx.getSender(), Math.subtractExact(senderBalance, totalAmount));

            if (updatedBalances.containsKey(tx.getReceiver())) {
                updatedBalances.put(tx.getReceiver(),
                        Math.addExact(updatedBalances.get(tx.getReceiver()), tx.getAmount()));
            }

            if (!validatorNodes.isEmpty()) {
                for (int i = 0; i < validatorNodes.size(); i++) {
                    String validatorAddress = validatorNodes.get(i).getAddress();

                    if (updatedBalances.containsKey(validatorAddress)) {
                        long feePerComputer = calculateValidatorFeeShare(fee, i);
                        updatedBalances.put(validatorAddress,
                                Math.addExact(updatedBalances.get(validatorAddress), feePerComputer));
                    }
                }
            }
            updatedNonces.put(tx.getSender(), Math.incrementExact(expectedNonce));
        } catch (ArithmeticException e) {
            System.out.println("Balance je izvan podrzanog raspona.");
            return false;
        }

        temporaryBalances.clear();
        temporaryBalances.putAll(updatedBalances);
        temporaryNonces.clear();
        temporaryNonces.putAll(updatedNonces);
        // ako prođe sve ovo vrati true
        return true;
    }

    private boolean transactionsCheck(Block newBlock) {
        Map<String, Long> temporaryBalances = createBalanceSnapshot();
        Map<String, Long> temporaryNonces = createNonceSnapshot();
        return transactionsCheck(newBlock, temporaryBalances, temporaryNonces);
    }

    private boolean transactionsCheck(Block newBlock, Map<String, Long> temporaryBalances,
            Map<String, Long> temporaryNonces) { // provjerava se jesu li
                                                                                             // transakcije dobro
                                                                                             // odrađene u ovome
        // blocku prije nego što se izloži za mine
        int brojSystemTransakcija = 0;
        for (int i = 0; i < newBlock.getTransactions().size(); i++) {
            Transactions tx = newBlock.getTransactions().get(i);
            if (tx.isSystemTransaction()) {
                // system transakcija je prva transakcija u bloku
                brojSystemTransakcija++;
                // u jednom bloku smije biti samo jedna coinbase transakcija
                if (brojSystemTransakcija > 1) {
                    System.out.println("Blok ima vise od jedne coinbase transakcije.");
                    return false;
                }
                // miner si ne smije sam povecati nagradu
                // System ima digitalni potpis
                // Treba se nekome poslati money
                if (!ConsensusRules.isCoinbaseValid(tx, i, newBlock.index, publicWalletRegistry)) {
                    System.out.println("System transakcija nije valjana.");
                    return false;
                }
                try {
                    temporaryBalances.put(tx.getReceiver(),
                            Math.addExact(temporaryBalances.get(tx.getReceiver()), tx.getAmount()));
                } catch (ArithmeticException e) {
                    System.out.println("Miner balance je izvan podrzanog raspona.");
                    return false;
                }
                continue;
            }

            // transakcije nevezane za minera
            // senderWallet sada više ne sadrži privatne ključeve nego samo ono što svi
            // smiju vidjeti

            // Provjera balansa
            // ovo sada zbog temporary balance vise ne treba
            /*
             * if (senderWallet.getBalance() < tx.getAmount() +
             * calculate_fee(tx.getAmount())) {
             * System.out.println("Posiljatelj nema dovoljno sredstava: " + tx.getSender());
             * return false;
             * }
             */
            // je li negativno (min koliko se moze poslati)
            // Provjera potpisa
            // String data = tx.getSender() + tx.getReceiver() + tx.getAmount();
            if (!ConsensusRules.isRegularTransactionValid(tx, publicWalletRegistry)) {
                System.out.println("Transakcija nije valjana.");
                return false;
            }
            // ova je nova metoda
            if (!applyTransactionToTemporaryState(tx, temporaryBalances, temporaryNonces)) {
                return false;
            }
        }

        // Još jedna provjeza za
        if (brojSystemTransakcija != 1) {
            System.out.println("Blok mora imati tocno jednu System transakciju.");
            return false;
        }

        return true;

    }

    // promjeniti cijelu ovu metodu kasnije

    public synchronized boolean isChainValid() {
        Map<String, Long> replayBalances = new HashMap<>(initialBalances);
        Map<String, Long> replayNonces = createInitialNonceSnapshot();

        for (int i = 0; i < chain.size(); i++) {
            Block current = chain.get(i);
            // provjerava odgovara li index
            if (current.index != i) {
                System.out.println("Index bloka " + i + " nije valjan.");
                return false;
            }
            // provjera merkle roota blokova
            if (!ConsensusRules.isMerkleRootValid(current)) {
                System.out.println("Merkle root bloka " + i + " nije valjan.");
                return false;
            }
            // provjera jesu li točno izračunati hashevi hasha
            if (!ConsensusRules.isBlockHashValid(current)) {
                System.out.println("Hash bloka " + i + " nije valjan.");
                return false;
            }

            // Genesis nema PoW ni coinbase/system transakciju
            if (i == 0) {
                if (!current.previousHash.equals("0")) {
                    System.out.println("Genesis previous hash nije valjan.");
                    return false;
                }

                if (!current.getTransactions().isEmpty()) {
                    System.out.println("Genesis ne smije imati transakcije.");
                    return false;
                }
                continue;
            }

            Block previous = chain.get(i - 1);
            // provjerava se je li ovo povezani lanac uopće
            if (!ConsensusRules.isBlockLinkedTo(current, previous)) {
                System.out.println("Previous hash bloka " + i + " nije valjan.");
                return false;
            }

            if (!ConsensusRules.isProofOfWorkValid(current, current.getDifficulty())) {
                System.out.println("Proof-of-Work bloka " + i + " nije valjan.");
                return false;
            }

            if (!transactionsCheck(current, replayBalances, replayNonces)) {
                System.out.println("Transakcije bloka " + i + " nisu valjane.");
                return false;
            }
        }

        // Provjera odgovara li stvarno stanje rezultatu blockchaina
        for (Map.Entry<String, PublicWallet> entry : publicWalletRegistry.entrySet()) {
            String address = entry.getKey();
            long actualBalance = entry.getValue().getBalance();
            long replayBalance = replayBalances.getOrDefault(address, 0L);
            if (actualBalance != replayBalance) { // ovo ce se maknuti kada preciznost bude veća
                                                  // jer double nije siguran
                System.out.println("Balance se ne podudara za adresu: " + address);
                return false;
            }
        }
        for (Map.Entry<String, Long> entry : nextNonces.entrySet()) {
            long replayNonce = replayNonces.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() != replayNonce) {
                System.out.println("Transaction nonce se ne podudara za adresu: " + entry.getKey());
                return false;
            }
        }
        return true;
    }

    public synchronized BigInteger getCumulativeWork() {
        return calculateCumulativeWork(chain);
    }

    public static BigInteger calculateCumulativeWork(
            List<Block> blocks) {

        BigInteger cumulativeWork = BigInteger.ZERO;

        if (blocks == null) {
            return cumulativeWork;
        }

        for (int i = 1; i < blocks.size(); i++) {

            int blockDifficulty = blocks.get(i).getDifficulty();

            if (blockDifficulty < 1 || blockDifficulty > 64) {
                throw new IllegalArgumentException(
                        "Block ima neispravan difficulty.");
            }

            BigInteger blockWork = BigInteger.ONE.shiftLeft(
                    blockDifficulty * 4);

            cumulativeWork = cumulativeWork.add(blockWork);
        }

        return cumulativeWork;
    }

    public synchronized ArrayList<Block> getChainSnapshot() {
        return new ArrayList<>(chain);
    }

    public synchronized boolean replaceChainIfStronger(
            List<Block> candidateChain) {

        ReplayState candidateState = validateAndReplayCandidateChain(candidateChain);

        if (candidateState == null) {
            System.out.println("Primljeni candidate chain nije valjan.");
            return false;
        }

        BigInteger currentWork = calculateCumulativeWork(chain);
        BigInteger candidateWork = calculateCumulativeWork(candidateChain);

        if (candidateWork.compareTo(currentWork) <= 0) {
            return false;
        }

        Set<String> candidateTransactionIds = new HashSet<>();

        for (Block block : candidateChain) {
            for (Transactions transaction : block.getTransactions()) {
                if (!transaction.isSystemTransaction()) {
                    candidateTransactionIds.add(transaction.getHash());
                }
            }
        }

        LinkedHashMap<String, Transactions> possiblePendingTransactions = new LinkedHashMap<>();

        synchronized (transactionPoolLock) {
            for (Transactions transaction : transactionPool) {
                possiblePendingTransactions.put(
                        transaction.getHash(),
                        transaction);
            }
        }

        /*
         * Transakcije iz napuštenog forka koje nisu u pobjedničkom
         * chainu vraćamo u mempool ako su još valjane.
         */
        for (Block oldBlock : chain) {
            for (Transactions transaction : oldBlock.getTransactions()) {

                if (!transaction.isSystemTransaction()
                        && !candidateTransactionIds.contains(transaction.getHash())) {

                    possiblePendingTransactions.putIfAbsent(
                            transaction.getHash(),
                            transaction);
                }
            }
        }

        chain = new ArrayList<>(candidateChain);

        for (Map.Entry<String, PublicWallet> entry : publicWalletRegistry.entrySet()) {

            long newBalance = candidateState.balances.getOrDefault(
                    entry.getKey(),
                    0L);

            entry.getValue().setBalance(newBalance);
        }

        nextNonces.clear();
        nextNonces.putAll(candidateState.nonces);

        synchronized (transactionPoolLock) {

            transactionPool.clear();

            Map<String, Long> temporaryBalances = createBalanceSnapshot();
            Map<String, Long> temporaryNonces = createNonceSnapshot();

            for (Transactions transaction : possiblePendingTransactions.values()) {

                if (candidateTransactionIds.contains(transaction.getHash())) {
                    continue;
                }

                if (isTransactionValidForMempool(transaction)
                        && applyTransactionToTemporaryState(
                                transaction,
                                temporaryBalances,
                                temporaryNonces)) {

                    transactionPool.add(transaction);
                }
            }
        }

        if (chain.size() > 1) {
            difficulty = getLatestBlock().getDifficulty();
        }

        System.out.println(
                "Prebačeno na jači chain. Novi cumulative work: "
                        + candidateWork);

        return true;
    }

    private ReplayState validateAndReplayCandidateChain(
            List<Block> candidateChain) {

        if (candidateChain == null || candidateChain.isEmpty()) {
            return null;
        }

        Block candidateGenesis = candidateChain.get(0);
        Block ownGenesis = chain.get(0);

        if (!ownGenesis.hash.equals(candidateGenesis.hash)
                || candidateGenesis.index != 0
                || !"0".equals(candidateGenesis.previousHash)
                || !candidateGenesis.getTransactions().isEmpty()
                || !ConsensusRules.isMerkleRootValid(candidateGenesis)
                || !ConsensusRules.isBlockHashValid(candidateGenesis)) {

            return null;
        }

        Map<String, Long> replayBalances = new HashMap<>(initialBalances);
        Map<String, Long> replayNonces = createInitialNonceSnapshot();

        for (int i = 1; i < candidateChain.size(); i++) {

            Block current = candidateChain.get(i);
            Block previous = candidateChain.get(i - 1);

            if (current.index != i) {
                return null;
            }

            if (current.getDifficulty() < 1
                    || current.getDifficulty() > 64) {

                return null;
            }

            /*
             * Difficulty se smije mijenjati postupno.
             * Točan automatski retarget možemo dodati kasnije.
             */
            if (i > 1
                    && Math.abs(
                            current.getDifficulty()
                                    - previous.getDifficulty()) > 1) {

                return null;
            }

            if (!ConsensusRules.isBlockLinkedTo(current, previous)
                    || !ConsensusRules.isMerkleRootValid(current)
                    || !ConsensusRules.isBlockHashValid(current)
                    || !ConsensusRules.isProofOfWorkValid(
                            current,
                            current.getDifficulty())
                    || !transactionsCheck(current, replayBalances, replayNonces)) {

                return null;
            }
        }

        return new ReplayState(replayBalances, replayNonces);
    }

    public synchronized ArrayList<Block> getChain() {
        return new ArrayList<>(chain);
    }

    public synchronized void addValidatorNode(Computer validatorNode) {
        validatorNodes.add(validatorNode);
    }

    public ArrayList<Computer> getValidatorNodes() {
        return validatorNodes;
    }

    public synchronized void setDifficulty(int difficulty) {
        if (difficulty < 1 || difficulty > 64) { // zbog SHA256
            throw new IllegalArgumentException(
                    "Difficulty mora biti između 1 i 64.");
        }

        this.difficulty = difficulty;
    }

    public synchronized int getDifficulty() {
        return this.difficulty;
    }

    public void while_active() {
        while (true) {
            // Do something

        }
    }

    public boolean hasPendingTransactions() {
        synchronized (transactionPoolLock) {
            return !transactionPool.isEmpty();
        }
    }

    public boolean IsMiningInProgress() {
        return activeMiners.get() > 0;
    }

    private boolean isTransactionValidForMempool(Transactions tx) {
        return ConsensusRules.isRegularTransactionValid(tx, publicWalletRegistry);
    }

    public synchronized boolean addPendingTransaction(Transactions tx) {

        synchronized (transactionPoolLock) {
            if (!isTransactionValidForMempool(tx)) {
                System.out.println("Transakcija nije valjana za mempool.");
                return false;
            }

            // ista transakcija ne smije dva puta u pool
            for (Transactions pendingTx : transactionPool) {
                if (pendingTx.getHash().equals(tx.getHash())) {
                    System.out.println("Transakcija je vec u mempoolu.");
                    return false;
                }
            }

            Map<String, Long> temporaryBalances = createBalanceSnapshot();
            Map<String, Long> temporaryNonces = createNonceSnapshot();

            /*
             * Prvo primijenimo sve transakcije koje su vec
             * rezervirale sredstva u poolu.
             */
            for (Transactions pendingTx : transactionPool) {
                if (!applyTransactionToTemporaryState(pendingTx, temporaryBalances, temporaryNonces)) {
                    System.out.println("Postojeci mempool nije valjan.");
                    return false;
                }
            }

            /*
             * Nova transakcija mora biti moguca nakon svih
             * prethodnih pending transakcija.
             */
            if (!applyTransactionToTemporaryState(tx, temporaryBalances, temporaryNonces)) {

                System.out.println("Nema dovoljno slobodnih sredstava.");
                return false;
            }

            transactionPool.add(tx);
            System.out.println("dodana transakcija"); // puff
            return true;
        }
    }

    public synchronized Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    // ideja je da se kopa koliko god treba, a da se prije rudarenja čeka 30 sekundi
    // da ljudi pošalju svoje transakcije - odbačena ideja xD
    public void minePendingTransactions(String minerAddress) {
        minePendingTransactionsInternal(minerAddress, true);
    }

    public Block minePendingTransactionsForNetwork(String minerAddress) {

        return minePendingTransactionsInternal(minerAddress, false); // mora biti false jer inace nema smisla
    }

    private Block minePendingTransactionsInternal(String minerAddress, boolean useGlasanje) {

        activeMiners.incrementAndGet();

        try {
            List<Transactions> miningBatch;
            ArrayList<Transactions> approvedTransactions = new ArrayList<>();

            String parentHash;
            int nextBlockIndex;
            int miningDifficulty;

            /*
             * Ovo je samo kratko zakljucavanje tijekom stvaranja
             * kandidata. Sam PoW se ne izvodi unutar synchronized dijela.
             */
            synchronized (this) {
                Block parentBlock = getLatestBlock();

                parentHash = parentBlock.hash;
                nextBlockIndex = parentBlock.index + 1;
                miningDifficulty = difficulty;

                /*
                 * Svaki miner dobiva vlastitu kopiju trenutnog poola.
                 */
                synchronized (transactionPoolLock) {
                    if (transactionPool.isEmpty()) {
                        return null;
                    }

                    miningBatch = new ArrayList<>(transactionPool);
                }

                Map<String, Long> temporaryBalances = createBalanceSnapshot();
                Map<String, Long> temporaryNonces = createNonceSnapshot();

                for (Transactions tx : miningBatch) {

                    boolean transactionApproved;

                    if (useGlasanje) {
                        transactionApproved = isTransactionApproved_Full(tx);
                    } else {
                        transactionApproved = ConsensusRules.isRegularTransactionValid(tx, publicWalletRegistry);
                    }
                    if (transactionApproved && applyTransactionToTemporaryState(tx, temporaryBalances, temporaryNonces)) {
                        approvedTransactions.add(tx);
                    } else {
                        System.out.println(
                                "Transakcija odbijena: "
                                        + tx.getSender() + " -> "
                                        + tx.getReceiver() + " ("
                                        + Money.format(tx.getAmount()) + ")");
                    }
                }
            }

            Transactions rewardTx = Transactions.createSystemTransaction(
                    minerAddress,
                    ConsensusRules.MINING_REWARD,
                    nextBlockIndex);

            approvedTransactions.add(0, rewardTx);

            Block newBlock = new Block(
                    nextBlockIndex,
                    parentHash,
                    System.currentTimeMillis(),
                    approvedTransactions,
                    0,
                    miningDifficulty);

            /*
             * Nema synchronized:
             * svi mineri ovdje rudare istovremeno.
             */
            newBlock.mineBlock(miningDifficulty);

            /*
             * Tek nakon pronalaska noncea miner pokusava
             * atomski dodati svoj blok.
             */
            boolean minerWon = tryAddMinedBlock(
                    newBlock,
                    parentHash,
                    miningDifficulty,
                    miningBatch);

            if (!minerWon) {
                System.out.println("Miner " + minerAddress + " je izgubio utrku. " + "Njegov blok je zastario.");
                return null;

            }

            System.out.println("Blok je iskopao miner: " + minerAddress);
            if (useGlasanje) {
                for (Computer validator : validatorNodes) {
                    if (validator.getType() == Computer.NodeType.LIGHT) {
                        if (!validator.receiveBlockHeader(newBlock, miningDifficulty)) {
                            System.out.println("LIGHT node odbio novi header.");
                        }
                    }
                }

                int indexTx = 0;

                for (Transactions tx : approvedTransactions) {
                    MerkleTree merkleTree = new MerkleTree();

                    boolean lightNodeApproved = isTransactionApproved_Light(
                            tx,
                            newBlock,
                            merkleTree.getMerkleProof(
                                    newBlock.getTransactionsToStringHashs(),
                                    indexTx),
                            indexTx);

                    if (lightNodeApproved) {
                        System.out.println(
                                "Light nodovi prihvatili transakciju: "
                                        + tx.getSender() + " -> "
                                        + tx.getReceiver() + " ("
                                        + Money.format(tx.getAmount()) + ")");
                    } else {
                        System.out.println(
                                "Light nodovi odbacili transakciju: "
                                        + tx.getSender() + " -> "
                                        + tx.getReceiver() + " ("
                                        + Money.format(tx.getAmount()) + ")");
                    }

                    indexTx++;
                }
            } else {
                System.out.println("todo da lightnodeovi rade nekako");
            }

            return newBlock;
        } finally {
            activeMiners.decrementAndGet();
        }
    }

    private boolean isTransactionApproved_Full(Transactions tx) {
        if (validatorNodes.isEmpty()) {
            System.out.println("nema validatora, ne moze se potvrditi");
            return false; // nema validatora, ne može se potvrditi
        }

        int approvals = 0;
        int ukupno = 0;

        for (Computer validator : validatorNodes) { // svatko za sebe provjeri je li dobro
            if (validator.getType() == Computer.NodeType.MINER || validator.getType() == Computer.NodeType.FULL) {
                if (validator.validateTransaction(tx)) {
                    approvals++;
                }
                ukupno++;
            }
        }
        if (ukupno == 0) {
            System.out.println("Nema FULL ili MINER validatora.");
            return false;
        }

        // mora biti >= 2/3 validatora
        return ConsensusRules.hasEnoughApprovals(approvals, ukupno);
    }

    private boolean isTransactionApproved_Light(Transactions tx, Block block, List<String> proof, int txIndex) {
        if (validatorNodes.isEmpty()) {
            System.out.println("nema validatora, ne moze se potvrditi");
            return false; // nema validatora, ne može se potvrditi
        }

        int approvals = 0;
        int ukupno = 0;

        for (Computer validator : validatorNodes) { // svatko za sebe provjeri je li dobro
            if (validator.getType() == Computer.NodeType.LIGHT) {
                if (validator.validateTransaction(tx, block, proof, txIndex)) {
                    approvals++;
                }
                ukupno++;
            }
        }
        if (ukupno == 0) {
            System.out.println("Nema LIGHT validatora.");
            return false;
        }

        // mora biti >= 2/3 validatora
        return ConsensusRules.hasEnoughApprovals(approvals, ukupno);
    }

    public synchronized boolean registerNetworkWallet(
            String address,
            String publicKey,
            long initialBalance) {

        if (address == null || address.isBlank() || publicKey == null || publicKey.isBlank()) {
            System.out.println("Network wallet nema adresu ili public key.");
            return false;
        }

        if (initialBalance < 0L) {
            System.out.println("Početni balance ne smije biti negativan.");
            return false;
        }

        PublicWallet existingWallet = publicWalletRegistry.get(address);

        if (existingWallet != null) {

            Long existingInitialBalance = initialBalances.get(address);

            if (!existingWallet.getPublicKey().equals(publicKey)
                    || existingInitialBalance == null
                    || existingInitialBalance != initialBalance) {

                System.out.println("Primljeni wallet se ne podudara s postojećim walletom.");
            }

            return false;
        }

        if (chain.size() != 1) {
            System.out.println("Novi početni wallet se ne može dodati nakon genesis faze.");
            return false;
        }

        try {
            PublicKey decodedPublicKey = Cryptography.stringToPublicKey(publicKey);
            String calculatedAddress = Cryptography.generateAddress(decodedPublicKey);

            if (!calculatedAddress.equals(address)) {
                System.out.println("Wallet adresa ne odgovara javnom ključu.");
                return false;
            }

        } catch (Exception e) {
            System.out.println("Primljen je neispravan javni ključ.");
            return false;
        }

        PublicWallet publicWallet = new PublicWallet(address, publicKey);
        publicWallet.increaseBalance(initialBalance);

        publicWalletRegistry.put(address, publicWallet);
        initialBalances.put(address, initialBalance);
        nextNonces.put(address, 0L);
        adresa_walleta.add(address);

        System.out.println("Network wallet registriran: " + address);
        return true;
    }

    public synchronized long getInitialBalance(String address) {

        Long balance = initialBalances.get(address);

        if (balance == null) {
            throw new IllegalArgumentException("Wallet nema početni balance.");
        }

        return balance;
    }

    public List<Transactions> getTransactionPoolSnapshot() {

        synchronized (transactionPoolLock) {
            return new ArrayList<>(transactionPool);
        }
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
        /*
         * System.out.println("\nSvi walleti:");
         * getWalletRegistry().forEach(
         * (address, wallet) -> System.out.println("Adresa: " + address + " | Balance: "
         * + wallet.getBalance()));
         */
        System.out.println("\nSvi walleti:");
        for (String address : adresa_walleta) {
            System.out.println(
                    "Adresa: " + address + " | Balance: "
                            + Money.format(getPublicWalletRegistry().get(address).getBalance()));
        }
    }

    public void printAllWallets_DETAL() {
        System.out.println("\nSvi walleti DETAL:");
        for (String address : adresa_walleta) {
            System.out
                    .println("Adresa: " + address + " | Balance: "
                            + Money.format(getPublicWalletRegistry().get(address).getBalance())
                            + " | Public key hash: "
                            + Cryptography.applySHA256(getPublicWalletRegistry().get(address).getPublicKey().toString())
                            + " | Private key hash: ");
            // +
            // Cryptography.applySHA256(getPublicWalletRegistry().get(address).getPrivateKey().toString()));
            // ova sada linija nema više smisla kako private ključevi nisu javni demo toga
            // je gotov
        }
    }
}
