package pl.q50.app.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Tyre pressure checks: absolute low pressure, one tyre lower than the others, and a slow leak
 * detected by comparing session-start (cold) readings across days. Comparing each tyre to the
 * average of all four cancels out temperature and season effects.
 */
public final class TyreMonitor {
    public static final float LOW_BAR = 2.0f;
    public static final float ASYM_BAR = 0.25f;
    public static final float LEAK_BAR = 0.15f;
    public static final long DAY_MS = 24L * 3600 * 1000;
    public static final long LEAK_MIN_AGE_MS = 2 * DAY_MS;
    public static final long LEAK_MAX_AGE_MS = 14 * DAY_MS;
    private static final int MAX_HISTORY = 30;
    private static final String KEY = "tyre_history";

    /** One session-start reading. */
    public static final class Reading {
        public final long wallMs;
        public final float[] bar;

        Reading(long wallMs, float[] bar) {
            this.wallMs = wallMs;
            this.bar = bar;
        }
    }

    private final List<Reading> history = new ArrayList<Reading>();
    private boolean recordedThisSession;
    /** Slow-leak result of this session's check: tyre index, drop (bar), age (days); -1 = none. */
    public int leakTyre = -1;
    public float leakDropBar;
    public int leakDays;

    public static boolean allValid(float[] t) {
        for (int i = 0; i < 4; i++) {
            if (Float.isNaN(t[i])) return false;
        }
        return true;
    }

    /** Index of the lowest tyre below LOW_BAR, or -1. */
    public static int lowTyre(float[] t) {
        int idx = -1;
        for (int i = 0; i < 4; i++) {
            if (!Float.isNaN(t[i]) && t[i] < LOW_BAR && (idx < 0 || t[i] < t[idx])) idx = i;
        }
        return idx;
    }

    /** Index of a tyre more than ASYM_BAR below the mean of the other three, or -1. */
    public static int asymmetricTyre(float[] t) {
        if (!allValid(t)) return -1;
        int idx = -1;
        float worst = 0;
        for (int i = 0; i < 4; i++) {
            float others = (t[0] + t[1] + t[2] + t[3] - t[i]) / 3f;
            float diff = others - t[i];
            if (diff > ASYM_BAR && diff > worst) {
                worst = diff;
                idx = i;
            }
        }
        return idx;
    }

    /** How much tyre i is below the mean of the other three (bar, positive = lower). */
    public static float belowOthers(float[] t, int i) {
        if (!allValid(t)) return Float.NaN;
        return (t[0] + t[1] + t[2] + t[3] - t[i]) / 3f - t[i];
    }

    /** Pressure of tyre i relative to the mean of all four (bar). */
    public static float deviation(float[] t, int i) {
        if (!allValid(t)) return Float.NaN;
        return t[i] - (t[0] + t[1] + t[2] + t[3]) / 4f;
    }

    /** Records the first complete reading of the session and runs the slow-leak check. */
    public void update(Snapshot s, long wallMs) {
        if (recordedThisSession || !allValid(s.tyres)) return;
        recordedThisSession = true;
        float[] now = s.tyres.clone();
        checkLeak(now, wallMs);
        history.add(new Reading(wallMs, now));
        while (history.size() > MAX_HISTORY) history.remove(0);
    }

    void checkLeak(float[] now, long wallMs) {
        leakTyre = -1;
        Reading ref = null;
        for (int i = 0; i < history.size(); i++) {
            Reading r = history.get(i);
            long age = wallMs - r.wallMs;
            if (age >= LEAK_MIN_AGE_MS && age <= LEAK_MAX_AGE_MS) {
                ref = r; // oldest reading inside the window
                break;
            }
        }
        if (ref == null) return;
        float meanDelta = 0;
        for (int i = 0; i < 4; i++) meanDelta += (now[i] - ref.bar[i]) / 4f;
        float worst = 0;
        for (int i = 0; i < 4; i++) {
            float rel = (now[i] - ref.bar[i]) - meanDelta;
            if (rel < -LEAK_BAR && rel < worst) {
                worst = rel;
                leakTyre = i;
            }
        }
        if (leakTyre >= 0) {
            leakDropBar = -worst;
            leakDays = (int) ((wallMs - ref.wallMs) / DAY_MS);
        }
    }

    public List<Reading> history() {
        return history;
    }

    public void load(Store store) {
        history.clear();
        String s = store.get(KEY);
        if (s == null || s.length() == 0) return;
        String[] rows = s.split(";");
        for (int i = 0; i < rows.length; i++) {
            String[] f = rows[i].split(",");
            if (f.length != 5) continue;
            try {
                float[] bar = new float[4];
                for (int k = 0; k < 4; k++) bar[k] = Float.parseFloat(f[k + 1]);
                history.add(new Reading(Long.parseLong(f[0]), bar));
            } catch (NumberFormatException e) {
                // skip a damaged row
            }
        }
    }

    public void save(Store store) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            Reading r = history.get(i);
            if (i > 0) sb.append(';');
            sb.append(r.wallMs);
            for (int k = 0; k < 4; k++) sb.append(',').append(r.bar[k]);
        }
        store.put(KEY, sb.toString());
    }
}
