import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BlockchainDashboard extends JPanel {
    private static final String OVERVIEW = "overview";
    private static final String CHAIN = "chain";
    private static final String WALLETS = "wallets";
    private static final String TRANSACTIONS = "transactions";
    private static final String NODES = "nodes";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BlockchainUIController controller;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageHost = new JPanel(pageLayout);
    private final Map<String,ModernTheme.NavButton> navigation = new HashMap<>();
    private final JLabel pageTitle = label("Pregled mreže",Font.BOLD,27,ModernTheme.TEXT);
    private final JLabel pageSubtitle = label("Stanje lanca, walleta i minera u stvarnom vremenu",Font.PLAIN,13,ModernTheme.MUTED);
    private final JLabel clockLabel = label("",Font.BOLD,12,ModernTheme.MUTED);
    private final ModernTheme.PillLabel livePill = new ModernTheme.PillLabel("LIVE · LOCAL",ModernTheme.SUCCESS);
    private final JLabel toast = label("",Font.BOLD,12,ModernTheme.TEXT);
    private final JTextField globalSearch = new PromptField("Traži blok / wallet");
    private JButton loginButton;
    private JToggleButton autoModeButton;

    private final OverviewPage overviewPage = new OverviewPage();
    private final ChainPage chainPage = new ChainPage();
    private final WalletPage walletPage = new WalletPage();
    private final TransactionsPage transactionsPage = new TransactionsPage();
    private final NodesPage nodesPage = new NodesPage();
    private final javax.swing.Timer refreshTimer;
    private final javax.swing.Timer animationTimer;
    private final javax.swing.Timer toastTimer;

    private BlockchainUIController.Snapshot snapshot;
    private boolean motionEnabled = true;
    private boolean liveEnabled = true;
    private double motionPhase;

    public BlockchainDashboard(BlockchainUIController controller) {
        this.controller = controller;
        this.snapshot = controller.snapshot();
        setLayout(new BorderLayout());
        setBackground(ModernTheme.CANVAS);
        add(createSidebar(),BorderLayout.WEST);
        add(createWorkspace(),BorderLayout.CENTER);

        refreshTimer = new javax.swing.Timer(600,event -> refreshSnapshot());
        refreshTimer.setCoalesce(true);
        refreshTimer.start();

        animationTimer = new javax.swing.Timer(16,event -> animate());
        animationTimer.setCoalesce(true);
        animationTimer.start();

        toastTimer = new javax.swing.Timer(3400,event -> toast.setVisible(false));
        toastTimer.setRepeats(false);
        showPage(OVERVIEW);
        refreshSnapshot();
    }

    public void shutdown() {
        refreshTimer.stop();
        animationTimer.stop();
        toastTimer.stop();
        controller.shutdown();
    }

    public void setMotionEnabled(boolean enabled) {
        motionEnabled = enabled;
        if(enabled) animationTimer.start();
        else {
            animationTimer.stop();
            motionPhase = 0;
            repaintAnimatedViews();
        }
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(ModernTheme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(224,0));
        sidebar.setBorder(ModernTheme.padding(24,18,20,18));

        JPanel brand = new JPanel(new BorderLayout(12,0));
        brand.setOpaque(false);
        brand.add(new BrandMark(),BorderLayout.WEST);
        JPanel names = verticalPanel();
        names.add(label("MATHOS COIN",Font.BOLD,18,ModernTheme.TEXT));
        names.add(label("BLOCKCHAIN OBSERVATORY",Font.BOLD,9,ModernTheme.BLUE));
        brand.add(names,BorderLayout.CENTER);
        sidebar.add(brand,BorderLayout.NORTH);

        JPanel nav = verticalPanel();
        nav.setBorder(ModernTheme.padding(36,0,0,0));
        ButtonGroup group = new ButtonGroup();
        addNav(nav,group,OVERVIEW,"01   Pregled");
        addNav(nav,group,CHAIN,"02   Blockchain");
        addNav(nav,group,WALLETS,"03   Walleti");
        addNav(nav,group,TRANSACTIONS,"04   Transakcije");
        addNav(nav,group,NODES,"05   Nodeovi i mining");
        sidebar.add(nav,BorderLayout.CENTER);

        ModernTheme.RoundedPanel status = new ModernTheme.RoundedPanel(16,ModernTheme.CARD,ModernTheme.BORDER);
        status.setLayout(new BoxLayout(status,BoxLayout.Y_AXIS));
        status.setBorder(ModernTheme.padding(14,14,14,14));
        JLabel local = label("LOCAL SIMULATION",Font.BOLD,10,ModernTheme.CYAN);
        JLabel description = label("Core ostaje nepromijenjen",Font.PLAIN,11,ModernTheme.MUTED);
        local.setAlignmentX(LEFT_ALIGNMENT);
        description.setAlignmentX(LEFT_ALIGNMENT);
        status.add(local);
        status.add(Box.createVerticalStrut(5));
        status.add(description);
        status.add(Box.createVerticalStrut(12));

        JToggleButton motion = secondaryToggle("Animacije",true);
        motion.setAlignmentX(LEFT_ALIGNMENT);
        motion.addActionListener(event -> {
            setMotionEnabled(motion.isSelected());
            motion.setText(motion.isSelected() ? "Animacije" : "Motion paused");
        });
        status.add(motion);
        status.add(Box.createVerticalStrut(8));
        autoModeButton = secondaryToggle("Auto mode · OFF",false);
        autoModeButton.setAlignmentX(LEFT_ALIGNMENT);
        autoModeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));
        autoModeButton.setToolTipText("Promjenjivi promet svakih 0.45–1.4 sekunde, uz povremene burstove i 10% nevaljanih pokušaja.");
        autoModeButton.addActionListener(event -> {
            boolean enabled = autoModeButton.isSelected();
            runAction(() -> controller.setAutoMode(enabled));
        });
        status.add(autoModeButton);
        sidebar.add(status,BorderLayout.SOUTH);
        return sidebar;
    }

    private void addNav(JPanel parent, ButtonGroup group, String id, String text) {
        ModernTheme.NavButton button = new ModernTheme.NavButton(text);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));
        button.addActionListener(event -> showPage(id));
        navigation.put(id,button);
        group.add(button);
        parent.add(button);
        parent.add(Box.createVerticalStrut(7));
    }

    private JPanel createWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(ModernTheme.CANVAS);
        workspace.add(createHeader(),BorderLayout.NORTH);
        pageHost.setOpaque(false);
        pageHost.setBorder(ModernTheme.padding(0,24,24,24));
        pageHost.add(overviewPage,OVERVIEW);
        pageHost.add(chainPage,CHAIN);
        pageHost.add(walletPage,WALLETS);
        pageHost.add(transactionsPage,TRANSACTIONS);
        pageHost.add(nodesPage,NODES);
        workspace.add(pageHost,BorderLayout.CENTER);
        return workspace;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(24,0));
        header.setOpaque(false);
        header.setBorder(ModernTheme.padding(22,28,18,28));

        JPanel titlePanel = verticalPanel();
        titlePanel.add(pageTitle);
        titlePanel.add(Box.createVerticalStrut(3));
        titlePanel.add(pageSubtitle);
        titlePanel.setMinimumSize(new Dimension(280,48));
        header.add(titlePanel,BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        actions.setOpaque(false);
        globalSearch.setColumns(18);
        globalSearch.setToolTipText("Traži block height, hash, wallet ili adresu");
        ModernTheme.styleInput(globalSearch);
        globalSearch.addActionListener(event -> runGlobalSearch());
        actions.add(globalSearch);
        actions.add(livePill);
        loginButton = outlineButton("Login");
        loginButton.addActionListener(event -> showLoginDialog());
        actions.add(loginButton);
        actions.add(clockLabel);

        JToggleButton live = secondaryToggle("Pause live",true);
        live.addActionListener(event -> {
            liveEnabled = live.isSelected();
            live.setText(liveEnabled ? "Pause live" : "Resume live");
            livePill.setText(liveEnabled ? "LIVE · LOCAL" : "PAUSED");
            livePill.setTone(liveEnabled ? ModernTheme.SUCCESS : ModernTheme.WARNING);
            if(liveEnabled) {
                refreshTimer.start();
                refreshSnapshot();
            } else refreshTimer.stop();
        });
        actions.add(live);
        header.add(actions,BorderLayout.EAST);

        toast.setVisible(false);
        toast.setBorder(ModernTheme.padding(8,12,8,12));
        header.add(toast,BorderLayout.SOUTH);
        return header;
    }

    private void showPage(String page) {
        pageLayout.show(pageHost,page);
        navigation.values().forEach(button -> button.setSelected(false));
        ModernTheme.NavButton selected = navigation.get(page);
        if(selected != null) selected.setSelected(true);

        if(OVERVIEW.equals(page)) setHeader("Pregled mreže","Stanje lanca, walleta i minera u stvarnom vremenu");
        else if(CHAIN.equals(page)) setHeader("Blockchain explorer","Blokovi su povezani hashom i otvaraju stvarne detalje headera");
        else if(WALLETS.equals(page)) setHeader("Wallet universe","Bubble mapa i precizna rang-lista svih registriranih adresa");
        else if(TRANSACTIONS.equals(page)) setHeader("Transakcije","Potpiši, pošalji i prati transakciju od mempoola do bloka");
        else setHeader("Nodeovi i mining","Kontroliraj simulirane nodeove i gledaj stvarnu mining utrku");
    }

    private void setHeader(String title, String subtitle) {
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
        pageTitle.getParent().revalidate();
        pageTitle.getParent().repaint();
    }

    private void refreshSnapshot() {
        snapshot = controller.snapshot();
        clockLabel.setText(CLOCK.format(Instant.now()));
        overviewPage.update(snapshot);
        chainPage.update(snapshot);
        walletPage.update(snapshot);
        transactionsPage.update(snapshot);
        nodesPage.update(snapshot);
        updateLoginState();
        updateAutoModeState();
        livePill.setText(snapshot.mining ? "MINING · LOCAL" : liveEnabled ? "LIVE · LOCAL" : "PAUSED");
        livePill.setTone(snapshot.mining ? ModernTheme.PURPLE : liveEnabled ? ModernTheme.SUCCESS : ModernTheme.WARNING);
    }

    private void updateAutoModeState() {
        autoModeButton.setSelected(snapshot.autoModeEnabled);
        autoModeButton.setText(snapshot.autoModeEnabled ? "Auto mode · ON" : "Auto mode · OFF");
        autoModeButton.setForeground(snapshot.autoModeEnabled ? ModernTheme.SUCCESS : ModernTheme.TEXT);
        autoModeButton.setToolTipText("Prihvaćeno: " + snapshot.automaticTransactions + " · odbijeno: " + snapshot.rejectedAutomaticTransactions + " · promet dolazi u nasumičnim burstovima.");
    }

    private void animate() {
        motionPhase = (System.nanoTime() % 1_800_000_000L) / 1_800_000_000.0;
        repaintAnimatedViews();
    }

    private void repaintAnimatedViews() {
        overviewPage.setPhase(motionPhase);
        chainPage.setPhase(motionPhase);
        walletPage.setPhase(motionPhase);
        nodesPage.setPhase(motionPhase);
    }

    private void runGlobalSearch() {
        String query = globalSearch.getText().trim().toLowerCase(Locale.ROOT);
        if(query.isEmpty()) return;
        for(BlockchainUIController.BlockView block : snapshot.blocks) {
            if(String.valueOf(block.index).equals(query) || block.hash.toLowerCase(Locale.ROOT).contains(query)) {
                showPage(CHAIN);
                chainPage.selectBlock(block);
                return;
            }
        }
        for(BlockchainUIController.WalletView wallet : snapshot.wallets) {
            if(wallet.label.toLowerCase(Locale.ROOT).contains(query) || wallet.address.toLowerCase(Locale.ROOT).contains(query)) {
                showPage(WALLETS);
                walletPage.focusWallet(query);
                return;
            }
        }
        showToast(false,"Nema bloka ni walleta koji odgovara pretrazi.");
    }

    private void runAction(Supplier<BlockchainUIController.ActionResult> action) {
        new SwingWorker<BlockchainUIController.ActionResult,Void>() {
            @Override
            protected BlockchainUIController.ActionResult doInBackground() {
                return action.get();
            }

            @Override
            protected void done() {
                try {
                    showResult(get());
                    refreshSnapshot();
                } catch(Exception e) {
                    showToast(false,"Akcija nije završila: " + safeMessage(e));
                }
            }
        }.execute();
    }

    private void showResult(BlockchainUIController.ActionResult result) {
        showToast(result.success,result.message);
    }

    private void showToast(boolean success, String message) {
        toast.setText((success ? "OK  " : "!  ") + message);
        toast.setForeground(success ? ModernTheme.SUCCESS : ModernTheme.ERROR);
        toast.setVisible(true);
        toastTimer.restart();
    }

    private void openBlock(BlockchainUIController.BlockView block) {
        showPage(CHAIN);
        chainPage.selectBlock(block);
    }

    private void updateLoginState() {
        BlockchainUIController.LoginView login = controller.currentLogin();
        if(login == null) {
            loginButton.setText("Login");
            loginButton.setForeground(ModernTheme.TEXT);
            loginButton.setToolTipText("Provjeri public i private login hash");
            return;
        }
        loginButton.setText(compact(login.label,10) + " · " + login.type);
        loginButton.setForeground(ModernTheme.SUCCESS);
        loginButton.setToolTipText("Prijavljen: " + login.label + " · " + login.address);
    }

    private class OverviewPage extends JPanel {
        private final MetricCard height = new MetricCard("CHAIN HEIGHT","0",ModernTheme.PURPLE);
        private final MetricCard transactions = new MetricCard("CONFIRMED TX","0",ModernTheme.BLUE);
        private final MetricCard supply = new MetricCard("TOTAL VALUE","0 MATHOS",ModernTheme.CYAN);
        private final MetricCard nodes = new MetricCard("VALIDATOR NODES","0",ModernTheme.SUCCESS);
        private final ChainLane chainLane = new ChainLane(BlockchainDashboard.this::openBlock);
        private final WalletBubbleView bubbles = new WalletBubbleView();
        private final MiningRaceView mining = new MiningRaceView();
        private final ActivityPanel activity = new ActivityPanel();

        OverviewPage() {
            setOpaque(false);
            setLayout(new BorderLayout());
            JPanel content = verticalPanel();
            content.setBorder(ModernTheme.padding(2,2,16,2));

            JPanel metrics = new JPanel(new GridLayout(1,4,14,0));
            metrics.setOpaque(false);
            metrics.add(height);
            metrics.add(transactions);
            metrics.add(supply);
            metrics.add(nodes);
            fixedHeight(metrics,112);
            content.add(metrics);
            content.add(Box.createVerticalStrut(16));

            ModernTheme.SmoothScrollPane chainScroll = new ModernTheme.SmoothScrollPane(chainLane,Adjustable.HORIZONTAL);
            chainScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            chainScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            content.add(sectionCard("Lanac blokova","Klikni blok za header, Merkle root i transakcije",chainScroll,250));
            content.add(Box.createVerticalStrut(16));

            JPanel middle = new JPanel(new GridLayout(1,2,16,0));
            middle.setOpaque(false);
            ModernTheme.SmoothScrollPane bubbleScroll = new ModernTheme.SmoothScrollPane(bubbles);
            bubbleScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            middle.add(sectionCard("Wallet bubbles","Logaritamska skala jasno odvaja male i velike balanse",bubbleScroll,320));
            middle.add(sectionCard("Mining race","Pojavljuje se samo kada postoji kandidat ili aktivna utrka",mining,320));
            fixedHeight(middle,400);
            content.add(middle);
            content.add(Box.createVerticalStrut(16));
            content.add(sectionCard("Aktivnost mreže","Kratki log bitnih događaja bez razvojnog šuma",activity,270));

            ModernTheme.SmoothScrollPane scroll = new ModernTheme.SmoothScrollPane(content);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scroll,BorderLayout.CENTER);
        }

        void update(BlockchainUIController.Snapshot data) {
            height.setValue("#" + data.chainHeight,"TIP " + ModernTheme.shortHash(data.latestHash,6));
            transactions.setValue(String.valueOf(data.confirmedTransactionCount),data.pendingTransactions.size() + " lokalno pending");
            supply.setValue(data.totalSupplyText + " MATHOS","8-decimal ledger");
            long online = data.nodes.stream().filter(node -> node.running || node.mining).count();
            nodes.setValue(String.valueOf(data.nodes.size()),online + " aktivno · " + data.difficulty + " difficulty");
            chainLane.setBlocks(data.blocks);
            bubbles.setWallets(data.wallets);
            mining.update(data);
            activity.update(data.activity);
        }

        void setPhase(double phase) {
            chainLane.setPhase(phase);
            bubbles.setPhase(phase);
            mining.setPhase(phase);
        }
    }

    private class ChainPage extends JPanel {
        private final ChainLane chainLane = new ChainLane(this::selectBlock);
        private final BlockDetailPanel details = new BlockDetailPanel();
        private final JTextField search = new PromptField("Traži block height ili hash");
        private BlockchainUIController.BlockView selected;

        ChainPage() {
            setOpaque(false);
            setLayout(new BorderLayout(0,14));

            JPanel toolbar = new JPanel(new BorderLayout(12,0));
            toolbar.setOpaque(false);
            search.setToolTipText("Block height ili dio hasha");
            ModernTheme.styleInput(search);
            search.addActionListener(event -> searchBlock());
            toolbar.add(search,BorderLayout.CENTER);
            JButton validate = outlineButton("Provjeri cijeli chain");
            validate.addActionListener(event -> {
                validate.setEnabled(false);
                controller.validateChainAsync().whenComplete((valid,error) -> SwingUtilities.invokeLater(() -> {
                    validate.setEnabled(true);
                    if(error != null) showToast(false,"Provjera nije završila.");
                    else showToast(valid,"Chain je " + (valid ? "potpuno valjan." : "nevaljan."));
                    refreshSnapshot();
                }));
            });
            toolbar.add(validate,BorderLayout.EAST);
            add(toolbar,BorderLayout.NORTH);

            JPanel content = verticalPanel();
            ModernTheme.SmoothScrollPane laneScroll = new ModernTheme.SmoothScrollPane(chainLane,Adjustable.HORIZONTAL);
            laneScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            content.add(sectionCard("Block lane","Genesis → latest block; scrollbar i kotačić pomiču lanac",laneScroll,260));
            content.add(Box.createVerticalStrut(14));
            content.add(details);
            ModernTheme.SmoothScrollPane pageScroll = new ModernTheme.SmoothScrollPane(content);
            pageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(pageScroll,BorderLayout.CENTER);
        }

        void update(BlockchainUIController.Snapshot data) {
            chainLane.setBlocks(data.blocks);
            if(selected == null && !data.blocks.isEmpty()) selectBlock(data.blocks.get(data.blocks.size() - 1));
            else if(selected != null) {
                for(BlockchainUIController.BlockView block : data.blocks) if(block.index == selected.index) {
                    selected = block;
                    details.setBlock(block);
                    break;
                }
            }
        }

        void selectBlock(BlockchainUIController.BlockView block) {
            selected = block;
            chainLane.setSelectedIndex(block.index);
            details.setBlock(block);
        }

        private void searchBlock() {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            for(BlockchainUIController.BlockView block : snapshot.blocks) {
                if(String.valueOf(block.index).equals(query) || block.hash.toLowerCase(Locale.ROOT).contains(query)) {
                    selectBlock(block);
                    return;
                }
            }
            showToast(false,"Traženi blok nije pronađen.");
        }

        void setPhase(double phase) {
            chainLane.setPhase(phase);
        }
    }

    private class WalletPage extends JPanel {
        private final JTextField search = new PromptField("Traži label ili adresu");
        private final JComboBox<String> sort = new JComboBox<>(new String[]{"Balance: najveći","Balance: najmanji","Adresa A-Z","Tip nodea"});
        private final WalletBubbleView bubbles = new WalletBubbleView();
        private final JPanel leaderboard = verticalPanel();
        private List<BlockchainUIController.WalletView> source = new ArrayList<>();

        WalletPage() {
            setOpaque(false);
            setLayout(new BorderLayout(0,14));
            JPanel toolbar = new JPanel(new BorderLayout(12,0));
            toolbar.setOpaque(false);
            search.setToolTipText("Traži label ili adresu");
            ModernTheme.styleInput(search);
            ModernTheme.styleInput(sort);
            search.getDocument().addDocumentListener(documentListener(this::applyFilter));
            sort.addActionListener(event -> applyFilter());
            JPanel filters = new JPanel(new BorderLayout(10,0));
            filters.setOpaque(false);
            filters.add(search,BorderLayout.CENTER);
            filters.add(sort,BorderLayout.EAST);
            toolbar.add(filters,BorderLayout.CENTER);
            ModernTheme.GradientButton addWallet = new ModernTheme.GradientButton("Novi wallet / node");
            addWallet.addActionListener(event -> showCreateNodeDialog());
            toolbar.add(addWallet,BorderLayout.EAST);
            add(toolbar,BorderLayout.NORTH);

            JPanel content = verticalPanel();
            ModernTheme.SmoothScrollPane bubbleScroll = new ModernTheme.SmoothScrollPane(bubbles);
            bubbleScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            content.add(sectionCard("Wallet bubble map","Hover otkriva adresu; log skala naglašava razliku balansa",bubbleScroll,350));
            content.add(Box.createVerticalStrut(14));
            ModernTheme.SmoothScrollPane listScroll = new ModernTheme.SmoothScrollPane(leaderboard);
            content.add(sectionCard("Sortirane adrese","Točan balance i uloga uz svaki wallet",listScroll,380));
            ModernTheme.SmoothScrollPane pageScroll = new ModernTheme.SmoothScrollPane(content);
            pageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(pageScroll,BorderLayout.CENTER);
        }

        void update(BlockchainUIController.Snapshot data) {
            source = new ArrayList<>(data.wallets);
            applyFilter();
        }

        void focusWallet(String query) {
            search.setText(query);
            applyFilter();
        }

        private void applyFilter() {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            List<BlockchainUIController.WalletView> filtered = new ArrayList<>();
            for(BlockchainUIController.WalletView wallet : source) {
                if(query.isEmpty() || wallet.label.toLowerCase(Locale.ROOT).contains(query) || wallet.address.toLowerCase(Locale.ROOT).contains(query)) filtered.add(wallet);
            }
            int sortIndex = sort.getSelectedIndex();
            if(sortIndex == 1) filtered.sort(Comparator.comparingLong(wallet -> wallet.balance));
            else if(sortIndex == 2) filtered.sort(Comparator.comparing(wallet -> wallet.address));
            else if(sortIndex == 3) filtered.sort(Comparator.comparing(wallet -> wallet.nodeType == null ? "ZZZ" : wallet.nodeType.name()));
            else filtered.sort(Comparator.comparingLong((BlockchainUIController.WalletView wallet) -> wallet.balance).reversed());
            bubbles.setWallets(filtered);
            rebuildWalletList(filtered);
        }

        private void rebuildWalletList(List<BlockchainUIController.WalletView> wallets) {
            leaderboard.removeAll();
            int rank = 1;
            for(BlockchainUIController.WalletView wallet : wallets) {
                leaderboard.add(walletRow(rank++,wallet));
                leaderboard.add(Box.createVerticalStrut(8));
            }
            leaderboard.add(Box.createVerticalGlue());
            leaderboard.revalidate();
            leaderboard.repaint();
        }

        void setPhase(double phase) {
            bubbles.setPhase(phase);
        }
    }

    private class TransactionsPage extends JPanel {
        private final JComboBox<WalletChoice> sender = new JComboBox<>();
        private final JComboBox<WalletChoice> receiver = new JComboBox<>();
        private final JTextField amount = new PromptField("0.00");
        private final JLabel fee = label("Fee  —",Font.PLAIN,12,ModernTheme.MUTED);
        private final JLabel total = label("Ukupno  —",Font.BOLD,13,ModernTheme.TEXT);
        private final JPanel transactionList = verticalPanel();
        private String walletFingerprint = "";

        TransactionsPage() {
            setOpaque(false);
            setLayout(new BorderLayout());
            JPanel content = verticalPanel();
            content.add(createTransactionComposer());
            content.add(Box.createVerticalStrut(14));
            ModernTheme.SmoothScrollPane txScroll = new ModernTheme.SmoothScrollPane(transactionList);
            content.add(sectionCard("Transaction stream","Pending, system reward i potvrđene transakcije",txScroll,440));
            ModernTheme.SmoothScrollPane pageScroll = new ModernTheme.SmoothScrollPane(content);
            pageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(pageScroll,BorderLayout.CENTER);
        }

        private ModernTheme.RoundedPanel createTransactionComposer() {
            ModernTheme.RoundedPanel card = new ModernTheme.RoundedPanel(18,ModernTheme.CARD,ModernTheme.BORDER);
            card.setLayout(new BorderLayout(0,16));
            card.setBorder(ModernTheme.padding(20,20,20,20));
            card.add(sectionHeading("Nova transakcija","Wallet potpisuje, a blockchain radi state-aware provjeru"),BorderLayout.NORTH);

            JPanel form = new JPanel(new GridLayout(1,3,12,0));
            form.setOpaque(false);
            form.add(labeledControl("SENDER",sender));
            form.add(labeledControl("RECEIVER",receiver));
            form.add(labeledControl("AMOUNT · MATHOS",amount));
            ModernTheme.styleInput(sender);
            ModernTheme.styleInput(receiver);
            ModernTheme.styleInput(amount);
            amount.getDocument().addDocumentListener(documentListener(this::updateFee));
            card.add(form,BorderLayout.CENTER);

            JPanel actions = new JPanel(new BorderLayout());
            actions.setOpaque(false);
            JPanel totals = new JPanel(new FlowLayout(FlowLayout.LEFT,18,0));
            totals.setOpaque(false);
            totals.add(fee);
            totals.add(total);
            actions.add(totals,BorderLayout.WEST);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
            buttons.setOpaque(false);
            JButton race = outlineButton("Pokreni mining race");
            race.addActionListener(event -> runAction(controller::startMiningRace));
            ModernTheme.GradientButton send = new ModernTheme.GradientButton("Potpiši i pošalji");
            send.addActionListener(event -> sendTransaction());
            buttons.add(race);
            buttons.add(send);
            actions.add(buttons,BorderLayout.EAST);
            card.add(actions,BorderLayout.SOUTH);
            fixedHeight(card,215);
            return card;
        }

        void update(BlockchainUIController.Snapshot data) {
            StringBuilder fingerprint = new StringBuilder();
            for(BlockchainUIController.WalletView wallet : data.wallets) fingerprint.append(wallet.address);
            if(!fingerprint.toString().equals(walletFingerprint)) {
                walletFingerprint = fingerprint.toString();
                Object oldSender = sender.getSelectedItem();
                Object oldReceiver = receiver.getSelectedItem();
                sender.removeAllItems();
                receiver.removeAllItems();
                for(BlockchainUIController.WalletView wallet : data.wallets) {
                    WalletChoice choice = new WalletChoice(wallet);
                    sender.addItem(choice);
                    receiver.addItem(choice);
                }
                restoreChoice(sender,oldSender);
                restoreChoice(receiver,oldReceiver);
                if(receiver.getItemCount() > 1 && receiver.getSelectedIndex() == sender.getSelectedIndex()) receiver.setSelectedIndex(1);
            }
            rebuildTransactions(data.transactions);
        }

        private void sendTransaction() {
            WalletChoice from = (WalletChoice) sender.getSelectedItem();
            WalletChoice to = (WalletChoice) receiver.getSelectedItem();
            if(from == null || to == null) {
                showToast(false,"Odaberi sender i receiver wallet.");
                return;
            }
            runAction(() -> controller.sendTransaction(from.wallet.address,to.wallet.address,amount.getText()));
        }

        private void updateFee() {
            try {
                long value = Money.fromCoins(amount.getText());
                long feeValue = ConsensusRules.calculateFee(value);
                fee.setText("Fee  " + Money.format(feeValue) + " MATHOS");
                total.setText("Ukupno  " + Money.format(Math.addExact(value,feeValue)) + " MATHOS");
            } catch(Exception e) {
                fee.setText("Fee  —");
                total.setText("Ukupno  —");
            }
        }

        private void rebuildTransactions(List<BlockchainUIController.TransactionView> values) {
            transactionList.removeAll();
            List<BlockchainUIController.TransactionView> reversed = new ArrayList<>(values);
            java.util.Collections.reverse(reversed);
            for(BlockchainUIController.TransactionView tx : reversed) {
                transactionList.add(transactionRow(tx));
                transactionList.add(Box.createVerticalStrut(8));
            }
            transactionList.add(Box.createVerticalGlue());
            transactionList.revalidate();
            transactionList.repaint();
        }
    }

    private class NodesPage extends JPanel {
        private final MiningRaceView mining = new MiningRaceView();
        private final JPanel nodeGrid = new JPanel();
        private final JSpinner difficulty = new JSpinner(new SpinnerNumberModel(4,1,6,1));
        private int lastDifficulty = -1;

        NodesPage() {
            setOpaque(false);
            setLayout(new BorderLayout());
            JPanel content = verticalPanel();
            content.add(createNodeToolbar());
            content.add(Box.createVerticalStrut(14));
            content.add(sectionCard("Mining arena","Idle stanje, spreman kandidat i aktivna utrka sada su jasno odvojeni",mining,300));
            content.add(Box.createVerticalStrut(14));
            nodeGrid.setLayout(new BoxLayout(nodeGrid,BoxLayout.Y_AXIS));
            nodeGrid.setOpaque(false);
            ModernTheme.SmoothScrollPane nodeScroll = new ModernTheme.SmoothScrollPane(nodeGrid);
            nodeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            content.add(sectionCard("Validator nodes","MINER, FULL i LIGHT uloge s jasnim lokalnim statusom",nodeScroll,360));
            ModernTheme.SmoothScrollPane scroll = new ModernTheme.SmoothScrollPane(content);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scroll,BorderLayout.CENTER);
        }

        private ModernTheme.RoundedPanel createNodeToolbar() {
            ModernTheme.RoundedPanel toolbar = new ModernTheme.RoundedPanel(18,ModernTheme.CARD,ModernTheme.BORDER);
            toolbar.setLayout(new BorderLayout(14,0));
            toolbar.setBorder(ModernTheme.padding(16,18,16,18));
            JPanel info = sectionHeading("Kontrola mreže","Difficulty 1–6; veće vrijednosti mogu znatno produljiti demo");
            toolbar.add(info,BorderLayout.WEST);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
            controls.setOpaque(false);
            styleSpinner(difficulty);
            JButton apply = outlineButton("Primijeni difficulty");
            apply.addActionListener(event -> runAction(() -> controller.setDifficulty((Integer) difficulty.getValue())));
            JButton validate = outlineButton("Validate chain");
            validate.addActionListener(event -> controller.validateChainAsync().whenComplete((valid,error) -> SwingUtilities.invokeLater(() -> {
                showToast(error == null && valid,error == null ? "Chain je " + (valid ? "valjan." : "nevaljan.") : "Provjera nije završila.");
                refreshSnapshot();
            })));
            ModernTheme.GradientButton mine = new ModernTheme.GradientButton("Start mining race");
            mine.addActionListener(event -> runAction(controller::startMiningRace));
            controls.add(difficulty);
            controls.add(apply);
            controls.add(validate);
            controls.add(mine);
            toolbar.add(controls,BorderLayout.EAST);
            fixedHeight(toolbar,88);
            return toolbar;
        }

        void update(BlockchainUIController.Snapshot data) {
            if(lastDifficulty != data.difficulty) {
                lastDifficulty = data.difficulty;
                difficulty.setValue(data.difficulty);
            }
            mining.update(data);
            rebuildNodes(data.nodes);
        }

        private void rebuildNodes(List<BlockchainUIController.NodeView> values) {
            nodeGrid.removeAll();
            for(int i = 0; i < values.size(); i += 2) {
                JPanel row = new JPanel(new GridLayout(1,2,12,0));
                row.setOpaque(false);
                row.add(nodeCard(values.get(i)));
                if(i + 1 < values.size()) row.add(nodeCard(values.get(i + 1)));
                else row.add(Box.createHorizontalGlue());
                fixedHeight(row,78);
                nodeGrid.add(row);
                nodeGrid.add(Box.createVerticalStrut(10));
            }
            nodeGrid.add(Box.createVerticalGlue());
            nodeGrid.revalidate();
            nodeGrid.repaint();
        }

        void setPhase(double phase) {
            mining.setPhase(phase);
        }
    }

    private ModernTheme.RoundedPanel walletRow(int rank, BlockchainUIController.WalletView wallet) {
        ModernTheme.RoundedPanel row = new ModernTheme.RoundedPanel(14,ModernTheme.RAISED,ModernTheme.withAlpha(ModernTheme.BORDER,150));
        row.setLayout(new BorderLayout(14,0));
        row.setBorder(ModernTheme.padding(11,14,11,14));
        JLabel number = label(String.format("%02d",rank),Font.BOLD,13,rank <= 3 ? ModernTheme.PURPLE : ModernTheme.MUTED);
        number.setPreferredSize(new Dimension(30,28));
        row.add(number,BorderLayout.WEST);

        JPanel identity = verticalPanel();
        identity.add(label(wallet.label,Font.BOLD,14,ModernTheme.TEXT));
        JLabel address = label(ModernTheme.shortHash(wallet.address,9),Font.PLAIN,11,ModernTheme.MUTED);
        address.setFont(new Font("Monospaced",Font.PLAIN,11));
        address.setToolTipText(wallet.address);
        identity.add(address);
        row.add(identity,BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        right.setOpaque(false);
        if(wallet.nodeType != null) right.add(new ModernTheme.PillLabel(wallet.nodeType.name(),nodeColor(wallet.nodeType)));
        right.add(label(wallet.balanceText + " MATHOS",Font.BOLD,14,ModernTheme.CYAN));
        JButton copy = tinyButton("Copy");
        copy.addActionListener(event -> copyText(wallet.address,"Adresa je kopirana."));
        right.add(copy);
        row.add(right,BorderLayout.EAST);
        fixedHeight(row,62);
        return row;
    }

    private ModernTheme.RoundedPanel transactionRow(BlockchainUIController.TransactionView tx) {
        ModernTheme.RoundedPanel row = new ModernTheme.RoundedPanel(14,ModernTheme.RAISED,ModernTheme.withAlpha(ModernTheme.BORDER,140));
        row.setLayout(new BorderLayout(14,0));
        row.setBorder(ModernTheme.padding(11,14,11,14));

        JPanel route = verticalPanel();
        String from = tx.system ? "System reward" : tx.senderLabel;
        route.add(label(from + "  →  " + tx.receiverLabel,Font.BOLD,14,ModernTheme.TEXT));
        JLabel hash = label(ModernTheme.shortHash(tx.hash,10),Font.PLAIN,11,ModernTheme.MUTED);
        hash.setFont(new Font("Monospaced",Font.PLAIN,11));
        hash.setToolTipText(tx.hash);
        route.add(hash);
        row.add(route,BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        right.setOpaque(false);
        Color tone = tx.status.contains("PENDING") ? ModernTheme.WARNING : tx.system ? ModernTheme.PURPLE : ModernTheme.SUCCESS;
        right.add(new ModernTheme.PillLabel(tx.status,tone));
        right.add(label(tx.amountText + " MATHOS",Font.BOLD,14,ModernTheme.CYAN));
        if(tx.blockIndex >= 0) right.add(label("Block #" + tx.blockIndex,Font.BOLD,11,ModernTheme.MUTED));
        row.add(right,BorderLayout.EAST);
        fixedHeight(row,64);
        return row;
    }

    private ModernTheme.RoundedPanel nodeCard(BlockchainUIController.NodeView node) {
        ModernTheme.RoundedPanel card = new ModernTheme.RoundedPanel(16,ModernTheme.RAISED,node.mining ? ModernTheme.PURPLE : ModernTheme.BORDER);
        card.setLayout(new BorderLayout(12,0));
        card.setBorder(ModernTheme.padding(15,16,15,16));

        JPanel identity = verticalPanel();
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        title.setOpaque(false);
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        title.add(label(node.label,Font.BOLD,15,ModernTheme.TEXT));
        title.add(new ModernTheme.PillLabel(node.type.name(),nodeColor(node.type)));
        identity.add(title);
        JLabel address = label(ModernTheme.shortHash(node.address,8),Font.PLAIN,11,ModernTheme.MUTED);
        address.setFont(new Font("Monospaced",Font.PLAIN,11));
        address.setToolTipText(node.address);
        address.setAlignmentX(LEFT_ALIGNMENT);
        identity.add(address);
        card.add(identity,BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT,9,0));
        controls.setOpaque(false);
        String state = node.mining ? "MINING" : node.running ? "RUNNING" : "READY";
        controls.add(new ModernTheme.PillLabel(state,node.mining ? ModernTheme.PURPLE : node.running ? ModernTheme.SUCCESS : ModernTheme.MUTED));
        JButton action = tinyButton(node.running ? "Stop" : "Start");
        action.addActionListener(event -> runAction(() -> node.running ? controller.stopNode(node.address) : controller.startNode(node.address)));
        controls.add(action);
        card.add(controls,BorderLayout.EAST);
        fixedHeight(card,78);
        return card;
    }

    private void showCreateNodeDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner,"Novi wallet i node",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        ModernTheme.RoundedPanel surface = new ModernTheme.RoundedPanel(22,ModernTheme.CARD,ModernTheme.PURPLE);
        surface.setLayout(new BorderLayout(0,18));
        surface.setBorder(ModernTheme.padding(24,24,22,24));
        surface.add(sectionHeading("Kreiraj wallet","Odaberi ulogu; novi wallet nakon genesis faze počinje s 0 MATHOS"),BorderLayout.NORTH);

        JComboBox<Computer.NodeType> type = new JComboBox<>(Computer.NodeType.values());
        JTextField initial = new JTextField("0");
        ModernTheme.styleInput(type);
        ModernTheme.styleInput(initial);
        boolean fundingAllowed = snapshot.blocks.size() == 1;
        initial.setEnabled(fundingAllowed);
        if(!fundingAllowed) initial.setToolTipText("Početni balance je dopušten samo prije prvog izrudarenog bloka.");

        JPanel form = new JPanel(new GridLayout(2,1,0,12));
        form.setOpaque(false);
        form.add(labeledControl("NODE TYPE",type));
        form.add(labeledControl("INITIAL BALANCE · MATHOS",initial));
        surface.add(form,BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        buttons.setOpaque(false);
        JButton cancel = outlineButton("Odustani");
        cancel.addActionListener(event -> dialog.dispose());
        ModernTheme.GradientButton create = new ModernTheme.GradientButton("Kreiraj node");
        create.addActionListener(event -> {
            BlockchainUIController.ActionResult result = controller.createWallet((Computer.NodeType) type.getSelectedItem(),initial.getText());
            showResult(result);
            if(result.success) {
                dialog.dispose();
                refreshSnapshot();
            }
        });
        buttons.add(cancel);
        buttons.add(create);
        surface.add(buttons,BorderLayout.SOUTH);
        dialog.setContentPane(surface);
        dialog.setSize(470,340);
        dialog.setLocationRelativeTo(this);
        dialog.setBackground(new Color(0,0,0,0));
        dialog.setVisible(true);
    }

    private void showLoginDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner,"Mathos Coin login",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        ModernTheme.RoundedPanel surface = new ModernTheme.RoundedPanel(22,ModernTheme.CARD,ModernTheme.BLUE);
        surface.setLayout(new BorderLayout(0,18));
        surface.setBorder(ModernTheme.padding(24,24,22,24));
        surface.add(sectionHeading("Login u node","Odaberi demo račun ili ručno unesi oba hasha; ostatak dashboarda ostaje dostupan"),BorderLayout.NORTH);

        List<BlockchainUIController.LoginView> options = controller.loginOptions();
        JComboBox<BlockchainUIController.LoginView> account = new JComboBox<>(options.toArray(new BlockchainUIController.LoginView[0]));
        JTextField publicHash = new JTextField();
        JTextField privateHash = new JTextField();
        ModernTheme.styleInput(account);
        ModernTheme.styleInput(publicHash);
        ModernTheme.styleInput(privateHash);

        Runnable fillHashes = () -> {
            BlockchainUIController.LoginView selected = (BlockchainUIController.LoginView) account.getSelectedItem();
            if(selected == null) return;
            publicHash.setText(selected.publicHash);
            privateHash.setText(selected.privateHash);
        };
        account.addActionListener(event -> fillHashes.run());
        fillHashes.run();

        JPanel form = new JPanel(new GridLayout(3,1,0,12));
        form.setOpaque(false);
        form.add(labeledControl("DEMO ACCOUNT",account));
        form.add(labeledControl("PUBLIC KEY HASH",publicHash));
        form.add(labeledControl("PRIVATE KEY HASH",privateHash));
        surface.add(form,BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(10,0));
        footer.setOpaque(false);
        footer.add(label("Login uspije samo kada oba hasha pripadaju istom registriranom walletu.",Font.PLAIN,10,ModernTheme.MUTED),BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,10));
        buttons.setOpaque(false);
        BlockchainUIController.LoginView current = controller.currentLogin();
        if(current != null) {
            JButton logout = outlineButton("Logout " + compact(current.label,10));
            logout.addActionListener(event -> {
                showResult(controller.logout());
                updateLoginState();
                dialog.dispose();
            });
            buttons.add(logout);
        }
        JButton cancel = outlineButton("Odustani");
        cancel.addActionListener(event -> dialog.dispose());
        ModernTheme.GradientButton login = new ModernTheme.GradientButton("Login");
        login.addActionListener(event -> {
            BlockchainUIController.ActionResult result = controller.login(publicHash.getText(),privateHash.getText());
            showResult(result);
            if(result.success) {
                updateLoginState();
                refreshSnapshot();
                dialog.dispose();
            }
        });
        buttons.add(cancel);
        buttons.add(login);
        footer.add(buttons,BorderLayout.SOUTH);
        surface.add(footer,BorderLayout.SOUTH);

        dialog.setContentPane(surface);
        dialog.setSize(620,500);
        dialog.setLocationRelativeTo(this);
        dialog.setBackground(new Color(0,0,0,0));
        dialog.setVisible(true);
    }

    private class MetricCard extends ModernTheme.RoundedPanel {
        private final JLabel value = label("0",Font.BOLD,25,ModernTheme.TEXT);
        private final JLabel detail = label("",Font.PLAIN,11,ModernTheme.MUTED);
        private final Color tone;

        MetricCard(String title, String valueText, Color tone) {
            super(17,ModernTheme.CARD,ModernTheme.BORDER);
            this.tone = tone;
            setLayout(new BorderLayout());
            setBorder(ModernTheme.padding(15,17,13,17));
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(label(title,Font.BOLD,10,ModernTheme.MUTED),BorderLayout.WEST);
            JLabel dot = label("●",Font.BOLD,12,tone);
            top.add(dot,BorderLayout.EAST);
            add(top,BorderLayout.NORTH);
            this.value.setText(valueText);
            add(this.value,BorderLayout.CENTER);
            add(detail,BorderLayout.SOUTH);
        }

        void setValue(String value, String detail) {
            this.value.setText(value);
            this.detail.setText(detail);
            setOutline(ModernTheme.withAlpha(tone,95));
        }
    }

    private static class BrandMark extends JComponent {
        BrandMark() {
            setPreferredSize(new Dimension(42,42));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = quality(graphics);
            g.setPaint(new GradientPaint(2,2,ModernTheme.PURPLE,40,40,ModernTheme.BLUE));
            g.fillRoundRect(1,1,40,40,14,14);
            g.setColor(ModernTheme.CANVAS);
            g.setStroke(new BasicStroke(2.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.drawLine(12,15,21,10);
            g.drawLine(21,10,31,16);
            g.drawLine(12,15,12,27);
            g.drawLine(12,27,22,32);
            g.drawLine(22,32,31,26);
            g.drawLine(31,16,31,26);
            g.dispose();
        }
    }

    private class ChainLane extends JComponent {
        private static final int CARD_WIDTH = 218;
        private static final int CARD_HEIGHT = 146;
        private static final int GAP = 62;
        private List<BlockchainUIController.BlockView> blocks = new ArrayList<>();
        private final Consumer<BlockchainUIController.BlockView> selection;
        private int selectedIndex = -1;
        private int hoverIndex = -1;
        private double phase;

        ChainLane(Consumer<BlockchainUIController.BlockView> selection) {
            this.selection = selection;
            setOpaque(false);
            setPreferredSize(new Dimension(760,185));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    int old = hoverIndex;
                    hoverIndex = indexAt(event.getX(),event.getY());
                    if(old != hoverIndex) repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent event) {
                    hoverIndex = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    int index = indexAt(event.getX(),event.getY());
                    if(index >= 0 && index < blocks.size()) {
                        selectedIndex = blocks.get(index).index;
                        selection.accept(blocks.get(index));
                        repaint();
                    }
                }
            });
        }

        void setBlocks(List<BlockchainUIController.BlockView> values) {
            blocks = new ArrayList<>(values);
            int width = Math.max(760,30 + blocks.size() * (CARD_WIDTH + GAP));
            setPreferredSize(new Dimension(width,185));
            revalidate();
            repaint();
        }

        void setSelectedIndex(int index) {
            selectedIndex = index;
            repaint();
        }

        void setPhase(double phase) {
            this.phase = phase;
            repaint();
        }

        private int indexAt(int x, int y) {
            for(int i = 0; i < blocks.size(); i++) {
                int cardX = 24 + i * (CARD_WIDTH + GAP);
                if(x >= cardX && x <= cardX + CARD_WIDTH && y >= 18 && y <= 18 + CARD_HEIGHT) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = quality(graphics);
            for(int i = 0; i < blocks.size(); i++) {
                int x = 24 + i * (CARD_WIDTH + GAP);
                int y = 18;
                if(i < blocks.size() - 1) drawConnector(g,x + CARD_WIDTH,y + CARD_HEIGHT / 2,GAP);
                drawBlock(g,blocks.get(i),i,x,y);
            }
            g.dispose();
        }

        private void drawConnector(Graphics2D g, int x, int y, int width) {
            g.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.setPaint(new GradientPaint(x,y,ModernTheme.withAlpha(ModernTheme.PURPLE,130),x + width,y,ModernTheme.withAlpha(ModernTheme.BLUE,160)));
            g.drawLine(x + 8,y,x + width - 13,y);
            Polygon arrow = new Polygon(new int[]{x + width - 15,x + width - 23,x + width - 23},new int[]{y,y - 5,y + 5},3);
            g.setColor(ModernTheme.BLUE);
            g.fill(arrow);
        }

        private void drawBlock(Graphics2D g, BlockchainUIController.BlockView block, int listIndex, int x, int y) {
            boolean newest = listIndex == blocks.size() - 1;
            boolean selected = block.index == selectedIndex;
            boolean hovered = listIndex == hoverIndex;
            float pulse = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
            if(newest && motionEnabled) {
                g.setColor(ModernTheme.withAlpha(ModernTheme.PURPLE,Math.round(18 + pulse * 24)));
                g.fillRoundRect(x - 7,y - 7,CARD_WIDTH + 14,CARD_HEIGHT + 14,23,23);
            }
            g.setColor(hovered ? ModernTheme.RAISED.brighter() : ModernTheme.RAISED);
            g.fillRoundRect(x,y,CARD_WIDTH,CARD_HEIGHT,18,18);
            g.setStroke(new BasicStroke(selected || newest ? 2f : 1f));
            g.setPaint(selected || newest ? new GradientPaint(x,y,ModernTheme.PURPLE,x + CARD_WIDTH,y + CARD_HEIGHT,ModernTheme.BLUE) : ModernTheme.BORDER);
            g.drawRoundRect(x,y,CARD_WIDTH,CARD_HEIGHT,18,18);

            g.setFont(ModernTheme.font(Font.BOLD,11));
            g.setColor(newest ? ModernTheme.CYAN : ModernTheme.MUTED);
            g.drawString(block.index == 0 ? "GENESIS" : "BLOCK #" + block.index,x + 16,y + 23);
            g.setFont(ModernTheme.font(Font.BOLD,17));
            g.setColor(ModernTheme.TEXT);
            g.drawString(block.index == 0 ? "Network origin" : block.transactions.size() + " transactions",x + 16,y + 50);
            g.setFont(new Font("Monospaced",Font.PLAIN,11));
            g.setColor(ModernTheme.BLUE);
            g.drawString(ModernTheme.shortHash(block.hash,8),x + 16,y + 74);
            g.setFont(ModernTheme.font(Font.PLAIN,11));
            g.setColor(ModernTheme.MUTED);
            g.drawString(block.index == 0 ? "Deterministic start" : "Miner  " + compact(block.minerLabel,18),x + 16,y + 101);
            g.drawString("Nonce  " + block.nonce,x + 16,y + 121);
            g.drawString(block.timestamp == 0 ? "Timestamp  0" : formatDate(block.timestamp),x + 16,y + 138);
        }
    }

    private class WalletBubbleView extends JComponent {
        private List<BlockchainUIController.WalletView> wallets = new ArrayList<>();
        private List<Bubble> bubbles = new ArrayList<>();
        private int hovered = -1;
        private double phase;

        WalletBubbleView() {
            setOpaque(false);
            setPreferredSize(new Dimension(700,285));
            ToolTipManager.sharedInstance().registerComponent(this);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    updateBubbleSize();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    int old = hovered;
                    hovered = bubbleAt(event.getPoint());
                    if(old != hovered) repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = -1;
                    repaint();
                }
            });
        }

        void setWallets(List<BlockchainUIController.WalletView> values) {
            wallets = new ArrayList<>(values);
            updateBubbleSize();
            repaint();
        }

        private void updateBubbleSize() {
            int width = Math.max(520,getWidth());
            int columns = Math.max(3,width / 180);
            int rows = Math.max(1,(int) Math.ceil(wallets.size() / (double) columns));
            int height = Math.max(260,rows * 180);
            if(getPreferredSize().height != height) {
                setPreferredSize(new Dimension(700,height));
                revalidate();
            }
        }

        void setPhase(double phase) {
            this.phase = phase;
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = bubbleAt(event.getPoint());
            if(index < 0) return null;
            BlockchainUIController.WalletView wallet = wallets.get(index);
            return wallet.label + " · " + wallet.address + " · " + wallet.balanceText + " MATHOS";
        }

        private int bubbleAt(Point point) {
            for(int i = bubbles.size() - 1; i >= 0; i--) if(bubbles.get(i).shape.contains(point)) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = quality(graphics);
            bubbles = layoutBubbles();
            for(int i = 0; i < bubbles.size(); i++) drawBubble(g,bubbles.get(i),wallets.get(i),i == hovered,i);
            if(wallets.isEmpty()) drawEmpty(g,"Nema walleta za ovaj filter.");
            g.dispose();
        }

        private List<Bubble> layoutBubbles() {
            List<Bubble> result = new ArrayList<>();
            int width = Math.max(520,getWidth());
            int columns = Math.max(3,width / 180);
            double minLog = Double.POSITIVE_INFINITY;
            double maxLog = Double.NEGATIVE_INFINITY;
            for(BlockchainUIController.WalletView wallet : wallets) {
                double value = Math.log1p(Math.max(0L,wallet.balance));
                minLog = Math.min(minLog,value);
                maxLog = Math.max(maxLog,value);
            }
            int cellWidth = width / columns;
            for(int i = 0; i < wallets.size(); i++) {
                BlockchainUIController.WalletView wallet = wallets.get(i);
                double walletLog = Math.log1p(Math.max(0L,wallet.balance));
                double ratio = maxLog <= minLog ? 0.65 : (walletLog - minLog) / (maxLog - minLog);
                int radius = 28 + (int) Math.round(ratio * 50);
                int column = i % columns;
                int row = i / columns;
                int centerX = column * cellWidth + cellWidth / 2;
                int centerY = row * 180 + 90;
                result.add(new Bubble(new Ellipse2D.Double(centerX - radius,centerY - radius,radius * 2.0,radius * 2.0),centerX,centerY,radius));
            }
            return result;
        }

        private void drawBubble(Graphics2D g, Bubble bubble, BlockchainUIController.WalletView wallet, boolean hover, int index) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2 + index * 0.7));
            Color first = index % 2 == 0 ? ModernTheme.PURPLE : ModernTheme.BLUE;
            Color second = index % 2 == 0 ? ModernTheme.BLUE : ModernTheme.CYAN;
            if(hover || (motionEnabled && index == 0)) {
                int glow = hover ? 60 : Math.round(16 + pulse * 20);
                g.setColor(ModernTheme.withAlpha(first,glow));
                g.fill(new Ellipse2D.Double(bubble.centerX - bubble.radius - 7,bubble.centerY - bubble.radius - 7,(bubble.radius + 7) * 2.0,(bubble.radius + 7) * 2.0));
            }
            g.setPaint(new RadialGradientPaint(new Point(bubble.centerX - bubble.radius / 3,bubble.centerY - bubble.radius / 3),bubble.radius * 1.35f,new float[]{0f,1f},new Color[]{ModernTheme.withAlpha(first,210),ModernTheme.withAlpha(second,105)}));
            g.fill(bubble.shape);
            g.setColor(ModernTheme.withAlpha(first,190));
            g.setStroke(new BasicStroke(hover ? 2f : 1f));
            g.draw(bubble.shape);
            drawCentered(g,compact(wallet.label,14),bubble.centerX,bubble.centerY - 8,ModernTheme.font(Font.BOLD,12),ModernTheme.TEXT);
            String balance = bubbleAmount(wallet.balance) + (bubble.radius < 42 ? "" : " MATHOS");
            drawCentered(g,balance,bubble.centerX,bubble.centerY + 12,ModernTheme.font(Font.BOLD,11),ModernTheme.CYAN);
            if(wallet.nodeType != null) drawCentered(g,wallet.nodeType.name(),bubble.centerX,bubble.centerY + 30,ModernTheme.font(Font.BOLD,9),ModernTheme.MUTED);
        }
    }

    private static class Bubble {
        final Ellipse2D shape;
        final int centerX;
        final int centerY;
        final int radius;

        Bubble(Ellipse2D shape, int centerX, int centerY, int radius) {
            this.shape = shape;
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
        }
    }

    private class MiningRaceView extends JComponent {
        private BlockchainUIController.Snapshot data;
        private double phase;

        MiningRaceView() {
            setOpaque(false);
            setPreferredSize(new Dimension(560,275));
        }

        void update(BlockchainUIController.Snapshot data) {
            this.data = data;
            repaint();
        }

        void setPhase(double phase) {
            this.phase = phase;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = quality(graphics);
            if(data == null) {
                drawEmpty(g,"Čekam snapshot mreže.");
                g.dispose();
                return;
            }
            List<BlockchainUIController.NodeView> miners = data.nodes.stream().filter(node -> node.type == Computer.NodeType.MINER).toList();
            if(miners.isEmpty()) {
                drawMiningIdle(g,"Nema MINER nodeova","Kreiraj MINER node prije pokretanja utrke.",0);
                g.dispose();
                return;
            }
            if(!data.mining && !data.hasPendingTransactions) {
                long automatic = miners.stream().filter(miner -> miner.running).count();
                drawMiningIdle(g,"Nema aktivnog mining rounda","Dodaj transakciju u mempool pa pokreni mining race.",automatic);
                g.dispose();
                return;
            }
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int blockW = 166;
            int blockH = 104;
            float pulse = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));

            for(int i = 0; i < miners.size(); i++) {
                BlockchainUIController.NodeView miner = miners.get(i);
                double angle = miners.size() == 1 ? Math.PI : i * Math.PI * 2 / miners.size();
                int x = centerX + (int) Math.round(Math.cos(angle) * Math.min(220,getWidth() * 0.34));
                int y = centerY + (int) Math.round(Math.sin(angle) * 90);
                boolean active = miner.mining || data.activeMiners.contains(miner.address);
                g.setStroke(new BasicStroke(active ? 2.2f : 1.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.setColor(active ? ModernTheme.withAlpha(ModernTheme.CYAN,185) : ModernTheme.withAlpha(ModernTheme.BORDER,125));
                g.drawLine(x,y,centerX,centerY);
                if(active && motionEnabled) {
                    int glow = Math.round(12 + pulse * 24);
                    g.setColor(ModernTheme.withAlpha(ModernTheme.PURPLE,glow));
                    g.fillRoundRect(x - 57,y - 34,114,68,18,18);
                }
                g.setColor(active ? ModernTheme.withAlpha(ModernTheme.PURPLE,155) : ModernTheme.RAISED);
                g.fillRoundRect(x - 50,y - 27,100,54,15,15);
                g.setColor(active ? ModernTheme.CYAN : ModernTheme.BORDER);
                g.drawRoundRect(x - 50,y - 27,100,54,15,15);
                drawCentered(g,compact(miner.label,12),x,y - 7,ModernTheme.font(Font.BOLD,11),ModernTheme.TEXT);
                drawCentered(g,active ? "MINING" : "READY",x,y + 11,ModernTheme.font(Font.BOLD,9),active ? ModernTheme.CYAN : ModernTheme.MUTED);
            }

            if(data.mining && motionEnabled) {
                g.setColor(ModernTheme.withAlpha(ModernTheme.BLUE,Math.round(18 + pulse * 28)));
                g.fillRoundRect(centerX - blockW / 2 - 8,centerY - blockH / 2 - 8,blockW + 16,blockH + 16,24,24);
            }
            g.setPaint(new GradientPaint(centerX - blockW / 2,centerY - blockH / 2,ModernTheme.withAlpha(ModernTheme.PURPLE,130),centerX + blockW / 2,centerY + blockH / 2,ModernTheme.withAlpha(ModernTheme.BLUE,120)));
            g.fillRoundRect(centerX - blockW / 2,centerY - blockH / 2,blockW,blockH,19,19);
            g.setColor(data.mining ? ModernTheme.CYAN : ModernTheme.BORDER);
            g.drawRoundRect(centerX - blockW / 2,centerY - blockH / 2,blockW,blockH,19,19);
            drawCentered(g,data.mining ? "AKTIVNA UTRKA" : "KANDIDAT JE SPREMAN",centerX,centerY - 17,ModernTheme.font(Font.BOLD,11),data.mining ? ModernTheme.CYAN : ModernTheme.WARNING);
            drawCentered(g,"Block kandidat #" + (data.chainHeight + 1),centerX,centerY + 7,ModernTheme.font(Font.BOLD,16),ModernTheme.TEXT);
            String bottom = data.activeMiners.isEmpty() ? "Mempool ima transakcije" : data.activeMiners.size() + " minera se natječu";
            drawCentered(g,bottom,centerX,centerY + 30,ModernTheme.font(Font.PLAIN,10),ModernTheme.MUTED);
            g.dispose();
        }

        private void drawMiningIdle(Graphics2D g, String title, String subtitle, long automaticMiners) {
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2 - 10;
            g.setColor(ModernTheme.withAlpha(ModernTheme.BLUE,34));
            g.fillRoundRect(centerX - 42,centerY - 56,84,65,18,18);
            g.setColor(ModernTheme.withAlpha(ModernTheme.PURPLE,75));
            g.fillRoundRect(centerX - 30,centerY - 46,84,65,18,18);
            g.setColor(ModernTheme.RAISED);
            g.fillRoundRect(centerX - 18,centerY - 36,84,65,18,18);
            g.setColor(ModernTheme.BORDER);
            g.drawRoundRect(centerX - 18,centerY - 36,84,65,18,18);
            drawCentered(g,"#" + (data.chainHeight + 1),centerX + 24,centerY - 3,ModernTheme.font(Font.BOLD,18),ModernTheme.MUTED);
            drawCentered(g,title,centerX,centerY + 62,ModernTheme.font(Font.BOLD,15),ModernTheme.TEXT);
            drawCentered(g,subtitle,centerX,centerY + 84,ModernTheme.font(Font.PLAIN,11),ModernTheme.MUTED);
            String status = data.nodes.stream().filter(node -> node.type == Computer.NodeType.MINER).count() + " minera spremna";
            if(automaticMiners > 0) status += " · " + automaticMiners + " auto mode";
            drawCentered(g,status,centerX,centerY + 107,ModernTheme.font(Font.BOLD,10),ModernTheme.BLUE);
        }
    }

    private class ActivityPanel extends JPanel {
        private final JPanel list = verticalPanel();
        private String fingerprint = "";

        ActivityPanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
            ModernTheme.SmoothScrollPane scroll = new ModernTheme.SmoothScrollPane(list);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scroll,BorderLayout.CENTER);
        }

        void update(List<BlockchainUIController.ActivityView> values) {
            String next = values.isEmpty() ? "empty" : values.size() + ":" + values.get(0).timestamp;
            if(next.equals(fingerprint)) return;
            fingerprint = next;
            list.removeAll();
            for(BlockchainUIController.ActivityView event : values) {
                JPanel row = new JPanel(new BorderLayout(12,0));
                row.setOpaque(false);
                JLabel dot = label("●",Font.BOLD,12,toneColor(event.tone));
                row.add(dot,BorderLayout.WEST);
                JPanel text = verticalPanel();
                text.add(label(event.title,Font.BOLD,13,ModernTheme.TEXT));
                text.add(label(event.detail,Font.PLAIN,11,ModernTheme.MUTED));
                row.add(text,BorderLayout.CENTER);
                row.add(label(CLOCK.format(Instant.ofEpochMilli(event.timestamp)),Font.BOLD,10,ModernTheme.MUTED),BorderLayout.EAST);
                row.setBorder(ModernTheme.padding(8,4,8,8));
                list.add(row);
                list.add(new JSeparator());
            }
            list.add(Box.createVerticalGlue());
            list.revalidate();
            list.repaint();
        }
    }

    private class BlockDetailPanel extends ModernTheme.RoundedPanel {
        private final JLabel blockTitle = label("Odaberi blok",Font.BOLD,20,ModernTheme.TEXT);
        private final JLabel subtitle = label("",Font.PLAIN,12,ModernTheme.MUTED);
        private final JPanel fields = new JPanel(new GridLayout(2,3,12,12));
        private final JPanel txList = verticalPanel();

        BlockDetailPanel() {
            super(18,ModernTheme.CARD,ModernTheme.BORDER);
            setLayout(new BorderLayout(0,16));
            setBorder(ModernTheme.padding(20,20,20,20));
            JPanel heading = verticalPanel();
            heading.add(blockTitle);
            heading.add(Box.createVerticalStrut(3));
            heading.add(subtitle);
            add(heading,BorderLayout.NORTH);
            fields.setOpaque(false);
            add(fields,BorderLayout.CENTER);
            ModernTheme.SmoothScrollPane scroll = new ModernTheme.SmoothScrollPane(txList);
            scroll.setPreferredSize(new Dimension(0,220));
            add(scroll,BorderLayout.SOUTH);
            setPreferredSize(new Dimension(900,470));
            setMaximumSize(new Dimension(Integer.MAX_VALUE,520));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        void setBlock(BlockchainUIController.BlockView block) {
            blockTitle.setText(block.index == 0 ? "Genesis block" : "Block #" + block.index);
            subtitle.setText(block.index == 0 ? "Deterministička početna točka mreže" : block.transactions.size() + " transakcija · miner " + block.minerLabel);
            fields.removeAll();
            fields.add(detailField("BLOCK HASH",block.hash,true));
            fields.add(detailField("PREVIOUS HASH",block.previousHash,true));
            fields.add(detailField("MERKLE ROOT",block.merkleRoot,true));
            fields.add(detailField("NONCE",String.valueOf(block.nonce),false));
            fields.add(detailField("TIMESTAMP",block.timestamp == 0 ? "0 · GENESIS" : formatDate(block.timestamp),false));
            fields.add(detailField("TRANSACTIONS",String.valueOf(block.transactions.size()),false));
            txList.removeAll();
            if(block.transactions.isEmpty()) txList.add(label("Genesis nema transakcije.",Font.PLAIN,12,ModernTheme.MUTED));
            for(BlockchainUIController.TransactionView tx : block.transactions) {
                txList.add(transactionRow(tx));
                txList.add(Box.createVerticalStrut(8));
            }
            fields.revalidate();
            fields.repaint();
            txList.revalidate();
            txList.repaint();
        }
    }

    private ModernTheme.RoundedPanel detailField(String name, String value, boolean copyable) {
        ModernTheme.RoundedPanel field = new ModernTheme.RoundedPanel(13,ModernTheme.RAISED,ModernTheme.withAlpha(ModernTheme.BORDER,130));
        field.setLayout(new BorderLayout(8,0));
        field.setBorder(ModernTheme.padding(10,12,10,12));
        JPanel text = verticalPanel();
        text.add(label(name,Font.BOLD,9,ModernTheme.MUTED));
        JLabel content = label(copyable ? ModernTheme.shortHash(value,10) : value,Font.BOLD,12,copyable ? ModernTheme.BLUE : ModernTheme.TEXT);
        if(copyable) {
            content.setFont(new Font("Monospaced",Font.PLAIN,11));
            content.setToolTipText(value);
        }
        text.add(content);
        field.add(text,BorderLayout.CENTER);
        if(copyable) {
            JButton copy = tinyButton("Copy");
            copy.addActionListener(event -> copyText(value,name + " je kopiran."));
            field.add(copy,BorderLayout.EAST);
        }
        return field;
    }

    private ModernTheme.RoundedPanel sectionCard(String title, String subtitle, Component content, int height) {
        ModernTheme.RoundedPanel card = new ModernTheme.RoundedPanel(18,ModernTheme.CARD,ModernTheme.BORDER);
        card.setLayout(new BorderLayout(0,13));
        card.setBorder(ModernTheme.padding(17,18,16,18));
        card.add(sectionHeading(title,subtitle),BorderLayout.NORTH);
        card.add(content,BorderLayout.CENTER);
        fixedHeight(card,height + 72);
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
    }

    private JPanel sectionHeading(String title, String subtitle) {
        JPanel heading = verticalPanel();
        JLabel titleLabel = label(title,Font.BOLD,16,ModernTheme.TEXT);
        JLabel subtitleLabel = label(subtitle,Font.PLAIN,11,ModernTheme.MUTED);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(3));
        heading.add(subtitleLabel);
        return heading;
    }

    private JPanel labeledControl(String title, JComponent control) {
        JPanel panel = verticalPanel();
        JLabel caption = label(title,Font.BOLD,10,ModernTheme.MUTED);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        control.setAlignmentX(LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        panel.add(caption);
        panel.add(Box.createVerticalStrut(7));
        panel.add(control);
        return panel;
    }

    private JButton outlineButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(ModernTheme.TEXT);
        button.setBackground(ModernTheme.RAISED);
        button.setFont(ModernTheme.font(Font.BOLD,12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ModernTheme.BORDER),ModernTheme.padding(10,14,10,14)));
        return button;
    }

    private JButton tinyButton(String text) {
        JButton button = outlineButton(text);
        button.setFont(ModernTheme.font(Font.BOLD,10));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ModernTheme.BORDER),ModernTheme.padding(6,9,6,9)));
        return button;
    }

    private JToggleButton secondaryToggle(String text, boolean selected) {
        JToggleButton button = new JToggleButton(text,selected) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = quality(graphics);
                g.setColor(isSelected() ? ModernTheme.withAlpha(ModernTheme.PURPLE,48) : ModernTheme.RAISED);
                g.fillRoundRect(0,0,getWidth(),getHeight(),11,11);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setForeground(ModernTheme.TEXT);
        button.setBackground(ModernTheme.RAISED);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ModernTheme.BORDER),ModernTheme.padding(8,11,8,11)));
        return button;
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setOpaque(false);
        spinner.setBackground(ModernTheme.RAISED);
        spinner.setBorder(BorderFactory.createLineBorder(ModernTheme.BORDER));
        if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JFormattedTextField field = editor.getTextField();
            field.setForeground(ModernTheme.TEXT);
            field.setBackground(ModernTheme.RAISED);
            field.setCaretColor(ModernTheme.TEXT);
            field.setBorder(ModernTheme.padding(7,9,7,9));
        }
        for(Component child : spinner.getComponents()) {
            child.setBackground(ModernTheme.RAISED);
            child.setForeground(ModernTheme.TEXT);
        }
    }

    private JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(ModernTheme.font(style,size));
        label.setForeground(color);
        return label;
    }

    private static void fixedHeight(JComponent component, int height) {
        component.setPreferredSize(new Dimension(100,height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
        component.setMinimumSize(new Dimension(0,height));
        component.setAlignmentX(LEFT_ALIGNMENT);
    }

    private static Color nodeColor(Computer.NodeType type) {
        if(type == Computer.NodeType.MINER) return ModernTheme.PURPLE;
        if(type == Computer.NodeType.LIGHT) return ModernTheme.CYAN;
        return ModernTheme.BLUE;
    }

    private static Color toneColor(String tone) {
        if("success".equals(tone)) return ModernTheme.SUCCESS;
        if("warning".equals(tone)) return ModernTheme.WARNING;
        if("danger".equals(tone)) return ModernTheme.ERROR;
        return ModernTheme.BLUE;
    }

    private static String compact(String value, int length) {
        if(value == null) return "—";
        return value.length() <= length ? value : value.substring(0,Math.max(1,length - 1)) + "…";
    }

    private static String bubbleAmount(long amount) {
        BigDecimal value = BigDecimal.valueOf(amount,8);
        BigDecimal absolute = value.abs();
        int scale = absolute.compareTo(BigDecimal.valueOf(1000)) >= 0 ? 0 : absolute.compareTo(BigDecimal.valueOf(100)) >= 0 ? 1 : absolute.compareTo(BigDecimal.TEN) >= 0 ? 2 : 3;
        return value.setScale(scale,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String formatDate(long timestamp) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
    }

    private static String safeMessage(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static Graphics2D quality(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void drawCentered(Graphics2D g, String text, int x, int y, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text,x - metrics.stringWidth(text) / 2,y + (metrics.getAscent() - metrics.getDescent()) / 2);
    }

    private void drawEmpty(Graphics2D g, String message) {
        drawCentered(g,message,getWidth() / 2,getHeight() / 2,ModernTheme.font(Font.PLAIN,12),ModernTheme.MUTED);
    }

    private void copyText(String value, String message) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);
        showToast(true,message);
    }

    private static DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        };
    }

    private static void restoreChoice(JComboBox<WalletChoice> combo, Object oldValue) {
        if(!(oldValue instanceof WalletChoice oldChoice)) return;
        for(int i = 0; i < combo.getItemCount(); i++) if(combo.getItemAt(i).wallet.address.equals(oldChoice.wallet.address)) {
            combo.setSelectedIndex(i);
            return;
        }
    }

    private static class WalletChoice {
        final BlockchainUIController.WalletView wallet;

        WalletChoice(BlockchainUIController.WalletView wallet) {
            this.wallet = wallet;
        }

        @Override
        public String toString() {
            return wallet.label + "  ·  " + wallet.balanceText + " MATHOS";
        }
    }

    private static class PromptField extends JTextField {
        private final String prompt;

        PromptField(String prompt) {
            this.prompt = prompt;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if(!getText().isEmpty()) return;
            Graphics2D g = quality(graphics);
            g.setFont(getFont());
            g.setColor(ModernTheme.MUTED);
            Insets insets = getInsets();
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(prompt,insets.left,getHeight() / 2 + (metrics.getAscent() - metrics.getDescent()) / 2);
            g.dispose();
        }
    }
}
