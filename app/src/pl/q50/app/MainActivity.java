package pl.q50.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import pl.q50.app.data.CsvLogger;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.DemoSource;
import pl.q50.app.data.SensorMap;
import pl.q50.app.data.Storage;
import pl.q50.app.data.VehicleSensorSource;
import pl.q50.app.i18n.Strings;
import pl.q50.app.ui.AppHost;
import pl.q50.app.ui.RootView;

/**
 * Q50 Monitor – reads the vehicle signals InTouch exposes as Android sensors.
 * One 10 Hz tick drives history sampling, CSV logging and redraws.
 */
public final class MainActivity extends Activity {
    private static final long TICK_MS = 100;
    private static final String PREFS = "q50";

    private DataHub hub;
    private VehicleSensorSource vehicle;
    private DemoSource demo;
    private CsvLogger logger;
    private RootView root;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private boolean visible;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            hub.sample(now);
            logger.writeRow(now);
            if (visible) {
                root.invalidate();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Strings.setLanguage(prefs.getString("lang", Strings.PL));
        hub = new DataHub(SensorMap.load(getAssets()));
        vehicle = new VehicleSensorSource((SensorManager) getSystemService(SENSOR_SERVICE), hub);
        demo = new DemoSource(hub);
        logger = new CsvLogger();
        root = new RootView(this, hub, logger, new Host());
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
        // Keep reading (and logging) in the background while a log is being recorded.
        if (!logger.isActive()) {
            vehicle.stop();
            demo.stop();
            stopTicking();
        }
    }

    @Override
    protected void onDestroy() {
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
    }
}
