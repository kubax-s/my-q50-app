package pl.q50.app.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.os.SystemClock;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import pl.q50.app.data.Channel;
import pl.q50.app.data.CsvLogger;
import pl.q50.app.data.DataHub;
import pl.q50.app.data.SensorMap;
import pl.q50.app.data.SignalDef;
import pl.q50.app.data.Storage;
import pl.q50.app.i18n.Strings;
import pl.q50.app.logic.Analytics;
import pl.q50.app.logic.Settings;
import pl.q50.app.logic.Snapshot;

/**
 * Desktop preview: runs a scripted drive through the real analytics and renders every tab of
 * the real RootView to PNG (800×480 design grid, 2× pixels).
 *
 * The drive: cold start and idle, city driving (one high rev on cold oil), motorway, a full
 * throttle launch to 160 km/h and a hard stop, a winding road, then parking straight after.
 * Tyre RR is 0.3 bar low. All values are synthetic.
 */
public final class Preview {
    private static final int W = 1600;
    private static final int H = 960;
    private static final long TICK = 100;

    private final DataHub hub;
    private final Analytics analytics;
    private final Snapshot snap = new Snapshot();
    private final CsvLogger logger = new CsvLogger();
    private long t;

    // vehicle state driven by the script
    private float rpm = 800;
    private float speed;
    private float throttle;
    private int gear = 1;
    private float gLat;
    private float gLong;
    private float torque = 40;
    /** Scripted target speed; tick() ramps towards it like a real car (unless direct). */
    private float target;
    private boolean direct;

    private Preview() {
        hub = new DataHub(SensorMap.load(new AssetManager("app/assets")));
        for (int i = 0; i < hub.map.all().size(); i++) {
            SignalDef d = hub.map.all().get(i);
            hub.add(new Channel(d.type, "VS_ID_" + d.key.toUpperCase(), "Ygomi", true, false, 0, 0));
        }
        // the ~27 still-unknown signals, with made-up placeholder names
        int[] unknown = {18, 19, 24, 25, 26, 27, 28, 29, 30, 31, 33, 34, 35, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int i = 0; i < unknown.length; i++) {
            hub.add(new Channel(unknown[i], "VS_ID_" + unknown[i], "Ygomi", true, false, 0, 0));
        }
        analytics = new Analytics(new Settings());
    }

    private Channel ch(String key) {
        return hub.signal(key);
    }

    private void set(String key, float value) {
        Channel c = ch(key);
        float raw = (value - c.def.offset) / c.def.scale;
        c.onValues(new float[]{raw, 0, 0}, t);
    }

    private static float torqueAt(float rpm) {
        if (rpm < 1000) return 300;
        if (rpm < 1600) return 300 + (rpm - 1000) / 600f * 100;
        if (rpm <= 5200) return 400;
        return 400 - (rpm - 5200) / 1600f * 80;
    }

    private void tick() {
        if (!direct) {
            float step = 12f * TICK / 1000f; // gentle 12 km/h per second
            speed = speed < target ? Math.min(target, speed + step) : Math.max(target, speed - step);
        }
        float sec = t / 1000f;
        float coolant = Math.min(90f, 12f + sec * 0.19f);
        float oil = Math.min(100f, 10f + sec * 0.13f);
        float press = 0.8f + rpm / 1000f * 0.55f + Math.max(0f, 80f - oil) * 0.02f;
        set("rpm", rpm);
        set("speed", speed);
        set("throttle", throttle);
        set("gear", gear);
        set("coolant", coolant);
        set("oil_temp", oil);
        set("oil_press", press);
        set("torque", torque);
        set("power", rpm * torque / 7023.5f);
        set("g_lat", gLat);
        set("g_long", gLong);
        set("tpms_fl", 2.45f);
        set("tpms_fr", 2.45f);
        set("tpms_rl", 2.40f);
        set("tpms_rr", 2.12f);
        for (Channel c : hub.channels()) {
            if (c.def == null) {
                float v = c.type % 3 == 0 ? (float) Math.round(Math.sin(sec / (c.type % 7 + 2)) * 40 + 50) : c.type * 1.5f;
                c.onValues(new float[]{v, 0, 0}, t);
            }
        }
        hub.sample(t);
        fill();
        analytics.update(snap, 1_800_000_000_000L + t);
        t += TICK;
    }

    private void fill() {
        snap.now = t;
        snap.rpm = ch("rpm").value();
        snap.speed = ch("speed").value();
        snap.throttle = ch("throttle").value();
        snap.coolant = ch("coolant").value();
        snap.oilTemp = ch("oil_temp").value();
        snap.oilPress = ch("oil_press").value();
        snap.torque = ch("torque").value();
        snap.power = ch("power").value();
        snap.gLat = ch("g_lat").value();
        snap.gLong = ch("g_long").value();
        snap.gear = Math.round(ch("gear").raw());
        snap.tyres[Snapshot.FL] = ch("tpms_fl").value();
        snap.tyres[Snapshot.FR] = ch("tpms_fr").value();
        snap.tyres[Snapshot.RL] = ch("tpms_rl").value();
        snap.tyres[Snapshot.RR] = ch("tpms_rr").value();
    }

    private void cruise(float seconds, float kmh, float rpmBase, float rpmSwing, float period) {
        long end = t + (long) (seconds * 1000);
        while (t < end) {
            float s = t / 1000f;
            gear = 4;
            target = kmh + (float) Math.sin(s / period) * kmh * 0.15f;
            rpm = rpmBase + (float) Math.sin(s / (period * 0.7f)) * rpmSwing;
            throttle = 20 + (float) Math.sin(s / period) * 8;
            torque = 120;
            gLat = (float) Math.sin(s / 3) * 0.05f;
            gLong = (float) Math.cos(s / period) * 0.05f;
            tick();
        }
    }

    private void idle(float seconds, int g) {
        long end = t + (long) (seconds * 1000);
        while (t < end) {
            gear = speed > 1 ? 4 : g;
            target = 0;
            rpm = 700;
            throttle = 0;
            torque = 30;
            gLat = 0;
            gLong = 0;
            tick();
        }
    }

    /** Full-throttle launch through M1..M5 up to {@code top} km/h, then a hard stop. */
    private void launchAndBrake(float top) {
        float[] rpmPerKmh = {95, 57, 38, 29, 23};
        int g = 0;
        direct = true;
        speed = 0;
        while (speed < top) {
            float accel = 23f - 0.085f * speed; // km/h per second
            speed += accel * TICK / 1000f;
            rpm = Math.max(1800, speed * rpmPerKmh[g]);
            if (rpm > 6800 && g < rpmPerKmh.length - 1) {
                g++;
                rpm = speed * rpmPerKmh[g];
            }
            gear = 16 + g;
            throttle = 100;
            torque = torqueAt(rpm);
            gLong = accel / 3.6f / 9.81f;
            gLat = 0;
            tick();
        }
        while (speed > 0) {
            speed = Math.max(0, speed - 36f * TICK / 1000f);
            rpm = Math.max(800, speed * 23);
            throttle = 0;
            torque = 0;
            gLong = speed > 0 ? -1.02f : 0;
            tick();
        }
        direct = false;
        target = 0;
    }

    private void windingRoad(float seconds) {
        long end = t + (long) (seconds * 1000);
        while (t < end) {
            float s = t / 1000f;
            gear = 18;
            gLat = (float) Math.sin(s / 2.2) * 0.85f;
            gLong = (float) Math.cos(s / 2.2) * 0.4f;
            target = 80 + (float) Math.sin(s / 2.2) * 15;
            rpm = 3600 + (float) Math.cos(s / 2.2) * 1300;
            throttle = 55 + (float) Math.cos(s / 2.2) * 40;
            torque = throttle >= 90 ? torqueAt(rpm) : torqueAt(rpm) * throttle / 100f;
            tick();
        }
    }

    private void run() {
        idle(60, 1);
        // city driving, with one high rev on cold oil
        for (int i = 0; i < 6; i++) {
            cruise(25, 45, 1700, 300, 4);
            idle(8, 4);
        }
        long end = t + 3000;
        while (t < end) {
            rpm = 4600;
            throttle = 85;
            target = 60;
            tick();
        }
        cruise(120, 50, 1900, 400, 5);
        cruise(420, 125, 2300, 1100, 9); // motorway, learns the oil pressure baseline
        idle(15, 4); // come to a full stop before the launch
        launchAndBrake(200);
        cruise(60, 70, 2000, 500, 6);
        windingRoad(150);
        idle(25, 1); // parked right after hard driving (first seconds are still rolling)
    }

    private void render(RootView root, int page, File out) throws IOException {
        root.showPage(page);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        root.onDraw(new Canvas(img));
        ImageIO.write(img, "png", out);
    }

    private static void contactSheet(File[] files, String[] titles, File out) throws IOException {
        int cols = 2;
        int cw = W / 2;
        int chh = H / 2;
        int label = 44;
        int rows = (files.length + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cw + 3 * 16, rows * (chh + label) + (rows + 1) * 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x05070A));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font("DejaVu Sans", Font.BOLD, 24));
        for (int i = 0; i < files.length; i++) {
            int x = 16 + (i % cols) * (cw + 16);
            int y = 16 + (i / cols) * (chh + label + 16);
            g.setColor(new Color(0xE8EAED));
            g.drawString(titles[i], x, y + 30);
            g.drawImage(ImageIO.read(files[i]), x, y + label, cw, chh, null);
        }
        ImageIO.write(sheet, "png", out);
    }

    /** Engine tab's turbo panel at each urgency level, side by side: build/preview/pl/turbo-levels.png. */
    private void turboLevels(AppHost host) throws IOException {
        Strings.setLanguage(Strings.PL);
        windingRoad(150); // more hard driving so the cool-down starts in the red
        RootView root = new RootView(new Context(), hub, logger, analytics, host);
        root.setPreviewSize(W, H);
        int[] levels = {2, 1, 0};
        BufferedImage strip = new BufferedImage(3 * 788 + 4 * 16, 364 + 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        g.setColor(new Color(0x05070A));
        g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
        for (int i = 0; i < levels.length; i++) {
            idle(0.1f, 1);
            while (analytics.turbo.level() != levels[i]) {
                idle(0.1f, 1);
            }
            idle(1.5f, 1);
            SystemClock.now = t;
            root.showPage(2);
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            root.onDraw(new Canvas(img));
            g.drawImage(img.getSubimage(800, 112, 788, 364), 16 + i * (788 + 16), 16, null);
        }
        ImageIO.write(strip, "png", new File("build/preview/pl/turbo-levels.png"));
    }

    public static void main(String[] args) throws IOException {
        Preview p = new Preview();
        p.run();
        SystemClock.now = p.t;

        AppHost host = new AppHost() {
            public void toggleLogging(Storage.Option target) {
            }

            public void marker() {
            }

            public void message(String text) {
            }

            public String savedStoragePath() {
                return null;
            }

            public void saveStoragePath(String path) {
            }

            public void setLanguage(String code) {
            }

            public String versionText() {
                return "0.4.3 (preview)";
            }

            public int vehicleSensorCount() {
                return 42;
            }

            public void settingsChanged() {
            }

            public void analyticsChanged() {
            }
        };
        String[] names = {"overview", "alerts", "engine", "trip", "gmeter", "sensors", "log", "settings"};
        for (int lang = 0; lang < 2; lang++) {
            String code = lang == 0 ? Strings.PL : Strings.EN;
            Strings.setLanguage(code);
            RootView root = new RootView(new Context(), p.hub, p.logger, p.analytics, host);
            root.setPreviewSize(W, H);
            File dir = new File("build/preview/" + code);
            dir.mkdirs();
            File[] files = new File[names.length];
            String[] titles = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                files[i] = new File(dir, (i + 1) + "-" + names[i] + ".png");
                p.render(root, i, files[i]);
                titles[i] = (i + 1) + ". " + names[i];
            }
            contactSheet(files, titles, new File(dir, "all.png"));
        }
        p.turboLevels(host);
        System.out.println("Rendered previews to build/preview/{pl,en} (simulated " + p.t / 1000 + " s of driving)");
    }
}
