package dev.keyset.profile;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyboardBottomRowTest {
    @Test
    void defaultsToAutomaticSpaceWidthAndNoCustomOrder() {
        assertEquals(new KeyboardBottomRow(List.of(), 0), KeyboardBottomRow.defaults());
    }

    @Test
    void clampsSpaceWidthAndKeepsZeroAsAutomatic() {
        assertEquals(0, KeyboardBottomRow.defaults().withSpaceWidth(0).spaceWidth());
        assertEquals(8, KeyboardBottomRow.defaults().withSpaceWidth(-1).spaceWidth());
        assertEquals(8, KeyboardBottomRow.defaults().withSpaceWidth(1).spaceWidth());
        assertEquals(32, KeyboardBottomRow.defaults().withSpaceWidth(40).spaceWidth());
    }

    @Test
    void orderIsAnImmutableUniqueListOfSupportedPhysicalInputs() {
        List<String> supplied = new ArrayList<>(List.of(
                "key.keyboard.left.win", "key.keyboard.space", "key.keyboard.fn",
                "key.keyboard.left.win", "key.keyboard.a", "key.mouse.left", "bad"));
        supplied.add(null);

        KeyboardBottomRow row = new KeyboardBottomRow(supplied, 16);
        supplied.clear();

        assertEquals(List.of("key.keyboard.left.win", "key.keyboard.space", "key.keyboard.fn"), row.order());
        assertThrows(UnsupportedOperationException.class, () -> row.order().add("key.keyboard.right.alt"));
    }

    @Test
    void presentationCopiesPreserveBottomRow() {
        KeyboardBottomRow row = new KeyboardBottomRow(List.of("key.keyboard.fn", "key.keyboard.space"), 18);
        KeyboardPresentation presentation = KeyboardPresentation.defaults().withBottomRow(row);

        assertEquals(row, presentation.withMode("key.keyboard.escape", KeyLegendMode.ICON).bottomRow());
        assertEquals(row, presentation.withPlatform(KeyboardPlatform.MAC).bottomRow());
    }
}
