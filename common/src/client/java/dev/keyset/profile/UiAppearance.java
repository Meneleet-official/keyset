package dev.keyset.profile;

/** User-facing appearance settings for the action keyboard. */
public record UiAppearance(
        int keyboardScale,
        int rowHeight,
        boolean inputOnRight,
        int accentColor,
        int panelColor,
        int panelOpacity) {

    private static final UiAppearance DEFAULTS = new UiAppearance(100, 25, false, 0x94E4D3, 0x000000, 35);

    public UiAppearance {
        keyboardScale = clamp(keyboardScale, 60, 100);
        rowHeight = clamp(rowHeight, 18, 32);
        accentColor &= 0xFFFFFF;
        panelColor &= 0xFFFFFF;
        panelOpacity = clamp(panelOpacity, 15, 90);
    }

    public static UiAppearance defaults() {
        return DEFAULTS;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
