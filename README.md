# Q50 Monitor

An app for the factory **InTouch** head unit in the Infiniti Q50 (V37, MY2014–2019), installed through **App Garage** from a USB stick. It reads the vehicle data that InTouch exposes to apps as Android sensors – no OBD adapter, no phone.

Plan and analysis (Polish): [`docs/PLAN.md`](docs/PLAN.md).

## Version 0.1 – Sensor explorer

| Tab | What it shows |
|---|---|
| **PRZEGLĄD** (overview) | RPM, speed, gear, throttle, coolant and oil temperature, oil pressure, torque, power, G-forces, pressure in all 4 tyres, with max values |
| **SENSORY** (sensors) | Every sensor the head unit exposes: type, name, values `v0–v2`, update rate, observed min…max. Tapping a row shows a 20 s chart. Filter: vehicle / changing / all |
| **LOG** | CSV logging (10 rows/s, all sensors), MARKER button, choice of storage (internal storage / USB stick) |

The UI is in Polish.

Mapped signals (sensor type → name, scale) live in [`app/assets/sensor-map.json`](app/assets/sensor-map.json). They come from community findings on the same engine (VR30DDTT) and are **not yet verified on this car** – that is what the explorer and the logs are for.

Without vehicle sensors (e.g. in an emulator) the app switches to a clearly labelled **DEMO** mode.

## Installing in the car

1. Format a USB stick as **FAT32** and put `q50-monitor.epk` in its **root** folder.
2. With the engine running, wait until InTouch finishes "Loading all apps".
3. Lower screen: **APPS → App Garage → Install Apps via USB** → select *Q50 Monitor* → **Install**.
4. Do not remove the stick during installation.

Updates: a newer `.epk` installs over an older one only if it is signed with **the same key**. If App Garage does not offer the new version, uninstall the old one and install again.

## Building

Requirements: JDK 17+, Node 22.18+, and `aapt`, `zipalign`, `apksigner`, `dx`/`d8`
(Ubuntu: `sudo apt-get install aapt zipalign apksigner dalvik-exchange`).

```bash
npm ci
npm test                    # .epk tool tests
bash scripts/test-java.sh   # app logic tests (plain JVM)
bash scripts/build-apk.sh   # -> build/q50-monitor.apk + build/q50-monitor.epk
```

The first build creates the signing key `signing/q50.keystore` (not in git). **Keep it** – without it an installed copy cannot be updated.

GitHub Actions builds the `.epk` on every push (artifact *q50-monitor-N*). For CI builds to update an installed app, add these repository secrets:
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
