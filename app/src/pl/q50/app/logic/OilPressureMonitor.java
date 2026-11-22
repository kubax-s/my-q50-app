package pl.q50.app.logic;

/**
 * Learns the normal warm-engine oil pressure per 500 rpm bin and flags readings well below it.
 * Also checks an absolute minimum, which works before anything has been learned.
 */
public final class OilPressureMonitor {
    public static final int BIN = 500;
    public static final int BINS = 15; // 0..7500 rpm
    public static final float LEARN_MIN_OIL_C = 80f;
    public static final float LEARN_MAX_OIL_C = 125f;
    /** Samples (ticks) a bin needs before it is trusted. 100 ticks = 10 s at 10 Hz. */
    public static final int MIN_SAMPLES = 100;
    public static final float LOW_RATIO = 0.7f;
    public static final float ABSOLUTE_MIN_BAR = 0.5f;
    private static final float ALPHA = 0.02f;
    private static final String KEY = "oil_baseline";

    public final float[] mean = new float[BINS];
    public final int[] count = new int[BINS];

    public static int bin(float rpm) {
        if (Float.isNaN(rpm) || rpm < 0) return -1;
        int b = (int) (rpm / BIN);
        return b < BINS ? b : -1;
    }

    /** Learned normal pressure at this RPM (bar), NaN if not learned yet. */
    public float expected(float rpm) {
        int b = bin(rpm);
        return b >= 0 && count[b] >= MIN_SAMPLES ? mean[b] : Float.NaN;
    }

    public boolean belowAbsolute(Snapshot s) {
        return s.engineRunning() && Snapshot.ok(s.oilPress) && s.oilPress < ABSOLUTE_MIN_BAR;
    }

    public boolean belowBaseline(Snapshot s) {
        if (!s.engineRunning() || !Snapshot.ok(s.oilPress) || !warm(s)) return false;
        float e = expected(s.rpm);
        return !Float.isNaN(e) && s.oilPress < LOW_RATIO * e;
    }

    public void update(Snapshot s) {
        if (!s.engineRunning() || !Snapshot.ok(s.oilPress) || !warm(s)) return;
        if (belowBaseline(s) || belowAbsolute(s)) return; // never learn from a suspicious reading
        int b = bin(s.rpm);
        if (b < 0) return;
        mean[b] = count[b] == 0 ? s.oilPress : mean[b] + ALPHA * (s.oilPress - mean[b]);
        if (count[b] < Integer.MAX_VALUE) count[b]++;
    }

    private static boolean warm(Snapshot s) {
        return Snapshot.ok(s.oilTemp) && s.oilTemp >= LEARN_MIN_OIL_C && s.oilTemp <= LEARN_MAX_OIL_C;
    }

    public void reset() {
        for (int i = 0; i < BINS; i++) {
            mean[i] = 0;
            count[i] = 0;
        }
    }

    public void load(Store store) {
        String s = store.get(KEY);
        if (s == null) return;
        String[] p = s.split(";");
        for (int i = 0; i < BINS && i < p.length; i++) {
            String[] f = p[i].split(",");
            if (f.length != 2) continue;
            try {
                mean[i] = Float.parseFloat(f[0]);
                count[i] = Integer.parseInt(f[1]);
            } catch (NumberFormatException e) {
                mean[i] = 0;
                count[i] = 0;
            }
        }
    }

    public void save(Store store) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BINS; i++) {
            if (i > 0) sb.append(';');
            sb.append(mean[i]).append(',').append(count[i]);
        }
        store.put(KEY, sb.toString());
    }
}
