package dev.keyset.editor;

import dev.keyset.profile.KeyboardPlatform;

/** Display-name overrides only; physical binding identifiers never change. */
public final class KeyboardKeyNames {
    private KeyboardKeyNames() {}
    public static String translationKey(String input, KeyboardPlatform platform) {
        if ("key.keyboard.fn".equals(input)) return "keyset.keyboard_fn";
        if ((platform == null || platform.resolved() != KeyboardPlatform.MAC) || input == null) return null;
        return switch (input) {
            case "key.keyboard.left.win" -> "keyset.key_left_command";
            case "key.keyboard.right.win" -> "keyset.key_right_command";
            case "key.keyboard.left.alt" -> "keyset.key_left_option";
            case "key.keyboard.right.alt" -> "keyset.key_right_option";
            default -> null;
        };
    }
}
