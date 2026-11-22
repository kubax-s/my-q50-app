package pl.q50.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All channels the app knows about. Accessed from the UI thread only. */
public final class DataHub {
    public final SensorMap map;
    private final List<Channel> channels = new ArrayList<Channel>();
    private final Map<String, Channel> byKey = new HashMap<String, Channel>();

    private static final Comparator<Channel> ORDER = new Comparator<Channel>() {
        public int compare(Channel a, Channel b) {
            if (a.vehicle != b.vehicle) {
                return a.vehicle ? -1 : 1;
            }
            if (a.type != b.type) {
                return a.type < b.type ? -1 : 1;
            }
            return a.name.compareTo(b.name);
        }
    };

    public DataHub(SensorMap map) {
        this.map = map;
    }

    public Channel add(Channel c) {
        if (c.vehicle) {
            c.def = map.forType(c.type);
            // First vehicle sensor of a mapped type wins; duplicates stay visible in the explorer.
            if (c.def != null && !byKey.containsKey(c.def.key)) {
                byKey.put(c.def.key, c);
            }
        }
        channels.add(c);
        Collections.sort(channels, ORDER);
        return c;
    }

    public void removeDemo() {
        for (int i = channels.size() - 1; i >= 0; i--) {
            Channel c = channels.get(i);
            if (c.demo) {
                channels.remove(i);
                if (c.def != null && byKey.get(c.def.key) == c) {
                    byKey.remove(c.def.key);
                }
            }
        }
    }

    public List<Channel> channels() {
        return channels;
    }

    /** Channel for a mapped signal key (e.g. "rpm"), or null. */
    public Channel signal(String key) {
        return byKey.get(key);
    }

    public int vehicleCount() {
        int n = 0;
        for (int i = 0; i < channels.size(); i++) {
            Channel c = channels.get(i);
            if (c.vehicle && !c.demo) {
                n++;
            }
        }
        return n;
    }

    public boolean demoActive() {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).demo) {
                return true;
            }
        }
        return false;
    }

    /** Changes whenever any channel receives a value; used to skip redraws when nothing moved. */
    public int changeSignature() {
        int sig = 0;
        for (int i = 0; i < channels.size(); i++) {
            sig = sig * 31 + channels.get(i).eventCount;
        }
        return sig;
    }

    public void sample(long nowMs) {
        for (int i = 0; i < channels.size(); i++) {
            channels.get(i).sample(nowMs);
        }
    }

    public void resetMinMax() {
        for (int i = 0; i < channels.size(); i++) {
            channels.get(i).resetMinMax();
        }
    }
}
