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

/** Start/stop CSV logging, markers, and choice of where the files go. */
final class LogPage implements Page {
    private final DataHub hub;
    private final CsvLogger logger;
    private final LogPageHost host;
    private List<Storage.Option> options = new ArrayList<Storage.Option>();
    private int chosen = -1;

    private final RectF startBtn = new RectF();
    private final RectF markerBtn = new RectF();
    private final RectF rescanBtn = new RectF();
    private final List<RectF> optionRects = new ArrayList<RectF>();

    LogPage(DataHub hub, CsvLogger logger, LogPageHost host) {
        this.hub = hub;
        this.logger = logger;
        this.host = host;
    }

    public String title() {
        return "LOG";
    }

    public void onShow() {
        if (options.isEmpty()) {
            rescan();
        }
    }

    private void rescan() {
        options = Storage.scan();
        chosen = -1;
        String saved = host.savedStoragePath();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).writable && options.get(i).root.getAbsolutePath().equals(saved)) {
                chosen = i;
            }
        }
        for (int i = 0; chosen < 0 && i < options.size(); i++) {
            if (options.get(i).writable) {
                chosen = i;
            }
        }
    }

    Storage.Option target() {
        return chosen >= 0 && chosen < options.size() ? options.get(chosen) : null;
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        boolean on = logger.isActive();
        float colW = th.px(300);
        startBtn.set(a.left, a.top, a.left + colW, a.top + th.px(110));
        markerBtn.set(a.left, startBtn.bottom + th.px(10), a.left + colW, startBtn.bottom + th.px(120));
        th.button(c, startBtn, on ? "STOP" : "START LOG", on ? Theme.HOT : Theme.OK, Theme.BG, 30);
        th.button(c, markerBtn, "MARKER" + (on ? "  #" + (logger.markers() + 1) : ""),
                on ? Theme.ACCENT : Theme.PANEL, on ? Theme.BG : Theme.DIM, 26);
        Paint hint = th.text(13, Theme.DIM, Paint.Align.LEFT);
        float hy = markerBtn.bottom + th.px(24);
        c.drawText("Marker = znacznik w logu (np. „teraz skręcam”),", a.left, hy, hint);
        c.drawText("dostępny też pod kropką REC na pasku zakładek.", a.left, hy + th.px(18), hint);
        c.drawText("Zapis: 10 wierszy/s, wszystkie sensory, v0–v2.", a.left, hy + th.px(36), hint);

        // status
        float x = a.left + colW + th.px(24);
        float y = a.top + th.px(20);
        Paint k = th.text(15, Theme.DIM, Paint.Align.LEFT);
        Paint v = th.bold(15, Theme.TEXT, Paint.Align.LEFT);
        float vx = x + th.px(110);
        float maxW = a.right - vx;
        String[][] status = {
                {"Stan", on ? "zapis trwa" : "zatrzymany"},
                {"Plik", logger.file() == null ? "—" : logger.file().getName()},
                {"Rozmiar", logger.file() == null ? "—" : Fmt.bytes(logger.file().length())},
                {"Czas", Fmt.duration(logger.elapsedMs())},
                {"Wiersze", Integer.toString(logger.rows())},
                {"Sensory", Integer.toString(on ? logger.columnCount() : hub.channels().size())},
        };
        for (int i = 0; i < status.length; i++) {
            c.drawText(status[i][0], x, y, k);
            v.setColor(i == 0 && on ? Theme.OK : Theme.TEXT);
            c.drawText(Theme.fit(v, status[i][1], maxW), vx, y, v);
            y += th.px(24);
        }
        if (logger.error() != null) {
            Paint e = th.bold(14, Theme.HOT, Paint.Align.LEFT);
            c.drawText(Theme.fit(e, "Błąd: " + logger.error(), a.right - x), x, y, e);
        }

        // storage options
        float st = a.top + th.px(180);
        c.drawText("MIEJSCE ZAPISU", x, st, th.bold(14, Theme.DIM, Paint.Align.LEFT));
        rescanBtn.set(a.right - th.px(140), st - th.px(24), a.right, st + th.px(8));
        th.button(c, rescanBtn, "SKANUJ", Theme.PANEL_HI, Theme.TEXT, 14);
        float oy = st + th.px(16);
        float rowH = th.px(34);
        optionRects.clear();
        for (int i = 0; i < options.size() && oy + rowH <= a.bottom; i++) {
            Storage.Option o = options.get(i);
            RectF r = new RectF(x, oy, a.right, oy + rowH - th.px(4));
            optionRects.add(r);
            th.panel(c, r.left, r.top, r.right, r.bottom, i == chosen ? Theme.PANEL_HI : Theme.PANEL);
            if (i == chosen) {
                th.outline(c, r.left, r.top, r.right, r.bottom, Theme.ACCENT, 2);
            }
            Paint p = th.text(15, o.writable ? Theme.TEXT : Theme.DIM, Paint.Align.LEFT);
            c.drawText(Theme.fit(p, o.logDir().getAbsolutePath(), r.width() - th.px(110)), r.left + th.px(10),
                    r.centerY() + th.px(5), p);
            c.drawText(o.writable ? "zapis OK" : "tylko odczyt", r.right - th.px(10), r.centerY() + th.px(5),
                    th.text(13, o.writable ? Theme.OK : Theme.DIM, Paint.Align.RIGHT));
            oy += rowH;
        }
        if (options.isEmpty()) {
            c.drawText("Brak miejsc do zapisu – włóż pendrive i naciśnij SKANUJ", x, oy + th.px(20),
                    th.text(14, Theme.HOT, Paint.Align.LEFT));
        }
    }

    public boolean onTap(float x, float y) {
        if (startBtn.contains(x, y)) {
            Storage.Option t = target();
            if (!logger.isActive() && (t == null || !t.writable)) {
                host.message("Wybierz miejsce zapisu z oznaczeniem „zapis OK”");
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
                host.message("Zatrzymaj zapis przed zmianą miejsca");
            } else {
                rescan();
            }
            return true;
        }
        for (int i = 0; i < optionRects.size(); i++) {
            if (optionRects.get(i).contains(x, y)) {
                if (logger.isActive()) {
                    host.message("Zatrzymaj zapis przed zmianą miejsca");
                } else if (!options.get(i).writable) {
                    host.message("Tu nie da się zapisać");
                } else {
                    chosen = i;
                    host.saveStoragePath(options.get(i).root.getAbsolutePath());
                }
                return true;
            }
        }
        return false;
    }
}
