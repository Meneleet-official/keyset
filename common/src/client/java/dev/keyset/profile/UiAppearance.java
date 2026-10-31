package dev.keyset.profile;

import java.util.Objects;

/** User-facing appearance settings for the action keyboard. */
public record UiAppearance(
        int keyboardScale,
        int rowHeight,
        boolean inputOnRight,
        int accentColor,
        int panelColor,
        int panelOpacity,
        int boundKeyColor,
        int conflictKeyColor,
        int unboundKeyColor,
        KeyboardFormFactor keyboardFormFactor,
        KeyboardPresentation keyboardPresentation,
        int mouseBodyColor,
        int mouseScale) {

    private static final int DEFAULT_ACCENT_COLOR = 0x94E4D3;
    private static final int DEFAULT_PANEL_COLOR = 0x000000;
    private static final int DEFAULT_BOUND_KEY_COLOR = 0x819D96;
    private static final int DEFAULT_CONFLICT_KEY_COLOR = 0xD77979;
    private static final int DEFAULT_UNBOUND_KEY_COLOR = 0x546760;
    private static final int DEFAULT_MOUSE_BODY_COLOR = 0x30363B;

    private static final UiAppearance DEFAULTS = new UiAppearance(
            100, 25, false, DEFAULT_ACCENT_COLOR, DEFAULT_PANEL_COLOR, 35,
            DEFAULT_BOUND_KEY_COLOR, DEFAULT_CONFLICT_KEY_COLOR, DEFAULT_UNBOUND_KEY_COLOR);

    /** Retains source compatibility for callers that only specify the original appearance fields. */
    public UiAppearance(int keyboardScale, int rowHeight, boolean inputOnRight,
                        int accentColor, int panelColor, int panelOpacity) {
        this(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                DEFAULT_BOUND_KEY_COLOR, DEFAULT_CONFLICT_KEY_COLOR, DEFAULT_UNBOUND_KEY_COLOR);
    }

    public UiAppearance(int keyboardScale, int rowHeight, boolean inputOnRight,
                        int accentColor, int panelColor, int panelOpacity,
                        int boundKeyColor, int conflictKeyColor, int unboundKeyColor) {
        this(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, KeyboardFormFactor.FULL,
                KeyboardPresentation.defaults(), DEFAULT_MOUSE_BODY_COLOR, 100);
    }

    /** Keeps source compatibility for callers using the former canonical constructor. */
    public UiAppearance(int keyboardScale, int rowHeight, boolean inputOnRight,
                        int accentColor, int panelColor, int panelOpacity,
                        int boundKeyColor, int conflictKeyColor, int unboundKeyColor,
                        KeyboardFormFactor keyboardFormFactor, KeyboardPresentation keyboardPresentation) {
        this(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, keyboardFormFactor, keyboardPresentation,
                DEFAULT_MOUSE_BODY_COLOR, 100);
    }

    /** Keeps source compatibility for callers specifying the physical form factor. */
    public UiAppearance(int keyboardScale, int rowHeight, boolean inputOnRight,
                        int accentColor, int panelColor, int panelOpacity,
                        int boundKeyColor, int conflictKeyColor, int unboundKeyColor,
                        KeyboardFormFactor keyboardFormFactor) {
        this(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, keyboardFormFactor,
                KeyboardPresentation.defaults(), DEFAULT_MOUSE_BODY_COLOR, 100);
    }

    /** Keeps source compatibility with callers using the former canonical constructor. */
    public UiAppearance(int keyboardScale, int rowHeight, boolean inputOnRight,
                        int accentColor, int panelColor, int panelOpacity,
                        int boundKeyColor, int conflictKeyColor, int unboundKeyColor,
                        KeyboardFormFactor keyboardFormFactor, KeyboardPresentation keyboardPresentation,
                        int mouseBodyColor) {
        this(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, keyboardFormFactor, keyboardPresentation,
                mouseBodyColor, 100);
    }

    public UiAppearance {
        keyboardScale = clamp(keyboardScale, 60, 100);
        rowHeight = clamp(rowHeight, 18, 32);
        accentColor &= 0xFFFFFF;
        panelColor &= 0xFFFFFF;
        panelOpacity = clamp(panelOpacity, 15, 90);
        boundKeyColor &= 0xFFFFFF;
        conflictKeyColor &= 0xFFFFFF;
        unboundKeyColor &= 0xFFFFFF;
        mouseBodyColor &= 0xFFFFFF;
        mouseScale = clamp(mouseScale, 60, 100);
        if (keyboardFormFactor == null) keyboardFormFactor = KeyboardFormFactor.FULL;
        keyboardPresentation = Objects.requireNonNullElseGet(keyboardPresentation, KeyboardPresentation::defaults);
    }

    public static UiAppearance defaults() {
        return DEFAULTS;
    }

    public UiAppearance withKeyboardFormFactor(KeyboardFormFactor formFactor) {
        return new UiAppearance(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, formFactor, keyboardPresentation,
                mouseBodyColor, mouseScale);
    }

    public UiAppearance withKeyboardPresentation(KeyboardPresentation presentation) {
        return new UiAppearance(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, keyboardFormFactor, presentation,
                mouseBodyColor, mouseScale);
    }

    public UiAppearance withMouseBodyColor(int color) {
        return new UiAppearance(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, keyboardFormFactor, keyboardPresentation,
                color, mouseScale);
    }

    public UiAppearance withMouseScale(int value) {
        return new UiAppearance(keyboardScale, rowHeight, inputOnRight, accentColor, panelColor, panelOpacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, keyboardFormFactor, keyboardPresentation,
                mouseBodyColor, value);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
