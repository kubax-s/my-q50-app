package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.Dyno;
import pl.q50.app.logic.OilPressureMonitor;
import pl.q50.app.logic.PerfTimer;
import pl.q50.app.logic.Snapshot;
import pl.q50.app.logic.TurboCooldown;
import pl.q50.app.logic.TyreMonitor;
import pl.q50.app.logic.WarmUp;

/** Reusable dashboard panels; pages lay them out. Buttons are reported via out-rects. */
public final class Panels {
    private static final long FLASH_MS = 5000;
    private static final Path PATH = new Path();

    private Panels() {
    }

    static void title(Canvas c, Theme th, String text, float l, float t) {
        c.drawText(text, l + th.px(12), t + th.px(22), th.bold(14, Theme.DIM, Paint.Align.LEFT));
    }

    static void bar(Canvas c, Theme th, float l, float t, float r, float frac, int color) {
        th.panel(c, l, t, r, t + th.px(12), Theme.LINE);
        float f = Math.max(0f, Math.min(1f, frac));
        if (f > 0) th.panel(c, l, t, l + (r - l) * f, t + th.px(12), color);
    }

    /** Formatted run result: time, quarter-mile time @ trap speed, or braking distance. */
    public static String result(int run, PerfTimer.Result r) {
        if (r.timeMs < 0) return "—";
        String time = Fmt.number(r.timeMs / 1000f, 2) + " s";
        if (run == PerfTimer.QUARTER && !Float.isNaN(r.extra)) {
            return time + " @ " + Fmt.number(r.extra, 0);
        }
        if (run == PerfTimer.BRAKE_100_0 && !Float.isNaN(r.extra)) {
            return Fmt.number(r.extra, 1) + " m";
        }
        return time;
    }

    static void warmUp(Canvas c, Theme th, Analytics an, float l, float t, float r, float b) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.warmUpTitle, l, t);
        Snapshot s = an.last;
        WarmUp w = an.warmUp;
        int state = w.state();
        int color = state == WarmUp.COLD ? Theme.COLD : state == WarmUp.WARMING ? Theme.ACCENT : Theme.OK;
        String oil = s == null || Float.isNaN(s.oilTemp) ? "—" : Fmt.number(s.oilTemp, 0) + " °C";
        Paint op = th.bold(36, state == WarmUp.UNKNOWN ? Theme.LINE : color, Paint.Align.LEFT);
        c.drawText(oil, l + th.px(12), t + th.px(64), op);
        float labelX = l + th.px(12) + op.measureText(oil) + th.px(14);
        String label = state == WarmUp.COLD ? tr.stateCold : state == WarmUp.WARMING ? tr.stateWarming
                : state == WarmUp.WARM ? tr.stateWarm : tr.noData;
        Paint lp = th.bold(15, color, Paint.Align.LEFT);
        c.drawText(Theme.fit(lp, label, r - labelX - th.px(12)), labelX, t + th.px(60), lp);
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

    static void turbo(Canvas c, Theme th, Analytics an, float l, float t, float r, float b) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.turboTitle, l, t);
        TurboCooldown tc = an.turbo;
        float x0 = l + th.px(12);
        float x1 = r - th.px(12);
        boolean need = tc.needsCooldown();
        int level = tc.level();
        int color = need ? Theme.turboColor(level) : Theme.OK;

        // headline: seconds in the zone colour (to "OK" while red, to fully cooled otherwise)
        int secs = (int) Math.ceil(level >= 2 ? tc.secondsUntilOk() : tc.remainingS());
        Paint hp = th.bold(30, color, Paint.Align.LEFT);
        c.drawText(Theme.fit(hp, need ? secs + " s" : tr.turboOk, x1 - x0), x0, t + th.px(60), hp);

        turboBar(c, th, tc, x0, t + th.px(72), x1, t + th.px(90), need);

        Paint k = th.text(13, Theme.DIM, Paint.Align.LEFT);
        String line;
        if (!need) {
            line = tr.turboHint;
        } else if (level >= 2) {
            line = tr.turboOkIn((int) Math.ceil(tc.secondsUntilOk())) + " · " + tr.turboFullIn((int) Math.ceil(tc.remainingS()));
        } else {
            line = tr.turboFullIn((int) Math.ceil(tc.remainingS()));
        }
        c.drawText(Theme.fit(k, line, x1 - x0), x0, t + th.px(112), k);
        if (tc.oilSlowsCooling()) {
            Paint op = th.text(13, Theme.ACCENT, Paint.Align.LEFT);
            c.drawText(Theme.fit(op, tr.turboHotOil(Fmt.number(tc.oilTemp(), 0)), x1 - x0), x0, t + th.px(132), op);
        }
    }

    /**
     * One bar, three zones laid out along the cool-down: red (don't switch off), orange (OK),
     * green (extra margin). Time runs left to right; the part already waited is dimmed and a
     * marker shows "now".
     */
    private static void turboBar(Canvas c, Theme th, TurboCooldown tc, float x0, float top, float x1, float bottom,
                                 boolean need) {
        float w = x1 - x0;
        float scale = tc.scaleS();
        float remaining = need ? tc.remainingS() : 0f;
        float xRed = x0 + w * (1f - Math.min(scale, TurboCooldown.RED_S) / scale);
        float xOrange = x0 + w * (1f - Math.min(scale, TurboCooldown.ORANGE_S) / scale);
        float xNow = x0 + w * (1f - remaining / scale);
        if (!need) {
            th.panel(c, x0, top, x1, bottom, Theme.LINE);
            return;
        }
        zone(c, th, x0, xRed, top, bottom, Theme.HOT, xNow);
        zone(c, th, xRed, xOrange, top, bottom, Theme.ACCENT, xNow);
        zone(c, th, xOrange, x1, top, bottom, Theme.OK, xNow);
        // "now" marker
        th.fill.setColor(Theme.TEXT);
        c.drawRect(xNow - th.px(1.5f), top - th.px(4), xNow + th.px(1.5f), bottom + th.px(4), th.fill);
    }

    private static void zone(Canvas c, Theme th, float from, float to, float top, float bottom, int color, float xNow) {
        if (to <= from) return;
        // waited part: dimmed
        float split = Math.max(from, Math.min(to, xNow));
        if (split > from) {
            th.fill.setColor((color & 0x00FFFFFF) | 0x40000000);
            c.drawRect(from, top, split, bottom, th.fill);
        }
        if (to > split) {
            th.fill.setColor(color);
            c.drawRect(split, top, to, bottom, th.fill);
        }
    }

    static void oil(Canvas c, Theme th, Analytics an, float l, float t, float r, float b) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.oilPressTitle, l, t);
        Snapshot s = an.last;
        OilPressureMonitor m = an.oil;
        float press = s == null ? Float.NaN : s.oilPress;
        float rpm = s == null ? Float.NaN : s.rpm;
        float expected = Float.isNaN(rpm) ? Float.NaN : m.expected(rpm);
        boolean low = s != null && (m.belowAbsolute(s) || m.belowBaseline(s));
        String now = Float.isNaN(press) ? "—" : Fmt.number(press, 1) + " bar";
        c.drawText(now, r - th.px(12), t + th.px(22), th.bold(16, low ? Theme.HOT : Theme.TEXT, Paint.Align.RIGHT));

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
        Paint ax = th.text(10, Theme.DIM, Paint.Align.CENTER);
        for (int k1 = 2000; k1 <= 6000; k1 += 2000) {
            float x = cl + (cr - cl) * k1 / (float) (OilPressureMonitor.BIN * OilPressureMonitor.BINS);
            c.drawText((k1 / 1000) + "k", x, cb - th.px(3), ax);
        }
        c.drawText(Fmt.number(maxBar, 0) + " bar", cl + th.px(4), ct + th.px(12), th.text(10, Theme.DIM, Paint.Align.LEFT));
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

    static void tyres(Canvas c, Theme th, Analytics an, float l, float t, float r, float b) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.tyresTitle, l, t);
        c.drawText(tr.vsAverage, r - th.px(12), t + th.px(22), th.text(12, Theme.DIM, Paint.Align.RIGHT));
        Snapshot s = an.last;
        float[] p = s == null ? null : s.tyres;
        float cx = (l + r) / 2;
        float top = t + th.px(30);
        float rowH = (b - th.px(26) - top) / 2;
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? (l + cx) / 2 : (cx + r) / 2;
            float y = top + (i / 2) * rowH + rowH * 0.55f;
            float v = p == null ? Float.NaN : p[i];
            boolean low = !Float.isNaN(v) && v < TyreMonitor.LOW_BAR;
            c.drawText(Float.isNaN(v) ? "—" : Fmt.number(v, 2), x, y, th.bold(20, low ? Theme.HOT : Theme.TEXT, Paint.Align.CENTER));
            float dev = p == null ? Float.NaN : TyreMonitor.deviation(p, i);
            String d = Float.isNaN(dev) ? "" : (dev >= 0 ? "+" : "") + Fmt.number(dev, 2);
            int dc = !Float.isNaN(dev) && dev < -TyreMonitor.ASYM_BAR ? Theme.HOT : Theme.DIM;
            c.drawText(tr.tyreShort[i] + "  " + d, x, y + th.px(15), th.text(12, dc, Paint.Align.CENTER));
        }
        TyreMonitor tm = an.tyres;
        String leak = tm.leakTyre >= 0
                ? tr.alertTyreLeak(tr.tyreShort[tm.leakTyre], Fmt.number(tm.leakDropBar, 2), tm.leakDays)
                : tr.noLeak + " · " + tr.tyreSessions(tm.history().size());
        Paint k = th.text(12, tm.leakTyre >= 0 ? Theme.ACCENT : Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(k, leak, r - l - th.px(24)), l + th.px(12), b - th.px(9), k);
    }

    static void dyno(Canvas c, Theme th, Analytics an, float l, float t, float r, float b, RectF resetOut) {
        Strings tr = Strings.get();
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.dynoTitle, l, t);
        resetOut.set(r - th.px(120), t + th.px(6), r - th.px(8), t + th.px(32));
        th.button(c, resetOut, tr.resetDyno, Theme.PANEL_HI, Theme.TEXT, 12);

        Dyno d = an.dyno;
        float cl = l + th.px(36);
        float cr = r - th.px(36);
        float ct = t + th.px(62);
        float cb = b - th.px(26);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
        c.drawRect(cl, ct, cr, cb, th.stroke);
        Paint ax = th.text(11, Theme.DIM, Paint.Align.CENTER);
        for (int rpm = 2000; rpm < Dyno.RPM_MAX; rpm += 1000) {
            float x = cl + (cr - cl) * (rpm - Dyno.RPM_MIN) / (float) (Dyno.RPM_MAX - Dyno.RPM_MIN);
            c.drawLine(x, cb, x, cb + th.px(4), th.stroke);
            c.drawText(Integer.toString(rpm / 1000), x, cb + th.px(16), ax);
        }
        c.drawText("×1000", cr + th.px(30), cb + th.px(16), th.text(11, Theme.DIM, Paint.Align.RIGHT));

        if (d.isEmpty()) {
            Paint ep = th.text(13, Theme.DIM, Paint.Align.CENTER);
            c.drawText(Theme.fit(ep, tr.dynoEmpty, cr - cl - th.px(10)), (cl + cr) / 2, (ct + cb) / 2, ep);
            return;
        }
        float maxP = 50;
        float maxT = 50;
        for (int i = 0; i < Dyno.BINS; i++) {
            if (!Float.isNaN(d.power[i])) maxP = Math.max(maxP, d.power[i]);
            if (!Float.isNaN(d.torque[i])) maxT = Math.max(maxT, d.torque[i]);
        }
        maxP *= 1.1f;
        maxT *= 1.1f;
        curve(c, th, d.power, maxP, cl, ct, cr, cb, Theme.ACCENT);
        curve(c, th, d.torque, maxT, cl, ct, cr, cb, Theme.COLD);
        c.drawText(Fmt.number(maxP, 0), cl - th.px(4), ct + th.px(10), th.text(11, Theme.ACCENT, Paint.Align.RIGHT));
        c.drawText(Fmt.number(maxT, 0), cr + th.px(4), ct + th.px(10), th.text(11, Theme.COLD, Paint.Align.LEFT));

        int pp = Dyno.peak(d.power);
        int pt = Dyno.peak(d.torque);
        if (pp >= 0) {
            c.drawText(Fmt.number(d.power[pp], 0) + " " + (tr.en ? "PS" : "KM") + " @ " + Dyno.binCenterRpm(pp),
                    l + th.px(12), t + th.px(48), th.bold(13, Theme.ACCENT, Paint.Align.LEFT));
        }
        if (pt >= 0) {
            c.drawText(Fmt.number(d.torque[pt], 0) + " Nm @ " + Dyno.binCenterRpm(pt), (l + r) / 2, t + th.px(48),
                    th.bold(13, Theme.COLD, Paint.Align.LEFT));
        }
    }

    private static void curve(Canvas c, Theme th, float[] values, float max, float cl, float ct, float cr, float cb, int color) {
        PATH.reset();
        boolean started = false;
        th.fill.setColor(color);
        for (int i = 0; i < values.length; i++) {
            if (Float.isNaN(values[i])) {
                started = false;
                continue;
            }
            float x = cl + (cr - cl) * (Dyno.binCenterRpm(i) - Dyno.RPM_MIN) / (float) (Dyno.RPM_MAX - Dyno.RPM_MIN);
            float y = cb - (cb - ct) * values[i] / max;
            if (started) {
                PATH.lineTo(x, y);
            } else {
                PATH.moveTo(x, y);
                started = true;
            }
            c.drawCircle(x, y, th.px(2.5f), th.fill);
        }
        th.stroke.setColor(color);
        th.stroke.setStrokeWidth(th.px(2));
        c.drawPath(PATH, th.stroke);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
    }

    /** Timing table with last/best results; the reset button sits in the title row. */
    static void perf(Canvas c, Theme th, Analytics an, float l, float t, float r, float b, long now, RectF resetOut) {
        Strings tr = Strings.get();
        PerfTimer p = an.perf;
        th.panel(c, l, t, r, b, Theme.PANEL);
        title(c, th, tr.perfTitle, l, t);
        resetOut.set(r - th.px(150), t + th.px(6), r - th.px(8), t + th.px(32));
        th.button(c, resetOut, tr.resetRecords, Theme.PANEL_HI, Theme.TEXT, 12);

        float w = r - l;
        float cName = l + th.px(12);
        float cLast = l + w * 0.36f;
        float cBest = l + w * 0.68f;
        float y = t + th.px(50);
        Paint hp = th.text(11, Theme.DIM, Paint.Align.LEFT);
        c.drawText(tr.colLast, cLast, y, hp);
        c.drawText(tr.colBest, cBest, y, hp);
        float rowH = Math.min(th.px(30), (b - y - th.px(6)) / PerfTimer.COUNT);
        for (int run = 0; run < PerfTimer.COUNT; run++) {
            y += rowH;
            boolean flash = p.lastCompleted == run && now - p.lastCompletedAt < FLASH_MS;
            if (flash) {
                th.panel(c, l + th.px(4), y - rowH * 0.72f, r - th.px(4), y + rowH * 0.28f, Theme.PANEL_HI);
            }
            c.drawText(tr.runNames[run], cName, y, th.bold(14, Theme.TEXT, Paint.Align.LEFT));
            Paint vp;
            String last;
            if (p.isRunning(run)) {
                last = Fmt.number(p.elapsedMs(run, now) / 1000f, 1) + " s…";
                vp = th.bold(14, Theme.ACCENT, Paint.Align.LEFT);
            } else {
                last = result(run, p.last[run]);
                vp = th.bold(14, flash ? Theme.OK : Theme.TEXT, Paint.Align.LEFT);
            }
            c.drawText(Theme.fit(vp, last, cBest - cLast - th.px(6)), cLast, y, vp);
            Paint bp = th.bold(14, Theme.ACCENT, Paint.Align.LEFT);
            c.drawText(Theme.fit(bp, result(run, p.best[run]), r - cBest - th.px(8)), cBest, y, bp);
        }
    }

    /** Vertical bars of time shares. */
    static void timeBars(Canvas c, Theme th, String title, String[] names, long[] ms, float l, float t, float r, float b) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        Paint tp = th.bold(12, Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(tp, title, r - l - th.px(20)), l + th.px(10), t + th.px(18), tp);
        long total = 0;
        for (int i = 0; i < ms.length; i++) total += ms[i];
        float cl = l + th.px(8);
        float cr = r - th.px(8);
        float ct = t + th.px(34);
        float cb = b - th.px(20);
        float slot = (cr - cl) / names.length;
        Paint np = th.text(10, Theme.DIM, Paint.Align.CENTER);
        Paint pp = th.bold(10, Theme.TEXT, Paint.Align.CENTER);
        for (int i = 0; i < names.length; i++) {
            float share = total > 0 ? ms[i] / (float) total : 0f;
            float x0 = cl + i * slot + th.px(2);
            float x1 = cl + (i + 1) * slot - th.px(2);
            float top = cb - (cb - ct - th.px(12)) * share;
            th.fill.setColor(share > 0 ? Theme.ACCENT : Theme.LINE);
            c.drawRect(x0, share > 0 ? top : cb - th.px(2), x1, cb, th.fill);
            c.drawText(names[i], (x0 + x1) / 2, cb + th.px(13), np);
            if (share >= 0.005f) {
                c.drawText(Fmt.number(share * 100, 0), (x0 + x1) / 2, top - th.px(3), pp);
            }
        }
    }
}
