package dev.keyset.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AppearancePaletteTest {
    @Test
    void paperKeysHaveBlackLegendsRegardlessOfPanelOpacityAndInteraction() {
        for (int opacity : new int[] {15, 35, 90}) {
            var palette = AppearancePalette.from(AppearancePreset.PAPER.applyTo(new UiAppearance(100, 25, false, 0, 0, opacity)));
            for (boolean assigned : new boolean[] {false, true}) for (boolean conflict : new boolean[] {false, true}) {
                for (boolean emphasized : new boolean[] {false, true}) {
                    assertEquals(0xFF000000, palette.keyLegendColor(assigned, conflict, !assigned, emphasized));
                }
            }
        }
    }
    @Test
    void idleLegendsAreBlackOnLightFacesAndGrayOnDarkFaces() {
        for (var preset : AppearancePreset.values()) {
            var palette = AppearancePalette.from(preset.applyTo(UiAppearance.defaults()));
            for (boolean free : new boolean[] {false, true}) {
                var colors = palette.keyColors(false, false, free);
                int gray = colors.text();
                assertEquals((gray >>> 16) & 255, (gray >>> 8) & 255);
                assertEquals(gray & 255, (gray >>> 8) & 255);
                if (AppearancePalette.contrastRatio(0, colors.face()) >= AppearancePalette.contrastRatio(0xFFFFFF, colors.face())) {
                    assertEquals(0xFF000000, gray, preset.name());
                } else assertNotEquals(0xFFFFFFFF, gray, preset.name());
                assertContrast("idle gray legend", gray, colors.face());
            }
            var assigned = palette.keyColors(true, false, false);
            assertContrast("assigned legend", assigned.text(), assigned.face());
        }
    }

    @Test
    void customInputColorsKeepTheirEdgesAndReadableKeyFaces() {
        for (int panel : new int[] {0x000000, 0xEEECE4, 0x5F5F5F}) {
            for (int color : new int[] {0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF}) {
                UiAppearance appearance = new UiAppearance(100, 25, false, 0x94E4D3, panel, 90,
                    color, color, color);
                AppearancePalette palette = AppearancePalette.from(appearance);
                for (var colors : new AppearancePalette.KeyColors[] {
                        palette.keyColors(true, false, false), palette.keyColors(false, true, false),
                        palette.keyColors(false, false, true)}) {
                    assertEquals(0xFF000000 | color, colors.edge());
                    assertContrast("custom key face", colors.text(), colors.face());
                }
            }
        }
    }

    @Test
    void keyboardFacesRemainReadableWhenPaintedOverCustomOpaqueOutlines() {
        for (int panel : new int[] {0x000000, 0xEEECE4}) {
            for (int color : new int[] {0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF}) {
                var palette = AppearancePalette.from(new UiAppearance(100, 25, false, 0x94E4D3, panel, 90,
                    color, color, color));
                for (var colors : new AppearancePalette.KeyColors[] {palette.keyColors(true, false, false),
                        palette.keyColors(false, true, false), palette.keyColors(false, false, true)}) {
                    int face = colors.face();
                    assertEquals(255, face >>> 24, "The custom outline must not bleed through the face");
                    int actualBackground = AppearancePalette.composite(color, face, (face >>> 24) / 255.0);
                    assertContrast("key label over its painted custom outline", colors.text(), actualBackground);
                }
            }
        }
    }

    @Test
    void defaultAssignedAndFreeKeysAreDistinctAndConflictsTakePriority() {
        var appearance = UiAppearance.defaults();
        var palette = AppearancePalette.from(appearance);
        assertNotEquals(appearance.boundKeyColor(), appearance.unboundKeyColor());
        int boundFace = palette.keyColors(true, false, false).face();
        int freeFace = palette.keyColors(false, false, true).face();
        assertNotEquals(boundFace, freeFace);
        assertTrue(AppearancePalette.contrastRatio(0, freeFace) < AppearancePalette.contrastRatio(0, boundFace));
        assertEquals(0xFFD77979, palette.keyColors(true, true, false).edge());
        assertEquals(palette.keyColors(false, true, false), palette.keyColors(true, true, true));
    }

    @Test
    void presetAssignedAndFreeFacesStaySubtlyDistinctAndReadableAcrossOpacity() {
        for (AppearancePreset preset : AppearancePreset.values()) {
            for (int opacity : new int[] {15, 35, 90}) {
                UiAppearance appearance = preset.applyTo(
                        new UiAppearance(100, 25, false, 0, 0, opacity));
                AppearancePalette palette = AppearancePalette.from(appearance);
                AppearancePalette.KeyColors bound = palette.keyColors(true, false, false);
                AppearancePalette.KeyColors free = palette.keyColors(false, false, true);
                double faceContrast = AppearancePalette.contrastRatio(bound.face(), free.face());

                if (preset == AppearancePreset.BLACK) {
                    // The supplied Black theme deliberately uses closer neutral key colors.
                    assertTrue(faceContrast > 1.0 && faceContrast < 1.35);
                } else {
                    assertTrue(faceContrast >= 1.35 && faceContrast <= 1.65,
                            () -> preset.name() + " assigned/free face contrast at opacity " + opacity
                                    + " was " + faceContrast);
                }
                assertContrast(preset + " bound key label at opacity " + opacity,
                        bound.text(), bound.face());
                assertContrast(preset + " free key label at opacity " + opacity,
                        free.text(), free.face());
            }
        }
    }

    @Test
    void eachKeyTypeHasItsOwnColorIndependentOfTheUiAccent() {
        var first = AppearancePalette.from(new UiAppearance(100, 25, false, 0xFF00FF, 0, 35,
            0x112233, 0x445566, 0x778899));
        var second = AppearancePalette.from(new UiAppearance(100, 25, false, 0x00FF00, 0, 35,
            0x112233, 0x445566, 0x778899));
        assertEquals(0xFF112233, first.keyColors(true, false, false).edge());
        assertEquals(0xFF445566, first.keyColors(false, true, false).edge());
        assertEquals(0xFF778899, first.keyColors(false, false, true).edge());
        assertEquals(first.keyColors(true, false, false), second.keyColors(true, false, false));
        assertEquals(first.keyColors(false, true, false), second.keyColors(false, true, false));
        assertEquals(first.keyColors(false, false, true), second.keyColors(false, false, true));
    }

    @Test
    void mouseBodyColorIsReturnedAsAnOpaqueExactRgbValue() {
        AppearancePalette palette = AppearancePalette.from(UiAppearance.defaults().withMouseBodyColor(0x12ABEF));

        assertEquals(0xFF12ABEF, palette.mouseBodyColor());
        assertEquals(0xFF30363B, AppearancePalette.from(UiAppearance.defaults()).mouseBodyColor());
    }

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
        for (AppearancePreset preset : AppearancePreset.values()) {
            for (int opacity = 15; opacity <= 90; opacity += 5) {
                UiAppearance appearance = preset.applyTo(
                        new UiAppearance(100, 25, false, 0, 0, opacity));
                assertReadable(appearance);
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
        for (boolean assigned : new boolean[] {false, true}) for (boolean conflict : new boolean[] {false, true}) {
            var colors = palette.keyColors(assigned, conflict, !assigned);
            for (boolean emphasized : new boolean[] {false, true}) {
                assertContrast("keycap", palette.keyLegendColor(assigned, conflict, !assigned, emphasized), colors.face());
            }
        }

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
