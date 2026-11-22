package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.TripStats;

/** Trip computer since app start or the last reset. */
final class TripPage implements Page {
    private static final String[] GEAR_NAMES = {"P", "R", "N", "D", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};
    private static final String[] BAND_NAMES = {"<2k", "2–3k", "3–4k", "4–5k", "5k+"};

    private final Analytics an;
    private final RectF resetBtn = new RectF();

    TripPage(Analytics analytics) {
        this.an = analytics;
    }

    public String title() {
        return Strings.get().tabTrip;
    }

    public void onShow() {
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();
        TripStats tr = an.trip;
        float gap = th.px(8);
        float lw = (a.width() - gap) * 0.46f;
        float l = a.left;
        float r = a.left + lw;
        th.panel(c, l, a.top, r, a.bottom, Theme.PANEL);

        String power = t.en ? " PS" : " KM";
        String[][] rows = {
                {t.tripTime, Fmt.duration(tr.elapsedMs)},
                {t.tripDriving, Fmt.duration(tr.drivingMs)},
                {t.tripIdle, Fmt.duration(tr.idleMs)},
                {t.tripDistance, Fmt.number(tr.distanceKm(), 1) + " km"},
                {t.tripAvgSpeed, unit(tr.avgSpeed(), 0, " km/h")},
                {t.tripMaxSpeed, unit(tr.maxSpeed, 0, " km/h")},
                {t.tripMaxRpm, unit(tr.maxRpm, 0, " rpm")},
                {t.tripMaxPower, unit(tr.maxPower, 0, power)},
                {t.tripMaxTorque, unit(tr.maxTorque, 0, " Nm")},
                {t.tripMaxGLat, unit(tr.maxGLat, 2, " g")},
                {t.tripGLong, Fmt.number(tr.maxGPlus, 2) + " / " + Fmt.number(tr.maxGMinus, 2)},
                {t.tripMaxCoolant, unit(tr.maxCoolant, 0, " °C")},
                {t.tripMaxOil, unit(tr.maxOilTemp, 0, " °C")},
                {t.tripSpirited, Float.isNaN(tr.spiritedShare()) ? "—" : Fmt.number(tr.spiritedShare() * 100, 0) + " %"},
        };
        float rowH = Math.min(th.px(22), (a.height() - th.px(64)) / rows.length);
        float y = a.top + th.px(8) + rowH;
        Paint k = th.text(14, Theme.DIM, Paint.Align.LEFT);
        Paint v = th.bold(14, Theme.TEXT, Paint.Align.RIGHT);
        for (int i = 0; i < rows.length; i++) {
            c.drawText(rows[i][0], l + th.px(12), y, k);
            c.drawText(rows[i][1], r - th.px(12), y, v);
            y += rowH;
        }
        resetBtn.set(l + th.px(12), a.bottom - th.px(44), r - th.px(12), a.bottom - th.px(8));
        th.button(c, resetBtn, t.resetTrip, Theme.PANEL_HI, Theme.TEXT, 14);

        float rl = r + gap;
        float half = (a.height() - gap) / 2;
        bars(c, th, t.rpmBandsTitle, BAND_NAMES, tr.rpmBandMs, rl, a.top, a.right, a.top + half);
        bars(c, th, t.gearsTitle, GEAR_NAMES, tr.gearMs, rl, a.top + half + gap, a.right, a.bottom);
    }

    private static String unit(float v, int decimals, String unit) {
        return Float.isNaN(v) ? "—" : Fmt.number(v, decimals) + unit;
    }

    /** Vertical bars of time shares. */
    private static void bars(Canvas c, Theme th, String title, String[] names, long[] ms, float l, float t, float r, float b) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        c.drawText(title, l + th.px(12), t + th.px(20), th.bold(13, Theme.DIM, Paint.Align.LEFT));
        long total = 0;
        for (int i = 0; i < ms.length; i++) total += ms[i];
        float cl = l + th.px(12);
        float cr = r - th.px(12);
        float ct = t + th.px(40);
        float cb = b - th.px(22);
        float slot = (cr - cl) / names.length;
        Paint np = th.text(11, Theme.DIM, Paint.Align.CENTER);
        Paint pp = th.bold(11, Theme.TEXT, Paint.Align.CENTER);
        for (int i = 0; i < names.length; i++) {
            float share = total > 0 ? ms[i] / (float) total : 0f;
            float x0 = cl + i * slot + th.px(3);
            float x1 = cl + (i + 1) * slot - th.px(3);
            float top = cb - (cb - ct - th.px(14)) * share;
            th.fill.setColor(share > 0 ? Theme.ACCENT : Theme.LINE);
            c.drawRect(x0, share > 0 ? top : cb - th.px(2), x1, cb, th.fill);
            c.drawText(names[i], (x0 + x1) / 2, cb + th.px(14), np);
            if (share >= 0.005f) {
                c.drawText(Fmt.number(share * 100, 0) + "%", (x0 + x1) / 2, top - th.px(3), pp);
            }
        }
    }

    public boolean onTap(float x, float y) {
        if (resetBtn.contains(x, y)) {
            an.trip.reset();
            return true;
        }
        return false;
    }
}
