import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class MathosCoinBrand {
    public static final String NAME = "MathosCoin";
    public static final String SYMBOL = "$MATH";

    private static final String LOGO_RESOURCE = "/assets/branding/MathosCoinLogo.png";
    private static final Path LOGO_PATH = Path.of("assets", "branding", "MathosCoinLogo.png");
    private static final int[] LOGO_SIZES = {16,22,24,32,42,48,54,60,64,128,180,256};
    private static final BufferedImage LOGO = loadLogo();
    private static final Map<Integer,BufferedImage> LOGO_VARIANTS = loadLogoVariants();

    private MathosCoinBrand() {
    }

    public static JComponent logo(int size) {
        return new Logo(size);
    }

    public static void applyWindowIcon(JFrame frame) {
        BufferedImage windowIcon = logoForSize(64);
        if(frame != null && windowIcon != null) {
            frame.setIconImage(windowIcon);
        }
    }

    public static boolean paintLogo(Graphics2D graphics,int x,int y,int width,int height) {
        BufferedImage logo = logoForSize(Math.max(width,height));

        if(logo == null || graphics == null || width <= 0 || height <= 0) {
            return false;
        }

        double scale = Math.min(
                width / (double) logo.getWidth(),
                height / (double) logo.getHeight());

        int drawWidth = Math.max(1,(int) Math.round(logo.getWidth() * scale));
        int drawHeight = Math.max(1,(int) Math.round(logo.getHeight() * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(logo,drawX,drawY,drawWidth,drawHeight,null);
        if(oldInterpolation != null) {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,oldInterpolation);
        }

        return true;
    }

    private static BufferedImage logoForSize(int requestedSize) {
        BufferedImage exact = LOGO_VARIANTS.get(requestedSize);
        if(exact != null) return exact;

        int closestSize = -1;
        int closestDistance = Integer.MAX_VALUE;

        for(int size : LOGO_SIZES) {
            if(!LOGO_VARIANTS.containsKey(size)) continue;
            int distance = Math.abs(size - requestedSize);
            if(distance < closestDistance || distance == closestDistance && size > closestSize) {
                closestDistance = distance;
                closestSize = size;
            }
        }

        return closestSize == -1 ? LOGO : LOGO_VARIANTS.get(closestSize);
    }

    private static BufferedImage loadLogo() {
        return loadImage(LOGO_RESOURCE,LOGO_PATH);
    }

    private static Map<Integer,BufferedImage> loadLogoVariants() {
        Map<Integer,BufferedImage> variants = new HashMap<>();

        for(int size : LOGO_SIZES) {
            String fileName = "MathosCoinLogo-" + size + ".png";
            BufferedImage variant = loadImage(
                    "/assets/branding/icons/" + fileName,
                    Path.of("assets", "branding", "icons", fileName));
            if(variant != null) variants.put(size,variant);
        }

        return variants;
    }

    private static BufferedImage loadImage(String resource,Path path) {
        try(InputStream stream = MathosCoinBrand.class.getResourceAsStream(resource)) {
            if(stream != null) {
                return ImageIO.read(stream);
            }
        } catch(Exception ignored) {

        }

        try {
            if(Files.exists(path)) {
                return ImageIO.read(path.toFile());
            }
        } catch(Exception ignored) {

        }

        return null;
    }

    private static class Logo extends JComponent {
        private final int size;

        private Logo(int size) {
            this.size = Math.max(16,size);
            setOpaque(false);
            setPreferredSize(new Dimension(this.size,this.size));
            setMinimumSize(new Dimension(this.size,this.size));
            setMaximumSize(new Dimension(this.size,this.size));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

            if(!paintLogo(g,0,0,getWidth(),getHeight())) {
                g.setPaint(new GradientPaint(0,0,new Color(155,92,255),getWidth(),getHeight(),new Color(36,90,255)));
                g.fillOval(1,1,Math.max(0,getWidth() - 2),Math.max(0,getHeight() - 2));
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif",Font.BOLD,Math.max(12,getHeight() / 2)));
                FontMetrics metrics = g.getFontMetrics();
                g.drawString("M",(getWidth() - metrics.stringWidth("M")) / 2,
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            }

            g.dispose();
        }
    }
}
