package android.view;

import android.content.Context;
import android.graphics.Canvas;

public class View {
    private int width;
    private int height;

    public View(Context ctx) {
    }

    /** Preview only: there is no layout pass on the desktop. */
    public void setPreviewSize(int w, int h) {
        width = w;
        height = h;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setKeepScreenOn(boolean on) {
    }

    public void setBackgroundColor(int color) {
    }

    public void invalidate() {
    }

    protected void onDraw(Canvas c) {
    }

    public boolean onTouchEvent(MotionEvent e) {
        return false;
    }
}
