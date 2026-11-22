package pl.q50.app.data;

/**
 * Live state of one Android sensor (= one vehicle signal on InTouch).
 * Written by the sensor thread ({@link #onValues}), read by the UI thread: {@code eventCount} is
 * volatile and written last, so a reader that sees a new count also sees the new values.
 */
public final class Channel {
    /** History length: 20 s at the 10 Hz sampling tick. */
    public static final int HISTORY = 200;

    public final int type;
    public final String name;
    public final String vendor;
    public final boolean vehicle;
    public final boolean demo;
    public final float maxRange;
    public final float resolution;
    public SignalDef def;

    /** Latest values[]; SensorEvent on Android 2.3 carries up to 3 floats. */
    public final float[] values = new float[3];
    public int valueCount;
    public volatile int eventCount;
    public long lastEventMs;
    public float rateHz;
    public float min = Float.NaN;
    public float max = Float.NaN;

    private final float[] history = new float[HISTORY];
    private int historyPos;
    private int historyCount;
    private int rateBaseCount;
    private long rateBaseMs;

    // Formatted text is cached and only rebuilt when a new value arrives (no per-frame garbage).
    private int fmtCount = -1;
    private String fmtCache;
    private int rawCount = -1;
    private final String[] rawCache = new String[3];
    private float rangeMin = Float.NaN;
    private float rangeMax = Float.NaN;
    private String rangeCache = "";

    public Channel(int type, String name, String vendor, boolean vehicle, boolean demo,
                   float maxRange, float resolution) {
        this.type = type;
        this.name = name == null ? "" : name;
        this.vendor = vendor == null ? "" : vendor;
        this.vehicle = vehicle;
        this.demo = demo;
        this.maxRange = maxRange;
        this.resolution = resolution;
    }

    public void onValues(float[] v, long nowMs) {
        int n = Math.min(v.length, values.length);
        for (int i = 0; i < n; i++) {
            values[i] = v[i];
        }
        valueCount = n;
        float v0 = n > 0 ? v[0] : Float.NaN;
        if (!Float.isNaN(v0)) {
            if (Float.isNaN(min) || v0 < min) {
                min = v0;
            }
            if (Float.isNaN(max) || v0 > max) {
                max = v0;
            }
        }
        eventCount++;
        lastEventMs = nowMs;
    }

    public boolean hasValue() {
        return eventCount > 0;
    }

    /** True once values[0] has taken more than one value – the interesting ones when mapping. */
    public boolean varies() {
        return hasValue() && max > min;
    }

    public float raw() {
        return hasValue() ? values[0] : Float.NaN;
    }

    /** Value converted with the signal definition (or raw when unmapped). */
    public float value() {
        float r = raw();
        return def == null || Float.isNaN(r) ? r : def.convert(r);
    }

    public String formatted() {
        int n = eventCount;
        if (n == 0) {
            return "—";
        }
        if (n != fmtCount) {
            fmtCache = def == null ? Fmt.raw(values[0]) : def.formatRaw(values[0]);
            fmtCount = n;
        }
        return fmtCache;
    }

    /** values[k] as raw text, cached per event. */
    public String rawText(int k) {
        int n = eventCount;
        if (n == 0 || k >= valueCount) {
            return "—";
        }
        if (n != rawCount) {
            for (int i = 0; i < rawCache.length; i++) {
                rawCache[i] = i < valueCount ? Fmt.raw(values[i]) : "—";
            }
            rawCount = n;
        }
        return rawCache[k];
    }

    /** "min … max" of raw values[0], cached until the range changes. */
    public String rangeText() {
        if (!hasValue()) {
            return "";
        }
        if (Float.compare(min, rangeMin) != 0 || Float.compare(max, rangeMax) != 0) {
            rangeMin = min;
            rangeMax = max;
            rangeCache = Fmt.raw(min) + " … " + Fmt.raw(max);
        }
        return rangeCache;
    }




    public void resetMinMax() {
        min = Float.NaN;
        max = Float.NaN;
        if (hasValue()) {
            min = values[0];
            max = values[0];
        }
    }

    /** Called by the 10 Hz tick. */
    public void sample(long nowMs) {
        history[historyPos] = raw();
        historyPos = (historyPos + 1) % HISTORY;
        if (historyCount < HISTORY) {
            historyCount++;
        }
        if (rateBaseMs == 0) {
            rateBaseMs = nowMs;
            rateBaseCount = eventCount;
        } else if (nowMs - rateBaseMs >= 1000) {
            rateHz = (eventCount - rateBaseCount) * 1000f / (nowMs - rateBaseMs);
            rateBaseMs = nowMs;
            rateBaseCount = eventCount;
        }
    }

    public int historyCount() {
        return historyCount;
    }

    /** i = 0 is the oldest sample still kept. */
    public float history(int i) {
        int start = (historyPos - historyCount + HISTORY) % HISTORY;
        return history[(start + i) % HISTORY];
    }
}
