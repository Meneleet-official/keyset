package dev.keyset.editor;

import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.AppearancePreset;
import dev.keyset.profile.UiAppearance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeycapPainterTest {
    @Test
    void keycapsUseHardSquarePixelCornersAndKeepASeparateFrontBevel() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        Map<String, Integer> pixels = new HashMap<>();
        KeycapPainter.draw((left, top, right, bottom, color) -> {
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) pixels.put(x + ":" + y, color);
            }
        }, 0, 0, 24, 20, palette, new KeycapPainter.State(false, true, false, false, false, true));

        assertEquals(palette.text(), pixels.get("0:0"), "The upper corner is a crisp pixel, not a rounded cutout");
        assertEquals(palette.text(), pixels.get("23:0"));
        assertNotEquals(pixels.get("12:8"), pixels.get("12:18"), "The physical front bevel remains visible");
    }

    @Test
    void everySizeStaysInsideItsHitboxIncludingTinyPreviewsAndWideKeys() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        for (int width : new int[] {1, 2, 4, 5, 9, 16, 30, 64}) {
            for (int height : new int[] {1, 2, 5, 6, 12, 18, 32}) {
                KeycapPainter.draw((left, top, right, bottom, color) -> {
                    assertTrue(right > left && bottom > top);
                    assertTrue(left >= 10 && top >= 20);
                    assertTrue(right <= 10 + width && bottom <= 20 + height);
                }, 10, 20, width, height, palette, state(false, false, false));
            }
        }
    }

    @Test
    void topFaceRetainsItsTypeColorAndReadableLegendAboveTheSlopingFront() {
        for (var preset : AppearancePreset.values()) {
            var palette = AppearancePalette.from(preset.applyTo(UiAppearance.defaults()));
            for (var state : new KeycapPainter.State[] {state(false, false, true),
                    state(false, true, false), state(true, false, false)}) {
                Map<String, Integer> pixels = new HashMap<>();
                KeycapPainter.draw((left, top, right, bottom, color) -> {
                    for (int y = top; y < bottom; y++) {
                        for (int x = left; x < right; x++) pixels.put(x + ":" + y, color);
                    }
                }, 0, 0, 24, 20, palette, state);
                int face = pixels.get("12:9");
                assertEquals(palette.keyColors(state.assigned(), state.conflict(), state.free()).face(), face);
                assertTrue(AppearancePalette.contrastRatio(KeycapPainter.labelColor(palette, state), face) >= 4.5);
                assertNotEquals(face, pixels.get("12:18"), "The front bevel must be visible");
            }
        }
    }

    @Test
    void idleLegendsAreGrayAndHoverOrSelectionRestoresTheActiveLegend() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        var idle = state(false, false, true);
        int muted = KeycapPainter.labelColor(palette, idle);
        assertNotEquals(palette.text(), muted);
        assertEquals((muted >>> 16) & 255, (muted >>> 8) & 255);
        assertEquals(muted & 255, (muted >>> 8) & 255);
        assertEquals(palette.text(), KeycapPainter.labelColor(palette,
                new KeycapPainter.State(true, false, false, false, false, true)));
        assertEquals(palette.text(), KeycapPainter.labelColor(palette,
                new KeycapPainter.State(false, true, false, false, false, true)));
        assertEquals(palette.text(), KeycapPainter.labelColor(palette,
                new KeycapPainter.State(false, false, false, false, true, true)));
    }

    private static KeycapPainter.State state(boolean assigned, boolean conflict, boolean free) {
        return new KeycapPainter.State(false, false, assigned, conflict, false, free);
    }
}
