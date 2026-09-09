import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class WlanGUI_Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WlanTheme.install();
            JFrame frame = new JFrame("MATHOSCOIN · WLAN Node");
            AtomicReference<WlanDashboard> dashboard = new AtomicReference<>();

            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int width = Math.min(1480,workArea.width);
            int height = Math.min(920,workArea.height);
            frame.setMinimumSize(new Dimension(Math.min(980,width),Math.min(620,height)));
            frame.setSize(width,height);
            frame.setLocation(workArea.x + (workArea.width - width) / 2,workArea.y + (workArea.height - height) / 2);
            frame.setContentPane(new NodeLaunchPanel(frame,dashboard));
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) {
                    WlanDashboard runningDashboard = dashboard.get();
                    if(runningDashboard != null) runningDashboard.shutdown();
                }
            });
            frame.setVisible(true);
        });
    }

    private static class NodeLaunchPanel extends WlanTheme.BackgroundPanel {
        private final JFrame frame;
        private final AtomicReference<WlanDashboard> dashboard;
        private final JTextField aliasField = field(defaultAlias());
        private final JTextField nodeIdField = field(defaultNodeId());
        private final JTextField portField = field("5000");
        private final JTextField peerIpField = field("");
        private final JTextField peerPortField = field("5000");
        private final JLabel feedback = WlanTheme.label("UDP discovery je konfiguriran · ručni peer nije obavezan",11,WlanTheme.MUTED);
        private final WlanTheme.AccentButton startButton = new WlanTheme.AccentButton("START WLAN NODE  →",true);
        private final ButtonGroup typeGroup = new ButtonGroup();
        private final NodeTypeButton fullButton = new NodeTypeButton("FULL","Validira i čuva cijeli chain",Computer.NodeType.FULL);
        private final NodeTypeButton minerButton = new NodeTypeButton("MINER","Natječe se za nove blokove",Computer.NodeType.MINER);
        private final NodeTypeButton lightButton = new NodeTypeButton("LIGHT","Prati headere i Merkle dokaze",Computer.NodeType.LIGHT);

        private NodeLaunchPanel(JFrame frame,AtomicReference<WlanDashboard> dashboard) {
            super(new BorderLayout());
            this.frame = frame;
            this.dashboard = dashboard;

            JPanel shell = new JPanel(new GridLayout(1,2,22,0));
            shell.setOpaque(false);
            shell.add(buildIntro());
            shell.add(buildSetup());

            LaunchViewport viewport = new LaunchViewport(shell);
            JScrollPane scroll = WlanTheme.scroll(viewport);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            add(scroll,BorderLayout.CENTER);
        }

        private JComponent buildIntro() {
            WlanTheme.Card card = new WlanTheme.Card(new BorderLayout(),30)
                    .fill(WlanTheme.alpha(WlanTheme.PRIMARY,205))
                    .stroke(WlanTheme.alpha(WlanTheme.PRIMARY_LIGHT,155));
            card.setBorder(new EmptyBorder(42,42,38,42));

            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy,BoxLayout.Y_AXIS));

            JLabel network = WlanTheme.label("MATHOS  /  WLAN NETWORK",11,WlanTheme.TEXT_SOFT);
            network.setFont(WlanTheme.font(Font.BOLD,11));
            copy.add(network);
            copy.add(Box.createVerticalStrut(28));

            copy.add(WlanTheme.title("Run your part",39));
            copy.add(WlanTheme.title("of the chain.",39));
            copy.add(Box.createVerticalStrut(18));

            JTextArea detail = new JTextArea("Pokreni potpuno ravnopravan MATHOSCOIN node. Aplikacija sluša peerove, automatski ih otkriva na lokalnoj mreži i sinkronizira jedan zajednički blockchain.");
            detail.setEditable(false);
            detail.setLineWrap(true);
            detail.setWrapStyleWord(true);
            detail.setOpaque(false);
            detail.setForeground(WlanTheme.TEXT_SOFT);
            detail.setFont(WlanTheme.font(Font.PLAIN,15));
            detail.setMaximumSize(new Dimension(390,100));
            copy.add(detail);
            copy.add(Box.createVerticalStrut(34));

            copy.add(feature("01","PEER DISCOVERY","Nodeovi se pronalaze preko UDP broadcasta."));
            copy.add(Box.createVerticalStrut(15));
            copy.add(feature("02","LIVE CONSENSUS","Blokovi i transakcije putuju izravno među peerovima."));
            copy.add(Box.createVerticalStrut(15));
            copy.add(feature("03","LOCAL CONTROL","Login i Auto Mode vrijede samo za ovaj node."));

            card.add(copy,BorderLayout.NORTH);

            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.add(WlanTheme.label("MATHOSCOIN",11,WlanTheme.TEXT_SOFT),BorderLayout.WEST);
            JLabel symbol = WlanTheme.title("MATH",18);
            footer.add(symbol,BorderLayout.EAST);
            card.add(footer,BorderLayout.SOUTH);
            return card;
        }

        private JComponent feature(String number,String title,String description) {
            JPanel row = new JPanel(new BorderLayout(14,0));
            row.setOpaque(false);
            JLabel badge = WlanTheme.title(number,11);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setPreferredSize(new Dimension(38,38));
            badge.setBorder(BorderFactory.createLineBorder(WlanTheme.alpha(WlanTheme.TEXT_SOFT,100)));
            row.add(badge,BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
            JLabel titleLabel = WlanTheme.title(title,11);
            JLabel descriptionLabel = WlanTheme.label(description,11,WlanTheme.TEXT_SOFT);
            text.add(titleLabel);
            text.add(Box.createVerticalStrut(3));
            text.add(descriptionLabel);
            row.add(text,BorderLayout.CENTER);
            return row;
        }

        private JComponent buildSetup() {
            WlanTheme.Card card = new WlanTheme.Card(new BorderLayout(),30);
            card.setBorder(new EmptyBorder(32,34,28,34));

            JPanel form = new JPanel();
            form.setOpaque(false);
            form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));
            form.add(WlanTheme.label("NEW LOCAL SESSION",10,WlanTheme.CYAN));
            form.add(Box.createVerticalStrut(6));
            form.add(WlanTheme.title("Configure this node",26));
            form.add(Box.createVerticalStrut(7));
            form.add(WlanTheme.label("Svaki uređaj dobiva vlastiti identity i isti network ID.",12,WlanTheme.MUTED));
            form.add(Box.createVerticalStrut(23));

            JPanel identity = new JPanel(new GridLayout(1,2,12,0));
            identity.setOpaque(false);
            identity.add(input("DISPLAY NAME",aliasField));
            identity.add(input("UNIQUE NODE ID",nodeIdField));
            identity.setMaximumSize(new Dimension(Integer.MAX_VALUE,67));
            form.add(identity);
            form.add(Box.createVerticalStrut(18));

            form.add(WlanTheme.label("NODE ROLE",10,WlanTheme.MUTED));
            form.add(Box.createVerticalStrut(8));
            JPanel roles = new JPanel(new GridLayout(1,3,8,0));
            roles.setOpaque(false);
            roles.add(fullButton);
            roles.add(minerButton);
            roles.add(lightButton);
            roles.setMaximumSize(new Dimension(Integer.MAX_VALUE,78));
            typeGroup.add(fullButton);
            typeGroup.add(minerButton);
            typeGroup.add(lightButton);
            fullButton.setSelected(true);
            form.add(roles);
            form.add(Box.createVerticalStrut(18));

            JPanel local = new JPanel(new GridLayout(1,2,12,0));
            local.setOpaque(false);
            local.add(input("LISTEN PORT",portField));
            JLabel discovery = WlanTheme.label("<html>UDP 4999<br>AUTO DISCOVERY</html>",11,WlanTheme.SUCCESS);
            discovery.setFont(WlanTheme.font(Font.BOLD,11));
            discovery.setBorder(new EmptyBorder(22,12,0,0));
            local.add(discovery);
            local.setMaximumSize(new Dimension(Integer.MAX_VALUE,67));
            form.add(local);
            form.add(Box.createVerticalStrut(17));

            form.add(WlanTheme.label("OPTIONAL MANUAL BOOTSTRAP",10,WlanTheme.MUTED));
            form.add(Box.createVerticalStrut(8));
            JPanel peer = new JPanel(new GridLayout(1,2,12,0));
            peer.setOpaque(false);
            peer.add(input("PEER IP",peerIpField));
            peer.add(input("PEER PORT",peerPortField));
            peer.setMaximumSize(new Dimension(Integer.MAX_VALUE,67));
            form.add(peer);

            card.add(form,BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout(12,0));
            footer.setOpaque(false);
            feedback.setBorder(new EmptyBorder(0,0,0,8));
            footer.add(feedback,BorderLayout.CENTER);
            startButton.setPreferredSize(new Dimension(190,43));
            startButton.addActionListener(event -> startNode());
            footer.add(startButton,BorderLayout.EAST);
            card.add(footer,BorderLayout.SOUTH);
            return card;
        }

        private JComponent input(String label,JTextField field) {
            JPanel wrapper = new JPanel(new BorderLayout(0,6));
            wrapper.setOpaque(false);
            field.getAccessibleContext().setAccessibleName(label);
            wrapper.add(WlanTheme.label(label,9,WlanTheme.MUTED),BorderLayout.NORTH);
            wrapper.add(field,BorderLayout.CENTER);
            return wrapper;
        }

        private void startNode() {
            Computer.NodeType nodeType = fullButton.isSelected() ? Computer.NodeType.FULL
                    : minerButton.isSelected() ? Computer.NodeType.MINER : Computer.NodeType.LIGHT;
            int listenPort;
            int peerPort = 5000;

            try {
                listenPort = Integer.parseInt(portField.getText().trim());
                if(!peerIpField.getText().isBlank()) peerPort = Integer.parseInt(peerPortField.getText().trim());
            } catch(NumberFormatException exception) {
                showFeedback("Port mora biti cijeli broj.",WlanTheme.DANGER);
                return;
            }

            WlanUIController.Settings settings = new WlanUIController.Settings(
                    nodeIdField.getText(),aliasField.getText(),nodeType,listenPort,peerIpField.getText(),peerPort);
            startButton.setEnabled(false);
            startButton.setText("STARTING…");
            showFeedback("Otvaram socket i pokrećem discovery…",WlanTheme.CYAN);

            Thread worker = new Thread(() -> {
                try {
                    WlanUIController controller = WlanUIController.start(settings);
                    SwingUtilities.invokeLater(() -> openDashboard(controller));
                } catch(Exception exception) {
                    SwingUtilities.invokeLater(() -> {
                        startButton.setEnabled(true);
                        startButton.setText("START WLAN NODE  →");
                        showFeedback(exception.getMessage() == null ? "Node se nije mogao pokrenuti." : exception.getMessage(),WlanTheme.DANGER);
                    });
                }
            },"mathos-wlan-bootstrap");
            worker.setDaemon(true);
            worker.start();
        }

        private void openDashboard(WlanUIController controller) {
            if(!frame.isDisplayable()) {
                controller.shutdown();
                return;
            }
            WlanDashboard newDashboard = new WlanDashboard(controller);
            dashboard.set(newDashboard);
            frame.setContentPane(newDashboard);
            frame.revalidate();
            frame.repaint();
        }

        private void showFeedback(String message,Color color) {
            feedback.setText(message);
            feedback.setForeground(color);
        }

        private static JTextField field(String value) {
            JTextField field = new JTextField(value);
            field.setFont(WlanTheme.font(Font.PLAIN,12));
            return field;
        }
    }

    private static class LaunchViewport extends JPanel implements Scrollable {
        private final JPanel shell;

        private LaunchViewport(JPanel shell) {
            super(null);
            this.shell = shell;
            setOpaque(false);
            add(shell);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(1150,718);
        }

        @Override public void doLayout() {
            int shellWidth = Math.max(820,Math.min(1070,getWidth() - 56));
            int shellHeight = 650;
            int x = Math.max(28,(getWidth() - shellWidth) / 2);
            int y = Math.max(28,(getHeight() - shellHeight) / 2);
            shell.setBounds(x,y,shellWidth,shellHeight);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect,int orientation,int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect,int orientation,int direction) { return Math.max(80,visibleRect.height - 70); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private static class NodeTypeButton extends JToggleButton {
        private final String title;
        private final String detail;
        private boolean hover;

        private NodeTypeButton(String title,String detail,Computer.NodeType type) {
            this.title = title;
            this.detail = detail;
            setToolTipText(type + " · " + detail);
            getAccessibleContext().setAccessibleName(title + " node");
            getAccessibleContext().setAccessibleDescription(detail);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent event) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isSelected() ? WlanTheme.alpha(WlanTheme.PRIMARY,100)
                    : hover ? WlanTheme.alpha(WlanTheme.SURFACE_HIGH,245) : WlanTheme.alpha(WlanTheme.SURFACE_HIGH,180));
            g.fillRoundRect(0,0,getWidth(),getHeight(),17,17);
            g.setColor(isSelected() ? WlanTheme.PRIMARY_LIGHT : WlanTheme.BORDER);
            g.drawRoundRect(0,0,getWidth() - 1,getHeight() - 1,17,17);
            g.setFont(WlanTheme.font(Font.BOLD,11));
            g.setColor(WlanTheme.TEXT);
            g.drawString(title,12,26);
            g.setFont(WlanTheme.font(Font.PLAIN,9));
            g.setColor(WlanTheme.MUTED);
            String compact = detail.length() > 19 ? detail.substring(0,19) + "…" : detail;
            g.drawString(compact,12,47);
            if(hasFocus()) {
                g.setColor(WlanTheme.CYAN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(2,2,getWidth() - 5,getHeight() - 5,15,15);
            }
            g.dispose();
        }
    }

    private static String defaultAlias() {
        String computer = System.getenv("COMPUTERNAME");
        if(computer == null || computer.isBlank()) return "My MATH Node";
        return computer.substring(0,1).toUpperCase(Locale.ROOT) + computer.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String defaultNodeId() {
        String base = defaultAlias().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","-");
        return base + "-" + ThreadLocalRandom.current().nextInt(1000,10000);
    }
}
