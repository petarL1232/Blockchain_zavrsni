import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;

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
    public static final int CARD_RADIUS = 24;
    public static final int CONTROL_RADIUS = 16;

    private WlanTheme() {
    }

    public static void install() {
        UIManager.put("Panel.background",BACKGROUND);
        UIManager.put("Label.foreground",TEXT);
        UIManager.put("Label.font",font(Font.PLAIN,15));
        UIManager.put("Button.font",font(Font.BOLD,14));
        UIManager.put("TextField.font",font(Font.PLAIN,15));
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
        private double twinklePhase;
        private final Timer twinkleTimer;

        public BackgroundPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            twinkleTimer = new Timer(95,event -> {
                twinklePhase += .055;
                double outerRadius = Math.max(350.0,Math.min(getHeight() * .72,getWidth() * .52));
                int animatedX = Math.max(0,(int) Math.floor(getWidth() + 10.0 - outerRadius));
                repaint(animatedX,0,getWidth() - animatedX,getHeight());
            });
            twinkleTimer.setCoalesce(true);
        }

        @Override public void addNotify() {
            super.addNotify();
            twinkleTimer.start();
        }

        @Override public void removeNotify() {
            twinkleTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.setPaint(new GradientPaint(0,0,BACKGROUND,getWidth(),getHeight(),BACKGROUND_SOFT));
            g.fillRect(0,0,getWidth(),getHeight());

            g.setPaint(new RadialGradientPaint(
                    new Point(Math.max(0,getWidth() - 70),Math.max(170,getHeight() / 2)),520,
                    new float[]{0f,1f},
                    new Color[]{alpha(PRIMARY,48),alpha(PRIMARY,0)}));
            g.fillRect(0,0,getWidth(),getHeight());

            paintMathosBackground(g);
            g.dispose();
            super.paintComponent(graphics);
        }

        private void paintMathosBackground(Graphics2D g) {
            double centerX = getWidth() + 55.0;
            double centerY = Math.max(230.0,getHeight() * .48);
            double outerRadius = Math.max(350.0,Math.min(getHeight() * .72,getWidth() * .52));
            double[] radii = {outerRadius,outerRadius * .70,outerRadius * .40};
            float[] widths = {12f,18f,24f};
            Color[] colors = {alpha(PRIMARY_LIGHT,34),alpha(TEXT_SOFT,27),alpha(PRIMARY_LIGHT,31)};

            for(int i = 0; i < radii.length; i++) {
                double radius = radii[i];
                g.setStroke(new BasicStroke(widths[i],BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.setColor(colors[i]);
                g.draw(new Arc2D.Double(centerX - radius,centerY - radius,radius * 2,radius * 2,
                        101 + i * 5,154 - i * 9,Arc2D.OPEN));

                g.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.setColor(alpha(CYAN,33));
                g.draw(new Arc2D.Double(centerX - radius,centerY - radius,radius * 2,radius * 2,
                        101 + i * 5,154 - i * 9,Arc2D.OPEN));
            }

            int[] starCounts = {13,10,7};
            for(int ring = 0; ring < radii.length; ring++) {
                int count = starCounts[ring];
                double start = 108 + ring * 5;
                double sweep = 139 - ring * 10;

                for(int i = 0; i < count; i++) {
                    double angle = Math.toRadians(start + sweep * i / Math.max(1,count - 1));
                    double radius = radii[ring] + Math.sin(i * 2.17 + ring) * 5.0;
                    double x = centerX + Math.cos(angle) * radius;
                    double y = centerY - Math.sin(angle) * radius;
                    double glow = .52 + .48 * Math.sin(twinklePhase + i * 1.31 + ring * 2.07);
                    int starAlpha = 45 + (int) Math.round(glow * 105);
                    double size = 1.7 + glow * 1.8 + (i % 4 == 0 ? .8 : 0);
                    paintStar(g,x,y,size,starAlpha,ring == 1 ? TEXT_SOFT : CYAN);
                }
            }
        }

        private void paintStar(Graphics2D g,double x,double y,double size,int starAlpha,Color color) {
            if(x < -10 || x > getWidth() + 10 || y < -10 || y > getHeight() + 10) return;
            int glowSize = (int) Math.ceil(size * 4.0);
            g.setColor(alpha(color,Math.max(8,starAlpha / 5)));
            g.fillOval((int) Math.round(x - glowSize / 2.0),(int) Math.round(y - glowSize / 2.0),glowSize,glowSize);
            g.setColor(alpha(color,starAlpha));
            g.setStroke(new BasicStroke(Math.max(1f,(float) size / 2.6f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            int arm = (int) Math.ceil(size * 1.55);
            g.drawLine((int) Math.round(x - arm),(int) Math.round(y),(int) Math.round(x + arm),(int) Math.round(y));
            g.drawLine((int) Math.round(x),(int) Math.round(y - arm),(int) Math.round(x),(int) Math.round(y + arm));
            int center = Math.max(2,(int) Math.round(size));
            g.fillOval((int) Math.round(x - center / 2.0),(int) Math.round(y - center / 2.0),center,center);
        }
    }

    public static class Card extends JPanel {
        private final int radius;
        private Color fill = alpha(SURFACE,232);
        private Color stroke = BORDER;

        public Card(LayoutManager layout) {
            this(layout,CARD_RADIUS);
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

    public static class ControlSurface extends Card {
        public ControlSurface(LayoutManager layout) {
            super(layout,CONTROL_RADIUS);
            fill(SURFACE_HIGH);
        }

        @Override protected void paintChildren(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setClip(new RoundRectangle2D.Double(1,1,Math.max(0,getWidth() - 2),Math.max(0,getHeight() - 2),
                    CONTROL_RADIUS,CONTROL_RADIUS));
            super.paintChildren(g);
            g.dispose();
        }
    }

    public static class AccentButton extends JButton {
        private boolean hover;
        private final boolean filled;

        public AccentButton(String text, boolean filled) {
            super(text);
            this.filled = filled;
            setForeground(TEXT);
            setFont(font(Font.BOLD,14));
            setBorder(new EmptyBorder(11,18,11,18));
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
                g.fillRoundRect(0,0,getWidth(),getHeight(),CONTROL_RADIUS,CONTROL_RADIUS);
            } else {
                g.setColor(hover ? alpha(PRIMARY_LIGHT,48) : alpha(SURFACE_HIGH,210));
                g.fillRoundRect(0,0,getWidth(),getHeight(),CONTROL_RADIUS,CONTROL_RADIUS);
                g.setColor(hover ? PRIMARY_LIGHT : BORDER);
                g.drawRoundRect(0,0,getWidth() - 1,getHeight() - 1,CONTROL_RADIUS,CONTROL_RADIUS);
            }

            if(hasFocus()) {
                g.setColor(CYAN);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(2,2,getWidth() - 5,getHeight() - 5,CONTROL_RADIUS - 2,CONTROL_RADIUS - 2);
            }

            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class RoundedTextField extends JTextField {
        public RoundedTextField() {
            this("");
        }

        public RoundedTextField(String value) {
            super(value);
            setOpaque(false);
            setFont(font(Font.PLAIN,15));
            setBorder(new EmptyBorder(10,13,10,13));
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent event) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent event) { repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isEnabled() ? SURFACE_HIGH : alpha(SURFACE_HIGH,145));
            g.fillRoundRect(0,0,getWidth(),getHeight(),CONTROL_RADIUS,CONTROL_RADIUS);
            g.dispose();
            super.paintComponent(graphics);
        }

        @Override protected void paintBorder(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(hasFocus() ? PRIMARY_LIGHT : BORDER);
            g.setStroke(new BasicStroke(hasFocus() ? 1.6f : 1f));
            g.drawRoundRect(0,0,getWidth() - 1,getHeight() - 1,CONTROL_RADIUS,CONTROL_RADIUS);
            g.dispose();
        }
    }

    public static class PillLabel extends JLabel {
        public PillLabel(String text,Color color) {
            super(text);
            setForeground(color);
            setFont(font(Font.BOLD,12));
            setOpaque(false);
            setBorder(new EmptyBorder(7,11,7,11));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color tone = getForeground() == null ? MUTED : getForeground();
            int radius = Math.max(14,getHeight());
            g.setColor(alpha(tone,23));
            g.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);
            g.setColor(alpha(tone,90));
            g.drawRoundRect(0,0,getWidth() - 1,getHeight() - 1,radius,radius);
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
