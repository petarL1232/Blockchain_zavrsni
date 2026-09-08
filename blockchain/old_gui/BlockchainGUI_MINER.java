import javax.swing.*;
import java.awt.*;

class BlockchainGUI_MINER extends JFrame {
    private BlockChain blockchain;
    private Wallet wallet;
    private JLabel balanceLabel;
    private JTextArea chainArea;
    Computer computer;

    public BlockchainGUI_MINER(BlockChain blockchain, Wallet wallet, Computer computer) {
        this.blockchain = blockchain;
        this.wallet = wallet;
        this.computer = computer;

        
        this.setTitle("Blockchain GUI - MINER Node " + wallet.getAddress());
        this.setSize(600, 400);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setLocationRelativeTo(null);

        balanceLabel = new JLabel("Balance: " + Money.format(wallet.getBalance()));
        JButton mineBtn = new JButton("Mine Block");
        JButton txBtn = new JButton("Send Transaction");
        JButton refreshBtn = new JButton("Refresh balance");
        JButton stopBtn = new JButton("Stop mining");

        chainArea = new JTextArea();
        chainArea.setEditable(false);

        mineBtn.addActionListener(e -> {

            Thread minerThread2 = new Thread(computer);
            computer.running = true;
            minerThread2.start();

            this.updateUI();
        });

        txBtn.addActionListener(e -> {
            String receiver = JOptionPane.showInputDialog("Unesi adresu primatelja:");
            String amountStr = JOptionPane.showInputDialog("Unesi iznos:");
            try {
                long amount = Money.fromCoins(amountStr);
                if(amount < ConsensusRules.MIN_TRANSACTION_AMOUNT) {
                    throw new IllegalArgumentException("Iznos je premalen.");
                }
                //String signature = wallet.signData(wallet.getAddress() + receiver + new String(amount + "")); ovo sada više ne treba to se radi u Wallet klasi
                Transactions tx = blockchain.createTransaction(wallet, receiver, amount);
                blockchain.addPendingTransaction(tx);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                JOptionPane.showMessageDialog(this, "Neispravan iznos.");
            }

            this.updateUI();
        });

        refreshBtn.addActionListener(e -> {
            this.updateUI();
        });

        stopBtn.addActionListener(e -> {
            computer.stopNode();
            this.updateUI();
        });


        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(balanceLabel);
        topPanel.add(mineBtn);
        topPanel.add(txBtn);
        topPanel.add(refreshBtn);
        topPanel.add(stopBtn);

        this.add(topPanel, BorderLayout.NORTH);
        this.add(new JScrollPane(chainArea), BorderLayout.CENTER);

        this.updateUI();
        this.setVisible(true);
    }

    private void updateUI() {
        balanceLabel.setText("Balance: " + Money.format(wallet.getBalance()));
        chainArea.setText(blockchain.toString());
    }
}
