package dev.keyset.editor;

import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.AppearancePreset;
import dev.keyset.profile.UiAppearance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseDiagramPainterTest {
    @Test
    void customBodyColorFillsThePalmWithoutAnAccentDot() {
        for (int color : new int[] {0, 0xFFFFFF, 0xFF00AA, 0x2380D0}) {
            var palette = AppearancePalette.from(UiAppearance.defaults().withMouseBodyColor(color));
            var image = MouseArtworkRaster.paint(124, 148, canvas -> MouseDiagramPainter.shell(canvas,
                new Rect(0, 0, 124, 146), palette));
            for (int y = 74; y < 130; y++) for (int x = 20; x < 104; x++) {
                assertEquals(0xFF000000 | color, image.pixel(x, y), "The palm has a uniform configured color");
            }
            assertNotEquals(image.pixel(0, 80), image.pixel(62, 80), "The body edge remains visible");
        }
    }
    @Test
    void legendsContrastWithTheActualClickPlatesAndWheelInEveryStateAndTheme() {
        for (var preset : AppearancePreset.values()) for (int opacity : new int[] {15, 35, 90}) {
            verifyLegends(preset.applyTo(new UiAppearance(100, 25, false, 0, 0, opacity)));
        }
        for (int panel : new int[] {0x449CFE, 0x5F5F5F, 0, 0xFFFFFF}) {
            verifyLegends(new UiAppearance(100, 25, false, 0x94E4D3, panel, 90,
                0xFFFFFF, 0xFF0000, 0x000000));
        }
    }

    private static void verifyLegends(UiAppearance appearance) {
        var palette = AppearancePalette.from(appearance);
        var layout = MouseDiagramLayout.forArea(0, 0, 356, 138, 100);
        for (var state : new MouseDiagramPainter.State[] {
                new MouseDiagramPainter.State(false, false, false, false, false),
                new MouseDiagramPainter.State(false, false, false, false, false, true),
                new MouseDiagramPainter.State(false, false, true, false, false),
                new MouseDiagramPainter.State(false, false, true, true, false),
                new MouseDiagramPainter.State(true, false, false, false, false, true),
                new MouseDiagramPainter.State(false, true, false, false, false, true),
                new MouseDiagramPainter.State(false, false, false, false, true, true)}) {
            for (var part : layout.buttons()) {
                Rect r = part.bounds();
                var pixels = paint(part.control(), r, palette, state);
                int x = r.width() / 2, y = r.height() / 2;
                int background = pixels.get(x + ":" + y);
                int text = MouseDiagramPainter.labelColor(part.control(), palette, state);
                assertTrue(AppearancePalette.contrastRatio(text, background) >= 4.5,
                    () -> part.control() + " legend contrast: " + AppearancePalette.contrastRatio(text, background));
            }
        }
    }

    @Test
    void wheelLegendIsOnItsOwnRecessedFaceAndFreeTextRemainsGray() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        var wheel = MouseDiagramLayout.forArea(0, 0, 356, 138, 100).part(Control.MIDDLE).bounds();
        var free = new MouseDiagramPainter.State(false, false, false, false, false, true);
        var pixels = paint(Control.MIDDLE, wheel, palette, free);
        int foreground = MouseDiagramPainter.labelColor(Control.MIDDLE, palette, free);
        assertNotEquals(palette.text(), foreground);
        for (int y = wheel.height() / 2 - 3; y <= wheel.height() / 2 + 3; y++) {
            for (int x = wheel.width() / 2 - 2; x <= wheel.width() / 2 + 2; x++) {
                assertTrue(AppearancePalette.contrastRatio(foreground, pixels.get(x + ":" + y)) >= 4.5,
                    "Ribs and seams leave a clean area for M3");
            }
        }
        assertEquals(palette.text(), MouseDiagramPainter.labelColor(Control.MIDDLE, palette,
            new MouseDiagramPainter.State(true, false, false, false, false, true)));
    }

    @Test
    void conflictAndFreeColorsRemainDistinctWithoutLosingTheConfiguredFace() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        var layout = MouseDiagramLayout.forArea(0, 0, 356, 138, 100);
        for (var part : layout.buttons()) {
            var free = paint(part.control(), part.bounds(), palette,
                new MouseDiagramPainter.State(false, false, false, false, false, true));
            var bound = paint(part.control(), part.bounds(), palette,
                new MouseDiagramPainter.State(false, false, true, false, false));
            var conflict = paint(part.control(), part.bounds(), palette,
                new MouseDiagramPainter.State(false, false, true, true, false));
            assertNotEquals(free, bound);
            assertNotEquals(bound, conflict);
            assertTrue(conflict.containsValue(palette.keyColors(true, true, false).edge()));
        }
    }

    @Test
    void hoverAndFocusChangeTheActualButtonOutline() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        var layout = MouseDiagramLayout.forArea(0, 0, 184, 102, 100);
        for (var part : layout.buttons()) {
            var idle = paint(part.control(), part.bounds(), palette,
                new MouseDiagramPainter.State(false, false, true, false, false));
            assertNotEquals(idle, paint(part.control(), part.bounds(), palette,
                new MouseDiagramPainter.State(true, false, true, false, false)));
            assertNotEquals(idle, paint(part.control(), part.bounds(), palette,
                new MouseDiagramPainter.State(false, true, true, false, false)));
        }
    }

    private static Map<String, Integer> paint(Control control, Rect r, AppearancePalette palette,
                                              MouseDiagramPainter.State state) {
        Map<String, Integer> pixels = new HashMap<>();
        MouseDiagramPainter.button((x, y, right, bottom, color) -> {
            assertTrue(right > x && bottom > y);
            assertTrue(x >= r.x() && right <= r.right());
            assertTrue(y >= r.y() && bottom <= r.bottom() + 2);
            for (int py = y; py < bottom; py++) for (int px = x; px < right; px++) {
                pixels.put((px - r.x()) + ":" + (py - r.y()), color);
            }
        }, control, r, palette, state);
        return pixels;
    }
}
