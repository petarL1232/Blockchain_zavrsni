package assets.branding;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.ImageIO;

public class MathosCoinLogo {
    static final int W=865,H=847;
    static final double CX=432.5,CY=423.5;

    public static void main(String[] args) throws Exception {
        String out=args.length>0?args[0]:"/mnt/data/mathoscoin_java_v3.png";
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE); g.fillRect(0,0,W,H);
        draw(g);
        g.dispose();
        ImageIO.write(img,"PNG",new File(out));
    }

    static void draw(Graphics2D g){
        // dark coin: stronger violet glow near upper centre, dark perimeter
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(445,285),410f,
                new float[]{0f,.30f,.68f,1f},
                new Color[]{new Color(78,20,181),new Color(59,10,151),new Color(35,4,107),new Color(18,2,63)}));
        g.fill(new Ellipse2D.Double(CX-357,CY-357,714,714));

        // subtle localized glow visible in the source above the M
        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(430,185),190f,
                new float[]{0f,.45f,1f},
                new Color[]{new Color(110,50,255,48),new Color(90,35,240,24),new Color(90,35,240,0)}));
        g.fill(new Ellipse2D.Double(CX-357,CY-357,714,714));
        g.setComposite(oldComp);

        // outer orbit: conical/angular gradient like the reference
        g.setPaint(new AngularGradientPaint(CX,CY,
                new float[]{0f,.083f,.167f,.25f,.333f,.50f,.583f,.667f,.75f,.833f,.917f,1f},
                new Color[]{
                        new Color(70,3,201), new Color(57,2,179), new Color(57,3,178),
                        new Color(61,4,182), new Color(90,10,224), new Color(142,64,252),
                        new Color(171,104,253), new Color(192,140,253), new Color(152,77,253),
                        new Color(111,21,246), new Color(103,16,238), new Color(70,3,201)}));
        g.setStroke(new BasicStroke(72f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(CX-383,CY-383,766,766));

        // inner C ring: long arc, angular gradient; exact endpoints close to source
        g.setPaint(new AngularGradientPaint(CX,CY,
                new float[]{0f,.083f,.167f,.25f,.333f,.417f,.50f,.583f,.667f,.75f,.82f,1f},
                new Color[]{
                        new Color(223,198,255),new Color(219,189,253),new Color(193,142,252),
                        new Color(151,78,252),new Color(138,61,253),new Color(170,104,253),
                        new Color(178,118,253),new Color(155,79,252),new Color(196,147,252),
                        new Color(222,194,253),new Color(239,225,254),new Color(223,198,255)}));
        g.setStroke(new BasicStroke(43f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(CX-298,CY-298,596,596,65,264,Arc2D.OPEN));

        // M: four polygons with multi-stop gradients
        Path2D lt=new Path2D.Double(); lt.moveTo(237,249);lt.lineTo(432,390);lt.lineTo(432,500);lt.lineTo(237,344);lt.closePath();
        g.setPaint(new LinearGradientPaint(237,249,432,500,new float[]{0f,.48f,1f},new Color[]{new Color(252,241,255),new Color(235,211,255),new Color(197,143,255)}));g.fill(lt);
        Path2D rt=new Path2D.Double(); rt.moveTo(628,249);rt.lineTo(432,390);rt.lineTo(432,500);rt.lineTo(628,344);rt.closePath();
        g.setPaint(new LinearGradientPaint(628,249,432,500,new float[]{0f,.5f,1f},new Color[]{new Color(248,235,255),new Color(210,170,253),new Color(125,38,249)}));g.fill(rt);
        Path2D ll=new Path2D.Double(); ll.moveTo(237,344);ll.lineTo(328,420);ll.lineTo(328,618);ll.lineTo(237,551);ll.closePath();
        g.setPaint(new LinearGradientPaint(237,350,328,618,new float[]{0f,.55f,1f},new Color[]{new Color(150,70,252),new Color(172,108,253),new Color(200,151,253)}));g.fill(ll);
        Path2D rl=new Path2D.Double(); rl.moveTo(628,344);rl.lineTo(536,420);rl.lineTo(536,618);rl.lineTo(628,551);rl.closePath();
        g.setPaint(new LinearGradientPaint(628,350,536,618,new float[]{0f,.55f,1f},new Color[]{new Color(98,30,235),new Color(132,55,251),new Color(175,110,252)}));g.fill(rl);

        // nodes mask the underlying ring and leave a white halo just like the raster source
        drawNode(g,714,158,51,68);
        drawNode(g,129,663,51,68);
    }

    static void drawNode(Graphics2D g,double x,double y,double r,double hr){
        g.setColor(Color.WHITE);g.fill(new Ellipse2D.Double(x-hr,y-hr,hr*2,hr*2));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x-14,y-16),(float)(r*1.08),
                new float[]{0f,.55f,1f},new Color[]{new Color(118,39,255),new Color(91,13,234),new Color(65,4,195)}));
        g.fill(new Ellipse2D.Double(x-r,y-r,r*2,r*2));
    }

    // Conical gradient Paint. fraction 0 = screen angle 0° (right), .25=down, .5=left, .75=up.
    static class AngularGradientPaint implements Paint {
        final double cx,cy; final float[] f; final Color[] c;
        AngularGradientPaint(double cx,double cy,float[] f,Color[] c){this.cx=cx;this.cy=cy;this.f=f;this.c=c;}
        public PaintContext createContext(ColorModel cm, Rectangle db, Rectangle2D ub, AffineTransform xform, RenderingHints hints){
            Point2D p=xform.transform(new Point2D.Double(cx,cy),null);
            return new Ctx(p.getX(),p.getY(),f,c);
        }
        public int getTransparency(){return Transparency.OPAQUE;}
        static class Ctx implements PaintContext{
            final double cx,cy; final float[] f; final Color[] c;
            Ctx(double cx,double cy,float[] f,Color[] c){this.cx=cx;this.cy=cy;this.f=f;this.c=c;}
            public void dispose(){}
            public ColorModel getColorModel(){return ColorModel.getRGBdefault();}
            public Raster getRaster(int x,int y,int w,int h){
                WritableRaster ras=getColorModel().createCompatibleWritableRaster(w,h);
                int[] px=new int[w*h*4]; int k=0;
                for(int j=0;j<h;j++) for(int i=0;i<w;i++){
                    double a=Math.atan2((y+j+0.5)-cy,(x+i+0.5)-cx);
                    if(a<0)a+=Math.PI*2; float t=(float)(a/(Math.PI*2));
                    int s=0; while(s<f.length-2 && t>f[s+1])s++;
                    float u=(t-f[s])/(f[s+1]-f[s]); u=Math.max(0,Math.min(1,u));
                    Color aC=c[s], bC=c[s+1];
                    px[k++]=(int)(aC.getRed()+(bC.getRed()-aC.getRed())*u);
                    px[k++]=(int)(aC.getGreen()+(bC.getGreen()-aC.getGreen())*u);
                    px[k++]=(int)(aC.getBlue()+(bC.getBlue()-aC.getBlue())*u);
                    px[k++]=255;
                }
                ras.setPixels(0,0,w,h,px); return ras;
            }
        }
    }
}
