package dev.keyset.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearancePaletteTest {
    private static final int PAPER = 0xEEECE4;
    private static final int DARK = 0x000000;

    @Test
    void lightHeaderAndFooterButtonsRetainContrastOutsideCards() {
        for (int opacity = 15; opacity <= 90; opacity += 5) {
            AppearancePalette palette = AppearancePalette.from(new UiAppearance(100, 25, false, 0x25594C, PAPER, opacity));
            for (int fill : new int[] {palette.control(), palette.hover(), palette.selected(), palette.row()}) {
                int widget = palette.widgetSurface(fill);
                assertContrast("outside card button " + opacity, palette.text(), composite(0x202020, widget));
            }
        }
    }

    @Test
    void paperPaletteRemainsReadableAcrossSupportedOpacityRange() {
        for (int opacity : new int[] {15, 35, 90}) {
            assertReadable(new UiAppearance(100, 25, false, 0x25594C, PAPER, opacity));
        }
    }

    @Test
    void presetsKeepTextReadableOnControlsAndKeycaps() {
        int[][] presets = {
            {0x94E4D3, DARK},
            {0x78BDF2, 0x101A2C},
            {0xF2A66F, 0x24150E},
            {0x25594C, PAPER}
        };
        for (int[] preset : presets) {
            for (int opacity : new int[] {15, 35, 90}) {
                assertReadable(new UiAppearance(100, 25, false, preset[0], preset[1], opacity));
            }
        }
    }

    private static void assertReadable(UiAppearance appearance) {
        AppearancePalette palette = AppearancePalette.from(appearance);
        int surface = palette.effectiveSurface();
        int foreground = palette.text();

        assertContrast("panel", foreground, surface);
        assertContrast("muted text", palette.muted(), surface);
        assertContrast("conflict text", palette.red(), surface);
        assertOverlayContrast("panel highlight", palette.panelLight(), foreground, surface);
        assertOverlayContrast("conflict control", palette.conflictSurface(), palette.red(), surface);
        assertOverlayContrast("conflict hover", palette.conflictHover(), palette.red(), surface);
        assertOverlayContrast("control", palette.control(), foreground, surface);
        assertOverlayContrast("hover", palette.hover(), foreground, surface);
        assertOverlayContrast("selected", palette.selected(), foreground, surface);
        assertOverlayContrast("row", palette.row(), foreground, surface);
        assertOverlayContrast("alternate row", palette.rowAlternate(), foreground, surface);
        assertOverlayContrast("keycap", palette.keyFace(), foreground, surface);

        int selectedSurface = composite(surface, palette.selected());
        assertContrast("selected accent text", palette.accentText(), selectedSurface);
    }

    private static void assertOverlayContrast(String name, int overlay, int foreground, int surface) {
        assertContrast(name, foreground, composite(surface, overlay));
    }

    private static int composite(int surface, int overlay) {
        int alpha = (overlay >>> 24) & 0xFF;
        return AppearancePalette.composite(surface, overlay, alpha / 255.0);
    }

    private static void assertContrast(String name, int foreground, int background) {
        double contrast = AppearancePalette.contrastRatio(foreground, background);
        assertTrue(contrast >= 4.5, () -> name + " contrast was " + contrast);
    }
}
