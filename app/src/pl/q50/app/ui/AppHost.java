package pl.q50.app.ui;

import pl.q50.app.data.Storage;

/** Actions the pages ask the activity to perform. */
public interface AppHost {
    void toggleLogging(Storage.Option target);

    void marker();

    void message(String text);

    String savedStoragePath();

    void saveStoragePath(String path);

    /** Switches the UI language ("pl" / "en") and remembers it. */
    void setLanguage(String code);

    /** e.g. "0.2.0 (1795360000)". */
    String versionText();

    /** Number of real vehicle sensors found. */
    int vehicleSensorCount();

    /** Settings were changed by the user: persist them. */
    void settingsChanged();

    /** Records/baselines were changed (e.g. reset): persist them. */
    void analyticsChanged();
}
