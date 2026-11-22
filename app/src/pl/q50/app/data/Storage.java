package pl.q50.app.data;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds places where logs can be written. Where the head unit mounts a USB stick for Android
 * apps is not known yet, so every plausible mount point is probed and shown to the user.
 */
public final class Storage {
    public static final String DIR_NAME = "q50app";

    public static final class Option {
        public final File root;
        public final boolean writable;
        /** Shown in the UI; computed once instead of on every frame. */
        public final String displayPath;

        Option(File root, boolean writable) {
            this.root = root;
            this.writable = writable;
            this.displayPath = new File(root, DIR_NAME).getAbsolutePath();
        }

        public File logDir() {
            return new File(root, DIR_NAME);
        }
    }

    private static final String[] SKIP = {"asec", "obb", "secure", "shell", "self", "runtime"};

    public static List<Option> scan() {
        List<File> roots = new ArrayList<File>();
        addUnique(roots, Environment.getExternalStorageDirectory());
        String[] parents = {"/mnt", "/storage", "/media", "/udisk", "/usb"};
        for (int p = 0; p < parents.length; p++) {
            File parent = new File(parents[p]);
            File[] children = parent.listFiles();
            if (children == null) {
                continue;
            }
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirectory() && !skipped(children[i].getName())) {
                    addUnique(roots, children[i]);
                }
            }
        }
        List<Option> out = new ArrayList<Option>();
        for (int i = 0; i < roots.size(); i++) {
            out.add(new Option(roots.get(i), probe(roots.get(i))));
        }
        return out;
    }

    private static boolean skipped(String name) {
        for (int i = 0; i < SKIP.length; i++) {
            if (SKIP[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void addUnique(List<File> list, File f) {
        if (f == null) {
            return;
        }
        String path = f.getAbsolutePath();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAbsolutePath().equals(path)) {
                return;
            }
        }
        list.add(f);
    }

    /** Actually tries to create a file: canWrite() alone is unreliable on FUSE/vfat mounts. */
    private static boolean probe(File root) {
        File dir = new File(root, DIR_NAME);
        boolean existed = dir.isDirectory();
        if (!existed && !dir.mkdirs()) {
            return false;
        }
        File test = new File(dir, ".probe");
        try {
            FileOutputStream out = new FileOutputStream(test);
            out.write(1);
            out.close();
            test.delete();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (!existed) {
                dir.delete(); // leave no empty folders behind; CsvLogger creates it when needed
            }
        }
    }
}
