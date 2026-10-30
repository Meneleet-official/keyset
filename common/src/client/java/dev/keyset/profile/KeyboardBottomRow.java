package dev.keyset.profile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Custom order and spacebar width for the keyboard's bottom row. */
public record KeyboardBottomRow(List<String> order, int spaceWidth) {
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "key.keyboard.fn",
            "key.keyboard.left.control", "key.keyboard.right.control",
            "key.keyboard.left.win", "key.keyboard.right.win",
            "key.keyboard.left.alt", "key.keyboard.right.alt",
            "key.keyboard.space");

    public KeyboardBottomRow {
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        if (order != null) {
            for (String input : order) {
                if (input != null && ALLOWED_KEYS.contains(input)) sanitized.add(input);
            }
        }
        order = List.copyOf(sanitized);
        spaceWidth = spaceWidth == 0 ? 0 : Math.max(8, Math.min(32, spaceWidth));
    }

    public static KeyboardBottomRow defaults() {
        return new KeyboardBottomRow(List.of(), 0);
    }

    public KeyboardBottomRow withOrder(List<String> value) {
        return new KeyboardBottomRow(value, spaceWidth);
    }

    public KeyboardBottomRow withSpaceWidth(int value) {
        return new KeyboardBottomRow(order, value);
    }
}
