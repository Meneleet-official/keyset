package dev.keyset.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileHelpersTest {
    @TempDir Path directory;

    private static Profile profile(String id, String name) { return new Profile(id, name, Map.of()); }

    @Test
    void cycleWrapsInBothDirections() {
        List<Profile> profiles = List.of(profile("a", "A"), profile("b", "B"), profile("c", "C"));
        assertEquals("b", ProfileCycle.neighbor(profiles, "a", false).id());
        assertEquals("a", ProfileCycle.neighbor(profiles, "c", false).id());
        assertEquals("c", ProfileCycle.neighbor(profiles, "a", true).id());
        assertEquals("b", ProfileCycle.neighbor(profiles, "missing", false).id());
    }

    @Test
    void cycleNeedsTwoProfiles() {
        assertNull(ProfileCycle.neighbor(List.of(profile("a", "A")), "a", false));
        assertNull(ProfileCycle.neighbor(List.of(), "a", true));
    }

    @Test
    void exportFileNameIsSafeAndUnique() {
        assertEquals("keyset-pvp-mode-12345678.json",
                ProfileTransfer.fileName(profile("12345678-aaaa", "PvP Mode!")));
        assertEquals("keyset-profile-abcdef01.json",
                ProfileTransfer.fileName(profile("abcdef01-0000", "///")));
        String longName = ProfileTransfer.fileName(profile("11111111-2222", "x".repeat(100)));
        assertEquals("keyset-" + "x".repeat(32) + "-11111111.json", longName);
    }

    @Test
    void countsOnlyJsonFiles() throws Exception {
        assertEquals(0, ProfileTransfer.count(directory.resolve("missing")));
        Files.writeString(directory.resolve("a.json"), "{}");
        Files.writeString(directory.resolve("B.JSON"), "{}");
        Files.writeString(directory.resolve("c.txt"), "x");
        assertEquals(2, ProfileTransfer.count(directory));
    }

    @Test
    void backupCopiesTheFileAndKeepsOnlyTheNewest() throws Exception {
        Path file = directory.resolve("profiles.json");
        assertNull(ProfileBackups.create(file, "none"));
        Files.writeString(file, "{\"v\":1}");
        for (int i = 0; i < ProfileBackups.KEEP + 5; i++) {
            Path backup = ProfileBackups.create(file, "test");
            assertTrue(Files.isRegularFile(backup));
            assertEquals("{\"v\":1}", Files.readString(backup));
        }
        try (var backups = Files.list(directory.resolve("backups"))) {
            assertEquals(ProfileBackups.KEEP, backups.count());
        }
    }
}
