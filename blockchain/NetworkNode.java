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
import WLAN.GetHeadersPayload;
import WLAN.HeaderPayload;
import WLAN.HeadersPayload;
import WLAN.GetMerkleProofPayload;
import WLAN.MerkleProofPayload;
import WLAN.GetAccountStatePayload;
import WLAN.AccountStatePayload;

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
    private static final long FULL_CHAIN_REQUEST_TIMEOUT = 30_000L;
    private static final long HEADER_REQUEST_TIMEOUT = 30_000L;

    private final String nodeId;
    private final Computer.NodeType nodeType;
    private final int listenPort;
    private final String networkId;

    private final BlockChain blockchain;
    private final BlockChain_LightNodes lightBlockchain;

    private final Map<String, PeerConnection> activePeers = new ConcurrentHashMap<>();
    private final Map<String, Computer.NodeType> peerNodeTypes = new ConcurrentHashMap<>();
    private final Set<String> maintainedPeerAddresses = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> fullChainRequests = new ConcurrentHashMap<>();
    private final Map<String, Long> headerRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingMerkleProofRequest> pendingMerkleProofRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingAccountStateRequest> pendingAccountStateRequests = new ConcurrentHashMap<>();
    private final Map<String, String> lightTransactionRequests = new ConcurrentHashMap<>();
    private final Map<String, Transactions> lightPendingTransactions = new ConcurrentHashMap<>();
    private final Map<String, VerifiedLightTransaction> lightVerifiedTransactions = new ConcurrentHashMap<>();

    private volatile MerkleProofResult lastMerkleProofResult;
    private volatile LightAccountState lightAccountState;
    private volatile String lightWalletAddress;

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
        this.lightBlockchain = createLightBlockchain(nodeType,blockchain,null);
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
        this.lightBlockchain = createLightBlockchain(nodeType,blockchain,blockchainRepository);
    }

    private static BlockChain_LightNodes createLightBlockchain(
            Computer.NodeType nodeType,
            BlockChain blockchain,
            BlockchainRepository blockchainRepository) {

        if (nodeType != Computer.NodeType.LIGHT) {
            return null;
        }

        BlockChain_LightNodes lightBlockchain = new BlockChain_LightNodes();

        try {
            ArrayList<BlockChain_LightNodes.BlockHeader> storedHeaders =
                    blockchainRepository == null
                            ? new ArrayList<>()
                            : blockchainRepository.loadLightHeaders();

            if (storedHeaders.isEmpty()) {
                Block genesisBlock = blockchain.getChainSnapshot().get(0);

                lightBlockchain.addBlockHeader(
                        new BlockChain_LightNodes.BlockHeader(
                                genesisBlock.index,
                                genesisBlock.previousHash,
                                genesisBlock.getMerkleRoot(),
                                genesisBlock.timestamp,
                                genesisBlock.hash,
                                genesisBlock.nonce,
                                genesisBlock.getDifficulty()));

                if (blockchainRepository != null) {
                    blockchainRepository.saveLightHeader(
                            lightBlockchain.getLastHeader());
                }
            } else {
                for (BlockChain_LightNodes.BlockHeader header : storedHeaders) {
                    if (!lightBlockchain.addBlockHeader(header)) {
                        throw new IllegalStateException(
                                "Spremljeni LIGHT headeri nisu valjani.");
                    }
                }

                String genesisHash = blockchain.getChainSnapshot().get(0).hash;

                if (!genesisHash.equals(lightBlockchain.getHeader(0).blockHash)) {
                    throw new IllegalStateException(
                            "LIGHT headeri pripadaju drugom genesis blocku.");
                }
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "LIGHT headeri nisu učitani iz baze: " + e.getMessage(),
                    e);
        }

        return lightBlockchain;
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
            peerNodeTypes.put(
                    peerNodeId,
                    Computer.NodeType.valueOf(peerInfo.getNodeType()));
            savePeerToDatabase(peerNodeId,ipAddress,peerInfo.getListenPort(),peerInfo.getNodeType());
            System.out.println("HELLO handshake uspješan puff");
            System.out.println("Spojen node: " + peerNodeId);
            System.out.println("Node type: " + peerInfo.getNodeType());

            Computer.NodeType peerNodeType = Computer.NodeType.valueOf(peerInfo.getNodeType());
            sendCurrentState(connection,peerNodeType); // ovo je outbound dio jer naš node šalje konekciju da se spoji na njega
            requestAccountState(connection,peerNodeId);
            requestChainIfPeerStronger(connection, peerInfo, peerNodeId); // isto outbound
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
            peerNodeTypes.put(
                    peerNodeId,
                    Computer.NodeType.valueOf(peerInfo.getNodeType()));
            savePeerToDatabase(peerNodeId,connection.getRemoteAddress(),peerInfo.getListenPort(),peerInfo.getNodeType());
            System.out.println("Prihvaćen node: " + peerNodeId);
            System.out.println("Node type: " + peerInfo.getNodeType());

            Computer.NodeType peerNodeType = Computer.NodeType.valueOf(peerInfo.getNodeType());
            sendCurrentState(connection,peerNodeType); // ovo je inbound dio jer čeka da se netko spoji na naš node
            requestAccountState(connection,peerNodeId);
            requestChainIfPeerStronger(connection, peerInfo, peerNodeId);
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
                peerNodeTypes.remove(peerNodeId);
            }
            if (peerNodeId != null) {
                fullChainRequests.remove(peerNodeId);
                headerRequests.remove(peerNodeId);
                removeMerkleProofRequestsForPeer(peerNodeId);
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
                peerNodeTypes.remove(peerNodeId);
                fullChainRequests.remove(peerNodeId);
                headerRequests.remove(peerNodeId);
                removeMerkleProofRequestsForPeer(peerNodeId);
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
                handleTransaction(connection, message, peerNodeId);
            } else if (message.getType() == MessageType.BLOCK) {
                handleBlock(message, peerNodeId);
            } else if (message.getType() == MessageType.GET_CHAIN) {
                handleGetChain(connection, message);
            } else if (message.getType() == MessageType.CHAIN_RESPONSE) {
                handleChainResponse(message, peerNodeId);
            } else if (message.getType() == MessageType.GET_HEADERS) {
                handleGetHeaders(connection, message);
            } else if (message.getType() == MessageType.HEADERS) {
                handleHeaders(message, peerNodeId);
            } else if (message.getType() == MessageType.GET_MERKLE_PROOF) {
                handleGetMerkleProof(connection, message);
            } else if (message.getType() == MessageType.MERKLE_PROOF) {
                handleMerkleProof(message, peerNodeId);
            } else if (message.getType() == MessageType.GET_ACCOUNT_STATE) {
                handleGetAccountState(connection,message);
            } else if (message.getType() == MessageType.ACCOUNT_STATE) {
                handleAccountState(message,peerNodeId);
            } else if (message.getType() == MessageType.REJECT) {
                RejectPayload rejectPayload = MessageCodec.payloadAsPayloadTypeIWant(
                        message,
                        RejectPayload.class);

                PendingMerkleProofRequest rejectedProof = message.getReplyToId() == null
                        ? null
                        : pendingMerkleProofRequests.get(message.getReplyToId());

                String rejectedTransactionId = message.getReplyToId() == null
                        ? null
                        : lightTransactionRequests.remove(message.getReplyToId());

                PendingAccountStateRequest rejectedAccountState = message.getReplyToId() == null
                        ? null
                        : pendingAccountStateRequests.remove(message.getReplyToId());

                if (rejectedProof != null
                        && rejectedProof.peerNodeId.equals(peerNodeId)
                        && pendingMerkleProofRequests.remove(
                                message.getReplyToId(),
                                rejectedProof)) {

                    if (!rejectedProof.automatic) {
                        lastMerkleProofResult = new MerkleProofResult(
                                rejectedProof.payload.getBlockHash(),
                                rejectedProof.payload.getTransactionId(),
                                false,
                                rejectPayload.getReason());
                    }
                }

                if (rejectedTransactionId != null) {
                    lightPendingTransactions.remove(rejectedTransactionId);
                    removePendingTransactionFromDatabase(rejectedTransactionId);
                    requestAccountState();
                }

                if (rejectedAccountState != null) {
                    System.out.println("Account state zahtjev je odbijen: " + rejectPayload.getReason());
                }

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

        if (nodeType == Computer.NodeType.LIGHT
                || peerNodeTypes.get(peerNodeId) == Computer.NodeType.LIGHT) {
            return;
        }

        PeerConnection connection = activePeers.get(peerNodeId);

        if (connection == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previousRequest = fullChainRequests.get(peerNodeId);

        if (previousRequest != null
                && now - previousRequest < FULL_CHAIN_REQUEST_TIMEOUT) {
            return;
        }

        fullChainRequests.put(peerNodeId, now);

        try {
            NetworkMessage request = MessageCodec.createMessage(
                    MessageType.GET_CHAIN,
                    nodeId,
                    null,
                    new GetChainPayload(0));

            connection.send(request);

        } catch (IOException e) {
            fullChainRequests.remove(peerNodeId);
            System.out.println(
                    "Ne mogu zatražiti chain od: "
                            + peerNodeId);
        }
    }

    private void handleGetChain(
            PeerConnection connection,
            NetworkMessage message) throws IOException {

        if (nodeType == Computer.NodeType.LIGHT) {
            sendReject(
                    connection,
                    message,
                    "LIGHT_NODE_HAS_NO_FULL_CHAIN",
                    "LIGHT node ne sprema puni blockchain.");
            return;
        }

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

        if (nodeType == Computer.NodeType.LIGHT
                || peerNodeTypes.get(peerNodeId) == Computer.NodeType.LIGHT) {
            return;
        }

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

    private void requestHeaders(
            PeerConnection connection,
            String peerNodeId) {

        if (nodeType != Computer.NodeType.LIGHT
                || connection == null
                || peerNodeTypes.get(peerNodeId) == Computer.NodeType.LIGHT) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previousRequest = headerRequests.get(peerNodeId);

        if (previousRequest != null
                && now - previousRequest < HEADER_REQUEST_TIMEOUT) {
            return;
        }

        headerRequests.put(peerNodeId,now);

        try {
            NetworkMessage request = MessageCodec.createMessage(
                    MessageType.GET_HEADERS,
                    nodeId,
                    null,
                    new GetHeadersPayload(0));

            connection.send(request);
            System.out.println("LIGHT node je zatražio block headere od: " + peerNodeId);

        } catch (IOException e) {
            headerRequests.remove(peerNodeId);
            System.out.println("LIGHT node ne može zatražiti headere od: " + peerNodeId);
        }
    }

    private void handleGetHeaders(
            PeerConnection connection,
            NetworkMessage message) throws IOException {

        if (nodeType == Computer.NodeType.LIGHT) {
            sendReject(
                    connection,
                    message,
                    "LIGHT_NODE_IS_NOT_HEADER_SOURCE",
                    "Merkle proof i header sync moraju doći od FULL ili MINER nodea.");
            return;
        }

        GetHeadersPayload request = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                GetHeadersPayload.class);

        ArrayList<Block> chainSnapshot = blockchain.getChainSnapshot();
        int fromHeight = request.getFromHeight();

        if (fromHeight < 0 || fromHeight > chainSnapshot.size()) {
            sendReject(
                    connection,
                    message,
                    "INVALID_HEADER_HEIGHT",
                    "Traženi header height nije valjan.");
            return;
        }

        ArrayList<HeaderPayload> headers = new ArrayList<>();

        for (int i = fromHeight; i < chainSnapshot.size(); i++) {
            headers.add(
                    NetworkMapper.blockToHeaderPayload(chainSnapshot.get(i)));
        }

        HeadersPayload responsePayload = new HeadersPayload(
                fromHeight,
                headers,
                blockchain.getCumulativeWork().toString());

        NetworkMessage response = MessageCodec.createMessage(
                MessageType.HEADERS,
                nodeId,
                message.getMessageId(),
                responsePayload);

        connection.send(response);
    }

    private void handleHeaders(
            NetworkMessage message,
            String peerNodeId) {

        if (nodeType != Computer.NodeType.LIGHT
                || peerNodeTypes.get(peerNodeId) == Computer.NodeType.LIGHT) {
            return;
        }

        HeadersPayload payload;

        try {
            payload = MessageCodec.payloadAsPayloadTypeIWant(
                    message,
                    HeadersPayload.class);
        } catch (IllegalArgumentException e) {
            System.out.println("Peer " + peerNodeId + " poslao je neispravne block headere.");
            return;
        }

        if (payload.getHeaders() == null || payload.getHeaders().isEmpty()) {
            return;
        }

        ArrayList<BlockChain_LightNodes.BlockHeader> currentHeaders =
                lightBlockchain.getHeadersSnapshot();

        if (payload.getStartHeight() < 0
                || payload.getStartHeight() > currentHeaders.size()) {
            requestHeaders(activePeers.get(peerNodeId),peerNodeId);
            return;
        }

        ArrayList<BlockChain_LightNodes.BlockHeader> candidateHeaders = new ArrayList<>();

        for (int i = 0; i < payload.getStartHeight(); i++) {
            candidateHeaders.add(currentHeaders.get(i));
        }

        try {
            for (HeaderPayload headerPayload : payload.getHeaders()) {
                candidateHeaders.add(
                        NetworkMapper.payloadToHeader(headerPayload));
            }

            BigInteger calculatedWork = BlockChain_LightNodes.calculateCumulativeWork(
                    candidateHeaders);
            BigInteger claimedWork = new BigInteger(payload.getCumulativeWork());

            if (!calculatedWork.equals(claimedWork)) {
                System.out.println(
                        "Peer "
                                + peerNodeId
                                + " laže o cumulative worku headera.");
                return;
            }

        } catch (Exception e) {
            System.out.println(
                    "Neispravni headeri od "
                            + peerNodeId
                            + ": "
                            + e.getMessage());
            return;
        }

        BlockChain_LightNodes.BlockHeader candidateTip =
                candidateHeaders.get(candidateHeaders.size() - 1);
        BlockChain_LightNodes.BlockHeader currentTip = lightBlockchain.getLastHeader();

        if (candidateTip.blockHash.equals(currentTip.blockHash)) {
            return;
        }

        if (!lightBlockchain.replaceHeadersIfStronger(candidateHeaders)) {
            if (BlockChain_LightNodes.calculateCumulativeWork(candidateHeaders)
                    .compareTo(lightBlockchain.getCumulativeWork()) > 0) {

                requestHeaders(activePeers.get(peerNodeId),peerNodeId);
            }
            return;
        }

        saveLightHeadersToDatabase();
        requestLightTransactionProofs(
                candidateHeaders.size() - currentHeaders.size() > 1
                        ? null
                        : candidateTip.blockHash);
        requestAccountState();

        System.out.println(
                "LIGHT node prihvatio headere do blocka #"
                        + lightBlockchain.getLastHeader().height
                        + " od nodea "
                        + peerNodeId);
    }

    private void receiveLightHeader(
            BlockChain_LightNodes.BlockHeader header,
            String peerNodeId) {

        if (nodeType != Computer.NodeType.LIGHT || header == null) {
            return;
        }

        BlockChain_LightNodes.BlockHeader currentHeader = lightBlockchain.getHeader(header.height);

        if (currentHeader != null) {
            return;
        }

        if (header.height != lightBlockchain.size()) {
            requestHeaders(activePeers.get(peerNodeId),peerNodeId);
            return;
        }

        if (!lightBlockchain.addBlockHeader(header)) {
            requestHeaders(activePeers.get(peerNodeId),peerNodeId);
            return;
        }

        saveLightHeaderToDatabase(header);
        requestLightTransactionProofs(header.blockHash);
        requestAccountState();

        System.out.println(
                "LIGHT node prihvatio header blocka #"
                        + header.height
                        + " od nodea "
                        + peerNodeId);
    }

    private void handleGetMerkleProof(
            PeerConnection connection,
            NetworkMessage message) throws IOException {

        if (nodeType == Computer.NodeType.LIGHT) {
            sendReject(
                    connection,
                    message,
                    "LIGHT_NODE_HAS_NO_TRANSACTIONS",
                    "LIGHT node nema pune blockove za stvaranje Merkle proofa.");
            return;
        }

        GetMerkleProofPayload request = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                GetMerkleProofPayload.class);

        if (request.getTransactionId() == null
                || request.getTransactionId().isBlank()) {

            sendReject(
                    connection,
                    message,
                    "INVALID_MERKLE_REQUEST",
                    "Transaction ID mora biti zadan.");
            return;
        }

        Block requestedBlock = null;

        for (Block block : blockchain.getChainSnapshot()) {
            boolean requestedExactBlock = request.getBlockHash() != null
                    && !request.getBlockHash().isBlank();
            boolean blockMatches = requestedExactBlock
                    ? request.getBlockHash().equals(block.hash)
                    : block.getTransactions().stream().anyMatch(
                            transaction -> request.getTransactionId().equals(transaction.getHash()));

            if (blockMatches) {
                requestedBlock = block;
                break;
            }
        }

        if (requestedBlock == null) {
            sendReject(
                    connection,
                    message,
                    "BLOCK_NOT_FOUND",
                    "Traženi block ne postoji.");
            return;
        }

        int transactionIndex = -1;

        for (int i = 0; i < requestedBlock.getTransactions().size(); i++) {
            if (request.getTransactionId().equals(
                    requestedBlock.getTransactions().get(i).getHash())) {

                transactionIndex = i;
                break;
            }
        }

        if (transactionIndex < 0) {
            sendReject(
                    connection,
                    message,
                    "TRANSACTION_NOT_FOUND",
                    "Transakcija ne postoji u traženom blocku.");
            return;
        }

        MerkleTree merkleTree = new MerkleTree();

        MerkleProofPayload proofPayload = new MerkleProofPayload(
                requestedBlock.hash,
                request.getTransactionId(),
                transactionIndex,
                merkleTree.getMerkleProof(
                        requestedBlock.getTransactionsToStringHashs(),
                        transactionIndex),
                requestedBlock.getMerkleRoot());

        NetworkMessage response = MessageCodec.createMessage(
                MessageType.MERKLE_PROOF,
                nodeId,
                message.getMessageId(),
                proofPayload);

        connection.send(response);
    }

    private void handleMerkleProof(
            NetworkMessage message,
            String peerNodeId) {

        if (nodeType != Computer.NodeType.LIGHT || message.getReplyToId() == null) {
            return;
        }

        expireMerkleProofRequests(System.currentTimeMillis());

        PendingMerkleProofRequest pendingRequest = pendingMerkleProofRequests.get(
                message.getReplyToId());

        if (pendingRequest == null
                || !pendingRequest.peerNodeId.equals(peerNodeId)
                || !pendingMerkleProofRequests.remove(
                        message.getReplyToId(),
                        pendingRequest)) {
            return;
        }

        GetMerkleProofPayload requestedProof = pendingRequest.payload;

        MerkleProofPayload proofPayload;

        try {
            proofPayload = MessageCodec.payloadAsPayloadTypeIWant(
                    message,
                    MerkleProofPayload.class);
        } catch (IllegalArgumentException e) {
            lastMerkleProofResult = new MerkleProofResult(
                    requestedProof.getBlockHash(),
                    requestedProof.getTransactionId(),
                    false,
                    "Merkle proof payload nije valjan.");
            return;
        }

        BlockChain_LightNodes.BlockHeader header = lightBlockchain.getHeader(
                proofPayload.getBlockHash());

        boolean requestedAnyBlock = requestedProof.getBlockHash() == null
                || requestedProof.getBlockHash().isBlank();
        boolean matchesRequest = (requestedAnyBlock
                || requestedProof.getBlockHash().equals(proofPayload.getBlockHash()))
                && requestedProof.getTransactionId().equals(proofPayload.getTransactionId());

        boolean verified = header != null
                && matchesRequest
                && header.merkleRoot.equals(proofPayload.getMerkleRoot())
                && proofPayload.getTransactionIndex() >= 0
                && proofPayload.getSiblingHashes() != null
                && lightBlockchain.verifyTransaction(
                        proofPayload.getTransactionId(),
                        proofPayload.getSiblingHashes(),
                        header.merkleRoot,
                        proofPayload.getTransactionIndex());

        lastMerkleProofResult = new MerkleProofResult(
                proofPayload.getBlockHash(),
                requestedProof.getTransactionId(),
                verified,
                verified
                        ? "Transakcija je potvrđena Merkle proofom od nodea " + peerNodeId + "."
                        : "Merkle proof od nodea " + peerNodeId + " nije valjan.");

        if (verified) {
            Transactions confirmedTransaction = lightPendingTransactions.remove(
                    requestedProof.getTransactionId());

            if (confirmedTransaction != null) {
                lightVerifiedTransactions.put(
                        confirmedTransaction.getHash(),
                        new VerifiedLightTransaction(
                                confirmedTransaction,
                                header.height,
                                header.blockHash));
                removePendingTransactionFromDatabase(confirmedTransaction.getHash());
                lightTransactionRequests.entrySet().removeIf(
                        entry -> confirmedTransaction.getHash().equals(entry.getValue()));
                requestAccountState();
            }
        }

        System.out.println(lastMerkleProofResult.getMessage());
    }

    private void handleGetAccountState(
            PeerConnection connection,
            NetworkMessage message) throws IOException {

        if (nodeType == Computer.NodeType.LIGHT) {
            sendReject(
                    connection,
                    message,
                    "LIGHT_NODE_HAS_NO_ACCOUNT_STATE",
                    "Account state mora doći od FULL ili MINER nodea.");
            return;
        }

        GetAccountStatePayload request = MessageCodec.payloadAsPayloadTypeIWant(
                message,
                GetAccountStatePayload.class);

        BlockChain.TransactionAccountState accountState = blockchain.getTransactionAccountState(
                request.getAddress());

        if (accountState == null) {
            sendReject(
                    connection,
                    message,
                    "WALLET_NOT_FOUND",
                    "Traženi wallet nije registriran na ovome blockchainu.");
            return;
        }

        Block latestBlock = blockchain.getLatestBlock();
        AccountStatePayload responsePayload = new AccountStatePayload(
                request.getAddress(),
                accountState.spendableBalance,
                accountState.nextNonce,
                latestBlock.index,
                latestBlock.hash);

        NetworkMessage response = MessageCodec.createMessage(
                MessageType.ACCOUNT_STATE,
                nodeId,
                message.getMessageId(),
                responsePayload);

        connection.send(response);
    }

    private void handleAccountState(
            NetworkMessage message,
            String peerNodeId) {

        if (nodeType != Computer.NodeType.LIGHT || message.getReplyToId() == null) {
            return;
        }

        PendingAccountStateRequest pendingRequest = pendingAccountStateRequests.remove(
                message.getReplyToId());

        if (pendingRequest == null || !pendingRequest.peerNodeId.equals(peerNodeId)) {
            return;
        }

        AccountStatePayload payload;

        try {
            payload = MessageCodec.payloadAsPayloadTypeIWant(
                    message,
                    AccountStatePayload.class);
        } catch (IllegalArgumentException e) {
            System.out.println("Account state payload nije valjan.");
            return;
        }

        if (!pendingRequest.address.equals(payload.getAddress())
                || payload.getSpendableBalance() < 0L
                || payload.getNextNonce() < 0L) {
            System.out.println("FULL node je vratio neispravan account state.");
            return;
        }

        BlockChain_LightNodes.BlockHeader stateHeader = lightBlockchain.getHeader(
                payload.getBlockHash());

        if (stateHeader == null || stateHeader.height != payload.getBlockHeight()) {
            requestHeaders(activePeers.get(peerNodeId),peerNodeId);
            return;
        }

        lightAccountState = new LightAccountState(
                payload.getAddress(),
                payload.getSpendableBalance(),
                payload.getNextNonce(),
                payload.getBlockHeight(),
                payload.getBlockHash());

        System.out.println(
                "LIGHT wallet state sinkroniziran na blocku #"
                        + payload.getBlockHeight()
                        + " | balance: "
                        + Money.format(payload.getSpendableBalance())
                        + " $MATH");
    }

    private void handleBlock(NetworkMessage message, String peerNodeId) {

        BlockPayload payload;

        try {
            payload = MessageCodec.payloadAsPayloadTypeIWant(message, BlockPayload.class);
        } catch (IllegalArgumentException e) {
            System.out.println("Peer " + peerNodeId + " poslao je neispravan block payload.");
            return;
        }

        if (nodeType == Computer.NodeType.LIGHT) {
            if (peerNodeTypes.get(peerNodeId) == Computer.NodeType.LIGHT) {
                return;
            }

            receiveLightHeader(
                    NetworkMapper.blockPayloadToHeader(payload),
                    peerNodeId);
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

        Block latestBlock = blockchain.getLatestBlock();

        if (receivedBlock.index <= latestBlock.index) {
            System.out.println(
                    "Ignoriran stari ili već riješeni fork block #"
                            + receivedBlock.index
                            + " od nodea "
                            + peerNodeId);
            return;
        }

        if (receivedBlock.index > latestBlock.index + 1) {
            System.out.println(
                    "Nedostaju blokovi prije blocka #"
                            + receivedBlock.index
                            + " od nodea "
                            + peerNodeId);
            requestFullChain(peerNodeId);
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
            HelloPayload peerInfo,
            String peerNodeId) throws IOException {

        try {
            Computer.NodeType peerNodeType = Computer.NodeType.valueOf(
                    peerInfo.getNodeType());

            BigInteger peerWork = new BigInteger(
                    peerInfo.getCumulativeWork());

            if (peerWork.signum() < 0) {
                return;
            }

            if (nodeType == Computer.NodeType.LIGHT) {
                if (peerNodeType != Computer.NodeType.LIGHT
                        && peerWork.compareTo(lightBlockchain.getCumulativeWork()) > 0) {

                    requestHeaders(connection,peerNodeId);
                }
                return;
            }

            if (peerNodeType == Computer.NodeType.LIGHT) {
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
            fullChainRequests.put(peerNodeId, System.currentTimeMillis());

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
                    peerNodeTypes.remove(peer.getKey());
                    headerRequests.remove(peer.getKey());
                    removeMerkleProofRequestsForPeer(peer.getKey());
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

        if (nodeType == Computer.NodeType.LIGHT) {
            configureLightWallet(wallet.getAddress());
        }

        broadcastMessage(MessageType.WALLET, payload, null);

        return wallet;
    }

    public void configureLightWallet(String address) {

        if (nodeType != Computer.NodeType.LIGHT || address == null || address.isBlank()) {
            return;
        }

        lightWalletAddress = address;
        BlockChain.TransactionAccountState accountState = blockchain.getTransactionAccountState(address);

        if (accountState != null) {
            BlockChain_LightNodes.BlockHeader header = lightBlockchain.getLastHeader();
            lightAccountState = new LightAccountState(
                    address,
                    accountState.spendableBalance,
                    accountState.nextNonce,
                    header.height,
                    header.blockHash);
        }

        requestAccountState();
    }

    public void restoreLightPendingTransactions(List<Transactions> transactions) {

        if (nodeType != Computer.NodeType.LIGHT
                || transactions == null
                || lightWalletAddress == null
                || lightAccountState == null) {
            return;
        }

        ArrayList<Transactions> orderedTransactions = new ArrayList<>(transactions);
        orderedTransactions.sort((first,second) -> Long.compare(first.getNonce(),second.getNonce()));
        LightAccountState accountState = lightAccountState;

        for (Transactions transaction : orderedTransactions) {
            if (transaction == null
                    || transaction.isSystemTransaction()
                    || !lightWalletAddress.equals(transaction.getSender())
                    || transaction.getNonce() != accountState.nextNonce
                    || !transaction.verifySignature()) {
                continue;
            }

            try {
                long totalAmount = Math.addExact(
                        transaction.getAmount(),
                        ConsensusRules.calculateFee(transaction.getAmount()));

                if (totalAmount > accountState.spendableBalance) {
                    continue;
                }

                lightPendingTransactions.put(transaction.getHash(),transaction);
                accountState = new LightAccountState(
                        accountState.address,
                        Math.subtractExact(accountState.spendableBalance,totalAmount),
                        Math.incrementExact(accountState.nextNonce),
                        accountState.blockHeight,
                        accountState.blockHash);

            } catch (ArithmeticException ignored) {

            }
        }

        lightAccountState = accountState;
    }

    public Transactions createTransaction(
            Wallet senderWallet,
            String receiverAddress,
            long amount) {

        if (nodeType != Computer.NodeType.LIGHT) {
            return blockchain.createTransaction(senderWallet,receiverAddress,amount);
        }

        LightAccountState accountState = lightAccountState;

        if (senderWallet == null
                || accountState == null
                || !senderWallet.getAddress().equals(accountState.address)) {
            throw new IllegalStateException("LIGHT wallet state još nije sinkroniziran s FULL/MINER nodeom.");
        }

        long totalAmount = Math.addExact(amount,ConsensusRules.calculateFee(amount));

        if (totalAmount > accountState.spendableBalance) {
            throw new IllegalArgumentException("Wallet nema dovoljno raspoloživih sredstava.");
        }

        return senderWallet.createTransaction(
                receiverAddress,
                amount,
                accountState.nextNonce);
    }

    public boolean submitTransaction(Transactions transaction) {

        if (nodeType == Computer.NodeType.LIGHT) {
            if (transaction == null
                    || transaction.isSystemTransaction()
                    || lightWalletAddress == null
                    || !lightWalletAddress.equals(transaction.getSender())
                    || transaction.getAmount() < ConsensusRules.MIN_TRANSACTION_AMOUNT
                    || !transaction.verifySignature()) {
                return false;
            }

            LightAccountState accountState = lightAccountState;

            if (accountState == null || transaction.getNonce() != accountState.nextNonce) {
                requestAccountState();
                return false;
            }

            long totalAmount;

            try {
                totalAmount = Math.addExact(
                        transaction.getAmount(),
                        ConsensusRules.calculateFee(transaction.getAmount()));
            } catch (ArithmeticException e) {
                return false;
            }

            if (totalAmount > accountState.spendableBalance) {
                return false;
            }

            for (Map.Entry<String, PeerConnection> peer : activePeers.entrySet()) {
                if (peerNodeTypes.get(peer.getKey()) == Computer.NodeType.LIGHT) {
                    continue;
                }

                NetworkMessage message = MessageCodec.createMessage(
                        MessageType.TRANSACTION,
                        nodeId,
                        null,
                        NetworkMapper.transactionToPayload(transaction));

                lightPendingTransactions.put(transaction.getHash(),transaction);
                lightTransactionRequests.put(message.getMessageId(),transaction.getHash());

                try {
                    peer.getValue().send(message);
                    lightAccountState = new LightAccountState(
                            accountState.address,
                            Math.subtractExact(accountState.spendableBalance,totalAmount),
                            Math.incrementExact(accountState.nextNonce),
                            accountState.blockHeight,
                            accountState.blockHash);
                    savePendingTransactionToDatabase(transaction);
                    return true;

                } catch (IOException | ArithmeticException e) {
                    lightPendingTransactions.remove(transaction.getHash());
                    lightTransactionRequests.remove(message.getMessageId());
                }
            }

            requestAccountState();
            return false;
        }

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

    private void handleTransaction(
            PeerConnection connection,
            NetworkMessage message,
            String peerNodeId) throws IOException {

        if (nodeType == Computer.NodeType.LIGHT) {
            return;
        }

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

            sendReject(
                    connection,
                    message,
                    "INVALID_TRANSACTION_PAYLOAD",
                    "Transaction payload nije valjan.");

            return;
        }

        if (!blockchain.addPendingTransaction(transaction)) {
            sendReject(
                    connection,
                    message,
                    "TRANSACTION_REJECTED",
                    "Transakcija nije prošla consensus provjeru.");
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
                        + Money.format(transaction.getAmount())
                        + " $MATH");

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

            Computer.NodeType peerNodeType = peerNodeTypes.get(peer.getKey());

            if (peerNodeType == Computer.NodeType.LIGHT
                    && type == MessageType.TRANSACTION) {
                continue;
            }

            try {
                MessageType outgoingType = type;
                Object outgoingPayload = payload;

                if (peerNodeType == Computer.NodeType.LIGHT
                        && type == MessageType.BLOCK
                        && payload instanceof BlockPayload) {

                    BlockPayload blockPayload = (BlockPayload) payload;
                    ArrayList<HeaderPayload> headers = new ArrayList<>();

                    headers.add(new HeaderPayload(
                            blockPayload.getIndex(),
                            blockPayload.getPreviousHash(),
                            blockPayload.getMerkleRoot(),
                            blockPayload.getTimestamp(),
                            blockPayload.getHash(),
                            blockPayload.getNonce(),
                            blockPayload.getDifficulty()));

                    outgoingType = MessageType.HEADERS;
                    outgoingPayload = new HeadersPayload(
                            blockPayload.getIndex(),
                            headers,
                            blockchain.getCumulativeWork().toString());
                }

                NetworkMessage message = MessageCodec.createMessage(
                        outgoingType,
                        nodeId,
                        null,
                        outgoingPayload);

                peer.getValue().send(message);

            } catch (IOException e) {
                System.out.println(
                        "Slanje poruke nodeu "
                                + peer.getKey()
                                + " nije uspjelo.");

                PeerConnection connection = peer.getValue();
                activePeers.remove(peer.getKey(), connection);
                peerNodeTypes.remove(peer.getKey());
                headerRequests.remove(peer.getKey());
                removeMerkleProofRequestsForPeer(peer.getKey());
                closeQuietly(connection);
            }
        }
    }

    private void sendCurrentState(
            PeerConnection connection,
            Computer.NodeType peerNodeType) throws IOException {

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

        if (nodeType == Computer.NodeType.LIGHT
                || peerNodeType == Computer.NodeType.LIGHT) {
            return;
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

        if (nodeType == Computer.NodeType.LIGHT) {
            BlockChain_LightNodes.BlockHeader genesisHeader = lightBlockchain.getHeader(0);
            BlockChain_LightNodes.BlockHeader latestHeader = lightBlockchain.getLastHeader();

            return new HelloPayload(
                    networkId,
                    genesisHeader.blockHash,
                    nodeType.name(),
                    listenPort,
                    latestHeader.height,
                    latestHeader.blockHash,
                    lightBlockchain.getCumulativeWork().toString());
        }

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

    public boolean requestMerkleProof(
            String blockHash,
            String transactionId) {

        return requestMerkleProof(blockHash,transactionId,false);
    }

    private boolean requestMerkleProof(
            String blockHash,
            String transactionId,
            boolean automatic) {

        if (nodeType != Computer.NodeType.LIGHT
                || (!automatic && (blockHash == null || blockHash.isBlank()))
                || transactionId == null
                || transactionId.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();

        expireMerkleProofRequests(now);
        if (!automatic) {
            lastMerkleProofResult = null;
        }

        for (Map.Entry<String, PeerConnection> peer : activePeers.entrySet()) {
            if (peerNodeTypes.get(peer.getKey()) == Computer.NodeType.LIGHT) {
                continue;
            }

            GetMerkleProofPayload payload = new GetMerkleProofPayload(
                    blockHash == null ? "" : blockHash,
                    transactionId);

            NetworkMessage request = MessageCodec.createMessage(
                    MessageType.GET_MERKLE_PROOF,
                    nodeId,
                    null,
                    payload);

            pendingMerkleProofRequests.put(
                    request.getMessageId(),
                    new PendingMerkleProofRequest(
                            peer.getKey(),
                            payload,
                            now,
                            automatic));

            try {
                peer.getValue().send(request);
                return true;

            } catch (IOException e) {
                pendingMerkleProofRequests.remove(request.getMessageId());
            }
        }

        if (!automatic) {
            lastMerkleProofResult = new MerkleProofResult(
                    blockHash,
                    transactionId,
                    false,
                    "Nema spojenog FULL ili MINER nodea za Merkle proof.");
        }

        return false;
    }

    private void requestLightTransactionProofs(String blockHash) {

        if (nodeType != Computer.NodeType.LIGHT) {
            return;
        }

        for (String transactionId : lightPendingTransactions.keySet()) {
            requestMerkleProof(blockHash,transactionId,true);
        }
    }

    public boolean requestAccountState() {

        if (nodeType != Computer.NodeType.LIGHT || lightWalletAddress == null) {
            return false;
        }

        pendingAccountStateRequests.clear();

        for (Map.Entry<String, PeerConnection> peer : activePeers.entrySet()) {
            if (peerNodeTypes.get(peer.getKey()) == Computer.NodeType.LIGHT) {
                continue;
            }

            if (requestAccountState(peer.getValue(),peer.getKey())) {
                return true;
            }
        }

        return false;
    }

    private boolean requestAccountState(
            PeerConnection connection,
            String peerNodeId) {

        if (nodeType != Computer.NodeType.LIGHT
                || lightWalletAddress == null
                || connection == null
                || peerNodeTypes.get(peerNodeId) == Computer.NodeType.LIGHT) {
            return false;
        }

        GetAccountStatePayload payload = new GetAccountStatePayload(lightWalletAddress);
        NetworkMessage request = MessageCodec.createMessage(
                MessageType.GET_ACCOUNT_STATE,
                nodeId,
                null,
                payload);

        pendingAccountStateRequests.put(
                request.getMessageId(),
                new PendingAccountStateRequest(
                        peerNodeId,
                        lightWalletAddress));

        try {
            connection.send(request);
            return true;

        } catch (IOException e) {
            pendingAccountStateRequests.remove(request.getMessageId());
            return false;
        }
    }

    private void removeMerkleProofRequestsForPeer(String peerNodeId) {
        if (peerNodeId == null) {
            return;
        }

        for (Map.Entry<String, PendingMerkleProofRequest> entry
                : pendingMerkleProofRequests.entrySet()) {

            PendingMerkleProofRequest request = entry.getValue();

            if (peerNodeId.equals(request.peerNodeId)
                    && pendingMerkleProofRequests.remove(entry.getKey(),request)) {

                if (!request.automatic) {
                    lastMerkleProofResult = new MerkleProofResult(
                            request.payload.getBlockHash(),
                            request.payload.getTransactionId(),
                            false,
                            "Peer se odspojio prije Merkle proof odgovora.");
                }
            }
        }
    }

    private void expireMerkleProofRequests(long now) {
        for (Map.Entry<String, PendingMerkleProofRequest> entry
                : pendingMerkleProofRequests.entrySet()) {

            PendingMerkleProofRequest request = entry.getValue();

            if (now - request.requestedAt > HEADER_REQUEST_TIMEOUT
                    && pendingMerkleProofRequests.remove(entry.getKey(),request)) {

                if (!request.automatic) {
                    lastMerkleProofResult = new MerkleProofResult(
                            request.payload.getBlockHash(),
                            request.payload.getTransactionId(),
                            false,
                            "Merkle proof odgovor nije stigao na vrijeme.");
                }
            }
        }
    }

    public MerkleProofResult getLastMerkleProofResult() {
        expireMerkleProofRequests(System.currentTimeMillis());
        return lastMerkleProofResult;
    }

    public long getLightSpendableBalance(String address) {
        LightAccountState accountState = lightAccountState;

        if (nodeType == Computer.NodeType.LIGHT
                && accountState != null
                && accountState.address.equals(address)) {
            return accountState.spendableBalance;
        }

        PublicWallet wallet = blockchain.getPublicWalletRegistry().get(address);
        return wallet == null ? 0L : wallet.getBalance();
    }

    public int getLightPendingTransactionCount() {
        return lightPendingTransactions.size();
    }

    public List<LightTransaction> getLightTransactionsSnapshot() {
        ArrayList<LightTransaction> transactions = new ArrayList<>();

        for (Transactions transaction : lightPendingTransactions.values()) {
            transactions.add(new LightTransaction(transaction,false,-1,null));
        }

        for (VerifiedLightTransaction transaction : lightVerifiedTransactions.values()) {
            transactions.add(new LightTransaction(
                    transaction.transaction,
                    true,
                    transaction.blockHeight,
                    transaction.blockHash));
        }

        return transactions;
    }

    public BlockChain_LightNodes getLightBlockchain() {
        return lightBlockchain;
    }

    public boolean isLightHeaderChainValid() {
        return nodeType == Computer.NodeType.LIGHT
                && lightBlockchain != null
                && lightBlockchain.isChainValid();
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

    private void removePendingTransactionFromDatabase(String transactionId) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.removePendingTransaction(transactionId);
        } catch (Exception e) {
            System.out.println("LIGHT transakcija nije uklonjena iz pending baze: " + e.getMessage());
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

    private void saveLightHeaderToDatabase(
            BlockChain_LightNodes.BlockHeader header) {

        if (blockchainRepository == null) {
            return;
        }

        try {
            blockchainRepository.saveLightHeader(header);
        } catch (Exception e) {
            System.out.println("LIGHT header nije spremljen u bazu: " + e.getMessage());
        }
    }

    private void saveLightHeadersToDatabase() {

        if (blockchainRepository == null || lightBlockchain == null) {
            return;
        }

        try {
            blockchainRepository.replaceLightHeaders(
                    lightBlockchain.getHeadersSnapshot());
        } catch (Exception e) {
            System.out.println("LIGHT headeri nisu spremljeni u bazu: " + e.getMessage());
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

    private static class PendingMerkleProofRequest {

        private final String peerNodeId;
        private final GetMerkleProofPayload payload;
        private final long requestedAt;
        private final boolean automatic;

        private PendingMerkleProofRequest(
                String peerNodeId,
                GetMerkleProofPayload payload,
                long requestedAt,
                boolean automatic) {

            this.peerNodeId = peerNodeId;
            this.payload = payload;
            this.requestedAt = requestedAt;
            this.automatic = automatic;
        }
    }

    private static class PendingAccountStateRequest {

        private final String peerNodeId;
        private final String address;

        private PendingAccountStateRequest(String peerNodeId,String address) {
            this.peerNodeId = peerNodeId;
            this.address = address;
        }
    }

    private static class LightAccountState {

        private final String address;
        private final long spendableBalance;
        private final long nextNonce;
        private final int blockHeight;
        private final String blockHash;

        private LightAccountState(
                String address,
                long spendableBalance,
                long nextNonce,
                int blockHeight,
                String blockHash) {

            this.address = address;
            this.spendableBalance = spendableBalance;
            this.nextNonce = nextNonce;
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
        }
    }

    private static class VerifiedLightTransaction {

        private final Transactions transaction;
        private final int blockHeight;
        private final String blockHash;

        private VerifiedLightTransaction(Transactions transaction,int blockHeight,String blockHash) {
            this.transaction = transaction;
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
        }
    }

    public static class LightTransaction {

        public final Transactions transaction;
        public final boolean verified;
        public final int blockHeight;
        public final String blockHash;

        public LightTransaction(Transactions transaction,boolean verified,int blockHeight,String blockHash) {
            this.transaction = transaction;
            this.verified = verified;
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
        }
    }

    public static class MerkleProofResult {

        private final String blockHash;
        private final String transactionId;
        private final boolean verified;
        private final String message;

        public MerkleProofResult(
                String blockHash,
                String transactionId,
                boolean verified,
                String message) {

            this.blockHash = blockHash;
            this.transactionId = transactionId;
            this.verified = verified;
            this.message = message;
        }

        public String getBlockHash() {
            return blockHash;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public boolean isVerified() {
            return verified;
        }

        public String getMessage() {
            return message;
        }
    }

    @Override
    public void close() throws IOException {

        running = false;

        for (PeerConnection connection : activePeers.values()) {
            closeQuietly(connection);
        }

        activePeers.clear();
        peerNodeTypes.clear();
        fullChainRequests.clear();
        headerRequests.clear();
        pendingMerkleProofRequests.clear();
        pendingAccountStateRequests.clear();
        lightTransactionRequests.clear();

        if (peerDiscovery != null) {
            peerDiscovery.close();
        }

        if (server != null) {
            server.close();
        }
    }

}
