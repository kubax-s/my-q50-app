package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.GMeter;

/**
 * Friction circle: live dot with a 5 s trail, the envelope of the furthest point reached in each
 * direction, and the maxima. Lateral + is drawn to the right, acceleration (+) upwards.
 */
final class GMeterPage implements Page {
    private static final float SCALE_G = 1.2f;

    private final GMeter g;
    private final RectF resetBtn = new RectF();
    private final Path path = new Path();

    GMeterPage(GMeter gMeter) {
        this.g = gMeter;
    }

    public String title() {
        return Strings.get().tabGMeter;
    }

    public long refreshMs() {
        return 100;
    }

    public void onShow() {
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();
        float size = Math.min(a.height(), a.width() * 0.55f);
        float l = a.left;
        th.panel(c, l, a.top, l + size, a.top + size, Theme.PANEL);
        float cx = l + size / 2;
        float cy = a.top + size / 2;
        float radius = size / 2 - th.px(26);
        float perG = radius / SCALE_G;

        // rings and axes
        th.stroke.setStrokeWidth(th.px(1));
        Paint rl = th.text(10, Theme.DIM, Paint.Align.LEFT);
        for (int i = 1; i <= 4; i++) {
            float gv = i * 0.25f;
            th.stroke.setColor(i == 4 ? Theme.DIM : Theme.LINE);
            c.drawCircle(cx, cy, gv * perG, th.stroke);
            c.drawText(Fmt.number(gv, 2), cx + th.px(3), cy - gv * perG - th.px(2), rl);
        }
        th.stroke.setColor(Theme.LINE);
        c.drawLine(cx - radius, cy, cx + radius, cy, th.stroke);
        c.drawLine(cx, cy - radius, cx, cy + radius, th.stroke);
        Paint ax = th.bold(11, Theme.DIM, Paint.Align.CENTER);
        c.drawText(t.gAccelShort, cx, a.top + th.px(16), ax);
        c.drawText(t.gBrakeShort, cx, a.top + size - th.px(8), ax);
        c.drawText("−", l + th.px(12), cy + th.px(4), ax);
        c.drawText("+", l + size - th.px(12), cy + th.px(4), ax);

        // envelope of maxima per direction
        path.reset();
        boolean any = false;
        for (int i = 0; i <= GMeter.SECTORS; i++) {
            int s = i % GMeter.SECTORS;
            double ang = Math.toRadians((s + 0.5) * 360.0 / GMeter.SECTORS);
            float m = Math.min(SCALE_G, g.sectorMax[s]);
            float x = cx + (float) Math.cos(ang) * m * perG;
            float y = cy - (float) Math.sin(ang) * m * perG;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            if (g.sectorMax[s] > 0) any = true;
        }
        if (any) {
            th.fill.setColor(0x33FF8A00);
            c.drawPath(path, th.fill);
            th.stroke.setColor(0x99FF8A00);
            th.stroke.setStrokeWidth(th.px(1.5f));
            c.drawPath(path, th.stroke);
        }

        // trail and live dot
        int n = g.trailCount();
        for (int i = 0; i < n; i++) {
            float x = cx + clamp(g.trailLat(i)) * perG;
            float y = cy - clamp(g.trailLon(i)) * perG;
            int alpha = 40 + 160 * i / Math.max(1, n);
            th.fill.setColor((alpha << 24) | 0x00E8EAED);
            c.drawCircle(x, y, th.px(2.5f), th.fill);
        }
        if (!Float.isNaN(g.lat) && !Float.isNaN(g.lon)) {
            th.fill.setColor(Theme.ACCENT);
            c.drawCircle(cx + clamp(g.lat) * perG, cy - clamp(g.lon) * perG, th.px(9), th.fill);
        } else {
            c.drawText(t.gNoData, cx, cy + th.px(30), th.bold(14, Theme.DIM, Paint.Align.CENTER));
        }

        // numbers
        float pl = l + size + th.px(8);
        th.panel(c, pl, a.top, a.right, a.bottom, Theme.PANEL);
        float x = pl + th.px(14);
        float vr = a.right - th.px(14);
        Paint k = th.text(14, Theme.DIM, Paint.Align.LEFT);
        Paint big = th.bold(34, Theme.TEXT, Paint.Align.RIGHT);
        float y = a.top + th.px(40);
        c.drawText(t.gLateral, x, y, k);
        c.drawText(gv(g.lat), vr, y + th.px(4), big);
        y += th.px(46);
        c.drawText(t.gLongitudinal, x, y, k);
        c.drawText(gv(g.lon), vr, y + th.px(4), th.bold(34, Theme.TEXT, Paint.Align.RIGHT));
        y += th.px(38);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
        c.drawLine(x, y - th.px(14), vr, y - th.px(14), th.stroke);
        String[][] rows = {
                {t.gMaxLatPlus, gv(g.maxLatPlus)},
                {t.gMaxLatMinus, gv(g.maxLatMinus)},
                {t.gMaxAccel, gv(g.maxAccel)},
                {t.gMaxBrake, gv(g.maxBrake)},
                {t.gMaxCombined, gv(g.maxCombined)},
        };
        Paint v = th.bold(16, Theme.ACCENT, Paint.Align.RIGHT);
        float rowH = Math.min(th.px(28), (a.bottom - y - th.px(76)) / rows.length);
        for (int i = 0; i < rows.length; i++) {
            Paint kp = th.text(14, Theme.DIM, Paint.Align.LEFT);
            c.drawText(Theme.fit(kp, rows[i][0], vr - x - th.px(70)), x, y, kp);
            c.drawText(rows[i][1], vr, y, v);
            y += rowH;
        }
        Paint np = th.text(11, Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(np, t.gSignNote, vr - x), x, a.bottom - th.px(52), np);
        resetBtn.set(x, a.bottom - th.px(44), vr, a.bottom - th.px(8));
        th.button(c, resetBtn, t.resetG, Theme.PANEL_HI, Theme.TEXT, 14);
    }

    private static float clamp(float v) {
        return Math.max(-SCALE_G, Math.min(SCALE_G, v));
    }

    private static String gv(float v) {
        return Float.isNaN(v) ? "—" : Fmt.number(v, 2) + " g";
    }

    public boolean onTap(float x, float y) {
        if (resetBtn.contains(x, y)) {
            g.reset();
            return true;
        }
        return false;
    }
}
