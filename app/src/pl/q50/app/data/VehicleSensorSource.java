package pl.q50.app.data;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads every sensor the head unit exposes. On InTouch the vehicle CAN signals appear as
 * sensors with vendor "Ygomi", names "VS_ID_*" and types 12..53. Other sensors (if any) are
 * listed too, so the explorer shows the complete picture.
 */
public final class VehicleSensorSource implements SensorEventListener {
    private final SensorManager sm;
    private final DataHub hub;
    private final Map<Sensor, Channel> channels = new HashMap<Sensor, Channel>();
    private boolean registered;

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
        int t = s.getType();
        return vendor.indexOf("ygomi") >= 0 || name.indexOf("VS_ID") >= 0 || (t >= 12 && t <= 53);
    }

    public int sensorCount() {
        return channels.size();
    }

    public void start() {
        if (sm == null || registered) {
            return;
        }
        for (Sensor s : channels.keySet()) {
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        }
        registered = true;
    }

    public void stop() {
        if (sm == null || !registered) {
            return;
        }
        sm.unregisterListener(this);
        registered = false;
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
