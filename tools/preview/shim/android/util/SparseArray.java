package android.util;

import java.util.HashMap;
import java.util.Map;

public class SparseArray<E> {
    private final Map<Integer, E> map = new HashMap<Integer, E>();

    public void put(int key, E value) {
        map.put(key, value);
    }

    public E get(int key) {
        return map.get(key);
    }
}
