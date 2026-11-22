package android.graphics;

import java.awt.image.BufferedImage;

public final class Bitmap {
    public enum Config { RGB_565, ARGB_8888 }

    final BufferedImage img;

    private Bitmap(int w, int h) {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    public static Bitmap createBitmap(int w, int h, Config config) {
        return new Bitmap(w, h);
    }

    public int getWidth() {
        return img.getWidth();
    }

    public int getHeight() {
        return img.getHeight();
    }

    public void recycle() {
    }
}
