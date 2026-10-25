package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorPanelLayoutTest {
    @Test
    void mirroredPanelsRemainInsideResizedWindows() {
        for (int width : new int[] {640, 720, 960, 1920}) {
            for (double ratio : new double[] {0.35, 0.52, 0.65}) {
                var left = EditorPanelLayout.forWidth(width, ratio, false, false);
                var right = EditorPanelLayout.forWidth(width, ratio, true, false);
                assertEquals(left.sidebarWidth(), right.sidebarWidth());
                assertEquals(left.mainWidth(), right.mainWidth());
                assertEquals(width - 16, right.sidebarX() + right.sidebarWidth());
                assertEquals(width - 16, left.mainX() + left.mainWidth());
                assertEquals(12, right.sidebarX() - right.mainX() - right.mainWidth());
                assertEquals(12, left.mainX() - left.sidebarX() - left.sidebarWidth());
                assertEquals(width - left.dividerX(), right.dividerX());
            }
        }
    }

    @Test
    void splitterMovesInTheCorrectDirectionAfterMirroring() {
        for (boolean mirrored : new boolean[] {false, true}) {
            var layout = EditorPanelLayout.forWidth(1200, 0.52, mirrored, false);
            int wider = layout.dividerX() + (mirrored ? -60 : 60);
            assertTrue(layout.ratioAtDivider(wider) > 0.52);
            for (int drag : new int[] {-9999, 0, 600, 1200, 9999}) {
                var moved = EditorPanelLayout.forWidth(1200, layout.ratioAtDivider(drag), mirrored, false);
                assertEquals(layout.clampDividerX(drag), moved.dividerX());
            }
        }
    }

    @Test
    void compactPagesIgnorePanelPositionWithoutGoingOffscreen() {
        var layout = EditorPanelLayout.forWidth(320, 0.65, true, true);
        assertEquals(8, layout.mainX());
        assertEquals(304, layout.mainWidth());
        assertEquals(layout.mainX(), layout.sidebarX());
    }
}
