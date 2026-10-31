package dev.keyset.profile;

import java.util.Objects;

/** A complete, named color palette for the action keyboard appearance. */
public enum AppearancePreset {
    DEFAULT("keyset.appearance_preset_default", 0x94E4D3, 0x000000,
            0x819D96, 0xD77979, 0x546760, 0x30363B),
    OCEAN("keyset.appearance_preset_ocean", 0x78BDF2, 0x101A2C,
            0x79AACD, 0xD77979, 0x53748F, 0x273B52),
    EMBER("keyset.appearance_preset_ember", 0xF2A66F, 0x24150E,
            0xC49A79, 0xD77979, 0x88654E, 0x4A352A),
    PAPER("keyset.appearance_preset_paper", 0x25594C, 0xEEECE4,
            0x3B6051, 0xC74444, 0x8BA295, 0xC4C9CD),
    BLACK("keyset.appearance_preset_black", 12661812, 0,
            3158064, 10044227, 2236962, 3158064);

    private final String translationKey;
    private final int accentColor;
    private final int panelColor;
    private final int boundKeyColor;
    private final int conflictKeyColor;
    private final int unboundKeyColor;
    private final int mouseBodyColor;

    AppearancePreset(String translationKey, int accentColor, int panelColor,
                     int boundKeyColor, int conflictKeyColor, int unboundKeyColor, int mouseBodyColor) {
        this.translationKey = translationKey;
        this.accentColor = accentColor;
        this.panelColor = panelColor;
        this.boundKeyColor = boundKeyColor;
        this.conflictKeyColor = conflictKeyColor;
        this.unboundKeyColor = unboundKeyColor;
        this.mouseBodyColor = mouseBodyColor;
    }

    public String translationKey() { return translationKey; }
    public int accentColor() { return accentColor; }
    public int panelColor() { return panelColor; }
    public int boundKeyColor() { return boundKeyColor; }
    public int conflictKeyColor() { return conflictKeyColor; }
    public int unboundKeyColor() { return unboundKeyColor; }
    public int mouseBodyColor() { return mouseBodyColor; }

    /** Retains layout; Black also applies the supplied theme's 35% panel opacity. */
    public UiAppearance applyTo(UiAppearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        return new UiAppearance(appearance.keyboardScale(), appearance.rowHeight(), appearance.inputOnRight(),
                accentColor, panelColor, this == BLACK ? 35 : appearance.panelOpacity(),
                boundKeyColor, conflictKeyColor, unboundKeyColor, appearance.keyboardFormFactor(),
                appearance.keyboardPresentation(), mouseBodyColor, appearance.mouseScale());
    }

    /** Checks palette colors only; layout, opacity, and manual edits remain independent. */
    public boolean matches(UiAppearance appearance) {
        return appearance != null
                && appearance.accentColor() == accentColor
                && appearance.panelColor() == panelColor
                && appearance.boundKeyColor() == boundKeyColor
                && appearance.conflictKeyColor() == conflictKeyColor
                && appearance.unboundKeyColor() == unboundKeyColor
                && appearance.mouseBodyColor() == mouseBodyColor;
    }

    /** Replaces an exact palette from an earlier release while preserving layout and opacity. */
    public static UiAppearance refreshLegacyPalette(UiAppearance appearance) {
        if (appearance == null) return null;
        for (AppearancePreset preset : values()) {
            if (preset.matchesLegacyPalette(appearance)) return preset.applyTo(appearance);
        }
        return appearance;
    }

    private boolean matchesLegacyPalette(UiAppearance appearance) {
        if (appearance.mouseBodyColor() != 0x30363B) return false;
        return switch (this) {
            case DEFAULT -> hasColors(appearance, 0x94E4D3, 0x000000, 0x8DAFA6, 0xD77979, 0x48515A)
                    || hasColors(appearance, 0x94E4D3, 0x000000, 0x748C86, 0xD77979, 0x5F736D);
            case OCEAN -> hasColors(appearance, 0x78BDF2, 0x101A2C, 0x78BDF2, 0xD77979, 0x4A627B)
                    || hasColors(appearance, 0x78BDF2, 0x101A2C, 0x6F9CBE, 0xD77979, 0x587C98);
            case EMBER -> hasColors(appearance, 0xF2A66F, 0x24150E, 0xF2A66F, 0xD77979, 0x6B4B38)
                    || hasColors(appearance, 0xF2A66F, 0x24150E, 0xB78E70, 0xD77979, 0x927259);
            case PAPER -> hasColors(appearance, 0x25594C, 0xEEECE4, 0x25594C, 0xC74444, 0x747C78)
                    || hasColors(appearance, 0x25594C, 0xEEECE4, 0x5E7B70, 0xC74444, 0x7A9085);
            case BLACK -> false;
        };
    }

    private static boolean hasColors(UiAppearance appearance, int accent, int panel,
                                     int bound, int conflict, int unbound) {
        return appearance.accentColor() == accent
                && appearance.panelColor() == panel
                && appearance.boundKeyColor() == bound
                && appearance.conflictKeyColor() == conflict
                && appearance.unboundKeyColor() == unbound;
    }
}
