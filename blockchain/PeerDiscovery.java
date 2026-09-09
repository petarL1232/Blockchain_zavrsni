import WLAN.DiscoveryPayload;
import WLAN.HelloPayload;
import WLAN.MessageCodec;
import WLAN.MessageType;
import WLAN.NetworkMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class PeerDiscovery implements AutoCloseable {

    private static final int DISCOVERY_PORT = WLAN.Ipconfig.DISCOVERY_PORT; // staviti u ipconfig
    private static final int BUFFER_SIZE = 8192;
    private static final long DISCOVERY_INTERVAL = 5000L;

    private final NetworkNode networkNode;

    private volatile boolean running;
    private DatagramSocket socket;
    private Thread listenerThread;
    private Thread broadcastThread;

    public PeerDiscovery(NetworkNode networkNode) {
        this.networkNode = networkNode;
    }

    public synchronized void start() throws IOException {

        if (running) {
            return;
        }

        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(DISCOVERY_PORT));

        running = true;

        listenerThread = new Thread(this::listenForDiscoveryMessages);
        listenerThread.setName("udp-discovery-listener-" + networkNode.getNodeId());
        listenerThread.start();

        broadcastThread = new Thread(this::broadcastDiscoveryMessages);
        broadcastThread.setName("udp-discovery-broadcast-" + networkNode.getNodeId());
        broadcastThread.start();

        System.out.println("UDP peer discovery pokrenut na portu: " + DISCOVERY_PORT);
    }

    private void listenForDiscoveryMessages() {

        byte[] buffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
                socket.receive(packet);

                String json = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8);

                NetworkMessage message = MessageCodec.fromJson(json);

                if (message.getType() != MessageType.DISCOVER
                        && message.getType() != MessageType.DISCOVER_REPLY) {

                    continue;
                }

                DiscoveryPayload payload = MessageCodec.payloadAsPayloadTypeIWant(
                        message,
                        DiscoveryPayload.class);

                if (!isCompatible(message,payload)) {
                    continue;
                }

                if (message.getType() == MessageType.DISCOVER) {
                    sendDiscoveryReply(packet,message);
                } else {
                    handleDiscoveryReply(packet,message,payload);
                }

            } catch (SocketException e) {
                if (running) {
                    System.out.println("UDP discovery socket greska: " + e.getMessage());
                }
                return;
            } catch (Exception e) {
                if (running) {
                    System.out.println("Neispravna UDP discovery poruka: " + e.getMessage());
                }
            }
        }
    }

    private boolean isCompatible(NetworkMessage message,DiscoveryPayload payload) {

        if (payload == null || message.getProtocolVersion() != NetworkMessage.CURRENT_PROTOCOL_VERSION) {

            return false;
        }

        if (networkNode.getNodeId().equals(message.getSenderNodeId())) {
            return false;
        }

        HelloPayload ownInfo = networkNode.createHelloPayload();

        if (!ownInfo.getNetworkId().equals(payload.getNetworkId())) {
            return false;
        }

        if (!ownInfo.getGenesisHash().equals(payload.getGenesisHash())) {
            return false;
        }

        return payload.getListenPort() >= 1 && payload.getListenPort() <= 65535;
    }

    private void sendDiscoveryReply(DatagramPacket receivedPacket,NetworkMessage discoveryMessage) throws IOException {

        NetworkMessage reply = MessageCodec.createMessage(
                MessageType.DISCOVER_REPLY,
                networkNode.getNodeId(),
                discoveryMessage.getMessageId(),
                createDiscoveryPayload());

        sendMessage(
                reply,
                receivedPacket.getAddress(),
                receivedPacket.getPort());
    }

    private void handleDiscoveryReply(
            DatagramPacket packet,
            NetworkMessage message,
            DiscoveryPayload payload) {

        String peerNodeId = message.getSenderNodeId();
        String peerIpAddress = packet.getAddress().getHostAddress();

        /*
         * Samo jedan od dva noda otvara TCP konekciju.
         * Tako izbjegavamo dvije konekcije između istih nodova.
         */
        if (networkNode.getNodeId().compareTo(peerNodeId) >= 0) {
            return;
        }

        System.out.println(
                "UDP discovery pronašao node: "
                        + peerNodeId
                        + " | "
                        + peerIpAddress
                        + ":"
                        + payload.getListenPort());

        networkNode.maintainConnection(
                peerIpAddress,
                payload.getListenPort());
    }

    private void broadcastDiscoveryMessages() {

        while (running) {
            try {
                NetworkMessage discoveryMessage = MessageCodec.createMessage(
                        MessageType.DISCOVER,
                        networkNode.getNodeId(),
                        null,
                        createDiscoveryPayload());

                for (InetAddress broadcastAddress : findBroadcastAddresses()) {
                    sendMessage(
                            discoveryMessage,
                            broadcastAddress,
                            DISCOVERY_PORT);
                }

                Thread.sleep(DISCOVERY_INTERVAL);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (running) {
                    System.out.println("UDP discovery broadcast nije uspio: " + e.getMessage());
                }
            }
        }
    }

    private DiscoveryPayload createDiscoveryPayload() {

        HelloPayload helloPayload = networkNode.createHelloPayload();

        return new DiscoveryPayload(
                helloPayload.getNetworkId(),
                helloPayload.getGenesisHash(),
                helloPayload.getNodeType(),
                helloPayload.getListenPort());
    }

    private Set<InetAddress> findBroadcastAddresses() throws IOException {

        Set<InetAddress> broadcastAddresses = new HashSet<>();
        broadcastAddresses.add(InetAddress.getByName("255.255.255.255"));

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }

            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                if (interfaceAddress.getBroadcast() != null) {
                    broadcastAddresses.add(interfaceAddress.getBroadcast());
                }
            }
        }

        return broadcastAddresses;
    }

    private void sendMessage(
            NetworkMessage message,
            InetAddress address,
            int port) throws IOException {

        byte[] data = MessageCodec
                .toJson(message)
                .getBytes(StandardCharsets.UTF_8);

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                address,
                port);

        socket.send(packet);
    }

    @Override
    public synchronized void close() {

        running = false;

        if (broadcastThread != null) {
            broadcastThread.interrupt();
        }

        if (socket != null) {
            socket.close();
        }
    }
}