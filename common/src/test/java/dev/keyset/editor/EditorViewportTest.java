package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorViewportTest {
    @Test
    void largeMinecraftGuiScalesKeepActionsOnScreen() {
        for (int[] size : new int[][] {{320,240}, {480,252}, {480,270}, {639,360}}) {
            EditorViewport layout = EditorViewport.forSize(size[0], size[1]);
            assertTrue(layout.singlePane());
            assertTrue(layout.visibleActionRows(false) >= 8, "Action list keeps eight rows with no active filters");
            assertTrue(layout.visibleActionRows(true) >= 6, "Action list keeps six rows when a filter chip is visible");
            assertTrue(layout.rowsBottom() <= layout.footerY() - 6, "Footer stays separate");
            assertTrue(layout.keyboardPanelY() + layout.keyStep() * 5 + layout.keyStep() - 2 <= layout.inputControlsY(),
                "Keyboard stays above modifier controls");
            assertTrue(layout.categoryListTop() < layout.categoryListBottom(), "Filter list has visible rows");
        }
    }

    @Test
    void normalGuiSizesRetainTwoPanels() {
        for (int[] size : new int[][] {{960,540}, {1920,1080}, {640,480}, {640,360}, {960,440}}) {
            assertFalse(EditorViewport.forSize(size[0], size[1]).singlePane());
        }
        assertTrue(EditorViewport.forSize(960, 359).singlePane(), "Very short windows use compact tabs");
        assertTrue(EditorViewport.forSize(639, 480).singlePane(), "Narrow windows use compact tabs");
        assertTrue(EditorViewport.forSize(960, 360).rowsTop() < EditorViewport.forSize(960, 480).rowsTop(),
            "Short windows give the title row to the action list");
        EditorViewport normal = EditorViewport.forSize(640, 480);
        assertTrue((normal.categoryListBottom() - normal.categoryListTop()) / 24 >= 2,
            "Normal two-pane layout retains two rows of categories at the breakpoint");
    }

    @Test
    void compactPageRoutesScrollOnlyToItsVisibleList() {
        assertEquals(EditorViewport.ScrollTarget.ACTIONS, EditorViewport.scrollTarget(true,
            EditorViewport.Page.ACTIONS, true, true));
        assertEquals(EditorViewport.ScrollTarget.FILTERS, EditorViewport.scrollTarget(true,
            EditorViewport.Page.FILTERS, true, true));
        assertEquals(EditorViewport.ScrollTarget.NONE, EditorViewport.scrollTarget(true,
            EditorViewport.Page.INPUT, true, true));
        assertEquals(EditorViewport.ScrollTarget.NONE, EditorViewport.scrollTarget(true,
            EditorViewport.Page.ACTIONS, false, true));
        assertEquals(EditorViewport.ScrollTarget.ACTIONS, EditorViewport.scrollTarget(false,
            EditorViewport.Page.INPUT, true, false));
        assertEquals(EditorViewport.ScrollTarget.FILTERS, EditorViewport.scrollTarget(false,
            EditorViewport.Page.ACTIONS, false, true));
    }
}
