package android.os;

public class HandlerThread {
    public HandlerThread(String name) {
    }

    public void start() {
    }

    public Looper getLooper() {
        return new Looper();
    }

    public boolean quit() {
        return true;
    }
}
