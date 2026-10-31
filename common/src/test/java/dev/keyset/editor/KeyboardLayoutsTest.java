package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyboardLayoutsTest {
    @Test
    void minecraftLanguageSelectsNativeKeycapsWithoutChangingPhysicalKeys() {
        var russian = KeyboardLayouts.forLanguage("ru_ru");
        assertEquals("ru", russian.id());
        assertEquals("Й", russian.label("q", "?"));
        assertEquals("Ф", russian.label("a", "?"));
        assertEquals("Ч", russian.label("x", "?"));
        assertEquals("Ё", russian.label("grave", "?"));
        assertEquals("Х", russian.label("left_bracket", "?"));
        assertEquals("І", KeyboardLayouts.forLanguage("uk_ua").label("s", "?"));
        assertEquals("Ї", KeyboardLayouts.forLanguage("uk_ua").label("right_bracket", "?"));
        assertEquals("A", KeyboardLayouts.forLanguage("fr_fr").label("q", "?"));
        assertEquals("M", KeyboardLayouts.forLanguage("fr_fr").label("semicolon", "?"));
        assertEquals("Z", KeyboardLayouts.forLanguage("de_de").label("y", "?"));
        assertEquals("Ü", KeyboardLayouts.forLanguage("de_de").label("left_bracket", "?"));
    }

    @Test
    void regionalVariantsAndLocaleAliasesResolveBeforeTheFallback() {
        assertEquals("en-gb", KeyboardLayouts.forLanguage("EN_gB").id());
        assertEquals("fr-ca", KeyboardLayouts.forLanguage("fr_CA").id());
        assertEquals("pt-pt", KeyboardLayouts.forLanguage("pt_pt").id());
        assertEquals("pt", KeyboardLayouts.forLanguage("pt_br").id());
        assertEquals("nb", KeyboardLayouts.forLanguage("nn_no").id());
        assertEquals("he", KeyboardLayouts.forLanguage("iw_il").id());
        assertEquals("en", KeyboardLayouts.forLanguage("custom_language").id());
        assertEquals("en", KeyboardLayouts.forLanguage(null).id());
    }

    @Test
    void offlineCatalogAndAllManualOverridesContainACompletePhysicalKeyboard() {
        assertTrue(KeyboardLayouts.size() >= 100);
        for (int i = 0; i < KeyboardLayouts.MANUAL.size(); i++) {
            var layout = KeyboardLayouts.manual(i);
            assertNotNull(layout);
            assertEquals(47, layout.labels().size(), layout.id());
            assertEquals("F12", layout.label("not-a-character-key", "F12"));
        }
        assertEquals("'", KeyboardLayouts.forLanguage("en-dvorak").label("q", "?"));
        assertEquals("ß", KeyboardLayouts.forLanguage("de_de").label("minus", "?"));
    }
}
