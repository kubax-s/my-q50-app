package pl.q50.app.data;

/** Small, allocation-light number formatting that does not depend on the locale. */
public final class Fmt {
    private Fmt() {
    }

    public static String number(float v, int decimals) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return "—";
        }
        if (decimals <= 0) {
            return Integer.toString(Math.round(v));
        }
        int scale = 1;
        for (int i = 0; i < decimals; i++) {
            scale *= 10;
        }
        long scaled = Math.round((double) v * scale);
        boolean negative = scaled < 0;
        if (negative) {
            scaled = -scaled;
        }
        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        sb.append(scaled / scale).append('.');
        String frac = Long.toString(scaled % scale);
        for (int i = frac.length(); i < decimals; i++) {
            sb.append('0');
        }
        return sb.append(frac).toString();
    }

    /** Raw sensor value: up to 3 decimals, trailing zeros trimmed. */
    public static String raw(float v) {
        if (Float.isNaN(v)) {
            return "—";
        }
        String s = number(v, 3);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && s.charAt(end - 1) == '.') {
                end--;
            }
            s = s.substring(0, end);
        }
        return s;
    }

    public static String duration(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec / 60) % 60;
        long s = sec % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append(':');
            two(sb, m);
        } else {
            sb.append(m);
        }
        sb.append(':');
        two(sb, s);
        return sb.toString();
    }

    public static String bytes(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024 * 1024) {
            return number(b / 1024f, 1) + " kB";
        }
        return number(b / (1024f * 1024f), 1) + " MB";
    }

    private static void two(StringBuilder sb, long v) {
        if (v < 10) {
            sb.append('0');
        }
        sb.append(v);
    }
}
