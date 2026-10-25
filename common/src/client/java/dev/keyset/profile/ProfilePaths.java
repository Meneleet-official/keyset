package dev.keyset.profile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Resolves one Keyset settings directory shared by Minecraft instances on the same OS user. */
public final class ProfilePaths {
    private ProfilePaths() {}

    public static Path sharedDirectory(Map<String, String> environment, Map<String, String> properties) {
        String override = firstNonBlank(properties.get("keyset.configDir"), environment.get("KEYSET_CONFIG_DIR"));
        if (override != null) return Path.of(override).toAbsolutePath().normalize();

        Path home = Path.of(properties.getOrDefault("user.home", ".")).toAbsolutePath().normalize();
        String os = properties.getOrDefault("os.name", "").toLowerCase(Locale.ROOT);
        Path minecraftRoot;
        if (os.contains("win")) {
            String roaming = firstNonBlank(environment.get("APPDATA"), home.resolve("AppData/Roaming").toString());
            minecraftRoot = Path.of(roaming).resolve(".minecraft");
        } else if (os.contains("mac")) {
            minecraftRoot = home.resolve("Library/Application Support/minecraft");
        } else {
            minecraftRoot = home.resolve(".minecraft");
        }
        return minecraftRoot.resolve("config/keyset").toAbsolutePath().normalize();
    }

    /** Resolves settings that belong to one Minecraft installation. */
    public static Path instanceDirectory(Path gameDirectory) {
        return gameDirectory.toAbsolutePath().normalize().resolve("config/keyset");
    }

    /** Separate from the old profiles.json file, which is retained as a one-time migration source. */
    public static Path instanceProfilesFile(Path gameDirectory) {
        return instanceDirectory(gameDirectory).resolve("profiles-local.json");
    }

    public static Path instanceStorageModeFile(Path gameDirectory) {
        return instanceDirectory(gameDirectory).resolve("profile-storage.properties");
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        return second != null && !second.isBlank() ? second.trim() : null;
    }
}
