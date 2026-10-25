package dev.keyset.profile;

/** Pure color palette calculations for the action editor's translucent appearance settings. */
public final class AppearancePalette {
    private static final int DARK_BACKDROP = 0x202020;
    private static final double MIN_TEXT_CONTRAST = 4.6;

    private final UiAppearance appearance;
    private final int effectiveSurface;
    private final int text;
    private final int muted;
    private final int red;
    private final int panelLight;
    private final int conflictSurface;
    private final int conflictHover;
    private final int control;
    private final int hover;
    private final int selected;
    private final int row;
    private final int rowAlternate;
    private final int keyShadow;
    private final int keyFace;
    private final int keyEdge;
    private final int accentText;

    private AppearancePalette(UiAppearance appearance) {
        this.appearance = appearance;
        effectiveSurface = composite(DARK_BACKDROP, appearance.panelColor(), appearance.panelOpacity() / 100.0);
        text = contrastText(effectiveSurface);
        muted = readableForeground(effectiveSurface, text, 5.0);
        red = readableRed(effectiveSurface, text);

        panelLight = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.08), text, 0x78);
        conflictSurface = readableOverlay(effectiveSurface,
                mix(effectiveSurface, red & 0xFFFFFF, 0.18), red, 0x60);
        conflictHover = readableOverlay(effectiveSurface,
                mix(effectiveSurface, red & 0xFFFFFF, 0.26), red, 0xA0);
        control = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.10), text, 0x90);
        hover = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.18), text, 0xE0);
        selected = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.28), text, 0xA0);
        row = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.08), text, 0x30);
        rowAlternate = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.05), text, 0x1A);
        keyShadow = argb(0xD6, mix(effectiveSurface, 0x000000, 0.72));
        keyFace = readableOverlay(effectiveSurface,
                mix(effectiveSurface, appearance.accentColor(), 0.12), text, 0xE0);
        keyEdge = opaque(mix(effectiveSurface, appearance.accentColor(), 0.64));

        int selectedSurface = composite(effectiveSurface, selected & 0xFFFFFF, alpha(selected) / 255.0);
        accentText = contrastText(selectedSurface);
    }

    public static AppearancePalette from(UiAppearance appearance) {
        if (appearance == null) throw new IllegalArgumentException("appearance is required");
        return new AppearancePalette(appearance);
    }

    public int panel() {
        return argb(Math.round(appearance.panelOpacity() * 2.55f), appearance.panelColor());
    }

    public int panelLight() { return panelLight; }
    public int border() { return argb(82, mix(appearance.accentColor(), 0xFFFFFF, 0.22)); }
    public int text() { return text; }
    public int muted() { return muted; }
    public int accent() { return opaque(appearance.accentColor()); }
    public int red() { return red; }
    public int conflictSurface() { return conflictSurface; }
    public int conflictHover() { return conflictHover; }
    public int accentText() { return accentText; }
    public int control() { return control; }
    public int hover() { return hover; }
    public int selected() { return selected; }
    public int row() { return row; }
    public int rowAlternate() { return rowAlternate; }
    public int keyShadow() { return keyShadow; }
    public int keyFace() { return keyFace; }
    public int keyEdge() { return keyEdge; }
    public int effectiveSurface() { return effectiveSurface; }

    /** Light buttons can also sit outside a card; retain their tested surface there. */
    public int widgetSurface(int overlay) {
        if (text != 0xFF000000) return overlay;
        return opaque(composite(effectiveSurface, overlay, alpha(overlay) / 255.0));
    }

    /** Composite an ARGB overlay on the opaque panel surface used for contrast calculations. */
    public static int composite(int base, int overlay, double opacity) {
        double amount = Math.max(0.0, Math.min(1.0, opacity));
        int red = channel(base, overlay, 16, amount);
        int green = channel(base, overlay, 8, amount);
        int blue = channel(base, overlay, 0, amount);
        return (red << 16) | (green << 8) | blue;
    }

    public static double contrastRatio(int first, int second) {
        double left = luminance(first);
        double right = luminance(second);
        return (Math.max(left, right) + 0.05) / (Math.min(left, right) + 0.05);
    }

    private static int readableOverlay(int surface, int color, int foreground, int opacity) {
        int amount = Math.max(0, Math.min(255, opacity));
        int visible = composite(surface, color, amount / 255.0);
        if (contrastRatio(visible, foreground) >= 4.5) return argb(amount, color);

        int endpoint = contrastRatio(0x000000, foreground) >= contrastRatio(0xFFFFFF, foreground)
                ? 0x000000 : 0xFFFFFF;
        for (int step = 1; step <= 100; step++) {
            int adjusted = mix(color, endpoint, step / 100.0);
            visible = composite(surface, adjusted, amount / 255.0);
            if (contrastRatio(visible, foreground) >= 4.5) return argb(amount, adjusted);
        }
        // If even a fully tinted overlay cannot meet contrast at this alpha, use the readable
        // endpoint directly rather than letting a custom hue make labels disappear.
        return opaque(endpoint);
    }

    private static int readableRed(int surface, int foreground) {
        int color = foreground == 0xFFFFFFFF ? 0xFF8A8A : 0xB00020;
        int endpoint = foreground == 0xFFFFFFFF ? 0xFFFFFF : 0x000000;
        for (int step = 0; step <= 100; step++) {
            int candidate = mix(color, endpoint, step / 100.0);
            if (contrastRatio(candidate, surface) >= 4.5) return opaque(candidate);
        }
        return opaque(endpoint);
    }

    private static int readableForeground(int surface, int foreground, double ratio) {
        int endpoint = foreground == 0xFFFFFFFF ? 0xFFFFFF : 0x000000;
        for (int step = 1; step <= 100; step++) {
            int candidate = mix(surface, endpoint, step / 100.0);
            if (contrastRatio(candidate, surface) >= ratio) return opaque(candidate);
        }
        return opaque(endpoint);
    }

    private static int contrastText(int background) {
        int black = 0x000000;
        int white = 0xFFFFFF;
        return opaque(contrastRatio(background, black) >= contrastRatio(background, white) ? black : white);
    }

    private static int mix(int first, int second, double amount) {
        double mix = Math.max(0.0, Math.min(1.0, amount));
        int red = channel(first, second, 16, mix);
        int green = channel(first, second, 8, mix);
        int blue = channel(first, second, 0, mix);
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(int first, int second, int shift, double amount) {
        int left = (first >>> shift) & 0xFF;
        int right = (second >>> shift) & 0xFF;
        return (int) Math.round(left + (right - left) * amount);
    }

    private static double luminance(int color) {
        double red = linear((color >>> 16) & 0xFF);
        double green = linear((color >>> 8) & 0xFF);
        double blue = linear(color & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int alpha(int color) { return (color >>> 24) & 0xFF; }
    private static int argb(int alpha, int color) { return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF); }
    private static int opaque(int color) { return 0xFF000000 | (color & 0xFFFFFF); }
}
