package android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** java.awt-backed subset of android.graphics.Canvas for desktop previews. */
public class Canvas {
    private final Graphics2D g;
    private final int w;
    private final int h;

    public Canvas(BufferedImage img) {
        g = img.createGraphics();
        w = img.getWidth();
        h = img.getHeight();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void paint(Shape s, Paint p) {
        g.setColor(new Color(p.color, true));
        if (p.style == Paint.Style.STROKE) {
            g.setStroke(new BasicStroke(Math.max(1f, p.strokeWidth)));
            g.draw(s);
        } else {
            g.fill(s);
            if (p.style == Paint.Style.FILL_AND_STROKE) {
                g.setStroke(new BasicStroke(Math.max(1f, p.strokeWidth)));
                g.draw(s);
            }
        }
    }

    public void drawColor(int color) {
        g.setColor(new Color(color, true));
        g.fillRect(0, 0, w, h);
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        paint(new Rectangle2D.Float(l, t, r - l, b - t), p);
    }

    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        paint(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p);
    }

    public void drawCircle(float cx, float cy, float radius, Paint p) {
        paint(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), p);
    }

    public void drawLine(float x1, float y1, float x2, float y2, Paint p) {
        g.setColor(new Color(p.color, true));
        g.setStroke(new BasicStroke(Math.max(1f, p.strokeWidth), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    public void drawPath(Path path, Paint p) {
        paint(path.gp, p);
    }

    public void drawText(String text, float x, float y, Paint p) {
        g.setFont(p.font());
        g.setColor(new Color(p.color, true));
        float width = p.measureText(text);
        float dx = p.align == Paint.Align.CENTER ? -width / 2 : p.align == Paint.Align.RIGHT ? -width : 0;
        g.drawString(text, x + dx, y);
    }
}
