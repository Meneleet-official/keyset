package dev.keyset.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** A key assignment and its modifier keys, independent of Minecraft classes. */
public record Binding(
        String key,
        boolean ctrl,
        boolean shift,
        boolean alt,
        boolean command,
        List<String> extraKeys,
        String nativeSettings) {
    private static final String UNKNOWN_KEY = "key.keyboard.unknown";

    public Binding {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("A binding key cannot be blank");
        }

        Objects.requireNonNull(extraKeys, "extraKeys");
        LinkedHashSet<String> normalizedExtraKeys = new LinkedHashSet<>();
        for (String extraKey : extraKeys) {
            Objects.requireNonNull(extraKey, "extra key");
            if (extraKey.isBlank()) {
                throw new IllegalArgumentException("An extra binding key cannot be blank");
            }
            if (!isUnknown(extraKey) && !extraKey.equals(key)) {
                normalizedExtraKeys.add(extraKey);
            }
        }

        if (isUnknown(key)) {
            ctrl = false;
            shift = false;
            alt = false;
            command = false;
            normalizedExtraKeys.clear();
        }
        extraKeys = List.copyOf(normalizedExtraKeys);
    }

    public Binding(String key, boolean ctrl, boolean shift, boolean alt) {
        this(key, ctrl, shift, alt, false);
    }

    public Binding(String key, boolean ctrl, boolean shift, boolean alt, boolean command) {
        this(key, ctrl, shift, alt, command, List.of(), null);
    }

    public Binding(String key, boolean ctrl, boolean shift, boolean alt, boolean command, List<String> extraKeys) {
        this(key, ctrl, shift, alt, command, extraKeys, null);
    }

    /** Returns the primary input followed by extra physical inputs, without unknown keys. */
    public List<String> keys() {
        if (isUnknown(key)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(extraKeys.size() + 1);
        keys.add(key);
        keys.addAll(extraKeys);
        return List.copyOf(keys);
    }

    private static boolean isUnknown(String key) {
        return UNKNOWN_KEY.equals(key);
    }
}
