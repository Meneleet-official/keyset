package dev.keyset.editor;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumpadLayoutTest {
    @Test
    void physicalGridHasNoOverlapsAndCoversAllTwentyCells() {
        var occupied = new HashSet<String>();
        var inputs = new HashSet<String>();
        for (var key : NumpadLayout.keys()) {
            assertTrue(inputs.add(key.input()), "Every physical input is shown once");
            for (int x = key.column(); x < key.column() + key.columns(); x++) {
                for (int y = key.row(); y < key.row() + key.rows(); y++) {
                    assertTrue(x >= 0 && x < 4 && y >= 0 && y < 5);
                    assertTrue(occupied.add(x + ":" + y), "Overlapping keypad cell");
                }
            }
        }
        assertEquals(17, inputs.size());
        assertEquals(20, occupied.size());
        assertTrue(inputs.contains("key.keyboard.keypad.period"));
        assertFalse(inputs.contains("key.keyboard.keypad.decimal"));
    }

    @Test
    void spanningKeysAlignAtEverySupportedSize() {
        for (int unit = 1; unit <= 18; unit++) {
            for (int step = 6; step <= 22; step++) {
                for (var key : NumpadLayout.keys()) {
                    assertTrue(key.width(unit, 2) >= 1);
                    assertTrue(key.height(step, 2) >= 4);
                    assertTrue(key.x(unit, 2) + key.width(unit, 2) <= 4 * unit + 6);
                    assertTrue(key.y(step) + key.height(step, 2) <= 5 * step - 2);
                }
            }
        }
        assertEquals(2, NumpadLayout.keys().stream().filter(k -> k.input().endsWith("add")).findFirst().orElseThrow().rows());
        assertEquals(2, NumpadLayout.keys().stream().filter(k -> k.input().endsWith("enter")).findFirst().orElseThrow().rows());
        assertEquals(2, NumpadLayout.keys().stream().filter(k -> k.input().endsWith(".0")).findFirst().orElseThrow().columns());
    }
}
