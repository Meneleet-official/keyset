package dev.keyset.editor;

import java.util.List;

/** Physical four-column keypad. Spans keep tall keys and the wide zero aligned. */
public final class NumpadLayout {
    private NumpadLayout() {}

    public record Key(String input, String label, int column, int row, int columns, int rows) {
        public int x(int unit, int gap) { return column * (unit + gap); }
        public int y(int step) { return row * step; }
        public int width(int unit, int gap) { return columns * unit + (columns - 1) * gap; }
        public int height(int step, int gap) { return rows * step - gap; }
    }

    public static List<Key> keys() {
        return List.of(
            key("num.lock", "NM", 0, 0),
            key("keypad.divide", "/", 1, 0),
            key("keypad.multiply", "*", 2, 0),
            key("keypad.subtract", "-", 3, 0),
            key("keypad.7", "7", 0, 1), key("keypad.8", "8", 1, 1), key("keypad.9", "9", 2, 1),
            new Key("key.keyboard.keypad.add", "+", 3, 1, 1, 2),
            key("keypad.4", "4", 0, 2), key("keypad.5", "5", 1, 2), key("keypad.6", "6", 2, 2),
            key("keypad.1", "1", 0, 3), key("keypad.2", "2", 1, 3), key("keypad.3", "3", 2, 3),
            new Key("key.keyboard.keypad.enter", "EN", 3, 3, 1, 2),
            new Key("key.keyboard.keypad.0", "0", 0, 4, 2, 1), key("keypad.period", ".", 2, 4));
    }

    private static Key key(String input, String label, int column, int row) {
        return new Key("key.keyboard." + input, label, column, row, 1, 1);
    }
}
