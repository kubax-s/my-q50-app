package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import pl.q50.app.data.Channel;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.Fmt;
import pl.q50.app.data.SignalDef;
import pl.q50.app.i18n.Strings;

/** Big-number overview of the mapped signals: 4 × 3 tiles, the last one shows all four tyres. */
final class OverviewPage implements Page {
    private static final String[] TILES = {
            "rpm", "speed", "gear", "throttle",
            "coolant", "oil_temp", "oil_press", "torque",
            "power", "g_lat", "g_long", null, // null = tyre tile
    };
    /** Same order as Strings.tyreShort: FL, FR, RL, RR. */
    private static final String[] TYRES = {"tpms_fl", "tpms_fr", "tpms_rl", "tpms_rr"};

    private final DataHub hub;

    OverviewPage(DataHub hub) {
        this.hub = hub;
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
        float gap = th.px(8);
        int cols = 4;
        int rows = 3;
        float w = (a.width() - gap * (cols - 1)) / cols;
        float h = (a.bottom - top - gap * (rows - 1)) / rows;
        for (int i = 0; i < TILES.length; i++) {
            float l = a.left + (i % cols) * (w + gap);
            float t = top + (i / cols) * (h + gap);
            if (TILES[i] == null) {
                drawTyres(c, th, l, t, l + w, t + h);
            } else {
                drawTile(c, th, hub.signal(TILES[i]), TILES[i], l, t, l + w, t + h);
            }
        }
    }

    private void drawTile(Canvas c, Theme th, Channel ch, String key, float l, float t, float r, float b) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        SignalDef d = ch == null ? null : ch.def;
        String label = d == null ? key.toUpperCase() : d.label();
        c.drawText(label, l + th.px(10), t + th.px(22), th.text(14, Theme.DIM, Paint.Align.LEFT));
        if (ch == null || !ch.hasValue()) {
            c.drawText("—", (l + r) / 2, (t + b) / 2 + th.px(16), th.bold(44, Theme.LINE, Paint.Align.CENTER));
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
        c.drawText(ch.formatted(), (l + r) / 2, (t + b) / 2 + th.px(18), th.bold(48, color, Paint.Align.CENTER));
        c.drawText(d.unit(), r - th.px(10), b - th.px(10), th.text(14, Theme.DIM, Paint.Align.RIGHT));
        if (!"gear".equals(d.format)) {
            String range = "max " + d.formatRaw(ch.max);
            if (d.key.startsWith("g_")) {
                range = "min " + d.formatRaw(ch.min) + "  max " + d.formatRaw(ch.max);
            }
            c.drawText(range, l + th.px(10), b - th.px(10), th.text(12, Theme.DIM, Paint.Align.LEFT));
        }
    }

    private void drawTyres(Canvas c, Theme th, float l, float t, float r, float b) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        c.drawText(Strings.get().tyres, l + th.px(10), t + th.px(22), th.text(14, Theme.DIM, Paint.Align.LEFT));
        c.drawText("bar", r - th.px(10), b - th.px(10), th.text(14, Theme.DIM, Paint.Align.RIGHT));
        float cx = (l + r) / 2;
        float top = t + th.px(30);
        float rowH = (b - th.px(26) - top) / 2;
        for (int i = 0; i < TYRES.length; i++) {
            Channel ch = hub.signal(TYRES[i]);
            float x = i % 2 == 0 ? (l + cx) / 2 : (cx + r) / 2;
            float y = top + (i / 2) * rowH + rowH * 0.62f;
            String val = ch == null || !ch.hasValue() ? "—" : Fmt.number(ch.value(), 2);
            c.drawText(val, x, y, th.bold(26, Theme.TEXT, Paint.Align.CENTER));
            c.drawText(Strings.get().tyreShort[i], x, y + th.px(16), th.text(11, Theme.DIM, Paint.Align.CENTER));
        }
    }
}
