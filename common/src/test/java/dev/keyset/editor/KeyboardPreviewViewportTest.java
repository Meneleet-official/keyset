package dev.keyset.editor;

import dev.keyset.profile.KeyboardFormFactor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyboardPreviewViewportTest {
    @Test
    void minimumGuiFullRussianPreviewRetainsReadableLettersIncludingShortI() {
        var picker = ColorPickerLayout.forScreen(320, 240, true);
        var pane = picker.preview();
        var view = KeyboardPreviewViewport.forArea(pane.width(), pane.height() - 24, KeyboardFormFactor.FULL, 0);
        var keys = KeyboardDiagramLayout.forArea(0, 0, view.contentWidth(), view.diagramHeight(),
            100, KeyboardFormFactor.FULL, KeyboardLayouts.forLanguage("ru_ru"));
        for (String input : new String[] {"key.keyboard.q", "key.keyboard.w", "key.keyboard.a", "key.keyboard.semicolon"}) {
            var key = keys.keys().stream().filter(value -> value.input().equals(input)).findFirst().orElseThrow();
            var r = key.bounds();
            var placement = KeycapLabelLayout.fitGlyphLine(KeycapPainter.faceBounds(r.x(), r.y(), r.width(), r.height()),
                0, input.equals("key.keyboard.q") ? -3 : 0, 9, input.equals("key.keyboard.q") ? 10 : 8,
                -3, 8, .5f);
            assertTrue(placement.isPresent(), () -> "The preview must draw " + key.label());
        }
        assertTrue(view.scrollbarHeight() > 0);
        var end = KeyboardPreviewViewport.forArea(pane.width(), pane.height() - 24, KeyboardFormFactor.FULL, 1);
        assertEquals(view.contentWidth() - view.visibleWidth(), end.offset());
    }

    @Test
    void widePreviewDoesNotAddAnUnneededScrollbarAndPanIsClamped() {
        var wide = KeyboardPreviewViewport.forArea(440, 200, KeyboardFormFactor.FULL, 1);
        assertEquals(0, wide.scrollbarHeight());
        assertEquals(0, wide.offset());
        assertEquals(0, KeyboardPreviewViewport.forArea(280, 100, KeyboardFormFactor.FULL, -1).offset());
        assertEquals(0, KeyboardPreviewViewport.forArea(280, 100, KeyboardFormFactor.FULL, Double.NaN).offset());
    }
}
