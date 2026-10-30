package dev.keyset.profile;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Platform, per-physical-key legend choices, and keyboard bottom-row geometry. */
public record KeyboardPresentation(
        KeyboardPlatform platform,
        Map<String, KeyLegendMode> overrides,
        KeyboardBottomRow bottomRow) {

    /** Keeps source compatibility with the former two-field presentation. */
    public KeyboardPresentation(KeyboardPlatform platform, Map<String, KeyLegendMode> overrides) {
        this(platform, overrides, KeyboardBottomRow.defaults());
    }

    public KeyboardPresentation {
        platform = Objects.requireNonNullElse(platform, KeyboardPlatform.AUTO);
        bottomRow = Objects.requireNonNullElseGet(bottomRow, KeyboardBottomRow::defaults);
        TreeMap<String, KeyLegendMode> sanitized = new TreeMap<>();
        if (overrides != null) overrides.forEach((input, mode) -> {
            if (isPhysicalInput(input) && mode != null && mode != defaultMode(input)) sanitized.put(input, mode);
        });
        overrides = Map.copyOf(sanitized);
    }

    public static KeyboardPresentation defaults() {
        return new KeyboardPresentation(KeyboardPlatform.AUTO, Map.of());
    }

    public KeyLegendMode modeFor(String input) {
        if (input == null) return KeyLegendMode.TEXT;
        return overrides.getOrDefault(input, defaultMode(input));
    }

    public KeyboardPresentation withMode(String input, KeyLegendMode mode) {
        if (!isPhysicalInput(input)) throw new IllegalArgumentException("Expected a physical key input");
        Objects.requireNonNull(mode, "mode");
        TreeMap<String, KeyLegendMode> updated = new TreeMap<>(overrides);
        if (mode == defaultMode(input)) updated.remove(input);
        else updated.put(input, mode);
        return new KeyboardPresentation(platform, updated, bottomRow);
    }

    public KeyboardPresentation withPlatform(KeyboardPlatform value) {
        return new KeyboardPresentation(value, overrides, bottomRow);
    }

    public KeyboardPresentation withBottomRow(KeyboardBottomRow value) {
        return new KeyboardPresentation(platform, overrides, value);
    }

    private static KeyLegendMode defaultMode(String input) {
        String name = input.startsWith("key.keyboard.") ? input.substring("key.keyboard.".length()) : input;
        return switch (name) {
            case "escape", "left.control", "right.control", "left.alt", "right.alt" -> KeyLegendMode.TEXT;
            case "left.shift", "right.shift" -> KeyLegendMode.ICON_TEXT;
            default -> KeyLegendMode.ICON;
        };
    }

    private static boolean isPhysicalInput(String input) {
        return input != null && input.startsWith("key.keyboard.")
            && input.length() > "key.keyboard.".length()
            && input.length() <= 160
            && input.substring("key.keyboard.".length()).matches("[a-z0-9_.-]+");
    }
}
