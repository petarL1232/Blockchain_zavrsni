import java.util.Scanner;

import WLAN.Ipconfig;

public class Main_WLAN {

    private static final String NETWORK_ID = "MATHOS-DEVNET-1";

    public static void main(String[] args) throws Exception {

        /*
         * BlockChain blockchain = new BlockChain();
         * 
         * NetworkNode node = new NetworkNode(
         * Ipconfig.NODE_ID,
         * Computer.NodeType.valueOf(Ipconfig.NODE_TYPE),
         * Ipconfig.PORT,
         * NETWORK_ID,
         * blockchain);
         * 
         * node.start();
         * 
         * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         * try {
         * node.close();
         * } catch (Exception ignored) {
         * 
         * }
         * }));
         * 
         * if (Ipconfig.PEER_IP != null
         * && !Ipconfig.PEER_IP.isBlank()
         * && !Ipconfig.PEER_IP.equals(Ipconfig.IP_OF_MY_PC)) {
         * node.maintainConnection(Ipconfig.PEER_IP, Ipconfig.PORT);
         * }
         * 
         * System.out.println("Aktivnih peerova: " + node.getConnectedPeerCount());
         * 
         * Thread.currentThread().join();
         */

        BlockChain blockchain = new BlockChain();

        Computer.NodeType nodeType = Computer.NodeType.valueOf(
                Ipconfig.NODE_TYPE);

        NetworkNode node = new NetworkNode(
                Ipconfig.NODE_ID,
                nodeType,
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

            node.maintainConnection(
                    Ipconfig.PEER_IP,
                    Ipconfig.PORT);
        }

        Wallet myWallet = node.registerWallet(Money.coins(100));

        System.out.println("Moj wallet: " + myWallet.getAddress());
        System.out.println("Balance: " + Money.format(myWallet.getBalance()));

        if (nodeType == Computer.NodeType.MINER) {
            node.startMining(myWallet.getAddress());
        }

        System.out.println("Naredbe:");
        System.out.println("send ADRESA IZNOS");
        System.out.println("peers");
        System.out.println("balance");
        System.out.println("chain");
        System.out.println("quit");

        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {

            String command = scanner.nextLine().trim();
            String[] commandParts = command.split("\\s+");

            if (commandParts.length == 3
                    && "send".equalsIgnoreCase(commandParts[0])) {

                try {
                    String receiverAddress = commandParts[1];
                    long amount = Money.fromCoins(commandParts[2]);

                    Transactions transaction = myWallet.createTransaction(
                            receiverAddress,
                            amount);

                    if (node.submitTransaction(transaction)) {
                        System.out.println("Transakcija poslana u mrežu puff");
                    } else {
                        System.out.println("Transakcija nije prihvaćena.");
                    }

                } catch (Exception e) {
                    System.out.println(
                            "Neispravna naredba: " + e.getMessage());
                }

            } else if ("peers".equalsIgnoreCase(command)) {

                System.out.println(
                        "Aktivnih direktnih peerova: "
                                + node.getConnectedPeerCount());

            } else if ("balance".equalsIgnoreCase(command)) {

                System.out.println(
                        "Balance: "
                                + Money.format(myWallet.getBalance()));

            } else if ("chain".equalsIgnoreCase(command)) {

                blockchain.printBlockchain();

            } else if ("quit".equalsIgnoreCase(command)) {

                node.close();
                return;

            } else {
                System.out.println(
                        "Koristi: send ADRESA IZNOS, peers, balance, chain ili quit");
            }
        }
        scanner.close();
    }
}