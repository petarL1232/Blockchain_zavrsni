import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WlanUIController implements AutoCloseable {
    public static final String NETWORK_ID = "MATHOS-DEVNET-1";
    private static final int MAX_ACTIVITY = 180;

    private final BlockChain blockchain;
    private final NetworkNode networkNode;
    private final Wallet localWallet;
    private final Settings settings;
    private final long startedAt = System.currentTimeMillis();
    private final Object submitLock = new Object();
    private final Object autoModeLock = new Object();
    private final Object observationLock = new Object();
    private final ConcurrentLinkedDeque<ActivityView> activity = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService autoModeScheduler;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger automaticTransactions = new AtomicInteger(0);
    private final AtomicInteger rejectedAutomaticTransactions = new AtomicInteger(0);
    private final DatabaseManager databaseManager;
    private final BlockchainRepository blockchainRepository;

    private volatile boolean loggedIn;
    private volatile boolean autoModeEnabled;
    private volatile int autoModeGeneration;
    private volatile long lastAutoWaitingMessage;
    private ScheduledFuture<?> autoModeTask;

    private int observedPeerCount = -1;
    private int observedHeight = -1;
    private Set<String> observedPendingIds = new HashSet<>();

    private WlanUIController(
            BlockChain blockchain,
            NetworkNode networkNode,
            Wallet localWallet,
            Settings settings,
            DatabaseManager databaseManager,
            BlockchainRepository blockchainRepository) {

        this.blockchain = blockchain;
        this.networkNode = networkNode;
        this.localWallet = localWallet;
        this.settings = settings;
        this.databaseManager = databaseManager;
        this.blockchainRepository = blockchainRepository;

        ThreadFactory daemonFactory = task -> {
            Thread thread = new Thread(task, "mathos-wlan-auto");
            thread.setDaemon(true);
            return thread;
        };

        autoModeScheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    public static WlanUIController start(Settings settings) throws Exception {

        if (settings == null) {
            throw new IllegalArgumentException("Node settings are missing.");
        }

        settings.validate();

        String databasePath = "data/node-" + settings.listenPort + ".db";

        DatabaseManager databaseManager = new DatabaseManager(databasePath);

        WlanUIController controller = null;

        try {
            databaseManager.initSchema();

            BlockchainRepository repository = new BlockchainRepository(databaseManager);

            BlockChain blockchain = new BlockChain();

            ArrayList<BlockchainRepository.StoredWallet> storedWallets = repository.loadWallets();

            Wallet localWallet = null;
            boolean restoredFromDatabase = !storedWallets.isEmpty();

            /*
             * Lokalni wallet mora biti prvi jer sadrži private key.
             */
            for (BlockchainRepository.StoredWallet storedWallet : storedWallets) {

                if (!storedWallet.local) {
                    continue;
                }

                if (localWallet != null) {
                    throw new IllegalStateException(
                            "The database contains multiple local wallets.");
                }

                if (storedWallet.privateKey == null
                        || storedWallet.privateKey.isBlank()) {

                    throw new IllegalStateException(
                            "The local wallet has no private key.");
                }

                Wallet restoredWallet = new Wallet(
                        storedWallet.privateKey,
                        storedWallet.publicKey);

                if (!restoredWallet.getAddress().equals(storedWallet.address)) {
                    throw new IllegalStateException(
                            "The local wallet address does not match its keys.");
                }

                if (!blockchain.registerStoredLocalWallet(
                        restoredWallet,
                        storedWallet.initialBalance)) {

                    throw new IllegalStateException(
                            "The local wallet could not be loaded.");
                }

                localWallet = restoredWallet;
            }

            /*
             * Nakon lokalnog učitavamo javne wallete drugih nodeova.
             */
            for (BlockchainRepository.StoredWallet storedWallet : storedWallets) {

                if (storedWallet.local) {
                    continue;
                }

                boolean registered = blockchain.registerNetworkWallet(
                        storedWallet.address,
                        storedWallet.publicKey,
                        storedWallet.initialBalance);

                if (!registered) {
                    throw new IllegalStateException(
                            "The network wallet could not be loaded: "
                                    + storedWallet.address);
                }
            }

            /*
             * Ako je baza nova, stvaramo samo jedan lokalni wallet.
             */
            if (localWallet == null) {

                if (!storedWallets.isEmpty()) {
                    throw new IllegalStateException(
                            "The database has no wallet marked as local.");
                }

                localWallet = blockchain.registerWallet();
                blockchain.addInitialBalance(
                        localWallet.getAddress(),
                        Money.coins(100));

                repository.saveLocalWallet(
                        localWallet,
                        blockchain.getInitialBalance(localWallet.getAddress()));
            }

            boolean lightNode = settings.nodeType == Computer.NodeType.LIGHT;

            ArrayList<Block> storedChain = lightNode
                    ? blockchain.getChainSnapshot()
                    : repository.loadChain();

            ArrayList<Transactions> storedMempool = repository.loadMempool();

            boolean chainWasEmpty = !lightNode && storedChain.isEmpty();

            if (chainWasEmpty) {
                storedChain = blockchain.getChainSnapshot();
            }

            if (!lightNode && !blockchain.restoreStateFromDatabase(
                    storedChain,
                    storedMempool)) {

                throw new IllegalStateException(
                        "The blockchain state could not be loaded from the database.");
            }

            /*
             * Nova baza još nema genesis, pa ga sada trajno spremamo.
             */
            if (!lightNode && chainWasEmpty) {
                repository.saveAcceptedBlock(
                        blockchain.getLatestBlock(),
                        blockchain.getTransactionPoolSnapshot());
            }

            NetworkNode networkNode = new NetworkNode(
                    settings.nodeId,
                    settings.nodeType,
                    settings.listenPort,
                    NETWORK_ID,
                    blockchain,
                    repository);

            networkNode.configureLightWallet(localWallet.getAddress());
            if (lightNode) {
                networkNode.restoreLightPendingTransactions(storedMempool);
            }

            controller = new WlanUIController(
                    blockchain,
                    networkNode,
                    localWallet,
                    settings,
                    databaseManager,
                    repository);

            networkNode.start();

            /*
             * Prvo pokušavamo vratiti peerove zapamćene u bazi.
             */
            for (BlockchainRepository.StoredPeer storedPeer : repository.loadPeers()) {

                if (storedPeer.port == settings.listenPort
                        && storedPeer.nodeId.equals(settings.nodeId)) {

                    continue;
                }

                networkNode.maintainConnection(
                        storedPeer.host,
                        storedPeer.port);
            }

            if (settings.manualPeerIp != null
                    && !settings.manualPeerIp.isBlank()) {

                networkNode.maintainConnection(
                        settings.manualPeerIp.trim(),
                        settings.manualPeerPort);
            }

            if (settings.nodeType == Computer.NodeType.MINER) {
                networkNode.startMining(localWallet.getAddress());
            }

            controller.addActivity(
                    restoredFromDatabase
                            ? "WLAN node restored from database"
                            : "New WLAN node saved",
                    settings.nodeId
                            + " is listening on port "
                            + settings.listenPort
                            + ". Local wallet: "
                            + WlanTheme.compact(localWallet.getAddress(), 7),
                    "success");

            controller.addActivity(
                    "Local wallet is ready",
                    settings.nodeType == Computer.NodeType.LIGHT
                            ? "The LIGHT wallet is ready to sign. A FULL/MINER node provides its nonce and balance, while Merkle proofs verify transactions."
                            : restoredFromDatabase
                            ? "The wallet, blockchain and mempool were loaded from the local SQLite database."
                            : settings.alias + " starts with 100 $MATH. Login unlocks transaction signing.",
                    "info");

            return controller;

        } catch (Exception e) {

            if (controller != null) {
                controller.shutdown();
            } else {
                try {
                    databaseManager.close();
                } catch (Exception ignored) {

                }
            }

            throw e;
        }
    }

    public Snapshot snapshot() {
        ArrayList<Block> chain = new ArrayList<>();
        List<Transactions> pending = new ArrayList<>();
        ArrayList<BlockChain_LightNodes.BlockHeader> lightHeaders = new ArrayList<>();
        ArrayList<WalletView> walletViews = new ArrayList<>();
        long totalSupply = 0L;
        long localBalance;
        int difficulty;
        BigInteger cumulativeWork;
        boolean mining;
        boolean lightNode = settings.nodeType == Computer.NodeType.LIGHT;
        boolean lightAccountStateSynchronized = !lightNode
                || networkNode.isLightAccountStateSynchronized();
        List<NetworkNode.LightTransaction> lightTransactions = lightNode
                ? networkNode.getLightTransactionsSnapshot()
                : List.of();

        synchronized (blockchain) {
            if (lightNode && networkNode.getLightBlockchain() != null) {
                lightHeaders = networkNode.getLightBlockchain().getHeadersSnapshot();
            } else {
                chain = blockchain.getChainSnapshot();
                pending = blockchain.getTransactionPoolSnapshot();
            }

            for (PublicWallet wallet : blockchain.getPublicWalletRegistry().values()) {
                boolean local = wallet.getAddress().equals(localWallet.getAddress());
                long balance = lightNode && local
                        ? lightAccountStateSynchronized
                        ? networkNode.getLightSpendableBalance(localWallet.getAddress())
                        : 0L
                        : wallet.getBalance();
                if (!lightNode) {
                    try {
                        totalSupply = Math.addExact(totalSupply, balance);
                    } catch (ArithmeticException ignored) {
                        totalSupply = Long.MAX_VALUE;
                    }
                }
                walletViews.add(new WalletView(wallet.getAddress(),
                        local
                                ? settings.alias + (lightNode && !lightAccountStateSynchronized ? " · SYNCING" : "")
                                : "Wallet " + WlanTheme.compact(wallet.getAddress(), 4), balance,
                        local));
            }

            localBalance = lightNode
                    ? lightAccountStateSynchronized
                    ? networkNode.getLightSpendableBalance(localWallet.getAddress())
                    : 0L
                    : localWallet.getBalance();
            if (lightNode && !lightHeaders.isEmpty()) {
                difficulty = lightHeaders.get(lightHeaders.size() - 1).difficulty;
                cumulativeWork = networkNode.getLightBlockchain().getCumulativeWork();
                mining = false;
            } else {
                difficulty = blockchain.getDifficulty();
                cumulativeWork = blockchain.getCumulativeWork();
                mining = blockchain.IsMiningInProgress();
            }
        }
        walletViews.sort(Comparator.comparingLong((WalletView wallet) -> wallet.balance).reversed());

        ArrayList<BlockView> blockViews = new ArrayList<>();
        ArrayList<TransactionView> transactionViews = new ArrayList<>();
        int localBlocksMined = 0;

        for (Block block : chain) {
            String minerAddress = minerAddress(block);
            if (localWallet.getAddress().equals(minerAddress))
                localBlocksMined++;
            blockViews.add(
                    new BlockView(block.index, block.hash, block.previousHash, block.getMerkleRoot(), block.timestamp,
                            block.nonce, block.getDifficulty(), block.getTransactions().size(), minerAddress));

            for (int i = 0; i < block.getTransactions().size(); i++) {
                Transactions transaction = block.getTransactions().get(i);
                transactionViews.add(transactionView(transaction, "CONFIRMED", block.index, i));
            }
        }

        for (BlockChain_LightNodes.BlockHeader header : lightHeaders) {
            blockViews.add(
                    new BlockView(header.height, header.blockHash, header.previousHash, header.merkleRoot,
                            header.timestamp, header.nonce, header.difficulty, -1, null));
        }

        for (NetworkNode.LightTransaction lightTransaction : lightTransactions) {
            Transactions transaction = lightTransaction.transaction;
            transactionViews.add(transactionView(
                    transaction,
                    lightTransaction.verified ? "VERIFIED" : "PENDING",
                    lightTransaction.blockHeight,
                    -1));
            if (!lightTransaction.verified) {
                pending.add(transaction);
            }
        }

        for (Transactions transaction : pending) {
            transactionViews.add(transactionView(transaction, "PENDING", -1, -1));
        }
        transactionViews.sort((first, second) -> {
            if (first.blockHeight != second.blockHeight)
                return Integer.compare(second.blockHeight, first.blockHeight);
            return Integer.compare(second.position, first.position);
        });

        int peerCount = networkNode.getConnectedPeerCount();
        int height = lightNode
                ? lightHeaders.isEmpty() ? 0 : lightHeaders.get(lightHeaders.size() - 1).height
                : chain.isEmpty() ? 0 : chain.get(chain.size() - 1).index;
        observeChanges(peerCount, height, pending);

        ArrayList<ActivityView> activityViews = new ArrayList<>(activity);
        String tipHash = lightNode
                ? lightHeaders.isEmpty() ? "—" : lightHeaders.get(lightHeaders.size() - 1).blockHash
                : chain.get(chain.size() - 1).hash;
        NetworkNode.MerkleProofResult networkProof = networkNode.getLastMerkleProofResult();
        ProofView proof = networkProof == null
                ? null
                : new ProofView(networkProof.getBlockHash(), networkProof.getTransactionId(),
                        networkProof.isVerified(), networkProof.getMessage());

        return new Snapshot(
                settings.nodeId, settings.alias, settings.nodeType, settings.listenPort, localWallet.getAddress(),
                localBalance, lightAccountStateSynchronized, loggedIn, autoModeEnabled, automaticTransactions.get(),
                rejectedAutomaticTransactions.get(),
                peerCount, height, tipHash, difficulty, cumulativeWork, pending.size(),
                mining, localBlocksMined, totalSupply, System.currentTimeMillis() - startedAt,
                walletViews, blockViews, transactionViews, activityViews, proof, shuttingDown.get());
    }

    private TransactionView transactionView(Transactions transaction, String status, int blockHeight, int position) {
        return new TransactionView(transaction.getHash(),
                transaction.isSystemTransaction() ? "SYSTEM REWARD" : "REGULAR",
                transaction.getSender(), transaction.getReceiver(), transaction.getAmount(), transaction.getNonce(),
                status, blockHeight, position);
    }

    private String minerAddress(Block block) {
        if (block.index == 0 || block.getTransactions().isEmpty())
            return null;
        Transactions first = block.getTransactions().get(0);
        return first.isSystemTransaction() ? first.getReceiver() : null;
    }

    private void observeChanges(int peerCount, int height, List<Transactions> pending) {
        synchronized (observationLock) {
            if (observedPeerCount >= 0 && peerCount != observedPeerCount) {
                if (peerCount > observedPeerCount) {
                    addActivity("Mesh expanded", peerCount + " direct peer connections are active.", "success");
                } else {
                    addActivity("Peer status changed", peerCount + " direct peer connections remain active.",
                            "warning");
                }
            }

            if (observedHeight >= 0 && height > observedHeight) {
                addActivity("New block confirmed", "The local chain is now at height " + height + ".", "success");
            }

            Set<String> pendingIds = new HashSet<>();
            for (Transactions transaction : pending)
                pendingIds.add(transaction.getHash());
            if (observedHeight >= 0) {
                for (String pendingId : pendingIds) {
                    if (!observedPendingIds.contains(pendingId)) {
                        addActivity("Mempool received a transaction", WlanTheme.compact(pendingId, 7), "info");
                    }
                }
            }

            observedPeerCount = peerCount;
            observedHeight = height;
            observedPendingIds = pendingIds;
        }
    }

    public String getPublicLoginHash() {
        return Cryptography.applySHA256(localWallet.getPublicKeyString());
    }

    public String getPrivateLoginHash() {
        return Cryptography.applySHA256(localWallet.getPrivateKeyString());
    }

    public ActionResult login(String publicHash, String privateHash) {
        if (publicHash == null || publicHash.isBlank() || privateHash == null || privateHash.isBlank()) {
            return ActionResult.fail("Enter the public and private login hashes.");
        }

        if (!getPublicLoginHash().equals(publicHash.trim()) || !getPrivateLoginHash().equals(privateHash.trim())) {
            addActivity("Login rejected", "The entered hashes do not belong to the local session wallet.", "danger");
            return ActionResult.fail("The public or private login hash is incorrect.");
        }

        synchronized (submitLock) {
            loggedIn = true;
        }
        addActivity("Local wallet unlocked", settings.alias + " can now sign WLAN transactions.",
                "success");
        return ActionResult.ok("Login successful.", null);
    }

    public void logout() {
        synchronized (submitLock) {
            loggedIn = false;
        }
        setAutoMode(false);
        addActivity("Wallet locked", "Network monitoring remains active while transaction signing is disabled.", "warning");
    }

    public ActionResult sendTransaction(String receiverAddress, String amountText) {
        if (!loggedIn)
            return ActionResult.fail("Log in to the local wallet first.");
        if (receiverAddress == null || receiverAddress.isBlank())
            return ActionResult.fail("Receiver address is required.");

        long amount;
        try {
            amount = Money.fromCoins(amountText);
        } catch (Exception e) {
            return ActionResult.fail("Enter a valid $MATH amount with no more than 8 decimal places.");
        }

        if (amount < ConsensusRules.MIN_TRANSACTION_AMOUNT) {
            return ActionResult.fail("The amount is below the allowed minimum.");
        }

        return submitUnits(receiverAddress.trim(), amount, false);
    }

    private ActionResult submitUnits(String receiverAddress, long amount, boolean automatic) {
        if (shuttingDown.get())
            return ActionResult.fail("The node is shutting down.");

        try {
            Transactions transaction;
            boolean accepted;

            synchronized (submitLock) {
                if (!loggedIn)
                    return ActionResult.fail("The local wallet is locked.");
                transaction = networkNode.createTransaction(localWallet, receiverAddress, amount);
                accepted = networkNode.submitTransaction(transaction);
            }

            if (!accepted) {
                if (!automatic)
                    addActivity("Transaction rejected", receiverAddress + " did not pass local consensus rules.",
                            "danger");
                return ActionResult.fail(settings.nodeType == Computer.NodeType.LIGHT
                        ? "The LIGHT node has no connected FULL/MINER peer, or its wallet state needs to synchronize again."
                        : "The blockchain did not accept the transaction.");
            }

            addActivity(automatic ? "Auto Mode sent a transaction" : "Transaction broadcast",
                    settings.alias + " → " + WlanTheme.compact(receiverAddress, 7) + " · " + Money.format(amount)
                            + " $MATH",
                    "info");
            return ActionResult.ok(settings.nodeType == Computer.NodeType.LIGHT
                    ? "The transaction was signed and sent. It is waiting for Merkle proof confirmation."
                    : "The transaction was added to the mempool and sent to peers.", transaction.getHash());
        } catch (Exception e) {
            if (!automatic)
                addActivity("Send failed", safeMessage(e), "danger");
            return ActionResult.fail(safeMessage(e));
        }
    }

    public ActionResult connectManually(String ipAddress, String portText) {
        if (ipAddress == null || ipAddress.isBlank())
            return ActionResult.fail("IP address is required.");

        int port;
        try {
            port = Integer.parseInt(portText.trim());
        } catch (Exception e) {
            return ActionResult.fail("Port must be a whole number.");
        }

        if (port < 1 || port > 65535)
            return ActionResult.fail("Port must be between 1 and 65535.");
        networkNode.maintainConnection(ipAddress.trim(), port);
        addActivity("Manual peer added", ipAddress.trim() + ":" + port + " was passed to the reconnect worker.", "info");
        return ActionResult.ok("Connection attempt started.", null);
    }

    public ActionResult setAutoMode(boolean enabled) {
        synchronized (autoModeLock) {
            if (enabled && !loggedIn)
                return ActionResult.fail("Login is required for Auto Mode.");
            if (shuttingDown.get())
                return ActionResult.fail("The node is shutting down.");
            if (autoModeEnabled == enabled)
                return ActionResult.ok("Auto Mode is already " + (enabled ? "enabled." : "disabled."), null);

            autoModeEnabled = enabled;
            int generation = ++autoModeGeneration;
            if (autoModeTask != null)
                autoModeTask.cancel(false);
            autoModeTask = null;

            if (enabled) {
                try {
                    autoModeTask = autoModeScheduler.schedule(() -> runAutoMode(generation), 350,
                            TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException e) {
                    autoModeEnabled = false;
                    return ActionResult.fail("The Auto Mode worker is unavailable.");
                }
            }
        }

        addActivity(enabled ? "Auto Mode enabled" : "Auto Mode disabled",
                enabled ? "Only this local node creates and broadcasts random transactions."
                        : "The local traffic generator has stopped.",
                enabled ? "success" : "warning");
        return ActionResult.ok("Auto Mode is " + (enabled ? "enabled." : "disabled."), null);
    }

    private void runAutoMode(int generation) {
        if (!autoModeEnabled || !loggedIn || shuttingDown.get() || generation != autoModeGeneration)
            return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = random.nextInt(100) < 28 ? random.nextInt(2, 5) : 1;

        for (int i = 0; i < attempts; i++) {
            if (!autoModeEnabled || !loggedIn || generation != autoModeGeneration)
                return;
            createAutomaticTransaction(random.nextInt(10) == 0);
        }

        scheduleNextAutoMode(generation, random.nextLong(700L, 2401L));
    }

    private void createAutomaticTransaction(boolean intentionallyInvalid) {
        ArrayList<String> receivers = new ArrayList<>();

        synchronized (blockchain) {
            for (String address : blockchain.getPublicWalletRegistry().keySet()) {
                if (!address.equals(localWallet.getAddress()))
                    receivers.add(address);
            }
        }

        if (receivers.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastAutoWaitingMessage > 8000L) {
                lastAutoWaitingMessage = now;
                addActivity("Auto Mode is waiting for a peer wallet",
                        "Connect at least one more node so the generator has a receiver address.", "warning");
            }
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        String receiver = receivers.get(random.nextInt(receivers.size()));

        if (intentionallyInvalid) {
            ActionResult result = submitUnits(receiver, ConsensusRules.MIN_TRANSACTION_AMOUNT - 1L, true);
            rejectedAutomaticTransactions.incrementAndGet();
            addActivity("Auto Mode · test rejected",
                    "An intentionally tiny amount did not pass local consensus" + (result.success ? " (unexpected)." : "."),
                    "danger");
            return;
        }

        long spendable = getSpendableLocalBalance();
        long preferredMinimum = Math.max(ConsensusRules.MIN_TRANSACTION_AMOUNT, 5_000_000L);
        long maximum = Math.min(Money.coins(5), spendable / 8L);

        if (maximum < preferredMinimum) {
            if (spendable <= ConsensusRules.MIN_TRANSACTION_AMOUNT
                    + ConsensusRules.calculateFee(ConsensusRules.MIN_TRANSACTION_AMOUNT)) {
                rejectedAutomaticTransactions.incrementAndGet();
                return;
            }
            preferredMinimum = ConsensusRules.MIN_TRANSACTION_AMOUNT;
            maximum = Math.max(preferredMinimum, spendable / 3L);
        }

        long amount = maximum == preferredMinimum ? maximum : random.nextLong(preferredMinimum, maximum + 1L);
        while (amount > ConsensusRules.MIN_TRANSACTION_AMOUNT
                && amount + ConsensusRules.calculateFee(amount) > spendable)
            amount--;

        ActionResult result = submitUnits(receiver, amount, true);
        if (result.success)
            automaticTransactions.incrementAndGet();
        else
            rejectedAutomaticTransactions.incrementAndGet();
    }

    private long getSpendableLocalBalance() {
        if (settings.nodeType == Computer.NodeType.LIGHT) {
            return networkNode.getLightSpendableBalance(localWallet.getAddress());
        }

        long spendable = localWallet.getBalance();

        for (Transactions transaction : blockchain.getTransactionPoolSnapshot()) {
            if (transaction.isSystemTransaction() || !localWallet.getAddress().equals(transaction.getSender()))
                continue;
            try {
                spendable = Math.subtractExact(spendable,
                        Math.addExact(transaction.getAmount(), ConsensusRules.calculateFee(transaction.getAmount())));
            } catch (ArithmeticException e) {
                return 0L;
            }
        }

        return Math.max(0L, spendable);
    }

    private void scheduleNextAutoMode(int generation, long delay) {
        synchronized (autoModeLock) {
            if (!autoModeEnabled || !loggedIn || shuttingDown.get() || generation != autoModeGeneration)
                return;
            try {
                autoModeTask = autoModeScheduler.schedule(() -> runAutoMode(generation), delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                autoModeEnabled = false;
            }
        }
    }

    public boolean validateChain() {
        boolean lightNode = settings.nodeType == Computer.NodeType.LIGHT;
        boolean valid = lightNode
                ? networkNode.isLightHeaderChainValid()
                : blockchain.isChainValid();
        addActivity(valid ? "Chain validation passed" : "Chain validation failed",
                valid ? lightNode
                        ? "All LIGHT header hashes, PoW values and previous-hash links are consistent."
                        : "All block hashes, PoW values, transactions, balances and nonces are consistent."
                        : "The local blockchain reported a problem.",
                valid ? "success" : "danger");
        return valid;
    }

    public ActionResult requestMerkleProof(String blockHash, String transactionId) {
        if (settings.nodeType != Computer.NodeType.LIGHT)
            return ActionResult.fail("Merkle proofs can only be requested from a LIGHT node.");
        if (blockHash == null || blockHash.isBlank() || transactionId == null || transactionId.isBlank())
            return ActionResult.fail("Select a block and enter a transaction ID.");

        boolean sent = networkNode.requestMerkleProof(blockHash.trim(), transactionId.trim());
        if (!sent)
            return ActionResult.fail("No connected FULL or MINER node is available for a Merkle proof.");

        addActivity("Merkle proof requested",
                "Block " + WlanTheme.compact(blockHash, 7) + " · TX " + WlanTheme.compact(transactionId, 7),
                "info");
        return ActionResult.ok("Merkle proof request sent.", transactionId.trim());
    }

    public void addActivity(String title, String detail, String tone) {
        activity.addFirst(new ActivityView(title, detail, tone, System.currentTimeMillis()));
        while (activity.size() > MAX_ACTIVITY)
            activity.pollLast();
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank())
            return "Unknown error.";
        return exception.getMessage();
    }

    public BlockChain getBlockchain() {
        return blockchain;
    }

    public NetworkNode getNetworkNode() {
        return networkNode;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void shutdown() {

        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }

        synchronized (submitLock) {
            loggedIn = false;
        }

        synchronized (autoModeLock) {
            autoModeEnabled = false;
            autoModeGeneration++;

            if (autoModeTask != null) {
                autoModeTask.cancel(true);
            }
        }

        autoModeScheduler.shutdownNow();

        try {
            networkNode.close();
        } catch (IOException e) {
            System.out.println(
                    "The WLAN node did not close cleanly: "
                            + e.getMessage());
        }

        /*
         * Zadnji potpuni snapshot je sigurnosna mreža ako je neki
         * pojedinačni SQLite zapis ranije bio neuspješan.
         */
        try {
            synchronized (blockchain) {

                blockchainRepository.saveLocalWallet(
                        localWallet,
                        blockchain.getInitialBalance(localWallet.getAddress()));

                for (PublicWallet wallet : blockchain.getPublicWalletRegistry().values()) {

                    if (wallet.getAddress().equals(localWallet.getAddress())) {
                        continue;
                    }

                    blockchainRepository.saveNetworkWallet(
                            wallet.getAddress(),
                            wallet.getPublicKey(),
                            blockchain.getInitialBalance(wallet.getAddress()));
                }

                if (settings.nodeType != Computer.NodeType.LIGHT) {
                    blockchainRepository.replaceChain(
                            blockchain.getChainSnapshot(),
                            blockchain.getTransactionPoolSnapshot());
                }
            }

        } catch (Exception e) {
            System.out.println(
                    "Final blockchain save failed: "
                            + e.getMessage());
        }

        try {
            databaseManager.close();
        } catch (Exception e) {
            System.out.println(
                    "The SQLite database did not close cleanly: "
                            + e.getMessage());
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public static final class Settings {
        public final String nodeId;
        public final String alias;
        public final Computer.NodeType nodeType;
        public final int listenPort;
        public final String manualPeerIp;
        public final int manualPeerPort;

        public Settings(String nodeId, String alias, Computer.NodeType nodeType, int listenPort, String manualPeerIp,
                int manualPeerPort) {
            this.nodeId = nodeId == null ? "" : nodeId.trim();
            this.alias = alias == null || alias.isBlank() ? this.nodeId : alias.trim();
            this.nodeType = nodeType;
            this.listenPort = listenPort;
            this.manualPeerIp = manualPeerIp == null ? "" : manualPeerIp.trim();
            this.manualPeerPort = manualPeerPort;
        }

        private void validate() {
            if (nodeId.isBlank())
                throw new IllegalArgumentException("Node ID is required.");
            if (nodeType == null)
                throw new IllegalArgumentException("Node type is required.");
            if (listenPort < 1 || listenPort > 65535)
                throw new IllegalArgumentException("Listen port is invalid.");
            if (!manualPeerIp.isBlank() && (manualPeerPort < 1 || manualPeerPort > 65535)) {
                throw new IllegalArgumentException("Manual peer port is invalid.");
            }
        }
    }

    public static final class ActionResult {
        public final boolean success;
        public final String message;
        public final String transactionId;

        private ActionResult(boolean success, String message, String transactionId) {
            this.success = success;
            this.message = message;
            this.transactionId = transactionId;
        }

        public static ActionResult ok(String message, String transactionId) {
            return new ActionResult(true, message, transactionId);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message, null);
        }
    }

    public static final class Snapshot {
        public final String nodeId;
        public final String alias;
        public final Computer.NodeType nodeType;
        public final int listenPort;
        public final String localAddress;
        public final long localBalance;
        public final boolean lightAccountStateSynchronized;
        public final boolean loggedIn;
        public final boolean autoMode;
        public final int autoSent;
        public final int autoRejected;
        public final int peerCount;
        public final int chainHeight;
        public final String tipHash;
        public final int difficulty;
        public final BigInteger cumulativeWork;
        public final int mempoolSize;
        public final boolean mining;
        public final int localBlocksMined;
        public final long totalSupply;
        public final long uptime;
        public final List<WalletView> wallets;
        public final List<BlockView> blocks;
        public final List<TransactionView> transactions;
        public final List<ActivityView> activity;
        public final ProofView proof;
        public final boolean shuttingDown;

        private Snapshot(String nodeId, String alias, Computer.NodeType nodeType, int listenPort, String localAddress,
                long localBalance, boolean lightAccountStateSynchronized,
                boolean loggedIn, boolean autoMode, int autoSent, int autoRejected, int peerCount, int chainHeight,
                String tipHash,
                int difficulty, BigInteger cumulativeWork, int mempoolSize, boolean mining, int localBlocksMined,
                long totalSupply, long uptime,
                List<WalletView> wallets, List<BlockView> blocks, List<TransactionView> transactions,
                List<ActivityView> activity, ProofView proof, boolean shuttingDown) {
            this.nodeId = nodeId;
            this.alias = alias;
            this.nodeType = nodeType;
            this.listenPort = listenPort;
            this.localAddress = localAddress;
            this.localBalance = localBalance;
            this.lightAccountStateSynchronized = lightAccountStateSynchronized;
            this.loggedIn = loggedIn;
            this.autoMode = autoMode;
            this.autoSent = autoSent;
            this.autoRejected = autoRejected;
            this.peerCount = peerCount;
            this.chainHeight = chainHeight;
            this.tipHash = tipHash;
            this.difficulty = difficulty;
            this.cumulativeWork = cumulativeWork;
            this.mempoolSize = mempoolSize;
            this.mining = mining;
            this.localBlocksMined = localBlocksMined;
            this.totalSupply = totalSupply;
            this.uptime = uptime;
            this.wallets = walletViewsCopy(wallets);
            this.blocks = List.copyOf(blocks);
            this.transactions = List.copyOf(transactions);
            this.activity = List.copyOf(activity);
            this.proof = proof;
            this.shuttingDown = shuttingDown;
        }

        private static List<WalletView> walletViewsCopy(List<WalletView> wallets) {
            return List.copyOf(wallets);
        }
    }

    public static final class WalletView {
        public final String address;
        public final String label;
        public final long balance;
        public final boolean local;

        private WalletView(String address, String label, long balance, boolean local) {
            this.address = address;
            this.label = label;
            this.balance = balance;
            this.local = local;
        }
    }

    public static final class BlockView {
        public final int height;
        public final String hash;
        public final String previousHash;
        public final String merkleRoot;
        public final long timestamp;
        public final long nonce;
        public final int difficulty;
        public final int transactionCount;
        public final String minerAddress;

        private BlockView(int height, String hash, String previousHash, String merkleRoot, long timestamp, long nonce,
                int difficulty, int transactionCount, String minerAddress) {
            this.height = height;
            this.hash = hash;
            this.previousHash = previousHash;
            this.merkleRoot = merkleRoot;
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.difficulty = difficulty;
            this.transactionCount = transactionCount;
            this.minerAddress = minerAddress;
        }
    }

    public static final class TransactionView {
        public final String transactionId;
        public final String type;
        public final String sender;
        public final String receiver;
        public final long amount;
        public final long nonce;
        public final String status;
        public final int blockHeight;
        public final int position;

        private TransactionView(String transactionId, String type, String sender, String receiver, long amount,
                long nonce,
                String status, int blockHeight, int position) {
            this.transactionId = transactionId;
            this.type = type;
            this.sender = sender;
            this.receiver = receiver;
            this.amount = amount;
            this.nonce = nonce;
            this.status = status;
            this.blockHeight = blockHeight;
            this.position = position;
        }
    }

    public static final class ProofView {
        public final String blockHash;
        public final String transactionId;
        public final boolean verified;
        public final String message;

        private ProofView(String blockHash, String transactionId, boolean verified, String message) {
            this.blockHash = blockHash;
            this.transactionId = transactionId;
            this.verified = verified;
            this.message = message;
        }
    }

    public static final class ActivityView {
        public final String title;
        public final String detail;
        public final String tone;
        public final long timestamp;

        private ActivityView(String title, String detail, String tone, long timestamp) {
            this.title = title;
            this.detail = detail;
            this.tone = tone;
            this.timestamp = timestamp;
        }
    }
}
