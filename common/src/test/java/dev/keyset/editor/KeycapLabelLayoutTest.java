package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class KeycapLabelLayoutTest {
    @Test
    void keycapFaceExcludesBevelsAndLabelKeepsOnePixelPadding() {
        var face = KeycapPainter.faceBounds(10, 20, 24, 20);
        assertEquals(new KeycapPainter.Bounds(12, 21, 20, 15), face);

        for (var label : new LabelMetric[] {
                new LabelMetric("A", 7), new LabelMetric("ЖЩ", 15),
                new LabelMetric("CAPS", 24), new LabelMetric("SHIFT", 31)}) {
            var placement = KeycapLabelLayout.fit(face, label.width(), 9, 0.3f).orElseThrow();
            assertTrue(placement.scale() <= 1f);
            assertInsideFaceWithPadding(face, placement, label.width(), 9, label.text());
        }
    }

    @Test
    void smallKeysScaleLabelsDownAndKeepWideLabelsCentered() {
        var smallFace = KeycapPainter.faceBounds(0, 0, 14, 12);
        var largeFace = KeycapPainter.faceBounds(0, 0, 28, 20);
        var normalLetter = KeycapLabelLayout.fit(smallFace, 8, 9, 0.3f).orElseThrow();
        var wideModifier = KeycapLabelLayout.fit(smallFace, 24, 9, 0.3f).orElseThrow();
        var largeLetter = KeycapLabelLayout.fit(largeFace, 8, 9, 0.3f).orElseThrow();

        assertTrue(wideModifier.scale() < normalLetter.scale());
        assertTrue(normalLetter.scale() < largeLetter.scale());
        assertInsideFaceWithPadding(smallFace, normalLetter, 8, 9, "letter");
        assertInsideFaceWithPadding(smallFace, wideModifier, 24, 9, "modifier");
        assertInsideFaceWithPadding(largeFace, largeLetter, 8, 9, "large letter");
    }

    @Test
    void tinyPreviewFacesOmitLabelsBelowReadableScale() {
        var tinyFace = KeycapPainter.faceBounds(0, 0, 6, 8);
        assertTrue(KeycapLabelLayout.fit(tinyFace, 7, 9, 0.5f).isEmpty());
        assertTrue(KeycapLabelLayout.fit(tinyFace, 7, 9, 0.1f).isPresent());
        assertTrue(KeycapLabelLayout.fit(KeycapPainter.faceBounds(0, 0, 1, 1), 1, 9, 0f).isEmpty());
    }

    @Test
    void accentedGlyphAscendersAndCustomFontOverhangsRemainInsideTheFace() {
        var face = KeycapPainter.faceBounds(10, 20, 14, 14);
        for (int[] glyph : new int[][] {{0, -3, 8, 12}, {-2, -4, 12, 16}, {1, 1, 7, 8}}) {
            var placement = KeycapLabelLayout.fitGlyphBounds(face, glyph[0], glyph[1], glyph[2], glyph[3], .1f).orElseThrow();
            float left = placement.x() + glyph[0] * placement.scale();
            float top = placement.y() + glyph[1] * placement.scale();
            assertTrue(left >= face.x() + 1 - .001f);
            assertTrue(top >= face.y() + 1 - .001f);
            assertTrue(left + glyph[2] * placement.scale() <= face.x() + face.width() - 1 + .001f);
            assertTrue(top + glyph[3] * placement.scale() <= face.y() + face.height() - 1 + .001f);
        }
    }

    @Test
    void sharedCyrillicLineKeepsTheBaseLetterInShortITheSameSizeAndBaselineAsI() {
        var face = KeycapPainter.faceBounds(0, 0, 16, 16);
        var normal = KeycapLabelLayout.fitGlyphLine(face, 0, 0, 6, 8, -3, 8, .3f).orElseThrow();
        var accented = KeycapLabelLayout.fitGlyphLine(face, 0, -3, 6, 10, -3, 8, .3f).orElseThrow();
        assertEquals(normal.scale(), accented.scale());
        assertEquals(normal.y(), accented.y());
        // Vanilla И ink occupies y=0..7, Й adds its breve at y=-3..-1.
        assertTrue(normal.y() >= face.y() + 1);
        assertTrue(accented.y() - 3 * accented.scale() >= face.y() + 1 - .001f);
        assertTrue(accented.y() + 7 * accented.scale() <= face.y() + face.height() - 1 + .001f);
    }

    private static void assertInsideFaceWithPadding(KeycapPainter.Bounds face,
                                                    KeycapLabelLayout.Placement placement,
                                                    int textWidth, int fontHeight, String label) {
        assertTrue(placement.x() >= face.x() + 1f - 0.001f, label);
        assertTrue(placement.y() >= face.y() + 1f - 0.001f, label);
        assertTrue(placement.x() + textWidth * placement.scale() <= face.x() + face.width() - 1f + 0.001f, label);
        assertTrue(placement.y() + fontHeight * placement.scale() <= face.y() + face.height() - 1f + 0.001f, label);
    }

    private record LabelMetric(String text, int width) {}
}
