import javax.swing.*;
import java.awt.*;

public class BlockchainGUI_CreateAccountFrame extends JFrame {

    public BlockchainGUI_CreateAccountFrame(BlockChain blockchain) {
        setTitle("Create New Account");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // --- Dropdown za tip noda ---
        JPanel topPanel = new JPanel();
        JLabel label = new JLabel("Odaberi tip noda: ");
        String[] nodeTypes = {"LIGHT", "FULL", "MINER"};
        JComboBox<String> nodeTypeBox = new JComboBox<>(nodeTypes);
        JButton createBtn = new JButton("Create Account");

        topPanel.add(label);
        topPanel.add(nodeTypeBox);
        topPanel.add(createBtn);

        add(topPanel, BorderLayout.NORTH);

        // --- Text area za ispis ---
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- Event klik na Create ---
        createBtn.addActionListener(e -> {
            // Kreiraj novi wallet
            Wallet newWallet = blockchain.registerWallet();

            // Odabrani tip noda
            String selected = (String) nodeTypeBox.getSelectedItem();
            Computer.NodeType type = Computer.NodeType.valueOf(selected);

            // Napravi novi Computer objekt vezan uz wallet adresu
            Computer za_dodati = new Computer(type, newWallet.getAddress(), blockchain);
            blockchain.getValidatorNodes().add(za_dodati);

            // Hash-evi za login
            String pubHash = Cryptography.applySHA256(
                    newWallet.getPublicKeyString());
            
            String privHash = Cryptography.applySHA256(
                    newWallet.getPrivateKeyString());

            // Ispis
            StringBuilder sb = new StringBuilder();
            sb.append("✅ Novi " + selected + " node kreiran!\n\n");
            sb.append("Adresa: " + newWallet.getAddress() + "\n");
            sb.append("Public Key Hash (za login):\n" + pubHash + "\n\n");
            sb.append("Private Key Hash (za login):\n" + privHash + "\n");

            textArea.setText(sb.toString());
            blockchain.printAllWallets_DETAL();
        });

        setVisible(true);
    }
}
