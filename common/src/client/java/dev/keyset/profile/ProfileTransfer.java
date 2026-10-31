package dev.keyset.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Naming and discovery rules for portable profile files in the transfer folder. */
public final class ProfileTransfer {
    private static final int MAX_SLUG_LENGTH = 32;

    private ProfileTransfer() {}

    /** File name for an exported profile: a readable slug plus the start of its id, so names never collide. */
    public static String fileName(Profile profile) {
        String slug = profile.name().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-").replaceAll("^[.-]+|[.-]+$", "");
        if (slug.isBlank()) slug = "profile";
        if (slug.length() > MAX_SLUG_LENGTH) slug = slug.substring(0, MAX_SLUG_LENGTH);
        return "keyset-" + slug + "-" + profile.id().replace("-", "").substring(0, 8) + ".json";
    }

    /** Number of JSON files waiting in the transfer folder. */
    public static int count(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        try (var files = Files.list(directory)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .count();
        }
    }
}
