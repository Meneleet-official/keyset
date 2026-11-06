package dev.keyset.profile;

import java.util.Locale;

/** Physical keyboard size used for the input diagram. */
public enum KeyboardFormFactor {
    AUTO("auto", "Auto"),
    FULL("full", "100%"),
    TKL("tkl", "TKL"),
    COMPACT_75("75", "75%"),
    COMPACT_60("60", "60%");

    private final String id;
    private final String label;

    KeyboardFormFactor(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    /** Picks the largest keyboard whose keys stay comfortably readable in the given width. */
    public KeyboardFormFactor resolve(int availableWidth, int availableHeight) {
        if (this != AUTO) return this;
        int pitch = 15;
        if (availableWidth >= 24 * pitch) return FULL;
        if (availableWidth >= 19 * pitch) return TKL;
        if (availableWidth >= 16 * pitch) return COMPACT_75;
        return COMPACT_60;
    }

    /** Unknown or missing stored ids use the original full-size keyboard. */
    public static KeyboardFormFactor fromId(String id) {
        if (id == null) return AUTO;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (KeyboardFormFactor value : values()) {
            if (value.id.equals(normalized)) return value;
        }
        return AUTO;
    }
}
