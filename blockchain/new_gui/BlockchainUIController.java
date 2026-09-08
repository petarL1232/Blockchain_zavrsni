import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Ova klasa je most između Swinga i blockchain logike. GUI preko nje dobiva
 * samo sigurne snapshotove, a core klase ne moraju znati ništa o dizajnu.
 */
public class BlockchainUIController {
    private static final int MAX_ACTIVITY = 120;

    private final BlockChain blockchain;
    private final Map<String,String> labels = new ConcurrentHashMap<>();
    private final Map<String,Computer> nodes = new ConcurrentHashMap<>();
    private final Map<String,SubmittedTransaction> locallySubmitted = new ConcurrentHashMap<>();
    private final Set<String> activeMiners = ConcurrentHashMap.newKeySet();
    private final Map<String,Thread> nodeThreads = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ActivityView> activity = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean miningRaceRunning = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger threadNumber = new AtomicInteger(0);
    private final AtomicInteger lastAnnouncedBlock = new AtomicInteger(0);
    private final AtomicInteger automaticTransactions = new AtomicInteger(0);
    private final AtomicInteger rejectedAutomaticTransactions = new AtomicInteger(0);
    private final Object validationLock = new Object();
    private final Object autoModeLock = new Object();
    private final ExecutorService background;
    private final ScheduledExecutorService autoModeScheduler;

    private volatile boolean validating;
    private volatile boolean validationKnown;
    private volatile boolean chainValid;
    private volatile String loggedInAddress;
    private volatile boolean autoModeEnabled;
    private volatile int autoModeGeneration;
    private ScheduledFuture<?> autoModeTask;

    private BlockchainUIController(BlockChain blockchain) {
        this.blockchain = blockchain;
        ThreadFactory daemonFactory = task -> {
            Thread thread = new Thread(task,"mathos-gui-worker-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        background = Executors.newCachedThreadPool(daemonFactory);
        autoModeScheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    public static BlockchainUIController createDemo() {
        BlockChain blockchain = new BlockChain();
        BlockchainUIController controller = new BlockchainUIController(blockchain);

        Wallet treasury = controller.addDemoNode("Treasury",Computer.NodeType.FULL,Money.coins(1000));
        Wallet alice = controller.addDemoNode("Alice",Computer.NodeType.MINER,Money.coins(120));
        Wallet bob = controller.addDemoNode("Bob",Computer.NodeType.FULL,Money.coins(250));
        Wallet mia = controller.addDemoNode("Mia",Computer.NodeType.MINER,Money.coins(80));
        Wallet luka = controller.addDemoNode("Luka",Computer.NodeType.FULL,Money.coins(160));
        controller.addDemoNode("SPV node",Computer.NodeType.LIGHT,Money.coins(40));

        controller.submitDemoTransaction(treasury,bob,Money.coins(40));
        controller.submitDemoTransaction(treasury,alice,Money.coins(25));
        blockchain.minePendingTransactions(alice.getAddress());
        controller.announceNewBlocks();

        controller.submitDemoTransaction(bob,mia,Money.coins(12));
        controller.submitDemoTransaction(alice,luka,Money.coins(8));
        blockchain.minePendingTransactions(mia.getAddress());
        controller.announceNewBlocks();

        if(blockchain.getChain().size() != 3) {
            throw new IllegalStateException("Demo blockchain nije uspio napraviti dva sample bloka.");
        }
        controller.chainValid = blockchain.isChainValid();
        controller.validationKnown = true;
        controller.addActivity("Demo mreža je spremna","Dva bloka, šest walleta i svi tipovi nodeova su spremni za istraživanje.","success");
        return controller;
    }

    private Wallet addDemoNode(String label, Computer.NodeType type, long initialBalance) {
        Wallet wallet = blockchain.registerWallet();
        blockchain.addInitialBalance(wallet.getAddress(),initialBalance);
        Computer node = new Computer(type,wallet.getAddress(),blockchain);
        blockchain.addValidatorNode(node);
        labels.put(wallet.getAddress(),label);
        nodes.put(wallet.getAddress(),node);
        return wallet;
    }

    private void submitDemoTransaction(Wallet sender, Wallet receiver, long amount) {
        Transactions tx = blockchain.createTransaction(sender,receiver.getAddress(),amount);
        if(!blockchain.addPendingTransaction(tx)) {
            throw new IllegalStateException("Demo transakcija nije prihvaćena.");
        }
        locallySubmitted.put(tx.getHash(),new SubmittedTransaction(tx,System.currentTimeMillis()));
    }

    public BlockChain getBlockchain() {
        return blockchain;
    }

    public String labelFor(String address) {
        if(address == null || address.isBlank()) return "Nepoznata adresa";
        if("COINBASE".equals(address)) return "System";
        return labels.getOrDefault(address,shortAddress(address));
    }

    public List<LoginView> loginOptions() {
        List<Wallet> wallets;
        synchronized(blockchain) {
            wallets = new ArrayList<>(blockchain.getPrivateWalletRegistry().values());
        }
        List<LoginView> options = new ArrayList<>();
        for(Wallet wallet : wallets) options.add(loginView(wallet));
        options.sort(Comparator.comparing(option -> option.label));
        return immutable(options);
    }

    public LoginView currentLogin() {
        String address = loggedInAddress;
        if(address == null) return null;
        Wallet wallet;
        synchronized(blockchain) {
            wallet = blockchain.getPrivateWalletRegistry().get(address);
        }
        return wallet == null ? null : loginView(wallet);
    }

    public ActionResult login(String publicHash, String privateHash) {
        if(publicHash == null || publicHash.isBlank() || privateHash == null || privateHash.isBlank()) return ActionResult.fail("Unesi public i private login hash.");
        for(LoginView option : loginOptions()) {
            if(!option.publicHash.equals(publicHash.trim()) || !option.privateHash.equals(privateHash.trim())) continue;
            if(option.type == null) return ActionResult.fail("Wallet postoji, ali nije povezan s nodeom.");
            loggedInAddress = option.address;
            addActivity("Login uspješan",option.label + " je prijavljen kao " + option.type + " node.","success");
            return ActionResult.ok("Pozdrav " + option.label + " · " + option.type + ".",option.address);
        }
        addActivity("Login odbijen","Public ili private login hash nije ispravan.","danger");
        return ActionResult.fail("Login podaci nisu ispravni.");
    }

    public ActionResult logout() {
        LoginView current = currentLogin();
        if(current == null) return ActionResult.fail("Nitko nije prijavljen.");
        loggedInAddress = null;
        addActivity("Logout",current.label + " se odjavio iz dashboarda.","warning");
        return ActionResult.ok("Korisnik je odjavljen.");
    }

    private LoginView loginView(Wallet wallet) {
        Computer node = nodes.get(wallet.getAddress());
        return new LoginView(labelFor(wallet.getAddress()),wallet.getAddress(),node == null ? null : node.getType(),
                Cryptography.applySHA256(wallet.getPublicKeyString()),Cryptography.applySHA256(wallet.getPrivateKeyString()));
    }

    public Snapshot snapshot() {
        List<Block> chain = blockchain.getChain();
        Map<String,PublicWallet> walletRegistry;
        List<Computer> validators;
        synchronized(blockchain) {
            walletRegistry = new HashMap<>(blockchain.getPublicWalletRegistry());
            validators = new ArrayList<>(blockchain.getValidatorNodes());
        }

        Set<String> confirmedHashes = new HashSet<>();
        List<BlockView> blockViews = new ArrayList<>();
        List<TransactionView> transactionViews = new ArrayList<>();
        int confirmedTransactionCount = 0;

        for(Block block : chain) {
            List<TransactionView> blockTransactions = new ArrayList<>();
            String minerAddress = null;
            for(Transactions tx : block.getTransactions()) {
                if(tx.isSystemTransaction() && minerAddress == null) minerAddress = tx.getReceiver();
                confirmedHashes.add(tx.getHash());
                TransactionView view = transactionView(tx,"POTVRĐENA",block.index);
                blockTransactions.add(view);
                transactionViews.add(view);
                confirmedTransactionCount++;
            }
            blockViews.add(new BlockView(block.index,block.hash,block.previousHash,block.getMerkleRoot(),block.timestamp,block.nonce,blockTransactions,minerAddress,labelFor(minerAddress)));
        }

        boolean hasPendingTransactions = blockchain.hasPendingTransactions();
        List<SubmittedTransaction> submitted = new ArrayList<>(locallySubmitted.values());
        submitted.sort(Comparator.comparingLong(value -> value.submittedAt));
        List<TransactionView> pendingViews = new ArrayList<>();
        for(SubmittedTransaction local : submitted) {
            String hash = local.transaction.getHash();
            if(confirmedHashes.contains(hash)) continue;
            String status = hasPendingTransactions ? "PENDING · LOKALNO PRAĆENA" : "NIJE U LANCU";
            TransactionView view = transactionView(local.transaction,status,-1);
            transactionViews.add(view);
            if(hasPendingTransactions) pendingViews.add(view);
        }

        Map<String,Computer.NodeType> types = new HashMap<>();
        for(Computer validator : validators) types.put(validator.getAddress(),validator.getType());

        List<WalletView> walletViews = new ArrayList<>();
        long totalSupply = 0L;
        for(Map.Entry<String,PublicWallet> entry : walletRegistry.entrySet()) {
            long balance = entry.getValue().getBalance();
            totalSupply = Math.addExact(totalSupply,balance);
            walletViews.add(new WalletView(labelFor(entry.getKey()),entry.getKey(),balance,types.get(entry.getKey())));
        }
        walletViews.sort(Comparator.comparingLong((WalletView wallet) -> wallet.balance).reversed().thenComparing(wallet -> wallet.label));

        List<NodeView> nodeViews = new ArrayList<>();
        for(Computer validator : validators) {
            String address = validator.getAddress();
            Thread thread = nodeThreads.get(address);
            boolean running = thread != null && thread.isAlive();
            nodeViews.add(new NodeView(labelFor(address),address,validator.getType(),running,activeMiners.contains(address)));
        }
        nodeViews.sort(Comparator.comparing(node -> node.label));

        List<String> activeMinerAddresses = new ArrayList<>(activeMiners);
        activeMinerAddresses.sort(Comparator.comparing(this::labelFor));
        List<ActivityView> activityViews = new ArrayList<>(activity);
        Block latest = chain.get(chain.size() - 1);
        boolean mining = blockchain.IsMiningInProgress() || !activeMinerAddresses.isEmpty();
        return new Snapshot(blockViews,walletViews,nodeViews,transactionViews,pendingViews,activityViews,activeMinerAddresses,
                blockchain.getDifficulty(),latest.index,latest.hash,confirmedTransactionCount,totalSupply,hasPendingTransactions,
                mining,miningRaceRunning.get(),validating,validationKnown,chainValid,autoModeEnabled,automaticTransactions.get(),
                rejectedAutomaticTransactions.get(),shuttingDown.get());
    }

    private TransactionView transactionView(Transactions tx, String status, int blockIndex) {
        return new TransactionView(tx.getHash(),tx.getSender(),labelFor(tx.getSender()),tx.getReceiver(),labelFor(tx.getReceiver()),tx.getAmount(),tx.isSystemTransaction(),status,blockIndex);
    }

    public ActionResult sendTransaction(String senderAddress, String receiverAddress, String amountText) {
        return sendTransaction(senderAddress,receiverAddress,amountText,false);
    }

    private ActionResult sendTransaction(String senderAddress, String receiverAddress, String amountText, boolean automatic) {
        if(shuttingDown.get()) return ActionResult.fail("Dashboard se gasi.");
        if(senderAddress == null || receiverAddress == null) return ActionResult.fail("Odaberi sender i receiver wallet.");
        if(senderAddress.equals(receiverAddress)) return ActionResult.fail("Sender i receiver ne mogu biti isti wallet.");

        long amount;
        try {
            amount = Money.fromCoins(amountText);
        } catch(Exception e) {
            return ActionResult.fail("Upiši valjan iznos s najviše 8 decimala.");
        }
        if(amount < ConsensusRules.MIN_TRANSACTION_AMOUNT) return ActionResult.fail("Iznos je manji od minimalno dopuštenog.");

        Wallet sender;
        synchronized(blockchain) {
            sender = blockchain.getPrivateWalletRegistry().get(senderAddress);
            if(!blockchain.getPublicWalletRegistry().containsKey(receiverAddress)) return ActionResult.fail("Receiver adresa nije registrirana.");
        }
        if(sender == null) return ActionResult.fail("Privatni wallet sendera nije dostupan.");

        Transactions tx = blockchain.createTransaction(sender,receiverAddress,amount);
        if(!blockchain.addPendingTransaction(tx)) {
            if(!automatic) addActivity("Transakcija je odbijena",labelFor(senderAddress) + " nema dovoljno slobodnih sredstava ili transakcija nije valjana.","danger");
            return ActionResult.fail("Blockchain je odbio transakciju.");
        }
        locallySubmitted.put(tx.getHash(),new SubmittedTransaction(tx,System.currentTimeMillis()));
        addActivity(automatic ? "Auto mode · transakcija poslana" : "Transakcija čeka rudarenje",labelFor(senderAddress) + " → " + labelFor(receiverAddress) + " · " + Money.format(amount) + " MATHOS","info");
        return ActionResult.ok("Transakcija je dodana u mempool.",tx.getHash());
    }

    public ActionResult setAutoMode(boolean enabled) {
        if(shuttingDown.get()) return ActionResult.fail("Dashboard se gasi.");
        synchronized(autoModeLock) {
            if(autoModeEnabled == enabled) return ActionResult.ok("Auto mode je već " + (enabled ? "uključen." : "isključen."));
            autoModeEnabled = enabled;
            int generation = ++autoModeGeneration;
            if(autoModeTask != null) autoModeTask.cancel(false);
            autoModeTask = null;
            if(enabled) {
                try {
                    autoModeTask = autoModeScheduler.schedule(() -> runAutoTraffic(generation),250,TimeUnit.MILLISECONDS);
                } catch(RejectedExecutionException e) {
                    autoModeEnabled = false;
                    return ActionResult.fail("Auto mode worker nije dostupan.");
                }
            }
        }
        addActivity(enabled ? "Auto mode je uključen" : "Auto mode je isključen",enabled ? "Promet dolazi u nasumičnim razmacima i povremenim burstovima; 10% pokušaja namjerno je nevaljano." : "Automatsko stvaranje transakcija je zaustavljeno.",enabled ? "success" : "warning");
        return ActionResult.ok("Auto mode je " + (enabled ? "uključen." : "isključen."));
    }

    private void runAutoTraffic(int generation) {
        if(!autoModeEnabled || shuttingDown.get() || generation != autoModeGeneration) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = random.nextInt(100) < 25 ? random.nextInt(2,5) : 1;
        for(int i = 0; i < attempts; i++) {
            if(!autoModeEnabled || shuttingDown.get() || generation != autoModeGeneration) return;
            try {
                createAutoTransaction(random.nextInt(10) == 0);
            } catch(RuntimeException e) {
                rejectedAutomaticTransactions.incrementAndGet();
                addActivity("Auto mode · pokušaj odbijen","Generator nije napravio transakciju: " + safeMessage(e),"danger");
            }
        }
        scheduleNextAutoTraffic(generation,random.nextLong(450,1401));
    }

    private void scheduleNextAutoTraffic(int generation, long delay) {
        synchronized(autoModeLock) {
            if(!autoModeEnabled || shuttingDown.get() || generation != autoModeGeneration) return;
            try {
                autoModeTask = autoModeScheduler.schedule(() -> runAutoTraffic(generation),delay,TimeUnit.MILLISECONDS);
            } catch(RejectedExecutionException e) {
                autoModeEnabled = false;
                addActivity("Auto mode je zaustavljen","Scheduler više nije dostupan.","danger");
            }
        }
    }

    ActionResult createAutoTransaction(boolean intentionallyInvalid) {
        Snapshot current = snapshot();
        List<WalletView> nodeWallets = new ArrayList<>();
        for(WalletView wallet : current.wallets) if(wallet.nodeType != null) nodeWallets.add(wallet);
        if(nodeWallets.size() < 2) return ActionResult.fail("Auto mode treba najmanje dva node walleta.");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BlockchainUIController.WalletView sender = nodeWallets.get(random.nextInt(nodeWallets.size()));
        BlockchainUIController.WalletView receiver = nodeWallets.get(random.nextInt(nodeWallets.size() - 1));
        if(receiver.address.equals(sender.address)) receiver = nodeWallets.get(nodeWallets.size() - 1);
        if(receiver.address.equals(sender.address)) receiver = nodeWallets.get(0);

        if(intentionallyInvalid) {
            int reason = random.nextInt(3);
            ActionResult result;
            String detail;
            if(reason == 0) {
                result = sendTransaction(sender.address,sender.address,"1",true);
                detail = labelFor(sender.address) + " pokušao je poslati sredstva samome sebi.";
            } else if(reason == 1) {
                result = sendTransaction(sender.address,"AUTO_INVALID_ADDRESS","1",true);
                detail = labelFor(sender.address) + " koristio je receiver adresu koja ne postoji.";
            } else {
                result = sendTransaction(sender.address,receiver.address,"0.00000001",true);
                detail = "Iznos je bio manji od minimalno dopuštenog.";
            }
            if(result.success) throw new IllegalStateException("Namjerno nevaljana transakcija neočekivano je prihvaćena.");
            rejectedAutomaticTransactions.incrementAndGet();
            addActivity("Auto mode · pokušaj odbijen",detail,"danger");
            return ActionResult.fail(detail);
        }

        Map<String,Long> reserved = new HashMap<>();
        for(TransactionView pending : current.pendingTransactions) {
            if(pending.system) continue;
            long total;
            try {
                total = Math.addExact(pending.amount,ConsensusRules.calculateFee(pending.amount));
                reserved.merge(pending.senderAddress,total,Math::addExact);
            } catch(ArithmeticException e) {
                reserved.put(pending.senderAddress,Long.MAX_VALUE);
            }
        }
        List<WalletView> spenders = new ArrayList<>();
        for(WalletView wallet : nodeWallets) {
            long available = wallet.balance - reserved.getOrDefault(wallet.address,0L);
            if(available / 10 >= Money.UNITS_PER_COIN / 100) spenders.add(wallet);
        }
        if(spenders.isEmpty()) {
            rejectedAutomaticTransactions.incrementAndGet();
            addActivity("Auto mode · pokušaj odbijen","Nijedan wallet trenutno nema dovoljno slobodnih sredstava.","danger");
            return ActionResult.fail("Nema dovoljno slobodnih sredstava za automatsku transakciju.");
        }
        sender = spenders.get(random.nextInt(spenders.size()));
        List<WalletView> receivers = new ArrayList<>();
        for(WalletView wallet : nodeWallets) if(!wallet.address.equals(sender.address)) receivers.add(wallet);
        receiver = receivers.get(random.nextInt(receivers.size()));
        long available = sender.balance - reserved.getOrDefault(sender.address,0L);
        long oneCent = Money.UNITS_PER_COIN / 100;
        long maxCents = Math.min(500L,available / 10 / oneCent);
        long cents = random.nextLong(1L,maxCents + 1L);
        ActionResult result = sendTransaction(sender.address,receiver.address,Money.format(Math.multiplyExact(cents,oneCent)),true);
        if(result.success) automaticTransactions.incrementAndGet();
        else {
            rejectedAutomaticTransactions.incrementAndGet();
            addActivity("Auto mode · pokušaj odbijen",labelFor(sender.address) + " → " + labelFor(receiver.address) + " · " + result.message,"danger");
        }
        return result;
    }

    public ActionResult createWallet(Computer.NodeType type, String initialAmountText) {
        if(shuttingDown.get()) return ActionResult.fail("Dashboard se gasi.");
        if(type == null) return ActionResult.fail("Odaberi tip nodea.");

        long initialAmount = 0L;
        if(initialAmountText != null && !initialAmountText.isBlank()) {
            try {
                initialAmount = Money.fromCoins(initialAmountText);
            } catch(Exception e) {
                return ActionResult.fail("Početni balance nije valjan.");
            }
        }
        if(initialAmount < 0L) return ActionResult.fail("Početni balance ne smije biti negativan.");
        if(initialAmount > 0L && blockchain.getChain().size() != 1) {
            return ActionResult.fail("Početni balance može se dodati samo dok postoji samo genesis blok.");
        }

        Wallet wallet;
        try {
            wallet = blockchain.registerWallet();
            if(initialAmount > 0L) blockchain.addInitialBalance(wallet.getAddress(),initialAmount);
            Computer node = new Computer(type,wallet.getAddress(),blockchain);
            blockchain.addValidatorNode(node);
            labels.put(wallet.getAddress(),nextNodeLabel(type));
            nodes.put(wallet.getAddress(),node);
        } catch(RuntimeException e) {
            return ActionResult.fail("Node nije kreiran: " + safeMessage(e));
        }
        addActivity("Kreiran je novi " + type + " node",labelFor(wallet.getAddress()) + " · " + shortAddress(wallet.getAddress()),"success");
        return ActionResult.ok("Wallet i node su uspješno kreirani.",wallet.getAddress());
    }

    private String nextNodeLabel(Computer.NodeType type) {
        int number = 1;
        for(Computer node : nodes.values()) if(node.getType() == type) number++;
        if(type == Computer.NodeType.MINER) return "Miner " + number;
        if(type == Computer.NodeType.LIGHT) return "Light node " + number;
        return "Full node " + number;
    }

    public ActionResult setDifficulty(int difficulty) {
        if(difficulty < 1 || difficulty > 6) return ActionResult.fail("Difficulty mora biti između 1 i 6.");
        blockchain.setDifficulty(difficulty);
        validationKnown = false;
        addActivity("Difficulty je promijenjen","Nova Proof-of-Work težina je " + difficulty + ".","warning");
        return ActionResult.ok("Difficulty je postavljen na " + difficulty + ".");
    }

    public ActionResult startMiningRace() {
        if(shuttingDown.get()) return ActionResult.fail("Dashboard se gasi.");
        if(!blockchain.hasPendingTransactions()) return ActionResult.fail("Nema pending transakcija za rudarenje.");
        if(!miningRaceRunning.compareAndSet(false,true)) return ActionResult.fail("Mining race već traje.");

        List<Computer> miners = new ArrayList<>();
        for(Computer node : nodes.values()) {
            if(node.getType() == Computer.NodeType.MINER && !activeMiners.contains(node.getAddress())) miners.add(node);
        }
        miners.sort(Comparator.comparing(node -> labelFor(node.getAddress())));
        if(miners.isEmpty()) {
            miningRaceRunning.set(false);
            return ActionResult.fail("Nema slobodnih MINER nodeova.");
        }

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger remaining = new AtomicInteger(miners.size());
        for(Computer miner : miners) activeMiners.add(miner.getAddress());
        try {
            for(Computer miner : miners) background.execute(() -> {
                try {
                    start.await();
                    blockchain.minePendingTransactions(miner.getAddress());
                    announceNewBlocks();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    activeMiners.remove(miner.getAddress());
                    if(remaining.decrementAndGet() == 0) {
                        miningRaceRunning.set(false);
                        addActivity("Mining race je završio","Pobjednički blok je dodan, a ostali kandidati su odbačeni.","success");
                    }
                }
            });
        } catch(RejectedExecutionException e) {
            for(Computer miner : miners) activeMiners.remove(miner.getAddress());
            miningRaceRunning.set(false);
            return ActionResult.fail("Mining worker nije dostupan.");
        }
        addActivity("Mining race je počeo",miners.size() + " minera istovremeno radi na istom candidate bloku.","info");
        start.countDown();
        return ActionResult.ok("Pokrenuta je utrka za " + miners.size() + " minera.");
    }

    public ActionResult startNode(String address) {
        if(shuttingDown.get()) return ActionResult.fail("Dashboard se gasi.");
        Computer node = nodes.get(address);
        if(node == null) return ActionResult.fail("Node s ovom adresom ne postoji.");

        synchronized(nodeThreads) {
            Thread existing = nodeThreads.get(address);
            if(existing != null && existing.isAlive()) return ActionResult.fail("Node već radi.");
            node.running = true;
            Thread thread = new Thread(() -> runNode(node),"mathos-node-" + shortAddress(address));
            thread.setDaemon(true);
            nodeThreads.put(address,thread);
            thread.start();
        }
        addActivity(labelFor(address) + " je pokrenut",node.getType() + " node sada automatski radi u pozadini.","success");
        return ActionResult.ok("Node je pokrenut.");
    }

    private void runNode(Computer node) {
        String address = node.getAddress();
        try {
            if(node.getType() != Computer.NodeType.MINER) {
                node.run();
                return;
            }
            while(node.running && !shuttingDown.get()) {
                if(blockchain.hasPendingTransactions() && activeMiners.add(address)) {
                    try {
                        blockchain.minePendingTransactions(address);
                        announceNewBlocks();
                    } finally {
                        activeMiners.remove(address);
                    }
                }
                Thread.sleep(5000);
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            activeMiners.remove(address);
            nodeThreads.remove(address,Thread.currentThread());
        }
    }

    public ActionResult stopNode(String address) {
        Computer node = nodes.get(address);
        if(node == null) return ActionResult.fail("Node s ovom adresom ne postoji.");
        Thread thread;
        synchronized(nodeThreads) {
            thread = nodeThreads.get(address);
            if(thread == null || !thread.isAlive()) return ActionResult.fail("Node već miruje.");
            node.stopNode();
            thread.interrupt();
        }
        addActivity(labelFor(address) + " je zaustavljen","Node više ne radi automatski, ali i dalje ostaje dio mreže.","warning");
        return ActionResult.ok("Node je zaustavljen.");
    }

    /* Ova metoda namjerno može blokirati; GUI je treba pozvati preko SwingWorkera ili validateChainAsync(). */
    public boolean validateChain() {
        synchronized(validationLock) {
            validating = true;
            try {
                chainValid = blockchain.isChainValid();
                validationKnown = true;
                addActivity(chainValid ? "Lanac je valjan" : "Lanac nije valjan",chainValid ? "Hash, PoW, Merkle root i stanje walleta su potvrđeni." : "Najmanje jedna consensus provjera nije prošla.",chainValid ? "success" : "danger");
                return chainValid;
            } finally {
                validating = false;
            }
        }
    }

    public CompletableFuture<Boolean> validateChainAsync() {
        if(shuttingDown.get()) return CompletableFuture.completedFuture(false);
        try {
            return CompletableFuture.supplyAsync(this::validateChain,background);
        } catch(RejectedExecutionException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private void announceNewBlocks() {
        List<Block> chain = blockchain.getChain();
        int latestIndex = chain.get(chain.size() - 1).index;
        while(true) {
            int announced = lastAnnouncedBlock.get();
            if(announced >= latestIndex || !lastAnnouncedBlock.compareAndSet(announced,announced + 1)) break;
            Block block = chain.get(announced + 1);
            String minerAddress = block.getTransactions().isEmpty() ? null : block.getTransactions().get(0).getReceiver();
            addActivity("Blok #" + block.index + " je iskopan",labelFor(minerAddress) + " je pobijedio s nonceom " + block.nonce + ".","success");
        }
    }

    private void addActivity(String title, String detail, String tone) {
        activity.addFirst(new ActivityView(title,detail,System.currentTimeMillis(),tone));
        while(activity.size() > MAX_ACTIVITY) activity.pollLast();
    }

    public void shutdown() {
        if(!shuttingDown.compareAndSet(false,true)) return;
        synchronized(autoModeLock) {
            autoModeEnabled = false;
            autoModeGeneration++;
            if(autoModeTask != null) autoModeTask.cancel(false);
            autoModeTask = null;
        }
        for(Computer node : nodes.values()) node.stopNode();
        for(Thread thread : nodeThreads.values()) thread.interrupt();
        nodeThreads.clear();
        autoModeScheduler.shutdownNow();
        background.shutdownNow();
        addActivity("Dashboard je zaustavljen","Svi GUI node threadovi dobili su signal za gašenje.","warning");
    }

    private static String shortAddress(String address) {
        if(address == null) return "—";
        if(address.length() <= 12) return address;
        return address.substring(0,6) + "…" + address.substring(address.length() - 4);
    }

    private static String safeMessage(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static final class SubmittedTransaction {
        final Transactions transaction;
        final long submittedAt;

        SubmittedTransaction(Transactions transaction, long submittedAt) {
            this.transaction = transaction;
            this.submittedAt = submittedAt;
        }
    }

    public static final class Snapshot {
        public final List<BlockView> blocks;
        public final List<WalletView> wallets;
        public final List<NodeView> nodes;
        public final List<NodeView> validators;
        public final List<TransactionView> transactions;
        public final List<TransactionView> pendingTransactions;
        public final List<ActivityView> activity;
        public final List<String> activeMiners;
        public final int difficulty;
        public final int chainHeight;
        public final String latestHash;
        public final int confirmedTransactionCount;
        public final long totalSupply;
        public final String totalSupplyText;
        public final boolean hasPendingTransactions;
        public final boolean mining;
        public final boolean miningRaceRunning;
        public final boolean validating;
        public final boolean validationKnown;
        public final boolean chainValid;
        public final boolean autoModeEnabled;
        public final int automaticTransactions;
        public final int rejectedAutomaticTransactions;
        public final boolean shuttingDown;

        Snapshot(List<BlockView> blocks, List<WalletView> wallets, List<NodeView> nodes, List<TransactionView> transactions,
                List<TransactionView> pendingTransactions, List<ActivityView> activity, List<String> activeMiners,
                int difficulty, int chainHeight, String latestHash, int confirmedTransactionCount, long totalSupply,
                boolean hasPendingTransactions, boolean mining, boolean miningRaceRunning, boolean validating,
                boolean validationKnown, boolean chainValid, boolean autoModeEnabled, int automaticTransactions,
                int rejectedAutomaticTransactions, boolean shuttingDown) {
            this.blocks = immutable(blocks);
            this.wallets = immutable(wallets);
            this.nodes = immutable(nodes);
            this.validators = this.nodes;
            this.transactions = immutable(transactions);
            this.pendingTransactions = immutable(pendingTransactions);
            this.activity = immutable(activity);
            this.activeMiners = immutable(activeMiners);
            this.difficulty = difficulty;
            this.chainHeight = chainHeight;
            this.latestHash = latestHash;
            this.confirmedTransactionCount = confirmedTransactionCount;
            this.totalSupply = totalSupply;
            this.totalSupplyText = Money.format(totalSupply);
            this.hasPendingTransactions = hasPendingTransactions;
            this.mining = mining;
            this.miningRaceRunning = miningRaceRunning;
            this.validating = validating;
            this.validationKnown = validationKnown;
            this.chainValid = chainValid;
            this.autoModeEnabled = autoModeEnabled;
            this.automaticTransactions = automaticTransactions;
            this.rejectedAutomaticTransactions = rejectedAutomaticTransactions;
            this.shuttingDown = shuttingDown;
        }
    }

    public static final class BlockView {
        public final int index;
        public final String hash;
        public final String previousHash;
        public final String merkleRoot;
        public final long timestamp;
        public final long nonce;
        public final List<TransactionView> transactions;
        public final String minerAddress;
        public final String minerLabel;

        BlockView(int index, String hash, String previousHash, String merkleRoot, long timestamp, long nonce,
                List<TransactionView> transactions, String minerAddress, String minerLabel) {
            this.index = index;
            this.hash = hash;
            this.previousHash = previousHash;
            this.merkleRoot = merkleRoot;
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.transactions = immutable(transactions);
            this.minerAddress = minerAddress;
            this.minerLabel = minerLabel;
        }
    }

    public static final class WalletView {
        public final String label;
        public final String address;
        public final long balance;
        public final String balanceText;
        public final Computer.NodeType nodeType;

        WalletView(String label, String address, long balance, Computer.NodeType nodeType) {
            this.label = label;
            this.address = address;
            this.balance = balance;
            this.balanceText = Money.format(balance);
            this.nodeType = nodeType;
        }
    }

    public static final class NodeView {
        public final String label;
        public final String address;
        public final Computer.NodeType type;
        public final boolean running;
        public final boolean mining;

        NodeView(String label, String address, Computer.NodeType type, boolean running, boolean mining) {
            this.label = label;
            this.address = address;
            this.type = type;
            this.running = running;
            this.mining = mining;
        }
    }

    public static final class TransactionView {
        public final String hash;
        public final String senderAddress;
        public final String senderLabel;
        public final String receiverAddress;
        public final String receiverLabel;
        public final long amount;
        public final String amountText;
        public final boolean system;
        public final String status;
        public final int blockIndex;

        TransactionView(String hash, String senderAddress, String senderLabel, String receiverAddress, String receiverLabel,
                long amount, boolean system, String status, int blockIndex) {
            this.hash = hash;
            this.senderAddress = senderAddress;
            this.senderLabel = senderLabel;
            this.receiverAddress = receiverAddress;
            this.receiverLabel = receiverLabel;
            this.amount = amount;
            this.amountText = Money.format(amount);
            this.system = system;
            this.status = status;
            this.blockIndex = blockIndex;
        }
    }

    public static final class ActivityView {
        public final String title;
        public final String detail;
        public final long timestamp;
        public final String tone;

        ActivityView(String title, String detail, long timestamp, String tone) {
            this.title = title;
            this.detail = detail;
            this.timestamp = timestamp;
            this.tone = tone;
        }
    }

    public static final class LoginView {
        public final String label;
        public final String address;
        public final Computer.NodeType type;
        public final String publicHash;
        public final String privateHash;

        LoginView(String label, String address, Computer.NodeType type, String publicHash, String privateHash) {
            this.label = label;
            this.address = address;
            this.type = type;
            this.publicHash = publicHash;
            this.privateHash = privateHash;
        }

        @Override
        public String toString() {
            return label + " · " + (type == null ? "WALLET" : type);
        }
    }

    public static final class ActionResult {
        public final boolean success;
        public final String message;
        public final String value;

        private ActionResult(boolean success, String message, String value) {
            this.success = success;
            this.message = message;
            this.value = value;
        }

        public static ActionResult ok(String message) {
            return new ActionResult(true,message,null);
        }

        public static ActionResult ok(String message, String value) {
            return new ActionResult(true,message,value);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false,message,null);
        }
    }
}
