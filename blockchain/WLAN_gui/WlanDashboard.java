import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class WlanDashboard extends WlanTheme.BackgroundPanel {
    private final WlanUIController controller;
    private final ExecutorService background;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final Map<String,NavButton> navigation = new HashMap<>();
    private final Timer refreshTimer;

    private WlanUIController.Snapshot snapshot;
    private boolean updatingControls;
    private int selectedBlockHeight = -1;
    private boolean blockSelectionLocked;
    private String walletFingerprint = "";
    private String walletVisualFingerprint = "";
    private String chainFingerprint = "";
    private String transactionFingerprint = "";
    private String activityFingerprint = "";

    private JLabel headerNode;
    private JLabel headerStatus;
    private JLabel headerLogin;
    private JLabel toast;

    private MetricCard peersMetric;
    private MetricCard heightMetric;
    private MetricCard mempoolMetric;
    private MetricCard workMetric;
    private NetworkTopologyView topologyView;
    private ChainRibbon chainRibbon;
    private MiningPanel miningPanel;
    private JComboBox<ReceiverChoice> receiverBox;
    private JTextField amountField;
    private WlanTheme.AccentButton sendButton;
    private WlanTheme.AccentButton loginButton;
    private WlanTheme.Toggle autoToggle;
    private JLabel autoDescription;
    private JLabel balanceValue;

    private JPanel chainList;
    private JPanel blockInspector;
    private DefaultTableModel transactionModel;
    private JTable transactionTable;
    private JComboBox<String> transactionFilter;
    private ZoomableWalletView walletView;
    private JPanel walletInspector;
    private JPanel activityList;
    private JLabel identityState;
    private WlanTheme.AccentButton identityLoginButton;
    private JTextField publicLoginHash;
    private JTextField privateLoginHash;

    public WlanDashboard(WlanUIController controller) {
        super(new BorderLayout());
        this.controller = controller;
        setBorder(new EmptyBorder(0,0,0,0));

        ThreadFactory daemonFactory = task -> {
            Thread thread = new Thread(task,"mathos-wlan-ui-worker");
            thread.setDaemon(true);
            return thread;
        };
        background = Executors.newSingleThreadExecutor(daemonFactory);

        add(buildRail(),BorderLayout.WEST);
        add(buildMainArea(),BorderLayout.CENTER);
        add(buildToast(),BorderLayout.SOUTH);

        showPage("COMMAND");
        refreshTimer = new Timer(650,event -> requestSnapshot());
        refreshTimer.start();
        requestSnapshot();
    }

    private JComponent buildRail() {
        JPanel rail = new JPanel();
        rail.setOpaque(false);
        rail.setPreferredSize(new Dimension(88,0));
        rail.setBorder(new EmptyBorder(18,12,16,12));
        rail.setLayout(new BoxLayout(rail,BoxLayout.Y_AXIS));

        LogoMark logo = new LogoMark();
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        rail.add(logo);
        rail.add(Box.createVerticalStrut(34));

        addNavigation(rail,"COMMAND","L","Live");
        addNavigation(rail,"CHAIN","C","Chain");
        addNavigation(rail,"TRANSACTIONS","T","Tx");
        addNavigation(rail,"WALLETS","W","Wallets");
        addNavigation(rail,"NODE","N","Node");

        rail.add(Box.createVerticalGlue());
        JLabel version = WlanTheme.label("WLAN\nv2",12,WlanTheme.MUTED);
        version.setHorizontalAlignment(SwingConstants.CENTER);
        version.setAlignmentX(Component.CENTER_ALIGNMENT);
        rail.add(version);
        return rail;
    }

    private void addNavigation(JPanel rail,String page,String symbol,String label) {
        NavButton button = new NavButton(symbol,label);
        button.setToolTipText("Open " + label + " page");
        button.getAccessibleContext().setAccessibleName(label + " page");
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(event -> showPage(page));
        navigation.put(page,button);
        rail.add(button);
        rail.add(Box.createVerticalStrut(9));
    }

    private JComponent buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(14,0,10,16));
        main.add(buildHeader(),BorderLayout.NORTH);

        pages.setOpaque(false);
        pages.setBorder(new EmptyBorder(14,0,0,0));
        pages.add(buildCommandPage(),"COMMAND");
        pages.add(buildChainPage(),"CHAIN");
        pages.add(buildTransactionsPage(),"TRANSACTIONS");
        pages.add(buildWalletPage(),"WALLETS");
        pages.add(buildNodePage(),"NODE");
        main.add(pages,BorderLayout.CENTER);
        return main;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(18,0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(4,4,4,4));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand,BoxLayout.Y_AXIS));
        JLabel name = WlanTheme.title("MATHOSCOIN",22);
        JLabel subtitle = WlanTheme.label("WLAN NODE CONTROL CENTER  /  MATH",12,WlanTheme.CYAN);
        brand.add(name);
        brand.add(Box.createVerticalStrut(2));
        brand.add(subtitle);
        header.add(brand,BorderLayout.WEST);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,2));
        status.setOpaque(false);
        headerNode = badge("STARTING",WlanTheme.PRIMARY_LIGHT);
        headerStatus = badge("NO DIRECT PEERS",WlanTheme.WARNING);
        headerLogin = badge("WALLET LOCKED",WlanTheme.MUTED);
        status.add(headerNode);
        status.add(headerStatus);
        status.add(headerLogin);
        header.add(status,BorderLayout.EAST);
        return header;
    }

    private JComponent buildToast() {
        JPanel position = new JPanel(new BorderLayout());
        position.setOpaque(false);
        position.setBorder(new EmptyBorder(0,96,10,16));
        toast = new WlanTheme.PillLabel("UDP discovery configured · monitoring persisted blockchain state",WlanTheme.MUTED);
        toast.setFont(WlanTheme.font(Font.PLAIN,12));
        position.add(toast,BorderLayout.CENTER);
        return position;
    }

    private JComponent buildCommandPage() {
        JPanel page = pagePanel();

        JPanel metrics = new JPanel(new GridLayout(1,4,12,0));
        metrics.setOpaque(false);
        metrics.setPreferredSize(new Dimension(0,102));
        peersMetric = new MetricCard("DIRECT PEERS","0","UDP discovery configured",WlanTheme.CYAN);
        heightMetric = new MetricCard("CHAIN HEIGHT","0","Genesis only",WlanTheme.PRIMARY_LIGHT);
        mempoolMetric = new MetricCard("MEMPOOL","0","Waiting for traffic",WlanTheme.PURPLE);
        workMetric = new MetricCard("CUMULATIVE WORK","0","Proof-of-work total",WlanTheme.SUCCESS);
        metrics.add(peersMetric);
        metrics.add(heightMetric);
        metrics.add(mempoolMetric);
        metrics.add(workMetric);
        page.add(metrics,BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(12,0,0,0));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;

        constraints.gridx = 0;
        constraints.weightx = .64;
        constraints.insets = new Insets(0,0,0,12);
        center.add(buildTopologyCard(),constraints);

        constraints.gridx = 1;
        constraints.weightx = .36;
        constraints.insets = new Insets(0,0,0,0);
        center.add(buildCommandCard(),constraints);
        page.add(center,BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout(12,0));
        lower.setOpaque(false);
        lower.setPreferredSize(new Dimension(0,205));
        lower.setBorder(new EmptyBorder(12,0,0,0));

        WlanTheme.Card chainCard = card(new BorderLayout());
        chainCard.add(sectionHeader("LIVE CHAIN RIBBON","Newest accepted blocks on this node"),BorderLayout.NORTH);
        chainRibbon = new ChainRibbon();
        chainCard.add(chainRibbon,BorderLayout.CENTER);
        lower.add(chainCard,BorderLayout.CENTER);

        WlanTheme.Card miningCard = card(new BorderLayout());
        miningCard.setPreferredSize(new Dimension(315,0));
        miningCard.add(sectionHeader("LOCAL MINING","Only this physical node"),BorderLayout.NORTH);
        miningPanel = new MiningPanel();
        miningCard.add(miningPanel,BorderLayout.CENTER);
        lower.add(miningCard,BorderLayout.EAST);
        page.add(lower,BorderLayout.SOUTH);
        return page;
    }

    private JComponent buildTopologyCard() {
        WlanTheme.Card card = card(new BorderLayout());
        card.add(sectionHeader("LIVE WLAN MESH","Real local node + current direct peer count"),BorderLayout.NORTH);
        topologyView = new NetworkTopologyView();
        card.add(topologyView,BorderLayout.CENTER);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT,14,0));
        legend.setOpaque(false);
        legend.setBorder(new EmptyBorder(0,18,14,18));
        legend.add(dotLegend(WlanTheme.PRIMARY_LIGHT,"THIS NODE"));
        legend.add(dotLegend(WlanTheme.PURPLE,"DIRECT PEER"));
        legend.add(dotLegend(WlanTheme.CYAN,"NETWORK PULSE"));
        card.add(legend,BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildCommandCard() {
        WlanTheme.Card card = card(new BorderLayout());
        card.add(sectionHeader("LOCAL COMMAND DECK","Signing is restricted to your session wallet"),BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(4,18,16,18));
        body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));

        JPanel walletLine = new JPanel(new BorderLayout(8,0));
        walletLine.setOpaque(false);
        walletLine.setMaximumSize(new Dimension(Integer.MAX_VALUE,66));
        walletLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel walletText = new JPanel();
        walletText.setOpaque(false);
        walletText.setLayout(new BoxLayout(walletText,BoxLayout.Y_AXIS));
        walletText.add(WlanTheme.label("AVAILABLE BALANCE",12,WlanTheme.MUTED));
        balanceValue = WlanTheme.title("— MATH",24);
        walletText.add(balanceValue);
        walletLine.add(walletText,BorderLayout.CENTER);
        loginButton = new WlanTheme.AccentButton("LOGIN",false);
        loginButton.setPreferredSize(new Dimension(88,38));
        loginButton.addActionListener(event -> showLoginDialog());
        walletLine.add(loginButton,BorderLayout.EAST);
        body.add(walletLine);
        body.add(Box.createVerticalStrut(18));

        body.add(fieldLabel("RECEIVER ADDRESS"));
        receiverBox = new JComboBox<>();
        receiverBox.setEditable(true);
        receiverBox.getAccessibleContext().setAccessibleName("Receiver address");
        body.add(roundedComboBox(receiverBox,0,42));
        body.add(Box.createVerticalStrut(11));

        body.add(fieldLabel("AMOUNT / MATH"));
        amountField = new WlanTheme.RoundedTextField("1");
        amountField.getAccessibleContext().setAccessibleName("Amount in MATH");
        amountField.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        amountField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(amountField);
        body.add(Box.createVerticalStrut(13));

        sendButton = new WlanTheme.AccentButton("SIGN + BROADCAST",true);
        sendButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendButton.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        sendButton.addActionListener(event -> sendTransaction());
        body.add(sendButton);
        body.add(Box.createVerticalGlue());

        JSeparator separator = new JSeparator();
        separator.setForeground(WlanTheme.BORDER);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        body.add(separator);
        body.add(Box.createVerticalStrut(13));

        JPanel autoLine = new JPanel(new BorderLayout(12,0));
        autoLine.setOpaque(false);
        autoLine.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        autoLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel autoText = new JPanel();
        autoText.setOpaque(false);
        autoText.setLayout(new BoxLayout(autoText,BoxLayout.Y_AXIS));
        autoText.add(WlanTheme.title("LOCAL AUTO MODE",13));
        autoDescription = WlanTheme.label("Login required",12,WlanTheme.MUTED);
        autoText.add(autoDescription);
        autoLine.add(autoText,BorderLayout.CENTER);
        autoToggle = new WlanTheme.Toggle();
        autoToggle.setToolTipText("Enable local Auto Mode");
        autoToggle.getAccessibleContext().setAccessibleName("Local Auto Mode");
        autoToggle.addActionListener(event -> changeAutoMode());
        JPanel togglePosition = new JPanel(new FlowLayout(FlowLayout.RIGHT,0,10));
        togglePosition.setOpaque(false);
        togglePosition.add(autoToggle);
        autoLine.add(togglePosition,BorderLayout.EAST);
        body.add(autoLine);
        card.add(body,BorderLayout.CENTER);
        return card;
    }

    private JComponent buildChainPage() {
        JPanel page = pagePanel();
        page.add(pageTitle("BLOCKCHAIN","Inspect the locally accepted proof-of-work history"),BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12,0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14,0,0,0));

        WlanTheme.Card listCard = card(new BorderLayout());
        listCard.add(sectionHeader("BLOCK STREAM","Select a block for full header details"),BorderLayout.NORTH);
        chainList = new JPanel();
        chainList.setOpaque(false);
        chainList.setBorder(new EmptyBorder(5,12,14,12));
        chainList.setLayout(new BoxLayout(chainList,BoxLayout.Y_AXIS));
        listCard.add(WlanTheme.scroll(chainList),BorderLayout.CENTER);
        content.add(listCard,BorderLayout.CENTER);

        WlanTheme.Card inspectorCard = card(new BorderLayout());
        inspectorCard.setPreferredSize(new Dimension(390,0));
        inspectorCard.add(sectionHeader("BLOCK INSPECTOR","Header data verified by this node"),BorderLayout.NORTH);
        blockInspector = new JPanel();
        blockInspector.setOpaque(false);
        blockInspector.setBorder(new EmptyBorder(6,18,18,18));
        blockInspector.setLayout(new BoxLayout(blockInspector,BoxLayout.Y_AXIS));
        inspectorCard.add(blockInspector,BorderLayout.CENTER);
        content.add(inspectorCard,BorderLayout.EAST);
        page.add(content,BorderLayout.CENTER);
        return page;
    }

    private JComponent buildTransactionsPage() {
        JPanel page = pagePanel();
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        title.add(pageTitle("TRANSACTIONS","Confirmed history and the live local mempool"),BorderLayout.WEST);
        transactionFilter = new JComboBox<>(new String[]{"ALL","PENDING","CONFIRMED","SYSTEM REWARD","REGULAR"});
        transactionFilter.addActionListener(event -> updateTransactionTable());
        title.add(roundedComboBox(transactionFilter,180,40),BorderLayout.EAST);
        page.add(title,BorderLayout.NORTH);

        WlanTheme.Card tableCard = card(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(0,0,0,0));
        tableCard.add(sectionHeader("LEDGER TRAFFIC","Amounts are stored in exact long units · 1 MATH = 100,000,000 units"),BorderLayout.NORTH);
        transactionModel = new DefaultTableModel(
                new Object[]{"STATUS","TYPE","AMOUNT","FROM","TO","TX NONCE","BLOCK","TX ID"},0) {
            @Override public boolean isCellEditable(int row,int column) { return false; }
        };
        transactionTable = new JTable(transactionModel);
        transactionTable.getAccessibleContext().setAccessibleName("MATHOSCOIN transactions");
        styleTable(transactionTable);
        tableCard.add(WlanTheme.scroll(transactionTable),BorderLayout.CENTER);
        tableCard.setBorder(new EmptyBorder(14,0,0,0));
        page.add(tableCard,BorderLayout.CENTER);
        return page;
    }

    private JComponent buildWalletPage() {
        JPanel page = pagePanel();
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        title.add(pageTitle("WALLET UNIVERSE","Balance-weighted account state on this node"),BorderLayout.WEST);

        JPanel zoom = new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0));
        zoom.setOpaque(false);
        WlanTheme.AccentButton minus = new WlanTheme.AccentButton("−",false);
        WlanTheme.AccentButton fit = new WlanTheme.AccentButton("FIT",false);
        WlanTheme.AccentButton plus = new WlanTheme.AccentButton("+",false);
        minus.addActionListener(event -> walletView.zoomOut());
        fit.addActionListener(event -> walletView.fitView());
        plus.addActionListener(event -> walletView.zoomIn());
        zoom.add(minus);
        zoom.add(fit);
        zoom.add(plus);
        title.add(zoom,BorderLayout.EAST);
        page.add(title,BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12,0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14,0,0,0));

        WlanTheme.Card universeCard = card(new BorderLayout());
        walletView = new ZoomableWalletView();
        walletView.setSelectionChanged(this::updateWalletInspector);
        universeCard.add(walletView,BorderLayout.CENTER);
        content.add(universeCard,BorderLayout.CENTER);

        WlanTheme.Card inspectorCard = card(new BorderLayout());
        inspectorCard.setPreferredSize(new Dimension(340,0));
        inspectorCard.add(sectionHeader("WALLET SIGNAL","Selected bubble details"),BorderLayout.NORTH);
        walletInspector = new JPanel();
        walletInspector.setOpaque(false);
        walletInspector.setBorder(new EmptyBorder(8,18,18,18));
        walletInspector.setLayout(new BoxLayout(walletInspector,BoxLayout.Y_AXIS));
        inspectorCard.add(walletInspector,BorderLayout.CENTER);
        content.add(inspectorCard,BorderLayout.EAST);
        page.add(content,BorderLayout.CENTER);
        return page;
    }

    private JComponent buildNodePage() {
        JPanel page = pagePanel();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(pageTitle("NODE + IDENTITY","Operate this WLAN node and its persistent local wallet"),BorderLayout.NORTH);
        JPanel upper = new JPanel(new GridLayout(1,2,12,0));
        upper.setOpaque(false);
        upper.setPreferredSize(new Dimension(0,385));
        upper.setBorder(new EmptyBorder(14,0,0,0));
        upper.add(buildIdentityCard());
        upper.add(buildConnectivityCard());
        top.add(upper,BorderLayout.CENTER);
        page.add(top,BorderLayout.NORTH);

        WlanTheme.Card activityCard = card(new BorderLayout());
        activityCard.add(sectionHeader("LOCAL EVENT STREAM","Detected state changes and actions from this application"),BorderLayout.NORTH);
        activityList = new JPanel();
        activityList.setOpaque(false);
        activityList.setBorder(new EmptyBorder(4,14,16,14));
        activityList.setLayout(new BoxLayout(activityList,BoxLayout.Y_AXIS));
        activityCard.add(WlanTheme.scroll(activityList),BorderLayout.CENTER);
        JPanel activityPosition = new JPanel(new BorderLayout());
        activityPosition.setOpaque(false);
        activityPosition.setBorder(new EmptyBorder(12,0,0,0));
        activityPosition.add(activityCard,BorderLayout.CENTER);
        page.add(activityPosition,BorderLayout.CENTER);
        return page;
    }

    private JComponent buildIdentityCard() {
        WlanTheme.Card card = card(new BorderLayout());
        card.add(sectionHeader("LOCAL IDENTITY","Persistent wallet restored from this node's SQLite database"),BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(4,18,18,18));
        body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));
        identityState = WlanTheme.title("WALLET LOCKED",18);
        body.add(identityState);
        body.add(Box.createVerticalStrut(14));
        body.add(fieldLabel("PUBLIC LOGIN HASH"));
        publicLoginHash = readonlyField("");
        body.add(fieldWithCopy(publicLoginHash));
        body.add(Box.createVerticalStrut(10));
        body.add(fieldLabel("PRIVATE LOGIN HASH"));
        privateLoginHash = readonlyField("");
        body.add(fieldWithCopy(privateLoginHash));
        body.add(Box.createVerticalStrut(14));

        JPanel actions = new JPanel(new GridLayout(1,2,10,0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        identityLoginButton = new WlanTheme.AccentButton("OPEN LOGIN",true);
        identityLoginButton.addActionListener(event -> showLoginDialog());
        WlanTheme.AccentButton logout = new WlanTheme.AccentButton("LOGOUT",false);
        logout.addActionListener(event -> {
            controller.logout();
            requestSnapshot();
        });
        actions.add(identityLoginButton);
        actions.add(logout);
        body.add(actions);
        card.add(body,BorderLayout.CENTER);
        return card;
    }

    private JComponent buildConnectivityCard() {
        WlanTheme.Card card = card(new BorderLayout());
        card.add(sectionHeader("CONNECTIVITY","Automatic LAN discovery with manual TCP fallback"),BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(4,18,18,18));
        body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));
        body.add(infoStrip("PEER DISCOVERY","UDP 4999 · automatic search on the local network",WlanTheme.CYAN));
        body.add(Box.createVerticalStrut(8));
        body.add(infoStrip("LOCAL DATABASE","Wallet, blocks and transactions persist in SQLite",WlanTheme.SUCCESS));
        body.add(Box.createVerticalStrut(14));
        body.add(fieldLabel("MANUAL PEER / OPTIONAL"));

        JPanel peerLine = new JPanel(new BorderLayout(8,0));
        peerLine.setOpaque(false);
        JTextField ip = new WlanTheme.RoundedTextField();
        ip.getAccessibleContext().setAccessibleName("Manual peer IP address");
        ip.putClientProperty("JTextField.placeholderText","192.168.1.115");
        JTextField port = new WlanTheme.RoundedTextField("5000");
        port.getAccessibleContext().setAccessibleName("Manual peer port");
        port.setPreferredSize(new Dimension(82,40));
        peerLine.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        peerLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        peerLine.add(ip,BorderLayout.CENTER);
        peerLine.add(port,BorderLayout.EAST);
        body.add(peerLine);
        body.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new GridLayout(1,2,10,0));
        actions.setOpaque(false);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        WlanTheme.AccentButton connect = new WlanTheme.AccentButton("CONNECT",true);
        connect.addActionListener(event -> {
            String ipAddress = ip.getText();
            String peerPort = port.getText();
            executeAction("Connecting",() -> controller.connectManually(ipAddress,peerPort));
        });
        WlanTheme.AccentButton validate = new WlanTheme.AccentButton("VALIDATE CHAIN",false);
        validate.addActionListener(event -> validateChain());
        actions.add(connect);
        actions.add(validate);
        body.add(actions);
        card.add(body,BorderLayout.CENTER);
        return card;
    }

    private JPanel pagePanel() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        return page;
    }

    private WlanTheme.Card card(LayoutManager layout) {
        WlanTheme.Card card = new WlanTheme.Card(layout);
        card.setBorder(new EmptyBorder(0,0,0,0));
        return card;
    }

    private JComponent roundedComboBox(JComboBox<?> comboBox,int width,int height) {
        WlanTheme.ControlSurface surface = new WlanTheme.ControlSurface(new BorderLayout());
        surface.setBorder(new EmptyBorder(1,4,1,2));
        surface.setAlignmentX(Component.LEFT_ALIGNMENT);
        surface.setMaximumSize(new Dimension(width > 0 ? width : Integer.MAX_VALUE,height));
        if(width > 0) surface.setPreferredSize(new Dimension(width,height));
        comboBox.setOpaque(false);
        comboBox.setBorder(new EmptyBorder(0,7,0,3));
        surface.add(comboBox,BorderLayout.CENTER);
        return surface;
    }

    private JComponent pageTitle(String title,String subtitle) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(WlanTheme.title(title,27));
        panel.add(Box.createVerticalStrut(3));
        panel.add(WlanTheme.label(subtitle,13,WlanTheme.MUTED));
        return panel;
    }

    private JComponent sectionHeader(String title,String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(15,18,12,18));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
        text.add(WlanTheme.title(title,13));
        text.add(Box.createVerticalStrut(2));
        text.add(WlanTheme.label(subtitle,12,WlanTheme.MUTED));
        header.add(text,BorderLayout.WEST);
        return header;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = WlanTheme.label(text,12,WlanTheme.MUTED);
        label.setBorder(new EmptyBorder(0,1,5,0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel badge(String text,Color color) {
        return new WlanTheme.PillLabel(text,color);
    }

    private JComponent dotLegend(Color color,String text) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        panel.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        dot.setFont(WlanTheme.font(Font.BOLD,12));
        panel.add(dot);
        panel.add(WlanTheme.label(text,12,WlanTheme.MUTED));
        return panel;
    }

    private JTextField readonlyField(String value) {
        JTextField field = new WlanTheme.RoundedTextField(value);
        field.setEditable(false);
        field.setForeground(WlanTheme.TEXT_SOFT);
        field.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
        return field;
    }

    private JComponent fieldWithCopy(JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(7,0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(field,BorderLayout.CENTER);
        WlanTheme.AccentButton copy = new WlanTheme.AccentButton("COPY",false);
        copy.addActionListener(event -> copy(field.getText()));
        panel.add(copy,BorderLayout.EAST);
        return panel;
    }

    private JComponent infoStrip(String title,String value,Color accent) {
        WlanTheme.Card panel = new WlanTheme.Card(new BorderLayout(10,0),16)
                .fill(WlanTheme.alpha(WlanTheme.SURFACE_HIGH,185))
                .stroke(WlanTheme.alpha(accent,75));
        panel.setBorder(new EmptyBorder(9,11,9,11));
        JLabel dot = new JLabel("●");
        dot.setForeground(accent);
        panel.add(dot,BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
        text.add(WlanTheme.title(title,11));
        text.add(WlanTheme.label(value,12,WlanTheme.MUTED));
        panel.add(text,BorderLayout.CENTER);
        return panel;
    }

    private void styleTable(JTable table) {
        table.setOpaque(false);
        table.setBackground(WlanTheme.SURFACE);
        table.setForeground(WlanTheme.TEXT_SOFT);
        table.setSelectionBackground(WlanTheme.alpha(WlanTheme.PRIMARY,120));
        table.setSelectionForeground(WlanTheme.TEXT);
        table.setGridColor(WlanTheme.alpha(WlanTheme.BORDER,130));
        table.setRowHeight(46);
        table.setShowVerticalLines(false);
        table.setFont(WlanTheme.font(Font.PLAIN,13));
        table.getTableHeader().setBackground(WlanTheme.SURFACE_HIGH);
        table.getTableHeader().setForeground(WlanTheme.MUTED);
        table.getTableHeader().setFont(WlanTheme.font(Font.BOLD,12));
        table.getTableHeader().setPreferredSize(new Dimension(0,40));
        table.setDefaultRenderer(Object.class,new TransactionCellRenderer());
        table.setAutoCreateRowSorter(true);
    }

    private void showPage(String page) {
        pageLayout.show(pages,page);
        for(Map.Entry<String,NavButton> entry : navigation.entrySet()) entry.getValue().setSelected(entry.getKey().equals(page));
        if(topologyView != null) topologyView.setAnimationEnabled("COMMAND".equals(page));
        if(miningPanel != null) miningPanel.setAnimationEnabled("COMMAND".equals(page));
        if(walletView != null) walletView.setAnimationEnabled("WALLETS".equals(page));
    }

    private void requestSnapshot() {
        if(background.isShutdown()) return;
        if(!refreshRunning.compareAndSet(false,true)) return;
        try {
            background.execute(() -> {
                try {
                    WlanUIController.Snapshot next = controller.snapshot();
                    SwingUtilities.invokeLater(() -> {
                        try {
                            applySnapshot(next);
                        } finally {
                            refreshRunning.set(false);
                        }
                    });
                } catch(Exception e) {
                    refreshRunning.set(false);
                    SwingUtilities.invokeLater(() -> showToast("Snapshot error · " + e.getMessage(),WlanTheme.DANGER));
                }
            });
        } catch(RejectedExecutionException ignored) {
            refreshRunning.set(false);
        }
    }

    private void applySnapshot(WlanUIController.Snapshot next) {
        snapshot = next;
        updatingControls = true;

        headerNode.setText(WlanTheme.compact(next.nodeId,10) + "  ·  " + next.nodeType + "  ·  :" + next.listenPort);
        headerStatus.setText(next.peerCount == 0 ? "NO DIRECT PEERS" : "CONNECTED  " + next.peerCount);
        headerStatus.setForeground(next.peerCount == 0 ? WlanTheme.WARNING : WlanTheme.SUCCESS);
        headerLogin.setText(next.loggedIn ? "SIGNED IN  ·  " + WlanTheme.compact(next.alias,10) : "WALLET LOCKED");
        headerLogin.setForeground(next.loggedIn ? WlanTheme.SUCCESS : WlanTheme.MUTED);

        peersMetric.update(String.valueOf(next.peerCount),next.peerCount == 0 ? "Attempting UDP 4999" : "Direct TCP connections");
        heightMetric.update(String.valueOf(next.chainHeight),"Tip " + WlanTheme.compact(next.tipHash,6));
        mempoolMetric.update(String.valueOf(next.mempoolSize),next.mempoolSize == 0 ? "Waiting for traffic" : "Ready for mining");
        workMetric.update(WlanTheme.compact(next.cumulativeWork.toString(),8),"Difficulty " + next.difficulty);
        balanceValue.setText(Money.format(next.localBalance) + " MATH");

        loginButton.setText(next.loggedIn ? "LOGOUT" : "LOGIN");
        sendButton.setEnabled(next.loggedIn);
        receiverBox.setEnabled(next.loggedIn);
        amountField.setEnabled(next.loggedIn);
        autoToggle.setEnabled(next.loggedIn);
        autoToggle.setSelected(next.autoMode);
        autoDescription.setText(next.loggedIn
                ? (next.autoMode ? next.autoSent + " sent · " + next.autoRejected + " rejected" : "Only this node will generate traffic")
                : "Login required");

        topologyView.setSnapshot(next);
        chainRibbon.setBlocks(next.blocks);
        miningPanel.setSnapshot(next);
        updateReceiverChoices(next.wallets);
        updateChain(next.blocks);
        updateTransactions(next.transactions);
        updateWallets(next.wallets);
        updateActivity(next.activity);
        updateIdentity(next);
        updatingControls = false;
    }

    private void updateReceiverChoices(List<WlanUIController.WalletView> wallets) {
        StringBuilder fingerprint = new StringBuilder();
        for(WlanUIController.WalletView wallet : wallets) fingerprint.append(wallet.address);
        if(fingerprint.toString().equals(walletFingerprint)) return;
        walletFingerprint = fingerprint.toString();

        Object editorValue = receiverBox.isEditable() ? receiverBox.getEditor().getItem() : null;
        DefaultComboBoxModel<ReceiverChoice> model = new DefaultComboBoxModel<>();
        for(WlanUIController.WalletView wallet : wallets) {
            if(!wallet.local) model.addElement(new ReceiverChoice(wallet.label,wallet.address));
        }
        receiverBox.setModel(model);
        receiverBox.setEditable(true);
        if(editorValue != null && !editorValue.toString().isBlank()) receiverBox.getEditor().setItem(editorValue);
    }

    private void updateChain(List<WlanUIController.BlockView> blocks) {
        String fingerprint = blocks.size() + ":" + (blocks.isEmpty() ? "" : blocks.get(blocks.size() - 1).hash);
        if(fingerprint.equals(chainFingerprint)) return;
        chainFingerprint = fingerprint;
        chainList.removeAll();

        for(int i = blocks.size() - 1; i >= 0; i--) {
            WlanUIController.BlockView block = blocks.get(i);
            BlockRow row = new BlockRow(block);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE,104));
            chainList.add(row);
            chainList.add(Box.createVerticalStrut(8));
        }

        if(!blockSelectionLocked && !blocks.isEmpty()) selectedBlockHeight = blocks.get(blocks.size() - 1).height;
        updateBlockInspector();
        chainList.revalidate();
        chainList.repaint();
    }

    private void updateBlockInspector() {
        if(snapshot == null || blockInspector == null) return;
        WlanUIController.BlockView selected = null;
        for(WlanUIController.BlockView block : snapshot.blocks) if(block.height == selectedBlockHeight) selected = block;

        blockInspector.removeAll();
        if(selected == null) {
            blockInspector.add(WlanTheme.label("Select a block.",12,WlanTheme.MUTED));
        } else {
            blockInspector.add(WlanTheme.title("BLOCK #" + selected.height,26));
            blockInspector.add(WlanTheme.label(selected.height == 0 ? "DETERMINISTIC GENESIS" : "PROOF-OF-WORK ACCEPTED",12,
                    selected.height == 0 ? WlanTheme.PURPLE : WlanTheme.SUCCESS));
            blockInspector.add(Box.createVerticalStrut(18));
            addInspectorValue(blockInspector,"HASH",selected.hash);
            addInspectorValue(blockInspector,"PREVIOUS HASH",selected.previousHash);
            addInspectorValue(blockInspector,"MERKLE ROOT",selected.merkleRoot);
            addInspectorValue(blockInspector,"DIFFICULTY",String.valueOf(selected.difficulty));
            addInspectorValue(blockInspector,"MINING NONCE",String.valueOf(selected.nonce));
            addInspectorValue(blockInspector,"TRANSACTIONS",String.valueOf(selected.transactionCount));
            addInspectorValue(blockInspector,"MINER",selected.minerAddress == null ? "—" : selected.minerAddress);
            addInspectorValue(blockInspector,"TIME",selected.timestamp == 0 ? "Genesis constant" : new SimpleDateFormat("dd.MM.yyyy  HH:mm:ss").format(new Date(selected.timestamp)));
        }
        blockInspector.revalidate();
        blockInspector.repaint();
    }

    private void addInspectorValue(JPanel panel,String label,String value) {
        panel.add(WlanTheme.label(label,12,WlanTheme.MUTED));
        JTextArea area = new JTextArea(value == null ? "—" : value);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setOpaque(false);
        area.setForeground(WlanTheme.TEXT_SOFT);
        area.setFont(WlanTheme.font(Font.PLAIN,12));
        area.setBorder(new EmptyBorder(3,0,10,0));
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE,52));
        panel.add(area);
    }

    private void updateTransactions(List<WlanUIController.TransactionView> transactions) {
        StringBuilder fingerprint = new StringBuilder();
        for(WlanUIController.TransactionView transaction : transactions) {
            fingerprint.append(transaction.transactionId).append(':')
                    .append(transaction.status).append(':').append(transaction.blockHeight).append('|');
        }
        if(fingerprint.toString().equals(transactionFingerprint)) return;
        transactionFingerprint = fingerprint.toString();
        updateTransactionTable();
    }

    private void updateTransactionTable() {
        if(snapshot == null || transactionModel == null) return;
        String filter = transactionFilter == null ? "ALL" : String.valueOf(transactionFilter.getSelectedItem());
        transactionModel.setRowCount(0);

        for(WlanUIController.TransactionView transaction : snapshot.transactions) {
            if(!"ALL".equals(filter)
                    && !filter.equals(transaction.status)
                    && !filter.equals(transaction.type)) continue;

            transactionModel.addRow(new Object[]{
                    transaction.status,
                    transaction.type,
                    Money.format(transaction.amount) + " MATH",
                    "SYSTEM REWARD".equals(transaction.type) ? "SYSTEM" : WlanTheme.compact(transaction.sender,7),
                    WlanTheme.compact(transaction.receiver,7),
                    transaction.nonce,
                    transaction.blockHeight < 0 ? "MEMPOOL" : "#" + transaction.blockHeight,
                    WlanTheme.compact(transaction.transactionId,8)
            });
        }
    }

    private void updateWallets(List<WlanUIController.WalletView> wallets) {
        StringBuilder fingerprint = new StringBuilder();
        for(WlanUIController.WalletView wallet : wallets) {
            fingerprint.append(wallet.address).append(':').append(wallet.balance).append('|');
        }
        if(fingerprint.toString().equals(walletVisualFingerprint)) return;
        walletVisualFingerprint = fingerprint.toString();
        walletView.setWallets(wallets);
        updateWalletInspector();
    }

    private void updateWalletInspector() {
        if(walletInspector == null || walletView == null) return;
        WlanUIController.WalletView wallet = walletView.getSelectedWallet();
        walletInspector.removeAll();

        if(wallet == null) {
            walletInspector.add(WlanTheme.label("No wallet selected.",12,WlanTheme.MUTED));
        } else {
            walletInspector.add(WlanTheme.title(wallet.local ? "YOUR WALLET" : wallet.label.toUpperCase(),20));
            walletInspector.add(WlanTheme.label(wallet.local ? "LOCAL PRIVATE KEY AVAILABLE" : "REMOTE PUBLIC ACCOUNT",12,
                    wallet.local ? WlanTheme.SUCCESS : WlanTheme.PURPLE));
            walletInspector.add(Box.createVerticalStrut(20));
            walletInspector.add(WlanTheme.label("BALANCE",12,WlanTheme.MUTED));
            walletInspector.add(WlanTheme.title(Money.format(wallet.balance) + " MATH",25));
            walletInspector.add(Box.createVerticalStrut(18));
            addInspectorValue(walletInspector,"ADDRESS",wallet.address);
            walletInspector.add(Box.createVerticalGlue());
            JTextArea hint = new JTextArea("Bubble radius uses logarithmic balance scaling. Mouse wheel zooms; drag pans. Lines are layout guides, not peer links.");
            hint.setEditable(false);
            hint.setLineWrap(true);
            hint.setWrapStyleWord(true);
            hint.setOpaque(false);
            hint.setForeground(WlanTheme.MUTED);
            hint.setFont(WlanTheme.font(Font.PLAIN,12));
            hint.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
            walletInspector.add(hint);
        }
        walletInspector.revalidate();
        walletInspector.repaint();
    }

    private void updateActivity(List<WlanUIController.ActivityView> activities) {
        if(activityList == null) return;
        StringBuilder fingerprint = new StringBuilder();
        for(WlanUIController.ActivityView activity : activities) {
            fingerprint.append(activity.timestamp).append(':').append(activity.title).append('|');
        }
        if(fingerprint.toString().equals(activityFingerprint)) return;
        activityFingerprint = fingerprint.toString();
        activityList.removeAll();
        int shown = 0;

        for(WlanUIController.ActivityView activity : activities) {
            if(shown++ >= 80) break;
            activityList.add(new ActivityRow(activity));
            activityList.add(Box.createVerticalStrut(7));
        }
        if(activities.isEmpty()) activityList.add(WlanTheme.label("No local events yet.",12,WlanTheme.MUTED));
        activityList.revalidate();
        activityList.repaint();
    }

    private void updateIdentity(WlanUIController.Snapshot next) {
        if(identityState == null) return;
        identityState.setText(next.loggedIn ? "SIGNED IN  /  " + next.alias : "WALLET LOCKED");
        identityState.setForeground(next.loggedIn ? WlanTheme.SUCCESS : WlanTheme.TEXT);
        identityLoginButton.setEnabled(!next.loggedIn);
        identityLoginButton.setText(next.loggedIn ? "SIGNED IN" : "OPEN LOGIN");
        setTextIfChanged(publicLoginHash,controller.getPublicLoginHash());
        setTextIfChanged(privateLoginHash,controller.getPrivateLoginHash());
    }

    private void setTextIfChanged(JTextField field,String value) {
        String next = value == null ? "" : value;
        if(next.equals(field.getText())) return;
        field.setText(next);
        field.setCaretPosition(0);
    }

    private void sendTransaction() {
        Object receiverValue = receiverBox.isEditable() ? receiverBox.getEditor().getItem() : receiverBox.getSelectedItem();
        String receiver;
        if(receiverValue instanceof ReceiverChoice) receiver = ((ReceiverChoice) receiverValue).address;
        else receiver = receiverValue == null ? "" : receiverValue.toString();
        String amount = amountField.getText();
        executeAction("Signing transaction",() -> controller.sendTransaction(receiver,amount));
    }

    private void changeAutoMode() {
        if(updatingControls) return;
        WlanUIController.ActionResult result = controller.setAutoMode(autoToggle.isSelected());
        showToast(result.message,result.success ? WlanTheme.SUCCESS : WlanTheme.DANGER);
        if(!result.success) autoToggle.setSelected(false);
        requestSnapshot();
    }

    private void showLoginDialog() {
        if(controller.isLoggedIn()) {
            controller.logout();
            showToast("Wallet locked · Auto Mode stopped",WlanTheme.WARNING);
            requestSnapshot();
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner,"MATHOSCOIN · Session login",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        WlanTheme.Card surface = new WlanTheme.Card(new BorderLayout(),26);
        surface.setBorder(new EmptyBorder(22,24,22,24));
        surface.add(pageTitle("UNLOCK LOCAL WALLET","Verify both hashes before this node signs anything"),BorderLayout.NORTH);

        JPanel fields = new JPanel();
        fields.setOpaque(false);
        fields.setBorder(new EmptyBorder(22,0,14,0));
        fields.setLayout(new BoxLayout(fields,BoxLayout.Y_AXIS));
        JTextField publicHash = new WlanTheme.RoundedTextField();
        JTextField privateHash = new WlanTheme.RoundedTextField();
        publicHash.getAccessibleContext().setAccessibleName("Public login hash");
        privateHash.getAccessibleContext().setAccessibleName("Private login hash for this local wallet");
        fields.add(fieldLabel("PUBLIC LOGIN HASH"));
        fields.add(publicHash);
        fields.add(Box.createVerticalStrut(12));
        fields.add(fieldLabel("PRIVATE LOGIN HASH"));
        fields.add(privateHash);
        fields.add(Box.createVerticalStrut(10));
        fields.add(WlanTheme.label("This wallet is stored locally and restored from SQLite when the node restarts.",12,WlanTheme.SUCCESS));
        surface.add(fields,BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        buttons.setOpaque(false);
        WlanTheme.AccentButton fill = new WlanTheme.AccentButton("USE LOCAL CREDENTIALS",false);
        fill.addActionListener(event -> {
            publicHash.setText(controller.getPublicLoginHash());
            privateHash.setText(controller.getPrivateLoginHash());
        });
        WlanTheme.AccentButton cancel = new WlanTheme.AccentButton("CANCEL",false);
        cancel.addActionListener(event -> dialog.dispose());
        WlanTheme.AccentButton login = new WlanTheme.AccentButton("AUTHENTICATE",true);
        login.addActionListener(event -> {
            WlanUIController.ActionResult result = controller.login(publicHash.getText(),privateHash.getText());
            showToast(result.message,result.success ? WlanTheme.SUCCESS : WlanTheme.DANGER);
            if(result.success) dialog.dispose();
            requestSnapshot();
        });
        buttons.add(fill);
        buttons.add(cancel);
        buttons.add(login);
        surface.add(buttons,BorderLayout.SOUTH);

        dialog.setContentPane(surface);
        dialog.setSize(680,410);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void validateChain() {
        showToast("Validating full local chain…",WlanTheme.CYAN);
        background.execute(() -> {
            boolean valid = controller.validateChain();
            SwingUtilities.invokeLater(() -> {
                showToast(valid ? "Chain valid · full replay passed" : "Chain invalid · inspect console",valid ? WlanTheme.SUCCESS : WlanTheme.DANGER);
                requestSnapshot();
            });
        });
    }

    private void executeAction(String progress,Callable<WlanUIController.ActionResult> action) {
        showToast(progress + "…",WlanTheme.CYAN);
        background.execute(() -> {
            try {
                WlanUIController.ActionResult result = action.call();
                SwingUtilities.invokeLater(() -> {
                    showToast(result.message,result.success ? WlanTheme.SUCCESS : WlanTheme.DANGER);
                    requestSnapshot();
                });
            } catch(Exception e) {
                SwingUtilities.invokeLater(() -> showToast("Action failed · " + e.getMessage(),WlanTheme.DANGER));
            }
        });
    }

    private void showToast(String message,Color color) {
        toast.setText("  " + message);
        toast.setForeground(color);
    }

    private void copy(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);
        showToast("Copied to clipboard",WlanTheme.SUCCESS);
    }

    public void shutdown() {
        refreshTimer.stop();
        background.shutdownNow();
        controller.shutdown();
    }

    private static String duration(long millis) {
        long seconds = Math.max(0,millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long rest = seconds % 60L;
        return String.format("%02d:%02d:%02d",hours,minutes,rest);
    }

    private static final class ReceiverChoice {
        private final String label;
        private final String address;

        private ReceiverChoice(String label,String address) {
            this.label = label;
            this.address = address;
        }

        @Override public String toString() {
            return address;
        }
    }

    private class BlockRow extends WlanTheme.Card {
        private final WlanUIController.BlockView block;

        private BlockRow(WlanUIController.BlockView block) {
            super(new BorderLayout(14,0),16);
            this.block = block;
            setBorder(new EmptyBorder(13,14,13,14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel number = WlanTheme.title(String.format("#%03d",block.height),19);
            number.setPreferredSize(new Dimension(72,0));
            add(number,BorderLayout.WEST);

            JPanel data = new JPanel();
            data.setOpaque(false);
            data.setLayout(new BoxLayout(data,BoxLayout.Y_AXIS));
            data.add(WlanTheme.label(WlanTheme.compact(block.hash,15),12,WlanTheme.TEXT_SOFT));
            data.add(Box.createVerticalStrut(4));
            data.add(WlanTheme.label(block.transactionCount + " TX  ·  DIFF " + block.difficulty + "  ·  NONCE " + block.nonce,12,WlanTheme.MUTED));
            add(data,BorderLayout.CENTER);

            JLabel status = badge(block.height == 0 ? "GENESIS" : "ACCEPTED",block.height == 0 ? WlanTheme.PURPLE : WlanTheme.SUCCESS);
            add(status,BorderLayout.EAST);
            setFocusable(true);
            getAccessibleContext().setAccessibleName("Block " + block.height + ", " + block.transactionCount + " transactions");
            MouseAdapter selection = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    selectBlock();
                }
            };
            installSelection(this,selection);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"),"selectBlock");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"),"selectBlock");
            getActionMap().put("selectBlock",new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { selectBlock(); }
            });
        }

        private void installSelection(Component component,MouseAdapter listener) {
            component.addMouseListener(listener);
            component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if(component instanceof Container) {
                for(Component child : ((Container) component).getComponents()) installSelection(child,listener);
            }
        }

        private void selectBlock() {
            selectedBlockHeight = block.height;
            blockSelectionLocked = true;
            updateBlockInspector();
            chainList.repaint();
            requestFocusInWindow();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if(block.height == selectedBlockHeight) fill(WlanTheme.alpha(WlanTheme.PRIMARY,52)).stroke(WlanTheme.PRIMARY_LIGHT);
            else fill(WlanTheme.alpha(WlanTheme.SURFACE_HIGH,145)).stroke(WlanTheme.BORDER);
            super.paintComponent(graphics);
            if(hasFocus()) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(WlanTheme.CYAN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(2,2,getWidth() - 5,getHeight() - 5,14,14);
                g.dispose();
            }
        }
    }

    private static class ActivityRow extends JPanel {
        private ActivityRow(WlanUIController.ActivityView activity) {
            super(new BorderLayout(12,0));
            setOpaque(false);
            setBorder(new EmptyBorder(8,4,8,4));
            Color tone = "success".equals(activity.tone) ? WlanTheme.SUCCESS
                    : "danger".equals(activity.tone) ? WlanTheme.DANGER
                    : "warning".equals(activity.tone) ? WlanTheme.WARNING : WlanTheme.CYAN;
            JLabel dot = new JLabel("●");
            dot.setForeground(tone);
            dot.setVerticalAlignment(SwingConstants.TOP);
            add(dot,BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
            JLabel title = WlanTheme.title(activity.title,12);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            title.setMaximumSize(new Dimension(Integer.MAX_VALUE,title.getPreferredSize().height));
            text.add(title);
            JTextArea detail = new JTextArea(activity.detail);
            detail.setEditable(false);
            detail.setOpaque(false);
            detail.setLineWrap(true);
            detail.setWrapStyleWord(true);
            detail.setForeground(WlanTheme.MUTED);
            detail.setFont(WlanTheme.font(Font.PLAIN,12));
            detail.setRows(2);
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            detail.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
            text.add(detail);
            add(text,BorderLayout.CENTER);
            JLabel time = WlanTheme.label(new SimpleDateFormat("HH:mm:ss").format(new Date(activity.timestamp)),12,WlanTheme.MUTED);
            add(time,BorderLayout.EAST);
        }
    }

    private static class TransactionCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focused,int row,int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,value,selected,focused,row,column);
            label.setBorder(new EmptyBorder(0,10,0,10));
            if(!selected) {
                label.setBackground(row % 2 == 0 ? WlanTheme.alpha(WlanTheme.SURFACE,245) : WlanTheme.alpha(WlanTheme.SURFACE_HIGH,185));
                label.setForeground(WlanTheme.TEXT_SOFT);
                if(column == 0) label.setForeground("PENDING".equals(value) ? WlanTheme.WARNING : WlanTheme.SUCCESS);
                if(column == 1 && "SYSTEM REWARD".equals(value)) label.setForeground(WlanTheme.PURPLE);
            }
            return label;
        }
    }

    private static class MetricCard extends WlanTheme.Card {
        private final JLabel value;
        private final JLabel detail;

        private MetricCard(String title,String initial,String detailText,Color accent) {
            super(new BorderLayout(),18);
            setBorder(new EmptyBorder(14,16,13,16));
            JLabel titleLabel = WlanTheme.label(title,12,WlanTheme.MUTED);
            titleLabel.setIcon(new ColorDotIcon(accent,7));
            titleLabel.setIconTextGap(8);
            add(titleLabel,BorderLayout.NORTH);
            value = WlanTheme.title(initial,26);
            add(value,BorderLayout.CENTER);
            detail = WlanTheme.label(detailText,12,WlanTheme.MUTED);
            add(detail,BorderLayout.SOUTH);
        }

        private void update(String valueText,String detailText) {
            value.setText(valueText);
            detail.setText(detailText);
        }
    }

    private static class ColorDotIcon implements Icon {
        private final Color color;
        private final int size;
        private ColorDotIcon(Color color,int size) { this.color = color; this.size = size; }
        @Override public void paintIcon(Component component,Graphics graphics,int x,int y) {
            graphics.setColor(color);
            graphics.fillOval(x,y,size,size);
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    private static class NavButton extends JButton {
        private boolean selected;
        private boolean hover;
        private final String symbol;
        private final String label;

        private NavButton(String symbol,String label) {
            this.symbol = symbol;
            this.label = label;
            setPreferredSize(new Dimension(62,58));
            setMaximumSize(new Dimension(62,58));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent event) { hover = false; repaint(); }
            });
        }

        @Override public void setSelected(boolean selected) {
            super.setSelected(selected);
            this.selected = selected;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if(selected || hover) {
                g.setColor(selected ? WlanTheme.alpha(WlanTheme.PRIMARY,105) : WlanTheme.alpha(WlanTheme.SURFACE_HIGH,180));
                g.fillRoundRect(0,0,getWidth(),getHeight(),17,17);
                if(selected) {
                    g.setColor(WlanTheme.CYAN);
                    g.fillRoundRect(0,13,3,getHeight() - 26,3,3);
                }
            }
            g.setFont(WlanTheme.font(Font.BOLD,20));
            g.setColor(selected ? WlanTheme.TEXT : WlanTheme.MUTED);
            FontMetrics symbolMetrics = g.getFontMetrics();
            g.drawString(symbol,(getWidth() - symbolMetrics.stringWidth(symbol)) / 2,25);
            g.setFont(WlanTheme.font(Font.BOLD,10));
            FontMetrics labelMetrics = g.getFontMetrics();
            g.drawString(label.toUpperCase(),(getWidth() - labelMetrics.stringWidth(label.toUpperCase())) / 2,44);
            if(hasFocus()) {
                g.setColor(WlanTheme.CYAN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(2,2,getWidth() - 5,getHeight() - 5,15,15);
            }
            g.dispose();
        }
    }

    private static class LogoMark extends JComponent {
        private LogoMark() { setPreferredSize(new Dimension(54,54)); setMaximumSize(new Dimension(54,54)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(WlanTheme.alpha(WlanTheme.PRIMARY,72));
            g.fillOval(1,1,52,52);
            g.setStroke(new BasicStroke(2.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.setColor(WlanTheme.TEXT_SOFT);
            g.drawArc(7,7,40,40,38,284);
            g.setColor(WlanTheme.CYAN);
            g.drawArc(13,13,28,28,52,262);
            g.setColor(WlanTheme.TEXT);
            g.drawArc(19,19,16,16,67,232);
            g.fillOval(24,24,6,6);
            g.dispose();
        }
    }

    private static class NetworkTopologyView extends JPanel {
        private WlanUIController.Snapshot snapshot;
        private double phase;
        private boolean animationEnabled;
        private Timer timer;

        private NetworkTopologyView() { setOpaque(false); }

        @Override public void addNotify() {
            super.addNotify();
            if(timer == null) timer = new Timer(28,event -> { phase += .018; repaint(); });
            if(animationEnabled) timer.start();
        }

        @Override public void removeNotify() {
            if(timer != null) timer.stop();
            super.removeNotify();
        }

        private void setAnimationEnabled(boolean animationEnabled) {
            this.animationEnabled = animationEnabled;
            if(timer == null) timer = new Timer(28,event -> { phase += .018; repaint(); });
            if(animationEnabled && isDisplayable()) timer.start();
            else timer.stop();
        }

        private void setSnapshot(WlanUIController.Snapshot snapshot) {
            this.snapshot = snapshot;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int cx = width / 2;
            int cy = height / 2 + 4;
            int peers = snapshot == null ? 0 : snapshot.peerCount;
            int orbit = Math.max(105,Math.min(width,height) / 3);

            g.setStroke(new BasicStroke(1f));
            g.setColor(WlanTheme.alpha(WlanTheme.PRIMARY_LIGHT,28));
            g.drawOval(cx - orbit,cy - orbit,orbit * 2,orbit * 2);
            g.drawOval(cx - orbit / 2,cy - orbit / 2,orbit,orbit);

            for(int i = 0; i < peers; i++) {
                double angle = -Math.PI / 2.0 + i * Math.PI * 2.0 / Math.max(1,peers);
                int px = cx + (int) (Math.cos(angle) * orbit);
                int py = cy + (int) (Math.sin(angle) * orbit);
                g.setColor(WlanTheme.alpha(WlanTheme.PURPLE,100));
                g.setStroke(new BasicStroke(1.4f));
                g.drawLine(cx,cy,px,py);

                double progress = (phase + i * .17) % 1.0;
                int packetX = (int) (cx + (px - cx) * progress);
                int packetY = (int) (cy + (py - cy) * progress);
                g.setColor(WlanTheme.CYAN);
                g.fillOval(packetX - 4,packetY - 4,8,8);

                g.setColor(WlanTheme.alpha(WlanTheme.PURPLE,70));
                g.fillOval(px - 32,py - 32,64,64);
                g.setColor(WlanTheme.PURPLE);
                g.fillOval(px - 25,py - 25,50,50);
                g.setColor(WlanTheme.TEXT);
                g.setFont(WlanTheme.font(Font.BOLD,12));
                String peer = String.format("P%02d",i + 1);
                g.drawString(peer,px - g.getFontMetrics().stringWidth(peer) / 2,py + 4);
                g.setFont(WlanTheme.font(Font.PLAIN,12));
                g.setColor(WlanTheme.MUTED);
                String direct = "DIRECT";
                g.drawString(direct,px - g.getFontMetrics().stringWidth(direct) / 2,py + 42);
            }

            double pulse = 7 + Math.sin(phase * 6) * 4;
            g.setColor(WlanTheme.alpha(WlanTheme.PRIMARY_LIGHT,45));
            g.fillOval((int) (cx - 63 - pulse),(int) (cy - 63 - pulse),(int) ((63 + pulse) * 2),(int) ((63 + pulse) * 2));
            g.setPaint(new RadialGradientPaint(new Point(cx - 20,cy - 25),105,new float[]{0f,1f},
                    new Color[]{WlanTheme.PRIMARY_LIGHT,WlanTheme.PRIMARY}));
            g.fillOval(cx - 60,cy - 60,120,120);
            g.setColor(WlanTheme.TEXT);
            g.setFont(WlanTheme.font(Font.BOLD,14));
            String node = snapshot == null ? "NODE" : WlanTheme.compact(snapshot.nodeId,8);
            g.drawString(node,cx - g.getFontMetrics().stringWidth(node) / 2,cy - 5);
            g.setFont(WlanTheme.font(Font.BOLD,12));
            String type = snapshot == null ? "STARTING" : snapshot.nodeType.name();
            g.setColor(WlanTheme.TEXT_SOFT);
            g.drawString(type,cx - g.getFontMetrics().stringWidth(type) / 2,cy + 15);

            if(peers == 0) {
                g.setFont(WlanTheme.font(Font.PLAIN,12));
                g.setColor(WlanTheme.WARNING);
                String scanning = "DISCOVERY ATTEMPT / MANUAL FALLBACK READY";
                g.drawString(scanning,cx - g.getFontMetrics().stringWidth(scanning) / 2,cy + orbit + 35);
            }
            g.dispose();
        }
    }

    private static class ChainRibbon extends JPanel {
        private List<WlanUIController.BlockView> blocks = List.of();
        private ChainRibbon() { setOpaque(false); }
        private void setBlocks(List<WlanUIController.BlockView> blocks) { this.blocks = List.copyOf(blocks); repaint(); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int shown = Math.min(6,blocks.size());
            if(shown == 0) { g.dispose(); return; }
            int gap = 18;
            int cardWidth = Math.max(96,(getWidth() - 36 - gap * (shown - 1)) / shown);
            int cardHeight = Math.min(92,getHeight() - 18);
            int start = blocks.size() - shown;
            int y = (getHeight() - cardHeight) / 2;

            for(int i = 0; i < shown; i++) {
                WlanUIController.BlockView block = blocks.get(start + i);
                int x = 18 + i * (cardWidth + gap);
                if(i > 0) {
                    g.setColor(WlanTheme.alpha(WlanTheme.CYAN,100));
                    g.setStroke(new BasicStroke(2f));
                    g.drawLine(x - gap,y + cardHeight / 2,x,y + cardHeight / 2);
                }
                g.setColor(block.height == blocks.get(blocks.size() - 1).height
                        ? WlanTheme.alpha(WlanTheme.PRIMARY,110) : WlanTheme.alpha(WlanTheme.SURFACE_HIGH,220));
                g.fillRoundRect(x,y,cardWidth,cardHeight,15,15);
                g.setColor(block.height == 0 ? WlanTheme.PURPLE : WlanTheme.alpha(WlanTheme.PRIMARY_LIGHT,150));
                g.drawRoundRect(x,y,cardWidth,cardHeight,15,15);
                g.setColor(WlanTheme.TEXT);
                g.setFont(WlanTheme.font(Font.BOLD,13));
                g.drawString("#" + block.height,x + 12,y + 23);
                g.setFont(WlanTheme.font(Font.PLAIN,12));
                g.setColor(WlanTheme.TEXT_SOFT);
                g.drawString(WlanTheme.compact(block.hash,6),x + 12,y + 44);
                g.setColor(WlanTheme.MUTED);
                g.drawString(block.transactionCount + " TX  ·  D" + block.difficulty,x + 12,y + 64);
            }
            g.dispose();
        }
    }

    private static class MiningPanel extends JPanel {
        private WlanUIController.Snapshot snapshot;
        private double phase;
        private boolean animationEnabled;
        private Timer timer;
        private MiningPanel() { setOpaque(false); }

        @Override public void addNotify() {
            super.addNotify();
            if(timer == null) timer = new Timer(30,event -> { phase += .035; repaint(); });
            if(animationEnabled) timer.start();
        }

        @Override public void removeNotify() {
            if(timer != null) timer.stop();
            super.removeNotify();
        }

        private void setAnimationEnabled(boolean animationEnabled) {
            this.animationEnabled = animationEnabled;
            if(timer == null) timer = new Timer(30,event -> { phase += .035; repaint(); });
            if(animationEnabled && isDisplayable()) timer.start();
            else timer.stop();
        }

        private void setSnapshot(WlanUIController.Snapshot snapshot) { this.snapshot = snapshot; repaint(); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = 70;
            int cy = getHeight() / 2;
            boolean miner = snapshot != null && snapshot.nodeType == Computer.NodeType.MINER;
            boolean active = miner && snapshot.mining;
            Color color = !miner ? WlanTheme.MUTED : active ? WlanTheme.CYAN : WlanTheme.PURPLE;

            for(int ring = 3; ring >= 1; ring--) {
                double wave = (phase * 25 + ring * 13) % 45;
                g.setColor(WlanTheme.alpha(color,Math.max(0,70 - (int) wave)));
                int radius = 27 + (int) wave;
                g.drawOval(cx - radius,cy - radius,radius * 2,radius * 2);
            }
            g.setColor(WlanTheme.alpha(color,60));
            g.fillOval(cx - 34,cy - 34,68,68);
            g.setColor(color);
            g.fillOval(cx - 25,cy - 25,50,50);
            g.setFont(WlanTheme.font(Font.BOLD,15));
            g.setColor(WlanTheme.TEXT);
            g.drawString(miner ? "M" : "—",cx - g.getFontMetrics().stringWidth(miner ? "M" : "—") / 2,cy + 5);

            int tx = 128;
            g.setFont(WlanTheme.font(Font.BOLD,13));
            g.setColor(WlanTheme.TEXT);
            g.drawString(!miner ? "MINING DISABLED" : active ? "HASHING NOW" : "MINER READY",tx,cy - 16);
            g.setFont(WlanTheme.font(Font.PLAIN,12));
            g.setColor(WlanTheme.MUTED);
            g.drawString(snapshot == null ? "Starting…" : snapshot.localBlocksMined + " blocks won locally",tx,cy + 4);
            g.drawString(snapshot == null ? "" : "Uptime " + duration(snapshot.uptime),tx,cy + 22);
            g.dispose();
        }
    }
}
