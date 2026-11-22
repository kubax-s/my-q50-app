package android.graphics;

public class RectF {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {
    }

    public RectF(float l, float t, float r, float b) {
        set(l, t, r, b);
    }

    public void set(float l, float t, float r, float b) {
        left = l;
        top = t;
        right = r;
        bottom = b;
    }

    public void setEmpty() {
        left = top = right = bottom = 0;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public boolean contains(float x, float y) {
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }
}
