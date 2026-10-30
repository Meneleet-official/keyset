package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GlyphInkBoundsTest {
    @Test
    void shortIUsesVisibleInkInsteadOfThePaddedAtlasCell() {
        String[] rows = {".#..#....", "..##.....", ".........", "#...#....", "#..##....",
            "#.#.#....", "##..#....", "#...#....", "#...#....", "#...#....", ".........", "........."};
        var ink = GlyphInkBounds.scan(9, 12, (x, y) -> rows[y].charAt(x) == '#' ? 0xffffffff : 0x00ffffff).orElseThrow();
        assertEquals(new GlyphInkBounds(0, 0, 5, 10), ink);
        for (int size : new int[] {12, 14, 16, 20, 28}) {
            var face = KeycapPainter.faceBounds(0, 0, size, size);
            var base = KeycapLabelLayout.fitGlyphLine(face, 0, 0, 5, 7, -3, 8, .1f).orElseThrow();
            var accented = KeycapLabelLayout.fitGlyphLine(face, ink.x(), ink.y() - 3,
                ink.width(), ink.height(), -3, 8, .1f).orElseThrow();
            assertEquals(base.scale(), accented.scale());
            assertEquals(base.x(), accented.x());
            assertEquals(base.y(), accented.y());
            assertTrue(accented.y() - 3 * accented.scale() >= face.y() + 1 - .001f);
            assertTrue(accented.y() + 7 * accented.scale() <= face.y() + face.height() - 1 + .001f);
        }
    }

    @Test
    void transparentRgbDoesNotCountAsInk() {
        assertTrue(GlyphInkBounds.scan(9, 12, (x, y) -> 0x00ffffff).isEmpty());
        assertEquals(new GlyphInkBounds(2, 3, 1, 1),
            GlyphInkBounds.scan(8, 8, (x, y) -> x == 2 && y == 3 ? 0x01000000 : 0).orElseThrow());
    }
}
