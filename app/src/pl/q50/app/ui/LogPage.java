package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

import pl.q50.app.data.CsvLogger;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.Fmt;
import pl.q50.app.data.Storage;
import pl.q50.app.i18n.Strings;

/** Start/stop CSV logging, markers, and choice of where the files go. */
final class LogPage implements Page {
    private final DataHub hub;
    private final CsvLogger logger;
    private final AppHost host;
    private List<Storage.Option> options = new ArrayList<Storage.Option>();
    private int chosen = -1;

    private final RectF startBtn = new RectF();
    private final RectF markerBtn = new RectF();
    private final RectF rescanBtn = new RectF();
    private static final int MAX_OPTIONS = 12;
    private final RectF[] optionRects = new RectF[MAX_OPTIONS];
    private int optionCount;
    private final android.os.Handler ui = new android.os.Handler();
    private boolean scanning;
    private java.util.List<String> hintLines;
    private String hintKey;

    LogPage(DataHub hub, CsvLogger logger, AppHost host) {
        this.hub = hub;
        this.logger = logger;
        this.host = host;
        for (int i = 0; i < MAX_OPTIONS; i++) optionRects[i] = new RectF();
    }

    public String title() {
        return Strings.get().tabLog;
    }

    public long refreshMs() {
        return 500;
    }

    public void onShow() {
        if (options.isEmpty() && !scanning) {
            rescan();
        }
    }

    /** Probing mounts writes test files (possibly to a slow USB stick): done off the UI thread. */
    private void rescan() {
        scanning = true;
        new Thread("storage-scan") {
            public void run() {
                final List<Storage.Option> found = Storage.scan();
                ui.post(new Runnable() {
                    public void run() {
                        scanning = false;
                        applyScan(found);
                    }
                });
            }
        }.start();
    }

    private void applyScan(List<Storage.Option> found) {
        options = found;
        chosen = -1;
        String saved = host.savedStoragePath();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).writable && options.get(i).root.getAbsolutePath().equals(saved)) {
                chosen = i;
            }
        }
        // never pick RAM (tmpfs) on its own: only a real disk with room is chosen automatically
        for (int i = 0; chosen < 0 && i < options.size(); i++) {
            if (options.get(i).recommended()) {
                chosen = i;
            }
        }
    }

    Storage.Option target() {
        return chosen >= 0 && chosen < options.size() ? options.get(chosen) : null;
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings t = Strings.get();
        boolean on = logger.isActive();
        float colW = th.px(300);
        startBtn.set(a.left, a.top, a.left + colW, a.top + th.px(110));
        markerBtn.set(a.left, startBtn.bottom + th.px(10), a.left + colW, startBtn.bottom + th.px(120));
        th.button(c, startBtn, on ? t.stop : t.start, on ? Theme.HOT : Theme.OK, on ? Theme.ON_HOT : Theme.ON_COLOR, 30);
        th.button(c, markerBtn, t.marker + (on ? "  #" + (logger.markers() + 1) : ""),
                on ? Theme.ACCENT : Theme.PANEL, on ? Theme.ON_COLOR : Theme.DIM, 26);
        Paint hint = th.text(13, Theme.DIM, Paint.Align.LEFT);
        float hy = markerBtn.bottom + th.px(24);
        String key = t.code + (int) colW;
        if (!key.equals(hintKey)) { // wrap once per language/width, not every frame
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < t.logHints.length; i++) {
                if (i > 0) all.append(' ');
                all.append(t.logHints[i]);
            }
            hintLines = Theme.wrap(hint, all.toString(), colW);
            hintKey = key;
        }
        java.util.List<String> lines = hintLines;
        for (int i = 0; i < lines.size() && hy + th.px(17) * i < a.bottom; i++) {
            c.drawText(lines.get(i), a.left, hy + th.px(17) * i, hint);
        }

        // status
        float x = a.left + colW + th.px(24);
        float y = a.top + th.px(20);
        Paint k = th.text(15, Theme.DIM, Paint.Align.LEFT);
        Paint v = th.bold(15, Theme.TEXT, Paint.Align.LEFT);
        float vx = x + th.px(110);
        float maxW = a.right - vx;
        String[][] status = {
                {t.state, on ? t.recording : t.stopped},
                {t.file, logger.file() == null ? "—" : logger.file().getName()},
                {t.size, logger.file() == null ? "—" : Fmt.bytes(logger.bytes()) + " / " + Fmt.bytes(logger.maxBytes())},
                {t.time, Fmt.duration(logger.elapsedMs())},
                {t.rows, Integer.toString(logger.rows())},
                {t.sensors, Integer.toString(on ? logger.columnCount() : hub.channels().size())},
        };
        for (int i = 0; i < status.length; i++) {
            c.drawText(status[i][0], x, y, k);
            v.setColor(i == 0 && on ? Theme.OK : Theme.TEXT);
            c.drawText(Theme.fit(v, status[i][1], maxW), vx, y, v);
            y += th.px(24);
        }
        if (logger.error() != null) {
            Paint e = th.bold(14, Theme.HOT, Paint.Align.LEFT);
            c.drawText(Theme.fit(e, t.errorPrefix + logger.error(), a.right - x), x, y, e);
        }

        // storage options
        float st = a.top + th.px(180);
        c.drawText(t.storageTitle, x, st, th.bold(14, Theme.DIM, Paint.Align.LEFT));
        rescanBtn.set(a.right - th.px(140), st - th.px(24), a.right, st + th.px(8));
        th.button(c, rescanBtn, t.rescan, Theme.PANEL_HI, Theme.TEXT, 14);
        float oy = st + th.px(16);
        float rowH = th.px(34);
        optionCount = 0;
        for (int i = 0; i < options.size() && i < MAX_OPTIONS && oy + rowH <= a.bottom; i++) {
            Storage.Option o = options.get(i);
            RectF r = optionRects[i];
            r.set(x, oy, a.right, oy + rowH - th.px(4));
            optionCount = i + 1;
            th.panel(c, r.left, r.top, r.right, r.bottom, i == chosen ? Theme.PANEL_HI : Theme.PANEL);
            if (i == chosen) {
                th.outline(c, r.left, r.top, r.right, r.bottom, Theme.ACCENT, 2);
            }
            Paint p = th.text(15, o.writable ? Theme.TEXT : Theme.DIM, Paint.Align.LEFT);
            c.drawText(Theme.fit(p, o.displayPath, r.width() - th.px(190)), r.left + th.px(10),
                    r.centerY() + th.px(5), p);
            String kind;
            int kc;
            if (!o.writable) {
                kind = t.readOnly;
                kc = Theme.DIM;
            } else if (o.ram) {
                kind = "RAM";
                kc = Theme.WARN;
            } else {
                kind = (o.flash ? t.usbStorage : t.writable)
                        + (o.freeBytes >= 0 ? " · " + Fmt.bytes(o.freeBytes) + " " + t.freeSuffix : "");
                kc = o.recommended() ? Theme.OK : Theme.WARN;
            }
            c.drawText(kind, r.right - th.px(10), r.centerY() + th.px(5), th.text(13, kc, Paint.Align.RIGHT));
            oy += rowH;
        }
        if (scanning && options.isEmpty()) {
            c.drawText(t.scanning, x, oy + th.px(20), th.text(14, Theme.DIM, Paint.Align.LEFT));
        } else if (options.isEmpty()) {
            c.drawText(t.noStorage, x, oy + th.px(20),
                    th.text(14, Theme.HOT, Paint.Align.LEFT));
        }
    }

    public boolean onTap(float x, float y) {
        if (startBtn.contains(x, y)) {
            Storage.Option t = target();
            if (!logger.isActive() && (t == null || !t.writable)) {
                host.message(Strings.get().chooseWritable);
            } else {
                host.toggleLogging(t);
            }
            return true;
        }
        if (markerBtn.contains(x, y)) {
            host.marker();
            return true;
        }
        if (rescanBtn.contains(x, y)) {
            if (logger.isActive()) {
                host.message(Strings.get().stopBeforeChange);
            } else if (!scanning) {
                rescan();
            }
            return true;
        }
        for (int i = 0; i < optionCount; i++) {
            if (optionRects[i].contains(x, y)) {
                if (logger.isActive()) {
                    host.message(Strings.get().stopBeforeChange);
                } else if (!options.get(i).writable) {
                    host.message(Strings.get().cannotWriteHere);
                } else {
                    chosen = i;
                    host.saveStoragePath(options.get(i).root.getAbsolutePath());
                    if (options.get(i).ram) {
                        host.message(Strings.get().ramStorage);
                    }
                }
                return true;
            }
        }
        return false;
    }
}
