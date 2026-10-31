package dev.keyset.profile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Timestamped copies of the profile file taken before risky operations. */
public final class ProfileBackups {
    /** Number of newest backups kept next to the profile file. */
    public static final int KEEP = 20;

    private ProfileBackups() {}

    /**
     * Copies {@code source} into a sibling {@code backups} folder and prunes the oldest copies.
     *
     * @return the new backup, or {@code null} when there is no profile file yet
     */
    public static Path create(Path source, String reason) throws IOException {
        Objects.requireNonNull(source, "source");
        if (!Files.isRegularFile(source)) return null;
        Path directory = source.getParent().resolve("backups");
        Files.createDirectories(directory);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path backup = directory.resolve("profiles-" + timestamp + "-" + reason + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".json");
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        prune(directory, backup);
        return backup;
    }

    private static void prune(Path directory, Path newest) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "profiles-*.json")) {
            List<Path> sorted = new ArrayList<>();
            files.forEach(sorted::add);
            // Names start with a sortable timestamp; file times can tie on coarse clocks.
            sorted.sort(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed());
            int kept = 1; // the backup just taken always stays
            for (Path path : sorted) {
                if (path.equals(newest)) continue;
                if (kept < KEEP) kept++;
                else Files.deleteIfExists(path);
            }
        }
    }
}
