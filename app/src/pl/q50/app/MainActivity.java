package pl.q50.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import pl.q50.app.data.Channel;
import pl.q50.app.data.CsvLogger;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.DemoSource;
import pl.q50.app.data.SensorMap;
import pl.q50.app.data.Storage;
import pl.q50.app.data.VehicleSensorSource;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.AlertEngine;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.PerfTimer;
import pl.q50.app.logic.Settings;
import pl.q50.app.logic.Snapshot;
import pl.q50.app.logic.Store;
import pl.q50.app.ui.AppHost;
import pl.q50.app.ui.Panels;
import pl.q50.app.ui.RootView;

/**
 * My Q50 – reads the vehicle signals InTouch exposes as Android sensors.
 * One 10 Hz tick drives history sampling, analytics (alerts, timing, trip), CSV logging and
 * redraws. It keeps running while the app is in the background, so trip statistics and the
 * turbo cool-down estimate stay correct when another app is on screen.
 */
public final class MainActivity extends Activity {
    private static final long TICK_MS = 100;
    private static final String PREFS = "q50";
    private static final long SAVE_EVERY_MS = 30000;

    private DataHub hub;
    private VehicleSensorSource vehicle;
    private DemoSource demo;
    private CsvLogger logger;
    private RootView root;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private boolean visible;
    private boolean ticking;
    private Settings settings;
    private Analytics analytics;
    private Store store;
    private final Snapshot snap = new Snapshot();
    private long lastSaveMs;
    private long shownResultAt;
    private ToneGenerator tone;

    private final Runnable tick = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            hub.sample(now);
            fillSnapshot(now);
            analytics.update(snap, System.currentTimeMillis());
            logger.writeRow(now);
            onAnalyticsTick(now);
            if (visible) {
                root.invalidate();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private float value(String key) {
        Channel c = hub.signal(key);
        return c != null && c.hasValue() ? c.value() : Float.NaN;
    }

    private void fillSnapshot(long now) {
        snap.now = now;
        snap.rpm = value("rpm");
        snap.speed = value("speed");
        snap.throttle = value("throttle");
        snap.coolant = value("coolant");
        snap.oilTemp = value("oil_temp");
        snap.oilPress = value("oil_press");
        snap.torque = value("torque");
        snap.power = value("power");
        snap.gLat = value("g_lat");
        snap.gLong = value("g_long");
        Channel gear = hub.signal("gear");
        snap.gear = gear != null && gear.hasValue() ? Math.round(gear.raw()) : -1;
        snap.tyres[Snapshot.FL] = value("tpms_fl");
        snap.tyres[Snapshot.FR] = value("tpms_fr");
        snap.tyres[Snapshot.RL] = value("tpms_rl");
        snap.tyres[Snapshot.RR] = value("tpms_rr");
    }

    private void onAnalyticsTick(long now) {
        PerfTimer perf = analytics.perf;
        if (perf.lastCompleted >= 0 && perf.lastCompletedAt != shownResultAt) {
            shownResultAt = perf.lastCompletedAt;
            Strings t = Strings.get();
            root.message(t.newResult(t.runNames[perf.lastCompleted],
                    Panels.result(perf.lastCompleted, perf.last[perf.lastCompleted])));
            analytics.save(store);
        }
        int raised = analytics.alerts.raisedSeverity;
        if (raised >= AlertEngine.WARNING && settings.sound()) {
            beep(raised == AlertEngine.CRITICAL);
        }
        if (now - lastSaveMs >= SAVE_EVERY_MS) {
            lastSaveMs = now;
            analytics.save(store);
        }
    }

    private void beep(boolean critical) {
        try {
            if (tone == null) {
                tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            }
            tone.startTone(critical ? ToneGenerator.TONE_SUP_ERROR : ToneGenerator.TONE_PROP_BEEP2, critical ? 900 : 300);
        } catch (RuntimeException e) {
            tone = null; // audio not available on this unit – stay silent
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Strings.setLanguage(prefs.getString("lang", Strings.PL));
        store = new PrefsStore();
        settings = new Settings();
        settings.load(store);
        analytics = new Analytics(settings);
        analytics.load(store);
        hub = new DataHub(SensorMap.load(getAssets()));
        vehicle = new VehicleSensorSource((SensorManager) getSystemService(SENSOR_SERVICE), hub);
        demo = new DemoSource(hub);
        logger = new CsvLogger();
        root = new RootView(this, hub, logger, analytics, new Host());
        setContentView(root);
        root.showPage(prefs.getInt("page", 0));
        if (hub.map.error != null) {
            root.message(hub.map.error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        vehicle.start();
        if (hub.vehicleCount() == 0) {
            demo.start();
        }
        startTicking();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        prefs.edit().putInt("page", root.currentPage()).commit();
        analytics.save(store);
        // Sensors and the tick keep running in the background (trip, cool-down, logging).
    }

    @Override
    protected void onDestroy() {
        analytics.save(store);
        if (tone != null) {
            tone.release();
        }
        logger.stop();
        vehicle.stop();
        demo.dispose();
        stopTicking();
        super.onDestroy();
    }

    private void startTicking() {
        if (!ticking) {
            ticking = true;
            handler.post(tick);
        }
    }

    private void stopTicking() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    private final class Host implements AppHost {
        public void toggleLogging(Storage.Option target) {
            if (logger.isActive()) {
                logger.stop();
                root.message(Strings.get().savedRows(logger.rows()));
                return;
            }
            String err = logger.start(target.logDir(), hub.channels());
            root.message(err != null ? err : Strings.get().loggingTo(logger.file().getName()));
        }

        public void marker() {
            if (!logger.isActive()) {
                root.message(Strings.get().startLoggingFirst);
                return;
            }
            logger.marker();
            root.message(Strings.get().markerNo(logger.markers()));
        }

        public void message(String text) {
            root.message(text);
        }

        public String savedStoragePath() {
            return prefs.getString("storage", null);
        }

        public void saveStoragePath(String path) {
            prefs.edit().putString("storage", path).commit();
        }

        public void setLanguage(String code) {
            Strings.setLanguage(code);
            prefs.edit().putString("lang", Strings.get().code).commit();
            root.invalidate();
        }

        public String versionText() {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                return info.versionName + " (" + info.versionCode + ")";
            } catch (PackageManager.NameNotFoundException e) {
                return "?";
            }
        }

        public int vehicleSensorCount() {
            return hub.vehicleCount();
        }

        public void settingsChanged() {
            settings.save(store);
        }

        public void analyticsChanged() {
            analytics.save(store);
        }
    }

    private final class PrefsStore implements Store {
        public String get(String key) {
            return prefs.getString(key, null);
        }

        public void put(String key, String value) {
            prefs.edit().putString(key, value).commit();
        }
    }
}
