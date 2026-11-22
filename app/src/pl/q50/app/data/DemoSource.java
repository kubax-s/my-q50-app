package pl.q50.app.data;

import android.os.Handler;
import android.os.SystemClock;

import java.util.List;

/**
 * Synthetic signals for testing the UI without the car (emulator, phone). Every demo channel is
 * flagged so the UI can show a DEMO banner; it is never mixed with real vehicle data.
 */
public final class DemoSource {
    private static final long PERIOD_MS = 50;
    /** Unmapped types so the explorer has something "unknown" to show. */
    private static final int[] EXTRA_TYPES = {24, 25, 41, 45};

    private final DataHub hub;
    private final Handler handler = new Handler();
    private final float[] v = new float[3];
    private Channel[] mapped;
    private Channel[] extra;
    private long t0;
    private boolean running;

    private final Runnable tick = new Runnable() {
        public void run() {
            if (!running) {
                return;
            }
            step(SystemClock.uptimeMillis());
            handler.postDelayed(this, PERIOD_MS);
        }
    };

    public DemoSource(DataHub hub) {
        this.hub = hub;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            return;
        }
        if (mapped == null) {
            List<SignalDef> defs = hub.map.all();
            mapped = new Channel[defs.size()];
            for (int i = 0; i < defs.size(); i++) {
                SignalDef d = defs.get(i);
                mapped[i] = hub.add(new Channel(d.type, "DEMO_" + d.key.toUpperCase(), "DEMO", true, true, 0, 0));
            }
            extra = new Channel[EXTRA_TYPES.length];
            for (int i = 0; i < EXTRA_TYPES.length; i++) {
                extra[i] = hub.add(new Channel(EXTRA_TYPES[i], "DEMO_UNKNOWN_" + EXTRA_TYPES[i], "DEMO", true, true, 0, 0));
            }
        }
        t0 = SystemClock.uptimeMillis();
        running = true;
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    /** Removes demo channels from the hub (when real sensors show up). */
    public void dispose() {
        stop();
        hub.removeDemo();
        mapped = null;
        extra = null;
    }

    private void step(long now) {
        double t = (now - t0) / 1000.0;
        // A 20 s cycle: accelerate through the gears, then cruise.
        double phase = (t % 20.0) / 20.0;
        double rpm = phase < 0.6 ? 1500 + 5000 * ((phase * 5) % 1.0) : 1800 + 100 * Math.sin(t);
        int gear = phase < 0.6 ? 16 + (int) (phase * 5) : 4;
        double speed = phase < 0.6 ? 20 + phase * 230 : 120;
        double warm = Math.min(1.0, t / 120.0);
        double torque = phase < 0.6 ? 350 + 50 * Math.sin(t * 3) : 80;

        for (int i = 0; i < mapped.length; i++) {
            Channel c = mapped[i];
            String key = c.def == null ? "" : c.def.key;
            float raw;
            if ("rpm".equals(key)) raw = (float) rpm;
            else if ("speed".equals(key)) raw = (float) speed;
            else if ("gear".equals(key)) raw = gear;
            else if ("throttle".equals(key)) raw = (float) (phase < 0.6 ? 95 : 12);
            else if ("torque".equals(key)) raw = (float) torque;
            else if ("power".equals(key)) raw = (float) (rpm * torque);
            else if ("coolant".equals(key)) raw = (float) (20 + 70 * warm);
            else if ("oil_temp".equals(key)) raw = (float) (20 + 85 * warm);
            else if ("oil_press".equals(key)) raw = (float) (0.15 + rpm / 6500.0 * 0.4);
            else if ("g_lat".equals(key)) raw = (float) (0.4 * Math.sin(t / 2));
            else if ("g_long".equals(key)) raw = (float) (phase < 0.6 ? 0.35 : -0.05);
            else if (key.startsWith("tpms")) raw = (float) (36 + (c.type - 36) * 0.4);
            else raw = 0;
            v[0] = raw;
            c.onValues(v, now);
        }
        for (int i = 0; i < extra.length; i++) {
            v[0] = (float) (Math.sin(t / (i + 1)) * (i + 1) * 10);
            extra[i].onValues(v, now);
        }
    }
}
