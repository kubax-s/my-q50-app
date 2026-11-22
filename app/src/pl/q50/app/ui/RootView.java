package pl.q50.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

import pl.q50.app.data.CsvLogger;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.AlertEngine;
import pl.q50.app.logic.Analytics;

/**
 * The whole UI in one view: tab bar, current page, alert banner, message line.
 * Long-pressing anywhere while logging adds a marker.
 */
public final class RootView extends View {
    private static final float TAB_H = 56;
    private static final float MARGIN = 10;
    private static final float BANNER_H = 48;
    private static final long MESSAGE_MS = 3000;
    private static final long LONG_PRESS_MS = 700;

    private final Theme th = new Theme();
    private final Page[] pages;
    private final int logIndex;
    private final CsvLogger logger;
    private final Analytics analytics;
    private final AppHost host;
    private final RectF[] tabRects;
    private final RectF area = new RectF();
    private final RectF banner = new RectF();
    private final RectF bannerOk = new RectF();
    private final Handler handler = new Handler();
    private int current;
    private String message;
    private long messageUntil;
    private float downX;
    private float downY;
    private boolean longPressFired;
    private String bannerAlertId;

    private final Runnable longPress = new Runnable() {
        public void run() {
            // Only consumes the touch while logging; otherwise a slow tap still counts as a tap.
            if (logger.isActive()) {
                longPressFired = true;
                host.marker();
            }
        }
    };

    public RootView(Context ctx, DataHub hub, CsvLogger logger, Analytics analytics, AppHost host) {
        super(ctx);
        this.logger = logger;
        this.analytics = analytics;
        this.host = host;
        LogPage logPage = new LogPage(hub, logger, host);
        pages = new Page[]{
                new OverviewPage(hub, analytics.settings),
                new HealthPage(analytics),
                new PerformancePage(analytics, host),
                new TripPage(analytics),
                new SensorsPage(hub),
                logPage,
                new SettingsPage(hub, analytics.settings, host),
        };
        logIndex = 5;
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
        Strings t = Strings.get();

        // tab bar: equal-width tabs across the full width
        float gap = th.px(4);
        float left = th.px(MARGIN);
        float tabW = (getWidth() - 2 * left - gap * (pages.length - 1)) / pages.length;
        float tabBottom = th.px(TAB_H);
        for (int i = 0; i < pages.length; i++) {
            RectF r = tabRects[i];
            r.set(left + i * (tabW + gap), th.px(6), left + i * (tabW + gap) + tabW, tabBottom - th.px(6));
            boolean sel = i == current;
            String title = pages[i].title();
            int fg = sel ? Theme.ACCENT : Theme.DIM;
            if (i == logIndex && logger.isActive()) {
                title = t.recTab(Fmt.duration(logger.elapsedMs()));
                fg = (now / 500) % 2 == 0 ? Theme.HOT : 0xFFB04040;
            }
            th.fill.setColor(sel ? Theme.PANEL_HI : Theme.BG);
            c.drawRoundRect(r, th.px(8), th.px(8), th.fill);
            Paint p = th.bold(14, fg, Paint.Align.CENTER);
            c.drawText(Theme.fit(p, title, r.width() - th.px(6)), r.centerX(),
                    r.centerY() - (p.ascent() + p.descent()) / 2, p);
            if (sel) {
                th.fill.setColor(Theme.ACCENT);
                c.drawRect(r.left + th.px(10), r.bottom - th.px(3), r.right - th.px(10), r.bottom, th.fill);
            }
        }

        List<AlertEngine.Alert> alerts = analytics.alerts.visible();
        float bottom = getHeight() - th.px(MARGIN);
        if (!alerts.isEmpty()) {
            bottom -= th.px(BANNER_H + 6);
        }
        area.set(th.px(MARGIN), tabBottom + th.px(4), getWidth() - th.px(MARGIN), bottom);
        pages[current].draw(c, area, th, now);

        drawBanner(c, alerts, t);

        if (message != null && now < messageUntil) {
            float h = th.px(40);
            float b = area.bottom - th.px(8);
            th.panel(c, th.px(80), b - h, getWidth() - th.px(80), b, 0xEE303844);
            c.drawText(message, getWidth() / 2f, b - h / 2 + th.px(6), th.bold(16, Theme.TEXT, Paint.Align.CENTER));
        }
    }

    private void drawBanner(Canvas c, List<AlertEngine.Alert> alerts, Strings t) {
        if (alerts.isEmpty()) {
            banner.setEmpty();
            bannerOk.setEmpty();
            bannerAlertId = null;
            return;
        }
        AlertEngine.Alert a = alerts.get(0);
        bannerAlertId = a.id;
        int bg;
        int fg;
        if (a.severity == AlertEngine.CRITICAL) {
            bg = Theme.HOT;
            fg = Theme.TEXT;
        } else if (a.severity == AlertEngine.WARNING) {
            bg = Theme.ACCENT;
            fg = Theme.BG;
        } else {
            bg = 0xFF2B5C8A;
            fg = Theme.TEXT;
        }
        banner.set(th.px(MARGIN), getHeight() - th.px(MARGIN + BANNER_H), getWidth() - th.px(MARGIN),
                getHeight() - th.px(MARGIN));
        th.fill.setColor(bg);
        c.drawRoundRect(banner, th.px(8), th.px(8), th.fill);
        bannerOk.set(banner.right - th.px(90), banner.top + th.px(6), banner.right - th.px(8), banner.bottom - th.px(6));
        th.button(c, bannerOk, t.alertOk, 0x33000000, fg, 18);
        float textRight = bannerOk.left - th.px(8);
        if (alerts.size() > 1) {
            String more = "+" + (alerts.size() - 1);
            Paint mp = th.bold(16, fg, Paint.Align.RIGHT);
            c.drawText(more, textRight, banner.centerY() + th.px(6), mp);
            textRight -= mp.measureText(more) + th.px(12);
        }
        Paint p = th.bold(18, fg, Paint.Align.LEFT);
        c.drawText(Theme.fit(p, a.text, textRight - banner.left - th.px(14)), banner.left + th.px(14),
                banner.centerY() + th.px(7), p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longPressFired = false;
                handler.postDelayed(longPress, LONG_PRESS_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > th.px(30) || Math.abs(e.getY() - downY) > th.px(30)) {
                    handler.removeCallbacks(longPress);
                }
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (!longPressFired) {
                    handleUp(e.getX(), e.getY());
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
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
        if (bannerAlertId != null && banner.contains(x, y)) {
            analytics.alerts.snooze(bannerAlertId, SystemClock.uptimeMillis());
            invalidate();
            return;
        }
        if (pages[current].onTap(x, y)) {
            invalidate();
        }
    }
}
