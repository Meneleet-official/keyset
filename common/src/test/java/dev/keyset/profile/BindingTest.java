package dev.keyset.profile;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BindingTest {
    @Test
    void keepsChordOrderAndNormalizesDuplicatePhysicalKeys() {
        Binding binding = new Binding(
                "key.keyboard.m",
                false,
                false,
                false,
                false,
                List.of("key.keyboard.c", "key.keyboard.left.shift", "key.keyboard.c", "key.keyboard.m"));

        assertEquals(List.of("key.keyboard.c", "key.keyboard.left.shift"), binding.extraKeys());
        assertEquals(List.of("key.keyboard.m", "key.keyboard.c", "key.keyboard.left.shift"), binding.keys());
        assertThrows(UnsupportedOperationException.class, () -> binding.keys().clear());
    }

    @Test
    void unboundKeyDoesNotRetainModifiersOrChordKeys() {
        Binding binding = new Binding(
                "key.keyboard.unknown",
                true,
                true,
                true,
                true,
                List.of("key.keyboard.c"),
                "{\"native\":true}");

        assertEquals(new Binding("key.keyboard.unknown", false, false, false, false,
                List.of(), "{\"native\":true}"), binding);
        assertEquals(List.of(), binding.keys());
    }

    @Test
    void rejectsNullAndBlankExtraKeys() {
        assertThrows(NullPointerException.class, () -> new Binding(
                "key.keyboard.m", false, false, false, false, Arrays.asList("key.keyboard.c", null)));
        assertThrows(IllegalArgumentException.class, () -> new Binding(
                "key.keyboard.m", false, false, false, false, List.of(" ")));
    }
}
