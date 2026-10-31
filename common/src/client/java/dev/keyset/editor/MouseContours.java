package dev.keyset.editor;

import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.editor.MouseDiagramPainter.Canvas;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Vector silhouettes for the mouse diagram, rasterized into reusable scanline spans. */
public final class MouseContours {
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final int COLOR_BANDS_PER_ROW = 32;

    private static final Map<Shape, List<Point>> PATHS = paths();
    private static final Map<CacheKey, Span[][]> SPANS = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Span[][]> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private MouseContours() {}

    public enum Shape {
        BODY, LEFT, RIGHT, SIDE_BODY, SIDE_FRONT, SIDE_REAR, WHEEL
    }

    @FunctionalInterface
    public interface ColorFunction {
        int color(double u, double v);
    }

    /** Fills a solid contour. All emitted rectangles have positive size and stay within bounds. */
    public static void fill(Canvas canvas, Shape shape, Rect bounds, int color) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(bounds, "bounds");
        if (!hasRasterBounds(bounds)) return;

        Span[][] rows = spans(shape, bounds.width(), bounds.height());
        for (int row = 0; row < rows.length; row++) {
            int top = bounds.y() + row;
            int bottom = top + 1;
            for (Span span : rows[row]) {
                if (span.left() < span.right()) {
                    canvas.fill(bounds.x() + span.left(), top, bounds.x() + span.right(), bottom, color);
                }
            }
        }
    }

    /**
     * Samples a smooth gradient, merging adjacent equal-color spans before emission.
     * The callback coordinates are normalized within the contour; RIGHT mirrors the LEFT contour
     * and its color samples.
     */
    public static void fill(Canvas canvas, Shape shape, Rect bounds, ColorFunction colors) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(colors, "colors");
        if (!hasRasterBounds(bounds)) return;

        Span[][] rows = shape == Shape.RIGHT
            ? spans(Shape.LEFT, bounds.width(), bounds.height())
            : spans(shape, bounds.width(), bounds.height());
        for (int row = 0; row < rows.length; row++) {
            double v = (row + 0.5) / bounds.height();
            int top = bounds.y() + row;
            int bottom = top + 1;
            for (Span span : rows[row]) {
                int width = span.right() - span.left();
                int bands = Math.min(COLOR_BANDS_PER_ROW, width);
                int pendingLeft = span.left(), pendingRight = span.left(), pendingColor = 0;
                boolean pending = false;
                for (int band = 0; band < bands; band++) {
                    int left = span.left() + width * band / bands;
                    int right = span.left() + width * (band + 1) / bands;
                    if (right <= left) continue;
                    double u = ((left + right) / 2.0) / bounds.width();
                    int color = colors.color(u, v);
                    if (pending && pendingColor == color && pendingRight == left) {
                        pendingRight = right;
                    } else {
                        if (pending) coloredSpan(canvas, shape, bounds, pendingLeft, pendingRight, top, pendingColor);
                        pendingLeft = left;
                        pendingRight = right;
                        pendingColor = color;
                        pending = true;
                    }
                }
                if (pending) coloredSpan(canvas, shape, bounds, pendingLeft, pendingRight, top, pendingColor);
            }
        }
    }

    private static void coloredSpan(Canvas canvas, Shape shape, Rect bounds, int left, int right, int top, int color) {
        if (shape == Shape.RIGHT) {
            canvas.fill(bounds.x() + bounds.width() - right, top,
                bounds.x() + bounds.width() - left, top + 1, color);
        } else {
            canvas.fill(bounds.x() + left, top, bounds.x() + right, top + 1, color);
        }
    }

    /**
     * Tests a GUI-space point against the exact raster coverage emitted by {@link #fill}.
     * Coordinates use the usual half-open pixel convention: left/top inclusive, right/bottom exclusive.
     */
    public static boolean contains(Shape shape, Rect bounds, double x, double y) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(bounds, "bounds");
        if (!hasRasterBounds(bounds)
                || !Double.isFinite(x) || !Double.isFinite(y)) return false;

        double localX = x - bounds.x();
        double localY = y - bounds.y();
        if (localX < 0 || localY < 0 || localX >= bounds.width() || localY >= bounds.height()) return false;
        int column = (int) Math.floor(localX);
        int row = (int) Math.floor(localY);
        for (Span span : spans(shape, bounds.width(), bounds.height())[row]) {
            if (column >= span.left() && column < span.right()) return true;
        }
        return false;
    }

    private static Span[][] spans(Shape shape, int width, int height) {
        CacheKey key = new CacheKey(shape, width, height);
        synchronized (SPANS) {
            Span[][] cached = SPANS.get(key);
            if (cached != null) return cached;

            Span[][] result;
            if (shape == Shape.RIGHT) {
                result = mirror(spans(Shape.LEFT, width, height), width);
            } else {
                result = rasterize(PATHS.get(shape), width, height);
            }
            SPANS.put(key, result);
            return result;
        }
    }

    private static Span[][] mirror(Span[][] source, int width) {
        Span[][] result = new Span[source.length][];
        for (int row = 0; row < source.length; row++) {
            Span[] spans = source[row];
            Span[] mirrored = new Span[spans.length];
            for (int index = 0; index < spans.length; index++) {
                Span span = spans[spans.length - index - 1];
                mirrored[index] = new Span(width - span.right(), width - span.left());
            }
            result[row] = mirrored;
        }
        return result;
    }

    private static Span[][] rasterize(List<Point> polygon, int width, int height) {
        Span[][] rows = new Span[height][];
        for (int row = 0; row < height; row++) {
            double scanY = (row + 0.5) / height;
            List<Double> intersections = new ArrayList<>();
            for (int index = 0; index < polygon.size() - 1; index++) {
                Point first = polygon.get(index);
                Point second = polygon.get(index + 1);
                boolean crosses = first.y() <= scanY && scanY < second.y()
                    || second.y() <= scanY && scanY < first.y();
                if (crosses) {
                    double amount = (scanY - first.y()) / (second.y() - first.y());
                    intersections.add(first.x() + amount * (second.x() - first.x()));
                }
            }
            Collections.sort(intersections);
            List<Span> spans = new ArrayList<>(Math.max(1, intersections.size() / 2));
            for (int index = 0; index + 1 < intersections.size(); index += 2) {
                int left = pixelStart(intersections.get(index), width);
                int right = pixelStart(intersections.get(index + 1), width);
                left = Math.max(0, Math.min(width, left));
                right = Math.max(0, Math.min(width, right));
                if (left < right) spans.add(new Span(left, right));
            }
            rows[row] = spans.toArray(Span[]::new);
        }
        return rows;
    }

    /** Returns the first pixel whose center lies at or right of a normalized edge. */
    private static int pixelStart(double normalizedX, int width) {
        return (int) Math.ceil(normalizedX * width - 0.5);
    }

    private static boolean hasRasterBounds(Rect bounds) {
        return bounds.width() > 0 && bounds.height() > 0
            && bounds.x() <= Integer.MAX_VALUE - bounds.width()
            && bounds.y() <= Integer.MAX_VALUE - bounds.height();
    }

    private static Map<Shape, List<Point>> paths() {
        Map<Shape, List<Point>> paths = new EnumMap<>(Shape.class);
        // A straight-sided graphic: the silhouette and every control share square corners.
        List<Point> rectangle = List.of(new Point(0, 0), new Point(1, 0),
            new Point(1, 1), new Point(0, 1), new Point(0, 0));
        for (Shape shape : Shape.values()) paths.put(shape, rectangle);
        return Map.copyOf(paths);
    }
    private record CacheKey(Shape shape, int width, int height) {}
    private record Span(int left, int right) {}
    private record Point(double x, double y) {}

}

