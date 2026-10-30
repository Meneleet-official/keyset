package dev.keyset.editor;

import dev.keyset.profile.KeyboardPlatform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyboardKeyNamesTest {
    @Test
    void macUsesCommandAndOptionForBothPhysicalSidesWhilePcNamesStayNative() {
        for (String side : new String[] {"left", "right"}) {
            assertEquals("keyset.key_" + side + "_command",
                KeyboardKeyNames.translationKey("key.keyboard." + side + ".win", KeyboardPlatform.MAC));
            assertEquals("keyset.key_" + side + "_option",
                KeyboardKeyNames.translationKey("key.keyboard." + side + ".alt", KeyboardPlatform.MAC));
            for (var platform : new KeyboardPlatform[] {KeyboardPlatform.PC, KeyboardPlatform.LAPTOP}) {
                assertNull(KeyboardKeyNames.translationKey("key.keyboard." + side + ".win", platform));
                assertNull(KeyboardKeyNames.translationKey("key.keyboard." + side + ".alt", platform));
            }
        }
        assertNull(KeyboardKeyNames.translationKey("key.keyboard.a", KeyboardPlatform.MAC));
        assertNull(KeyboardKeyNames.translationKey("key.mouse.left", KeyboardPlatform.MAC));
    }
}
