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

        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(1)).increaseBalance(500); // sender
        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(2)); // primač
        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(3)).increaseBalance(200);
        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(4));
        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(5)).increaseBalance(100);;
        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(6)).increaseBalance(2000);;
        blockchain.getWalletRegistry().get(blockchain.getAdreseWalleta().get(7)).increaseBalance(1000);;

        Computer c1_System_vise_nije_xD = new Computer(Computer.NodeType.FULL, blockchain.getAdreseWalleta().get(1), blockchain);
        Computer c2_Alice = new Computer(Computer.NodeType.MINER, blockchain.getAdreseWalleta().get(2), blockchain);
        Computer c3_Bob = new Computer(Computer.NodeType.FULL, blockchain.getAdreseWalleta().get(3), blockchain);
        Computer c4_Oliver = new Computer(Computer.NodeType.FULL, blockchain.getAdreseWalleta().get(4), blockchain);
        Computer c5_JA = new Computer(Computer.NodeType.MINER, blockchain.getAdreseWalleta().get(5), blockchain);
        Computer c6_samo_za_test_light_node = new Computer(Computer.NodeType.LIGHT, blockchain.getAdreseWalleta().get(6), blockchain);
        Computer c7_samo_za_test_light_node = new Computer(Computer.NodeType.LIGHT, blockchain.getAdreseWalleta().get(7), blockchain);

        blockchain.addValidatorNode(c2_Alice);
        blockchain.addValidatorNode(c3_Bob);
        blockchain.addValidatorNode(c4_Oliver);
        blockchain.addValidatorNode(c6_samo_za_test_light_node);
        blockchain.addValidatorNode(c7_samo_za_test_light_node);
        blockchain.addValidatorNode(c1_System_vise_nije_xD);
        blockchain.addValidatorNode(c5_JA);

        /*
        Thread minerThread = new Thread(c2_Alice);
        Thread minerThread2 = new Thread(c5_JA);
        minerThread.start();
        minerThread2.start();*/

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
        String pubHash = pubKeyField.getText().trim();
        String privHash = privKeyField.getText().trim();

        for (String address : blockchain.getAdreseWalleta()) {
            Wallet w = blockchain.getWalletRegistry().get(address);

            String pub = Cryptography.applySHA256(w.getPublicKey().toString());
            String priv = Cryptography.applySHA256(w.getPrivateKey().toString());
            System.out.println(pub);

            if (pub.equals(pubHash) && priv.equals(privHash)) {
                for (Computer c : blockchain.getValidatorNodes()) {
                    if (c.getAddress().equals(w.getAddress())) {
                        System.out.println("USPIJEHHHHHHH");
                        if (c.getType() == Computer.NodeType.MINER) {
                            logArea.append("login uspjesan! Pozdrav "+ ""+c.getType().toString()+"\n");
                            logArea.updateUI();
                            new BlockchainGUI_MINER(blockchain, w, c);
                        } else {
                            new BlockchainGUI_LIGHT_FULL(blockchain, w, c);
                        }
                        
                        //dispose();
                        return;
                    }
                }
            }
        }

        logArea.append("Login neuspjesan!\n");
        logArea.updateUI();
    }

    private void createAccount() {
        new BlockchainGUI_CreateAccountFrame(blockchain);
        //dispose();
    }
}