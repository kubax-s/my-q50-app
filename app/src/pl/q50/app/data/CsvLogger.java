package pl.q50.app.data;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pl.q50.app.i18n.Strings;

/**
 * Writes a snapshot of every channel at the sampling tick (10 Hz) to CSV:
 *   q50_YYYYMMDD_HHMMSS.csv          t_ms, marker, s<type>_0, s<type>_1, s<type>_2, ...
 *   q50_YYYYMMDD_HHMMSS_sensors.txt  sensor list (type, name, vendor, range, mapping)
 * Rows are built on the UI thread and written on a background thread.
 *
 * To keep files small (and RAM use low when the target is a tmpfs), a value is only written
 * when it differs from the previous row: an empty cell means "unchanged". The first row is
 * complete. Logging stops by itself when the size cap of the target is reached.
 */
public final class CsvLogger {
    private HandlerThread thread;
    private Handler io;
    private Writer writer;
    private List<Channel> columns;
    private File file;
    private long startMs;
    private int rows;
    private int markers;
    private int pendingMarker;
    private volatile String error;
    /** Bytes handed to the writer; avoids asking the file system for the size on every frame. */
    private volatile long bytes;
    /** Bytes queued for writing (UI thread); used for the size cap. */
    private long queued;
    private long maxBytes;
    private float[] last;

    public boolean isActive() {
        return writer != null;
    }

    public File file() {
        return file;
    }

    public long elapsedMs() {
        return isActive() ? SystemClock.uptimeMillis() - startMs : 0;
    }

    public int rows() {
        return rows;
    }

    public int markers() {
        return markers;
    }

    public long bytes() {
        return bytes;
    }

    public int columnCount() {
        return columns == null ? 0 : columns.size();
    }

    public String error() {
        return error;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** @return null on success, otherwise an error message. */
    public String start(File dir, List<Channel> channels, long capBytes) {
        if (isActive()) {
            return null;
        }
        error = null;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return Strings.get().cannotCreateDir + dir;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File csv = new File(dir, "q50_" + stamp + ".csv");
        File meta = new File(dir, "q50_" + stamp + "_sensors.txt");
        columns = new ArrayList<Channel>(channels);
        try {
            writeMeta(meta);
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csv), "UTF-8"), 64 * 1024);
            StringBuilder h = new StringBuilder("t_ms,marker");
            for (int i = 0; i < columns.size(); i++) {
                int t = columns.get(i).type;
                h.append(",s").append(t).append("_0,s").append(t).append("_1,s").append(t).append("_2");
            }
            writer.write(h.append('\n').toString());
        } catch (IOException e) {
            closeQuietly();
            return Strings.get().writeError + e.getMessage();
        }
        file = csv;
        startMs = SystemClock.uptimeMillis();
        rows = 0;
        bytes = 0;
        queued = 0;
        maxBytes = capBytes;
        last = new float[columns.size() * 3];
        for (int i = 0; i < last.length; i++) last[i] = Float.NaN;
        markers = 0;
        pendingMarker = 0;
        thread = new HandlerThread("csv-logger");
        thread.start();
        io = new Handler(thread.getLooper());
        return null;
    }

    public void marker() {
        if (isActive()) {
            markers++;
            pendingMarker = markers;
        }
    }

    /**
     * Called from the UI thread at the sampling tick.
     * @return false when the size cap is reached (the caller should stop logging).
     */
    public boolean writeRow(long nowMs) {
        if (!isActive()) {
            return true;
        }
        if (maxBytes > 0 && queued >= maxBytes) {
            return false;
        }
        StringBuilder sb = new StringBuilder(32 + columns.size() * 24);
        sb.append(nowMs - startMs).append(',');
        if (pendingMarker > 0) {
            sb.append(pendingMarker);
            pendingMarker = 0;
        }
        for (int i = 0; i < columns.size(); i++) {
            Channel c = columns.get(i);
            for (int k = 0; k < 3; k++) {
                sb.append(',');
                if (c.hasValue() && k < c.valueCount) {
                    float v = c.values[k];
                    int idx = i * 3 + k;
                    if (Float.floatToIntBits(v) != Float.floatToIntBits(last[idx])) {
                        sb.append(v);
                        last[idx] = v;
                    }
                }
            }
        }
        final String line = sb.append('\n').toString();
        queued += line.length();
        final boolean flush = (++rows % 10) == 0;
        final Writer w = writer;
        io.post(new Runnable() {
            public void run() {
                try {
                    w.write(line);
                    bytes += line.length();
                    if (flush) {
                        w.flush();
                    }
                } catch (IOException e) {
                    error = e.getMessage();
                }
            }
        });
        return true;
    }

    public void stop() {
        if (!isActive()) {
            return;
        }
        final Writer w = writer;
        final HandlerThread t = thread;
        writer = null;
        // Close on the writer thread after the queued rows, then end that thread. (Quitting
        // the looper first could drop the pending rows and the close.)
        io.post(new Runnable() {
            public void run() {
                try {
                    w.close();
                } catch (IOException e) {
                    error = e.getMessage();
                }
                t.quit();
            }
        });
        thread = null;
        io = null;
    }

    private void writeMeta(File meta) throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(meta), "UTF-8");
        try {
            w.write("type\tname\tvendor\tvehicle\tmax_range\tresolution\tmapped_key\tmapped_label\n");
            for (int i = 0; i < columns.size(); i++) {
                Channel c = columns.get(i);
                w.write(c.type + "\t" + c.name + "\t" + c.vendor + "\t" + c.vehicle + "\t" + c.maxRange + "\t"
                        + c.resolution + "\t" + (c.def == null ? "" : c.def.key) + "\t"
                        + (c.def == null ? "" : c.def.labelEn) + "\n");
            }
        } finally {
            w.close();
        }
    }

    private void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
            writer = null;
        }
    }
}
