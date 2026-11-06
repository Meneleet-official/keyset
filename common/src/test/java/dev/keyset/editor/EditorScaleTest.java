package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditorScaleTest {
    @Test
    void automaticScaleFollowsTheWindowNotTheGameSetting() {
        assertEquals(1, EditorScale.automatic(854, 480));
        assertEquals(2, EditorScale.automatic(1280, 720));
        assertEquals(2, EditorScale.automatic(1366, 768));
        assertEquals(2, EditorScale.automatic(1920, 1080));
        assertEquals(2, EditorScale.automatic(1912, 981));
        assertEquals(3, EditorScale.automatic(2560, 1440));
        assertEquals(4, EditorScale.automatic(3840, 2160));
    }

    @Test
    void automaticScaleKeepsTheTwoPanelEditorWheneverTheWindowAllowsIt() {
        for (int[] size : new int[][] {{1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {1912, 981},
                {2560, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}}) {
            int scale = EditorScale.automatic(size[0], size[1]);
            assertFalse(EditorViewport.forSize(size[0] / scale, size[1] / scale).singlePane(), size[0] + "x" + size[1]);
        }
    }

    @Test
    void narrowWindowsAreLimitedByWidth() {
        assertEquals(1, EditorScale.automatic(1000, 1080));
    }

    @Test
    void modesStepAroundTheAutomaticScale() {
        assertEquals(1, EditorScale.forMode(EditorScale.Mode.SMALLER, 1920, 1080, 4));
        assertEquals(3, EditorScale.forMode(EditorScale.Mode.LARGER, 1920, 1080, 4));
        assertEquals(4, EditorScale.forMode(EditorScale.Mode.GAME, 1920, 1080, 4));
        assertEquals(1, EditorScale.forMode(EditorScale.Mode.SMALLER, 854, 480, 2));
        assertTrue(EditorScale.forMode(EditorScale.Mode.LARGER, 640, 480, 1) >= 1);
    }
}
