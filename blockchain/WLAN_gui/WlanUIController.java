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

    private volatile boolean loggedIn;
    private volatile boolean autoModeEnabled;
    private volatile int autoModeGeneration;
    private volatile long lastAutoWaitingMessage;
    private ScheduledFuture<?> autoModeTask;

    private int observedPeerCount = -1;
    private int observedHeight = -1;
    private Set<String> observedPendingIds = new HashSet<>();

    private WlanUIController(BlockChain blockchain,NetworkNode networkNode,Wallet localWallet,Settings settings) {
        this.blockchain = blockchain;
        this.networkNode = networkNode;
        this.localWallet = localWallet;
        this.settings = settings;

        ThreadFactory daemonFactory = task -> {
            Thread thread = new Thread(task,"mathos-wlan-auto");
            thread.setDaemon(true);
            return thread;
        };
        autoModeScheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    public static WlanUIController start(Settings settings) throws IOException {
        if(settings == null) throw new IllegalArgumentException("Node postavke nedostaju.");
        settings.validate();

        BlockChain blockchain = new BlockChain();
        NetworkNode networkNode = new NetworkNode(
                settings.nodeId,
                settings.nodeType,
                settings.listenPort,
                NETWORK_ID,
                blockchain);

        /*
         * Wallet registriramo prije network starta kako remote node ne bi stigao
         * poslati chain prije nego što naš lokalni identity postoji.
         */
        Wallet localWallet = networkNode.registerWallet(Money.coins(100));
        WlanUIController controller = new WlanUIController(blockchain,networkNode,localWallet,settings);

        try {
            networkNode.start();

            if(settings.manualPeerIp != null && !settings.manualPeerIp.isBlank()) {
                networkNode.maintainConnection(settings.manualPeerIp.trim(),settings.manualPeerPort);
            }

            if(settings.nodeType == Computer.NodeType.MINER) {
                networkNode.startMining(localWallet.getAddress());
            }

            controller.addActivity("WLAN node je online",
                    settings.nodeId + " sluša na portu " + settings.listenPort + ". UDP discovery je zatražen za MATHOSCOIN peerove.","success");
            controller.addActivity("Session wallet je spreman",
                    settings.alias + " ima početnih 100 MATH. Login otključava potpisivanje transakcija.","info");
            return controller;
        } catch(IOException | RuntimeException e) {
            controller.shutdown();
            throw e;
        }
    }

    public Snapshot snapshot() {
        ArrayList<Block> chain;
        List<Transactions> pending;
        ArrayList<WalletView> walletViews = new ArrayList<>();
        long totalSupply = 0L;
        long localBalance;
        int difficulty;
        BigInteger cumulativeWork;
        boolean mining;

        synchronized(blockchain) {
            chain = blockchain.getChainSnapshot();
            pending = blockchain.getTransactionPoolSnapshot();

            for(PublicWallet wallet : blockchain.getPublicWalletRegistry().values()) {
                long balance = wallet.getBalance();
                try {
                    totalSupply = Math.addExact(totalSupply,balance);
                } catch(ArithmeticException ignored) {
                    totalSupply = Long.MAX_VALUE;
                }
                boolean local = wallet.getAddress().equals(localWallet.getAddress());
                walletViews.add(new WalletView(wallet.getAddress(),local ? settings.alias : "Wallet " + WlanTheme.compact(wallet.getAddress(),4),balance,local));
            }

            localBalance = localWallet.getBalance();
            difficulty = blockchain.getDifficulty();
            cumulativeWork = blockchain.getCumulativeWork();
            mining = blockchain.IsMiningInProgress();
        }
        walletViews.sort(Comparator.comparingLong((WalletView wallet) -> wallet.balance).reversed());

        ArrayList<BlockView> blockViews = new ArrayList<>();
        ArrayList<TransactionView> transactionViews = new ArrayList<>();
        int localBlocksMined = 0;

        for(Block block : chain) {
            String minerAddress = minerAddress(block);
            if(localWallet.getAddress().equals(minerAddress)) localBlocksMined++;
            blockViews.add(new BlockView(block.index,block.hash,block.previousHash,block.getMerkleRoot(),block.timestamp,
                    block.nonce,block.getDifficulty(),block.getTransactions().size(),minerAddress));

            for(int i = 0; i < block.getTransactions().size(); i++) {
                Transactions transaction = block.getTransactions().get(i);
                transactionViews.add(transactionView(transaction,"CONFIRMED",block.index,i));
            }
        }

        for(Transactions transaction : pending) {
            transactionViews.add(transactionView(transaction,"PENDING",-1,-1));
        }
        transactionViews.sort((first,second) -> {
            if(first.blockHeight != second.blockHeight) return Integer.compare(second.blockHeight,first.blockHeight);
            return Integer.compare(second.position,first.position);
        });

        int peerCount = networkNode.getConnectedPeerCount();
        int height = chain.isEmpty() ? 0 : chain.get(chain.size() - 1).index;
        observeChanges(peerCount,height,pending);

        ArrayList<ActivityView> activityViews = new ArrayList<>(activity);
        Block latest = chain.get(chain.size() - 1);

        return new Snapshot(
                settings.nodeId,settings.alias,settings.nodeType,settings.listenPort,localWallet.getAddress(),
                localBalance,loggedIn,autoModeEnabled,automaticTransactions.get(),rejectedAutomaticTransactions.get(),
                peerCount,height,latest.hash,difficulty,cumulativeWork,pending.size(),
                mining,localBlocksMined,totalSupply,System.currentTimeMillis() - startedAt,
                walletViews,blockViews,transactionViews,activityViews,shuttingDown.get());
    }

    private TransactionView transactionView(Transactions transaction,String status,int blockHeight,int position) {
        return new TransactionView(transaction.getHash(),transaction.isSystemTransaction() ? "SYSTEM REWARD" : "REGULAR",
                transaction.getSender(),transaction.getReceiver(),transaction.getAmount(),transaction.getNonce(),status,blockHeight,position);
    }

    private String minerAddress(Block block) {
        if(block.index == 0 || block.getTransactions().isEmpty()) return null;
        Transactions first = block.getTransactions().get(0);
        return first.isSystemTransaction() ? first.getReceiver() : null;
    }

    private void observeChanges(int peerCount,int height,List<Transactions> pending) {
        synchronized(observationLock) {
            if(observedPeerCount >= 0 && peerCount != observedPeerCount) {
                if(peerCount > observedPeerCount) {
                    addActivity("Mesh se proširio",peerCount + " direktnih peer konekcija je aktivno.","success");
                } else {
                    addActivity("Peer status se promijenio",peerCount + " direktnih peer konekcija je ostalo aktivno.","warning");
                }
            }

            if(observedHeight >= 0 && height > observedHeight) {
                addActivity("Novi block je potvrđen","Lokalni chain je sada na heightu " + height + ".","success");
            }

            Set<String> pendingIds = new HashSet<>();
            for(Transactions transaction : pending) pendingIds.add(transaction.getHash());
            if(observedHeight >= 0) {
                for(String pendingId : pendingIds) {
                    if(!observedPendingIds.contains(pendingId)) {
                        addActivity("Mempool je primio transakciju",WlanTheme.compact(pendingId,7),"info");
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

    public ActionResult login(String publicHash,String privateHash) {
        if(publicHash == null || publicHash.isBlank() || privateHash == null || privateHash.isBlank()) {
            return ActionResult.fail("Unesi public i private login hash.");
        }

        if(!getPublicLoginHash().equals(publicHash.trim()) || !getPrivateLoginHash().equals(privateHash.trim())) {
            addActivity("Login je odbijen","Uneseni hashevi ne pripadaju lokalnom session walletu.","danger");
            return ActionResult.fail("Public ili private login hash nije ispravan.");
        }

        synchronized(submitLock) {
            loggedIn = true;
        }
        addActivity("Lokalni wallet je otključan",settings.alias + " sada može potpisivati WLAN transakcije.","success");
        return ActionResult.ok("Login uspješan.",null);
    }

    public void logout() {
        synchronized(submitLock) {
            loggedIn = false;
        }
        setAutoMode(false);
        addActivity("Wallet je zaključan","Monitoring mreže ostaje aktivan, a potpisivanje je ugašeno.","warning");
    }

    public ActionResult sendTransaction(String receiverAddress,String amountText) {
        if(!loggedIn) return ActionResult.fail("Prvo se prijavi u lokalni wallet.");
        if(receiverAddress == null || receiverAddress.isBlank()) return ActionResult.fail("Receiver adresa nije unesena.");

        long amount;
        try {
            amount = Money.fromCoins(amountText);
        } catch(Exception e) {
            return ActionResult.fail("Upiši valjan MATH iznos s najviše 8 decimala.");
        }

        if(amount < ConsensusRules.MIN_TRANSACTION_AMOUNT) {
            return ActionResult.fail("Iznos je manji od minimalno dopuštenog.");
        }

        return submitUnits(receiverAddress.trim(),amount,false);
    }

    private ActionResult submitUnits(String receiverAddress,long amount,boolean automatic) {
        if(shuttingDown.get()) return ActionResult.fail("Node se gasi.");

        try {
            Transactions transaction;
            boolean accepted;

            synchronized(submitLock) {
                if(!loggedIn) return ActionResult.fail("Lokalni wallet je zaključan.");
                transaction = blockchain.createTransaction(localWallet,receiverAddress,amount);
                accepted = networkNode.submitTransaction(transaction);
            }

            if(!accepted) {
                if(!automatic) addActivity("Transakcija je odbijena",receiverAddress + " nije prošao lokalna consensus pravila.","danger");
                return ActionResult.fail("Blockchain nije prihvatio transakciju.");
            }

            addActivity(automatic ? "Auto mode je poslao transakciju" : "Transakcija je broadcastana",
                    settings.alias + " → " + WlanTheme.compact(receiverAddress,7) + " · " + Money.format(amount) + " MATH","info");
            return ActionResult.ok("Transakcija je dodana u mempool i poslana peerovima.",transaction.getHash());
        } catch(Exception e) {
            if(!automatic) addActivity("Slanje nije uspjelo",safeMessage(e),"danger");
            return ActionResult.fail(safeMessage(e));
        }
    }

    public ActionResult connectManually(String ipAddress,String portText) {
        if(ipAddress == null || ipAddress.isBlank()) return ActionResult.fail("IP adresa nije unesena.");

        int port;
        try {
            port = Integer.parseInt(portText.trim());
        } catch(Exception e) {
            return ActionResult.fail("Port mora biti cijeli broj.");
        }

        if(port < 1 || port > 65535) return ActionResult.fail("Port mora biti između 1 i 65535.");
        networkNode.maintainConnection(ipAddress.trim(),port);
        addActivity("Ručni peer je dodan",ipAddress.trim() + ":" + port + " je predan reconnect workeru.","info");
        return ActionResult.ok("Pokušaj povezivanja je pokrenut.",null);
    }

    public ActionResult setAutoMode(boolean enabled) {
        synchronized(autoModeLock) {
            if(enabled && !loggedIn) return ActionResult.fail("Login je potreban za Auto Mode.");
            if(shuttingDown.get()) return ActionResult.fail("Node se gasi.");
            if(autoModeEnabled == enabled) return ActionResult.ok("Auto Mode je već " + (enabled ? "uključen." : "isključen."),null);

            autoModeEnabled = enabled;
            int generation = ++autoModeGeneration;
            if(autoModeTask != null) autoModeTask.cancel(false);
            autoModeTask = null;

            if(enabled) {
                try {
                    autoModeTask = autoModeScheduler.schedule(() -> runAutoMode(generation),350,TimeUnit.MILLISECONDS);
                } catch(RejectedExecutionException e) {
                    autoModeEnabled = false;
                    return ActionResult.fail("Auto Mode worker nije dostupan.");
                }
            }
        }

        addActivity(enabled ? "Auto Mode je uključen" : "Auto Mode je isključen",
                enabled ? "Samo ovaj lokalni node nasumično stvara i broadcasta transakcije." : "Lokalni generator prometa je zaustavljen.",
                enabled ? "success" : "warning");
        return ActionResult.ok("Auto Mode je " + (enabled ? "uključen." : "isključen."),null);
    }

    private void runAutoMode(int generation) {
        if(!autoModeEnabled || !loggedIn || shuttingDown.get() || generation != autoModeGeneration) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = random.nextInt(100) < 28 ? random.nextInt(2,5) : 1;

        for(int i = 0; i < attempts; i++) {
            if(!autoModeEnabled || !loggedIn || generation != autoModeGeneration) return;
            createAutomaticTransaction(random.nextInt(10) == 0);
        }

        scheduleNextAutoMode(generation,random.nextLong(700L,2401L));
    }

    private void createAutomaticTransaction(boolean intentionallyInvalid) {
        ArrayList<String> receivers = new ArrayList<>();

        synchronized(blockchain) {
            for(String address : blockchain.getPublicWalletRegistry().keySet()) {
                if(!address.equals(localWallet.getAddress())) receivers.add(address);
            }
        }

        if(receivers.isEmpty()) {
            long now = System.currentTimeMillis();
            if(now - lastAutoWaitingMessage > 8000L) {
                lastAutoWaitingMessage = now;
                addActivity("Auto Mode čeka peer wallet","Poveži barem još jedan node kako bi generator dobio receiver adresu.","warning");
            }
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        String receiver = receivers.get(random.nextInt(receivers.size()));

        if(intentionallyInvalid) {
            ActionResult result = submitUnits(receiver,ConsensusRules.MIN_TRANSACTION_AMOUNT - 1L,true);
            rejectedAutomaticTransactions.incrementAndGet();
            addActivity("Auto Mode · test odbijen",
                    "Namjerno premalen iznos nije prošao local consensus" + (result.success ? " (neočekivano)." : "."),"danger");
            return;
        }

        long spendable = getSpendableLocalBalance();
        long preferredMinimum = Math.max(ConsensusRules.MIN_TRANSACTION_AMOUNT,5_000_000L);
        long maximum = Math.min(Money.coins(5),spendable / 8L);

        if(maximum < preferredMinimum) {
            if(spendable <= ConsensusRules.MIN_TRANSACTION_AMOUNT + ConsensusRules.calculateFee(ConsensusRules.MIN_TRANSACTION_AMOUNT)) {
                rejectedAutomaticTransactions.incrementAndGet();
                return;
            }
            preferredMinimum = ConsensusRules.MIN_TRANSACTION_AMOUNT;
            maximum = Math.max(preferredMinimum,spendable / 3L);
        }

        long amount = maximum == preferredMinimum ? maximum : random.nextLong(preferredMinimum,maximum + 1L);
        while(amount > ConsensusRules.MIN_TRANSACTION_AMOUNT
                && amount + ConsensusRules.calculateFee(amount) > spendable) amount--;

        ActionResult result = submitUnits(receiver,amount,true);
        if(result.success) automaticTransactions.incrementAndGet();
        else rejectedAutomaticTransactions.incrementAndGet();
    }

    private long getSpendableLocalBalance() {
        long spendable = localWallet.getBalance();

        for(Transactions transaction : blockchain.getTransactionPoolSnapshot()) {
            if(transaction.isSystemTransaction() || !localWallet.getAddress().equals(transaction.getSender())) continue;
            try {
                spendable = Math.subtractExact(spendable,
                        Math.addExact(transaction.getAmount(),ConsensusRules.calculateFee(transaction.getAmount())));
            } catch(ArithmeticException e) {
                return 0L;
            }
        }

        return Math.max(0L,spendable);
    }

    private void scheduleNextAutoMode(int generation,long delay) {
        synchronized(autoModeLock) {
            if(!autoModeEnabled || !loggedIn || shuttingDown.get() || generation != autoModeGeneration) return;
            try {
                autoModeTask = autoModeScheduler.schedule(() -> runAutoMode(generation),delay,TimeUnit.MILLISECONDS);
            } catch(RejectedExecutionException ignored) {
                autoModeEnabled = false;
            }
        }
    }

    public boolean validateChain() {
        boolean valid = blockchain.isChainValid();
        addActivity(valid ? "Chain validation je prošao" : "Chain validation nije prošao",
                valid ? "Svi block hashevi, PoW, transakcije, balancei i nonceovi su konzistentni." : "Lokalni blockchain je prijavio problem.",
                valid ? "success" : "danger");
        return valid;
    }

    public void addActivity(String title,String detail,String tone) {
        activity.addFirst(new ActivityView(title,detail,tone,System.currentTimeMillis()));
        while(activity.size() > MAX_ACTIVITY) activity.pollLast();
    }

    private String safeMessage(Exception exception) {
        if(exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) return "Nepoznata greška.";
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
        if(!shuttingDown.compareAndSet(false,true)) return;
        synchronized(submitLock) {
            loggedIn = false;
        }
        synchronized(autoModeLock) {
            autoModeEnabled = false;
            autoModeGeneration++;
            if(autoModeTask != null) autoModeTask.cancel(true);
        }
        autoModeScheduler.shutdownNow();
        try {
            networkNode.close();
        } catch(IOException e) {
            System.out.println("WLAN node se nije potpuno zatvorio: " + e.getMessage());
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

        public Settings(String nodeId,String alias,Computer.NodeType nodeType,int listenPort,String manualPeerIp,int manualPeerPort) {
            this.nodeId = nodeId == null ? "" : nodeId.trim();
            this.alias = alias == null || alias.isBlank() ? this.nodeId : alias.trim();
            this.nodeType = nodeType;
            this.listenPort = listenPort;
            this.manualPeerIp = manualPeerIp == null ? "" : manualPeerIp.trim();
            this.manualPeerPort = manualPeerPort;
        }

        private void validate() {
            if(nodeId.isBlank()) throw new IllegalArgumentException("Node ID nije unesen.");
            if(nodeType == null) throw new IllegalArgumentException("Node type nije odabran.");
            if(listenPort < 1 || listenPort > 65535) throw new IllegalArgumentException("Listen port nije valjan.");
            if(!manualPeerIp.isBlank() && (manualPeerPort < 1 || manualPeerPort > 65535)) {
                throw new IllegalArgumentException("Manual peer port nije valjan.");
            }
        }
    }

    public static final class ActionResult {
        public final boolean success;
        public final String message;
        public final String transactionId;

        private ActionResult(boolean success,String message,String transactionId) {
            this.success = success;
            this.message = message;
            this.transactionId = transactionId;
        }

        public static ActionResult ok(String message,String transactionId) {
            return new ActionResult(true,message,transactionId);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false,message,null);
        }
    }

    public static final class Snapshot {
        public final String nodeId;
        public final String alias;
        public final Computer.NodeType nodeType;
        public final int listenPort;
        public final String localAddress;
        public final long localBalance;
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
        public final boolean shuttingDown;

        private Snapshot(String nodeId,String alias,Computer.NodeType nodeType,int listenPort,String localAddress,long localBalance,
                boolean loggedIn,boolean autoMode,int autoSent,int autoRejected,int peerCount,int chainHeight,String tipHash,
                int difficulty,BigInteger cumulativeWork,int mempoolSize,boolean mining,int localBlocksMined,long totalSupply,long uptime,
                List<WalletView> wallets,List<BlockView> blocks,List<TransactionView> transactions,List<ActivityView> activity,boolean shuttingDown) {
            this.nodeId = nodeId;
            this.alias = alias;
            this.nodeType = nodeType;
            this.listenPort = listenPort;
            this.localAddress = localAddress;
            this.localBalance = localBalance;
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

        private WalletView(String address,String label,long balance,boolean local) {
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

        private BlockView(int height,String hash,String previousHash,String merkleRoot,long timestamp,long nonce,
                int difficulty,int transactionCount,String minerAddress) {
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

        private TransactionView(String transactionId,String type,String sender,String receiver,long amount,long nonce,
                String status,int blockHeight,int position) {
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

    public static final class ActivityView {
        public final String title;
        public final String detail;
        public final String tone;
        public final long timestamp;

        private ActivityView(String title,String detail,String tone,long timestamp) {
            this.title = title;
            this.detail = detail;
            this.tone = tone;
            this.timestamp = timestamp;
        }
    }
}
