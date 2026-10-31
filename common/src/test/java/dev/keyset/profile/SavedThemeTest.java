package dev.keyset.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedThemeTest {
    @Test
    void capturesPaletteAndTrimsThemeName() {
        UiAppearance appearance = new UiAppearance(80, 27, true, 0x123456, 0x654321, 48,
                0xABCDEF, 0x102030, 0xFEDCBA, KeyboardFormFactor.values()[0]);

        SavedTheme saved = SavedTheme.capture("  My colors  ", appearance);

        assertEquals("My colors", saved.name());
        UUID.fromString(saved.id());
        assertEquals(appearance.accentColor(), saved.accentColor());
        assertEquals(appearance.panelColor(), saved.panelColor());
        assertEquals(appearance.panelOpacity(), saved.panelOpacity());
        assertEquals(appearance.boundKeyColor(), saved.boundKeyColor());
        assertEquals(appearance.conflictKeyColor(), saved.conflictKeyColor());
        assertEquals(appearance.unboundKeyColor(), saved.unboundKeyColor());
        assertEquals(appearance.mouseBodyColor(), saved.mouseBodyColor());
    }

    @Test
    void legacyThemeConstructorUsesDefaultMouseColorAndApplyingThemeSetsCapturedColor() {
        String id = UUID.randomUUID().toString();
        SavedTheme legacy = theme(id, "Legacy", 0x123456, 40);
        assertEquals(0x30363B, legacy.mouseBodyColor());

        UiAppearance custom = UiAppearance.defaults().withMouseBodyColor(0xABCDEF);
        SavedTheme saved = SavedTheme.capture("Custom", custom);
        assertEquals(0xABCDEF, saved.applyTo(UiAppearance.defaults()).mouseBodyColor());

        KeyboardBottomRow row = new KeyboardBottomRow(java.util.List.of("key.keyboard.fn", "key.keyboard.space"), 21);
        UiAppearance target = UiAppearance.defaults().withMouseScale(71).withKeyboardPresentation(
                KeyboardPresentation.defaults().withBottomRow(row));
        assertEquals(71, saved.applyTo(target).mouseScale());
        assertEquals(row, saved.applyTo(target).keyboardPresentation().bottomRow());
    }

    @Test
    void rejectsInvalidNamesColorsAndOpacity() {
        String id = UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class, () -> theme(id, "  ", 0, 35));
        assertThrows(IllegalArgumentException.class, () -> theme(id, "x".repeat(65), 0, 35));
        assertThrows(IllegalArgumentException.class, () -> theme(id, "Theme", -1, 35));
        assertThrows(IllegalArgumentException.class, () -> theme(id, "Theme", 0x1000000, 35));
        assertThrows(IllegalArgumentException.class, () -> theme(id, "Theme", 0, 14));
        assertThrows(IllegalArgumentException.class, () -> theme(id, "Theme", 0, 91));
    }

    private static SavedTheme theme(String id, String name, int color, int opacity) {
        return new SavedTheme(id, name, color, color, opacity, color, color, color);
    }
}
