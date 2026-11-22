package pl.q50.app.data;

import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds places where logs can be written.
 *
 * On InTouch, {@code /mnt/sdcard} (what Android calls external storage) is a tmpfs, i.e. RAM,
 * and the USB stick is mounted somewhere else (community finding from an app run on the unit).
 * So mount points are classified with /proc/mounts: real flash file systems (vfat, exfat, …)
 * are preferred, RAM file systems are only offered with a small size cap, and every candidate
 * is probed with an actual write.
 */
public final class Storage {
    public static final String DIR_NAME = "q50app";
    /** Cap for a log in RAM (tmpfs): it uses the head unit's memory. */
    public static final long RAM_LOG_CAP = 2L * 1024 * 1024;
    /** Cap for one log file on a real disk. FAT32 allows 4 GB; this keeps files easy to handle. */
    public static final long DISK_LOG_CAP = 200L * 1024 * 1024;
    /** Free space always left on a disk. */
    public static final long DISK_RESERVE = 50L * 1024 * 1024;

    private static final String[] FLASH_FS = {"vfat", "exfat", "texfat", "fuseblk", "ntfs", "msdos", "sdfat"};
    private static final String[] RAM_FS = {"tmpfs", "ramfs"};
    private static final String[] PARENTS = {"/mnt", "/storage", "/media", "/udisk", "/usb"};
    private static final String[] SKIP = {"asec", "obb", "secure", "shell", "self", "runtime", "emulated", "media_rw"};

    public static final class Option {
        public final File root;
        public final boolean writable;
        /** File system type from /proc/mounts, "" if unknown. */
        public final String fsType;
        public final boolean ram;
        public final boolean flash;
        /** Free bytes, -1 if unknown. */
        public final long freeBytes;
        /** Shown in the UI; computed once instead of on every frame. */
        public final String displayPath;

        Option(File root, boolean writable, String fsType, long freeBytes) {
            this.root = root;
            this.writable = writable;
            this.fsType = fsType == null ? "" : fsType;
            this.ram = oneOf(this.fsType, RAM_FS);
            this.flash = oneOf(this.fsType, FLASH_FS);
            this.freeBytes = freeBytes;
            this.displayPath = new File(root, DIR_NAME).getAbsolutePath();
        }

        public File logDir() {
            return new File(root, DIR_NAME);
        }

        /** Largest log this place should take (0 = not enough room). */
        public long logCap() {
            if (ram) {
                return freeBytes < 0 ? RAM_LOG_CAP : Math.max(0, Math.min(RAM_LOG_CAP, freeBytes / 4));
            }
            if (freeBytes < 0) {
                return DISK_LOG_CAP;
            }
            return Math.max(0, Math.min(DISK_LOG_CAP, freeBytes - DISK_RESERVE));
        }

        /** Good for logging without asking: writable, not RAM, room for at least 1 MB. */
        public boolean recommended() {
            return writable && !ram && logCap() >= 1024 * 1024;
        }
    }

    /** One line of /proc/mounts. */
    static final class Mount {
        final String path;
        final String fs;

        Mount(String path, String fs) {
            this.path = path;
            this.fs = fs;
        }
    }

    public static List<Option> scan() {
        List<Mount> mounts = readMounts("/proc/mounts");
        List<File> roots = new ArrayList<File>();
        // 1. removable-looking mounts with a flash file system
        for (int i = 0; i < mounts.size(); i++) {
            Mount m = mounts.get(i);
            if (oneOf(m.fs, FLASH_FS) && !systemPath(m.path)) {
                addUnique(roots, new File(m.path));
            }
        }
        // 2. Android's "external storage" (on InTouch: RAM)
        addUnique(roots, Environment.getExternalStorageDirectory());
        // 3. anything else that looks like a mount point
        for (int p = 0; p < PARENTS.length; p++) {
            File[] children = new File(PARENTS[p]).listFiles();
            if (children == null) {
                continue;
            }
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirectory() && !oneOf(children[i].getName(), SKIP)) {
                    addUnique(roots, children[i]);
                }
            }
        }
        List<Option> out = new ArrayList<Option>();
        for (int i = 0; i < roots.size(); i++) {
            File r = roots.get(i);
            out.add(new Option(r, probe(r), fsTypeOf(r.getAbsolutePath(), mounts), freeBytes(r)));
        }
        // recommended places first, RAM last
        List<Option> sorted = new ArrayList<Option>();
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < out.size(); i++) {
                Option o = out.get(i);
                int rank = o.recommended() ? 0 : o.ram ? 2 : 1;
                if (rank == pass) sorted.add(o);
            }
        }
        return sorted;
    }

    static List<Mount> readMounts(String file) {
        List<Mount> list = new ArrayList<Mount>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(file), 4096);
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length >= 3) {
                    list.add(new Mount(f[1], f[2]));
                }
            }
        } catch (IOException e) {
            // not readable: classification falls back to "unknown"
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
        return list;
    }

    /** File system of the longest mount point that contains {@code path}. */
    static String fsTypeOf(String path, List<Mount> mounts) {
        String best = "";
        int bestLen = -1;
        for (int i = 0; i < mounts.size(); i++) {
            Mount m = mounts.get(i);
            boolean contains = path.equals(m.path) || m.path.equals("/")
                    || path.startsWith(m.path.endsWith("/") ? m.path : m.path + "/");
            if (contains && m.path.length() > bestLen) {
                best = m.fs;
                bestLen = m.path.length();
            }
        }
        return best;
    }

    private static boolean systemPath(String p) {
        return p.equals("/") || p.startsWith("/system") || p.startsWith("/data") || p.startsWith("/cache")
                || p.startsWith("/proc") || p.startsWith("/dev") || p.startsWith("/sys") || p.startsWith("/vendor")
                || p.startsWith("/acct") || p.startsWith("/config");
    }

    static boolean oneOf(String s, String[] list) {
        for (int i = 0; i < list.length; i++) {
            if (list[i].equalsIgnoreCase(s)) {
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

    public static long freeBytes(File dir) {
        try {
            StatFs st = new StatFs(dir.getAbsolutePath());
            return (long) st.getAvailableBlocks() * (long) st.getBlockSize();
        } catch (RuntimeException e) {
            return -1;
        }
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
