package pl.q50.app.logic;

import java.util.HashMap;
import java.util.Map;

/** Plain-JVM checks for the computed features. Run: bash scripts/test-java.sh */
public final class LogicTest {
    private static int failures;
    private static final long TICK = 100;

    static final class MapStore implements Store {
        final Map<String, String> map = new HashMap<String, String>();

        public String get(String key) {
            return map.get(key);
        }

        public void put(String key, String value) {
            map.put(key, value);
        }
    }

    public static void main(String[] args) {
        perfTimer();
        braking();
        turbo();
        oilPressure();
        tyres();
        alerts();
        trip();
        dyno();
        settings();
        warmUp();
        gMeter();
        alertHistory();
        perfRejectsJumps();
        turboHotOil();
        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("LogicTest OK");
    }

    /** Constant 20 km/h per second from standstill: 0–100 in 5 s, quarter mile in ~12 s. */
    static void perfTimer() {
        PerfTimer p = new PerfTimer();
        Snapshot s = new Snapshot();
        long t = 0;
        for (; t <= 2000; t += TICK) {
            s.now = t;
            s.speed = 0;
            p.update(s);
        }
        check(p.armed(), "armed while stopped");
        long launch = t;
        for (; t <= launch + 14000; t += TICK) {
            s.now = t;
            s.speed = Math.min(260f, (t - launch) / 1000f * 20f);
            p.update(s);
        }
        near("0-100", 5000 - 25, p.last[PerfTimer.ZERO_100].timeMs, 40);
        near("60-100", 2000, p.last[PerfTimer.R60_100].timeMs, 20);
        near("80-120", 2000, p.last[PerfTimer.R80_120].timeMs, 20);
        near("100-200", 5000, p.last[PerfTimer.R100_200].timeMs, 20);
        // s = a t^2 / 2 with a = 20/3.6 m/s^2, from the 0.5 km/h start threshold
        double a = 20 / 3.6;
        double tq = Math.sqrt(2 * PerfTimer.QUARTER_M / a);
        near("quarter", (long) (tq * 1000) - 25, p.last[PerfTimer.QUARTER].timeMs, 80);
        near("quarter trap", (long) (tq * 20), (long) p.last[PerfTimer.QUARTER].extra, 2);
        check(p.best[PerfTimer.ZERO_100].timeMs == p.last[PerfTimer.ZERO_100].timeMs, "best = last after first run");

        MapStore store = new MapStore();
        p.save(store);
        PerfTimer q = new PerfTimer();
        q.load(store);
        check(q.best[PerfTimer.QUARTER].timeMs == p.best[PerfTimer.QUARTER].timeMs, "best persisted");
    }

    /** 120 km/h, then constant 40 km/h per second braking: 100–0 in 2.5 s over ~34.7 m. */
    static void braking() {
        PerfTimer p = new PerfTimer();
        Snapshot s = new Snapshot();
        long t = 0;
        for (; t < 1000; t += TICK) {
            s.now = t;
            s.speed = 120;
            p.update(s);
        }
        long b0 = t;
        for (; t < b0 + 5000; t += TICK) {
            s.now = t;
            s.speed = Math.max(0f, 120f - (t - b0) / 1000f * 40f);
            p.update(s);
        }
        near("100-0 time", 2500 - 12, p.last[PerfTimer.BRAKE_100_0].timeMs, 30);
        double dist = (100 / 3.6) * (100 / 3.6) / (2 * 40 / 3.6);
        near("100-0 distance x10", (long) (dist * 10), (long) (p.last[PerfTimer.BRAKE_100_0].extra * 10), 5);
    }

    static void turbo() {
        TurboCooldown c = new TurboCooldown();
        AlertEngine alerts = new AlertEngine();
        Snapshot s = new Snapshot();
        s.rpm = 5000;
        s.speed = 150;
        s.power = 250;
        s.throttle = 90;
        long t = 0;
        for (; t <= 60000; t += TICK) {
            s.now = t;
            c.update(s);
        }
        near("cooldown after 60 s hard x1", 90, Math.round(c.remainingS()), 1);
        s.rpm = 700;
        s.speed = 0;
        s.power = 5;
        s.throttle = 0;
        s.gear = 1;
        long idle0 = t;
        for (; t <= idle0 + 30000; t += TICK) {
            s.now = t;
            c.update(s);
            alerts.set(AlertEngine.TURBO_COOLDOWN, s.engineRunning() && s.parked() && c.needsCooldown(), "x");
            alerts.update(t, null);
        }
        near("cooldown after 30 s idle", 60, Math.round(c.remainingS()), 1);
        check(alerts.isActive(AlertEngine.TURBO_COOLDOWN), "turbo reminder while parked");
    }

    static void oilPressure() {
        OilPressureMonitor m = new OilPressureMonitor();
        Snapshot s = new Snapshot();
        s.rpm = 2100;
        s.oilTemp = 95;
        s.oilPress = 2.5f;
        check(Float.isNaN(m.expected(2100)), "nothing learned yet");
        for (int i = 0; i < 200; i++) m.update(s);
        near("learned x10", 25, Math.round(m.expected(2100) * 10), 0);
        s.oilPress = 1.5f;
        check(m.belowBaseline(s), "1.5 bar < 70% of 2.5 bar");
        s.oilPress = 2.0f;
        check(!m.belowBaseline(s), "2.0 bar is fine");
        s.oilTemp = 50;
        s.oilPress = 1.0f;
        check(!m.belowBaseline(s), "cold oil is not compared to the warm baseline");
        s.oilPress = 0.4f;
        check(m.belowAbsolute(s), "absolute minimum");
        s.rpm = 0;
        check(!m.belowAbsolute(s), "engine off is not an alarm");

        MapStore store = new MapStore();
        m.save(store);
        OilPressureMonitor n = new OilPressureMonitor();
        n.load(store);
        near("baseline persisted x10", 25, Math.round(n.expected(2100) * 10), 0);
    }

    static void tyres() {
        check(TyreMonitor.asymmetricTyre(new float[]{2.4f, 2.4f, 2.4f, 2.1f}) == Snapshot.RR, "asymmetric RR");
        check(TyreMonitor.asymmetricTyre(new float[]{2.4f, 2.35f, 2.4f, 2.3f}) == -1, "within tolerance");
        check(TyreMonitor.lowTyre(new float[]{1.9f, 2.4f, 1.95f, 2.4f}) == Snapshot.FL, "lowest tyre below 2.0");

        long day = TyreMonitor.DAY_MS;
        long now = 100 * day;
        MapStore store = new MapStore();
        store.put("tyre_history", (now - 5 * day) + ",2.4,2.4,2.4,2.4");
        TyreMonitor m = new TyreMonitor();
        m.load(store);
        Snapshot s = new Snapshot();
        s.tyres[0] = 2.3f;
        s.tyres[1] = 2.3f;
        s.tyres[2] = 2.3f;
        s.tyres[3] = 2.05f; // everything 0.1 lower (colder day), RR 0.35 lower
        m.update(s, now);
        check(m.leakTyre == Snapshot.RR, "slow leak on RR, got " + m.leakTyre);
        near("leak days", 5, m.leakDays, 0);
        near("leak drop x100", 19, Math.round(m.leakDropBar * 100), 1);
        m.save(store);
        TyreMonitor n = new TyreMonitor();
        n.load(store);
        check(n.history().size() == 2, "history persisted");

        TyreMonitor uniform = new TyreMonitor();
        uniform.load(new MapStore());
        uniform.update(s, now);
        check(uniform.leakTyre == -1, "no history, no leak");
    }

    static void alerts() {
        AlertEngine e = new AlertEngine();
        Settings settings = new Settings();
        e.set(AlertEngine.COOLANT_HOT, true, "hot");
        e.update(0, settings);
        check(e.visible().isEmpty(), "on-delay");
        e.update(2500, settings);
        check(e.visible().size() == 1 && e.raisedSeverity == AlertEngine.CRITICAL, "raised after delay");
        e.set(AlertEngine.TYRE_ASYM, true, "tyre");
        e.update(2600, settings);
        e.update(8000, settings);
        check(e.visible().size() == 2 && e.visible().get(0).id.equals(AlertEngine.COOLANT_HOT), "critical first");
        e.snooze(AlertEngine.COOLANT_HOT, 8000);
        e.update(8100, settings);
        check(e.visible().size() == 1, "snoozed");
        settings.toggleAlert(AlertEngine.TYRE_ASYM);
        e.update(8200, settings);
        check(e.visible().isEmpty(), "disabled in settings");
        e.set(AlertEngine.COOLANT_HOT, false, null);
        e.update(9000, settings);
        check(e.isActive(AlertEngine.COOLANT_HOT), "off-delay keeps it active");
        e.update(11500, settings);
        check(!e.isActive(AlertEngine.COOLANT_HOT), "cleared after off-delay");
    }

    static void trip() {
        TripStats tr = new TripStats();
        Snapshot s = new Snapshot();
        s.rpm = 2500;
        s.speed = 60;
        s.gear = 4;
        s.throttle = 20;
        for (long t = 0; t <= 60000; t += TICK) {
            s.now = t;
            tr.update(s);
        }
        near("distance m", 1000, Math.round(tr.distanceM), 2);
        near("avg speed", 60, Math.round(tr.avgSpeed()), 0);
        near("gear D ms", 60000, tr.gearMs[3], 100);
        near("band 2000-3000 ms", 60000, tr.rpmBandMs[1], 100);
        check(tr.spiritedMs == 0, "calm driving");
        check(TripStats.gearSlot(16) == 4 && TripStats.gearSlot(22) == 10 && TripStats.gearSlot(9) == -1, "gear slots");
    }

    static void dyno() {
        Dyno d = new Dyno();
        Snapshot s = new Snapshot();
        s.throttle = 50;
        s.rpm = 3100;
        s.power = 999;
        d.update(s);
        check(d.isEmpty(), "part throttle ignored");
        s.throttle = 95;
        s.power = 200;
        s.torque = 400;
        d.update(s);
        s.rpm = 6400;
        s.power = 305;
        s.torque = 335;
        d.update(s);
        check(Dyno.binCenterRpm(Dyno.peak(d.power)) == 6375, "power peak bin");
        check(Dyno.binCenterRpm(Dyno.peak(d.torque)) == 3125, "torque peak bin");
    }

    static void settings() {
        Settings a = new Settings();
        a.toggleTile("g_lat");
        a.toggleAlert(AlertEngine.TURBO_COOLDOWN);
        a.toggleSound();
        MapStore store = new MapStore();
        a.save(store);
        Settings b = new Settings();
        b.load(store);
        check(!b.isTileVisible("g_lat") && b.isTileVisible("rpm"), "tiles persisted");
        check(!b.isAlertEnabled(AlertEngine.TURBO_COOLDOWN) && b.isAlertEnabled(AlertEngine.OIL_HOT), "alerts persisted");
        check(b.sound(), "sound persisted");
        check(b.visibleTileCount() == Settings.TILES.length - 1, "visible count");
    }

    static void warmUp() {
        WarmUp w = new WarmUp();
        Snapshot s = new Snapshot();
        for (long t = 0; t <= 600000; t += 1000) {
            s.now = t;
            s.coolant = Math.min(90f, 20f + t / 1000f * 0.25f); // 80 °C after 240 s
            s.oilTemp = Math.min(100f, 20f + t / 1000f * 0.2f); // 80 °C after 300 s
            w.update(s);
        }
        check(w.coldStart(), "cold start");
        near("coolant warm ms", 240000, w.coolantWarmMs, 1000);
        near("oil warm ms", 300000, w.oilWarmMs, 1000);
        check(w.state() == WarmUp.WARM, "warm at the end");
        s.oilTemp = 40;
        s.rpm = 4500;
        check(WarmUp.coldOilUnderLoad(s), "cold oil at 4500 rpm");
    }

    static void turboHotOil() {
        check(TurboCooldown.heatFactor(90f) == 1f && TurboCooldown.heatFactor(130f) == 1.5f, "heat factor");
        check(TurboCooldown.coolFactor(Float.NaN) == 1f && TurboCooldown.coolFactor(120f) == 0.5f, "cool factor");
        near("cool factor at 110 x100", 75, Math.round(TurboCooldown.coolFactor(110f) * 100), 0);

        TurboCooldown c = new TurboCooldown();
        Snapshot s = new Snapshot();
        s.rpm = 5000;
        s.speed = 150;
        s.power = 250;
        s.throttle = 90;
        s.oilTemp = 120;
        long t = 0;
        for (; t <= 60000; t += TICK) {
            s.now = t;
            c.update(s);
        }
        near("60 s hard on 120 °C oil", 135, Math.round(c.remainingS()), 1);
        check(c.level() == TurboCooldown.LEVEL_RED && c.oilSlowsCooling(), "red, hot oil slows cooling");
        s.rpm = 700;
        s.speed = 0;
        s.power = 5;
        s.throttle = 0;
        long idle0 = t;
        for (; t <= idle0 + 30000; t += TICK) {
            s.now = t;
            c.update(s);
        }
        near("30 s idle on 120 °C oil cools only 15 s", 120, Math.round(c.remainingS()), 1);
        s.oilTemp = 90;
        while (c.level() == TurboCooldown.LEVEL_RED) {
            s.now = t += TICK;
            c.update(s);
        }
        near("orange from 45 s", 45, Math.round(c.remainingS()), 1);
        while (c.level() == TurboCooldown.LEVEL_ORANGE) {
            s.now = t += TICK;
            c.update(s);
        }
        near("green below 15 s", 15, Math.round(c.remainingS()), 1);
    }

    /** A data gap that makes speed jump 0 -> 60 -> 100 must not produce timing results. */
    static void perfRejectsJumps() {
        PerfTimer p = new PerfTimer();
        Snapshot s = new Snapshot();
        float[] v = {0, 0, 0, 60, 100, 0, 0, 120, 120};
        for (int i = 0; i < v.length; i++) {
            s.now = i * TICK;
            s.speed = v[i];
            p.update(s);
        }
        for (int r = 0; r < PerfTimer.COUNT; r++) {
            check(p.last[r].timeMs < 0, "no result from a speed jump, run " + r);
        }
    }

    static void gMeter() {
        GMeter g = new GMeter();
        Snapshot s = new Snapshot();
        float[][] pts = {{0.8f, 0f}, {-0.6f, 0f}, {0f, 0.4f}, {0f, -0.9f}, {0.5f, 0.5f}};
        for (int i = 0; i < pts.length; i++) {
            s.gLat = pts[i][0];
            s.gLong = pts[i][1];
            g.update(s);
        }
        near("max lat + x100", 80, Math.round(g.maxLatPlus * 100), 0);
        near("max lat - x100", 60, Math.round(g.maxLatMinus * 100), 0);
        near("max accel x100", 50, Math.round(g.maxAccel * 100), 0);
        near("max brake x100", 90, Math.round(g.maxBrake * 100), 0);
        near("max combined x100", 90, Math.round(g.maxCombined * 100), 0);
        check(GMeter.sector(1f, 0f) == 0, "sector of +lat");
        check(GMeter.sector(0f, 1f) == 9, "sector of +lon (90 deg)");
        check(GMeter.sector(0f, -1f) == 27, "sector of -lon (270 deg)");
        near("envelope at 45 deg x100", 71, Math.round(g.sectorMax[GMeter.sector(0.5f, 0.5f)] * 100), 0);
        check(g.trailCount() == 5 && g.trailLat(0) == 0.8f && g.trailLon(4) == 0.5f, "trail order");
        s.gLat = Float.NaN;
        g.update(s);
        check(g.trailCount() == 5, "NaN not added to trail");
        g.reset();
        check(Float.isNaN(g.maxCombined) && g.sectorMax[0] == 0f, "reset");
    }

    static void alertHistory() {
        AlertEngine e = new AlertEngine();
        Settings settings = new Settings();
        e.set(AlertEngine.OIL_HOT, true, "Olej 127 °C");
        e.update(0, settings);
        e.update(2500, settings);
        e.update(5000, settings);
        check(e.history().size() == 1 && e.history().get(0).text.equals("Olej 127 °C"), "history records once");
        e.snooze(AlertEngine.OIL_HOT, 5000);
        check(e.snoozeRemainingMs(AlertEngine.OIL_HOT, 6000) == AlertEngine.SNOOZE_MS - 1000, "snooze remaining");
        e.unsnooze(AlertEngine.OIL_HOT);
        e.update(6000, settings);
        check(e.visible().size() == 1, "unsnoozed");
        settings.toggleAlert(AlertEngine.TYRE_LOW);
        e.set(AlertEngine.TYRE_LOW, true, "x");
        e.update(7000, settings);
        e.update(13000, settings);
        check(e.history().size() == 1, "disabled alerts are not recorded");
    }

    private static void near(String what, long expected, long actual, long tolerance) {
        check(Math.abs(expected - actual) <= tolerance, what + ": expected " + expected + " ±" + tolerance + ", got " + actual);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
