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

    // settings
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
                "also available under the REC dot on the tab bar.",
                "Logging: 10 rows/s, all sensors, v0–v2."}
                : new String[]{"Marker = znacznik w logu (np. „teraz skręcam”),",
                "dostępny też pod kropką REC na pasku zakładek.",
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
