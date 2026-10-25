package dev.keyset.editor;

import java.util.EnumMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseDiagramLayoutTest {
    @Test
    void allControlsFitWithoutOverlapAtSupportedAreaSizesAndScales() {
        for (int width : new int[] {160, 184, 224, 280, 356, 600}) {
            for (int height : new int[] {80, 102, 138}) {
                for (int scale : new int[] {60, 80, 100}) {
                    var layout = MouseDiagramLayout.forArea(17, 29, width, height, scale);
                    var controls = new EnumMap<MouseDiagramLayout.Control, MouseDiagramLayout.Rect>(MouseDiagramLayout.Control.class);
                    var unique = new HashSet<MouseDiagramLayout.Control>();

                    assertEquals(8, layout.buttons().size());
                    for (var button : layout.buttons()) {
                        assertTrue(unique.add(button.control()), "Every mouse input is shown once");
                        controls.put(button.control(), button.bounds());
                        assertInside(button.bounds(), 17, 29, width, height);
                    }
                    assertEquals(8, unique.size());
                    assertInside(layout.body(), 17, 29, width, height);
                    assertInside(layout.extraLabel(), 17, 29, width, height);

                    for (int i = 0; i < layout.buttons().size(); i++) {
                        for (int j = i + 1; j < layout.buttons().size(); j++) {
                            assertFalse(overlaps(layout.buttons().get(i).bounds(), layout.buttons().get(j).bounds()),
                                "Clickable mouse controls must not overlap");
                        }
                    }

                    var left = controls.get(MouseDiagramLayout.Control.LEFT);
                    var right = controls.get(MouseDiagramLayout.Control.RIGHT);
                    var middle = controls.get(MouseDiagramLayout.Control.MIDDLE);
                    assertEquals(left.width(), right.width());
                    assertEquals(left.height(), right.height());
                    assertEquals(left.y(), right.y());
                    assertEquals(left.x() - layout.body().x(), layout.body().right() - right.right(),
                        "Primary buttons mirror across the body center");
                    assertEquals(layout.body().x() + layout.body().width() / 2,
                        middle.x() + middle.width() / 2, "Wheel hitbox is centered");
                    assertTrue(middle.width() >= 14, "M3 has room for its caption even at reduced scale");

                    var extra6 = controls.get(MouseDiagramLayout.Control.EXTRA6);
                    var extra7 = controls.get(MouseDiagramLayout.Control.EXTRA7);
                    var extra8 = controls.get(MouseDiagramLayout.Control.EXTRA8);
                    assertEquals(extra6.y(), extra7.y());
                    assertEquals(extra6.y(), extra8.y());
                    assertEquals(extra6.height(), extra7.height());
                    assertEquals(extra6.height(), extra8.height());
                    assertEquals(extra6.width(), extra7.width());
                    assertEquals(extra6.width(), extra8.width());
                    assertEquals(extra6.x(), layout.extraLabel().x());
                    assertEquals(extra8.right(), layout.extraLabel().right());
                    assertTrue(layout.extraLabel().bottom() <= extra6.y(), "The extras title sits above its row");
                }
            }
        }
    }

    @Test
    void bodyKeepsItsShapeAndMinimumCaptionRoomWhileResizing() {
        for (int height : new int[] {80, 102, 138}) {
            var small = MouseDiagramLayout.forArea(0, 0, 356, height, 60);
            var large = MouseDiagramLayout.forArea(0, 0, 356, height, 100);
            assertTrue(small.body().height() >= 60, "M1/M2 labels have room at reduced scale");
            assertTrue(large.body().height() >= small.body().height());
            assertTrue(large.body().width() >= small.body().width());
            assertEquals(0.82, (double) large.body().width() / large.body().height(), 0.04);
        }

        var narrower = MouseDiagramLayout.forArea(0, 0, 224, 102, 100);
        var wider = MouseDiagramLayout.forArea(0, 0, 600, 102, 100);
        assertEquals(narrower.body().height(), wider.body().height());
        assertTrue(wider.body().x() > narrower.body().x());
    }

    private static void assertInside(MouseDiagramLayout.Rect rect, int x, int y, int width, int height) {
        assertTrue(rect.width() > 0 && rect.height() > 0);
        assertTrue(rect.x() >= x && rect.y() >= y);
        assertTrue(rect.right() <= x + width && rect.bottom() <= y + height);
    }

    private static boolean overlaps(MouseDiagramLayout.Rect a, MouseDiagramLayout.Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }
}
