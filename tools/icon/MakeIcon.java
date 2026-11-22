import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Draws the launcher icon: black background, a gauge arc with ticks and "Q50".
 * Usage: java tools/icon/MakeIcon.java <size> <out.png>
 */
public final class MakeIcon {
    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]);
        float s = size / 96f;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, size, size);

        float cx = 48 * s;
        float cy = 52 * s;
        float r = 34 * s;
        // gauge arc: 240° sweep, grey track then an orange segment
        g.setStroke(new BasicStroke(5 * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x3A3F47));
        g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, -30, 240, Arc2D.OPEN));
        g.setPaint(new GradientPaint(cx - r, cy, new Color(0xFFB347), cx + r, cy - r, new Color(0xFF6A00)));
        g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, 90, 120, Arc2D.OPEN));
        // ticks
        g.setStroke(new BasicStroke(1.6f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x8A9099));
        for (int i = 0; i <= 8; i++) {
            double a = Math.toRadians(210 - i * 30);
            float r1 = r - 8 * s;
            float r2 = r - 4.5f * s;
            g.draw(new Line2D.Double(cx + Math.cos(a) * r1, cy - Math.sin(a) * r1, cx + Math.cos(a) * r2, cy - Math.sin(a) * r2));
        }
        // "Q50"
        Font f = new Font("DejaVu Sans", Font.BOLD, Math.round(23 * s));
        g.setFont(f);
        String text = "Q50";
        float tw = (float) f.getStringBounds(text, g.getFontRenderContext()).getWidth();
        g.setPaint(new GradientPaint(0, cy - 12 * s, new Color(0xFFFFFF), 0, cy + 10 * s, new Color(0xB8BEC6)));
        g.drawString(text, cx - tw / 2, cy + 9 * s);
        g.dispose();
        ImageIO.write(img, "png", new File(args[1]));
    }
}
