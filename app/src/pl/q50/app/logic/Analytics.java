package pl.q50.app.logic;

import pl.q50.app.data.Fmt;
import pl.q50.app.i18n.Strings;

/** Everything computed from the live signals, updated once per tick. */
public final class Analytics {
    public static final float COOLANT_HOT_ON = 105f;
    public static final float COOLANT_HOT_OFF = 102f;
    public static final float OIL_HOT_ON = 125f;
    public static final float OIL_HOT_OFF = 122f;

    public final Settings settings;
    public final TripStats trip = new TripStats();
    public final PerfTimer perf = new PerfTimer();
    public final Dyno dyno = new Dyno();
    public final GMeter gMeter = new GMeter();
    public final TurboCooldown turbo = new TurboCooldown();
    public final WarmUp warmUp = new WarmUp();
    public final OilPressureMonitor oil = new OilPressureMonitor();
    public final TyreMonitor tyres = new TyreMonitor();
    public final AlertEngine alerts = new AlertEngine();
    /** False while the app runs lean in the background: acceleration timing is paused. */
    public boolean timingEnabled = true;
    /** Latest snapshot, for pages that draw live markers. */
    public Snapshot last;

    public Analytics(Settings settings) {
        this.settings = settings;
    }

    public void update(Snapshot s, long wallMs) {
        last = s;
        trip.update(s);
        if (timingEnabled) {
            perf.update(s);
        }
        dyno.update(s);
        gMeter.update(s);
        turbo.update(s);
        warmUp.update(s);
        oil.update(s);
        tyres.update(s, wallMs);
        evaluateAlerts(s);
        alerts.update(s.now, settings);
    }

    /** Alert texts are only built while a condition holds, so a quiet drive allocates nothing here. */
    private void evaluateAlerts(Snapshot s) {
        Strings t = Strings.get();

        boolean coolantHot = Snapshot.ok(s.coolant)
                && s.coolant >= (alerts.isActive(AlertEngine.COOLANT_HOT) ? COOLANT_HOT_OFF : COOLANT_HOT_ON);
        alerts.set(AlertEngine.COOLANT_HOT, coolantHot, coolantHot ? t.alertCoolantHot(Fmt.number(s.coolant, 0)) : null);

        boolean oilHot = Snapshot.ok(s.oilTemp)
                && s.oilTemp >= (alerts.isActive(AlertEngine.OIL_HOT) ? OIL_HOT_OFF : OIL_HOT_ON);
        alerts.set(AlertEngine.OIL_HOT, oilHot, oilHot ? t.alertOilHot(Fmt.number(s.oilTemp, 0)) : null);

        boolean pressLow = oil.belowAbsolute(s);
        alerts.set(AlertEngine.OIL_PRESS_LOW, pressLow, pressLow ? t.alertOilPressLow(Fmt.number(s.oilPress, 1)) : null);

        boolean belowBaseline = oil.belowBaseline(s);
        alerts.set(AlertEngine.OIL_PRESS_BASELINE, belowBaseline,
                belowBaseline ? t.alertOilPressBaseline(Fmt.number(s.oilPress, 1), Fmt.number(oil.expected(s.rpm), 1)) : null);

        boolean coldOil = WarmUp.coldOilUnderLoad(s);
        alerts.set(AlertEngine.COLD_OIL_LOAD, coldOil, coldOil ? t.alertColdOil(Fmt.number(s.oilTemp, 0)) : null);

        int low = TyreMonitor.lowTyre(s.tyres);
        alerts.set(AlertEngine.TYRE_LOW, low >= 0, low >= 0 ? t.alertTyreLow(t.tyreShort[low], Fmt.number(s.tyres[low], 2)) : null);

        int asym = TyreMonitor.asymmetricTyre(s.tyres);
        alerts.set(AlertEngine.TYRE_ASYM, asym >= 0,
                asym >= 0 ? t.alertTyreAsym(t.tyreShort[asym], Fmt.number(TyreMonitor.belowOthers(s.tyres, asym), 2)) : null);

        int leak = tyres.leakTyre;
        alerts.set(AlertEngine.TYRE_LEAK, leak >= 0,
                leak >= 0 ? t.alertTyreLeak(t.tyreShort[leak], Fmt.number(tyres.leakDropBar, 2), tyres.leakDays) : null);

        boolean cooldown = s.engineRunning() && s.parked() && turbo.needsCooldown();
        alerts.set(AlertEngine.TURBO_COOLDOWN, cooldown, cooldown ? t.alertTurboZone(turbo.level(),
                (int) Math.ceil(turbo.secondsUntilOk()), (int) Math.ceil(turbo.remainingS())) : null);
    }

    public void load(Store store) {
        perf.load(store);
        oil.load(store);
        tyres.load(store);
    }

    public void save(Store store) {
        perf.save(store);
        oil.save(store);
        tyres.save(store);
    }
}
