package pl.q50.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import pl.q50.app.data.CsvLogger;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.Fmt;

/** The whole UI in one view: tab bar, current page, REC indicator, message line. */
public final class RootView extends View {
    private static final float TAB_H = 56;
    private static final float MARGIN = 10;
    private static final long MESSAGE_MS = 3000;

    private final Theme th = new Theme();
    private final Page[] pages;
    private final CsvLogger logger;
    private final RectF[] tabRects;
    private final RectF recRect = new RectF();
    private final RectF area = new RectF();
    private final LogPage logPage;
    private int current;
    private String message;
    private long messageUntil;
    private float downX;
    private float downY;

    public RootView(Context ctx, DataHub hub, CsvLogger logger, LogPageHost host) {
        super(ctx);
        this.logger = logger;
        logPage = new LogPage(hub, logger, host);
        pages = new Page[]{new OverviewPage(hub), new SensorsPage(hub), logPage};
        tabRects = new RectF[pages.length];
        for (int i = 0; i < pages.length; i++) {
            tabRects[i] = new RectF();
        }
        setKeepScreenOn(true);
        setBackgroundColor(Theme.BG);
    }

    public int currentPage() {
        return current;
    }

    public void showPage(int i) {
        current = Math.max(0, Math.min(pages.length - 1, i));
        pages[current].onShow();
        invalidate();
    }

    public void message(String text) {
        message = text;
        messageUntil = SystemClock.uptimeMillis() + MESSAGE_MS;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        th.s = Math.min(getWidth() / 800f, getHeight() / 480f);
        c.drawColor(Theme.BG);

        // tab bar
        float tabW = th.px(170);
        float tabBottom = th.px(TAB_H);
        for (int i = 0; i < pages.length; i++) {
            RectF r = tabRects[i];
            r.set(th.px(MARGIN) + i * (tabW + th.px(6)), th.px(6), th.px(MARGIN) + i * (tabW + th.px(6)) + tabW,
                    tabBottom - th.px(6));
            boolean sel = i == current;
            th.button(c, r, pages[i].title(), sel ? Theme.PANEL_HI : Theme.BG, sel ? Theme.ACCENT : Theme.DIM, 17);
            if (sel) {
                th.fill.setColor(Theme.ACCENT);
                c.drawRect(r.left + th.px(12), r.bottom - th.px(3), r.right - th.px(12), r.bottom, th.fill);
            }
        }
        if (logger.isActive()) {
            recRect.set(getWidth() - th.px(MARGIN + 200), th.px(6), getWidth() - th.px(MARGIN), tabBottom - th.px(6));
            th.panel(c, recRect.left, recRect.top, recRect.right, recRect.bottom, Theme.PANEL_HI);
            boolean blink = (now / 500) % 2 == 0;
            th.fill.setColor(blink ? Theme.HOT : 0xFF802020);
            c.drawCircle(recRect.left + th.px(20), recRect.centerY(), th.px(8), th.fill);
            Paint p = th.bold(16, Theme.TEXT, Paint.Align.LEFT);
            c.drawText("REC " + Fmt.duration(logger.elapsedMs()) + "  +M", recRect.left + th.px(36),
                    recRect.centerY() + th.px(6), p);
        } else {
            recRect.setEmpty();
            c.drawText("Q50 Monitor", getWidth() - th.px(MARGIN), tabBottom / 2 + th.px(6),
                    th.text(15, Theme.LINE, Paint.Align.RIGHT));
        }

        area.set(th.px(MARGIN), tabBottom + th.px(4), getWidth() - th.px(MARGIN), getHeight() - th.px(MARGIN));
        pages[current].draw(c, area, th, now);

        if (message != null && now < messageUntil) {
            float h = th.px(40);
            float b = getHeight() - th.px(16);
            th.panel(c, th.px(80), b - h, getWidth() - th.px(80), b, 0xEE303844);
            c.drawText(message, getWidth() / 2f, b - h / 2 + th.px(6), th.bold(16, Theme.TEXT, Paint.Align.CENTER));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                handleUp(e.getX(), e.getY());
                return true;
            default:
                return true;
        }
    }

    private void handleUp(float x, float y) {
        float dx = x - downX;
        float dy = y - downY;
        if (Math.abs(dx) > th.px(140) && Math.abs(dx) > 2 * Math.abs(dy)) {
            showPage(current + (dx < 0 ? 1 : -1));
            return;
        }
        if (Math.abs(dx) > th.px(30) || Math.abs(dy) > th.px(30)) {
            return; // a drag, not a tap
        }
        for (int i = 0; i < tabRects.length; i++) {
            if (tabRects[i].contains(x, y)) {
                showPage(i);
                return;
            }
        }
        if (recRect.contains(x, y)) {
            logger.marker();
            message("Marker #" + logger.markers());
            return;
        }
        if (pages[current].onTap(x, y)) {
            invalidate();
        }
    }
}
