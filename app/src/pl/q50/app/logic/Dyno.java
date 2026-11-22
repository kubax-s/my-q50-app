package pl.q50.app.logic;

/**
 * "Dyno" from the car's own torque and power signals: the highest torque and power seen in
 * each 250 rpm bin while the throttle is (nearly) wide open.
 */
public final class Dyno {
    public static final int RPM_MIN = 1000;
    public static final int RPM_MAX = 7500;
    public static final int BIN = 250;
    public static final int BINS = (RPM_MAX - RPM_MIN) / BIN;
    public static final float WOT_THROTTLE = 90f;

    public final float[] torque = new float[BINS];
    public final float[] power = new float[BINS];

    public Dyno() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < BINS; i++) {
            torque[i] = Float.NaN;
            power[i] = Float.NaN;
        }
    }

    public static int bin(float rpm) {
        if (Float.isNaN(rpm) || rpm < RPM_MIN || rpm >= RPM_MAX) return -1;
        return (int) ((rpm - RPM_MIN) / BIN);
    }

    public static int binCenterRpm(int bin) {
        return RPM_MIN + bin * BIN + BIN / 2;
    }

    public void update(Snapshot s) {
        if (!Snapshot.ok(s.throttle) || s.throttle < WOT_THROTTLE) return;
        int b = bin(s.rpm);
        if (b < 0) return;
        torque[b] = TripStats.max(torque[b], s.torque);
        power[b] = TripStats.max(power[b], s.power);
    }

    public boolean isEmpty() {
        for (int i = 0; i < BINS; i++) {
            if (!Float.isNaN(power[i]) || !Float.isNaN(torque[i])) return false;
        }
        return true;
    }

    /** Index of the bin with the highest value, or -1. */
    public static int peak(float[] values) {
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            if (!Float.isNaN(values[i]) && (best < 0 || values[i] > values[best])) best = i;
        }
        return best;
    }
}
