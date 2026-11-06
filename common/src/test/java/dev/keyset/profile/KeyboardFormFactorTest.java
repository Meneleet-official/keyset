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
    void missingOrUnknownIdsFallBackToAutomaticSize() {
        assertEquals(KeyboardFormFactor.AUTO, KeyboardFormFactor.fromId(null));
        assertEquals(KeyboardFormFactor.AUTO, KeyboardFormFactor.fromId("future-size"));
        assertEquals(KeyboardFormFactor.FULL, KeyboardFormFactor.AUTO.resolve(1000, 400));
        assertEquals(KeyboardFormFactor.COMPACT_60, KeyboardFormFactor.AUTO.resolve(200, 400));
        assertEquals(KeyboardFormFactor.TKL, KeyboardFormFactor.fromId(" TKL "));
    }
}
