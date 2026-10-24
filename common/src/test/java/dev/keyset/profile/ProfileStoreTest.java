package dev.keyset.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoreTest {
    private Path directory;

    @BeforeEach
    void createWorkspaceLocalTempDirectory() throws IOException {
        Path buildDirectory = Path.of("build");
        Files.createDirectories(buildDirectory);
        directory = Files.createTempDirectory(buildDirectory, "profile-store-test-");
    }

    @AfterEach
    void removeWorkspaceLocalTempDirectory() throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void createsDefaultProfileFromCurrentBindingsAndReloadsIt() throws IOException {
        Path file = directory.resolve("profiles.json");
        Map<String, Binding> current = Map.of(
                "key.jump", new Binding("key.keyboard.space", false, false, false));

        ProfileStore store = new ProfileStore(file);
        store.load(current);

        assertEquals("Default", store.active().name());
        assertEquals(current, store.active().bindings());
        assertTrue(Files.exists(file));

        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(store.profiles(), reloaded.profiles());
        assertEquals(store.active(), reloaded.active());
    }

    @Test
    void roundTripsKeyNamesAndAllModifierCombinations() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore store = new ProfileStore(file);
        store.load(Map.of());
        Map<String, Binding> bindings = new LinkedHashMap<>();
        bindings.put("key.keyboard.combo", new Binding("key.keyboard.w", true, true, true, true));
        bindings.put("key.mouse.combo", new Binding("key.mouse.left", false, true, false));
        bindings.put("key.unbound", new Binding("key.keyboard.unknown", false, false, false));
        Profile created = store.create("  Combat  ", bindings);
        store.save();

        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());

        assertEquals("Combat", reloaded.active().name());
        assertEquals(created.bindings(), reloaded.active().bindings());
        assertEquals(new Binding("key.keyboard.w", true, true, true, true),
                reloaded.active().bindings().get("key.keyboard.combo"));
    }

    @Test
    void roundTripsArbitraryChordsMouseInputsAndNativeSettings() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore store = new ProfileStore(file);
        store.load(Map.of());
        String nativeSettings = "{\"enabled\":true,\"hotkey\":\"M+C\"}";
        Binding malilibChord = new Binding(
                "key.keyboard.m", false, false, false, false, List.of("key.keyboard.c"), nativeSettings);
        Binding mouseChord = new Binding(
                "key.mouse.left",
                true,
                true,
                false,
                true,
                List.of("key.keyboard.left.control", "key.keyboard.left.shift"));
        Profile created = store.create("Litematica", Map.of(
                "litematica.open_menu", malilibChord,
                "mouse.multi_modifier", mouseChord));
        store.save();

        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());

        assertEquals(created, reloaded.active());
        assertEquals(List.of("key.keyboard.m", "key.keyboard.c"),
                reloaded.active().bindings().get("litematica.open_menu").keys());
        assertEquals(nativeSettings, reloaded.active().bindings().get("litematica.open_menu").nativeSettings());
        assertEquals(List.of("key.mouse.left", "key.keyboard.left.control", "key.keyboard.left.shift"),
                reloaded.active().bindings().get("mouse.multi_modifier").keys());
        assertTrue(reloaded.active().bindings().get("mouse.multi_modifier").command());
    }

    @Test
    void schemaOneBindingsWithoutCommandDefaultToFalseAndCanBeResaved() throws IOException {
        Path file = directory.resolve("profiles.json");
        String profileId = UUID.randomUUID().toString();
        String contents = """
                {
                  "schemaVersion": 1,
                  "activeProfileId": "%s",
                  "profiles": [{
                    "id": "%s",
                    "name": "Default",
                    "bindings": {
                      "old.action": {
                        "key": "key.keyboard.a",
                        "ctrl": true,
                        "shift": false,
                        "alt": true
                      }
                    }
                  }]
                }
                """.formatted(profileId, profileId);
        Files.writeString(file, contents);

        ProfileStore store = new ProfileStore(file);
        store.load(Map.of());
        assertEquals(new Binding("key.keyboard.a", true, false, true, false),
                store.active().bindings().get("old.action"));
        store.save();

        assertTrue(Files.readString(file).contains("\"schemaVersion\": 2"));

        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(new Binding("key.keyboard.a", true, false, true, false),
                reloaded.active().bindings().get("old.action"));
    }

    @Test
    void updateActiveKeepsBindingsForMissingModsAndOverwritesCurrentOnes() throws IOException {
        ProfileStore store = new ProfileStore(directory.resolve("profiles.json"));
        store.load(Map.of(
                "vanilla.jump", new Binding("key.keyboard.space", false, false, false),
                "optional.mod.action", new Binding("key.keyboard.x", true, false, false)));
        store.updateActive(Map.of(
                "vanilla.jump", new Binding("key.keyboard.j", false, true, false),
                "new.mod.action", new Binding("key.keyboard.n", false, false, true)));

        assertEquals(Map.of(
                "vanilla.jump", new Binding("key.keyboard.j", false, true, false),
                "optional.mod.action", new Binding("key.keyboard.x", true, false, false),
                "new.mod.action", new Binding("key.keyboard.n", false, false, true)), store.active().bindings());
    }

    @Test
    void rejectsCorruptOrUnsupportedFilesWithoutOverwritingThem() throws IOException {
        Path file = directory.resolve("profiles.json");
        for (String contents : List.of("{ broken", "{\"schemaVersion\":3}")) {
            Files.writeString(file, contents);
            assertThrows(IOException.class, () -> new ProfileStore(file).load(Map.of()));
            assertEquals(contents, Files.readString(file));
        }
    }

    @Test
    void rejectsMalformedExtendedBindingFieldsWithoutOverwritingThem() throws IOException {
        Path file = directory.resolve("profiles.json");
        String profileId = UUID.randomUUID().toString();
        for (String extensionFields : List.of(
                "\"extraKeys\": \"key.keyboard.c\"",
                "\"extraKeys\": [\"key.keyboard.c\", 2]",
                "\"extraKeys\": [\" \"]",
                "\"nativeSettings\": 2",
                "\"nativeSettings\": null")) {
            String contents = """
                    {
                      "schemaVersion": 2,
                      "activeProfileId": "%s",
                      "profiles": [{
                        "id": "%s",
                        "name": "Default",
                        "bindings": {"action": {
                          "key": "key.keyboard.m",
                          "ctrl": false,
                          "shift": false,
                          "alt": false,
                          "command": false,
                          %s
                        }}
                      }]
                    }
                    """.formatted(profileId, profileId, extensionFields);
            Files.writeString(file, contents);

            assertThrows(IOException.class, () -> new ProfileStore(file).load(Map.of()), extensionFields);
            assertEquals(contents, Files.readString(file));
        }
    }

    @Test
    void trimsAndValidatesUniqueNames() throws IOException {
        ProfileStore store = new ProfileStore(directory.resolve("profiles.json"));
        store.load(Map.of());
        Profile second = store.create("  Editing  ", Map.of());
        assertEquals("Editing", second.name());

        assertThrows(IllegalArgumentException.class, () -> store.create("editing", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> store.create("  ", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> store.create("x".repeat(49), Map.of()));
        store.rename(second.id(), " Main ");
        assertEquals("Main", store.profiles().get(1).name());
        assertThrows(IllegalArgumentException.class, () -> store.rename(second.id(), " default "));
    }

    @Test
    void deletingActiveSelectsFirstRemainingAndRefusesFinalProfile() throws IOException {
        ProfileStore store = new ProfileStore(directory.resolve("profiles.json"));
        store.load(Map.of());
        Profile original = store.active();
        Profile second = store.create("Second", Map.of());
        store.delete(second.id());

        assertEquals(original, store.active());
        assertThrows(IllegalStateException.class, () -> store.delete(original.id()));
        assertEquals(1, store.profiles().size());
    }

    @Test
    void failedSaveCanBeRetriedAfterThePathBecomesWritable() throws IOException {
        Path parent = directory.resolve("profiles-dir");
        Path file = parent.resolve("profiles.json");
        ProfileStore store = new ProfileStore(file);
        store.load(Map.of());
        Profile created = store.create("Retry", Map.of());

        String originalContents = Files.readString(file);
        Files.delete(file);
        Files.delete(SharedSettingsFiles.lockFileFor(file));
        Files.delete(parent);
        Files.writeString(parent, "blocks the parent directory");
        assertThrows(IOException.class, store::save);
        assertEquals(created, store.active());

        Files.delete(parent);
        Files.createDirectories(parent);
        Files.writeString(file, originalContents);
        store.save();
        assertTrue(Files.exists(file));
        assertNotEquals(0L, Files.size(file));
    }

    @Test
    void checkpointRestoresDeletedActiveProfileAndBindingsAfterSaveFailure() throws IOException {
        Path parent = directory.resolve("checkpoint-profiles-dir");
        Path file = parent.resolve("profiles.json");
        ProfileStore store = new ProfileStore(file);
        store.load(Map.of("default.action", new Binding("key.keyboard.d", false, false, false)));
        Profile defaultProfile = store.active();
        Profile other = store.create("Other", Map.of(
                "other.action", new Binding("key.keyboard.o", true, false, true)));
        store.save();
        ProfileStore.State checkpoint = store.checkpoint();

        String originalContents = Files.readString(file);
        Files.delete(file);
        Files.delete(SharedSettingsFiles.lockFileFor(file));
        Files.delete(parent);
        Files.writeString(parent, "blocks the parent directory");
        store.delete(other.id());
        assertEquals(defaultProfile, store.active());
        assertThrows(IOException.class, store::save);

        store.restore(checkpoint);
        assertEquals(other, store.active());
        assertEquals(checkpoint.profiles(), store.profiles());
        assertEquals(new Binding("key.keyboard.o", true, false, true),
                store.active().bindings().get("other.action"));

        Files.delete(parent);
        Files.createDirectories(parent);
        Files.writeString(file, originalContents);
        store.save();
        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(checkpoint.profiles(), reloaded.profiles());
        assertEquals(other, reloaded.active());
    }

    @Test
    void checkpointAndStateAreDefensiveSnapshots() throws IOException {
        ProfileStore store = new ProfileStore(directory.resolve("profiles.json"));
        store.load(Map.of());
        ProfileStore.State checkpoint = store.checkpoint();

        assertThrows(UnsupportedOperationException.class, () -> checkpoint.profiles().clear());
        store.create("Later", Map.of());
        store.restore(checkpoint);

        assertEquals(1, store.profiles().size());
        assertEquals(checkpoint.activeProfileId(), store.active().id());
    }

    @Test
    void restoreRejectsInvalidCheckpointWithoutChangingCurrentState() throws IOException {
        ProfileStore store = new ProfileStore(directory.resolve("profiles.json"));
        store.load(Map.of());
        ProfileStore.State before = store.checkpoint();
        Profile invalid = new Profile("not-a-uuid", "Invalid", Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> store.restore(new ProfileStore.State(List.of(invalid), invalid.id())));
        assertEquals(before, store.checkpoint());
    }

    @Test
    void snapshotsAreImmutable() throws IOException {
        ProfileStore store = new ProfileStore(directory.resolve("profiles.json"));
        Map<String, Binding> bindings = new LinkedHashMap<>();
        bindings.put("key.jump", new Binding("key.keyboard.space", false, false, false));
        store.load(bindings);
        bindings.clear();

        assertEquals(1, store.active().bindings().size());
        assertThrows(UnsupportedOperationException.class,
                () -> store.active().bindings().put("other", new Binding("key.keyboard.x", false, false, false)));
        assertThrows(UnsupportedOperationException.class,
                () -> store.profiles().add(store.active()));
        assertFalse(store.profiles().isEmpty());
    }
}
