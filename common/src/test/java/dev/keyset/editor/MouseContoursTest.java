package dev.keyset.editor;

import dev.keyset.editor.MouseContours.Shape;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseContoursTest {
    @Test
    void everyShapeRasterizesWithinItsBoundsWithoutEmptyFillCalls() {
        for (Shape shape : Shape.values()) {
            for (int width : new int[] {1, 2, 3, 11, 48, 96}) {
                for (int height : new int[] {1, 2, 7, 31, 64}) {
                    Rect bounds = new Rect(17, 29, width, height);
                    int[] calls = {0};
                    MouseContours.fill((left, top, right, bottom, color) -> {
                        calls[0]++;
                        assertTrue(left < right, shape + " emits positive-width spans");
                        assertTrue(top < bottom, shape + " emits positive-height spans");
                        assertTrue(left >= bounds.x() && right <= bounds.right());
                        assertTrue(top >= bounds.y() && bottom <= bounds.bottom());
                    }, shape, bounds, 0xFF55AA77);
                    assertTrue(calls[0] >= 0);
                }
            }
        }
    }

    @Test
    void rightClickPlateIsAnExactPixelMirrorOfTheLeftPlate() {
        for (int width : new int[] {9, 32, 57, 100}) {
            for (int height : new int[] {7, 29, 64}) {
                Map<String, Integer> left = paint(Shape.LEFT, new Rect(5, 9, width, height));
                Map<String, Integer> right = paint(Shape.RIGHT, new Rect(73, 41, width, height));
                for (int row = 0; row < height; row++) {
                    for (int column = 0; column < width; column++) {
                        assertEquals(left.containsKey(column + ":" + row),
                            right.containsKey((width - column - 1) + ":" + row),
                            "The primary click plates mirror exactly at " + width + "x" + height);
                    }
                }
            }
        }
    }

    @Test
    void rightClickPlateGradientMirrorsWithItsContour() {
        Rect leftBounds = new Rect(0, 0, 83, 61);
        Rect rightBounds = new Rect(200, 30, leftBounds.width(), leftBounds.height());
        ColorFunctionTest colors = new ColorFunctionTest();
        Map<String, Integer> left = paint(Shape.LEFT, leftBounds, colors::color);
        Map<String, Integer> right = paint(Shape.RIGHT, rightBounds, colors::color);
        for (int row = 0; row < leftBounds.height(); row++) {
            for (int column = 0; column < leftBounds.width(); column++) {
                assertEquals(left.get(column + ":" + row),
                    right.get((leftBounds.width() - column - 1) + ":" + row));
            }
        }
    }

    @Test
    void containsMatchesThePaintedPixelCoverageForEveryShape() {
        Rect bounds = new Rect(37, 81, 73, 49);
        for (Shape shape : Shape.values()) {
            Set<String> painted = new HashSet<>(paint(shape, bounds).keySet());
            for (int row = 0; row < bounds.height(); row++) {
                for (int column = 0; column < bounds.width(); column++) {
                    boolean filled = painted.contains(column + ":" + row);
                    assertEquals(filled, MouseContours.contains(shape, bounds,
                        bounds.x() + column + 0.5, bounds.y() + row + 0.5),
                        shape + " contains agrees with scanline coverage");
                }
            }
            assertFalse(MouseContours.contains(shape, bounds, bounds.x() - 0.01, bounds.y()));
            assertFalse(MouseContours.contains(shape, bounds, bounds.right(), bounds.y()));
            assertFalse(MouseContours.contains(shape, bounds, bounds.x(), bounds.bottom()));
        }
    }

    @Test
    void invalidBoundsAreNoOpsForFillAndContains() {
        for (Shape shape : Shape.values()) {
            for (Rect bounds : new Rect[] {new Rect(0, 0, 0, 10), new Rect(0, 0, 10, 0),
                    new Rect(0, 0, -2, 4), new Rect(0, 0, 4, -2),
                    new Rect(Integer.MAX_VALUE, 0, 1, 1), new Rect(0, Integer.MAX_VALUE, 1, 1)}) {
                int[] calls = {0};
                MouseContours.fill((left, top, right, bottom, color) -> calls[0]++, shape, bounds, 1);
                assertEquals(0, calls[0]);
                assertFalse(MouseContours.contains(shape, bounds, 0, 0));
            }
        }
    }

    @Test
    void gradientRasterizerBoundsItsSamplingAndCoalescesIdenticalColors() {
        Rect bounds = new Rect(13, 23, 91, 47);
        Map<Integer, Integer> callsPerRow = new HashMap<>();
        Set<Integer> colors = new HashSet<>();
        MouseContours.fill((left, top, right, bottom, color) -> {
            assertEquals(1, bottom - top);
            assertTrue(left < right);
            callsPerRow.merge(top, 1, Integer::sum);
            colors.add(color);
        }, Shape.BODY, bounds, (u, v) -> ((int) Math.round(u * 200) << 8) | (int) Math.round(v * 200));
        assertTrue(callsPerRow.values().stream().allMatch(calls -> calls <= 32));
        assertTrue(colors.size() > 8, "A gradient varies across rows and horizontal bands");
        callsPerRow.clear();
        MouseContours.fill((left, top, right, bottom, color) -> callsPerRow.merge(top, 1, Integer::sum),
            Shape.BODY, bounds, (u, v) -> 0xFF445566);
        assertTrue(callsPerRow.values().stream().allMatch(calls -> calls == 1),
            "Flat portions emit one span, rather than repeated equal-color bands");
    }

    @Test
    void squareBodyKeepsItsFullWidthFromTopToBottom() {
        Rect bounds = new Rect(0, 0, 100, 180);
        Map<Integer, Integer> widths = new HashMap<>();
        MouseContours.fill((left, top, right, bottom, color) -> widths.put(top, right - left), Shape.BODY, bounds, 1);
        assertEquals(bounds.height(), widths.size());
        assertTrue(widths.values().stream().allMatch(width -> width == bounds.width()));
    }

    @Test
    void squareButtonsAndWheelHaveVisibleClickableCorners() {
        for (Shape shape : Shape.values()) {
            Rect bounds = new Rect(13, 27, 64, 28);
            Map<String, Integer> pixels = paint(shape, bounds);
            assertEquals(bounds.width() * bounds.height(), pixels.size());
            for (double[] corner : new double[][] {{13.1, 27.1}, {76.9, 27.1}, {13.1, 54.9}, {76.9, 54.9}}) {
                assertTrue(MouseContours.contains(shape, bounds, corner[0], corner[1]), shape.name());
            }
        }
    }
    private static Map<String, Integer> paint(Shape shape, Rect bounds) {
        return paint(shape, bounds, (u, v) -> 0xFF123456);
    }

    private static Map<String, Integer> paint(Shape shape, Rect bounds, MouseContours.ColorFunction colors) {
        Map<String, Integer> pixels = new HashMap<>();
        MouseContours.fill((left, top, right, bottom, color) -> {
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    pixels.put((x - bounds.x()) + ":" + (y - bounds.y()), color);
                }
            }
        }, shape, bounds, colors);
        return pixels;
    }

    private static final class ColorFunctionTest {
        private int color(double u, double v) {
            return ((int) Math.round(u * 200) << 8) | (int) Math.round(v * 200);
        }
    }
}


