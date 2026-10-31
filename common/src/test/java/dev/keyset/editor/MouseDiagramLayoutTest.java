package dev.keyset.editor;

import java.util.EnumMap;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseDiagramLayoutTest {
    @Test
    void requestedMouseScaleIsRespectedEvenInSmallPanels() {
        for (int[] size : new int[][] {{160, 80}, {280, 148}, {600, 300}}) {
            var full = MouseDiagramLayout.forArea(0, 0, size[0], size[1], 100);
            for (int scale : new int[] {60, 80}) {
                var changed = MouseDiagramLayout.forArea(0, 0, size[0], size[1], scale);
                assertEquals(full.body().width() * scale / 100.0, changed.body().width(), 1.1);
                assertEquals(full.body().height() * scale / 100.0, changed.body().height(), 1.1);
            }
        }
    }
    @Test
    void primaryButtonsFormOneSilhouetteWithThePalmInsteadOfProtrudingFromTheShell() {
        for (int width : new int[] {160, 280, 600}) for (int height : new int[] {80, 138}) {
            var board = MouseDiagramLayout.forArea(0, 0, width, height, 100);
            for (var control : new MouseDiagramLayout.Control[] {MouseDiagramLayout.Control.LEFT, MouseDiagramLayout.Control.RIGHT}) {
                var rect = board.part(control).bounds();
                var shape = control == MouseDiagramLayout.Control.LEFT ? MouseContours.Shape.LEFT : MouseContours.Shape.RIGHT;
                for (int y = rect.y(); y < rect.bottom(); y++) for (int x = rect.x(); x < rect.right(); x++) {
                    if (!MouseContours.contains(shape, rect, x + .5, y + .5)) continue;
                    assertTrue(MouseContours.contains(MouseContours.Shape.BODY, board.body(), x + .5, y + .5),
                        "Click plates must stay inside the continuous mouse shell");
                }
            }
        }
    }
    @Test
    void artworkAndAllEightInputsFitEverySupportedAreaAndScale() {
        for (int width : new int[] {160, 184, 224, 280, 356, 600}) {
            for (int height : new int[] {80, 102, 138}) {
                for (int scale : new int[] {60, 80, 100}) {
                    var layout = MouseDiagramLayout.forArea(17, 29, width, height, scale);
                    assertEquals(8, layout.buttons().size());
                    var controls = EnumSet.noneOf(MouseDiagramLayout.Control.class);
                    for (var part : layout.buttons()) {
                        assertTrue(controls.add(part.control()));
                        inside(part.bounds(), 17, 29, width, height);
                    }
                    for (var rect : new MouseDiagramLayout.Rect[] {layout.body(), layout.sideBody(),
                            layout.topViewLabel(), layout.sideViewLabel(), layout.extraLabel()}) {
                        inside(rect, 17, 29, width, height);
                    }
                    assertTrue(layout.body().right() < layout.sideBody().x());
                    assertTrue(layout.topViewLabel().bottom() <= layout.body().y());
                    assertTrue(layout.sideViewLabel().bottom() <= layout.sideBody().y());
                    assertTrue(layout.sideBody().bottom() < layout.extraLabel().y());
                    assertTrue(layout.extraLabel().bottom() <= layout.part(MouseDiagramLayout.Control.EXTRA6).bounds().y());
                    var front = layout.part(MouseDiagramLayout.Control.SIDE5).bounds();
                    var rear = layout.part(MouseDiagramLayout.Control.SIDE4).bounds();
                    assertTrue(front.right() <= rear.x(), "M5 is nearer the nose, M4 nearer the palm");
                    for (var side : new MouseDiagramLayout.Rect[] {front, rear}) {
                        for (int y = side.y(); y < side.bottom(); y++) for (int x = side.x(); x < side.right(); x++) {
                            assertTrue(MouseContours.contains(MouseContours.Shape.SIDE_BODY, layout.sideBody(), x + .5, y + .5),
                                "Both thumb buttons remain inside the curved side shell");
                        }
                    }
                }
            }
        }
    }

    @Test
    void wheelWinsItsOverlappingRegionAndSquareCornersRemainClickable() {
        var layout = MouseDiagramLayout.forArea(0, 0, 356, 138, 100);
        var wheel = layout.part(MouseDiagramLayout.Control.MIDDLE).bounds();
        assertEquals(MouseDiagramLayout.Control.MIDDLE,
            layout.controlAt(wheel.x() + wheel.width() / 2.0, wheel.y() + wheel.height() / 2.0));
        var left = layout.part(MouseDiagramLayout.Control.LEFT).bounds();
        var right = layout.part(MouseDiagramLayout.Control.RIGHT).bounds();
        assertEquals(MouseDiagramLayout.Control.LEFT,
            layout.controlAt(left.x() + left.width() / 2.0, left.y() + left.height() * .75));
        assertEquals(MouseDiagramLayout.Control.RIGHT,
            layout.controlAt(right.x() + right.width() / 2.0, right.y() + right.height() * .75));
        assertEquals(MouseDiagramLayout.Control.LEFT, layout.controlAt(left.x() + .1, left.y() + .1));
        assertNull(layout.controlAt(-1, -1));
    }

    @Test
    void everyVisibleControlHasAnUnambiguousClickableAreaAfterResizing() {
        for (int[] area : new int[][] {{160, 80}, {224, 102}, {600, 138}}) {
            var layout = MouseDiagramLayout.forArea(0, 0, area[0], area[1], 60);
            var pixels = new EnumMap<MouseDiagramLayout.Control, Integer>(MouseDiagramLayout.Control.class);
            for (int y = 0; y < area[1]; y++) for (int x = 0; x < area[0]; x++) {
                var control = layout.controlAt(x + .5, y + .5);
                if (control == null) continue;
                assertTrue(layout.part(control).bounds().contains(x + .5, y + .5));
                int hits = 0;
                for (var part : layout.buttons()) if (layout.hitTest(part.control(), x + .5, y + .5)) hits++;
                assertEquals(1, hits);
                pixels.merge(control, 1, Integer::sum);
            }
            assertEquals(8, pixels.size(), "Every physical input retains a clickable region");
        }
    }

    @Test
    void scalingPreservesTheOrganicMouseProportionsAndSharedArtworkCoordinates() {
        for (int height : new int[] {80, 102, 138}) {
            var small = MouseDiagramLayout.forArea(0, 0, 356, height, 60);
            var large = MouseDiagramLayout.forArea(0, 0, 356, height, 100);
            assertTrue(large.body().height() >= small.body().height());
            assertEquals(124.0 / 146, large.body().width() / (double) large.body().height(), .035);
            var sampled = large.scaled(2);
            assertEquals(large.body().x() * 2, sampled.body().x());
            assertEquals(large.body().height() * 2, sampled.body().height());
            for (var part : large.buttons()) {
                assertEquals(part.bounds().scaled(2), sampled.part(part.control()).bounds());
            }
        }
    }

    private static void inside(MouseDiagramLayout.Rect rect, int x, int y, int width, int height) {
        assertTrue(rect.width() > 0 && rect.height() > 0);
        assertTrue(rect.x() >= x && rect.y() >= y);
        assertTrue(rect.right() <= x + width && rect.bottom() <= y + height);
    }
}

