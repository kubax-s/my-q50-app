package android.os;

/** No message loop in previews: posted work never runs. */
public class Handler {
    public Handler() {
    }

    public Handler(Looper looper) {
    }

    public boolean post(Runnable r) {
        return false;
    }

    public boolean postDelayed(Runnable r, long delayMs) {
        return false;
    }

    public void removeCallbacks(Runnable r) {
    }
}
