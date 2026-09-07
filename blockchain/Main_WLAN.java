import WLAN.Ipconfig;

public class Main_WLAN {

    private static final String NETWORK_ID = "MATHOS-DEVNET-1";

    public static void main(String[] args) throws Exception {

        BlockChain blockchain = new BlockChain();

        NetworkNode node = new NetworkNode(
                Ipconfig.NODE_ID,
                Computer.NodeType.valueOf(Ipconfig.NODE_TYPE),
                Ipconfig.PORT,
                NETWORK_ID,
                blockchain);

        node.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                node.close();
            } catch (Exception ignored) {

            }
        }));

        if (Ipconfig.PEER_IP != null
                && !Ipconfig.PEER_IP.isBlank()
                && !Ipconfig.PEER_IP.equals(Ipconfig.IP_OF_MY_PC)) {
            node.connectToPeer(Ipconfig.PEER_IP, Ipconfig.PORT);
        }

        System.out.println("Aktivnih peerova: " + node.getConnectedPeerCount());

        Thread.currentThread().join();
    }
}