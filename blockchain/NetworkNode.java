
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.io.IOException;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkNode implements AutoCloseable {

    private static final long PING_INTERVAL = 5000;

    private final String nodeId;
    private final Computer.NodeType nodeType;
    private final int listenPort;
    private final String networkId;

    private final BlockChain blockchain;
    private final Map<String, PeerConnection> activePeers = new ConcurrentHashMap<>();

    private volatile boolean running;
    private Server server;

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
                    System.out.println("Greška kod prihvaćanja peera: " + e.getMessage());
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

            System.out.println("HELLO handshake uspješan puff");
            System.out.println("Spojen node: " + peerNodeId);
            System.out.println("Node type: " + peerInfo.getNodeType());

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

            System.out.println("Prihvaćen node: " + peerNodeId);
            System.out.println("Node type: " + peerInfo.getNodeType());

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

    public void maintainConnection(String ipAddress, int port) {

        Thread reconnectThread = new Thread(() -> {

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

    private String calculateCumulativeWork() {

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

    private void closeQuietly(PeerConnection connection) {

        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (IOException ignored) {

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

    @Override
    public void close() throws IOException {

        running = false;

        for (PeerConnection connection : activePeers.values()) {
            closeQuietly(connection);
        }

        activePeers.clear();

        if (server != null) {
            server.close();
        }
    }

}