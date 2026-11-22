package pl.q50.app.data;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads every sensor the head unit exposes. On InTouch the vehicle CAN signals appear as
 * sensors with vendor "Ygomi", names "VS_ID_*" and types 12..53. Other sensors (if any) are
 * listed too, so the explorer shows the complete picture.
 *
 * Load on the head unit: events are delivered on a background thread (never the UI thread),
 * and each sensor is requested only as fast as it is needed. The periods follow what other
 * InTouch apps run with: 100 ms for the signals the UI animates, 200 ms for the rest. In the
 * background only the signals the analytics need stay subscribed (200 ms).
 */
public final class VehicleSensorSource implements SensorEventListener {
    /** Foreground: all sensors, fast signals at 100 ms, the rest at 200 ms. */
    public static final int MODE_FOREGROUND = 0;
    /** Sensor explorer on screen: every sensor at 100 ms, to show real update rates up to 10 Hz. */
    public static final int MODE_EXPLORER = 1;
    /** App not visible and not logging: only what trip, alerts and cool-down need, at 200 ms. */
    public static final int MODE_BACKGROUND = 2;

    static final int FAST_US = 100000;
    static final int SLOW_US = 200000;

    /** Signals the UI animates and the timing uses. */
    private static final String[] FAST_KEYS = {"rpm", "speed", "throttle", "torque", "power", "g_lat", "g_long", "gear"};
    /** Signals the analytics need while the app is in the background. */
    private static final String[] BACKGROUND_KEYS = {"rpm", "speed", "throttle", "power", "torque", "gear",
            "coolant", "oil_temp", "oil_press", "tpms_fl", "tpms_fr", "tpms_rl", "tpms_rr"};

    private final SensorManager sm;
    private final DataHub hub;
    private final Map<Sensor, Channel> channels = new HashMap<Sensor, Channel>();
    private HandlerThread thread;
    private Handler handler;
    private boolean registered;
    private int mode = MODE_FOREGROUND;
    private int subscribed;
    private int failed;

    public VehicleSensorSource(SensorManager sm, DataHub hub) {
        this.sm = sm;
        this.hub = hub;
        if (sm == null) {
            return;
        }
        List<Sensor> sensors;
        try {
            sensors = sm.getSensorList(Sensor.TYPE_ALL);
        } catch (RuntimeException e) {
            return; // vendor sensor HAL failure: run without sensors (demo mode)
        }
        for (int i = 0; sensors != null && i < sensors.size(); i++) {
            Sensor s = sensors.get(i);
            Channel c = new Channel(s.getType(), s.getName(), s.getVendor(), isVehicle(s), false,
                    s.getMaximumRange(), s.getResolution());
            channels.put(s, hub.add(c));
        }
    }

    public static boolean isVehicle(Sensor s) {
        String vendor = s.getVendor() == null ? "" : s.getVendor().toLowerCase();
        String name = s.getName() == null ? "" : s.getName();
        if (vendor.indexOf("ygomi") >= 0 || name.indexOf("VS_ID") >= 0) {
            return true;
        }
        // Fallback by type range only on Android 2.3 (InTouch): newer Android versions define
        // their own standard sensors in 12..53 (e.g. 13 = ambient temperature), which on a
        // phone or emulator must not be mistaken for vehicle signals.
        int t = s.getType();
        return Build.VERSION.SDK_INT <= 10 && t >= 12 && t <= 53;
    }

    public int sensorCount() {
        return channels.size();
    }

    /** Sensors currently subscribed / registrations the system refused. */
    public int subscribedCount() {
        return subscribed;
    }

    public int failedCount() {
        return failed;
    }

    private static boolean keyIn(Channel c, String[] keys) {
        if (c.def == null) return false;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(c.def.key)) return true;
        }
        return false;
    }

    /** Requested period in microseconds, or 0 when the sensor is not needed in this mode. */
    static int periodUs(int mode, boolean fast, boolean neededInBackground) {
        switch (mode) {
            case MODE_EXPLORER:
                return FAST_US;
            case MODE_BACKGROUND:
                return neededInBackground ? SLOW_US : 0;
            default:
                return fast ? FAST_US : SLOW_US;
        }
    }

    public void start() {
        if (sm == null || registered) {
            return;
        }
        if (thread == null) {
            thread = new HandlerThread("vehicle-sensors");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        subscribed = 0;
        failed = 0;
        for (Map.Entry<Sensor, Channel> e : channels.entrySet()) {
            Channel c = e.getValue();
            int us = periodUs(mode, keyIn(c, FAST_KEYS), keyIn(c, BACKGROUND_KEYS));
            if (us == 0) continue;
            try {
                // the int is a period in µs on API 9+; events arrive on our thread
                if (sm.registerListener(this, e.getKey(), us, handler)) {
                    subscribed++;
                } else {
                    failed++;
                }
            } catch (RuntimeException ex) {
                failed++; // SecurityException / IllegalArgumentException from the vendor HAL
            }
        }
        registered = true;
    }

    /** Re-subscribes with the rates of the given mode (no-op if unchanged). */
    public void setMode(int newMode) {
        if (newMode == mode) {
            return;
        }
        mode = newMode;
        if (registered) {
            stop();
            start();
        }
    }

    public void stop() {
        if (sm == null || !registered) {
            return;
        }
        try {
            sm.unregisterListener(this);
        } catch (RuntimeException ignored) {
            // nothing else to do
        }
        registered = false;
        subscribed = 0;
    }

    /** Stops listening and ends the background thread. */
    public void release() {
        stop();
        if (thread != null) {
            thread.quit();
            thread = null;
            handler = null;
        }
    }

    public void onSensorChanged(SensorEvent event) {
        Channel c = channels.get(event.sensor);
        if (c != null && event.values != null) {
            c.onValues(event.values, SystemClock.uptimeMillis());
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
