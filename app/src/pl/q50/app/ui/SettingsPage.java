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
 * Settings in three sections: GENERAL (language, theme, accent colour, info), TILES (what the
 * overview shows) and ALERTS (which alerts are on, alert sound). Every change is saved and
 * applied immediately.
 */
final class SettingsPage implements Page {
    private static final int GENERAL = 0;
    private static final int TILES = 1;
    private static final int ALERTS = 2;

    private final DataHub hub;
    private final Settings settings;
    private final AppHost host;
    private int section;

    private final RectF[] sectionBtns = {new RectF(), new RectF(), new RectF()};
    private final RectF plBtn = new RectF();
    private final RectF enBtn = new RectF();
    private final RectF[] themeBtns = new RectF[Theme.THEME_COUNT];
    private final RectF[] accentBtns = new RectF[Theme.ACCENTS.length];
    private final RectF[] tileRects = new RectF[Settings.TILES.length];
    private final RectF[] alertRects = new RectF[AlertEngine.IDS.length];
    private final RectF soundRect = new RectF();

    SettingsPage(DataHub hub, Settings settings, AppHost host) {
        this.hub = hub;
        this.settings = settings;
        this.host = host;
        for (int i = 0; i < themeBtns.length; i++) themeBtns[i] = new RectF();
        for (int i = 0; i < accentBtns.length; i++) accentBtns[i] = new RectF();
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
        // section switcher
        float sw = th.px(150);
        for (int i = 0; i < sectionBtns.length; i++) {
            RectF r = sectionBtns[i];
            r.set(a.left + i * (sw + th.px(6)), a.top, a.left + i * (sw + th.px(6)) + sw, a.top + th.px(38));
            drawChoice(c, th, r, t.settingsSections[i], section == i, 15);
        }
        float top = a.top + th.px(52);
        if (section == GENERAL) {
            drawGeneral(c, a, th, t, top);
        } else if (section == TILES) {
            drawTiles(c, a, th, top);
        } else {
            drawAlerts(c, a, th, t, top);
        }
    }

    private void drawGeneral(Canvas c, RectF a, Theme th, Strings t, float top) {
        // language (labels fetch their paint right before drawing: buttons reuse the shared bold paint)
        c.drawText(t.language, a.left, top + th.px(14), label(th));
        float y = top + th.px(22);
        plBtn.set(a.left, y, a.left + th.px(130), y + th.px(40));
        enBtn.set(plBtn.right + th.px(8), y, plBtn.right + th.px(138), y + th.px(40));
        drawChoice(c, th, plBtn, "POLSKI", !t.en, 16);
        drawChoice(c, th, enBtn, "ENGLISH", t.en, 16);

        // theme: each button is drawn in its own colours, as a preview
        y = plBtn.bottom + th.px(30);
        c.drawText(t.themeTitle, a.left, y - th.px(8), label(th));
        float bw = th.px(150);
        for (int i = 0; i < Theme.THEME_COUNT; i++) {
            RectF r = themeBtns[i];
            r.set(a.left + i * (bw + th.px(8)), y, a.left + i * (bw + th.px(8)) + bw, y + th.px(48));
            th.fill.setColor(Theme.surface(i, Theme.SURFACE_PANEL));
            c.drawRoundRect(r, th.px(8), th.px(8), th.fill);
            Paint p = th.bold(15, Theme.surface(i, Theme.SURFACE_TEXT), Paint.Align.CENTER);
            c.drawText(t.themeNames[i], r.centerX(), r.centerY() - (p.ascent() + p.descent()) / 2, p);
            boolean sel = settings.theme == i;
            th.outline(c, r.left, r.top, r.right, r.bottom, sel ? Theme.ACCENT : Theme.LINE, sel ? 3 : 1);
        }

        // accent colour swatches
        y = themeBtns[0].bottom + th.px(32);
        c.drawText(t.accentTitle, a.left, y - th.px(8), label(th));
        float d = th.px(44);
        float gap = th.px(14);
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            RectF r = accentBtns[i];
            r.set(a.left + i * (d + gap), y, a.left + i * (d + gap) + d, y + d);
            boolean sel = settings.accent == i;
            if (sel) {
                th.stroke.setColor(Theme.TEXT);
                th.stroke.setStrokeWidth(th.px(3));
                c.drawCircle(r.centerX(), r.centerY(), d / 2, th.stroke);
            }
            th.fill.setColor(Theme.light ? Theme.scale(Theme.ACCENTS[i], 0.72f) : Theme.ACCENTS[i]);
            c.drawCircle(r.centerX(), r.centerY(), d / 2 - th.px(sel ? 6 : 2), th.fill);
        }

        // info
        float iy = accentBtns[0].bottom + th.px(36);
        Paint k = th.text(13, Theme.DIM, Paint.Align.LEFT);
        c.drawText(t.version + ": " + host.versionText(), a.left, iy, k);
        boolean demo = hub.demoActive();
        String source = demo ? t.demoMode : host.vehicleSensorCount() + " " + t.vehicleSensors;
        c.drawText(t.dataSource + ": " + source, a.left, iy + th.px(20),
                th.text(13, demo ? Theme.DEMO : Theme.DIM, Paint.Align.LEFT));
    }

    private static Paint label(Theme th) {
        return th.bold(13, Theme.DIM, Paint.Align.LEFT);
    }

    private void drawTiles(Canvas c, RectF a, Theme th, float top) {
        c.drawText(Strings.get().tilesTitle, a.left, top + th.px(10), th.bold(13, Theme.DIM, Paint.Align.LEFT));
        float y0 = top + th.px(22);
        float rowH = Math.min(th.px(48), (a.bottom - y0) / 6);
        float cw = (a.width() - th.px(10)) / 2;
        for (int i = 0; i < Settings.TILES.length; i++) {
            float l = a.left + (i % 2) * (cw + th.px(10));
            float tt = y0 + (i / 2) * rowH;
            tileRects[i].set(l, tt, l + cw, tt + rowH - th.px(5));
            toggle(c, th, tileRects[i], tileLabel(Settings.TILES[i]), settings.isTileVisible(Settings.TILES[i]));
        }
    }

    private void drawAlerts(Canvas c, RectF a, Theme th, Strings t, float top) {
        c.drawText(t.alertsTitle, a.left, top + th.px(10), th.bold(13, Theme.DIM, Paint.Align.LEFT));
        float y0 = top + th.px(22);
        int n = AlertEngine.IDS.length + 1; // + sound
        int rows = (n + 1) / 2;
        float rowH = Math.min(th.px(48), (a.bottom - y0) / rows);
        float cw = (a.width() - th.px(10)) / 2;
        for (int i = 0; i < n; i++) {
            float l = a.left + (i / rows) * (cw + th.px(10));
            float tt = y0 + (i % rows) * rowH;
            RectF r = i < AlertEngine.IDS.length ? alertRects[i] : soundRect;
            r.set(l, tt, l + cw, tt + rowH - th.px(5));
            if (i < AlertEngine.IDS.length) {
                toggle(c, th, r, t.alertNames[i], settings.isAlertEnabled(AlertEngine.IDS[i]));
            } else {
                toggle(c, th, r, t.alertSound, settings.sound());
            }
        }
    }

    private void drawChoice(Canvas c, Theme th, RectF r, String label, boolean selected, float size) {
        th.button(c, r, label, selected ? Theme.PANEL_HI : Theme.PANEL, selected ? Theme.ACCENT : Theme.DIM, size);
        if (selected) {
            th.outline(c, r.left, r.top, r.right, r.bottom, Theme.ACCENT, 2);
        }
    }

    /** A row with a check box: filled accent square when on. */
    private void toggle(Canvas c, Theme th, RectF r, String label, boolean on) {
        th.panel(c, r.left, r.top, r.right, r.bottom, Theme.PANEL);
        float box = Math.min(r.height() - th.px(10), th.px(22));
        float bl = r.left + th.px(10);
        float bt = r.centerY() - box / 2;
        if (on) {
            th.panel(c, bl, bt, bl + box, bt + box, Theme.ACCENT);
            th.stroke.setColor(Theme.ON_COLOR);
            th.stroke.setStrokeWidth(th.px(3));
            c.drawLine(bl + box * 0.22f, bt + box * 0.52f, bl + box * 0.42f, bt + box * 0.72f, th.stroke);
            c.drawLine(bl + box * 0.42f, bt + box * 0.72f, bl + box * 0.8f, bt + box * 0.3f, th.stroke);
        } else {
            th.outline(c, bl, bt, bl + box, bt + box, Theme.DIM, 2);
        }
        Paint p = th.text(15, on ? Theme.TEXT : Theme.DIM, Paint.Align.LEFT);
        float tx = bl + box + th.px(12);
        c.drawText(Theme.fit(p, label, r.right - tx - th.px(6)), tx, r.centerY() + th.px(5), p);
    }

    private void themeChanged() {
        Theme.apply(settings.theme, settings.accent);
        host.settingsChanged();
    }

    public boolean onTap(float x, float y) {
        for (int i = 0; i < sectionBtns.length; i++) {
            if (sectionBtns[i].contains(x, y)) {
                section = i;
                return true;
            }
        }
        if (section == GENERAL) {
            if (plBtn.contains(x, y)) {
                host.setLanguage(Strings.PL);
                return true;
            }
            if (enBtn.contains(x, y)) {
                host.setLanguage(Strings.EN);
                return true;
            }
            for (int i = 0; i < themeBtns.length; i++) {
                if (themeBtns[i].contains(x, y)) {
                    settings.theme = i;
                    themeChanged();
                    return true;
                }
            }
            for (int i = 0; i < accentBtns.length; i++) {
                if (accentBtns[i].contains(x, y)) {
                    settings.accent = i;
                    themeChanged();
                    return true;
                }
            }
        } else if (section == TILES) {
            for (int i = 0; i < tileRects.length; i++) {
                if (tileRects[i].contains(x, y)) {
                    settings.toggleTile(Settings.TILES[i]);
                    host.settingsChanged();
                    return true;
                }
            }
        } else {
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
        }
        return false;
    }
}
