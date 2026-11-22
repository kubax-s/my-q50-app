import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/** Scales a square image to an icon size. Usage: java tools/icon/ScaleIcon.java <in.png> <size> <out.png> */
public final class ScaleIcon {
    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        int size = Integer.parseInt(args[1]);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // keep aspect ratio, centre on transparent background
        double k = Math.min(size / (double) src.getWidth(), size / (double) src.getHeight());
        int w = (int) Math.round(src.getWidth() * k);
        int h = (int) Math.round(src.getHeight() * k);
        g.drawImage(src, (size - w) / 2, (size - h) / 2, w, h, null);
        g.dispose();
        ImageIO.write(out, "png", new File(args[2]));
    }
}
