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
 * and each sensor is requested only as fast as it is needed – the app samples everything at
 * 10 Hz anyway. Only the sensor explorer asks for the full rate, to show real update rates.
 */
public final class VehicleSensorSource implements SensorEventListener {
    private final SensorManager sm;
    private final DataHub hub;
    /** Signals the UI animates (≈16 Hz); everything else is fine at ≈5 Hz. */
    private static final String[] FAST_KEYS = {"rpm", "speed", "throttle", "torque", "power", "g_lat", "g_long", "gear"};

    private final Map<Sensor, Channel> channels = new HashMap<Sensor, Channel>();
    private HandlerThread thread;
    private Handler handler;
    private boolean registered;
    private boolean fullRate;

    public VehicleSensorSource(SensorManager sm, DataHub hub) {
        this.sm = sm;
        this.hub = hub;
        if (sm == null) {
            return;
        }
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        for (int i = 0; i < sensors.size(); i++) {
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

    private static boolean isFast(Channel c) {
        if (c.def == null) return false;
        for (int i = 0; i < FAST_KEYS.length; i++) {
            if (FAST_KEYS[i].equals(c.def.key)) return true;
        }
        return false;
    }

    private int rateFor(Channel c) {
        if (fullRate) return SensorManager.SENSOR_DELAY_GAME;
        return isFast(c) ? SensorManager.SENSOR_DELAY_UI : SensorManager.SENSOR_DELAY_NORMAL;
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
        for (Map.Entry<Sensor, Channel> e : channels.entrySet()) {
            sm.registerListener(this, e.getKey(), rateFor(e.getValue()), handler);
        }
        registered = true;
    }

    /** Full sensor rate while the sensor explorer is on screen; reduced otherwise. */
    public void setFullRate(boolean full) {
        if (full == fullRate) {
            return;
        }
        fullRate = full;
        if (registered) {
            stop();
            start();
        }
    }

    public void stop() {
        if (sm == null || !registered) {
            return;
        }
        sm.unregisterListener(this);
        registered = false;
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
