package android.os;

import java.io.File;

public class StatFs {
    private final File f;

    public StatFs(String path) {
        f = new File(path);
        if (!f.exists()) throw new IllegalArgumentException("no such path: " + path);
    }

    public int getBlockSize() {
        return 4096;
    }

    public int getAvailableBlocks() {
        return (int) Math.min(Integer.MAX_VALUE, f.getUsableSpace() / 4096);
    }
}
