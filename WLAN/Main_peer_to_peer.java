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

        try (Server server = new Server(Ipconfig.PORT); PeerConnection connection = server.acceptConnection()) {

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

            HelloPayload peerInfo = MessageCodec.payloadAsPayloadTypeIWant(receivedMessage,HelloPayload.class);

            String rejectionReason = validateHello(
                    receivedMessage,
                    peerInfo);

            boolean accepted = rejectionReason == null;
            HelloPayload ownInfo = null;

            if (accepted) {
                ownInfo = createHelloPayload();
                System.out.println("Prihvaćen node: " + receivedMessage.getSenderNodeId());
                System.out.println("Node type: "+ peerInfo.getNodeType());
            } else {
                System.out.println("HELLO odbijen: " + rejectionReason);
            }

            HelloAckPayload ackPayload = new HelloAckPayload(accepted,rejectionReason,ownInfo);

            NetworkMessage ackMessage = MessageCodec.createMessage(MessageType.HELLO_ACK,Ipconfig.NODE_ID, receivedMessage.getMessageId(), ackPayload);

            connection.send(ackMessage);
        }   
        //catch (Exception e) { previše se javlja grešaka mozda ce se u buducnosti napraviti samo jedan catch da se ne mora sve pisat
            // TODO: handle exception
        //}
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

                RejectPayload rejectPayload = MessageCodec.payloadAsPayloadTypeIWant(response,RejectPayload.class);

                System.out.println("Konekcija odbijena: " + rejectPayload.getCode() + " | " + rejectPayload.getReason());

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
            return "Protocol version nije podržan.";
        }

        if (!NETWORK_ID.equals(payload.getNetworkId())) {
            return "Node pripada drugoj mreži.";
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
}
