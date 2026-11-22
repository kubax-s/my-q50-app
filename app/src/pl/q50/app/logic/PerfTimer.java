package pl.q50.app.logic;

/**
 * Automatic acceleration / braking timing from the speed signal. Crossing times are
 * interpolated between ticks, so a 10 Hz tick still gives ~10 ms resolution as long as the
 * speed signal itself updates often enough.
 */
public final class PerfTimer {
    public static final int ZERO_100 = 0;
    public static final int QUARTER = 1;
    public static final int R60_100 = 2;
    public static final int R80_120 = 3;
    public static final int R100_200 = 4;
    public static final int BRAKE_100_0 = 5;
    public static final int COUNT = 6;

    public static final double QUARTER_M = 402.336;
    private static final float STOP_KMH = 0.5f;
    private static final float DROP_ABORT_KMH = 4f;
    /**
     * Fastest believable change of the speed signal (km/h per second, ≈ 4 g). Anything faster is
     * a data gap or glitch: running runs are dropped and nothing starts on that tick.
     */
    public static final float MAX_RATE_KMH_S = 150f;

    /** Run definitions: kind, from, to, timeout. */
    private static final int STANDING = 0;
    private static final int ROLLING = 1;
    private static final int BRAKING = 2;
    private static final int[] KIND = {STANDING, STANDING, ROLLING, ROLLING, ROLLING, BRAKING};
    private static final float[] FROM = {0, 0, 60, 80, 100, 100};
    private static final float[] TO = {100, 0, 100, 120, 200, 0};
    private static final long[] TIMEOUT_MS = {15000, 25000, 15000, 15000, 30000, 15000};

    public static final class Result {
        /** Time in ms, or -1 when there is none yet. */
        public long timeMs = -1;
        /** Quarter mile: trap speed (km/h). Braking: distance (m). NaN otherwise. */
        public float extra = Float.NaN;

        void set(long t, float x) {
            timeMs = t;
            extra = x;
        }

        boolean better(long t, float x, int run) {
            if (timeMs < 0) return true;
            // braking is judged by distance, everything else by time
            return run == BRAKE_100_0 ? x < extra : t < timeMs;
        }
    }

    public final Result[] last = new Result[COUNT];
    public final Result[] best = new Result[COUNT];
    private final boolean[] running = new boolean[COUNT];
    private final double[] startT = new double[COUNT];
    private final double[] dist = new double[COUNT];
    private final float[] extreme = new float[COUNT];
    /** A run that started inside this tick already has its partial distance. */
    private final boolean[] fresh = new boolean[COUNT];
    private boolean stopped;
    private double lastStopT;
    private long prevT = -1;
    private float prevV = Float.NaN;
    /** Set when a run completes, for the UI to flash the result. */
    public int lastCompleted = -1;
    public long lastCompletedAt;

    public PerfTimer() {
        for (int i = 0; i < COUNT; i++) {
            last[i] = new Result();
            best[i] = new Result();
        }
    }

    public boolean isRunning(int run) {
        return running[run];
    }

    /** True while the car is stopped and standing runs will start on launch. */
    public boolean armed() {
        return stopped;
    }

    public long elapsedMs(int run, long now) {
        return running[run] ? (long) (now - startT[run]) : 0;
    }

    public void update(Snapshot s) {
        float v = s.speed;
        long t = s.now;
        if (Float.isNaN(v)) {
            abortAll();
            prevT = -1;
            prevV = Float.NaN;
            return;
        }
        if (prevT < 0 || Float.isNaN(prevV) || t - prevT > 1000) {
            abortAll();
            prevT = t;
            prevV = v;
            stopped = v < STOP_KMH;
            lastStopT = t;
            return;
        }
        double dt = (t - prevT) / 1000.0;
        if (dt > 0 && Math.abs(v - prevV) / dt > MAX_RATE_KMH_S) {
            abortAll();
            prevT = t;
            prevV = v;
            stopped = v < STOP_KMH;
            lastStopT = t;
            return;
        }
        double segM = (prevV + v) / 2.0 / 3.6 * dt;

        // standing starts
        if (v < STOP_KMH) {
            stopped = true;
            lastStopT = t;
            abortKind(STANDING);
        } else if (stopped) {
            stopped = false;
            double t0 = cross(prevT, prevV, t, v, STOP_KMH);
            for (int r = 0; r < COUNT; r++) {
                if (KIND[r] == STANDING) start(r, t0, v);
            }
            // distance covered since launch within this tick
            double partial = (v + STOP_KMH) / 2.0 / 3.6 * ((t - t0) / 1000.0);
            for (int r = 0; r < COUNT; r++) {
                if (KIND[r] == STANDING) dist[r] = partial;
            }
        }

        for (int r = 0; r < COUNT; r++) {
            if (KIND[r] == ROLLING && !running[r] && prevV < FROM[r] && v >= FROM[r]) {
                start(r, cross(prevT, prevV, t, v, FROM[r]), v);
                dist[r] = 0;
            }
            if (KIND[r] == BRAKING && !running[r] && prevV > FROM[r] && v <= FROM[r]) {
                double t0 = cross(prevT, prevV, t, v, FROM[r]);
                start(r, t0, v);
                dist[r] = (FROM[r] + v) / 2.0 / 3.6 * ((t - t0) / 1000.0);
            }
        }

        for (int r = 0; r < COUNT; r++) {
            if (!running[r]) continue;
            if (t - startT[r] > TIMEOUT_MS[r]) {
                running[r] = false;
                continue;
            }
            double before = dist[r];
            if (fresh[r]) {
                fresh[r] = false;
            } else {
                dist[r] += segM;
            }
            if (KIND[r] == BRAKING) {
                if (v > extreme[r] + DROP_ABORT_KMH) {
                    running[r] = false;
                    continue;
                }
                extreme[r] = Math.min(extreme[r], v);
                if (v < STOP_KMH) {
                    double tEnd = cross(prevT, prevV, t, v, STOP_KMH);
                    finish(r, tEnd, (float) dist[r], t);
                }
                continue;
            }
            if (v < extreme[r] - DROP_ABORT_KMH) {
                running[r] = false;
                continue;
            }
            extreme[r] = Math.max(extreme[r], v);
            if (r == QUARTER) {
                if (dist[r] >= QUARTER_M) {
                    double added = dist[r] - before;
                    double f = added > 0 ? (QUARTER_M - before) / added : 1.0;
                    double tEnd = prevT + f * (t - prevT);
                    float trap = (float) (prevV + f * (v - prevV));
                    finish(r, tEnd, trap, t);
                }
            } else if (v >= TO[r]) {
                finish(r, cross(prevT, prevV, t, v, TO[r]), Float.NaN, t);
            }
        }
        prevT = t;
        prevV = v;
    }

    private void start(int r, double t0, float v) {
        running[r] = true;
        fresh[r] = true;
        startT[r] = t0;
        extreme[r] = v;
    }

    private void finish(int r, double tEnd, float extra, long now) {
        running[r] = false;
        long ms = Math.round(tEnd - startT[r]);
        last[r].set(ms, extra);
        if (best[r].better(ms, extra, r)) {
            best[r].set(ms, extra);
        }
        lastCompleted = r;
        lastCompletedAt = now;
    }

    private void abortKind(int kind) {
        for (int r = 0; r < COUNT; r++) {
            if (KIND[r] == kind) running[r] = false;
        }
    }

    private void abortAll() {
        for (int r = 0; r < COUNT; r++) running[r] = false;
    }

    /** Interpolated time at which speed crossed {@code target} between two samples. */
    static double cross(long t1, float v1, long t2, float v2, float target) {
        if (v2 == v1) return t2;
        double f = (target - v1) / (double) (v2 - v1);
        f = Math.max(0, Math.min(1, f));
        return t1 + f * (t2 - t1);
    }

    public void resetBest() {
        for (int i = 0; i < COUNT; i++) {
            best[i].set(-1, Float.NaN);
            last[i].set(-1, Float.NaN);
        }
    }

    private static final String KEY = "perf_best";

    public void load(Store store) {
        String s = store.get(KEY);
        if (s == null) return;
        String[] p = s.split(";");
        for (int i = 0; i < COUNT && i < p.length; i++) {
            String[] f = p[i].split(",");
            if (f.length != 2) continue;
            try {
                best[i].set(Long.parseLong(f[0]), Float.parseFloat(f[1]));
            } catch (NumberFormatException e) {
                best[i].set(-1, Float.NaN);
            }
        }
    }

    public void save(Store store) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) sb.append(';');
            sb.append(best[i].timeMs).append(',').append(best[i].extra);
        }
        store.put(KEY, sb.toString());
    }
}
