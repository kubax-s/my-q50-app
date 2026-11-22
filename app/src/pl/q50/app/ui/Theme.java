package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Colours, paints and drawing helpers. Layout is designed on an 800×480 grid and scaled by s.
 *
 * The surface colours (BG … DIM) and the accent come from the chosen theme and accent
 * ({@link #apply}); the status colours (OK, WARN, HOT, COLD) never change, so red / orange /
 * green keep their meaning whatever the user picks.
 */
public final class Theme {
    // surfaces – set by apply()
    public static int BG;
    public static int PANEL;
    public static int PANEL_HI;
    public static int LINE;
    public static int TEXT;
    public static int DIM;
    /** Decorative highlight colour (selected tab, curves, records) – user choice. */
    public static int ACCENT;
    public static boolean light;
    /** Changes on every apply(); caches of rendered UI compare against it. */
    public static int version;

    // status colours – fixed meaning
    public static final int OK = 0xFF3DDC84;
    public static final int WARN = 0xFFFF8A00;
    public static final int COLD = 0xFF4FA3FF;
    public static final int HOT = 0xFFFF4D4D;
    public static final int DEMO = 0xFFFFD24D;
    /** Text drawn on top of a status/accent fill. */
    public static final int ON_COLOR = 0xFF0B0D10;
    public static final int ON_HOT = 0xFFFFFFFF;

    public static final int THEME_DARK = 0;
    public static final int THEME_BLACK = 1;
    public static final int THEME_GRAPHITE = 2;
    public static final int THEME_LIGHT = 3;
    public static final int THEME_COUNT = 4;

    /** Surfaces per theme: BG, PANEL, PANEL_HI, LINE, TEXT, DIM. */
    private static final int[][] SURFACES = {
            {0xFF0B0D10, 0xFF161A20, 0xFF222832, 0xFF2C333D, 0xFFE8EAED, 0xFF8A9099}, // dark (default)
            {0xFF000000, 0xFF0E0F12, 0xFF1A1C21, 0xFF26292F, 0xFFF2F3F5, 0xFF80868F}, // black
            {0xFF1C1F24, 0xFF272B32, 0xFF343A43, 0xFF424955, 0xFFEDEFF2, 0xFF9AA1AB}, // graphite
            {0xFFE6E9ED, 0xFFFFFFFF, 0xFFD9DEE4, 0xFFC2C9D1, 0xFF15181C, 0xFF5A6470}, // light
    };

    public static final int SURFACE_PANEL = 1;
    public static final int SURFACE_TEXT = 4;

    /** A surface colour of any theme (for drawing theme previews). */
    public static int surface(int theme, int which) {
        return SURFACES[theme >= 0 && theme < THEME_COUNT ? theme : THEME_DARK][which];
    }

    /** Accent choices: orange (default), red, gold, green, teal, blue, purple. */
    public static final int[] ACCENTS = {
            0xFFFF8A00, 0xFFE8453C, 0xFFD9A400, 0xFF6CC24A, 0xFF16B8A6, 0xFF3D8BFF, 0xFF9C6BFF,
    };

    static {
        apply(THEME_DARK, 0);
    }

    /** Switches theme and accent; takes effect on the next frame. Out-of-range values fall back to defaults. */
    public static void apply(int theme, int accent) {
        int[] s = SURFACES[theme >= 0 && theme < THEME_COUNT ? theme : THEME_DARK];
        BG = s[0];
        PANEL = s[1];
        PANEL_HI = s[2];
        LINE = s[3];
        TEXT = s[4];
        DIM = s[5];
        light = theme == THEME_LIGHT;
        int a = ACCENTS[accent >= 0 && accent < ACCENTS.length ? accent : 0];
        // on a light background the accent is darkened so text in it stays readable
        ACCENT = light ? scale(a, 0.72f) : a;
        version++;
    }

    /** Colour with its RGB multiplied by k (alpha kept). */
    static int scale(int color, float k) {
        int r = Math.round(((color >> 16) & 0xFF) * k);
        int g = Math.round(((color >> 8) & 0xFF) * k);
        int b = Math.round((color & 0xFF) * k);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Same colour with a new alpha (0..255). */
    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Turbo cool-down urgency: red = really wait, orange = should wait, green = (almost) done. */
    public static int turboColor(int level) {
        return level >= 2 ? HOT : level == 1 ? WARN : OK;
    }

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

    /** Greedy word wrap into lines no wider than maxWidth. */
    public static java.util.List<String> wrap(Paint p, String str, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        String[] words = str.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
            if (line.length() > 0 && p.measureText(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(words[i]);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
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
