package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.*;
import dev.keyset.profile.KeyboardFormFactor;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class KeycapSymbolsTest {
    @Test
    void physicalSymbolsLeaveLettersNumbersAndFunctionKeysUntouched() {
        for (String key : new String[] {"q", "1", "f1", "f12", "minus", "keypad.0"}) {
            assertTrue(KeycapSymbols.forInput("key.keyboard." + key).isEmpty());
        }
        assertTrue(KeycapSymbols.forInput(null).isEmpty());
        assertTrue(KeycapSymbols.forInput("key.mouse.left").isEmpty());
        assertSame(KeycapSymbols.forInput("key.keyboard.left.shift").orElseThrow(),
            KeycapSymbols.forInput("key.keyboard.right.shift").orElseThrow());
        assertSame(KeycapSymbols.forInput("key.keyboard.enter").orElseThrow(),
            KeycapSymbols.forInput("key.keyboard.keypad.enter").orElseThrow());
    }

    @Test
    void serviceKeysHaveNonemptyPixelArtThatFitsTheirFacesAndKeepsTheStateColor() {
        for (var form : KeyboardFormFactor.values()) {
            var board = KeyboardDiagramLayout.forArea(0, 0, 480, 180, 100,
                form, KeyboardLayouts.forLanguage("ru_ru"));
            for (var key : board.keys()) {
                var symbol = KeycapSymbols.forInput(key.input());
                if (symbol.isEmpty()) continue;
                var icon = symbol.get();
                var pixels = new HashSet<String>();
                icon.draw((x, y, right, bottom, color) -> {
                    assertEquals(0xff778899, color);
                    assertTrue(x >= 0 && y >= 0 && right <= icon.width() && bottom <= icon.height());
                    assertTrue(right > x && bottom == y + 1);
                    for (int px = x; px < right; px++) assertTrue(pixels.add(px + "," + y));
                }, 0xff778899);
                int expected = 0;
                for (String row : icon.rows()) {
                    assertEquals(icon.width(), row.length());
                    expected += (int) row.chars().filter(c -> c == '#').count();
                }
                assertTrue(expected > 0);
                assertEquals(expected, pixels.size());
                var r = key.bounds();
                assertTrue(KeycapLabelLayout.fit(KeycapPainter.faceBounds(r.x(), r.y(), r.width(), r.height()),
                    icon.width(), icon.height(), .3f).isPresent(), key.input());
            }
        }
    }
}
