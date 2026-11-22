package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Colours, paints and drawing helpers. Layout is designed on an 800×480 grid and scaled by s. */
public final class Theme {
    public static final int BG = 0xFF0B0D10;
    public static final int PANEL = 0xFF161A20;
    public static final int PANEL_HI = 0xFF222832;
    public static final int LINE = 0xFF2C333D;
    public static final int TEXT = 0xFFE8EAED;
    public static final int DIM = 0xFF8A9099;
    public static final int ACCENT = 0xFFFF8A00;
    public static final int OK = 0xFF3DDC84;
    public static final int COLD = 0xFF4FA3FF;
    public static final int HOT = 0xFFFF4D4D;
    public static final int DEMO = 0xFFFFD24D;

    public float s = 1f;
    public final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path arrow = new Path();

    public Theme() {
        stroke.setStyle(Paint.Style.STROKE);
        text.setColor(TEXT);
        bold.setColor(TEXT);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public float px(float designPx) {
        return designPx * s;
    }

    public void panel(Canvas c, float l, float t, float r, float b, int color) {
        fill.setColor(color);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, px(8), px(8), fill);
    }

    public void outline(Canvas c, float l, float t, float r, float b, int color, float width) {
        stroke.setColor(color);
        stroke.setStrokeWidth(px(width));
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, px(8), px(8), stroke);
    }

    public Paint text(float size, int color, Paint.Align align) {
        text.setTextSize(px(size));
        text.setColor(color);
        text.setTextAlign(align);
        return text;
    }

    public Paint bold(float size, int color, Paint.Align align) {
        bold.setTextSize(px(size));
        bold.setColor(color);
        bold.setTextAlign(align);
        return bold;
    }

    /** Text cut to fit maxWidth, ending with "…". */
    public static String fit(Paint p, String str, float maxWidth) {
        if (p.measureText(str) <= maxWidth) {
            return str;
        }
        int n = p.breakText(str, true, maxWidth - p.measureText("…"), null);
        return str.substring(0, Math.max(0, n)) + "…";
    }

    /** Button with a drawn triangle (the stock 2.3 font may lack arrow glyphs). */
    public void arrowButton(Canvas c, RectF r, boolean up, boolean enabled) {
        fill.setColor(enabled ? PANEL_HI : PANEL);
        c.drawRoundRect(r, px(8), px(8), fill);
        float w = px(14);
        float cx = r.centerX();
        float cy = r.centerY();
        float dy = up ? -w * 0.6f : w * 0.6f;
        arrow.reset();
        arrow.moveTo(cx, cy + dy);
        arrow.lineTo(cx - w, cy - dy);
        arrow.lineTo(cx + w, cy - dy);
        arrow.close();
        fill.setColor(enabled ? TEXT : LINE);
        c.drawPath(arrow, fill);
    }

    /** Draws a button and returns nothing; hit-testing is done by the caller with the same rect. */
    public void button(Canvas c, RectF r, String label, int bg, int fg, float textSize) {
        fill.setColor(bg);
        c.drawRoundRect(r, px(8), px(8), fill);
        Paint p = bold(textSize, fg, Paint.Align.CENTER);
        c.drawText(label, r.centerX(), r.centerY() - (p.ascent() + p.descent()) / 2, p);
    }
}
