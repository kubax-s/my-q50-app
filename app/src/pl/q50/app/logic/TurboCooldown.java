package pl.q50.app.logic;

/**
 * Estimates how long the engine should idle before switching off after hard driving, so the
 * turbochargers can cool down. There is no turbo or exhaust temperature on the head unit, so
 * this is a model: hard driving builds up "seconds needed", idling and light driving work them
 * off. Oil temperature – the one real temperature the turbos share (they are oil cooled) –
 * scales both: hot oil adds heat faster and takes longer to cool down.
 */
public final class TurboCooldown {
    public static final float MAX_S = 150f;
    public static final float HARD_POWER = 150f;     // PS
    public static final float HARD_RPM = 4000f;
    public static final float HARD_THROTTLE = 60f;
    public static final float LIGHT_POWER = 60f;     // PS
    /** Oil temperature above which heat builds up faster and cooling slows down. */
    public static final float OIL_HOT_C = 100f;
    /** Oil temperature at which the effect is at its maximum. */
    public static final float OIL_VERY_HOT_C = 120f;
    /** Remaining seconds above which the reminder is shown. */
    public static final float REMIND_S = 5f;
    /** Levels of the remaining wait: at or above RED_S it really matters, below ORANGE_S almost done. */
    public static final float RED_S = 45f;
    public static final float ORANGE_S = 15f;
    public static final int LEVEL_GREEN = 0;
    public static final int LEVEL_ORANGE = 1;
    public static final int LEVEL_RED = 2;
    private static final long ENGINE_OFF_RESET_MS = 60000;
    private static final long MAX_STEP_MS = 1000;

    private float neededS;
    private long lastMs = -1;
    private long offMs;
    private float oil = Float.NaN;

    public float remainingS() {
        return neededS;
    }

    public float fraction() {
        return neededS / MAX_S;
    }

    public boolean needsCooldown() {
        return neededS > REMIND_S;
    }

    public int level() {
        if (neededS >= RED_S) return LEVEL_RED;
        if (neededS >= ORANGE_S) return LEVEL_ORANGE;
        return LEVEL_GREEN;
    }

    /** True when hot oil currently slows the cool-down (for an explanatory hint). */
    public boolean oilSlowsCooling() {
        return coolFactor(oil) < 1f;
    }

    public float oilTemp() {
        return oil;
    }

    /** Hard driving with hot oil heats up faster: ×1 up to 100 °C, ×1.5 from 120 °C. */
    static float heatFactor(float oilC) {
        if (Float.isNaN(oilC)) return 1f;
        return 1f + 0.5f * hotness(oilC);
    }

    /** Idling with hot oil cools down slower: ×1 up to 100 °C, ×0.5 from 120 °C. */
    static float coolFactor(float oilC) {
        if (Float.isNaN(oilC)) return 1f;
        return 1f - 0.5f * hotness(oilC);
    }

    private static float hotness(float oilC) {
        return Math.max(0f, Math.min(1f, (oilC - OIL_HOT_C) / (OIL_VERY_HOT_C - OIL_HOT_C)));
    }

    public void update(Snapshot s) {
        oil = s.oilTemp;
        if (lastMs < 0) {
            lastMs = s.now;
            return;
        }
        float dt = Math.max(0, Math.min(MAX_STEP_MS, s.now - lastMs)) / 1000f;
        lastMs = s.now;
        if (!s.engineRunning()) {
            offMs += (long) (dt * 1000);
            if (offMs >= ENGINE_OFF_RESET_MS) neededS = 0;
            return;
        }
        offMs = 0;
        boolean hard = (Snapshot.ok(s.power) && s.power >= HARD_POWER)
                || (s.rpm >= HARD_RPM && Snapshot.ok(s.throttle) && s.throttle >= HARD_THROTTLE);
        if (hard) {
            neededS += dt * 1.5f * heatFactor(oil);
        } else if (!Snapshot.ok(s.speed) || s.speed < 5f) {
            neededS -= dt * coolFactor(oil);
        } else if (!Snapshot.ok(s.power) || s.power < LIGHT_POWER) {
            neededS -= dt * 0.5f * coolFactor(oil);
        }
        neededS = Math.max(0f, Math.min(MAX_S, neededS));
    }
}
