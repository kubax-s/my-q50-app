package pl.q50.app.data;

/** Plain-JVM checks for the Android-independent data classes. Run: bash scripts/test-java.sh */
public final class DataTest {
    private static int failures;

    public static void main(String[] args) {
        // Fmt
        eq("1.50", Fmt.number(1.5f, 2));
        eq("-0.25", Fmt.number(-0.25f, 2));
        eq("3", Fmt.number(2.6f, 0));
        eq("0.05", Fmt.number(0.05f, 2));
        eq("—", Fmt.number(Float.NaN, 1));
        eq("12.5", Fmt.raw(12.5f));
        eq("7", Fmt.raw(7.0f));
        eq("0.123", Fmt.raw(0.1234f));
        eq("1:05", Fmt.duration(65_000));
        eq("1:01:01", Fmt.duration(3_661_000));
        eq("1.5 kB", Fmt.bytes(1536));

        // gear enum observed on a VR30
        eq("P", SignalDef.gear(1));
        eq("D", SignalDef.gear(4));
        eq("M1", SignalDef.gear(16));
        eq("M7", SignalDef.gear(22));
        eq("?9", SignalDef.gear(9));

        // scaling: oil pressure MPa -> bar, power rpm*Nm -> KM
        SignalDef oil = new SignalDef(16, "oil_press", "CIŚN. OLEJU", "bar", 10f, 0f, 1, false, null, Float.NaN, Float.NaN);
        eq("3.5", oil.formatRaw(0.35f));
        SignalDef power = new SignalDef(32, "power", "MOC", "KM", 1.42377e-4f, 0f, 0, false, null, Float.NaN, Float.NaN);
        eq("305", power.formatRaw(6400f * 335f)); // ~ rated output at 6400 rpm

        // Channel: min/max, varies, history order, rate
        Channel c = new Channel(13, "VS_ID_TEST", "Ygomi", true, false, 0, 0);
        check(!c.hasValue() && !c.varies(), "empty channel");
        c.onValues(new float[]{800, 0, 0}, 1000);
        check(!c.varies(), "single value does not vary");
        c.onValues(new float[]{3000, 0, 0}, 1050);
        check(c.varies() && c.min == 800 && c.max == 3000, "min/max");
        for (int i = 0; i < Channel.HISTORY + 5; i++) {
            c.onValues(new float[]{i, 0, 0}, 2000 + i * 100);
            c.sample(2000 + i * 100);
        }
        check(c.historyCount() == Channel.HISTORY, "history capped");
        check(c.history(0) == 5 && c.history(Channel.HISTORY - 1) == Channel.HISTORY + 4, "history order");
        check(Math.abs(c.rateHz - 10f) < 0.01f, "rate ~10 Hz, got " + c.rateHz);

        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("DataTest OK");
    }

    private static void eq(String expected, String actual) {
        check(expected.equals(actual), "expected '" + expected + "' got '" + actual + "'");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
