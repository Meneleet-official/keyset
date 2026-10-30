package dev.keyset.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearancePresetTest {
    @Test
    void appliesEveryPaletteColorAndPreservesLayoutAndOpacity() {
        UiAppearance starting = new UiAppearance(73, 29, true, 0x123456, 0x654321, 68,
                0x112233, 0x445566, 0x778899).withMouseScale(74)
                .withKeyboardPresentation(KeyboardPresentation.defaults().withBottomRow(
                        new KeyboardBottomRow(java.util.List.of("key.keyboard.fn", "key.keyboard.space"), 19)));

        for (AppearancePreset preset : AppearancePreset.values()) {
            UiAppearance expected = new UiAppearance(73, 29, true,
                    preset.accentColor(), preset.panelColor(), preset == AppearancePreset.BLACK ? 35 : 68,
                    preset.boundKeyColor(), preset.conflictKeyColor(), preset.unboundKeyColor())
                    .withMouseBodyColor(preset.mouseBodyColor()).withMouseScale(74)
                    .withKeyboardPresentation(starting.keyboardPresentation());

            UiAppearance applied = preset.applyTo(starting);

            assertEquals(expected, applied, preset.name());
            assertEquals(starting.keyboardPresentation().bottomRow(), applied.keyboardPresentation().bottomRow());
            assertTrue(preset.matches(applied), preset.name());
        }
    }

    @Test
    void matchingIgnoresLayoutAndOpacityButDetectsEachManualColorEdit() {
        AppearancePreset preset = AppearancePreset.DEFAULT;
        UiAppearance palette = preset.applyTo(UiAppearance.defaults());

        assertTrue(preset.matches(new UiAppearance(80, 31, true, palette.accentColor(), palette.panelColor(), 70,
                palette.boundKeyColor(), palette.conflictKeyColor(), palette.unboundKeyColor())));
        assertFalse(preset.matches(withColors(palette, palette.accentColor() ^ 1, palette.panelColor(),
                palette.boundKeyColor(), palette.conflictKeyColor(), palette.unboundKeyColor())));
        assertFalse(preset.matches(withColors(palette, palette.accentColor(), palette.panelColor() ^ 1,
                palette.boundKeyColor(), palette.conflictKeyColor(), palette.unboundKeyColor())));
        assertFalse(preset.matches(withColors(palette, palette.accentColor(), palette.panelColor(),
                palette.boundKeyColor() ^ 1, palette.conflictKeyColor(), palette.unboundKeyColor())));
        assertFalse(preset.matches(withColors(palette, palette.accentColor(), palette.panelColor(),
                palette.boundKeyColor(), palette.conflictKeyColor() ^ 1, palette.unboundKeyColor())));
        assertFalse(preset.matches(withColors(palette, palette.accentColor(), palette.panelColor(),
                palette.boundKeyColor(), palette.conflictKeyColor(), palette.unboundKeyColor() ^ 1)));
        assertFalse(preset.matches(palette.withMouseBodyColor(palette.mouseBodyColor() ^ 1)));
        assertFalse(preset.matches(null));
    }

    @Test
    void presetsUseDistinctSensibleMouseShellColors() {
        assertEquals(0x30363B, AppearancePreset.DEFAULT.mouseBodyColor());
        assertEquals(0x273B52, AppearancePreset.OCEAN.mouseBodyColor());
        assertEquals(0x4A352A, AppearancePreset.EMBER.mouseBodyColor());
        assertEquals(0xC4C9CD, AppearancePreset.PAPER.mouseBodyColor());
        for (AppearancePreset preset : AppearancePreset.values()) {
            assertEquals(preset.mouseBodyColor(), preset.applyTo(UiAppearance.defaults()).mouseBodyColor());
        }
    }

    @Test
    void appearanceCopyMethodsPreserveCustomMouseBodyColor() {
        UiAppearance custom = UiAppearance.defaults().withMouseBodyColor(0x123456);

        assertEquals(0x123456, custom.withKeyboardFormFactor(KeyboardFormFactor.TKL).mouseBodyColor());
        assertEquals(0x123456, custom.withKeyboardPresentation(KeyboardPresentation.defaults()).mouseBodyColor());
        assertEquals(0xFFFFFF, custom.withMouseBodyColor(-1).mouseBodyColor());
        assertEquals(0x30363B, new UiAppearance(100, 25, false, 1, 2, 35,
                3, 4, 5, KeyboardFormFactor.TKL, KeyboardPresentation.defaults()).mouseBodyColor());
    }

    @Test
    void retainsTheExistingTranslationKeys() {
        assertEquals("keyset.appearance_preset_default", AppearancePreset.DEFAULT.translationKey());
        assertEquals("keyset.appearance_preset_ocean", AppearancePreset.OCEAN.translationKey());
        assertEquals("keyset.appearance_preset_ember", AppearancePreset.EMBER.translationKey());
        assertEquals("keyset.appearance_preset_paper", AppearancePreset.PAPER.translationKey());
    }

    @Test
    void refreshesOnlyTheFourExactPreviousBasePalettes() {
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (preset == AppearancePreset.BLACK) continue;
            UiAppearance previous = previousPalette(preset);

            assertEquals(preset.applyTo(previous), AppearancePreset.refreshLegacyPalette(previous), preset.name());
        }
    }

    @Test
    void refreshesLowContrastPalettesWithoutChangingCustomColors() {
        int[][] colors = {
                {0x748C86, 0x5F736D}, {0x6F9CBE, 0x587C98},
                {0xB78E70, 0x927259}, {0x5E7B70, 0x7A9085}
        };
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (preset == AppearancePreset.BLACK) continue;
            int[] pair = colors[preset.ordinal()];
            UiAppearance previous = new UiAppearance(73, 29, true, preset.accentColor(), preset.panelColor(), 68,
                    pair[0], preset.conflictKeyColor(), pair[1]);
            assertEquals(preset.applyTo(previous), AppearancePreset.refreshLegacyPalette(previous), preset.name());
            UiAppearance custom = withColors(previous, previous.accentColor(), pair[0] ^ 1, pair[1]);
            assertSame(custom, AppearancePreset.refreshLegacyPalette(custom), preset.name());
        }
    }

    @Test
    void leavesManualAccentBoundAndFreeColorEditsUntouched() {
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (preset == AppearancePreset.BLACK) continue;
            UiAppearance previous = previousPalette(preset);
            UiAppearance[] edited = {
                withColors(previous, previous.accentColor() ^ 1,
                        previous.boundKeyColor(), previous.unboundKeyColor()),
                withColors(previous, previous.accentColor(),
                        previous.boundKeyColor() ^ 1, previous.unboundKeyColor()),
                withColors(previous, previous.accentColor(),
                        previous.boundKeyColor(), previous.unboundKeyColor() ^ 1)
            };

            for (UiAppearance custom : edited) {
                assertSame(custom, AppearancePreset.refreshLegacyPalette(custom), preset.name());
            }
        }
    }

    private static UiAppearance previousPalette(AppearancePreset preset) {
        return switch (preset) {
            case DEFAULT -> new UiAppearance(73, 29, true, 0x94E4D3, 0x000000, 68,
                    0x8DAFA6, 0xD77979, 0x48515A);
            case OCEAN -> new UiAppearance(73, 29, true, 0x78BDF2, 0x101A2C, 68,
                    0x78BDF2, 0xD77979, 0x4A627B);
            case EMBER -> new UiAppearance(73, 29, true, 0xF2A66F, 0x24150E, 68,
                    0xF2A66F, 0xD77979, 0x6B4B38);
            case PAPER -> new UiAppearance(73, 29, true, 0x25594C, 0xEEECE4, 68,
                    0x25594C, 0xC74444, 0x747C78);
            case BLACK -> throw new IllegalArgumentException("Black has no legacy palette");
        };
    }

    @Test
    void blackPresetMatchesTheSuppliedThemeAndIsNotMigratedOnReload() {
        UiAppearance starting = new UiAppearance(73, 29, true, 1, 2, 68).withMouseScale(80);
        UiAppearance applied = AppearancePreset.BLACK.applyTo(starting);
        assertEquals(12661812, applied.accentColor());
        assertEquals(0, applied.panelColor());
        assertEquals(35, applied.panelOpacity());
        assertEquals(3158064, applied.boundKeyColor());
        assertEquals(10044227, applied.conflictKeyColor());
        assertEquals(2236962, applied.unboundKeyColor());
        assertEquals(3158064, applied.mouseBodyColor());
        assertEquals(73, applied.keyboardScale());
        assertEquals(80, applied.mouseScale());
        assertSame(applied, AppearancePreset.refreshLegacyPalette(applied));
    }

    private static UiAppearance withColors(UiAppearance base, int accent, int bound, int unbound) {
        return withColors(base, accent, base.panelColor(), bound, base.conflictKeyColor(), unbound);
    }

    private static UiAppearance withColors(UiAppearance base, int accent, int panel,
                                           int bound, int conflict, int unbound) {
        return new UiAppearance(base.keyboardScale(), base.rowHeight(), base.inputOnRight(),
                accent, panel, base.panelOpacity(), bound, conflict, unbound);
    }
}
