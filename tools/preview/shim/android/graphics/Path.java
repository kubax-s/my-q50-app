package android.graphics;

import java.awt.geom.GeneralPath;

public class Path {
    final GeneralPath gp = new GeneralPath();

    public void reset() {
        gp.reset();
    }

    public void moveTo(float x, float y) {
        gp.moveTo(x, y);
    }

    public void lineTo(float x, float y) {
        gp.lineTo(x, y);
    }

    public void close() {
        gp.closePath();
    }
}
