package pl.q50.app.logic;

/**
 * Estimates how long the engine should idle before switching off after hard driving, so the
 * turbochargers can cool down. A simple heuristic: hard driving builds up "seconds needed",
 * idling and light driving work them off.
 */
public final class TurboCooldown {
    public static final float MAX_S = 120f;
    public static final float HARD_POWER = 150f;     // PS
    public static final float HARD_RPM = 4000f;
    public static final float HARD_THROTTLE = 60f;
    public static final float LIGHT_POWER = 60f;     // PS
    /** Remaining seconds above which the reminder is shown. */
    public static final float REMIND_S = 5f;
    private static final long ENGINE_OFF_RESET_MS = 60000;
    private static final long MAX_STEP_MS = 1000;

    private float neededS;
    private long lastMs = -1;
    private long offMs;

    public float remainingS() {
        return neededS;
    }

    public float fraction() {
        return neededS / MAX_S;
    }

    public boolean needsCooldown() {
        return neededS > REMIND_S;
    }

    public void update(Snapshot s) {
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
            neededS += dt * 1.5f;
        } else if (!Snapshot.ok(s.speed) || s.speed < 5f) {
            neededS -= dt;
        } else if (!Snapshot.ok(s.power) || s.power < LIGHT_POWER) {
            neededS -= dt * 0.5f;
        }
        neededS = Math.max(0f, Math.min(MAX_S, neededS));
    }
}
