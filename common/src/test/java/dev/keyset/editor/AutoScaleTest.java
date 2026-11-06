package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.keyset.profile.KeyboardFormFactor;
import dev.keyset.profile.UiAppearance;
import org.junit.jupiter.api.Test;

class AutoScaleTest {
    @Test
    void autoScaleSurvivesAppearanceValidation() {
        UiAppearance appearance = UiAppearance.defaults().withKeyboardScale(UiAppearance.AUTO_SCALE)
            .withMouseScale(UiAppearance.AUTO_SCALE);
        assertEquals(UiAppearance.AUTO_SCALE, appearance.keyboardScale());
        assertEquals(UiAppearance.AUTO_SCALE, appearance.mouseScale());
        assertEquals(60, UiAppearance.defaults().withKeyboardScale(10).keyboardScale());
    }

    @Test
    void autoKeyboardStaysComfortableOnHugeAreasButFillsSmallOnes() {
        var language = KeyboardLayouts.forLanguage("en_us");
        var huge = KeyboardDiagramLayout.forArea(0, 0, 4000, 2000, 0, KeyboardFormFactor.FULL, language);
        var full = KeyboardDiagramLayout.forArea(0, 0, 4000, 2000, 100, KeyboardFormFactor.FULL, language);
        assertTrue(huge.bounds().width() < full.bounds().width());
        var small = KeyboardDiagramLayout.forArea(0, 0, 480, 200, 0, KeyboardFormFactor.FULL, language);
        var fit = KeyboardDiagramLayout.forArea(0, 0, 480, 200, 100, KeyboardFormFactor.FULL, language);
        assertEquals(fit.bounds(), small.bounds());
    }
}
