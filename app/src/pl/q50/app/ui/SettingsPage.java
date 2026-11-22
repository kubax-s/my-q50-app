package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import pl.q50.app.data.DataHub;
import pl.q50.app.i18n.Strings;

/** App settings: UI language (Polish by default) and basic info. */
final class SettingsPage implements Page {
    private final DataHub hub;
    private final AppHost host;
    private final RectF plBtn = new RectF();
    private final RectF enBtn = new RectF();

    SettingsPage(DataHub hub, AppHost host) {
        this.hub = hub;
        this.host = host;
    }

    public String title() {
        return Strings.get().tabSettings;
    }

    public void onShow() {
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();
        float y = a.top + th.px(26);
        c.drawText(t.language, a.left, y, th.bold(14, Theme.DIM, Paint.Align.LEFT));

        float bw = th.px(220);
        float bt = y + th.px(14);
        plBtn.set(a.left, bt, a.left + bw, bt + th.px(70));
        enBtn.set(plBtn.right + th.px(12), bt, plBtn.right + th.px(12) + bw, bt + th.px(70));
        // Language names are always shown in their own language.
        drawChoice(c, th, plBtn, "POLSKI", !t.en);
        drawChoice(c, th, enBtn, "ENGLISH", t.en);

        float iy = plBtn.bottom + th.px(56);
        Paint k = th.text(15, Theme.DIM, Paint.Align.LEFT);
        Paint v = th.bold(15, Theme.TEXT, Paint.Align.LEFT);
        float vx = a.left + th.px(180);
        c.drawText(t.version, a.left, iy, k);
        c.drawText(host.versionText(), vx, iy, v);

        iy += th.px(28);
        c.drawText(t.dataSource, a.left, iy, th.text(15, Theme.DIM, Paint.Align.LEFT));
        boolean demo = hub.demoActive();
        String source = demo ? t.demoMode : host.vehicleSensorCount() + " " + t.vehicleSensors;
        c.drawText(source, vx, iy, th.bold(15, demo ? Theme.DEMO : Theme.TEXT, Paint.Align.LEFT));
    }

    private void drawChoice(Canvas c, Theme th, RectF r, String label, boolean selected) {
        th.button(c, r, label, selected ? Theme.PANEL_HI : Theme.PANEL, selected ? Theme.ACCENT : Theme.DIM, 22);
        if (selected) {
            th.outline(c, r.left, r.top, r.right, r.bottom, Theme.ACCENT, 2);
        }
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
        return false;
    }
}
