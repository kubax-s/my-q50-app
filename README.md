# My Q50

An app for the factory **InTouch** head unit in the Infiniti Q50 (V37, MY2014–2019), installed through **App Garage** from a USB stick. It reads the vehicle data that InTouch exposes to apps as Android sensors – no OBD adapter, no phone.

Plan and analysis: [`docs/PLAN.md`](docs/PLAN.md).

## Version 0.4

| Tab | What it shows |
|---|---|
| **PRZEGLĄD** (overview) | Big tiles: RPM, speed, gear, throttle, coolant and oil temperature, oil pressure, torque, power, G-forces, all 4 tyres – live values only. Tiles can be hidden in settings; the grid re-flows |
| **ALERTY** (alerts) | State of every alert (tap an active one to snooze/unsnooze), recent alert history, tyre pressures vs. the average and slow-leak check |
| **SILNIK** (engine) | Warm-up state and times, turbo cool-down timer, oil pressure vs. a baseline learned per 500 rpm, "dyno" power/torque curve from full-throttle pulls |
| **TRASA** (trip) | Trip computer (time, driving/idle, distance, average/max speed, max RPM/power/torque/temperatures, spirited share); automatic 0–100, 1/4 mile, 60–100, 80–120, 100–200 km/h and 100–0 braking with last/best; time in RPM bands and in each gear |
| **G-METR** (G-meter) | Friction circle with live dot, 5 s trail and the envelope of the maximum reached in each direction; current and maximum lateral/acceleration/braking/combined G |
| **SENSORY** (sensors) | Every sensor the head unit exposes: type, name, values `v0–v2`, update rate, observed min…max. Tapping a row shows a 20 s chart |
| **LOG** | CSV logging (10 rows/s, all sensors), markers (button or long-press anywhere), choice of storage |
| **USTAWIENIA** (settings) | Three sections: **general** (language, theme: dark / black / graphite / light, accent colour from 7 swatches), **tiles** (what the overview shows), **alerts** (which alerts are on, alert sound). Status colours (red / orange / green / blue) never change with the theme, so warnings keep their meaning |

### Alerts
Shown as a banner at the bottom of every tab; tapping it snoozes that alert for 5 minutes (the ALERTY tab can undo that). Each one can be switched off in settings.

| Alert | Condition |
|---|---|
| Coolant overheating | ≥ 105 °C for 2 s |
| Oil overheating | ≥ 125 °C for 2 s |
| Low oil pressure | < 0.5 bar with the engine running |
| Oil pressure below normal | < 70 % of the learned value for this RPM (warm oil) |
| Cold oil under load | oil < 60 °C and > 4000 rpm or > 80 % throttle |
| Low tyre pressure | any tyre < 2.0 bar |
| One tyre lower | a tyre > 0.25 bar below the other three |
| Slow tyre leak | a tyre lost > 0.15 bar more than the others over 2–14 days (session-start readings) |
| Turbo cool-down | parked after hard driving – one bar with three zones and a "now" marker: red = don't switch off, orange = OK to switch off, green = optional extra margin (last 15 s). An estimate from engine load; hot oil (100–120 °C) builds it up to 1.5× faster and halves the cooling rate, since there is no turbo/exhaust temperature on the head unit |

Thresholds are deliberately simple and live in [`app/src/pl/q50/app/logic/`](app/src/pl/q50/app/logic); they will be tuned once the signal scales are confirmed on the car. Records, the oil pressure baseline and tyre history are stored on the head unit.

The UI is in Polish by default and can be switched to English in the settings tab (the choice is remembered). All texts live in [`Strings.java`](app/src/pl/q50/app/i18n/Strings.java); signal names come from `label`/`label_en` in the sensor map.

Mapped signals (sensor type → name, scale) live in [`app/assets/sensor-map.json`](app/assets/sensor-map.json). They come from community findings on the same engine (VR30DDTT) and are **not yet verified on this car** – that is what the explorer and the logs are for.

Without vehicle sensors (e.g. in an emulator) the app switches to a clearly labelled **DEMO** mode.

## Installing in the car

1. Format a USB stick as **FAT32** and put `my-q50.epk` in its **root** folder.
2. With the engine running, wait until InTouch finishes "Loading all apps".
3. Lower screen: **APPS → App Garage → Install Apps via USB** → select *My Q50* → **Install**.
4. Do not remove the stick during installation.

Updates: a newer `.epk` installs over an older one only if it is signed with **the same key**. If App Garage does not offer the new version, uninstall the old one and install again.

## Building

Requirements: JDK 17+, Node 22.18+, and `aapt`, `zipalign`, `apksigner`, `dx`/`d8`
(Ubuntu: `sudo apt-get install aapt zipalign apksigner dalvik-exchange`).

```bash
npm ci
npm test                    # .epk tool tests
bash scripts/test-java.sh   # formatting, i18n and logic tests (plain JVM)
bash scripts/build-apk.sh   # -> build/my-q50.apk + build/my-q50.epk
```

The first build creates the signing key `signing/q50.keystore` (not in git). **Keep it** – without it an installed copy cannot be updated.

### Performance on the head unit

The InTouch Atom renders in software (no GPU acceleration for apps on Android 2.3), so the app keeps both drawing and sensor traffic low. Limits follow what is known about the unit and what other InTouch apps run with (see [docs/PLAN.md § Head unit resources](docs/PLAN.md#15-head-unit-resources)):
- **At most 4 redraws per second** – the rate [Q50-Telemetry](https://github.com/rayrmzz/Q50-Telemetry) validated on InTouch without "not responding" errors. Overview, engine, G-meter, sensors 4/s; trip, alerts, log 2/s; settings 1/s; taps redraw at once. A frame is skipped when no sensor value changed (parked with the engine off: one frame every 5 s).
- **Sensors on a background thread** at 100 ms for animated signals and 200 ms for the rest (the periods other InTouch apps use); the explorer asks for 100 ms for all. Registration failures are counted, never crash the app.
- **Lean in the background** (another app on screen, no log running): 2 Hz tick, only the 13 signals trip/alerts/cool-down need, acceleration timing paused.
- **Logs never fill RAM**: `/mnt/sdcard` on InTouch is a tmpfs (RAM). Storage is classified via `/proc/mounts`; a real USB stick is chosen automatically, RAM only by hand with a 2 MB cap, disks keep 50 MB free and cap a file at 200 MB. Rows only contain changed values (empty cell = unchanged).
- **No per-frame garbage** in hot paths (cached formatted values, alert texts built only while active), a cached tab bar bitmap, no double background fill, no file-system calls while drawing, storage scan off the UI thread.
- **Flash-friendly** – settings/records/baselines written at most once a minute, only when changed, asynchronously (`apply()`).
- **Small**: ~0.45 MB of app data on the heap after a long drive; `onLowMemory` drops the cached tab bar.

`bash scripts/preview.sh --bench` prints time, allocations and draw calls per frame for every tab (app code only).

### Icon

The launcher icon is generated by `tools/icon/MakeIcon.java` in two sizes: 48 px ([`drawable`](app/res/drawable/ic_launcher.png), mdpi) and 72 px ([`drawable-hdpi`](app/res/drawable-hdpi/ic_launcher.png)) – the InTouch screen reports 240 dpi (hdpi), so the 72 px one is used there without scaling. To use your own image on your own unit, put a square PNG at `app/icon-override.png`: it is git-ignored and the build scales it to both sizes.

### Desktop preview (no Android needed)

```bash
bash scripts/preview.sh     # -> build/preview/{pl,en}/N-tab.png + all.png
```

Runs a scripted drive (cold start, city, motorway, launch and braking, winding road, parking) through the real analytics and renders every tab with the **real drawing code**: the Android graphics calls go to `java.awt` via small stand-ins in [`tools/preview/shim`](tools/preview/shim). Fonts differ slightly from the head unit (DejaVu Sans vs. Droid Sans, which is narrower), so it is a close preview, not a pixel-exact one.

GitHub Actions builds the `.epk` on every push (artifact *my-q50-N*). For CI builds to update an installed app, add these repository secrets:
- `APK_KEYSTORE_B64` – output of `base64 -w0 signing/q50.keystore`
- `APK_KEYSTORE_PASS` – keystore password (default `android`)

### Platform constraints
- Android 2.3.7 (API 10), x86, Dalvik: Java 8 without lambdas and without APIs missing from Android 2.3; no libraries, no native code.
- APK signature scheme v1 (JAR).
- `.epk` = the APK encrypted with AES-256-CBC, with the AES key encrypted to the public OBU certificate from the firmware ([`keys/`](keys/README.md)). Tool: `node tools/epk.ts build|inspect`.

## Credits

The `.epk` format, the OBU certificate and the sensor map come from community work:
[bugjosh/appgarage-dash](https://github.com/bugjosh/appgarage-dash) (MIT),
[oneezeeroo/Q50-Reverse-Engineering](https://github.com/oneezeeroo/Q50-Reverse-Engineering) (MIT).

Personal project, not affiliated with Infiniti/Nissan. Use only in your own car, and keep your eyes on the road rather than the screen.
