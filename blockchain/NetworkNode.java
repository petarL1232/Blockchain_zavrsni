import WLAN.HelloPayload;
import WLAN.Client;
import WLAN.HelloAckPayload;
import WLAN.MessageCodec;
import WLAN.MessageType;
import WLAN.NetworkMessage;
import WLAN.PeerConnection;
import WLAN.PingPayload;
import WLAN.PongPayload;
import WLAN.RejectPayload;
import WLAN.Server;
import WLAN.TransactionPayload;
import WLAN.WalletPayload;
import WLAN.BlockPayload;
import WLAN.ChainResponsePayload;
import WLAN.GetChainPayload;

import java.math.BigInteger;
import java.util.ArrayList;
import java.io.IOException;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean; //peak
import java.util.Set;

public class NetworkNode implements AutoCloseable {

    private static final long PING_INTERVAL = 5000;
    private static final long MINING_IDLE_WAIT = 1000L;

    private final String nodeId;
    private final Computer.NodeType nodeType;
    private final int listenPort;
    private final String networkId;

    private final BlockChain blockchain;

    private final Map<String, PeerConnection> activePeers = new ConcurrentHashMap<>();
    private final Set<String> maintainedPeerAddresses = ConcurrentHashMap.newKeySet();

    private volatile boolean running;
    private final AtomicBoolean miningLoopStarted = new AtomicBoolean(false);
    private Server server;

    private PeerDiscovery peerDiscovery;

    private final BlockchainRepository blockchainRepository;

    public NetworkNode(
            String nodeId,
            Computer.NodeType nodeType,
            int listenPort,
            String networkId,
            BlockChain blockchain) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.listenPort = listenPort;
        this.networkId = networkId;
        this.blockchain = blockchain;
        this.blockchainRepository = null;
    }

    public NetworkNode(
            String nodeId,
            Computer.NodeType nodeType,
            int listenPort,
            String networkId,
            BlockChain blockchain,
            BlockchainRepository blockchainRepository) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.listenPort = listenPort;
        this.networkId = networkId;
        this.blockchain = blockchain;
        this.blockchainRepository = blockchainRepository;
    }

    public synchronized void start() throws IOException {

        if (running) {
            return;
        }

        server = new Server(listenPort);
        running = true;

        Thread serverThread = new Thread(this::acceptPeers);
        serverThread.setName("server-" + nodeId);
        serverThread.start();

        Thread pingThread = new Thread(this::pingPeers);
        pingThread.setName("ping-" + nodeId);
        pingThread.start();

        try {
            peerDiscovery = new PeerDiscovery(this);
            peerDiscovery.start();
        } catch (IOException e) {
            System.out.println("UDP discovery nije pokrenut ): \n " + e.getMessage()
                    + ". Ručno spajanje i dalje radi ako je uneseno jel");
        }

        System.out.println("Network node pokrenut: " + nodeId);
        System.out.println("Node type: " + nodeType);
    }

    private void acceptPeers() {

        while (running) {
            try {
                PeerConnection connection = server.acceptConnection();

                Thread peerThread = new Thread(() -> handleIncomingPeer(connection));
                peerThread.setName(
                        "peer-" + connection.getRemoteAddress() + "-" + connection.getRemotePort());
                peerThread.start();

            } catch (IOException e) {
                if (running) {
                    System.out.println("Greyka kod prihvaćanja peera: " + e.getMessage());
                }
            }
        }
    }

    public void connectToPeer(String ipAddress, int port) {

        PeerConnection connection = null;
        boolean registered = false;
        String peerNodeId = null;

        try {
            Client client = new Client(ipAddress, port);
            connection = client.getConnection();

            NetworkMessage helloMessage = MessageCodec.createMessage(
                    MessageType.HELLO,
                    nodeId,
                    null,
                    createHelloPayload());

            connection.send(helloMessage);

            NetworkMessage response = connection.receive();

            if (response == null) {
                System.out.println("Peer se odspojio prije HELLO_ACK poruke.");
                return;
            }

            if (response.getType() == MessageType.REJECT) {
                RejectPayload rejectPayload = MessageCodec.payloadAsPayloadTypeIWant(
                        response,
                        RejectPayload.class);

                System.out.println(
                        "Konekcija odbijena: "
                                + rejectPayload.getCode()
                                + " | "
                                + rejectPayload.getReason());

                return;
            }

            if (response.getType() != MessageType.HELLO_ACK) {
                System.out.println("Očekivan je HELLO_ACK.");
                return;
            }

            if (!helloMessage.getMessageId().equals(response.getReplyToId())) {
                System.out.println("HELLO_ACK ne odgovara poslanoj HELLO poruci.");
                return;
            }

            HelloAckPayload ackPayload = MessageCodec.payloadAsPayloadTypeIWant(
                    response,
                    HelloAckPayload.class);

            if (!ackPayload.isAccepted()) {
                System.out.println("Peer je odbio konekciju: " + ackPayload.getReason());
                return;
            }

            HelloPayload peerInfo = ackPayload.getNodeInfo();
            String validationError = validateHello(response, peerInfo);

            if (validationError != null) {
                System.out.println("Peer HELLO podaci nisu valjani: " + validationError);
                return;
            }

            peerNodeId = response.getSenderNodeId();

            if (nodeId.equals(peerNodeId)) {
                System.out.println("Node se ne može spojiti sam na sebe.");
                return;
            }

            if (activePeers.putIfAbsent(peerNodeId, connection) != null) {
                System.out.println("Već postoji konekcija s nodeom: " + peerNodeId);
                return;
            }

            registered = true;
            savePeerToDatabase(peerNodeId,ipAddress,peerInfo.getListenPort(),peerInfo.getNodeType());
            System.out.println("HELLO handshake uspješan puff");
            System.out.println("Spojen node: " + peerNodeId);
            System.out.println("Node type: " + peerInfo.getNodeType());

            sendCurrentState(connection); // ovo je outbound dio jer naš node šalje konekciju da se spoji na njega
            requestChainIfPeerStronger(connection, peerInfo); // isto outbound
            startPeerListener(connection, peerNodeId);

        } catch (Exception e) {
            System.out.println(
                    "Neuspješno spajanje na peer "
                            + ipAddress
                            + ":"
                            + port
                            + " | "
                            + e.getMessage());
        } finally {
            if (!registered) {
                closeQuietly(connection);
            }
        }
    }

    private void handleIncomingPeer(PeerConnection connection) {

        String peerNodeId = null;
        boolean registered = false;

        try (connection) {
            NetworkMessage receivedMessage = connection.receive();

            if (receivedMessage == null) {
                System.out.println("Peer se odspojio prije HELLO poruke.");
                return;
            }

            if (receivedMessage.getType() != MessageType.HELLO) {
                sendReject(
                        connection,
                        receivedMessage,
                        "EXPECTED_HELLO",
                        "Prva poruka mora biti HELLO.");

                return;
            }

            peerNodeId = receivedMessage.getSenderNodeId();

            HelloPayload peerInfo = MessageCodec.payloadAsPayloadTypeIWant(
                    receivedMessage,
                    HelloPayload.class);

            String rejectionReason = validateHello(receivedMessage, peerInfo);

            if (rejectionReason == null && nodeId.equals(peerNodeId)) {
                rejectionReason = "Node se ne može spojiti sam na sebe.";
            }

            if (rejectionReason == null) {
                PeerConnection existingConnection = activePeers.putIfAbsent(
                        peerNodeId,
                        connection);

                if (existingConnection == null) {
                    registered = true;
                } else {
                    rejectionReason = "Konekcija s ovim nodeom već postoji.";
                }
            }

            boolean accepted = rejectionReason == null;

            HelloAckPayload ackPayload = new HelloAckPayload(
                    accepted,
                    rejectionReason,
                    accepted ? createHelloPayload() : null);

            NetworkMessage ackMessage = MessageCodec.createMessage(
                    MessageType.HELLO_ACK,
                    nodeId,
                    receivedMessage.getMessageId(),
                    ackPayload);

            connection.send(ackMessage);

            if (!accepted) {
                System.out.println("HELLO odbijen: " + rejectionReason);
                return;
            }
            savePeerToDatabase(peerNodeId,connection.getRemoteAddress(),peerInfo.getListenPort(),peerInfo.getNodeType());
            System.out.println("Prihvaćen node: " + peerNodeId);
            System.out.println("Node type: " + peerInfo.getNodeType());

            sendCurrentState(connection); // ovo je inbound dio jer čeka da se netko spoji na naš node
            requestChainIfPeerStronger(connection, peerInfo);
            listenForMessages(connection, peerNodeId);

        } catch (SocketException e) {
            System.out.println("Peer se odspojio: " + peerNodeId);
        } catch (Exception e) {
            System.out.println(
                    "Greška tijekom komunikacije s peerom "
                            + peerNodeId
                            + ": "
                            + e.getMessage());
        } finally {
            if (registered) {
                activePeers.remove(peerNodeId, connection);
            }
        }
    }

    private void startPeerListener(PeerConnection connection, String peerNodeId) {

        Thread listenerThread = new Thread(() -> {

            try (connection) {
                listenForMessages(connection, peerNodeId);

            } catch (SocketException e) {
                System.out.println("Peer se odspojio: " + peerNodeId);
            } catch (Exception e) {
                System.out.println(
                        "Greška tijekom komunikacije s peerom "
                                + peerNodeId
                                + ": "
                                + e.getMessage());
            } finally {
                activePeers.remove(peerNodeId, connection);
            }
        });

        listenerThread.setName("listener-" + peerNodeId);
        listenerThread.start();
    }

    private void listenForMessages(
            PeerConnection connection,
            String peerNodeId) throws Exception {

        while (running) {
            NetworkMessage message = connection.receive();

            if (message == null) {
                System.out.println("Peer se odspojio: " + peerNodeId);
                return;
            }

            if (!peerNodeId.equals(message.getSenderNodeId())) {
                System.out.println("Peer je promijenio node ID tijekom konekcije.");
                return;
            }

            if (message.getType() == MessageType.PING) {
                handlePing(connection, message, peerNodeId);
            } else if (message.getType() == MessageType.PONG) {
                handlePong(message, peerNodeId);
            } else if (message.getType() == MessageType.WALLET) {
                handleWallet(message, peerNodeId);
            } else if (message.getType() == MessageType.TRANSACTION) {
                handleTransaction(message, peerNodeId);
            } else if (message.getType() == MessageType.BLOCK) {
                handleBlock(message, peerNodeId);
            } else if (message.getType() == MessageType.GET_CHAIN) {
                handleGetChain(connection, message);
            } else if (message.getType() == MessageType.CHAIN_RESPONSE) {
                handleChainResponse(message, peerNodeId);
            } else if (message.getType() == MessageType.REJECT) {
                RejectPayload rejectPayload = MessageCodec.payloadAsPayloadTypeIWant(
                        message,
                        RejectPayload.class);

                System.out.println(
                        "Poruka odbijena od "
                                + peerNodeId
                                + ": "
                                + rejectPayload.getReason());

            } else {
                sendReject(
                        connection,
                        message,
                        "MESSAGE_NOT_SUPPORTED",
                        "Ova vrsta poruke još nije implementirana.");
            }
        }
    }

    private void requestFullChain(String peerNodeId) {

        PeerConnection connection = activePeers.get(peerNodeId);

        if (connection == null) {
            return;
        }

        try {
            NetworkMessage request = MessageCodec.createMessage(
                    MessageType.GET_CHAIN,
                    nodeId,
                    null,
                    new GetChainPayload(0));

            connection.send(request);

        } catch (IOException e) {
            System.out.println(
                    "Ne mogu zatražiti chain od: "
                            + peerNodeId);
        }
    }

    private void handleGetChain(
            PeerConnection connection,
            NetworkMessage message) throws IOException {

        GetChainPayload request = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                GetChainPayload.class);

        ArrayList<Block> chainSnapshot = blockchain.getChainSnapshot();

        int fromHeight = request.getFromHeight();

        if (fromHeight < 0 || fromHeight > chainSnapshot.size()) {
            sendReject(
                    connection,
                    message,
                    "INVALID_HEIGHT",
                    "Traženi chain height nije valjan.");

            return;
        }

        ArrayList<BlockPayload> blocks = new ArrayList<>();

        for (int i = fromHeight; i < chainSnapshot.size(); i++) {
            blocks.add(
                    NetworkMapper.blockToPayload(chainSnapshot.get(i)));
        }

        ChainResponsePayload responsePayload = new ChainResponsePayload(
                fromHeight,
                blocks,
                blockchain.getCumulativeWork().toString());

        NetworkMessage response = MessageCodec.createMessage(
                MessageType.CHAIN_RESPONSE,
                nodeId,
                message.getMessageId(),
                responsePayload);

        connection.send(response);
    }

    private void handleChainResponse(
            NetworkMessage message,
            String peerNodeId) {

        ChainResponsePayload payload = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                ChainResponsePayload.class);

        /*
         * Za prvi WLAN sync tražimo cijeli chain.
         * Kasnije možemo efikasno slati samo nedostajuće blokove.
         */
        if (payload.getStartHeight() != 0
                || payload.getBlocks() == null
                || payload.getBlocks().isEmpty()) {

            return;
        }

        ArrayList<Block> candidateChain = new ArrayList<>();

        try {
            for (BlockPayload blockPayload : payload.getBlocks()) {
                candidateChain.add(
                        NetworkMapper.payloadToBlock(blockPayload));
            }

            BigInteger calculatedWork = BlockChain.calculateCumulativeWork(candidateChain);

            BigInteger claimedWork = new BigInteger(payload.getCumulativeWork());

            if (!calculatedWork.equals(claimedWork)) {
                System.out.println(
                        "Peer "
                                + peerNodeId
                                + " laže o cumulative worku.");

                return;
            }

        } catch (Exception e) {
            System.out.println(
                    "Neispravan chain response od "
                            + peerNodeId
                            + ": "
                            + e.getMessage());

            return;
        }

        if (!blockchain.replaceChainIfStronger(candidateChain)) {
            return;
        }
        saveChainToDatabase();

        BlockPayload newTip = payload
                .getBlocks()
                .get(payload.getBlocks().size() - 1);

        broadcastMessage(
                MessageType.BLOCK,
                newTip,
                peerNodeId);
    }

    private void handleBlock(NetworkMessage message, String peerNodeId) {

        BlockPayload payload;

        try {
            payload = MessageCodec.payloadAsPayloadTypeIWant(message, BlockPayload.class);
        } catch (IllegalArgumentException e) {
            System.out.println("Peer " + peerNodeId + " poslao je neispravan block payload.");
            return;
        }

        /*
         * if (payload.getDifficulty() != blockchain.getDifficulty()) {
         * System.out.println(
         * "Blok od "
         * + peerNodeId
         * + " koristi pogrešan difficulty.");
         * 
         * return;
         * }
         */

        if (blockchain.hasBlockHash(payload.getHash())) {
            return;
        }

        Block receivedBlock;

        try {
            receivedBlock = NetworkMapper.payloadToBlock(payload);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "Blok od "
                            + peerNodeId
                            + " nije moguće pretvoriti: "
                            + e.getMessage());

            return;
        }

        if (!blockchain.receiveBlock(receivedBlock)) {
            System.out.println("Blok od " + peerNodeId + " nije prihvaćen.");
            requestFullChain(peerNodeId); // trazi full chain jer je mozda fork potrebno napravit
            return;
        }
        saveBlockToDatabase(receivedBlock);

        System.out.println(
                "Prihvaćen block #"
                        + receivedBlock.index
                        + " od nodea "
                        + peerNodeId
                        + " | "
                        + receivedBlock.hash);

        broadcastMessage(
                MessageType.BLOCK,
                payload,
                peerNodeId);
    }

    private void requestChainIfPeerStronger(
            PeerConnection connection,
            HelloPayload peerInfo) throws IOException {

        try {
            BigInteger peerWork = new BigInteger(
                    peerInfo.getCumulativeWork());

            if (peerWork.signum() < 0) {
                return;
            }

            if (peerWork.compareTo(
                    blockchain.getCumulativeWork()) <= 0) {

                return;
            }

            NetworkMessage request = MessageCodec.createMessage(
                    MessageType.GET_CHAIN,
                    nodeId,
                    null,
                    new GetChainPayload(0));

            connection.send(request);

            System.out.println(
                    "Peer ima jači chain. Pokrenut GET_CHAIN.");

        } catch (NumberFormatException e) {
            System.out.println(
                    "Peer je poslao neispravan cumulative work.");
        }
    }

    private void handlePing(
            PeerConnection connection,
            NetworkMessage message,
            String peerNodeId) throws IOException {

        PingPayload pingPayload = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                PingPayload.class);

        PongPayload pongPayload = new PongPayload(
                pingPayload.getSentAt(),
                System.currentTimeMillis());

        NetworkMessage pongMessage = MessageCodec.createMessage(
                MessageType.PONG,
                nodeId,
                message.getMessageId(),
                pongPayload);

        connection.send(pongMessage);

        System.out.println("PING primljen od: " + peerNodeId);
    }

    private void handlePong(NetworkMessage message, String peerNodeId) {

        PongPayload pongPayload = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                PongPayload.class);

        long responseTime = System.currentTimeMillis() - pongPayload.getPingSentAt();

        System.out.println(
                "PONG primljen od "
                        + peerNodeId
                        + " za "
                        + responseTime
                        + " ms");
    }

    private void pingPeers() {

        while (running) {
            try {
                Thread.sleep(PING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            for (Map.Entry<String, PeerConnection> peer : activePeers.entrySet()) {
                try {
                    long sentAt = System.currentTimeMillis();

                    NetworkMessage pingMessage = MessageCodec.createMessage(
                            MessageType.PING,
                            nodeId,
                            null,
                            new PingPayload(sentAt));

                    peer.getValue().send(pingMessage);

                } catch (IOException e) {
                    System.out.println(
                            "Ne mogu poslati PING nodeu: " + peer.getKey());

                    PeerConnection connection = peer.getValue();
                    activePeers.remove(peer.getKey(), connection);
                    closeQuietly(connection);
                }
            }
        }
    }

    public Wallet registerWallet(long initialBalance) {

        if (initialBalance < 0L) {
            throw new IllegalArgumentException("Početni balance ne smije biti negativan.");
        }

        Wallet wallet = blockchain.registerWallet();

        if (initialBalance > 0L) {
            blockchain.addInitialBalance(wallet.getAddress(), initialBalance);
        }
        saveLocalWalletToDatabase(wallet,blockchain.getInitialBalance(wallet.getAddress()));
        WalletPayload payload = new WalletPayload(
                wallet.getAddress(),
                wallet.getPublicKeyString(),
                initialBalance);

        broadcastMessage(MessageType.WALLET, payload, null);

        return wallet;
    }

    public boolean submitTransaction(Transactions transaction) {

        if (!blockchain.addPendingTransaction(transaction)) {
            return false;
        }
        savePendingTransactionToDatabase(transaction);
        TransactionPayload payload = NetworkMapper.transactionToPayload(transaction);
        broadcastMessage(MessageType.TRANSACTION, payload, null);

        return true;
    }

    private void handleWallet(NetworkMessage message, String peerNodeId) {

        WalletPayload payload = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                WalletPayload.class);

        boolean added = blockchain.registerNetworkWallet(
                payload.getAddress(),
                payload.getPublicKey(),
                payload.getInitialBalance());

        if (!added) {
            return;
        }
        saveNetworkWalletToDatabase(payload.getAddress(),payload.getPublicKey(),payload.getInitialBalance());

        System.out.println(
                "Wallet primljen od "
                        + peerNodeId
                        + ": "
                        + payload.getAddress());

        broadcastMessage(MessageType.WALLET, payload, peerNodeId);
    }

    private void handleTransaction(NetworkMessage message, String peerNodeId) {

        TransactionPayload payload = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                TransactionPayload.class);

        Transactions transaction;

        try {
            transaction = NetworkMapper.payloadToTransactions(payload);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "Peer "
                            + peerNodeId
                            + " poslao je neispravan transaction payload: "
                            + e.getMessage());

            return;
        }

        if (!blockchain.addPendingTransaction(transaction)) {
            return;
        }
        savePendingTransactionToDatabase(transaction);

        System.out.println(
                "Transakcija primljena od "
                        + peerNodeId
                        + ": "
                        + transaction.getSender()
                        + " -> "
                        + transaction.getReceiver()
                        + " | "
                        + Money.format(transaction.getAmount()));

        broadcastMessage(
                MessageType.TRANSACTION,
                payload,
                peerNodeId);
    }

    private void broadcastMessage(
            MessageType type,
            Object payload,
            String excludedPeerNodeId) {

        for (Map.Entry<String, PeerConnection> peer : activePeers.entrySet()) {

            if (peer.getKey().equals(excludedPeerNodeId)) {
                continue;
            }

            try {
                NetworkMessage message = MessageCodec.createMessage(
                        type,
                        nodeId,
                        null,
                        payload);

                peer.getValue().send(message);

            } catch (IOException e) {
                System.out.println(
                        "Slanje poruke nodeu "
                                + peer.getKey()
                                + " nije uspjelo.");

                PeerConnection connection = peer.getValue();
                activePeers.remove(peer.getKey(), connection);
                closeQuietly(connection);
            }
        }
    }

    private void sendCurrentState(PeerConnection connection) throws IOException {

        ArrayList<PublicWallet> wallets;

        synchronized (blockchain) {
            wallets = new ArrayList<>(
                    blockchain.getPublicWalletRegistry().values());
        }

        for (PublicWallet wallet : wallets) {

            WalletPayload walletPayload = new WalletPayload(
                    wallet.getAddress(),
                    wallet.getPublicKey(),
                    blockchain.getInitialBalance(wallet.getAddress()));

            NetworkMessage walletMessage = MessageCodec.createMessage(
                    MessageType.WALLET,
                    nodeId,
                    null,
                    walletPayload);

            connection.send(walletMessage);
        }

        List<Transactions> pendingTransactions = blockchain.getTransactionPoolSnapshot();

        for (Transactions transaction : pendingTransactions) {

            NetworkMessage transactionMessage = MessageCodec.createMessage(
                    MessageType.TRANSACTION,
                    nodeId,
                    null,
                    NetworkMapper.transactionToPayload(transaction));

            connection.send(transactionMessage);
        }
    }

    public void maintainConnection(String ipAddress, int port) {
        String peerAdress = ipAddress + ":" + port;

        if (!maintainedPeerAddresses.add(peerAdress)) {
            return;
        }

        Thread reconnectThread = new Thread(() -> {
            try {
                while (running) {

                    if (!isConnectedTo(ipAddress)) {
                        connectToPeer(ipAddress, port);
                    }

                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                maintainedPeerAddresses.remove(peerAdress);
            }
        });

        reconnectThread.setName("reconnect-" + ipAddress);
        reconnectThread.start();
    }

    private boolean isConnectedTo(String ipAddress) {

        for (PeerConnection connection : activePeers.values()) {
            if (connection.getRemoteAddress().equals(ipAddress)) {
                return true;
            }
        }

        return false;
    }

    private void sendReject(
            PeerConnection connection,
            NetworkMessage rejectedMessage,
            String code,
            String reason) throws IOException {

        NetworkMessage rejectMessage = MessageCodec.createMessage(
                MessageType.REJECT,
                nodeId,
                rejectedMessage.getMessageId(),
                new RejectPayload(code, reason));

        connection.send(rejectMessage);
    }

    private String validateHello(
            NetworkMessage message,
            HelloPayload payload) {

        if (payload == null) {
            return "HELLO payload nedostaje.";
        }

        if (message.getProtocolVersion() != NetworkMessage.CURRENT_PROTOCOL_VERSION) {
            return "Protocol version nije podržan.";
        }

        if (!networkId.equals(payload.getNetworkId())) {
            return "Node pripada drugoj mreži.";
        }

        String ownGenesisHash = blockchain.getChain().get(0).hash;

        if (!ownGenesisHash.equals(payload.getGenesisHash())) {
            return "Genesis hash se ne podudara.";
        }

        if (!isKnownNodeType(payload.getNodeType())) {
            return "Node type nije valjan.";
        }

        if (payload.getListenPort() < 1 || payload.getListenPort() > 65535) {
            return "Listen port nije valjan.";
        }

        return null;
    }

    private boolean isKnownNodeType(String receivedNodeType) {

        try {
            Computer.NodeType.valueOf(receivedNodeType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public HelloPayload createHelloPayload() {

        ArrayList<Block> chain = blockchain.getChain();

        Block genesisBlock = chain.get(0);
        Block latestBlock = chain.get(chain.size() - 1);

        return new HelloPayload(
                networkId,
                genesisBlock.hash,
                nodeType.name(),
                listenPort,
                latestBlock.index,
                latestBlock.hash,
                calculateCumulativeWork());
    }

    private String calculateCumulativeWorkIfSameDifficultyEverywhere() {

        int minedBlocks = Math.max(0, blockchain.getChain().size() - 1); // genesis se ne racuna
        BigInteger workPerBlock = BigInteger.ONE.shiftLeft(blockchain.getDifficulty() * 4); // * 4 zato što jedna hex 0
                                                                                            // predstavlja 4 bita da su
                                                                                            // točno postavljena
        // npr. difficulty = 3, ukupno 3 * 4 = 12 bitova mora biti postavljeno na 0 kako
        // bi prva 3 chara bila stavljena na 0
        // procjenjeno koliko ce pokusaja za to trebati je 4096 = 2^12 (shiftleft to
        // radi na brz način)

        return workPerBlock.multiply(BigInteger.valueOf(minedBlocks)).toString();
    }

    private String calculateCumulativeWork() {
        return blockchain.getCumulativeWork().toString();
    }

    private void closeQuietly(PeerConnection connection) {

        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (IOException ignored) {

        }
    }

    public void startMining(String minerAddress) {

        if (nodeType != Computer.NodeType.MINER) {
            throw new IllegalStateException("Samo MINER node može pokrenuti rudarenje.");
        }

        if (!blockchain.getPublicWalletRegistry().containsKey(minerAddress)) {
            throw new IllegalArgumentException("Miner wallet nije registriran.");
        }

        if (!miningLoopStarted.compareAndSet(false, true)) {
            return;
        }

        Thread miningThread = new Thread(() -> miningLoop(minerAddress));
        miningThread.setName("miner-" + nodeId);
        miningThread.start();
    }

    private void miningLoop(String minerAddress) {

        while (running) {

            if (!blockchain.hasPendingTransactions()) {

                try {
                    Thread.sleep(MINING_IDLE_WAIT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                continue;
            }

            System.out.println("Miner " + nodeId + " započinje rudarenje jer mempool nije prazan.");

            Block minedBlock = blockchain.minePendingTransactionsForNetwork(
                    minerAddress);

            if (minedBlock == null) {
                System.out.println("Miner " + nodeId + " je izgubio mining utrku.");

                continue;
            }
            saveBlockToDatabase(minedBlock);

            BlockPayload payload = NetworkMapper.blockToPayload(minedBlock);
            broadcastMessage(
                    MessageType.BLOCK,
                    payload,
                    null);

            System.out.println(
                    "Block #"
                            + minedBlock.index
                            + " poslan cijeloj mreži.");
        }
    }

    public int getConnectedPeerCount() {
        return activePeers.size();
    }

    public String getNodeId() {
        return nodeId;
    }

    public Computer.NodeType getNodeType() {
        return nodeType;
    }

    public BlockChain getBlockchain() {
        return blockchain;
    }

    private void saveLocalWalletToDatabase(
            Wallet wallet,
            long initialBalance) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.saveLocalWallet(
                    wallet,
                    initialBalance);
        } catch (Exception e) {
            System.out.println("Lokalni wallet nije spremljen u bazu: "+ e.getMessage());
        }
    }

    private void saveNetworkWalletToDatabase(
            String address,
            String publicKey,
            long initialBalance) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.saveNetworkWallet(
                    address,
                    publicKey,
                    initialBalance);
        } catch (Exception e) {
            System.out.println("Network wallet nije spremljen u bazu: "+ e.getMessage());
        }
    }

    private void savePendingTransactionToDatabase(
            Transactions transaction) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.savePendingTransaction(transaction);
        } catch (Exception e) {
            System.out.println("Pending transakcija nije spremljena u bazu: "+ e.getMessage());
        }
    }

    private void saveBlockToDatabase(Block block) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.saveAcceptedBlock(
                    block,
                    blockchain.getTransactionPoolSnapshot());
        } catch (Exception e) {
            System.out.println("Block nije spremljen u bazu: "+ e.getMessage());
        }
    }

    private void saveChainToDatabase() {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.replaceChain(
                    blockchain.getChainSnapshot(),
                    blockchain.getTransactionPoolSnapshot());
        } catch (Exception e) {
            System.out.println("Novi chain nije spremljen u bazu: " + e.getMessage());
        }
    }

    private void savePeerToDatabase(
            String peerNodeId,
            String host,
            int port,
            String peerNodeType) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.savePeer(
                    peerNodeId,
                    host,
                    port,
                    peerNodeType);
        } catch (Exception e) {
            System.out.println("Peer nije spremljen u bazu: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {

        running = false;

        for (PeerConnection connection : activePeers.values()) {
            closeQuietly(connection);
        }

        activePeers.clear();

        if (peerDiscovery != null) {
            peerDiscovery.close();
        }

        if (server != null) {
            server.close();
        }
    }

}