import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class WlanTheme {
    public static final Color BACKGROUND = new Color(3,8,23);
    public static final Color BACKGROUND_SOFT = new Color(6,17,38);
    public static final Color SURFACE = new Color(10,23,48);
    public static final Color SURFACE_HIGH = new Color(14,31,61);
    public static final Color BORDER = new Color(37,59,98);
    public static final Color PRIMARY = new Color(0,71,187); // plava sa slike
    public static final Color PRIMARY_LIGHT = new Color(33,111,255);
    public static final Color PURPLE = new Color(118,87,255);
    public static final Color CYAN = new Color(56,189,248);
    public static final Color TEXT = new Color(237,244,255);
    public static final Color TEXT_SOFT = new Color(206,217,229); // svijetla boja sa slike
    public static final Color MUTED = new Color(145,167,199);
    public static final Color SUCCESS = new Color(53,211,154);
    public static final Color WARNING = new Color(255,184,77);
    public static final Color DANGER = new Color(255,98,125);

    private WlanTheme() {
    }

    public static void install() {
        UIManager.put("Panel.background",BACKGROUND);
        UIManager.put("Label.foreground",TEXT);
        UIManager.put("Label.font",font(Font.PLAIN,14));
        UIManager.put("Button.font",font(Font.BOLD,13));
        UIManager.put("TextField.font",font(Font.PLAIN,14));
        UIManager.put("TextField.background",SURFACE_HIGH);
        UIManager.put("TextField.foreground",TEXT);
        UIManager.put("TextField.caretForeground",CYAN);
        UIManager.put("TextField.selectionBackground",PRIMARY);
        UIManager.put("TextField.border",BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),new EmptyBorder(9,12,9,12)));
        UIManager.put("ComboBox.background",SURFACE_HIGH);
        UIManager.put("ComboBox.foreground",TEXT);
        UIManager.put("ComboBox.selectionBackground",PRIMARY);
        UIManager.put("ComboBox.selectionForeground",TEXT);
        UIManager.put("Spinner.background",SURFACE_HIGH);
        UIManager.put("OptionPane.background",SURFACE);
        UIManager.put("OptionPane.messageForeground",TEXT);
        UIManager.put("ToolTip.background",new Color(7,18,39));
        UIManager.put("ToolTip.foreground",TEXT);
        UIManager.put("ToolTip.border",BorderFactory.createLineBorder(BORDER));
        ToolTipManager.sharedInstance().setInitialDelay(180);
        ToolTipManager.sharedInstance().setDismissDelay(10000);
    }

    public static Font font(int style, float size) {
        return new Font("Segoe UI Variable",style,Math.round(size));
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(),color.getGreen(),color.getBlue(),Math.max(0,Math.min(255,alpha)));
    }

    public static String compact(String value, int visible) {
        if(value == null || value.isBlank()) return "—";
        if(value.length() <= visible * 2 + 3) return value;
        return value.substring(0,visible) + "…" + value.substring(value.length() - visible);
    }

    public static JLabel label(String text, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(Font.PLAIN,size));
        label.setForeground(color);
        return label;
    }

    public static JLabel title(String text, float size) {
        JLabel label = label(text,size,TEXT);
        label.setFont(font(Font.BOLD,size));
        return label;
    }

    public static JScrollPane scroll(Component content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(18);
        scrollPane.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        return scrollPane;
    }

    public static class BackgroundPanel extends JPanel {
        public BackgroundPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0,0,BACKGROUND,getWidth(),getHeight(),BACKGROUND_SOFT));
            g.fillRect(0,0,getWidth(),getHeight());

            g.setStroke(new BasicStroke(2f));
            g.setColor(alpha(PRIMARY,34));
            g.drawOval(getWidth() - 330,-250,520,520);
            g.setStroke(new BasicStroke(9f));
            g.setColor(alpha(TEXT_SOFT,18));
            g.drawOval(getWidth() - 220,-175,355,355);

            g.setPaint(new RadialGradientPaint(
                    new Point(Math.max(0,getWidth() - 90),120),340,
                    new float[]{0f,1f},
                    new Color[]{alpha(PRIMARY,44),alpha(PRIMARY,0)}));
            g.fillRect(0,0,getWidth(),getHeight());
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class Card extends JPanel {
        private final int radius;
        private Color fill = alpha(SURFACE,232);
        private Color stroke = BORDER;

        public Card(LayoutManager layout) {
            this(layout,22);
        }

        public Card(LayoutManager layout, int radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }

        public Card fill(Color fill) {
            this.fill = fill;
            return this;
        }

        public Card stroke(Color stroke) {
            this.stroke = stroke;
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0,0,getWidth() - 1,getHeight() - 1,radius,radius);
            g.setColor(stroke);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0,0,getWidth() - 1,getHeight() - 1,radius,radius);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class AccentButton extends JButton {
        private boolean hover;
        private final boolean filled;

        public AccentButton(String text, boolean filled) {
            super(text);
            this.filled = filled;
            setForeground(TEXT);
            setFont(font(Font.BOLD,13));
            setBorder(new EmptyBorder(10,16,10,16));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

            if(filled) {
                g.setPaint(new GradientPaint(0,0,hover ? PRIMARY_LIGHT : PRIMARY,getWidth(),getHeight(),PURPLE));
                g.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
            } else {
                g.setColor(hover ? alpha(PRIMARY_LIGHT,48) : alpha(SURFACE_HIGH,210));
                g.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
                g.setColor(hover ? PRIMARY_LIGHT : BORDER);
                g.drawRoundRect(0,0,getWidth() - 1,getHeight() - 1,14,14);
            }

            if(hasFocus()) {
                g.setColor(CYAN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(2,2,getWidth() - 5,getHeight() - 5,12,12);
            }

            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class Toggle extends JToggleButton {
        private float animation;
        private final Timer animationTimer;

        public Toggle() {
            setPreferredSize(new Dimension(48,26));
            setMinimumSize(new Dimension(48,26));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            animationTimer = new Timer(16,event -> {
                float target = isSelected() ? 1f : 0f;
                animation += (target - animation) * .24f;
                if(Math.abs(target - animation) < .01f) {
                    animation = target;
                    ((Timer) event.getSource()).stop();
                }
                repaint();
            });
            addItemListener(event -> {
                if(isDisplayable()) animationTimer.start();
            });
        }

        @Override public void addNotify() {
            super.addNotify();
            animation = isSelected() ? 1f : 0f;
            repaint();
        }

        @Override public void removeNotify() {
            animationTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color off = new Color(44,61,91);
            Color on = PRIMARY_LIGHT;
            int red = Math.round(off.getRed() + (on.getRed() - off.getRed()) * animation);
            int green = Math.round(off.getGreen() + (on.getGreen() - off.getGreen()) * animation);
            int blue = Math.round(off.getBlue() + (on.getBlue() - off.getBlue()) * animation);
            g.setColor(new Color(red,green,blue));
            g.fillRoundRect(0,1,getWidth(),getHeight() - 2,getHeight(),getHeight());
            int diameter = getHeight() - 8;
            int x = Math.round(4 + animation * (getWidth() - diameter - 8));
            g.setColor(TEXT);
            g.fillOval(x,4,diameter,diameter);
            if(hasFocus()) {
                g.setColor(CYAN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(1,1,getWidth() - 3,getHeight() - 3,getHeight(),getHeight());
            }
            g.dispose();
        }
    }

    private static class ThinScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = alpha(PRIMARY_LIGHT,125);
            trackColor = alpha(SURFACE_HIGH,70);
        }

        @Override protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0,0));
            return button;
        }

        @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if(!scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(thumbColor);
            g.fillRoundRect(bounds.x + 2,bounds.y + 2,Math.max(4,bounds.width - 4),Math.max(4,bounds.height - 4),10,10);
            g.dispose();
        }
    }
}
