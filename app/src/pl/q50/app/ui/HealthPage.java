package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.OilPressureMonitor;
import pl.q50.app.logic.Snapshot;
import pl.q50.app.logic.TurboCooldown;
import pl.q50.app.logic.TyreMonitor;
import pl.q50.app.logic.WarmUp;

/** Engine health: warm-up, turbo cool-down, oil pressure vs. learned baseline, tyres. */
final class HealthPage implements Page {
    private final Analytics an;

    HealthPage(Analytics analytics) {
        this.an = analytics;
    }

    public String title() {
        return Strings.get().tabHealth;
    }

    public void onShow() {
    }

    public boolean onTap(float x, float y) {
        return false;
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        float gap = th.px(8);
        float w = (a.width() - gap) / 2;
        float h = (a.height() - gap) / 2;
        Snapshot s = an.last;
        drawWarmUp(c, th, a.left, a.top, a.left + w, a.top + h, s, now);
        drawTurbo(c, th, a.left + w + gap, a.top, a.right, a.top + h);
        drawOil(c, th, a.left, a.top + h + gap, a.left + w, a.bottom, s);
        drawTyres(c, th, a.left + w + gap, a.top + h + gap, a.right, a.bottom, s);
    }

    private static void title(Canvas c, Theme th, String text, float l, float t) {
        c.drawText(text, l + th.px(12), t + th.px(22), th.bold(14, Theme.DIM, Paint.Align.LEFT));
    }

    private static void bar(Canvas c, Theme th, float l, float t, float r, float frac, int color) {
        th.panel(c, l, t, r, t + th.px(12), Theme.LINE);
        float f = Math.max(0f, Math.min(1f, frac));
        if (f > 0) th.panel(c, l, t, l + (r - l) * f, t + th.px(12), color);
    }

    private void drawWarmUp(Canvas c, Theme th, float l, float t, float r, float b, Snapshot s, long now) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.warmUpTitle, l, t);
        WarmUp w = an.warmUp;
        int state = w.state();
        int color = state == WarmUp.COLD ? Theme.COLD : state == WarmUp.WARMING ? Theme.ACCENT : Theme.OK;
        String oil = s == null || Float.isNaN(s.oilTemp) ? "—" : Fmt.number(s.oilTemp, 0) + " °C";
        c.drawText(oil, l + th.px(12), t + th.px(64), th.bold(36, state == WarmUp.UNKNOWN ? Theme.LINE : color, Paint.Align.LEFT));
        String label = state == WarmUp.COLD ? tr.stateCold : state == WarmUp.WARMING ? tr.stateWarming
                : state == WarmUp.WARM ? tr.stateWarm : tr.noData;
        Paint lp = th.bold(15, color, Paint.Align.LEFT);
        c.drawText(Theme.fit(lp, label, r - l - th.px(150)), l + th.px(140), t + th.px(58), lp);
        bar(c, th, l + th.px(12), t + th.px(76), r - th.px(12), w.progress(), color);

        Paint k = th.text(13, Theme.DIM, Paint.Align.LEFT);
        Paint v = th.bold(13, Theme.TEXT, Paint.Align.RIGHT);
        float y = t + th.px(112);
        if (!w.coldStart()) {
            c.drawText(Theme.fit(k, tr.warmStart, r - l - th.px(24)), l + th.px(12), y, k);
        } else {
            c.drawText(tr.coolantWarmAfter, l + th.px(12), y, k);
            c.drawText(w.coolantWarmMs < 0 ? "…" : Fmt.duration(w.coolantWarmMs), r - th.px(12), y, v);
            y += th.px(20);
            c.drawText(tr.oilWarmAfter, l + th.px(12), y, k);
            c.drawText(w.oilWarmMs < 0 ? "…" : Fmt.duration(w.oilWarmMs), r - th.px(12), y, v);
        }
    }

    private void drawTurbo(Canvas c, Theme th, float l, float t, float r, float b) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.turboTitle, l, t);
        TurboCooldown tc = an.turbo;
        boolean need = tc.needsCooldown();
        int color = need ? Theme.ACCENT : Theme.OK;
        String big = need ? tr.turboSeconds(Math.round(tc.remainingS())) : tr.turboOk;
        Paint bp = th.bold(30, color, Paint.Align.LEFT);
        c.drawText(Theme.fit(bp, big, r - l - th.px(24)), l + th.px(12), t + th.px(64), bp);
        bar(c, th, l + th.px(12), t + th.px(76), r - th.px(12), tc.fraction(), color);
        Paint k = th.text(13, Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(k, need ? tr.turboCooling : tr.turboHint, r - l - th.px(24)), l + th.px(12), t + th.px(112), k);
    }

    private void drawOil(Canvas c, Theme th, float l, float t, float r, float b, Snapshot s) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.oilPressTitle, l, t);
        OilPressureMonitor m = an.oil;
        float press = s == null ? Float.NaN : s.oilPress;
        float rpm = s == null ? Float.NaN : s.rpm;
        float expected = Float.isNaN(rpm) ? Float.NaN : m.expected(rpm);
        boolean low = s != null && (m.belowAbsolute(s) || m.belowBaseline(s));
        String now = Float.isNaN(press) ? "—" : Fmt.number(press, 1) + " bar";
        c.drawText(now, r - th.px(12), t + th.px(22), th.bold(16, low ? Theme.HOT : Theme.TEXT, Paint.Align.RIGHT));

        // chart: learned baseline per 500 rpm bin + current point
        float cl = l + th.px(12);
        float cr = r - th.px(12);
        float ct = t + th.px(34);
        float cb = b - th.px(30);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
        c.drawRect(cl, ct, cr, cb, th.stroke);
        float maxBar = 6f;
        for (int i = 0; i < OilPressureMonitor.BINS; i++) {
            if (m.count[i] > 0) maxBar = Math.max(maxBar, m.mean[i] * 1.2f);
        }
        if (!Float.isNaN(press)) maxBar = Math.max(maxBar, press * 1.2f);
        float binW = (cr - cl) / OilPressureMonitor.BINS;
        int learned = 0;
        for (int i = 0; i < OilPressureMonitor.BINS; i++) {
            if (m.count[i] == 0) continue;
            boolean trusted = m.count[i] >= OilPressureMonitor.MIN_SAMPLES;
            if (trusted) learned++;
            float y = cb - (cb - ct) * (m.mean[i] / maxBar);
            th.fill.setColor(trusted ? 0xFF3A4452 : 0xFF262C35);
            c.drawRect(cl + i * binW + th.px(1), y, cl + (i + 1) * binW - th.px(1), cb, th.fill);
        }
        if (!Float.isNaN(press) && !Float.isNaN(rpm)) {
            float x = cl + (cr - cl) * Math.min(1f, rpm / (OilPressureMonitor.BIN * OilPressureMonitor.BINS));
            float y = cb - (cb - ct) * Math.min(1f, press / maxBar);
            th.fill.setColor(low ? Theme.HOT : Theme.OK);
            c.drawCircle(x, y, th.px(5), th.fill);
        }
        Paint k = th.text(12, Theme.DIM, Paint.Align.LEFT);
        String info = !Float.isNaN(expected)
                ? tr.normal + " " + Fmt.number(expected, 1) + " bar @ " + Fmt.number(rpm, 0) + " rpm"
                : tr.oilLearning(learned, OilPressureMonitor.BINS);
        c.drawText(Theme.fit(k, info, r - l - th.px(24)), l + th.px(12), b - th.px(10), k);
    }

    private void drawTyres(Canvas c, Theme th, float l, float t, float r, float b, Snapshot s) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.tyresTitle, l, t);
        c.drawText(tr.vsAverage, r - th.px(12), t + th.px(22), th.text(12, Theme.DIM, Paint.Align.RIGHT));
        float[] p = s == null ? null : s.tyres;
        float cx = (l + r) / 2;
        float top = t + th.px(34);
        float rowH = (b - th.px(30) - top) / 2;
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? (l + cx) / 2 : (cx + r) / 2;
            float y = top + (i / 2) * rowH + rowH * 0.5f;
            float v = p == null ? Float.NaN : p[i];
            boolean low = !Float.isNaN(v) && v < TyreMonitor.LOW_BAR;
            c.drawText(Float.isNaN(v) ? "—" : Fmt.number(v, 2), x, y, th.bold(22, low ? Theme.HOT : Theme.TEXT, Paint.Align.CENTER));
            float dev = p == null ? Float.NaN : TyreMonitor.deviation(p, i);
            String d = Float.isNaN(dev) ? "" : (dev >= 0 ? "+" : "") + Fmt.number(dev, 2);
            int dc = !Float.isNaN(dev) && dev < -TyreMonitor.ASYM_BAR ? Theme.HOT : Theme.DIM;
            c.drawText(tr.tyreShort[i] + "  " + d, x, y + th.px(16), th.text(12, dc, Paint.Align.CENTER));
        }
        TyreMonitor tm = an.tyres;
        String leak = tm.leakTyre >= 0
                ? tr.alertTyreLeak(tr.tyreShort[tm.leakTyre], Fmt.number(tm.leakDropBar, 2), tm.leakDays)
                : tr.noLeak + " · " + tr.tyreSessions(tm.history().size());
        Paint k = th.text(12, tm.leakTyre >= 0 ? Theme.ACCENT : Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(k, leak, r - l - th.px(24)), l + th.px(12), b - th.px(10), k);
    }
}
