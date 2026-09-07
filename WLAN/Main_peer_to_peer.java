package WLAN;

import java.net.*;
import WLAN.Ipconfig;

public class Main_peer_to_peer {
    private static final String NETWORK_ID = "MATHOS-DEVNET-1";
    private static final String GENESIS_HASH = "GENESIS_TEST";

    public static void main(String[] args) throws Exception {
        /*
         * IP adresa = koje računalo?
         * Port = koji program/usluga na tom računalu?
         * TCP = način na koji ta dva programa pouzdano komuniciraju
         * 
         * TCP - server i client side ima - u našem slučaju svaki node ce biti i jedno i
         * drugo
         * samo sto ce imati kao client i server dugacije ovlasti u ovisnosti koja je
         * vrsta čvora.
         */

        // Client client = new Client(Ipconfig.IP_OF_MY_PC, Ipconfig.PORT);

        if (Ipconfig.SERVER_MODE) {
            runServer();
        } else {
            runClient();
        }

    }

    private static void runServer() throws Exception {

        try (Server server = new Server(Ipconfig.PORT)) {

            while (true) {
                PeerConnection connection = server.acceptConnection();
                Thread peerThread = new Thread(() -> handleIncomingPeer(connection)); // prvi Thread
                peerThread.setName("peer-" + connection.getRemoteAddress() + "-" + connection.getRemotePort());

                peerThread.start();
            }
        }
    }

    private static void runClient() throws Exception {

        try (Client client = new Client(Ipconfig.PEER_IP,
                Ipconfig.PORT)) {

            HelloPayload helloPayload = createHelloPayload();

            NetworkMessage helloMessage = MessageCodec.createMessage(
                    MessageType.HELLO,
                    Ipconfig.NODE_ID,
                    null,
                    helloPayload);

            client.send(helloMessage);

            NetworkMessage response = client.receive();

            if (response == null) {
                System.out.println("Server je zatvorio konekciju bez odgovora :mimimimi:.");
                return;
            }

            if (response.getType() == MessageType.REJECT) {

                RejectPayload rejectPayload = MessageCodec.payloadAsPayloadTypeIWant(response, RejectPayload.class);

                System.out
                        .println("Konekcija odbijena: " + rejectPayload.getCode() + " | " + rejectPayload.getReason());

                return;
            }

            if (response.getType() != MessageType.HELLO_ACK) {
                System.out.println("Ocekivan je HELLO_ACK.");
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
                System.out.println("Server je odbio konekciju : " + ackPayload.getReason());

                return;
            }

            HelloPayload serverInfo = ackPayload.getNodeInfo();

            String validationError = validateHello(
                    response,
                    serverInfo);

            if (validationError != null) {
                System.out.println("Server HELLO podaci nisu valjani: " + validationError);

                return;
            }

            System.out.println("HELLO handshake uspješan puff");
            System.out.println("Server node: " + response.getSenderNodeId());

            System.out.println("Server type: " + serverInfo.getNodeType());

            pingServer(client);
        }
    }

    private static void pingServer(Client client) throws Exception {

        while (true) {
            long sentAt = System.currentTimeMillis();
            PingPayload pingPayload = new PingPayload(sentAt);

            NetworkMessage pingMessage = MessageCodec.createMessage(
                    MessageType.PING,
                    Ipconfig.NODE_ID,
                    null,
                    pingPayload);

            client.send(pingMessage);

            NetworkMessage response = client.receive();

            if (response == null) {
                System.out.println("Server se odspojio.");
                return;
            }

            if (response.getType() != MessageType.PONG) {
                System.out.println("Očekivan je PONG, primljen je: "+ response.getType());
                return;
            }

            if (!pingMessage.getMessageId().equals(response.getReplyToId())) {
                System.out.println("PONG ne odgovara poslanoj PING poruci.");

                return;
            }

            PongPayload pongPayload = MessageCodec.payloadAsPayloadTypeIWant(response,PongPayload.class);

            if (pongPayload.getPingSentAt() != sentAt) {
                System.out.println("PONG sadrži pogrešan timestamp.");
                return;
            }

            long responseTime = System.currentTimeMillis() - sentAt; // da se exploitat da se skroz pinga nekoga fixat ako se stigne.
            System.out.println("PONG primljen za " + responseTime + " ms");
            Thread.sleep(5000);
        }
    }

    private static HelloPayload createHelloPayload() {

        return new HelloPayload(
                NETWORK_ID,
                GENESIS_HASH,
                Ipconfig.NODE_TYPE,
                Ipconfig.PORT,
                0,
                GENESIS_HASH,
                "0");
    }

    private static String validateHello(NetworkMessage message, HelloPayload payload) {

        if (payload == null) {
            return "HELLO payload nedostaje.";
        }

        if (message.getProtocolVersion() != NetworkMessage.CURRENT_PROTOCOL_VERSION) {
            return "Protocol version nije podrzan.";
        }

        if (!NETWORK_ID.equals(payload.getNetworkId())) {
            return "Node pripada drugoj mrezi.";
        }

        if (!GENESIS_HASH.equals(payload.getGenesisHash())) {
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

    private static boolean isKnownNodeType(String nodeType) {

        return "FULL".equals(nodeType) || "LIGHT".equals(nodeType) || "MINER".equals(nodeType);
    }

    private static void handleIncomingPeer(PeerConnection connection) {

        try (connection) {
            NetworkMessage receivedMessage = connection.receive();
            if (receivedMessage == null) {
                System.out.println("Peer se odspojio prije HELLO poruke.");

                return;
            }

            if (receivedMessage.getType() != MessageType.HELLO) {

                NetworkMessage rejectMessage = MessageCodec.createMessage(
                        MessageType.REJECT,
                        Ipconfig.NODE_ID,
                        receivedMessage.getMessageId(),
                        new RejectPayload(
                                "EXPECTED_HELLO",
                                "Prva poruka mora biti HELLO."));

                connection.send(rejectMessage);
                return;
            }

            HelloPayload peerInfo = MessageCodec.payloadAsPayloadTypeIWant(receivedMessage, HelloPayload.class);

            String rejectionReason = validateHello(receivedMessage, peerInfo);

            boolean accepted = rejectionReason == null;
            HelloPayload ownInfo = null;

            if (accepted) {
                ownInfo = createHelloPayload();

                System.out.println("Prihvacen node: " + receivedMessage.getSenderNodeId());

                System.out.println("Node type: " + peerInfo.getNodeType());
            } else {
                System.out.println("HELLO odbijen: " + rejectionReason);
            }

            HelloAckPayload ackPayload = new HelloAckPayload(
                    accepted,
                    rejectionReason,
                    ownInfo);

            NetworkMessage ackMessage = MessageCodec.createMessage(
                    MessageType.HELLO_ACK,
                    Ipconfig.NODE_ID,
                    receivedMessage.getMessageId(),
                    ackPayload);

            connection.send(ackMessage);

            if (!accepted) { // oni koji su rejectani ovdje je njihov lifespan gotov
                return;
            }

            // za one kod kojih je handshake gotov staviti da slušaju PING PONG
            listenForMessages(connection, receivedMessage.getSenderNodeId());

        } catch (SocketException e){
            System.out.println("Peer se odspojio!");
        } catch (Exception e) {
            System.out.println("Greška tijekom komunikacije s peerom: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static void listenForMessages(PeerConnection connection, String peerNodeId) throws Exception {
        // ping pong

        while (true) {
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
                PingPayload pingPayload = MessageCodec.payloadAsPayloadTypeIWant(message, PingPayload.class);

                PongPayload pongPayload = new PongPayload(pingPayload.getSentAt(), System.currentTimeMillis());

                NetworkMessage pongMessage = MessageCodec.createMessage(
                        MessageType.PONG,
                        Ipconfig.NODE_ID,
                        message.getMessageId(),
                        pongPayload);

                connection.send(pongMessage);

                System.out.println("PING primljen od: " + peerNodeId);

            } else {

                NetworkMessage rejectMessage = MessageCodec.createMessage(
                        MessageType.REJECT,
                        Ipconfig.NODE_ID,
                        message.getMessageId(),
                        new RejectPayload(
                                "MESSAGE_NOT_SUPPORTED",
                                "Ova vrsta poruke još nije implementirana."));

                connection.send(rejectMessage);
            }
        }

    }
}
