package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.List;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.AlertEngine;
import pl.q50.app.logic.Analytics;

/** All alerts with their current state (tap to snooze), recent alert history, tyre monitor. */
final class AlertsPage implements Page {
    private final Analytics an;
    private final AppHost host;
    private final RectF[] rows = new RectF[AlertEngine.IDS.length];

    AlertsPage(Analytics analytics, AppHost host) {
        this.an = analytics;
        this.host = host;
        for (int i = 0; i < rows.length; i++) rows[i] = new RectF();
    }

    public String title() {
        return Strings.get().tabAlerts;
    }

    public void onShow() {
    }

    static int severityColor(int severity) {
        return severity == AlertEngine.CRITICAL ? Theme.HOT : severity == AlertEngine.WARNING ? Theme.ACCENT : 0xFF4F8FD0;
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();
        AlertEngine e = an.alerts;
        float gap = th.px(8);
        float lw = (a.width() - gap) * 0.56f;
        float l = a.left;
        float r = l + lw;

        // alert list
        float hintH = th.px(20);
        float rowH = (a.height() - hintH) / rows.length;
        for (int i = 0; i < rows.length; i++) {
            String id = AlertEngine.IDS[i];
            AlertEngine.Alert al = e.get(id);
            boolean enabled = an.settings.isAlertEnabled(id);
            long snoozed = e.snoozeRemainingMs(id, now);
            float top = a.top + i * rowH;
            rows[i].set(l, top, r, top + rowH - th.px(3));
            boolean active = al.active && enabled;
            th.panel(c, l, top, r, top + rowH - th.px(3), active && snoozed == 0 ? Theme.PANEL_HI : Theme.PANEL);
            int dot = !enabled ? Theme.LINE : active ? severityColor(al.severity) : Theme.OK;
            th.fill.setColor(dot);
            float cy = top + (rowH - th.px(3)) / 2;
            c.drawCircle(l + th.px(16), cy, th.px(6), th.fill);

            String state;
            int sc;
            if (!enabled) {
                state = t.alertDisabled;
                sc = Theme.LINE;
            } else if (active && snoozed > 0) {
                state = t.snoozed(Fmt.duration(snoozed));
                sc = Theme.DIM;
            } else if (active) {
                state = al.text;
                sc = severityColor(al.severity);
            } else {
                state = t.alertOff;
                sc = Theme.OK;
            }
            float nameX = l + th.px(30);
            if (active && snoozed == 0) {
                // two lines: name, then the live alert text
                Paint np = th.bold(13, Theme.TEXT, Paint.Align.LEFT);
                c.drawText(Theme.fit(np, t.alertNames[i], r - nameX - th.px(10)), nameX, cy - th.px(2), np);
                Paint sp = th.text(12, sc, Paint.Align.LEFT);
                c.drawText(Theme.fit(sp, state, r - nameX - th.px(10)), nameX, cy + th.px(12), sp);
            } else {
                Paint sp = th.bold(13, sc, Paint.Align.RIGHT);
                Paint np = th.text(14, enabled ? Theme.TEXT : Theme.DIM, Paint.Align.LEFT);
                float stateW = Math.min(sp.measureText(state), (r - nameX) * 0.4f);
                c.drawText(Theme.fit(np, t.alertNames[i], r - nameX - stateW - th.px(20)), nameX, cy + th.px(5), np);
                c.drawText(Theme.fit(sp, state, stateW + th.px(1)), r - th.px(10), cy + th.px(5), sp);
            }
        }
        Paint hp = th.text(11, Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(hp, t.tapToSnooze, lw), l, a.bottom - th.px(5), hp);

        // history (top right) and tyres (bottom right)
        float rl = r + gap;
        float histB = a.top + (a.height() - gap) * 0.5f;
        th.panel(c, rl, a.top, a.right, histB, Theme.PANEL);
        Panels.title(c, th, t.historyTitle, rl, a.top);
        List<AlertEngine.Event> hist = e.history();
        if (hist.isEmpty()) {
            c.drawText(t.historyEmpty, rl + th.px(12), a.top + th.px(50), th.text(13, Theme.DIM, Paint.Align.LEFT));
        }
        float hy = a.top + th.px(46);
        float hRow = th.px(21);
        Paint tp = th.text(11, Theme.DIM, Paint.Align.RIGHT);
        for (int i = 0; i < hist.size() && hy <= histB - th.px(6); i++) {
            AlertEngine.Event ev = hist.get(i);
            String ago = t.ago(now - ev.at);
            float agoW = tp.measureText(ago);
            th.fill.setColor(severityColor(ev.severity));
            c.drawCircle(rl + th.px(14), hy - th.px(4), th.px(4), th.fill);
            Paint ep = th.text(12, Theme.TEXT, Paint.Align.LEFT);
            c.drawText(Theme.fit(ep, ev.text, a.right - rl - agoW - th.px(40)), rl + th.px(24), hy, ep);
            c.drawText(ago, a.right - th.px(10), hy, th.text(11, Theme.DIM, Paint.Align.RIGHT));
            hy += hRow;
        }
        Panels.tyres(c, th, an, rl, histB + gap, a.right, a.bottom);
    }

    public boolean onTap(float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        for (int i = 0; i < rows.length; i++) {
            if (!rows[i].contains(x, y)) continue;
            String id = AlertEngine.IDS[i];
            AlertEngine e = an.alerts;
            if (!an.settings.isAlertEnabled(id)) {
                host.message(Strings.get().enableInSettings);
            } else if (e.get(id).active) {
                if (e.snoozeRemainingMs(id, now) > 0) {
                    e.unsnooze(id);
                } else {
                    e.snooze(id, now);
                }
            }
            return true;
        }
        return false;
    }
}
