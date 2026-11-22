package pl.q50.app.ui;

import pl.q50.app.data.Storage;

/** Actions the log page asks the activity to perform. */
public interface LogPageHost {
    void toggleLogging(Storage.Option target);

    void marker();

    void message(String text);

    String savedStoragePath();

    void saveStoragePath(String path);
}
