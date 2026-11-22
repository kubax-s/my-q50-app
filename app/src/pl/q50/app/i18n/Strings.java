package pl.q50.app.i18n;

/**
 * All user-visible texts, Polish (default) and English side by side.
 * Pages read {@link #get()} on every draw, so switching language takes effect immediately.
 */
public final class Strings {
    public static final String PL = "pl";
    public static final String EN = "en";

    private static final Strings POLISH = new Strings(false);
    private static final Strings ENGLISH = new Strings(true);
    private static Strings current = POLISH;

    public static Strings get() {
        return current;
    }

    /** Unknown codes fall back to Polish. */
    public static void setLanguage(String code) {
        current = EN.equals(code) ? ENGLISH : POLISH;
    }

    public final boolean en;
    public final String code;

    // tabs
    public final String tabOverview;
    public final String tabSensors;
    public final String tabLog;
    public final String tabSettings;

    // overview
    public final String demoBanner;
    public final String noVehicleSensors;
    public final String noSensor;
    public final String noData;
    public final String tyres;
    public final String[] tyreShort; // FL, FR, RL, RR

    // sensors
    public final String[] filters; // vehicle, changing, all
    public final String filterPrefix;
    public final String resetMinMax;
    public final String[] tableHeads;
    public final String tapRowHint;
    public final String typePrefix;
    public final String range;
    public final String resolution;

    // log
    public final String start;
    public final String stop;
    public final String marker;
    public final String[] logHints;
    public final String state;
    public final String file;
    public final String size;
    public final String time;
    public final String rows;
    public final String sensors;
    public final String recording;
    public final String stopped;
    public final String errorPrefix;
    public final String storageTitle;
    public final String rescan;
    public final String writable;
    public final String readOnly;
    public final String noStorage;
    public final String chooseWritable;
    public final String stopBeforeChange;
    public final String cannotWriteHere;
    public final String startLoggingFirst;
    public final String cannotCreateDir;
    public final String writeError;

    // tabs added in 0.3/0.4
    public final String tabAlerts;
    public final String tabEngine;
    public final String tabTrip;
    public final String tabGMeter;

    // alerts page
    public final String alertOff;
    public final String alertDisabled;
    public final String historyTitle;
    public final String historyEmpty;
    public final String tapToSnooze;
    public final String enableInSettings;

    // g-meter
    public final String gLateral;
    public final String gLongitudinal;
    public final String gMaxLatPlus;
    public final String gMaxLatMinus;
    public final String gMaxAccel;
    public final String gMaxBrake;
    public final String gMaxCombined;
    public final String gAccelShort;
    public final String gBrakeShort;
    public final String gSignNote;
    public final String resetG;
    public final String gNoData;
    public final String perfTitle;

    // alerts
    public final String[] alertNames; // same order as AlertEngine.IDS
    public final String alertOk;

    // health
    public final String warmUpTitle;
    public final String stateCold;
    public final String stateWarming;
    public final String stateWarm;
    public final String coolantWarmAfter;
    public final String oilWarmAfter;
    public final String warmStart;
    public final String turboTitle;
    public final String turboCooling;
    public final String turboOk;
    public final String turboHint;
    public final String oilPressTitle;
    public final String normal;
    public final String tyresTitle;
    public final String noLeak;
    public final String vsAverage;

    // performance
    public final String[] runNames; // same order as PerfTimer runs
    public final String colLast;
    public final String colBest;
    public final String armed;
    public final String notArmed;
    public final String timing;
    public final String resetRecords;
    public final String dynoTitle;
    public final String dynoEmpty;
    public final String resetDyno;

    // trip
    public final String tripTime;
    public final String tripDriving;
    public final String tripIdle;
    public final String tripDistance;
    public final String tripAvgSpeed;
    public final String tripMaxSpeed;
    public final String tripMaxRpm;
    public final String tripMaxPower;
    public final String tripMaxTorque;
    public final String tripMaxGLat;
    public final String tripGLong;
    public final String tripMaxCoolant;
    public final String tripMaxOil;
    public final String tripSpirited;
    public final String rpmBandsTitle;
    public final String gearsTitle;
    public final String resetTrip;

    // settings
    public final String tilesTitle;
    public final String alertsTitle;
    public final String alertSound;
    public final String language;
    public final String version;
    public final String dataSource;
    public final String vehicleSensors;
    public final String demoMode;

    private Strings(boolean en) {
        this.en = en;
        code = en ? EN : PL;

        tabOverview = en ? "OVERVIEW" : "PRZEGLĄD";
        tabSensors = en ? "SENSORS" : "SENSORY";
        tabLog = "LOG";
        tabSettings = en ? "SETTINGS" : "USTAWIENIA";

        demoBanner = en ? "DEMO – no vehicle sensors found, data is synthetic"
                : "DEMO – nie wykryto sensorów pojazdu, dane są syntetyczne";
        noVehicleSensors = en ? "No vehicle sensors" : "Brak sensorów pojazdu";
        noSensor = en ? "no sensor" : "brak sensora";
        noData = en ? "no data" : "brak danych";
        tyres = en ? "TYRES" : "OPONY";
        tyreShort = en ? new String[]{"FL", "FR", "RL", "RR"} : new String[]{"LP", "PP", "LT", "PT"};

        filters = en ? new String[]{"VEHICLE", "CHANGING", "ALL"} : new String[]{"POJAZD", "ZMIENNE", "WSZYSTKIE"};
        filterPrefix = en ? "FILTER: " : "FILTR: ";
        resetMinMax = "RESET MIN/MAX";
        tableHeads = en ? new String[]{"TYPE", "NAME / SIGNAL", "v0", "v1", "v2", "Hz", "MIN … MAX"}
                : new String[]{"TYP", "NAZWA / SYGNAŁ", "v0", "v1", "v2", "Hz", "MIN … MAX"};
        tapRowHint = en ? "Tap a row to see a chart of the last 20 s (v0)"
                : "Dotknij wiersza, aby zobaczyć wykres ostatnich 20 s (v0)";
        typePrefix = en ? "TYPE " : "TYP ";
        range = en ? "range" : "zakres";
        resolution = en ? "res." : "rozdz.";

        start = "START LOG";
        stop = "STOP";
        marker = "MARKER";
        logHints = en
                ? new String[]{"Marker = a tag in the log (e.g. \"steering now\"),",
                "or long-press anywhere on the screen.",
                "Logging: 10 rows/s, all sensors, v0–v2."}
                : new String[]{"Marker = znacznik w logu (np. „teraz skręcam”),",
                "albo przytrzymaj palec na ekranie (dowolna zakładka).",
                "Zapis: 10 wierszy/s, wszystkie sensory, v0–v2."};
        state = en ? "State" : "Stan";
        file = en ? "File" : "Plik";
        size = en ? "Size" : "Rozmiar";
        time = en ? "Time" : "Czas";
        rows = en ? "Rows" : "Wiersze";
        sensors = en ? "Sensors" : "Sensory";
        recording = en ? "recording" : "zapis trwa";
        stopped = en ? "stopped" : "zatrzymany";
        errorPrefix = en ? "Error: " : "Błąd: ";
        storageTitle = en ? "STORAGE" : "MIEJSCE ZAPISU";
        rescan = en ? "SCAN" : "SKANUJ";
        writable = en ? "writable" : "zapis OK";
        readOnly = en ? "read-only" : "tylko odczyt";
        noStorage = en ? "No writable storage – insert a USB stick and press SCAN"
                : "Brak miejsc do zapisu – włóż pendrive i naciśnij SKANUJ";
        chooseWritable = en ? "Choose a storage marked \"writable\"" : "Wybierz miejsce zapisu z oznaczeniem „zapis OK”";
        stopBeforeChange = en ? "Stop logging before changing storage" : "Zatrzymaj zapis przed zmianą miejsca";
        cannotWriteHere = en ? "Cannot write here" : "Tu nie da się zapisać";
        startLoggingFirst = en ? "Start logging first" : "Najpierw włącz zapis";
        cannotCreateDir = en ? "Cannot create folder " : "Nie można utworzyć katalogu ";
        writeError = en ? "Write error: " : "Błąd zapisu: ";

        tabAlerts = en ? "ALERTS" : "ALERTY";
        tabEngine = en ? "ENGINE" : "SILNIK";
        tabTrip = en ? "TRIP" : "TRASA";
        tabGMeter = en ? "G-METER" : "G-METR";

        alertOff = "OK";
        alertDisabled = en ? "disabled" : "wyłączony";
        historyTitle = en ? "RECENT ALERTS" : "OSTATNIE ALERTY";
        historyEmpty = en ? "No alerts this session" : "Brak alertów w tej sesji";
        tapToSnooze = en ? "Tap an active alert to snooze it for 5 min (tap again to undo)"
                : "Dotknij aktywnego alertu, aby wyciszyć go na 5 min (ponownie – cofnij)";
        enableInSettings = en ? "This alert is disabled in settings" : "Ten alert jest wyłączony w ustawieniach";

        gLateral = en ? "Lateral" : "Boczne";
        gLongitudinal = en ? "Longitudinal" : "Wzdłużne";
        gMaxLatPlus = en ? "Max lateral +" : "Maks. boczne +";
        gMaxLatMinus = en ? "Max lateral −" : "Maks. boczne −";
        gMaxAccel = en ? "Max acceleration" : "Maks. przyspieszanie";
        gMaxBrake = en ? "Max braking" : "Maks. hamowanie";
        gMaxCombined = en ? "Max combined" : "Maks. łącznie";
        gAccelShort = en ? "ACCEL" : "PRZYSP.";
        gBrakeShort = en ? "BRAKE" : "HAM.";
        gSignNote = en ? "Axis directions to be confirmed in the car" : "Kierunki osi do potwierdzenia w aucie";
        resetG = "RESET G";
        gNoData = en ? "No G-force signals" : "Brak sygnałów przeciążeń";
        perfTitle = en ? "TIMING" : "POMIARY";

        alertNames = en
                ? new String[]{"Coolant overheating", "Oil overheating", "Low oil pressure", "Oil pressure below normal",
                "Cold oil under load", "Low tyre pressure", "One tyre lower", "Slow tyre leak", "Turbo cool-down"}
                : new String[]{"Przegrzanie płynu", "Przegrzanie oleju", "Niskie ciśnienie oleju",
                "Ciśnienie oleju poniżej normy", "Zimny olej pod obciążeniem", "Niskie ciśnienie w oponie",
                "Jedna opona niższa", "Wolny wyciek z opony", "Schłodzenie turbin"};
        alertOk = "OK";

        warmUpTitle = en ? "WARM-UP" : "ROZGRZANIE";
        stateCold = en ? "COLD – keep revs low" : "ZIMNY – bez wysokich obrotów";
        stateWarming = en ? "WARMING UP" : "ROZGRZEWANIE";
        stateWarm = en ? "WARM" : "ROZGRZANY";
        coolantWarmAfter = en ? "Coolant 80 °C after" : "Płyn 80 °C po";
        oilWarmAfter = en ? "Oil 80 °C after" : "Olej 80 °C po";
        warmStart = en ? "Warm start – no warm-up timing" : "Start na ciepłym silniku – bez pomiaru";
        turboTitle = "TURBO";
        turboCooling = en ? "Idle to cool down" : "Schłodź na wolnych obrotach";
        turboOk = en ? "Turbos cooled down" : "Turbiny schłodzone";
        turboHint = en ? "After hard driving, idle before switching off" : "Po ostrej jeździe odczekaj przed zgaszeniem";
        oilPressTitle = en ? "OIL PRESSURE" : "CIŚNIENIE OLEJU";
        normal = en ? "normal" : "norma";
        tyresTitle = en ? "TYRES" : "OPONY";
        noLeak = en ? "No sign of a slow leak" : "Brak oznak wolnego wycieku";
        vsAverage = en ? "vs. average of 4" : "względem średniej z 4";

        runNames = en
                ? new String[]{"0–100 km/h", "1/4 mile", "60–100 km/h", "80–120 km/h", "100–200 km/h", "100–0 km/h"}
                : new String[]{"0–100 km/h", "1/4 mili", "60–100 km/h", "80–120 km/h", "100–200 km/h", "100–0 km/h"};
        colLast = en ? "LAST" : "OSTATNI";
        colBest = en ? "BEST" : "REKORD";
        armed = en ? "Ready – launch!" : "Gotowy – ruszaj!";
        notArmed = en ? "Stop the car to time a launch" : "Zatrzymaj auto, aby zmierzyć start";
        timing = en ? "timing…" : "pomiar…";
        resetRecords = en ? "RESET RECORDS" : "RESET REKORDÓW";
        dynoTitle = en ? "DYNO (full throttle)" : "DYNO (pełny gaz)";
        dynoEmpty = en ? "Floor it (≥ 90% throttle) to draw the curve" : "Gaz do dechy (≥ 90%) rysuje krzywą";
        resetDyno = "RESET DYNO";

        tripTime = en ? "Time" : "Czas";
        tripDriving = en ? "Driving" : "Jazda";
        tripIdle = en ? "Idling" : "Postój";
        tripDistance = en ? "Distance" : "Dystans";
        tripAvgSpeed = en ? "Avg. speed" : "Śr. prędkość";
        tripMaxSpeed = en ? "Max speed" : "Maks. prędkość";
        tripMaxRpm = en ? "Max RPM" : "Maks. obroty";
        tripMaxPower = en ? "Max power" : "Maks. moc";
        tripMaxTorque = en ? "Max torque" : "Maks. moment";
        tripMaxGLat = en ? "Max lateral G" : "Maks. G bok";
        tripGLong = en ? "Long. G + / −" : "G wzdł. + / −";
        tripMaxCoolant = en ? "Max coolant" : "Maks. płyn";
        tripMaxOil = en ? "Max oil" : "Maks. olej";
        tripSpirited = en ? "Spirited driving" : "Dynamiczna jazda";
        rpmBandsTitle = en ? "TIME IN RPM BANDS" : "CZAS W ZAKRESACH OBROTÓW";
        gearsTitle = en ? "TIME IN GEARS" : "CZAS NA BIEGACH";
        resetTrip = en ? "RESET TRIP" : "RESET PRZEJAZDU";

        tilesTitle = en ? "SHOWN ON OVERVIEW" : "WIDOCZNE NA PRZEGLĄDZIE";
        alertsTitle = en ? "ALERTS" : "ALERTY";
        alertSound = en ? "Alert sound" : "Dźwięk alertów";

        language = en ? "LANGUAGE" : "JĘZYK";
        version = en ? "Version" : "Wersja";
        dataSource = en ? "Data source" : "Źródło danych";
        vehicleSensors = en ? "vehicle sensors" : "sensory pojazdu";
        demoMode = en ? "DEMO mode (no vehicle sensors)" : "tryb DEMO (brak sensorów pojazdu)";
    }

    public String sensorsSummary(int all, int varying, int listed) {
        if (en) {
            return all + " sensors · " + varying + " changing · " + listed + " listed";
        }
        return all + " " + plural(all, "sensor", "sensory", "sensorów") + " · " + varying + " "
                + plural(varying, "zmienny", "zmienne", "zmiennych") + " · " + listed + " na liście";
    }

    public String savedRows(int n) {
        return en ? "Saved " + n + (n == 1 ? " row" : " rows") : "Zapisano " + n + " " + plural(n, "wiersz", "wiersze", "wierszy");
    }

    public String loggingTo(String fileName) {
        return (en ? "Logging to " : "Zapis do ") + fileName;
    }

    public String markerNo(int n) {
        return "Marker #" + n;
    }

    public String alertCoolantHot(String temp) {
        return (en ? "Coolant " : "Płyn chłodzący ") + temp + " °C";
    }

    public String alertOilHot(String temp) {
        return (en ? "Oil " : "Olej ") + temp + " °C";
    }

    public String alertOilPressLow(String bar) {
        return (en ? "Low oil pressure: " : "Niskie ciśnienie oleju: ") + bar + " bar";
    }

    public String alertOilPressBaseline(String bar, String expected) {
        return en ? "Oil pressure " + bar + " bar, normally " + expected + " bar"
                : "Ciśnienie oleju " + bar + " bar, norma " + expected + " bar";
    }

    public String alertColdOil(String temp) {
        return en ? "Cold oil (" + temp + " °C) – keep revs low" : "Zimny olej (" + temp + " °C) – bez wysokich obrotów";
    }

    public String alertTyreLow(String tyre, String bar) {
        return (en ? "Low pressure: tyre " : "Niskie ciśnienie: opona ") + tyre + " " + bar + " bar";
    }

    public String alertTyreAsym(String tyre, String diff) {
        return en ? "Tyre " + tyre + ": " + diff + " bar below the others"
                : "Opona " + tyre + ": " + diff + " bar mniej niż pozostałe";
    }

    public String alertTyreLeak(String tyre, String drop, int days) {
        return en ? "Possible slow leak: " + tyre + " −" + drop + " bar in " + days + (days == 1 ? " day" : " days")
                : "Możliwy wolny wyciek: " + tyre + " −" + drop + " bar w " + days + " " + plural(days, "dzień", "dni", "dni");
    }

    public String alertTurbo(int seconds) {
        return en ? "Turbo cool-down: idle for " + seconds + " s more"
                : "Schłodzenie turbin: jeszcze " + seconds + " s na wolnych obrotach";
    }

    public String turboSeconds(int seconds) {
        return en ? seconds + " s left" : "jeszcze " + seconds + " s";
    }

    public String oilLearning(int learned, int total) {
        return en ? "Learning baseline: " + learned + "/" + total + " bands (warm oil)"
                : "Nauka normy: " + learned + "/" + total + " zakresów (ciepły olej)";
    }

    public String tyreSessions(int n) {
        return en ? "Session readings: " + n : "Pomiary z sesji: " + n;
    }

    public String newResult(String run, String result) {
        return (en ? "New result: " : "Nowy wynik: ") + run + "  " + result;
    }

    public String snoozed(String remaining) {
        return (en ? "snoozed " : "wyciszony ") + remaining;
    }

    public String ago(long ms) {
        long min = ms / 60000;
        if (min < 1) return en ? "just now" : "przed chwilą";
        if (min < 60) return en ? min + " min ago" : min + " min temu";
        long h = min / 60;
        return en ? h + " h ago" : h + " godz. temu";
    }

    public String recTab(String duration) {
        return "REC " + duration;
    }

    /** Polish plural: 1 sensor, 2–4 sensory (except 12–14), otherwise sensorów. */
    static String plural(int n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return few;
        }
        return many;
    }
}
