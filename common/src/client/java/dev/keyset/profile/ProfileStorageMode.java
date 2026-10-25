package dev.keyset.profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Stores whether this Minecraft installation uses the user's shared profile set. */
public final class ProfileStorageMode {
    private static final String SHARED_KEY = "profiles.shared";

    private final Path file;
    private boolean shared = true;

    public ProfileStorageMode(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public void load() throws IOException {
        shared = SharedSettingsFiles.withFileLock(file, () -> {
            if (!Files.exists(file)) return true;
            Properties values = new Properties();
            try (InputStream input = Files.newInputStream(file)) { values.load(input); }
            String value = values.getProperty(SHARED_KEY);
            if (value == null) return true;
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
            throw new IOException("Invalid profile storage mode in " + file);
        });
    }

    public boolean isShared() {
        return shared;
    }

    /** Saves the new mode atomically and changes memory only when persistence succeeds. */
    public void setShared(boolean value) throws IOException {
        SharedSettingsFiles.withFileLock(file, () -> {
            Properties values = new Properties();
            values.setProperty(SHARED_KEY, Boolean.toString(value));
            SharedSettingsFiles.writeBytesAtomically(file, () -> {
                try (var output = new java.io.ByteArrayOutputStream()) {
                    values.store(output, "Keyset profile storage mode for this Minecraft installation");
                    return output.toByteArray();
                }
            });
            return null;
        });
        shared = value;
    }

    public Path file() { return file; }

}
