package pl.q50.app.data;

import android.content.res.AssetManager;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Loads assets/sensor-map.json: sensor type -> signal definition. */
public final class SensorMap {
    private final SparseArray<SignalDef> byType = new SparseArray<SignalDef>();
    private final List<SignalDef> all = new ArrayList<SignalDef>();
    public final String error;

    private SensorMap(String error) {
        this.error = error;
    }

    public static SensorMap load(AssetManager assets) {
        try {
            SensorMap map = new SensorMap(null);
            map.parse(readAll(assets.open("sensor-map.json")));
            return map;
        } catch (IOException e) {
            return new SensorMap("sensor-map.json: " + e.getMessage());
        } catch (JSONException e) {
            return new SensorMap("sensor-map.json: " + e.getMessage());
        }
    }

    private void parse(String json) throws JSONException {
        JSONArray arr = new JSONObject(json).getJSONArray("signals");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            SignalDef d = new SignalDef(
                    o.getInt("type"),
                    o.getString("key"),
                    o.getString("label"),
                    o.optString("label_en", o.getString("label")),
                    o.optString("unit", ""),
                    o.optString("unit_en", o.optString("unit", "")),
                    toFloat(o.optDouble("scale", 1.0)),
                    toFloat(o.optDouble("offset", 0.0)),
                    o.optInt("decimals", 0),
                    o.optBoolean("verified", false),
                    o.has("format") ? o.getString("format") : null,
                    toFloat(o.optDouble("cold", Double.NaN)),
                    toFloat(o.optDouble("hot", Double.NaN)));
            byType.put(d.type, d);
            all.add(d);
        }
    }

    private static float toFloat(double d) {
        return (float) d;
    }

    public SignalDef forType(int type) {
        return byType.get(type);
    }

    public List<SignalDef> all() {
        return all;
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
