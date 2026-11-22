package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import pl.q50.app.data.Channel;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.Fmt;
import pl.q50.app.data.SignalDef;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Settings;
import pl.q50.app.logic.TyreMonitor;

/**
 * Big-number overview of the mapped signals. Tiles can be hidden in the settings; the grid
 * re-flows so the remaining tiles get bigger.
 */
final class OverviewPage implements Page {
    private static final String TYRE_TILE = "tyres";
    /** Same order as Strings.tyreShort: FL, FR, RL, RR. */
    private static final String[] TYRES = {"tpms_fl", "tpms_fr", "tpms_rl", "tpms_rr"};

    private final DataHub hub;
    private final Settings settings;
    private final String[] visible = new String[Settings.TILES.length];

    OverviewPage(DataHub hub, Settings settings) {
        this.hub = hub;
        this.settings = settings;
    }

    public String title() {
        return Strings.get().tabOverview;
    }

    public void onShow() {
    }

    public boolean onTap(float x, float y) {
        return false;
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        float top = a.top;
        if (hub.demoActive()) {
            th.panel(c, a.left, top, a.right, top + th.px(28), 0x33FFD24D);
            c.drawText(Strings.get().demoBanner,
                    a.centerX(), top + th.px(20), th.bold(15, Theme.DEMO, Paint.Align.CENTER));
            top += th.px(34);
        } else if (hub.vehicleCount() == 0) {
            c.drawText(Strings.get().noVehicleSensors, a.centerX(), top + th.px(20), th.bold(15, Theme.HOT, Paint.Align.CENTER));
            top += th.px(34);
        }
        int n = 0;
        for (int i = 0; i < Settings.TILES.length; i++) {
            if (settings.isTileVisible(Settings.TILES[i])) visible[n++] = Settings.TILES[i];
        }
        if (n == 0) {
            return;
        }
        int cols = columns(n);
        int rows = (n + cols - 1) / cols;
        float gap = th.px(8);
        float w = (a.width() - gap * (cols - 1)) / cols;
        float h = (a.bottom - top - gap * (rows - 1)) / rows;
        float valueSize = Math.max(30f, Math.min(96f, Math.min(h / th.s * 0.42f, w / th.s * 0.3f)));
        for (int i = 0; i < n; i++) {
            float l = a.left + (i % cols) * (w + gap);
            float t = top + (i / cols) * (h + gap);
            if (TYRE_TILE.equals(visible[i])) {
                drawTyres(c, th, l, t, l + w, t + h, valueSize * 0.55f);
            } else {
                drawTile(c, th, hub.signal(visible[i]), visible[i], l, t, l + w, t + h, valueSize);
            }
        }
    }

    /** Grid width for n tiles on a wide screen. */
    static int columns(int n) {
        if (n <= 3) return n;
        if (n == 4) return 2;
        if (n <= 6) return 3;
        if (n <= 8) return 4;
        if (n == 9) return 3;
        return 4;
    }

    private void drawTile(Canvas c, Theme th, Channel ch, String key, float l, float t, float r, float b, float size) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        SignalDef d = ch == null ? null : ch.def;
        String label = d == null ? key.toUpperCase() : d.label();
        c.drawText(label, l + th.px(10), t + th.px(22), th.text(14, Theme.DIM, Paint.Align.LEFT));
        if (ch == null || !ch.hasValue()) {
            c.drawText("—", (l + r) / 2, (t + b) / 2 + th.px(size * 0.35f), th.bold(size, Theme.LINE, Paint.Align.CENTER));
            c.drawText(ch == null ? Strings.get().noSensor : Strings.get().noData, (l + r) / 2, b - th.px(10),
                    th.text(12, Theme.DIM, Paint.Align.CENTER));
            return;
        }
        int color = Theme.TEXT;
        float v = ch.value();
        if (!Float.isNaN(d.hot) && v >= d.hot) {
            color = Theme.HOT;
        } else if (!Float.isNaN(d.cold) && v < d.cold) {
            color = Theme.COLD;
        }
        c.drawText(ch.formatted(), (l + r) / 2, (t + b) / 2 + th.px(size * 0.37f), th.bold(size, color, Paint.Align.CENTER));
        c.drawText(d.unit(), r - th.px(10), b - th.px(10), th.text(14, Theme.DIM, Paint.Align.RIGHT));
        if (!"gear".equals(d.format)) {
            String range = "max " + d.formatRaw(ch.max);
            if (d.key.startsWith("g_")) {
                range = "min " + d.formatRaw(ch.min) + "  max " + d.formatRaw(ch.max);
            }
            c.drawText(range, l + th.px(10), b - th.px(10), th.text(12, Theme.DIM, Paint.Align.LEFT));
        }
    }

    private void drawTyres(Canvas c, Theme th, float l, float t, float r, float b, float size) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        c.drawText(Strings.get().tyres, l + th.px(10), t + th.px(22), th.text(14, Theme.DIM, Paint.Align.LEFT));
        c.drawText("bar", r - th.px(10), b - th.px(10), th.text(14, Theme.DIM, Paint.Align.RIGHT));
        float cx = (l + r) / 2;
        float top = t + th.px(28);
        float bottom = b - th.px(22);
        float rowH = (bottom - top) / 2;
        // value + label must fit in a row: value takes ~70 %, the label below it the rest
        float vs = Math.min(size, rowH / th.s * 0.55f);
        float ls = Math.max(9f, Math.min(12f, rowH / th.s * 0.22f));
        for (int i = 0; i < TYRES.length; i++) {
            Channel ch = hub.signal(TYRES[i]);
            float x = i % 2 == 0 ? (l + cx) / 2 : (cx + r) / 2;
            float rowTop = top + (i / 2) * rowH;
            float baseline = rowTop + th.px(vs) * 0.95f;
            String val = ch == null || !ch.hasValue() ? "—" : Fmt.number(ch.value(), 2);
            boolean low = ch != null && ch.hasValue() && ch.value() < TyreMonitor.LOW_BAR;
            c.drawText(val, x, baseline, th.bold(vs, low ? Theme.HOT : Theme.TEXT, Paint.Align.CENTER));
            c.drawText(Strings.get().tyreShort[i], x, baseline + th.px(ls) + th.px(3), th.text(ls, Theme.DIM, Paint.Align.CENTER));
        }
    }
}
