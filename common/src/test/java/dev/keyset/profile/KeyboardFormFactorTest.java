package dev.keyset.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class KeyboardFormFactorTest {
    @Test
    void idsAndLabelsCoverTheSupportedKeyboardSizes() {
        assertEquals("100%", KeyboardFormFactor.FULL.label());
        assertEquals("TKL", KeyboardFormFactor.TKL.label());
        assertEquals("75%", KeyboardFormFactor.COMPACT_75.label());
        assertEquals("60%", KeyboardFormFactor.COMPACT_60.label());
        for (KeyboardFormFactor formFactor : KeyboardFormFactor.values()) {
            assertEquals(formFactor, KeyboardFormFactor.fromId(formFactor.id()));
        }
    }

    @Test
    void missingOrUnknownIdsFallBackToFullSize() {
        assertEquals(KeyboardFormFactor.FULL, KeyboardFormFactor.fromId(null));
        assertEquals(KeyboardFormFactor.FULL, KeyboardFormFactor.fromId("future-size"));
        assertEquals(KeyboardFormFactor.TKL, KeyboardFormFactor.fromId(" TKL "));
    }
}
