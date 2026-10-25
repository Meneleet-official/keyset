package dev.keyset.editor;

import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.UiAppearance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseDiagramPainterTest {
    @Test
    void selectedWheelCaptionKeepsTheShellsForegroundForCustomColors() {
        var palette = AppearancePalette.from(new UiAppearance(100, 25, false, 0xFFFFFF, 0x5F5F5F, 90));
        var layout = MouseDiagramLayout.forArea(0, 0, 280, 138, 100);
        var selected = new MouseDiagramPainter.State(false, true, false, false, false);
        Map<String, Integer> shell = new HashMap<>();
        MouseDiagramPainter.shell((x, y, right, bottom, color) -> {
            for (int py = y; py < bottom; py++) {
                for (int px = x; px < right; px++) shell.put(px + ":" + py, color);
            }
        }, layout.body(), palette);
        Rect wheel = part(layout, Control.MIDDLE);
        int background = shell.get(wheel.x() + wheel.width() / 2 + ":" + (wheel.bottom() - 5));
        assertTrue(AppearancePalette.contrastRatio(
            MouseDiagramPainter.labelColor(Control.MIDDLE, palette, selected), background) >= 4.5);
    }

    @Test
    void bodyContourHasSymmetricSmoothShouldersAtEachSize() {
        for (int height : new int[] {60, 72, 94, 128}) {
            int width = (int) Math.round(height * 0.82);
            for (int row = 0; row < height; row++) {
                int inset = MouseDiagramPainter.silhouetteInset(width, height, row);
                assertEquals(inset, MouseDiagramPainter.silhouetteInset(width, height, height - 1 - row));
                assertTrue(inset >= 0 && inset * 2 < width);
                if (row > 0 && row < height / 2) {
                    assertTrue(inset <= MouseDiagramPainter.silhouetteInset(width, height, row - 1));
                }
            }
        }
    }

    @Test
    void primaryCapsMirrorAndWheelNeverTouchesItsLabel() {
        var palette = AppearancePalette.from(UiAppearance.defaults());
        var state = new MouseDiagramPainter.State(false, false, false, false, false);
        for (int scale : new int[] {60, 80, 100}) {
            var layout = MouseDiagramLayout.forArea(0, 0, 184, 102, scale);
            Rect left = part(layout, Control.LEFT), right = part(layout, Control.RIGHT), middle = part(layout, Control.MIDDLE);
            Map<String, Integer> a = paint(Control.LEFT, left, palette, state);
            Map<String, Integer> b = paint(Control.RIGHT, right, palette, state);
            for (int row = 0; row < left.height() + 2; row++) {
                for (int column = 0; column < left.width(); column++) {
                    assertEquals(a.get(column + ":" + row), b.get((left.width() - column - 1) + ":" + row));
                }
            }
            assertTrue(middle.y() + 1 + MouseDiagramPainter.wheelHeight(middle) <= middle.bottom() - 14);
        }
    }

    @Test
    void buttonLabelsContrastWithTheirActualPaintedSurfaces() {
        for (UiAppearance appearance : new UiAppearance[] {UiAppearance.defaults(),
                new UiAppearance(100, 25, false, 0x25594C, 0xEEECE4, 35),
                new UiAppearance(100, 25, false, 0x25594C, 0xEEECE4, 90)}) {
            var palette = AppearancePalette.from(appearance);
            var layout = MouseDiagramLayout.forArea(0, 0, 280, 138, 100);
            for (var state : new MouseDiagramPainter.State[] {
                    new MouseDiagramPainter.State(false, false, false, false, false),
                    new MouseDiagramPainter.State(true, false, false, false, false),
                    new MouseDiagramPainter.State(false, true, false, false, false),
                    new MouseDiagramPainter.State(false, false, false, true, false)}) {
                for (var button : layout.buttons()) {
                    if (button.control() == Control.MIDDLE) continue;
                    Rect bounds = button.bounds();
                    var pixels = paint(button.control(), bounds, palette, state);
                    int face = pixels.get(bounds.width() / 2 + ":" + bounds.height() / 2);
                    int text = state.conflict() ? palette.red() : state.selected() ? palette.accentText() : palette.text();
                    assertTrue(AppearancePalette.contrastRatio(text, face) >= 4.5);
                }
            }
        }
    }

    private static Rect part(MouseDiagramLayout layout, Control control) {
        return layout.buttons().stream().filter(part -> part.control() == control).findFirst().orElseThrow().bounds();
    }

    private static Map<String, Integer> paint(Control control, Rect bounds, AppearancePalette palette, MouseDiagramPainter.State state) {
        Map<String, Integer> pixels = new HashMap<>();
        MouseDiagramPainter.button((x, y, right, bottom, color) -> {
            assertTrue(right > x && bottom > y);
            assertTrue(x >= bounds.x() && right <= bounds.right());
            assertTrue(y >= bounds.y() && bottom <= bounds.bottom() + 2);
            for (int py = y; py < bottom; py++) {
                for (int px = x; px < right; px++) pixels.put((px - bounds.x()) + ":" + (py - bounds.y()), color);
            }
        }, control, bounds, palette, state);
        return pixels;
    }
}
