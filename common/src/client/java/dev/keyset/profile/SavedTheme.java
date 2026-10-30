package dev.keyset.profile;

import java.util.Objects;
import java.util.UUID;

/** A named, portable snapshot of the keyboard palette and panel opacity. */
public record SavedTheme(
        String id,
        String name,
        int accentColor,
        int panelColor,
        int panelOpacity,
        int boundKeyColor,
        int conflictKeyColor,
        int unboundKeyColor,
        int mouseBodyColor) {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int DEFAULT_MOUSE_BODY_COLOR = 0x30363B;

    /** Keeps source compatibility with the former eight-argument theme constructor. */
    public SavedTheme(String id, String name, int accentColor, int panelColor, int panelOpacity,
                      int boundKeyColor, int conflictKeyColor, int unboundKeyColor) {
        this(id, name, accentColor, panelColor, panelOpacity, boundKeyColor, conflictKeyColor,
                unboundKeyColor, DEFAULT_MOUSE_BODY_COLOR);
    }

    public SavedTheme {
        id = validateId(id);
        name = normalizeName(name);
        requireColor(accentColor, "accentColor");
        requireColor(panelColor, "panelColor");
        requireColor(boundKeyColor, "boundKeyColor");
        requireColor(conflictKeyColor, "conflictKeyColor");
        requireColor(unboundKeyColor, "unboundKeyColor");
        requireColor(mouseBodyColor, "mouseBodyColor");
        if (panelOpacity < 15 || panelOpacity > 90) {
            throw new IllegalArgumentException("panelOpacity must be between 15 and 90");
        }
    }

    /** Captures the palette and opacity without copying layout preferences. */
    public static SavedTheme capture(String name, UiAppearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        return new SavedTheme(UUID.randomUUID().toString(), name,
                appearance.accentColor(), appearance.panelColor(), appearance.panelOpacity(),
                appearance.boundKeyColor(), appearance.conflictKeyColor(), appearance.unboundKeyColor(),
                appearance.mouseBodyColor());
    }

    /** Applies this theme while retaining the target appearance's layout preferences. */
    public UiAppearance applyTo(UiAppearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        return new UiAppearance(appearance.keyboardScale(), appearance.rowHeight(), appearance.inputOnRight(),
                accentColor, panelColor, panelOpacity, boundKeyColor, conflictKeyColor, unboundKeyColor,
                appearance.keyboardFormFactor(), appearance.keyboardPresentation(), mouseBodyColor,
                appearance.mouseScale());
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "id");
        try {
            UUID parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("id must be a canonical UUID", exception);
        }
        return id;
    }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException("A theme name cannot be empty");
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A theme name cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private static void requireColor(int color, String field) {
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException(field + " must be an RGB color between 0 and 0xFFFFFF");
        }
    }
}
