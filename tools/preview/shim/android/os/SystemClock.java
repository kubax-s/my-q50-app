package android.os;

/** Preview clock: set by the scenario so every page sees the same "now". */
public final class SystemClock {
    public static long now;

    private SystemClock() {
    }

    public static long uptimeMillis() {
        return now;
    }
}
