package android.graphics;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;

    public enum Align { LEFT, CENTER, RIGHT }

    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    private static final Font REGULAR = new Font("DejaVu Sans", Font.PLAIN, 12);
    private static final Font BOLD = new Font("DejaVu Sans", Font.BOLD, 12);
    private static final Graphics2D MEASURE;

    static {
        MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        MEASURE.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        MEASURE.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    int color = 0xFF000000;
    float textSize = 12;
    Align align = Align.LEFT;
    Style style = Style.FILL;
    float strokeWidth;
    Typeface typeface = Typeface.DEFAULT;

    public Paint() {
    }

    public Paint(int flags) {
    }

    public void setColor(int c) {
        color = c;
    }

    public int getColor() {
        return color;
    }

    public void setTextSize(float s) {
        textSize = s;
    }

    public void setTextAlign(Align a) {
        align = a;
    }

    public void setStyle(Style s) {
        style = s;
    }

    public void setStrokeWidth(float w) {
        strokeWidth = w;
    }

    public void setTypeface(Typeface t) {
        typeface = t;
    }

    Font font() {
        return (typeface != null && typeface.bold ? BOLD : REGULAR).deriveFont(textSize);
    }

    private FontMetrics metrics() {
        return MEASURE.getFontMetrics(font());
    }

    /** Benchmark mode: allocation-free width from cached per-size metrics (like native Skia). */
    public static boolean fastMeasure;
    private static final FontMetrics[][] CACHE = new FontMetrics[2][4000];

    private FontMetrics cachedMetrics() {
        int b = typeface != null && typeface.bold ? 1 : 0;
        int k = Math.min(3999, Math.round(textSize * 10));
        FontMetrics fm = CACHE[b][k];
        if (fm == null) {
            fm = MEASURE.getFontMetrics(font());
            CACHE[b][k] = fm;
        }
        return fm;
    }

    public float measureText(String s) {
        if (fastMeasure) {
            FontMetrics fm = cachedMetrics();
            int w = 0;
            for (int i = 0; i < s.length(); i++) w += fm.charWidth(s.charAt(i));
            return w;
        }
        return (float) font().getStringBounds(s, MEASURE.getFontRenderContext()).getWidth();
    }

    public float ascent() {
        return -(fastMeasure ? cachedMetrics() : metrics()).getAscent();
    }

    public float descent() {
        return (fastMeasure ? cachedMetrics() : metrics()).getDescent();
    }

    public int breakText(String text, boolean measureForwards, float maxWidth, float[] measuredWidth) {
        int n = 0;
        if (fastMeasure) {
            FontMetrics fm = cachedMetrics();
            int w = 0;
            while (n < text.length() && w + fm.charWidth(text.charAt(n)) <= maxWidth) {
                w += fm.charWidth(text.charAt(n));
                n++;
            }
        } else {
            while (n < text.length() && measureText(text.substring(0, n + 1)) <= maxWidth) {
                n++;
            }
        }
        if (measuredWidth != null && measuredWidth.length > 0) {
            measuredWidth[0] = measureText(text.substring(0, n));
        }
        return n;
    }
}
