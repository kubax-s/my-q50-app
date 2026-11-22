package pl.q50.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

import pl.q50.app.data.Channel;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;

/**
 * Sensor explorer: every sensor the head unit exposes, with live values[0..2], update rate and
 * observed range. Tapping a row shows a 20 s chart – the main tool for mapping unknown signals.
 */
final class SensorsPage implements Page {
    private static final float HEAD_H = 44;
    private static final float ROW_H = 27;
    private static final float DETAIL_H = 92;
    private static final float SIDE_W = 64;

    private final DataHub hub;
    private final List<Channel> rows = new ArrayList<Channel>();
    private int filter;
    private int scroll;
    private int visibleRows;
    private Channel selected;

    private final RectF filterBtn = new RectF();
    private final RectF resetBtn = new RectF();
    private final RectF upBtn = new RectF();
    private final RectF downBtn = new RectF();
    private final RectF table = new RectF();
    private float rowHpx = 1;
    private final Path path = new Path();

    SensorsPage(DataHub hub) {
        this.hub = hub;
    }

    public String title() {
        return Strings.get().tabSensors;
    }

    public void onShow() {
    }

    private void collect() {
        rows.clear();
        List<Channel> all = hub.channels();
        for (int i = 0; i < all.size(); i++) {
            Channel c = all.get(i);
            if (filter == 0 && !c.vehicle) continue;
            if (filter == 1 && !c.varies()) continue;
            rows.add(c);
        }
    }

    public void draw(Canvas c, RectF a, Theme th, long now) {
        Strings tr = Strings.get();
        collect();
        int varying = 0;
        List<Channel> all = hub.channels();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).varies()) varying++;
        }

        // header: buttons + counters
        float hb = a.top + th.px(HEAD_H - 8);
        filterBtn.set(a.left, a.top, a.left + th.px(190), hb);
        resetBtn.set(filterBtn.right + th.px(8), a.top, filterBtn.right + th.px(178), hb);
        th.button(c, filterBtn, tr.filterPrefix + tr.filters[filter], Theme.PANEL_HI, Theme.TEXT, 15);
        th.button(c, resetBtn, tr.resetMinMax, Theme.PANEL_HI, Theme.TEXT, 15);
        c.drawText(tr.sensorsSummary(all.size(), varying, rows.size()),
                a.right, a.top + th.px(24), th.text(14, Theme.DIM, Paint.Align.RIGHT));

        // table area
        table.set(a.left, a.top + th.px(HEAD_H), a.right - th.px(SIDE_W + 8), a.bottom - th.px(DETAIL_H + 8));
        rowHpx = th.px(ROW_H);
        visibleRows = Math.max(1, (int) ((table.height() - rowHpx) / rowHpx));
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;

        float[] col = columns(th, table);
        Paint hp = th.text(12, Theme.DIM, Paint.Align.LEFT);
        float y = table.top + th.px(20);
        String[] heads = tr.tableHeads;
        for (int i = 0; i < heads.length; i++) {
            c.drawText(heads[i], col[i], y, hp);
        }
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            Channel ch = rows.get(scroll + i);
            float top = table.top + rowHpx * (i + 1);
            if (ch == selected) {
                th.panel(c, table.left, top + th.px(1), table.right, top + rowHpx - th.px(1), Theme.PANEL_HI);
            } else if (i % 2 == 0) {
                th.panel(c, table.left, top + th.px(1), table.right, top + rowHpx - th.px(1), Theme.PANEL);
            }
            drawRow(c, th, ch, col, top + rowHpx * 0.68f, now);
        }

        // scroll buttons
        upBtn.set(a.right - th.px(SIDE_W), table.top, a.right, table.centerY() - th.px(4));
        downBtn.set(a.right - th.px(SIDE_W), table.centerY() + th.px(4), a.right, table.bottom);
        th.arrowButton(c, upBtn, true, scroll > 0);
        th.arrowButton(c, downBtn, false, scroll < maxScroll);

        drawDetail(c, th, a.left, a.bottom - th.px(DETAIL_H), a.right, a.bottom);
    }

    private float[] columns(Theme th, RectF t) {
        float l = t.left + th.px(6);
        return new float[]{
                l, l + th.px(44), l + th.px(270), l + th.px(350), l + th.px(420), l + th.px(490), l + th.px(540)};
    }

    private void drawRow(Canvas c, Theme th, Channel ch, float[] col, float y, long now) {
        int fg = ch.hasValue() ? Theme.TEXT : Theme.DIM;
        c.drawText(Integer.toString(ch.type), col[0], y, th.bold(15, ch.vehicle ? Theme.ACCENT : Theme.DIM, Paint.Align.LEFT));
        float nameW = col[2] - col[1] - th.px(8);
        float x = col[1];
        if (ch.def != null) {
            Paint lp = th.bold(14, Theme.ACCENT, Paint.Align.LEFT);
            String lab = ch.def.label() + " ";
            c.drawText(lab, x, y, lp);
            float lw = lp.measureText(lab);
            x += lw;
            nameW -= lw;
        }
        Paint np = th.text(14, fg, Paint.Align.LEFT);
        String name = ch.name.length() > 0 ? ch.name : ch.vendor;
        if (ch.demo) name = "[DEMO] " + name;
        c.drawText(Theme.fit(np, name, Math.max(th.px(20), nameW)), x, y, np);

        Paint vp = th.bold(15, fg, Paint.Align.LEFT);
        for (int k = 0; k < 3; k++) {
            String v = ch.hasValue() && k < ch.valueCount ? Fmt.raw(ch.values[k]) : "—";
            c.drawText(Theme.fit(vp, v, th.px(k == 0 ? 76 : 66)), col[2 + k], y, vp);
        }
        String hz = !ch.hasValue() ? "—" : Fmt.number(ch.rateHz, ch.rateHz < 10 ? 1 : 0);
        c.drawText(hz, col[5], y, th.text(14, Theme.DIM, Paint.Align.LEFT));
        String range = ch.hasValue() ? Fmt.raw(ch.min) + " … " + Fmt.raw(ch.max) : "";
        Paint rp = th.text(13, ch.varies() ? Theme.OK : Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(rp, range, table.right - col[6] - th.px(4)), col[6], y, rp);
    }

    private void drawDetail(Canvas c, Theme th, float l, float t, float r, float b) {
        th.panel(c, l, t, r, b, Theme.PANEL);
        Channel ch = selected;
        if (ch == null || !hub.channels().contains(ch)) {
            selected = null;
            c.drawText(Strings.get().tapRowHint, (l + r) / 2, (t + b) / 2 + th.px(6),
                    th.text(15, Theme.DIM, Paint.Align.CENTER));
            return;
        }
        Strings tr = Strings.get();
        float infoW = th.px(230);
        c.drawText(tr.typePrefix + ch.type + (ch.def != null ? " · " + ch.def.label() : ""), l + th.px(12), t + th.px(24),
                th.bold(15, Theme.ACCENT, Paint.Align.LEFT));
        c.drawText(ch.formatted() + (ch.def != null ? " " + ch.def.unit() : ""), l + th.px(12), t + th.px(58),
                th.bold(30, Theme.TEXT, Paint.Align.LEFT));
        Paint sp = th.text(12, Theme.DIM, Paint.Align.LEFT);
        c.drawText(Theme.fit(sp, ch.vendor + " · " + tr.range + " " + Fmt.raw(ch.maxRange) + " · " + tr.resolution + " " + Fmt.raw(ch.resolution),
                infoW - th.px(12)), l + th.px(12), t + th.px(80), sp);

        // 20 s chart of raw v0, auto-scaled
        float cl = l + infoW;
        float cr = r - th.px(12);
        float ct = t + th.px(12);
        float cb = b - th.px(12);
        th.stroke.setColor(Theme.LINE);
        th.stroke.setStrokeWidth(th.px(1));
        c.drawRect(cl, ct, cr, cb, th.stroke);
        int n = ch.historyCount();
        float lo = Float.NaN;
        float hi = Float.NaN;
        for (int i = 0; i < n; i++) {
            float v = ch.history(i);
            if (Float.isNaN(v)) continue;
            if (Float.isNaN(lo) || v < lo) lo = v;
            if (Float.isNaN(hi) || v > hi) hi = v;
        }
        if (n < 2 || Float.isNaN(lo)) {
            return;
        }
        if (hi - lo < 1e-6f) {
            hi = lo + 1;
            lo = lo - 1;
        }
        path.reset();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            float v = ch.history(i);
            if (Float.isNaN(v)) {
                started = false;
                continue;
            }
            float x = cl + (cr - cl) * i / (Channel.HISTORY - 1);
            float y = cb - (cb - ct) * (v - lo) / (hi - lo);
            if (started) {
                path.lineTo(x, y);
            } else {
                path.moveTo(x, y);
                started = true;
            }
        }
        th.stroke.setColor(Theme.ACCENT);
        th.stroke.setStrokeWidth(th.px(2));
        c.drawPath(path, th.stroke);
        Paint lp = th.text(11, Theme.DIM, Paint.Align.RIGHT);
        c.drawText(Fmt.raw(hi), cr - th.px(4), ct + th.px(13), lp);
        c.drawText(Fmt.raw(lo), cr - th.px(4), cb - th.px(4), lp);
    }

    public boolean onTap(float x, float y) {
        if (filterBtn.contains(x, y)) {
            filter = (filter + 1) % 3;
            scroll = 0;
            return true;
        }
        if (resetBtn.contains(x, y)) {
            hub.resetMinMax();
            return true;
        }
        if (upBtn.contains(x, y)) {
            scroll = Math.max(0, scroll - visibleRows);
            return true;
        }
        if (downBtn.contains(x, y)) {
            scroll = Math.min(Math.max(0, rows.size() - visibleRows), scroll + visibleRows);
            return true;
        }
        if (table.contains(x, y)) {
            int i = (int) ((y - table.top) / rowHpx) - 1;
            if (i >= 0 && scroll + i < rows.size()) {
                Channel ch = rows.get(scroll + i);
                selected = ch == selected ? null : ch;
                return true;
            }
        }
        return false;
    }
}
