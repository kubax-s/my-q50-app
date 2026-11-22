package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.Dyno;
import pl.q50.app.logic.PerfTimer;

/** Automatic acceleration/braking timing and a power/torque curve from full-throttle pulls. */
public final class PerformancePage implements Page {
    private static final long FLASH_MS = 5000;

    private final Analytics an;
    private final AppHost host;
    private final RectF resetRecords = new RectF();
    private final RectF resetDyno = new RectF();
    private final Path path = new Path();

    PerformancePage(Analytics analytics, AppHost host) {
        this.an = analytics;
        this.host = host;
    }

    public String title() {
        return Strings.get().tabPerformance;
    }

    public void onShow() {
    }

    /** Formatted run result: time, quarter-mile trap speed, or braking distance. */
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

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();
        PerfTimer p = an.perf;
        float gap = th.px(8);
        float lw = (a.width() - gap) * 0.48f;
        float l = a.left;
        float r = a.left + lw;
        th.panel(c, l, a.top, r, a.bottom, Theme.PANEL);

        // status line
        boolean armed = p.armed();
        Paint sp = th.bold(15, armed ? Theme.OK : Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(sp, armed ? t.armed : t.notArmed, lw - th.px(24)), l + th.px(12), a.top + th.px(24), sp);

        // table
        float cName = l + th.px(12);
        float cLast = l + lw * 0.44f;
        float cBest = l + lw * 0.73f;
        float y = a.top + th.px(52);
        Paint hp = th.text(12, Theme.DIM, Paint.Align.LEFT);
        c.drawText(t.colLast, cLast, y, hp);
        c.drawText(t.colBest, cBest, y, hp);
        float rowH = th.px(34);
        for (int run = 0; run < PerfTimer.COUNT; run++) {
            y += rowH;
            boolean flash = p.lastCompleted == run && now - p.lastCompletedAt < FLASH_MS;
            if (flash) {
                th.panel(c, l + th.px(4), y - rowH * 0.7f, r - th.px(4), y + rowH * 0.3f, Theme.PANEL_HI);
            }
            c.drawText(t.runNames[run], cName, y, th.bold(15, Theme.TEXT, Paint.Align.LEFT));
            Paint vp;
            String last;
            if (p.isRunning(run)) {
                last = Fmt.number(p.elapsedMs(run, now) / 1000f, 1) + " s…";
                vp = th.bold(15, Theme.ACCENT, Paint.Align.LEFT);
            } else {
                last = result(run, p.last[run]);
                vp = th.bold(15, flash ? Theme.OK : Theme.TEXT, Paint.Align.LEFT);
            }
            c.drawText(Theme.fit(vp, last, cBest - cLast - th.px(6)), cLast, y, vp);
            Paint bp = th.bold(15, Theme.ACCENT, Paint.Align.LEFT);
            c.drawText(Theme.fit(bp, result(run, p.best[run]), r - cBest - th.px(8)), cBest, y, bp);
        }
        resetRecords.set(l + th.px(12), a.bottom - th.px(48), r - th.px(12), a.bottom - th.px(10));
        th.button(c, resetRecords, t.resetRecords, Theme.PANEL_HI, Theme.TEXT, 14);

        drawDyno(c, th, r + gap, a.top, a.right, a.bottom, t);
    }

    private void drawDyno(Canvas c, Theme th, float l, float t, float r, float b, Strings tr) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        c.drawText(tr.dynoTitle, l + th.px(12), t + th.px(22), th.bold(14, Theme.DIM, Paint.Align.LEFT));
        resetDyno.set(r - th.px(130), t + th.px(6), r - th.px(8), t + th.px(34));
        th.button(c, resetDyno, tr.resetDyno, Theme.PANEL_HI, Theme.TEXT, 13);

        Dyno d = an.dyno;
        float cl = l + th.px(40);
        float cr = r - th.px(40);
        float ct = t + th.px(70);
        float cb = b - th.px(28);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
        c.drawRect(cl, ct, cr, cb, th.stroke);
        Paint ax = th.text(11, Theme.DIM, Paint.Align.CENTER);
        for (int rpm = 2000; rpm < Dyno.RPM_MAX; rpm += 1000) {
            float x = cl + (cr - cl) * (rpm - Dyno.RPM_MIN) / (float) (Dyno.RPM_MAX - Dyno.RPM_MIN);
            c.drawLine(x, cb, x, cb + th.px(4), th.stroke);
            c.drawText(Integer.toString(rpm / 1000), x, cb + th.px(16), ax);
        }
        c.drawText("×1000 rpm", cr, cb + th.px(16), th.text(11, Theme.DIM, Paint.Align.RIGHT));

        if (d.isEmpty()) {
            Paint ep = th.text(14, Theme.DIM, Paint.Align.CENTER);
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
        Paint a = th.bold(14, Theme.ACCENT, Paint.Align.LEFT);
        Paint b2 = th.bold(14, Theme.COLD, Paint.Align.LEFT);
        if (pp >= 0) {
            c.drawText("max " + Fmt.number(d.power[pp], 0) + " " + (tr.en ? "PS" : "KM") + " @ "
                    + Dyno.binCenterRpm(pp), l + th.px(12), t + th.px(52), a);
        }
        if (pt >= 0) {
            c.drawText("max " + Fmt.number(d.torque[pt], 0) + " Nm @ " + Dyno.binCenterRpm(pt), (l + r) / 2,
                    t + th.px(52), b2);
        }
    }

    private void curve(Canvas c, Theme th, float[] values, float max, float cl, float ct, float cr, float cb, int color) {
        path.reset();
        boolean started = false;
        for (int i = 0; i < values.length; i++) {
            if (Float.isNaN(values[i])) {
                started = false;
                continue;
            }
            float x = cl + (cr - cl) * (Dyno.binCenterRpm(i) - Dyno.RPM_MIN) / (float) (Dyno.RPM_MAX - Dyno.RPM_MIN);
            float y = cb - (cb - ct) * values[i] / max;
            if (started) {
                path.lineTo(x, y);
            } else {
                path.moveTo(x, y);
                started = true;
            }
            th.fill.setColor(color);
            c.drawCircle(x, y, th.px(2.5f), th.fill);
        }
        th.stroke.setColor(color);
        th.stroke.setStrokeWidth(th.px(2));
        c.drawPath(path, th.stroke);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
    }

    public boolean onTap(float x, float y) {
        if (resetRecords.contains(x, y)) {
            an.perf.resetBest();
            host.analyticsChanged();
            return true;
        }
        if (resetDyno.contains(x, y)) {
            an.dyno.reset();
            return true;
        }
        return false;
    }
}
