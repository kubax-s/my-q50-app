package pl.q50.app.logic;

/**
 * G-meter: current lateral/longitudinal G, a short trail, the furthest point reached in each
 * direction (friction-circle envelope) and the maxima. Sign convention of the head unit's
 * signals is not confirmed yet: positive longitudinal is assumed to be acceleration.
 */
public final class GMeter {
    /** Trail length in ticks (5 s at 10 Hz). */
    public static final int TRAIL = 50;
    /** Envelope resolution: 10° sectors. */
    public static final int SECTORS = 36;

    public float lat = Float.NaN;
    public float lon = Float.NaN;
    public float maxLatPlus = Float.NaN;
    public float maxLatMinus = Float.NaN;   // stored as a positive magnitude
    public float maxAccel = Float.NaN;
    public float maxBrake = Float.NaN;      // stored as a positive magnitude
    public float maxCombined = Float.NaN;
    public final float[] sectorMax = new float[SECTORS];

    private final float[] trailLat = new float[TRAIL];
    private final float[] trailLon = new float[TRAIL];
    private int trailPos;
    private int trailCount;

    public GMeter() {
        reset();
    }

    public void reset() {
        maxLatPlus = maxLatMinus = maxAccel = maxBrake = maxCombined = Float.NaN;
        for (int i = 0; i < SECTORS; i++) sectorMax[i] = 0f;
        trailPos = 0;
        trailCount = 0;
    }

    public void update(Snapshot s) {
        lat = s.gLat;
        lon = s.gLong;
        if (Float.isNaN(lat) || Float.isNaN(lon)) return;
        trailLat[trailPos] = lat;
        trailLon[trailPos] = lon;
        trailPos = (trailPos + 1) % TRAIL;
        if (trailCount < TRAIL) trailCount++;

        if (lat >= 0) maxLatPlus = TripStats.max(maxLatPlus, lat);
        else maxLatMinus = TripStats.max(maxLatMinus, -lat);
        if (lon >= 0) maxAccel = TripStats.max(maxAccel, lon);
        else maxBrake = TripStats.max(maxBrake, -lon);
        float mag = (float) Math.sqrt(lat * lat + lon * lon);
        maxCombined = TripStats.max(maxCombined, mag);
        int sec = sector(lat, lon);
        if (mag > sectorMax[sec]) sectorMax[sec] = mag;
    }

    /** Sector index for a point; 0 = straight "right" (+lat), counter-clockwise towards +lon. */
    public static int sector(float lat, float lon) {
        double deg = Math.toDegrees(Math.atan2(lon, lat));
        if (deg < 0) deg += 360;
        int s = (int) (deg / (360.0 / SECTORS));
        return s >= SECTORS ? SECTORS - 1 : s;
    }

    public int trailCount() {
        return trailCount;
    }

    /** i = 0 is the oldest point. */
    public float trailLat(int i) {
        return trailLat[(trailPos - trailCount + i + TRAIL) % TRAIL];
    }

    public float trailLon(int i) {
        return trailLon[(trailPos - trailCount + i + TRAIL) % TRAIL];
    }
}
