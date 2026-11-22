package pl.q50.app.logic;

/** Engine warm-up: oil temperature state and how long it took to warm up this session. */
public final class WarmUp {
    public static final float COLD_C = 60f;
    public static final float WARM_C = 80f;
    /** A session counts as a cold start when coolant starts below this. */
    public static final float COLD_START_C = 40f;

    public static final int UNKNOWN = 0;
    public static final int COLD = 1;
    public static final int WARMING = 2;
    public static final int WARM = 3;

    private long startMs = -1;
    private float startOil = Float.NaN;
    private boolean coldStart;
    /** Time from session start until coolant / oil reached WARM_C, or -1. */
    public long coolantWarmMs = -1;
    public long oilWarmMs = -1;
    public float oil = Float.NaN;

    public void update(Snapshot s) {
        oil = s.oilTemp;
        if (startMs < 0 && Snapshot.ok(s.coolant) && Snapshot.ok(s.oilTemp)) {
            startMs = s.now;
            startOil = s.oilTemp;
            coldStart = s.coolant < COLD_START_C;
        }
        if (startMs < 0 || !coldStart) return;
        if (coolantWarmMs < 0 && Snapshot.ok(s.coolant) && s.coolant >= WARM_C) coolantWarmMs = s.now - startMs;
        if (oilWarmMs < 0 && Snapshot.ok(s.oilTemp) && s.oilTemp >= WARM_C) oilWarmMs = s.now - startMs;
    }

    public boolean coldStart() {
        return coldStart;
    }

    public long sinceStartMs(long now) {
        return startMs < 0 ? 0 : now - startMs;
    }

    public int state() {
        if (Float.isNaN(oil)) return UNKNOWN;
        if (oil < COLD_C) return COLD;
        if (oil < WARM_C) return WARMING;
        return WARM;
    }

    /** Progress of the oil towards WARM_C, 0..1. */
    public float progress() {
        if (Float.isNaN(oil)) return 0f;
        float from = Float.isNaN(startOil) ? 20f : Math.min(startOil, WARM_C - 1f);
        return Math.max(0f, Math.min(1f, (oil - from) / (WARM_C - from)));
    }

    /** Cold oil under high load: high RPM or a lot of throttle while oil is below COLD_C. */
    public static boolean coldOilUnderLoad(Snapshot s) {
        return Snapshot.ok(s.oilTemp) && s.oilTemp < COLD_C && s.engineRunning()
                && (s.rpm > 4000f || (Snapshot.ok(s.throttle) && s.throttle > 80f));
    }
}
