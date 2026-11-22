package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.RectF;

/** One screen below the tab bar. */
public interface Page {
    String title();

    void draw(Canvas c, RectF area, Theme th, long nowMs);

    /** @return true when the tap was handled. */
    boolean onTap(float x, float y);

    void onShow();

    /** How often the page needs redrawing while data changes (ms); taps always redraw at once. */
    long refreshMs();
}
