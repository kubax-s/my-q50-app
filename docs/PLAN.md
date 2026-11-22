# Q50 App – analysis and work plan

An app installed on the **factory InTouch head unit** (App Garage, `.epk` package from a USB stick) in an Infiniti Q50 (V37) **MY2018** with the **3.0t VR30DDTT engine (305 hp, stock)**, showing as much data about the car as possible – without an OBD adapter and without a phone.

---

## 1. Platform – what we know about InTouch

### 1.1 System
Sources: community projects [oneezeeroo/Q50-Reverse-Engineering](https://github.com/oneezeeroo/Q50-Reverse-Engineering), [bugjosh/appgarage-dash](https://github.com/bugjosh/appgarage-dash) (a working app verified on a 2018 Q60 Red Sport 400, VR30DDTT), [doudineugene-dot/Q50-gtr-](https://github.com/doudineugene-dot/Q50-gtr-), and analysis of the provided `gtr.epk` file.

| Property | Value |
|---|---|
| Model years with App Garage | Q50 MY2014–2019, Q60 (V37) MY2017–2019. **MY2020+ has no App Garage.** |
| App platform | Customized **Android 2.3.7 (API 10)**, **x86** CPU (Intel Atom), running alongside the Linux side of the system (navigation, "carwings") |
| Screens | two, about **800×480**; apps open on the lower one by default (`ivi.defaultDisplay=LOWER`) |
| Installation | **FAT32** USB stick, `.epk` file in the root folder → *APPS → App Garage → Install Apps via USB* |
| Updates | a new version needs a higher `versionCode` and the same APK signing key |

### 1.2 The `.epk` format (verified on `gtr.epk`)

```
0x000  ".epk"            magic
0x004  0x0002            EPK version (v2)
0x006  0x0002            payload type (2 = APK in known packages)
0x008  0x0001            block count
0x00A  0x0080            key length (128 B = RSA-1024)
0x00C  256 B             AES key encrypted with RSA (128 B used)
0x10C  128 B             file name: "gtr.apk"
0x18C  4 B (BE)          data length: 2,156,512
0x190  …                 APK encrypted with AES-CBC (length divisible by 16, entropy ≈ 8 bit/B)
```

**Key point:** EPK is **public-key encryption only** – building a package needs just the **public OBU certificate** from the firmware (`CN=tt18002`, issuer `IT5.YGOMI.COM CA`, RSA-1024), which is publicly available in community projects. We do not need the private key. The APK itself is signed with an ordinary self-made key (v1/JAR signature).

### 1.3 Where the app gets vehicle data
InTouch exposes CAN bus signals as **standard Android sensors** (`SensorManager`, vendor "Ygomi", names `VS_ID_*`, types **12–53**). The required permission `com.ygomi.permission.IVI_CAN_READ` is granted automatically. A manifest with `ivi.isDistractive="false"` lets the app be used while driving.

According to appgarage-dash there are **~42 live signals**. Mapped so far:

| Type | Signal | Scale (VR30) |
|---:|---|---|
| 12 | Torque | ≈ Nm |
| 13 | Engine RPM | direct |
| 14 | Coolant temperature | °C |
| 15 | Oil temperature | °C |
| 16 | **Oil pressure** | MPa (×10 = bar) |
| 17 | Speed | km/h |
| 20 / 21 | Lateral / longitudinal G | ~g |
| 22 | Gear | P=1, R=2, N=3, D=4, M1–M7 = 16–22 |
| 23 | Throttle | % |
| 32 | Power | raw × 1.047e-4 = kW |
| 36–39 | Tyre pressure FR, FL, RR, RL | psi |

**Not available** on the head unit's CAN (per appgarage-dash): boost/MAP, AFR/lambda, knock, ignition timing – these are engine ECU values read by tools like EcuTek. The commercial "Red Sport" app (vqapps) does advertise a boost gauge with an "AUTO FIND" feature – to be investigated what it derives it from.

**~27 sensor types remain unmapped** – that is our field of discovery (stage 2).

### 1.4 Technical consequences

- **React Native / modern Android are out** – API 10, Dalvik, no Gradle (AGP does not support such a low `minSdk`).
- Build path: `javac --release 8` (no lambdas) → `dx`/`d8 --min-api 10` → `aapt` → `zipalign` → `apksigner` (v1) → wrap into `.epk`.
- UI drawn by hand on `Canvas` (no AndroidX, no libraries) – light and smooth on the Atom.
- **Where TypeScript fits:** the Android 2.3 WebView is a very old WebKit (no `requestAnimationFrame`, slow canvas) – smooth gauges in TS in the car are risky. TypeScript makes sense in: the `.epk` build tool (Node `crypto`), a data simulator, and a web app for analysing logs copied from the car.

---

## 2. What the app can show

Legend: **[N]** – native InTouch sensor (confirmed), **[?]** – possibly among the unmapped sensors (to be investigated), **[O]** – computed, **[X]** – unavailable without an external source.

### 2.1 Engine (live)
- RPM, torque, power (kW/hp) **[N]**
- Coolant and **oil** temperature **[N]**
- **Oil pressure** **[N]** – rare; not shown on the factory gauges
- Throttle **[N]**
- ATF temperature, battery voltage, outside temperature, fuel level, instantaneous fuel consumption **[?]**
- Boost, AFR, knock, ignition timing, intake air temperature **[X / ? – see stage 5]**

### 2.2 Driving and chassis
- Speed, gear (including manual mode M1–M7) **[N]**
- G-meter: lateral and longitudinal G, friction circle, peaks **[N+O]**
- **Pressure in each tyre** on a top-down car graphic **[N]**
- Steering angle (DAS), wheel speeds, brake pressure **[?]**

### 2.3 Engine health (the edge over ordinary gauges)
- **Oil pressure vs RPM and temperature curve** – record a baseline and warn when pressure falls below normal for the current conditions **[N+O]**
- **Ready for hard driving:** warning on high RPM / full throttle while the oil is cold (e.g. < 60 °C), warm-up bar **[N+O]**
- Coolant and oil warm-up time (thermostat health), trend over time **[N+O]**
- Peak temperatures per trip and turbo "cool-down" after hard driving – timer for the recommended idle time before switching off **[N+O]**
- Tyres: alarm on pressure drop and left/right asymmetry, trend (slow leak) **[N+O]**

### 2.4 Performance
- 0–100, 60–100, 80–120, 100–200 km/h, 1/4 mile, 100–0 (braking) timing **[N+O]**
- Peak power and torque per trip, power/torque vs RPM chart ("dyno") **[N+O]**
- Shift light in manual mode **[N]**
- Records and measurement history **[O]**

### 2.5 Trips and statistics
- Trip computer: time, distance (integrated from speed), average speed, idle time **[N+O]**
- Time in RPM bands / gears, share of spirited driving **[N+O]**
- Fuel use and cost – if a fuel consumption/level sensor turns up **[?]**
- CSV logs to USB stick / device storage (to verify that USB writing works) → analysis in the web app **[N+O]**

### 2.6 Alerts
Coolant/oil overheating, low oil pressure, tyre pressure drop, high RPM on cold oil, turbo cool-down reminder.

---

## 3. Architecture

```
┌──────────────────────── InTouch app (APK, Java 8 bytecode, API 10) ─────────────────────┐
│ UI: custom Canvas views, pages switched by swipe/tabs, 800×480, dark theme              │
│   Engine │ Tyres/Chassis │ Performance │ Trip │ Health │ Sensor explorer (dev)          │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ DataHub: channels (value, unit, state LIVE/STALE/DEMO/UNAVAILABLE), min/max, history     │
│ Engines: alerts, performance timing, trip statistics, CSV logger                         │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ Sources: VehicleSensorSource (SensorManager) │ DemoSource (synthetic) │ ReplaySource (CSV)│
└──────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────── Tooling (TypeScript / Node) ──────────────┐   ┌──── Web app (TS) ─────────────┐
│ epk tool: build / inspect .epk packages               │   │ Load CSV logs from the car:   │
│ simulator: generate test logs                         │   │ charts, trips, "dyno",        │
│ CI: GitHub Actions → APK + EPK as artifacts           │   │ oil pressure trends, etc.     │
└───────────────────────────────────────────────────────┘   └───────────────────────────────┘
```

Principles:
- The sensor map (type → name, scale, unit) is **one config file** – easy to correct after testing in the car.
- Every value has a state; missing data shows as "no data", never a fake number.
- Zero external dependencies in the APK; compiled against the API 10 `android.jar` so no missing Android API slips in.
- A persistent APK signing key in GitHub secrets (otherwise updates will not install).

---

## 4. Work plan

### Stage 0 – Verify the car ✅
- [x] App Garage + *Install Apps via USB* available (MY2018).
- [x] A community package (`gtr.epk`) installs and runs.
- [ ] Note the InTouch software version.

### Stage 1 – Toolchain + sensor explorer (≈1 week)
- [x] Repo layout: `app/` (Java), `tools/` (TS), `scripts/`, `docs/`.
- [x] APK build script (javac/dx/aapt/zipalign/apksigner v1), checks for `minSdk`, no native code, v1 signature.
- [x] `tools/epk.ts` (TypeScript): `build` and `inspect`, with tests; `inspect` reads `gtr.epk` correctly.
- [x] GitHub Actions: every push → `.apk` + `.epk` artifacts.
- [x] **App v0.1 "explorer":** overview of mapped signals, list of all sensors with a 20 s chart, CSV logging with markers.
- [x] Decision: UI in Java on Canvas (TS for tooling and future log analysis).
- [ ] **First install in the car** and check where files can be written (USB stick? internal storage?).

### Stage 2 – Signal mapping (1–2 weeks, in the car)
- [ ] Test drives with logging; correlate unmapped sensors with actions: refuelling (fuel level), steering, braking, lights on, transmission warm-up.
- [ ] Confirm scales (compare with the gauges and the trip computer).
- [ ] Output: `docs/sensor-map.md` + the map config file.

### Stage 3 – Dashboard MVP ✅ (v0.4, not yet tested in the car)
- [x] Overview with configurable tiles, **Alerts**, **Engine**, **Trip** and **G-meter** pages.
- [x] Metric units, dark theme, large fonts, one-tap operation; Polish/English UI.

### Stage 4 – Health, alerts, performance, logs (v0.4, not yet tested in the car)
- [x] Alerts with on/off delays, hysteresis, snooze, per-alert enable switch, optional sound.
- [x] Oil pressure baseline per 500 rpm, warm-up times, turbo cool-down estimate, tyre asymmetry and slow-leak check.
- [x] 0–100, 1/4 mile, 60–100, 80–120, 100–200, 100–0 timing with records; "dyno" from the torque/power sensors.
- [x] Trip computer (distance, times, maxima, RPM bands, gears).
- [x] G-meter: friction circle, trail, per-direction envelope, maxima; alert history.
- [ ] Tune thresholds once the signal scales are confirmed on the car.
- [ ] Per-trip CSV logs (automatic start/stop).

### Stage 5 – Data beyond the head unit's CAN (research, optional)
- [ ] Investigate whether boost can be derived from the sensors (like "AUTO FIND" in Red Sport).
- [ ] Check whether apps on the InTouch Android side can use Bluetooth (OBD adapter) – if so: boost, AFR, fuel trims, ATF temperature via OBD.
- [ ] Alternative: a phone with an OBD adapter as a supplement.

### Stage 6 – Web app for log analysis (TS)
- [ ] Load CSV from the USB stick, trip charts, comparison of runs, engine health trends.

---

## 5. Risks

| Risk | Mitigation |
|---|---|
| Different InTouch firmware → different OBU certificate or sensor map | Stage 0 (test a known package), sensor map as config |
| Head unit damage/instability | Only the official App Garage path, no firmware changes; a small, simple app |
| No boost/AFR/knock on the head unit's CAN | Clearly marked as unavailable; research in stage 5 |
| Android 2.3 – old APIs, weak WebView | Java + Canvas, compile against API 10, early performance testing |
| Driver distraction (app active while driving) | Clear, static layouts; audible alerts instead of looking at the screen |
| Lost signing key → no updates | Key in GitHub secrets + a backup copy |

---

## 6. Decisions

- Car: Q50 MY2018, VR30DDTT 305 hp, stock (no EcuTek – boost/AFR/knock only via stage 5).
- App Garage works (`gtr.epk` installed).
- In-car app: **Java**; TypeScript for tooling and the web app.
