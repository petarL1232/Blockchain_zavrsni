import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ZoomableWalletView extends JPanel {
    private static final double MIN_SCALE = 0.35;
    private static final double MAX_SCALE = 3.5;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private final ArrayList<Bubble> bubbles = new ArrayList<>();
    private double scale = 1.0;
    private double panX;
    private double panY;
    private Point dragStart;
    private double dragPanX;
    private double dragPanY;
    private Bubble selected;
    private Bubble hovered;
    private Runnable selectionChanged;
    private double animationPhase;
    private boolean firstLayout = true;
    private boolean animationEnabled;
    private Timer animationTimer;

    public ZoomableWalletView() {
        setOpaque(false);
        setMinimumSize(new Dimension(420,360));
        setFocusable(true);
        setToolTipText("");
        getAccessibleContext().setAccessibleName("Zoomable wallet universe");
        getAccessibleContext().setAccessibleDescription("Use the mouse wheel or plus and minus keys to zoom, drag or arrow keys to pan, and Home to fit all wallets.");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                dragStart = event.getPoint();
                dragPanX = panX;
                dragPanY = panY;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if(dragStart == null) return;
                panX = dragPanX + event.getX() - dragStart.x;
                panY = dragPanY + event.getY() - dragStart.y;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                setCursor(Cursor.getDefaultCursor());
                if(dragStart != null && dragStart.distance(event.getPoint()) < 5.0) selectAt(event.getPoint());
                dragStart = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                hovered = bubbleAt(event.getPoint());
                if(hovered == null) {
                    setToolTipText("Kotačićem zumiraj · povlačenjem pomiči mapu");
                } else {
                    setToolTipText(hovered.wallet.label + " · " + Money.format(hovered.wallet.balance) + " MATH · " + hovered.wallet.address);
                }
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = null;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if(event.getClickCount() == 2) focusSelected();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                zoomAt(event.getPoint(),Math.pow(1.12,-event.getPreciseWheelRotation()));
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        bindKey("LEFT","panLeft",() -> { panX += 34; repaint(); });
        bindKey("RIGHT","panRight",() -> { panX -= 34; repaint(); });
        bindKey("UP","panUp",() -> { panY += 34; repaint(); });
        bindKey("DOWN","panDown",() -> { panY -= 34; repaint(); });
        bindKey("HOME","fit",this::fitView);
        bindKey("PLUS","zoomIn",this::zoomIn);
        bindKey("EQUALS","zoomInEquals",this::zoomIn);
        bindKey("MINUS","zoomOut",this::zoomOut);
        bindKey("PAGE_DOWN","nextWallet",() -> cycleSelected(1));
        bindKey("PAGE_UP","previousWallet",() -> cycleSelected(-1));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ensureAnimationTimer();
        if(animationEnabled) animationTimer.start();
    }

    @Override
    public void removeNotify() {
        if(animationTimer != null) animationTimer.stop();
        super.removeNotify();
    }

    public void setAnimationEnabled(boolean animationEnabled) {
        this.animationEnabled = animationEnabled;
        ensureAnimationTimer();
        if(animationEnabled && isDisplayable()) animationTimer.start();
        else animationTimer.stop();
    }

    private void ensureAnimationTimer() {
        if(animationTimer != null) return;
        animationTimer = new Timer(30,event -> {
            animationPhase += 0.045;
            repaint();
        });
    }

    private void bindKey(String keyStroke,String name,Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke),name);
        getActionMap().put(name,new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }

    public void setWallets(List<WlanUIController.WalletView> wallets) {
        String selectedAddress = selected == null ? null : selected.wallet.address;
        String hoveredAddress = hovered == null ? null : hovered.wallet.address;
        ArrayList<WlanUIController.WalletView> ordered = new ArrayList<>(wallets);
        ordered.sort(Comparator
                .comparing((WlanUIController.WalletView wallet) -> !wallet.local)
                .thenComparing(wallet -> wallet.address));

        double minimumLog = Double.POSITIVE_INFINITY;
        double maximumLog = Double.NEGATIVE_INFINITY;

        for(WlanUIController.WalletView wallet : ordered) {
            double coins = Math.max(0.0,wallet.balance / (double) Money.UNITS_PER_COIN);
            double log = Math.log10(1.0 + coins);
            minimumLog = Math.min(minimumLog,log);
            maximumLog = Math.max(maximumLog,log);
        }

        bubbles.clear();
        selected = null;
        hovered = null;
        for(int i = 0; i < ordered.size(); i++) {
            WlanUIController.WalletView wallet = ordered.get(i);
            double coins = Math.max(0.0,wallet.balance / (double) Money.UNITS_PER_COIN);
            double log = Math.log10(1.0 + coins);
            double normalized = maximumLog <= minimumLog ? 0.55 : (log - minimumLog) / (maximumLog - minimumLog);
            double radius = 28.0 + Math.pow(normalized,0.72) * 72.0;

            double orbit = i == 0 ? 0.0 : 125.0 + Math.sqrt(i) * 132.0;
            double angle = i * GOLDEN_ANGLE - Math.PI / 2.0;
            Bubble bubble = new Bubble(wallet,Math.cos(angle) * orbit,Math.sin(angle) * orbit,radius);
            bubbles.add(bubble);
            if(wallet.address.equals(selectedAddress)) selected = bubble;
            if(wallet.address.equals(hoveredAddress)) hovered = bubble;
        }

        if(selected == null && !bubbles.isEmpty()) selected = bubbles.get(0);
        if(firstLayout && !bubbles.isEmpty()) {
            firstLayout = false;
            SwingUtilities.invokeLater(this::fitView);
        }
        repaint();
    }

    public void setSelectionChanged(Runnable selectionChanged) {
        this.selectionChanged = selectionChanged;
    }

    public WlanUIController.WalletView getSelectedWallet() {
        return selected == null ? null : selected.wallet;
    }

    public void zoomIn() {
        zoomAt(new Point(getWidth() / 2,getHeight() / 2),1.22);
    }

    public void zoomOut() {
        zoomAt(new Point(getWidth() / 2,getHeight() / 2),1.0 / 1.22);
    }

    public void fitView() {
        if(bubbles.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for(Bubble bubble : bubbles) {
            minX = Math.min(minX,bubble.x - bubble.radius);
            minY = Math.min(minY,bubble.y - bubble.radius);
            maxX = Math.max(maxX,bubble.x + bubble.radius);
            maxY = Math.max(maxY,bubble.y + bubble.radius);
        }

        double contentWidth = Math.max(1.0,maxX - minX);
        double contentHeight = Math.max(1.0,maxY - minY);
        scale = clamp(Math.min((getWidth() - 90.0) / contentWidth,(getHeight() - 90.0) / contentHeight),MIN_SCALE,1.35);
        panX = -((minX + maxX) / 2.0) * scale;
        panY = -((minY + maxY) / 2.0) * scale;
        repaint();
    }

    private void zoomAt(Point point,double factor) {
        double oldScale = scale;
        double newScale = clamp(oldScale * factor,MIN_SCALE,MAX_SCALE);
        if(newScale == oldScale) return;

        double worldX = (point.x - getWidth() / 2.0 - panX) / oldScale;
        double worldY = (point.y - getHeight() / 2.0 - panY) / oldScale;
        scale = newScale;
        panX = point.x - getWidth() / 2.0 - worldX * scale;
        panY = point.y - getHeight() / 2.0 - worldY * scale;
        repaint();
    }

    private void selectAt(Point point) {
        Bubble found = bubbleAt(point);
        if(found == null) return;
        selected = found;
        if(selectionChanged != null) selectionChanged.run();
        repaint();
    }

    private void cycleSelected(int direction) {
        if(bubbles.isEmpty()) return;
        int index = selected == null ? 0 : bubbles.indexOf(selected);
        if(index < 0) index = 0;
        selected = bubbles.get(Math.floorMod(index + direction,bubbles.size()));
        if(selectionChanged != null) selectionChanged.run();
        repaint();
    }

    private void focusSelected() {
        if(selected == null) return;
        scale = Math.max(scale,1.15);
        panX = -selected.x * scale;
        panY = -selected.y * scale;
        repaint();
    }

    private Bubble bubbleAt(Point point) {
        Point2D world = screenToWorld(point);
        for(int i = bubbles.size() - 1; i >= 0; i--) {
            Bubble bubble = bubbles.get(i);
            if(Point2D.distance(world.getX(),world.getY(),bubble.x,bubble.y) <= bubble.radius) return bubble;
        }
        return null;
    }

    private Point2D screenToWorld(Point point) {
        return new Point2D.Double(
                (point.x - getWidth() / 2.0 - panX) / scale,
                (point.y - getHeight() / 2.0 - panY) / scale);
    }

    private double clamp(double value,double minimum,double maximum) {
        return Math.max(minimum,Math.min(maximum,value));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        paintGrid(g);
        g.translate(getWidth() / 2.0 + panX,getHeight() / 2.0 + panY);
        g.scale(scale,scale);

        paintConnections(g);
        for(Bubble bubble : bubbles) paintBubble(g,bubble);
        g.dispose();

        Graphics2D overlay = (Graphics2D) graphics.create();
        overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        overlay.setColor(WlanTheme.alpha(WlanTheme.BACKGROUND,165));
        overlay.fillRoundRect(14,14,150,28,14,14);
        overlay.setFont(WlanTheme.font(Font.BOLD,10));
        overlay.setColor(WlanTheme.TEXT_SOFT);
        overlay.drawString(Math.round(scale * 100) + "%  ·  DRAG / ZOOM",28,32);
        if(hasFocus()) {
            overlay.setColor(WlanTheme.CYAN);
            overlay.setStroke(new BasicStroke(2f));
            overlay.drawRoundRect(1,1,getWidth() - 3,getHeight() - 3,18,18);
        }
        overlay.dispose();
    }

    private void paintGrid(Graphics2D g) {
        g.setColor(WlanTheme.alpha(WlanTheme.PRIMARY_LIGHT,20));
        int spacing = 42;
        int offsetX = (int) ((panX + getWidth() / 2.0) % spacing);
        int offsetY = (int) ((panY + getHeight() / 2.0) % spacing);
        for(int x = offsetX; x < getWidth(); x += spacing) {
            for(int y = offsetY; y < getHeight(); y += spacing) g.fillOval(x,y,2,2);
        }
    }

    private void paintConnections(Graphics2D g) {
        if(bubbles.size() < 2) return;
        Bubble center = bubbles.get(0);
        g.setStroke(new BasicStroke((float) (1.1 / scale)));

        for(int i = 1; i < bubbles.size(); i++) {
            Bubble bubble = bubbles.get(i);
            g.setColor(WlanTheme.alpha(WlanTheme.PRIMARY_LIGHT,55));
            g.drawLine((int) center.x,(int) center.y,(int) bubble.x,(int) bubble.y);

            double progress = (animationPhase + i * 0.19) % 1.0;
            double pulseX = center.x + (bubble.x - center.x) * progress;
            double pulseY = center.y + (bubble.y - center.y) * progress;
            g.setColor(WlanTheme.CYAN);
            double pulseRadius = 3.2 / scale;
            g.fill(new Ellipse2D.Double(pulseX - pulseRadius,pulseY - pulseRadius,pulseRadius * 2,pulseRadius * 2));
        }
    }

    private void paintBubble(Graphics2D g,Bubble bubble) {
        double radius = bubble.radius;
        boolean isSelected = bubble == selected;
        boolean isHovered = bubble == hovered;

        if(bubble.wallet.local) {
            double wave = 8.0 + Math.sin(animationPhase * 3.0) * 3.0;
            g.setColor(WlanTheme.alpha(WlanTheme.CYAN,38));
            g.fill(new Ellipse2D.Double(bubble.x - radius - wave,bubble.y - radius - wave,
                    (radius + wave) * 2,(radius + wave) * 2));
        }

        g.setColor(new Color(0,0,0,75));
        g.fill(new Ellipse2D.Double(bubble.x - radius + 5,bubble.y - radius + 9,radius * 2,radius * 2));

        Color first = bubble.wallet.local ? WlanTheme.PRIMARY_LIGHT : WlanTheme.PURPLE;
        Color second = bubble.wallet.local ? WlanTheme.PRIMARY : new Color(64,55,155);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(bubble.x - radius * .35,bubble.y - radius * .4),
                (float) (radius * 1.45),new float[]{0f,1f},new Color[]{first,second}));
        g.fill(new Ellipse2D.Double(bubble.x - radius,bubble.y - radius,radius * 2,radius * 2));

        g.setStroke(new BasicStroke((float) ((isSelected ? 3.0 : isHovered ? 2.0 : 1.0) / scale)));
        g.setColor(isSelected ? WlanTheme.CYAN : isHovered ? WlanTheme.TEXT_SOFT : WlanTheme.alpha(WlanTheme.TEXT_SOFT,110));
        g.draw(new Ellipse2D.Double(bubble.x - radius,bubble.y - radius,radius * 2,radius * 2));

        float labelSize = (float) Math.max(9.5,Math.min(15.0,radius / 5.1));
        g.setFont(WlanTheme.font(Font.BOLD,labelSize));
        FontMetrics labelMetrics = g.getFontMetrics();
        String label = bubble.wallet.local ? "YOU" : WlanTheme.compact(bubble.wallet.label,7);
        g.setColor(WlanTheme.TEXT);
        g.drawString(label,(float) (bubble.x - labelMetrics.stringWidth(label) / 2.0),(float) (bubble.y - 3));

        if(radius > 39.0) {
            String balance = Money.format(bubble.wallet.balance) + " MATH";
            g.setFont(WlanTheme.font(Font.PLAIN,Math.max(8.5f,labelSize - 3f)));
            FontMetrics balanceMetrics = g.getFontMetrics();
            g.setColor(WlanTheme.TEXT_SOFT);
            g.drawString(balance,(float) (bubble.x - balanceMetrics.stringWidth(balance) / 2.0),(float) (bubble.y + labelSize + 3));
        }
    }

    private static final class Bubble {
        private final WlanUIController.WalletView wallet;
        private final double x;
        private final double y;
        private final double radius;

        private Bubble(WlanUIController.WalletView wallet,double x,double y,double radius) {
            this.wallet = wallet;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }
}
