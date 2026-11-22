package android.content.res;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads assets from app/assets on disk. */
public class AssetManager {
    private final String root;

    public AssetManager(String root) {
        this.root = root;
    }

    public InputStream open(String name) throws IOException {
        return new FileInputStream(root + "/" + name);
    }
}
