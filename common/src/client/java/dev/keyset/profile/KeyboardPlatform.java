package dev.keyset.profile;

import java.util.Locale;

/** Physical keyboard platform used for the laptop and Mac diagram variants. */
public enum KeyboardPlatform {
    AUTO("auto", "keyset.keyboard_platform.auto"),
    PC("pc", "keyset.keyboard_platform.pc"),
    LAPTOP("laptop", "keyset.keyboard_platform.laptop"),
    MAC("mac", "keyset.keyboard_platform.mac");

    private final String id;
    private final String translationKey;

    KeyboardPlatform(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String id() { return id; }
    public String translationKey() { return translationKey; }

    public KeyboardPlatform resolved() {
        return this == AUTO ? forOperatingSystem(System.getProperty("os.name", "")) : this;
    }

    public static KeyboardPlatform forOperatingSystem(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("mac") || normalized.contains("darwin") ? MAC : PC;
    }

    public static KeyboardPlatform fromId(String id) {
        if (id == null) return AUTO;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (KeyboardPlatform platform : values()) if (platform.id.equals(normalized)) return platform;
        return AUTO;
    }
}
