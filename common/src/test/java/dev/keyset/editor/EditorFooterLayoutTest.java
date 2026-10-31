package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorFooterLayoutTest {
    @Test
    void allControlsRemainSeparateAtEveryPanelWidthAndPosition() {
        for (int screen : new int[] {640, 720, 960, 1920}) {
            for (boolean right : new boolean[] {false, true}) {
                for (double ratio : new double[] {0.35, 0.52, 0.65}) {
                    var panels = EditorPanelLayout.forWidth(screen, ratio, right, false);
                    var footer = EditorFooterLayout.forWidth(panels.mainWidth());
                    assertTrue(footer.actionWidth() > 0);
                    assertTrue(footer.actionWidth() < footer.resetX());
                    assertTrue(footer.resetX() + footer.actionWidth() < footer.undoX());
                    assertTrue(footer.undoX() + footer.undoWidth() < footer.resetAllX());
                    assertTrue(footer.resetAllX() + footer.resetAllWidth() < footer.doneX());
                    assertEquals(panels.mainX() + panels.mainWidth(), panels.mainX() + footer.doneX() + footer.doneWidth());
                }
            }
        }
        assertTrue(EditorFooterLayout.forWidth(240).actionWidth() < 36, "Narrow panels use compact action buttons");
    }
}
