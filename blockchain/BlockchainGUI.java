import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class BlockchainGUI extends JFrame implements KonstanteZaGUI {

    private BlockChain blockchain;
    private JTextField pubKeyField;
    private JTextField privKeyField;
    private JTextArea logArea;

    public BlockchainGUI(BlockChain blockchain) {
        this.blockchain = blockchain;

        blockchain.registerWallet();
        blockchain.registerWallet();
        blockchain.registerWallet();
        blockchain.registerWallet();
        blockchain.registerWallet();
        blockchain.registerWallet();
        blockchain.registerWallet();

        blockchain.addInitialBalance(blockchain.getAdreseWalleta().get(0), Money.coins(500));
        blockchain.addInitialBalance(blockchain.getAdreseWalleta().get(2), Money.coins(200));
        blockchain.addInitialBalance(blockchain.getAdreseWalleta().get(4), Money.coins(100));
        blockchain.addInitialBalance(blockchain.getAdreseWalleta().get(5), Money.coins(2000));
        blockchain.addInitialBalance(blockchain.getAdreseWalleta().get(6), Money.coins(1000));

        Computer c1_System_vise_nije_xD = new Computer(Computer.NodeType.FULL, blockchain.getAdreseWalleta().get(0),blockchain);
        Computer c2_Alice = new Computer(Computer.NodeType.MINER, blockchain.getAdreseWalleta().get(1), blockchain);
        Computer c3_Bob = new Computer(Computer.NodeType.FULL, blockchain.getAdreseWalleta().get(2), blockchain);
        Computer c4_Oliver = new Computer(Computer.NodeType.FULL, blockchain.getAdreseWalleta().get(3), blockchain);
        Computer c5_JA = new Computer(Computer.NodeType.MINER, blockchain.getAdreseWalleta().get(4), blockchain);
        Computer c6_samo_za_test_light_node = new Computer(Computer.NodeType.LIGHT,blockchain.getAdreseWalleta().get(5), blockchain);
        Computer c7_samo_za_test_light_node = new Computer(Computer.NodeType.LIGHT,blockchain.getAdreseWalleta().get(6), blockchain);

        blockchain.addValidatorNode(c2_Alice);
        blockchain.addValidatorNode(c3_Bob);
        blockchain.addValidatorNode(c4_Oliver);
        blockchain.addValidatorNode(c6_samo_za_test_light_node);
        blockchain.addValidatorNode(c7_samo_za_test_light_node);
        blockchain.addValidatorNode(c1_System_vise_nije_xD);
        blockchain.addValidatorNode(c5_JA);

        /*
         * Thread minerThread = new Thread(c2_Alice);
         * Thread minerThread2 = new Thread(c5_JA);
         * minerThread.start();
         * minerThread2.start();
         */

        blockchain.printAllWallets_DETAL();

        this.setTitle("Blockchain GUI - LOGIN");
        this.setSize(FRAME_WIDTH, FRAME_HEIGHT);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));

        panel.add(new JLabel("Public Key Hash:"));
        pubKeyField = new JTextField();
        panel.add(pubKeyField);

        panel.add(new JLabel("Private Key Hash:"));
        privKeyField = new JTextField();
        panel.add(privKeyField);

        JButton loginBtn = new JButton("Login");
        JButton createBtn = new JButton("Create Account");

        loginBtn.addActionListener(e -> login());
        createBtn.addActionListener(e -> createAccount());

        panel.add(loginBtn);
        panel.add(createBtn);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setVisible(true);

        this.add(panel, BorderLayout.CENTER);
        this.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        this.setVisible(true);
    }

    private void login() {
        String uneseniPublicHash = pubKeyField.getText().trim();
        String uneseniPrivateHash = privKeyField.getText().trim();

        for (Wallet wallet : blockchain.getPrivateWalletRegistry().values()) {
            String publicHash = Cryptography.applySHA256(
                    wallet.getPublicKeyString());

            String privateHash = Cryptography.applySHA256(
                    wallet.getPrivateKeyString());

            if (!publicHash.equals(uneseniPublicHash)
                    || !privateHash.equals(uneseniPrivateHash)) {
                continue;
            }

            for (Computer computer : blockchain.getValidatorNodes()) {
                if (!computer.getAddress().equals(wallet.getAddress())) {
                    continue;
                }

                logArea.append("Login uspjesan! Pozdrav "
                        + computer.getType() + "\n");

                if (computer.getType() == Computer.NodeType.MINER) {
                    new BlockchainGUI_MINER(blockchain, wallet, computer);
                } else {
                    new BlockchainGUI_LIGHT_FULL(blockchain, wallet, computer);
                }

                return;
            }

            logArea.append("Wallet postoji, ali nije povezan s nodeom.\n");
            return;
        }

        logArea.append("Login neuspjesan!\n");
    }

    private void createAccount() {
        new BlockchainGUI_CreateAccountFrame(blockchain);
        // dispose();
    }
}
