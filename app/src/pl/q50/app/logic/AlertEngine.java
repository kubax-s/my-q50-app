package pl.q50.app.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw conditions into alerts: a condition must hold for an on-delay before the alert shows,
 * and the alert stays until the condition has been gone for an off-delay. Alerts can be
 * acknowledged (snoozed) and disabled in the settings.
 */
public final class AlertEngine {
    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int CRITICAL = 2;

    public static final String COOLANT_HOT = "coolant_hot";
    public static final String OIL_HOT = "oil_hot";
    public static final String OIL_PRESS_LOW = "oil_press_low";
    public static final String OIL_PRESS_BASELINE = "oil_press_baseline";
    public static final String COLD_OIL_LOAD = "cold_oil_load";
    public static final String TYRE_LOW = "tyre_low";
    public static final String TYRE_ASYM = "tyre_asym";
    public static final String TYRE_LEAK = "tyre_leak";
    public static final String TURBO_COOLDOWN = "turbo_cooldown";

    /** All alerts in settings order. */
    public static final String[] IDS = {
            COOLANT_HOT, OIL_HOT, OIL_PRESS_LOW, OIL_PRESS_BASELINE, COLD_OIL_LOAD,
            TYRE_LOW, TYRE_ASYM, TYRE_LEAK, TURBO_COOLDOWN,
    };
    private static final int[] SEVERITY = {CRITICAL, CRITICAL, CRITICAL, WARNING, WARNING, WARNING, WARNING, WARNING, INFO};
    private static final long[] ON_DELAY_MS = {2000, 2000, 2000, 3000, 500, 5000, 5000, 0, 3000};
    private static final long OFF_DELAY_MS = 2000;
    public static final long SNOOZE_MS = 5 * 60 * 1000;

    public static final class Alert {
        public final String id;
        public final int severity;
        public String text = "";
        boolean condition;
        long condSince = -1;
        long clearSince = -1;
        public boolean active;
        long snoozedUntil;

        Alert(String id, int severity) {
            this.id = id;
            this.severity = severity;
        }
    }

    /** One raised alert, for the history list. */
    public static final class Event {
        public final String id;
        public final int severity;
        public final String text;
        public final long at;

        Event(String id, int severity, String text, long at) {
            this.id = id;
            this.severity = severity;
            this.text = text;
            this.at = at;
        }
    }

    public static final int HISTORY = 20;

    private final Alert[] alerts = new Alert[IDS.length];
    private final List<Event> history = new ArrayList<Event>();
    private final List<Alert> visible = new ArrayList<Alert>();
    /** Highest severity of alerts that became visible in the last update, or -1. */
    public int raisedSeverity = -1;

    public AlertEngine() {
        for (int i = 0; i < IDS.length; i++) alerts[i] = new Alert(IDS[i], SEVERITY[i]);
    }

    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return i;
        }
        return -1;
    }

    public static int severityOf(String id) {
        int i = index(id);
        return i < 0 ? INFO : SEVERITY[i];
    }

    public Alert get(String id) {
        return alerts[index(id)];
    }

    public boolean isActive(String id) {
        return get(id).active;
    }

    /** Sets the raw condition and the text to show while it is active. */
    public void set(String id, boolean condition, String text) {
        Alert a = get(id);
        a.condition = condition;
        if (condition && text != null) a.text = text;
    }

    public void update(long now, Settings settings) {
        raisedSeverity = -1;
        visible.clear();
        for (int i = 0; i < alerts.length; i++) {
            Alert a = alerts[i];
            boolean wasVisible = isVisible(a, now, settings);
            if (a.condition) {
                a.clearSince = -1;
                if (a.condSince < 0) a.condSince = now;
                if (!a.active && now - a.condSince >= ON_DELAY_MS[i]) {
                    a.active = true;
                    if (settings == null || settings.isAlertEnabled(a.id)) {
                        history.add(0, new Event(a.id, a.severity, a.text, now));
                        while (history.size() > HISTORY) history.remove(history.size() - 1);
                    }
                }
            } else {
                a.condSince = -1;
                if (a.active) {
                    if (a.clearSince < 0) a.clearSince = now;
                    if (now - a.clearSince >= OFF_DELAY_MS) {
                        a.active = false;
                        a.snoozedUntil = 0; // next occurrence alerts again
                    }
                }
            }
            if (isVisible(a, now, settings)) {
                visible.add(a);
                if (!wasVisible && a.severity > raisedSeverity) raisedSeverity = a.severity;
            }
        }
        // most severe first
        for (int i = 1; i < visible.size(); i++) {
            Alert x = visible.get(i);
            int j = i - 1;
            while (j >= 0 && visible.get(j).severity < x.severity) {
                visible.set(j + 1, visible.get(j));
                j--;
            }
            visible.set(j + 1, x);
        }
    }

    private static boolean isVisible(Alert a, long now, Settings settings) {
        return a.active && now >= a.snoozedUntil && (settings == null || settings.isAlertEnabled(a.id));
    }

    public List<Alert> visible() {
        return visible;
    }

    /** Most recent first. */
    public List<Event> history() {
        return history;
    }

    public long snoozeRemainingMs(String id, long now) {
        return Math.max(0, get(id).snoozedUntil - now);
    }

    public void unsnooze(String id) {
        get(id).snoozedUntil = 0;
    }

    public void snooze(String id, long now) {
        get(id).snoozedUntil = now + SNOOZE_MS;
        for (int i = visible.size() - 1; i >= 0; i--) {
            if (visible.get(i).id.equals(id)) visible.remove(i);
        }
    }
}
