package pl.q50.app.logic;

/** Trip computer since app start or the last reset. */
public final class TripStats {
    /** Upper bounds of the RPM bands: <2000, 2000–3000, 3000–4000, 4000–5000, 5000+. */
    public static final int[] RPM_BAND_LIMITS = {2000, 3000, 4000, 5000};
    /** Gear slots: P, R, N, D, M1..M7. */
    public static final int GEAR_SLOTS = 11;
    /** Throttle or RPM above these counts as spirited driving. */
    public static final float SPIRITED_THROTTLE = 70f;
    public static final float SPIRITED_RPM = 4500f;
    private static final long MAX_STEP_MS = 1000;

    public long elapsedMs;
    public long drivingMs;
    public long idleMs;
    public long spiritedMs;
    public double distanceM;
    public float maxSpeed = Float.NaN;
    public float maxRpm = Float.NaN;
    public float maxPower = Float.NaN;
    public float maxTorque = Float.NaN;
    public float maxGLat = Float.NaN;
    public float maxGPlus = Float.NaN;
    public float maxGMinus = Float.NaN;
    public float maxCoolant = Float.NaN;
    public float maxOilTemp = Float.NaN;
    public final long[] rpmBandMs = new long[RPM_BAND_LIMITS.length + 1];
    public final long[] gearMs = new long[GEAR_SLOTS];

    private long lastMs = -1;
    private float lastSpeed = Float.NaN;

    public void reset() {
        elapsedMs = drivingMs = idleMs = spiritedMs = 0;
        distanceM = 0;
        maxSpeed = maxRpm = maxPower = maxTorque = maxGLat = maxGPlus = maxGMinus = maxCoolant = maxOilTemp = Float.NaN;
        for (int i = 0; i < rpmBandMs.length; i++) rpmBandMs[i] = 0;
        for (int i = 0; i < gearMs.length; i++) gearMs[i] = 0;
        lastMs = -1;
        lastSpeed = Float.NaN;
    }

    public void update(Snapshot s) {
        if (lastMs < 0) {
            lastMs = s.now;
            lastSpeed = s.speed;
            return;
        }
        long dt = Math.max(0, Math.min(MAX_STEP_MS, s.now - lastMs));
        lastMs = s.now;
        elapsedMs += dt;

        if (Snapshot.ok(s.speed)) {
            float prev = Snapshot.ok(lastSpeed) ? lastSpeed : s.speed;
            distanceM += (prev + s.speed) / 2.0 / 3.6 * dt / 1000.0;
            if (s.speed >= 1f) {
                drivingMs += dt;
            } else if (s.engineRunning()) {
                idleMs += dt;
            }
        }
        lastSpeed = s.speed;

        if (s.engineRunning()) {
            rpmBandMs[band(s.rpm)] += dt;
            boolean spirited = s.rpm >= SPIRITED_RPM || (Snapshot.ok(s.throttle) && s.throttle >= SPIRITED_THROTTLE);
            if (spirited && Snapshot.ok(s.speed) && s.speed >= 1f) {
                spiritedMs += dt;
            }
        }
        int g = gearSlot(s.gear);
        if (g >= 0) gearMs[g] += dt;

        maxSpeed = max(maxSpeed, s.speed);
        maxRpm = max(maxRpm, s.rpm);
        maxPower = max(maxPower, s.power);
        maxTorque = max(maxTorque, s.torque);
        maxGLat = max(maxGLat, Snapshot.ok(s.gLat) ? Math.abs(s.gLat) : Float.NaN);
        maxGPlus = max(maxGPlus, s.gLong);
        maxGMinus = min(maxGMinus, s.gLong);
        maxCoolant = max(maxCoolant, s.coolant);
        maxOilTemp = max(maxOilTemp, s.oilTemp);
    }

    public float distanceKm() {
        return (float) (distanceM / 1000.0);
    }

    /** Average speed while moving, km/h (NaN before any driving). */
    public float avgSpeed() {
        return drivingMs < 1000 ? Float.NaN : (float) (distanceM / 1000.0 / (drivingMs / 3600000.0));
    }

    /** Share of driving time spent driving hard, 0..1. */
    public float spiritedShare() {
        return drivingMs < 1000 ? Float.NaN : spiritedMs / (float) drivingMs;
    }

    public static int band(float rpm) {
        for (int i = 0; i < RPM_BAND_LIMITS.length; i++) {
            if (rpm < RPM_BAND_LIMITS[i]) return i;
        }
        return RPM_BAND_LIMITS.length;
    }

    /** P=0, R=1, N=2, D=3, M1..M7 = 4..10; -1 when unknown. */
    public static int gearSlot(int code) {
        if (code >= 1 && code <= 4) return code - 1;
        if (code >= 16 && code <= 22) return code - 12;
        return -1;
    }

    static float max(float cur, float v) {
        if (Float.isNaN(v)) return cur;
        return Float.isNaN(cur) || v > cur ? v : cur;
    }

    static float min(float cur, float v) {
        if (Float.isNaN(v)) return cur;
        return Float.isNaN(cur) || v < cur ? v : cur;
    }
}
