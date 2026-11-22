package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.RectF;

import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;

/** Engine: warm-up, turbo cool-down, oil pressure vs. baseline, power/torque curve. */
final class EnginePage implements Page {
    private final Analytics an;
    private final RectF resetDyno = new RectF();

    EnginePage(Analytics analytics) {
        this.an = analytics;
    }

    public String title() {
        return Strings.get().tabEngine;
    }

    public void onShow() {
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        float gap = th.px(8);
        float w = (a.width() - gap) / 2;
        float h = (a.height() - gap) / 2;
        Panels.warmUp(c, th, an, a.left, a.top, a.left + w, a.top + h);
        Panels.turbo(c, th, an, a.left + w + gap, a.top, a.right, a.top + h);
        Panels.oil(c, th, an, a.left, a.top + h + gap, a.left + w, a.bottom);
        Panels.dyno(c, th, an, a.left + w + gap, a.top + h + gap, a.right, a.bottom, resetDyno);
    }

    public boolean onTap(float x, float y) {
        if (resetDyno.contains(x, y)) {
            an.dyno.reset();
            return true;
        }
        return false;
    }
}
