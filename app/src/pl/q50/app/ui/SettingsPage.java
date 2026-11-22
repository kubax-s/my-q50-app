package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.List;

import pl.q50.app.data.DataHub;
import pl.q50.app.data.SignalDef;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.AlertEngine;
import pl.q50.app.logic.Settings;

/**
 * Settings: language, which overview tiles are shown, which alerts are enabled, alert sound.
 * Every change is saved immediately.
 */
final class SettingsPage implements Page {
    private final DataHub hub;
    private final Settings settings;
    private final AppHost host;
    private final RectF plBtn = new RectF();
    private final RectF enBtn = new RectF();
    private final RectF[] tileRects = new RectF[Settings.TILES.length];
    private final RectF[] alertRects = new RectF[AlertEngine.IDS.length];
    private final RectF soundRect = new RectF();

    SettingsPage(DataHub hub, Settings settings, AppHost host) {
        this.hub = hub;
        this.settings = settings;
        this.host = host;
        for (int i = 0; i < tileRects.length; i++) tileRects[i] = new RectF();
        for (int i = 0; i < alertRects.length; i++) alertRects[i] = new RectF();
    }

    public String title() {
        return Strings.get().tabSettings;
    }

    public long refreshMs() {
        return 1000;
    }

    public void onShow() {
    }

    private String tileLabel(String key) {
        if ("tyres".equals(key)) return Strings.get().tyres;
        List<SignalDef> defs = hub.map.all();
        for (int i = 0; i < defs.size(); i++) {
            if (defs.get(i).key.equals(key)) return defs.get(i).label();
        }
        return key;
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();

        // row 1: language + info
        float bh = th.px(40);
        c.drawText(t.language, a.left, a.top + th.px(16), th.bold(13, Theme.DIM, Paint.Align.LEFT));
        plBtn.set(a.left, a.top + th.px(24), a.left + th.px(130), a.top + th.px(24) + bh);
        enBtn.set(plBtn.right + th.px(8), plBtn.top, plBtn.right + th.px(138), plBtn.bottom);
        drawChoice(c, th, plBtn, "POLSKI", !t.en);
        drawChoice(c, th, enBtn, "ENGLISH", t.en);
        Paint k = th.text(13, Theme.DIM, Paint.Align.LEFT);
        float ix = enBtn.right + th.px(24);
        c.drawText(t.version + ": " + host.versionText(), ix, plBtn.top + th.px(15), k);
        boolean demo = hub.demoActive();
        String source = demo ? t.demoMode : host.vehicleSensorCount() + " " + t.vehicleSensors;
        c.drawText(t.dataSource + ": " + source, ix, plBtn.top + th.px(35), th.text(13, demo ? Theme.DEMO : Theme.DIM, Paint.Align.LEFT));

        // tiles (left, 2 columns) and alerts (right, 1 column)
        float top = plBtn.bottom + th.px(30);
        float gap = th.px(10);
        float leftW = (a.width() - gap) * 0.5f;
        c.drawText(t.tilesTitle, a.left, top - th.px(8), th.bold(13, Theme.DIM, Paint.Align.LEFT));
        float rowH = Math.min(th.px(38), (a.bottom - top) / 6);
        float cw = (leftW - th.px(6)) / 2;
        for (int i = 0; i < Settings.TILES.length; i++) {
            float l = a.left + (i % 2) * (cw + th.px(6));
            float tt = top + (i / 2) * rowH;
            tileRects[i].set(l, tt, l + cw, tt + rowH - th.px(4));
            toggle(c, th, tileRects[i], tileLabel(Settings.TILES[i]), settings.isTileVisible(Settings.TILES[i]));
        }

        float rl = a.left + leftW + gap;
        c.drawText(t.alertsTitle, rl, top - th.px(8), th.bold(13, Theme.DIM, Paint.Align.LEFT));
        int n = AlertEngine.IDS.length + 1; // + sound
        float arow = Math.min(th.px(34), (a.bottom - top) / n);
        for (int i = 0; i < AlertEngine.IDS.length; i++) {
            float tt = top + i * arow;
            alertRects[i].set(rl, tt, a.right, tt + arow - th.px(3));
            toggle(c, th, alertRects[i], t.alertNames[i], settings.isAlertEnabled(AlertEngine.IDS[i]));
        }
        float st = top + AlertEngine.IDS.length * arow;
        soundRect.set(rl, st, a.right, st + arow - th.px(3));
        toggle(c, th, soundRect, t.alertSound, settings.sound());
    }

    private void drawChoice(Canvas c, Theme th, RectF r, String label, boolean selected) {
        th.button(c, r, label, selected ? Theme.PANEL_HI : Theme.PANEL, selected ? Theme.ACCENT : Theme.DIM, 16);
        if (selected) {
            th.outline(c, r.left, r.top, r.right, r.bottom, Theme.ACCENT, 2);
        }
    }

    /** A row with a check box: filled accent square when on. */
    private static void toggle(Canvas c, Theme th, RectF r, String label, boolean on) {
        th.panel(c, r.left, r.top, r.right, r.bottom, Theme.PANEL);
        float box = Math.min(r.height() - th.px(10), th.px(20));
        float bl = r.left + th.px(8);
        float bt = r.centerY() - box / 2;
        if (on) {
            th.panel(c, bl, bt, bl + box, bt + box, Theme.ACCENT);
            th.stroke.setColor(Theme.BG);
            th.stroke.setStrokeWidth(th.px(3));
            c.drawLine(bl + box * 0.22f, bt + box * 0.52f, bl + box * 0.42f, bt + box * 0.72f, th.stroke);
            c.drawLine(bl + box * 0.42f, bt + box * 0.72f, bl + box * 0.8f, bt + box * 0.3f, th.stroke);
        } else {
            th.outline(c, bl, bt, bl + box, bt + box, Theme.DIM, 2);
        }
        Paint p = th.text(14, on ? Theme.TEXT : Theme.DIM, Paint.Align.LEFT);
        float tx = bl + box + th.px(10);
        c.drawText(Theme.fit(p, label, r.right - tx - th.px(6)), tx, r.centerY() + th.px(5), p);
    }

    public boolean onTap(float x, float y) {
        if (plBtn.contains(x, y)) {
            host.setLanguage(Strings.PL);
            return true;
        }
        if (enBtn.contains(x, y)) {
            host.setLanguage(Strings.EN);
            return true;
        }
        for (int i = 0; i < tileRects.length; i++) {
            if (tileRects[i].contains(x, y)) {
                settings.toggleTile(Settings.TILES[i]);
                host.settingsChanged();
                return true;
            }
        }
        for (int i = 0; i < alertRects.length; i++) {
            if (alertRects[i].contains(x, y)) {
                settings.toggleAlert(AlertEngine.IDS[i]);
                host.settingsChanged();
                return true;
            }
        }
        if (soundRect.contains(x, y)) {
            settings.toggleSound();
            host.settingsChanged();
            return true;
        }
        return false;
    }
}
