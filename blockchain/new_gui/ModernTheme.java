import java.awt.Adjustable;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;

public final class ModernTheme {
    public static final Color CANVAS = new Color(7, 10, 22);
    public static final Color SIDEBAR = new Color(11, 16, 34);
    public static final Color CARD = new Color(16, 23, 44);
    public static final Color RAISED = new Color(21, 31, 59);
    public static final Color BORDER = new Color(92, 108, 150);
    public static final Color TEXT = new Color(247, 248, 255);
    public static final Color MUTED = new Color(170, 180, 208);
    public static final Color PURPLE = new Color(167, 139, 250);
    public static final Color BLUE = new Color(96, 165, 250);
    public static final Color CYAN = new Color(34, 211, 238);
    public static final Color SUCCESS = new Color(52, 211, 153);
    public static final Color WARNING = new Color(251, 191, 36);
    public static final Color ERROR = new Color(251, 113, 133);
    public static final Color BUTTON_PURPLE = new Color(109, 40, 217);
    public static final Color BUTTON_BLUE = new Color(29, 78, 216);
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    public static final int RADIUS = 18;

    private ModernTheme() {
    }

    public static void install() {
        Font regular = font(Font.PLAIN, 14);
        UIManager.put("Panel.background", CANVAS);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", regular);
        UIManager.put("Button.font", font(Font.BOLD, 14));
        UIManager.put("ToggleButton.font", font(Font.BOLD, 14));
        UIManager.put("TextField.font", regular);
        UIManager.put("TextField.background", RAISED);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", PURPLE);
        UIManager.put("TextField.selectionBackground", PURPLE.darker());
        UIManager.put("TextArea.font", regular);
        UIManager.put("TextArea.background", RAISED);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("ComboBox.font", regular);
        UIManager.put("ComboBox.background", RAISED);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Spinner.background", RAISED);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.font", font(Font.PLAIN, 12));
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ScrollPane.background", TRANSPARENT);
        UIManager.put("Viewport.background", TRANSPARENT);
        UIManager.put("ScrollBar.width", 11);
    }

    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static Color blend(Color from, Color to, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * value);
        int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * value);
        int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * value);
        int alpha = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * value);
        return new Color(red, green, blue, alpha);
    }

    public static String shortHash(String value, int visibleCharacters) {
        if(value == null || value.isBlank()) return "-";
        int shown = Math.max(2, visibleCharacters);
        if(value.length() <= shown * 2 + 3) return value;
        return value.substring(0, shown) + "..." + value.substring(value.length() - shown);
    }

    public static void styleInput(JComponent component) {
        component.setFont(font(Font.PLAIN, 14));
        component.setForeground(TEXT);
        component.setBackground(RAISED);
        component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), padding(9, 12, 9, 12)));
    }

    private static void antialias(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    public static class RoundedPanel extends JPanel {
        private int radius;
        private Color outline;

        public RoundedPanel() {
            this(RADIUS, CARD, null);
        }

        public RoundedPanel(int radius) {
            this(radius, CARD, null);
        }

        public RoundedPanel(int radius, Color background) {
            this(radius, background, null);
        }

        public RoundedPanel(int radius, Color background, Color outline) {
            this.radius = radius;
            this.outline = outline;
            setBackground(background);
            setOpaque(false);
        }

        public void setRadius(int radius) {
            this.radius = Math.max(0, radius);
            repaint();
        }

        public void setOutline(Color outline) {
            this.outline = outline;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            antialias(g2);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if(outline != null) {
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), radius, radius);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class GradientButton extends JButton {
        private float hoverProgress;
        private boolean hovered;
        private final Timer animation;

        public GradientButton(String text) {
            super(text);
            setFont(font(Font.BOLD, 14));
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(padding(10, 18, 10, 18));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            animation = new Timer(16, event -> animateHover());
            animation.setCoalesce(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    if(isEnabled()) animation.start();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    animation.start();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent event) {
                    repaint();
                }
            });
        }

        private void animateHover() {
            float target = hovered && isEnabled() ? 1f : 0f;
            hoverProgress += (target - hoverProgress) * 0.24f;
            if(Math.abs(target - hoverProgress) < 0.02f) {
                hoverProgress = target;
                animation.stop();
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.height = Math.max(42, size.height);
            return size;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            antialias(g2);
            if(!isEnabled()) g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            int pressedOffset = getModel().isPressed() ? 1 : 0;
            int width = Math.max(0, getWidth() - 1);
            int height = Math.max(0, getHeight() - 3);
            g2.setColor(withAlpha(Color.BLACK, 80));
            g2.fillRoundRect(1, 3, width, height, 14, 14);
            Color start = blend(BUTTON_PURPLE, new Color(126, 58, 237), hoverProgress);
            Color end = blend(BUTTON_BLUE, new Color(37, 99, 235), hoverProgress);
            g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g2.fillRoundRect(0, pressedOffset, width, height, 14, 14);
            if(hasFocus()) {
                g2.setColor(withAlpha(CYAN, 190));
                g2.drawRoundRect(1, 1, Math.max(0, width - 2), Math.max(0, height - 2), 12, 12);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class NavButton extends JToggleButton {
        private float hoverProgress;
        private boolean hovered;
        private final Timer animation;

        public NavButton(String text) {
            super(text);
            setFont(font(Font.BOLD, 14));
            setForeground(MUTED);
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(padding(11, 16, 11, 16));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            animation = new Timer(16, event -> animateState());
            animation.setCoalesce(true);
            addChangeListener(event -> {
                setForeground(isSelected() ? TEXT : MUTED);
                animation.start();
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    animation.start();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    animation.start();
                }
            });
        }

        private void animateState() {
            float target = hovered || isSelected() ? 1f : 0f;
            hoverProgress += (target - hoverProgress) * 0.24f;
            if(Math.abs(target - hoverProgress) < 0.02f) {
                hoverProgress = target;
                animation.stop();
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.height = Math.max(42, size.height);
            return size;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            antialias(g2);
            int alpha = Math.round((isSelected() ? 48 : 24) * hoverProgress);
            g2.setColor(withAlpha(isSelected() ? PURPLE : BLUE, alpha));
            g2.fillRoundRect(0, 1, getWidth(), Math.max(0, getHeight() - 2), 13, 13);
            if(isSelected()) {
                g2.setPaint(new GradientPaint(0, 6, PURPLE, 0, getHeight() - 6, BLUE));
                g2.fillRoundRect(0, 8, 4, Math.max(0, getHeight() - 16), 4, 4);
            }
            if(hasFocus()) {
                g2.setColor(withAlpha(CYAN, 150));
                g2.drawRoundRect(1, 2, Math.max(0, getWidth() - 3), Math.max(0, getHeight() - 5), 12, 12);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class PillLabel extends JLabel {
        private Color tone;

        public PillLabel(String text) {
            this(text, PURPLE);
        }

        public PillLabel(String text, Color tone) {
            super(text);
            this.tone = tone;
            setFont(font(Font.BOLD, 11));
            setForeground(blend(tone, Color.WHITE, 0.35f));
            setBorder(padding(5, 10, 5, 10));
            setOpaque(false);
        }

        public void setTone(Color tone) {
            this.tone = tone;
            setForeground(blend(tone, Color.WHITE, 0.35f));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            antialias(g2);
            g2.setColor(withAlpha(tone, 38));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(withAlpha(tone, 115));
            g2.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    public static class RoundedScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = withAlpha(BORDER, 150);
            thumbDarkShadowColor = TRANSPARENT;
            thumbHighlightColor = TRANSPARENT;
            thumbLightShadowColor = TRANSPARENT;
            trackColor = TRANSPARENT;
            trackHighlightColor = TRANSPARENT;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        private JButton invisibleButton() {
            JButton button = new JButton();
            Dimension hidden = new Dimension(0, 0);
            button.setPreferredSize(hidden);
            button.setMinimumSize(hidden);
            button.setMaximumSize(hidden);
            return button;
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, java.awt.Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            antialias(g2);
            g2.setColor(withAlpha(BORDER, 50));
            if(scrollbar.getOrientation() == Adjustable.VERTICAL) g2.fillRoundRect(bounds.x + bounds.width / 2 - 1, bounds.y + 3, 2, Math.max(0, bounds.height - 6), 2, 2);
            else g2.fillRoundRect(bounds.x + 3, bounds.y + bounds.height / 2 - 1, Math.max(0, bounds.width - 6), 2, 2, 2);
            g2.dispose();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, java.awt.Rectangle bounds) {
            if(bounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) graphics.create();
            antialias(g2);
            g2.setColor(withAlpha(isDragging ? CYAN : isThumbRollover() ? BLUE : BORDER, isDragging ? 220 : 175));
            if(scrollbar.getOrientation() == Adjustable.VERTICAL) g2.fillRoundRect(bounds.x + 2, bounds.y + 1, Math.max(4, bounds.width - 4), Math.max(0, bounds.height - 2), 8, 8);
            else g2.fillRoundRect(bounds.x + 1, bounds.y + 2, Math.max(0, bounds.width - 2), Math.max(4, bounds.height - 4), 8, 8);
            g2.dispose();
        }
    }

    public static class SmoothScrollPane extends JScrollPane implements MouseWheelListener {
        private final int axis;
        private final Timer scrollAnimation;
        private double position;
        private double target;

        public SmoothScrollPane(Component view, int axis) {
            super(view);
            if(axis != Adjustable.VERTICAL && axis != Adjustable.HORIZONTAL) throw new IllegalArgumentException("Axis mora biti Adjustable.VERTICAL ili Adjustable.HORIZONTAL");
            this.axis = axis;
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);
            setWheelScrollingEnabled(false);
            getViewport().setOpaque(false);
            getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
            styleScrollBar(getVerticalScrollBar(), Adjustable.VERTICAL);
            styleScrollBar(getHorizontalScrollBar(), Adjustable.HORIZONTAL);
            addMouseWheelListener(this);
            MouseAdapter stopOnDrag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    scrollAnimation.stop();
                }
            };
            getVerticalScrollBar().addMouseListener(stopOnDrag);
            getHorizontalScrollBar().addMouseListener(stopOnDrag);
            scrollAnimation = new Timer(16, event -> animateScroll());
            scrollAnimation.setCoalesce(true);
        }

        public SmoothScrollPane(Component view) {
            this(view, Adjustable.VERTICAL);
        }

        private void styleScrollBar(JScrollBar bar, int orientation) {
            bar.setUI(new RoundedScrollBarUI());
            bar.setOpaque(false);
            bar.setBackground(TRANSPARENT);
            bar.setForeground(PURPLE);
            bar.setUnitIncrement(18);
            if(orientation == Adjustable.VERTICAL) bar.setPreferredSize(new Dimension(11, 0));
            else bar.setPreferredSize(new Dimension(0, 11));
        }

        private JScrollBar activeScrollBar() {
            return axis == Adjustable.VERTICAL ? getVerticalScrollBar() : getHorizontalScrollBar();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent event) {
            JScrollBar bar = activeScrollBar();
            if(!bar.isVisible() || !bar.isEnabled()) return;
            if(!scrollAnimation.isRunning()) position = target = bar.getValue();
            int direction = event.getPreciseWheelRotation() < 0 ? -1 : 1;
            double distance = Math.max(16, bar.getUnitIncrement(direction)) * event.getScrollAmount() * event.getPreciseWheelRotation();
            double oldTarget = target;
            target = clamp(target + distance, bar);
            if(target == oldTarget) return;
            event.consume();
            scrollAnimation.start();
        }

        private void animateScroll() {
            JScrollBar bar = activeScrollBar();
            target = clamp(target, bar);
            position += (target - position) * 0.24;
            if(Math.abs(target - position) < 0.6) {
                position = target;
                scrollAnimation.stop();
            }
            bar.setValue((int) Math.round(position));
        }

        private double clamp(double value, JScrollBar bar) {
            int minimum = bar.getMinimum();
            int maximum = Math.max(minimum, bar.getMaximum() - bar.getVisibleAmount());
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
