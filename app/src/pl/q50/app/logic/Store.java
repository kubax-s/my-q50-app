package pl.q50.app.logic;

/** Persistent key-value storage (SharedPreferences in the app, a map in tests). */
public interface Store {
    String get(String key);

    void put(String key, String value);
}
