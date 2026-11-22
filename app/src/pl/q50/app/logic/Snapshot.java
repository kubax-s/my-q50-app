package pl.q50.app.logic;

/**
 * Converted vehicle values at one tick (NaN = no data). Filled from the sensors by the app,
 * or by tests directly – the logic classes never touch Android APIs.
 */
public final class Snapshot {
    public static final int FL = 0;
    public static final int FR = 1;
    public static final int RL = 2;
    public static final int RR = 3;

    public long now;
    public float rpm = Float.NaN;
    public float speed = Float.NaN;      // km/h
    public float throttle = Float.NaN;   // %
    public float coolant = Float.NaN;    // °C
    public float oilTemp = Float.NaN;    // °C
    public float oilPress = Float.NaN;   // bar
    public float torque = Float.NaN;     // Nm
    public float power = Float.NaN;      // PS / KM
    public float gLat = Float.NaN;       // g
    public float gLong = Float.NaN;      // g
    /** Raw gear code: P=1, R=2, N=3, D=4, M1..M7 = 16..22, -1 = unknown. */
    public int gear = -1;
    /** Tyre pressures in bar: FL, FR, RL, RR. */
    public final float[] tyres = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};

    public boolean engineRunning() {
        return rpm >= 300;
    }

    public boolean parked() {
        return gear == 1 || (!Float.isNaN(speed) && speed < 1f);
    }

    static boolean ok(float v) {
        return !Float.isNaN(v);
    }
}
