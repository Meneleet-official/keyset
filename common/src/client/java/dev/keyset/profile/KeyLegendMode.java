package dev.keyset.profile;

import java.util.Locale;

/** How a physical key's legend is rendered on the on-screen keyboard. */
public enum KeyLegendMode {
    TEXT("text", "keyset.legend_mode.text"),
    ICON("icon", "keyset.legend_mode.icon"),
    ICON_TEXT("icon_text", "keyset.legend_mode.icon_text");

    private final String id;
    private final String translationKey;

    KeyLegendMode(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String id() { return id; }
    public String translationKey() { return translationKey; }

    public static KeyLegendMode fromId(String id) {
        if (id == null) return TEXT;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (KeyLegendMode mode : values()) if (mode.id.equals(normalized)) return mode;
        return TEXT;
    }
}
