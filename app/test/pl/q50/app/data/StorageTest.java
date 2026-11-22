package pl.q50.app.data;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/** Storage classification and log size caps (plain JVM, with the android.os stand-ins). */
public final class StorageTest {
    private static int failures;

    public static void main(String[] args) throws Exception {
        // /proc/mounts as seen on InTouch-like systems: sdcard is tmpfs, the stick is vfat
        File mounts = File.createTempFile("mounts", ".txt");
        FileWriter w = new FileWriter(mounts);
        w.write("rootfs / rootfs ro 0 0\n");
        w.write("tmpfs /mnt/sdcard tmpfs rw,relatime 0 0\n");
        w.write("/dev/block/sda1 /mnt/usb vfat rw,dirsync 0 0\n");
        w.write("/dev/block/mmcblk0p2 /system ext3 ro 0 0\n");
        w.close();
        List<Storage.Mount> m = Storage.readMounts(mounts.getAbsolutePath());
        check(m.size() == 4, "4 mounts parsed");
        eq("tmpfs", Storage.fsTypeOf("/mnt/sdcard", m));
        eq("tmpfs", Storage.fsTypeOf("/mnt/sdcard/q50app", m));
        eq("vfat", Storage.fsTypeOf("/mnt/usb", m));
        eq("rootfs", Storage.fsTypeOf("/mnt/usbdisk", m)); // not a prefix match of /mnt/usb
        check(Storage.readMounts("/nonexistent/mounts").isEmpty(), "missing mounts file is harmless");
        mounts.delete();

        long mb = 1024L * 1024;
        Storage.Option ram = new Storage.Option(new File("/mnt/sdcard"), true, "tmpfs", 100 * mb);
        check(ram.ram && !ram.recommended(), "RAM is never recommended");
        check(ram.logCap() == Storage.RAM_LOG_CAP, "RAM log capped at 2 MB");
        Storage.Option tinyRam = new Storage.Option(new File("/mnt/sdcard"), true, "tmpfs", 4 * mb);
        check(tinyRam.logCap() == mb, "RAM log uses at most a quarter of free RAM");
        Storage.Option usb = new Storage.Option(new File("/mnt/usb"), true, "vfat", 8000 * mb);
        check(usb.flash && usb.recommended() && usb.logCap() == Storage.DISK_LOG_CAP, "USB with room");
        Storage.Option fullUsb = new Storage.Option(new File("/mnt/usb"), true, "vfat", 40 * mb);
        check(fullUsb.logCap() == 0 && !fullUsb.recommended(), "keeps 50 MB free on a full stick");
        Storage.Option readOnly = new Storage.Option(new File("/mnt/usb"), false, "vfat", 8000 * mb);
        check(!readOnly.recommended(), "read-only is not recommended");

        // logger stops at its cap
        File dir = new File(System.getProperty("java.io.tmpdir"), "q50-logtest-" + System.nanoTime());
        Channel c = new Channel(13, "VS_ID_ENGINE_RPM", "Ygomi", true, false, 0, 0);
        List<Channel> cols = new ArrayList<Channel>();
        cols.add(c);
        CsvLogger log = new CsvLogger();
        check(log.start(dir, cols, 200) == null, "logger starts");
        int rows = 0;
        for (int i = 0; i < 1000 && log.writeRow(i * 100L); i++) {
            c.onValues(new float[]{800 + i, 0, 0}, i * 100L);
            rows++;
        }
        check(rows > 3 && rows < 1000, "stopped by the 200 B cap after " + rows + " rows");
        log.stop();
        check(!log.isActive(), "stopped");

        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("StorageTest OK");
    }

    private static void eq(String a, String b) {
        check(a.equals(b), "expected " + a + " got " + b);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
