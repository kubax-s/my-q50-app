package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.TripStats;

/** Trip: trip computer, acceleration/braking timing, time in RPM bands and gears. */
final class TripPage implements Page {
    private static final String[] GEAR_NAMES = {"P", "R", "N", "D", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};
    private static final String[] BAND_NAMES = {"<2k", "2–3k", "3–4k", "4–5k", "5k+"};

    private final Analytics an;
    private final AppHost host;
    private final RectF resetTrip = new RectF();
    private final RectF resetRecords = new RectF();

    TripPage(Analytics analytics, AppHost host) {
        this.an = analytics;
        this.host = host;
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
        float lw = (a.width() - gap) * 0.36f;
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
                {t.tripMaxRpm, unit(tr.maxRpm, 0, "")},
                {t.tripMaxPower, unit(tr.maxPower, 0, power)},
                {t.tripMaxTorque, unit(tr.maxTorque, 0, " Nm")},
                {t.tripMaxCoolant, unit(tr.maxCoolant, 0, " °C")},
                {t.tripMaxOil, unit(tr.maxOilTemp, 0, " °C")},
                {t.tripSpirited, Float.isNaN(tr.spiritedShare()) ? "—" : Fmt.number(tr.spiritedShare() * 100, 0) + " %"},
        };
        float btnH = th.px(36);
        float rowH = Math.min(th.px(24), (a.height() - btnH - th.px(20)) / rows.length);
        float y = a.top + th.px(4) + rowH;
        Paint k = th.text(13, Theme.DIM, Paint.Align.LEFT);
        Paint v = th.bold(13, Theme.TEXT, Paint.Align.RIGHT);
        for (int i = 0; i < rows.length; i++) {
            float vw = v.measureText(rows[i][1]);
            c.drawText(Theme.fit(k, rows[i][0], lw - vw - th.px(30)), l + th.px(10), y, k);
            c.drawText(rows[i][1], r - th.px(10), y, v);
            y += rowH;
        }
        resetTrip.set(l + th.px(10), a.bottom - btnH - th.px(6), r - th.px(10), a.bottom - th.px(6));
        th.button(c, resetTrip, t.resetTrip, Theme.PANEL_HI, Theme.TEXT, 13);

        // right side: timing on top, time bars below
        float rl = r + gap;
        float perfB = a.top + (a.height() - gap) * 0.58f;
        Panels.perf(c, th, an, rl, a.top, a.right, perfB, now, resetRecords);
        float bt = perfB + gap;
        float bw = (a.right - rl - gap) * 0.4f;
        Panels.timeBars(c, th, t.rpmBandsTitle, BAND_NAMES, tr.rpmBandMs, rl, bt, rl + bw, a.bottom);
        Panels.timeBars(c, th, t.gearsTitle, GEAR_NAMES, tr.gearMs, rl + bw + gap, bt, a.right, a.bottom);
    }

    private static String unit(float v, int decimals, String unit) {
        return Float.isNaN(v) ? "—" : Fmt.number(v, decimals) + unit;
    }

    public boolean onTap(float x, float y) {
        if (resetTrip.contains(x, y)) {
            an.trip.reset();
            return true;
        }
        if (resetRecords.contains(x, y)) {
            an.perf.resetBest();
            host.analyticsChanged();
            return true;
        }
        return false;
    }
}
