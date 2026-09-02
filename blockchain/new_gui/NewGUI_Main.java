import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class NewGUI_Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ModernTheme.install();
            BlockchainUIController controller = BlockchainUIController.createDemo();
            BlockchainDashboard dashboard = new BlockchainDashboard(controller);

            JFrame frame = new JFrame("Mathos Coin - Network Observatory");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setMinimumSize(new Dimension(1180,760));
            frame.setSize(1480,920);
            frame.setLocationRelativeTo(null); 
            frame.setContentPane(dashboard);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    dashboard.shutdown();
                }
            });
            frame.setVisible(true);
        });
    }
}
