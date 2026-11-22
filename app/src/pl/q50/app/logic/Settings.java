package pl.q50.app.logic;

import java.util.HashSet;
import java.util.Set;

/** User choices: which overview tiles are shown, which alerts are enabled, alert sound. */
public final class Settings {
    /** Overview tiles in display order; "tyres" is the combined 4-tyre tile. */
    public static final String[] TILES = {
            "rpm", "speed", "gear", "throttle",
            "coolant", "oil_temp", "oil_press", "torque",
            "power", "g_lat", "g_long", "tyres",
    };

    private static final String KEY_HIDDEN = "settings_hidden_tiles";
    private static final String KEY_ALERTS_OFF = "settings_alerts_off";
    private static final String KEY_SOUND = "settings_alert_sound";

    private final Set<String> hiddenTiles = new HashSet<String>();
    private final Set<String> disabledAlerts = new HashSet<String>();
    private boolean sound;

    public boolean isTileVisible(String key) {
        return !hiddenTiles.contains(key);
    }

    public void toggleTile(String key) {
        if (!hiddenTiles.remove(key)) {
            hiddenTiles.add(key);
        }
    }

    public boolean isAlertEnabled(String id) {
        return !disabledAlerts.contains(id);
    }

    public void toggleAlert(String id) {
        if (!disabledAlerts.remove(id)) {
            disabledAlerts.add(id);
        }
    }

    public boolean sound() {
        return sound;
    }

    public void toggleSound() {
        sound = !sound;
    }

    public int visibleTileCount() {
        int n = 0;
        for (int i = 0; i < TILES.length; i++) {
            if (isTileVisible(TILES[i])) n++;
        }
        return n;
    }

    public void load(Store store) {
        hiddenTiles.clear();
        disabledAlerts.clear();
        split(store.get(KEY_HIDDEN), hiddenTiles);
        split(store.get(KEY_ALERTS_OFF), disabledAlerts);
        sound = "1".equals(store.get(KEY_SOUND));
    }

    public void save(Store store) {
        store.put(KEY_HIDDEN, join(hiddenTiles));
        store.put(KEY_ALERTS_OFF, join(disabledAlerts));
        store.put(KEY_SOUND, sound ? "1" : "0");
    }

    private static void split(String csv, Set<String> out) {
        if (csv == null || csv.length() == 0) return;
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() > 0) out.add(parts[i]);
        }
    }

    private static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }
}
