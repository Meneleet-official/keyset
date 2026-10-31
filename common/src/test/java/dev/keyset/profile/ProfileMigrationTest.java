package dev.keyset.profile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileMigrationTest {
    private Path directory;

    @BeforeEach
    void createWorkspaceLocalTempDirectory() throws IOException {
        Path buildDirectory = Path.of("build");
        Files.createDirectories(buildDirectory);
        directory = Files.createTempDirectory(buildDirectory, "profile-migration-test-");
    }

    @AfterEach
    void removeWorkspaceLocalTempDirectory() throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void legacyImportDoesNotUndoEditsRenamesOrDeletionButPortableImportRemainsExplicit() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        legacy.load(Map.of("key.jump", binding("key.keyboard.space")));
        Profile legacyDefault = legacy.active();
        Profile legacyGame = legacy.create("Game", Map.of("key.attack", binding("key.keyboard.r")));
        Profile legacyTest = legacy.create("test", Map.of("key.sneak", binding("key.keyboard.c")));
        legacy.save();

        Path sharedFile = directory.resolve("shared/profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("key.jump", binding("key.keyboard.j")));
        assertTrue(shared.importLegacyFile(legacyFile));
        assertEquals(4, shared.profiles().size());

        Map<String, Binding> editedBindings = new LinkedHashMap<>(legacyDefault.bindings());
        editedBindings.put("key.jump", binding("key.keyboard.k"));
        editedBindings.put("new.mod.action", binding("key.keyboard.n"));
        shared.select(legacyDefault.id());
        shared.updateActive(editedBindings);
        shared.rename(legacyDefault.id(), "My Default");
        shared.delete(legacyGame.id());
        shared.save();

        ProfileStore reloaded = new ProfileStore(sharedFile);
        reloaded.load(Map.of());
        assertFalse(reloaded.importLegacyFile(legacyFile));
        assertEquals(3, reloaded.profiles().size());
        assertEquals(Set.of("Default", "My Default", "test"),
                reloaded.profiles().stream().map(Profile::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(binding("key.keyboard.k"),
                reloaded.profiles().stream().filter(profile -> profile.id().equals(legacyDefault.id()))
                        .findFirst().orElseThrow().bindings().get("key.jump"));
        assertEquals(binding("key.keyboard.n"),
                reloaded.profiles().stream().filter(profile -> profile.id().equals(legacyDefault.id()))
                        .findFirst().orElseThrow().bindings().get("new.mod.action"));
        assertTrue(reloaded.profiles().stream().noneMatch(profile -> profile.id().equals(legacyGame.id())));
        assertNotNull(reloaded.profiles().stream().filter(profile -> profile.id().equals(legacyTest.id()))
                .findFirst().orElse(null));

        Path copiedLegacyFile = directory.resolve("legacy-copy/profiles.json");
        Files.createDirectories(copiedLegacyFile.getParent());
        Files.copy(legacyFile, copiedLegacyFile);
        assertFalse(reloaded.importLegacyFile(copiedLegacyFile));

        Path transferDirectory = directory.resolve("transfer");
        Files.createDirectories(transferDirectory);
        Files.copy(legacyFile, transferDirectory.resolve("legacy-copy.json"));
        assertEquals(2, reloaded.importDirectory(transferDirectory));
        assertTrue(reloaded.profiles().stream().anyMatch(profile -> profile.name().equals("Default (2)")));
        assertTrue(reloaded.profiles().stream().anyMatch(profile -> profile.name().equals("Game")));
        assertEquals(1, reloaded.profiles().stream().filter(profile -> profile.name().equals("test")).count());
    }

    @Test
    void legacyImportCanPickUpNewSourceProfileIdsWithoutReimportingOldOnes() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        legacy.load(Map.of("base.action", binding("key.keyboard.a")));
        Profile first = legacy.active();
        legacy.create("Game", Map.of("game.action", binding("key.keyboard.g")));
        legacy.save();

        Path sharedFile = directory.resolve("shared/profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("current.action", binding("key.keyboard.c")));
        assertTrue(shared.importLegacyFile(legacyFile));
        shared.save();

        shared = new ProfileStore(sharedFile);
        shared.load(Map.of());
        assertFalse(shared.importLegacyFile(legacyFile));

        Profile future = legacy.create("Future", Map.of("future.action", binding("key.keyboard.f")));
        legacy.save();
        assertTrue(shared.importLegacyFile(legacyFile));
        assertEquals(4, shared.profiles().size());
        assertTrue(shared.profiles().stream().anyMatch(profile -> profile.id().equals(first.id())));
        assertTrue(shared.profiles().stream().anyMatch(profile -> profile.id().equals(future.id())));
        assertFalse(shared.importLegacyFile(legacyFile));
    }

    @Test
    void copiedLegacyProfileEditedBeforeFirstMarkerDoesNotCreateAnotherProfile() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        legacy.load(Map.of("action", binding("key.keyboard.a")));
        Profile source = legacy.active();
        legacy.save();

        Path sharedFile = directory.resolve("shared/profiles.json");
        Files.createDirectories(sharedFile.getParent());
        Files.copy(legacyFile, sharedFile);
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of());
        shared.select(source.id());
        shared.updateActive(Map.of("action", binding("key.keyboard.b")));
        shared.rename(source.id(), "Personal");
        shared.save();

        shared = new ProfileStore(sharedFile);
        shared.load(Map.of());
        assertTrue(shared.importLegacyFile(legacyFile));
        assertEquals(1, shared.profiles().size());
        assertEquals("Personal", shared.active().name());
        assertEquals(binding("key.keyboard.b"), shared.active().bindings().get("action"));
        shared.save();
        ProfileStore reloaded = new ProfileStore(sharedFile);
        reloaded.load(Map.of());
        assertFalse(reloaded.importLegacyFile(legacyFile));
    }

    @Test
    void laterLegacyNumberedSubsetIsConsumedButConflictingNumberedProfileIsImportedOnce() throws IOException {
        Path sharedFile = directory.resolve("shared/profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("initial.action", binding("key.keyboard.i")));
        Map<String, Binding> canonicalBindings = Map.of(
                "game.action", binding("key.keyboard.g"),
                "game.optional", binding("key.keyboard.o"));
        Profile canonicalGame = shared.create("Game", canonicalBindings);
        assertTrue(shared.removeRedundantImportedCopies().isEmpty());
        shared.save();

        Path legacyFile = directory.resolve("later-legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore laterLegacy = new ProfileStore(legacyFile);
        laterLegacy.load(Map.of());
        Profile legacySubset = laterLegacy.active();
        laterLegacy.rename(legacySubset.id(), "Game (2)");
        laterLegacy.updateActive(Map.of("game.action", binding("key.keyboard.g")));
        Profile legacyConflict = laterLegacy.create("Game (3)", Map.of(
                "game.action", binding("key.keyboard.y")));
        laterLegacy.save();

        assertTrue(shared.importLegacyFile(legacyFile));
        assertEquals(3, shared.profiles().size());
        assertTrue(shared.profiles().stream().anyMatch(profile -> profile.id().equals(canonicalGame.id())));
        assertTrue(shared.profiles().stream().noneMatch(profile -> profile.id().equals(legacySubset.id())));
        assertTrue(shared.profiles().stream().anyMatch(profile -> profile.id().equals(legacyConflict.id())
                && profile.name().equals("Game (3)")));
        shared.save();

        ProfileStore reloaded = new ProfileStore(sharedFile);
        reloaded.load(Map.of());
        assertFalse(reloaded.importLegacyFile(legacyFile));
        assertEquals(3, reloaded.profiles().size());
        assertEquals(1, reloaded.profiles().stream().filter(profile -> profile.name().equals("Game (3)")).count());
    }

    @Test
    void redundantImportedCopiesAreRemovedOnceRoutesAreRedirectedAndLegacyCopiesStayRemoved() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        Binding sharedBinding = binding("key.keyboard.space");
        legacy.load(Map.of(
                "key.jump", sharedBinding,
                "keyset.next_profile", binding("key.keyboard.p")));
        Profile canonicalDefault = legacy.active();
        Profile duplicateDefault = legacy.create("Default (2)", Map.of("key.jump", sharedBinding));
        Profile canonicalGame = legacy.create("Game", Map.of(
                "game.action", binding("key.keyboard.g"),
                "game.optional", binding("key.keyboard.o")));
        Profile duplicateGame = legacy.create("Game (2)", Map.of("game.action", binding("key.keyboard.g")));
        Profile combat = legacy.create("Combat", Map.of("combat.action", binding("key.keyboard.x")));
        Profile conflictingCombat = legacy.create("Combat (2)", Map.of(
                "combat.action", binding("key.keyboard.y")));
        Profile suffixLookalike = legacy.create("Game (2) custom", Map.of(
                "game.action", binding("key.keyboard.g")));
        Profile unrelated = legacy.create("Notes (2)", Map.of("notes.action", binding("key.keyboard.n")));
        legacy.save();

        Path sharedFile = directory.resolve("shared/profiles.json");
        Files.createDirectories(sharedFile.getParent());
        Files.copy(legacyFile, sharedFile);
        ProfileStore store = new ProfileStore(sharedFile);
        store.load(Map.of());

        store.select(duplicateGame.id());
        Map<String, String> removed = store.removeRedundantImportedCopies();
        assertEquals(Map.of(
                duplicateDefault.id(), canonicalDefault.id(),
                duplicateGame.id(), canonicalGame.id()), removed);
        assertEquals(canonicalGame.id(), store.active().id());
        assertTrue(store.profiles().stream().noneMatch(profile ->
                profile.id().equals(duplicateDefault.id()) || profile.id().equals(duplicateGame.id())));
        assertTrue(store.profiles().stream().anyMatch(profile -> profile.id().equals(combat.id())));
        assertTrue(store.profiles().stream().anyMatch(profile -> profile.id().equals(conflictingCombat.id())));
        assertTrue(store.profiles().stream().anyMatch(profile -> profile.id().equals(suffixLookalike.id())));
        assertTrue(store.profiles().stream().anyMatch(profile -> profile.id().equals(unrelated.id())));
        assertTrue(store.removeRedundantImportedCopies().isEmpty());
        Profile laterIntentionalCopy = store.create("Game (3)", Map.of(
                "game.action", binding("key.keyboard.g")));
        store.save();

        store = new ProfileStore(sharedFile);
        store.load(Map.of());
        assertTrue(store.removeRedundantImportedCopies().isEmpty());
        assertTrue(store.profiles().stream().anyMatch(profile -> profile.id().equals(laterIntentionalCopy.id())));

        assertTrue(store.importLegacyFile(legacyFile));
        assertTrue(store.profiles().stream().noneMatch(profile -> profile.id().equals(duplicateDefault.id())));
        assertTrue(store.profiles().stream().noneMatch(profile -> profile.id().equals(duplicateGame.id())));
        store.save();
        assertFalse(store.importLegacyFile(legacyFile));

        Path preferencesFile = directory.resolve("shared/preferences.properties");
        ClientPreferences preferences = new ClientPreferences(preferencesFile);
        preferences.toggleRoute("world:default", duplicateDefault.id());
        preferences.toggleRoute("world:game", duplicateGame.id());
        preferences.toggleRoute("world:canonical-game", canonicalGame.id());
        preferences.remapProfileRoutes(removed);
        assertEquals(canonicalDefault.id(), preferences.profileFor("world:default"));
        assertEquals(canonicalGame.id(), preferences.profileFor("world:game"));
        assertEquals(canonicalGame.id(), preferences.profileFor("world:canonical-game"));
        preferences.save();

        ClientPreferences reloadedPreferences = new ClientPreferences(preferencesFile);
        reloadedPreferences.load();
        assertEquals(canonicalDefault.id(), reloadedPreferences.profileFor("world:default"));
        assertEquals(canonicalGame.id(), reloadedPreferences.profileFor("world:game"));
    }

    @Test
    void failedSaveRestoresLegacyConsumptionCheckpointSoMigrationCanRetry() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        legacy.load(Map.of("legacy.action", binding("key.keyboard.l")));
        Profile importedProfile = legacy.active();
        legacy.save();

        Path sharedDirectory = directory.resolve("shared");
        Path sharedFile = sharedDirectory.resolve("profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("current.action", binding("key.keyboard.c")));
        ProfileStore.State beforeMigration = shared.checkpoint();
        assertTrue(shared.importLegacyFile(legacyFile));
        assertTrue(shared.profiles().stream().anyMatch(profile -> profile.id().equals(importedProfile.id())));

        String originalContents = Files.readString(sharedFile);
        Files.delete(sharedFile);
        Files.delete(SharedSettingsFiles.lockFileFor(sharedFile));
        Files.delete(sharedDirectory);
        Files.writeString(sharedDirectory, "blocks the profile directory");
        assertThrows(IOException.class, shared::save);

        shared.restore(beforeMigration);
        assertEquals(beforeMigration.profiles(), shared.profiles());
        Files.delete(sharedDirectory);
        Files.createDirectories(sharedDirectory);
        Files.writeString(sharedFile, originalContents);
        assertTrue(shared.importLegacyFile(legacyFile));
        assertTrue(shared.profiles().stream().anyMatch(profile -> profile.id().equals(importedProfile.id())));
        shared.save();
        ProfileStore reloaded = new ProfileStore(sharedFile);
        reloaded.load(Map.of());
        assertFalse(reloaded.importLegacyFile(legacyFile));
        assertEquals(2, reloaded.profiles().size());
    }

    @Test
    void portableExportOmitsLegacyMigrationAndRepairMetadata() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        legacy.load(Map.of("legacy.action", binding("key.keyboard.l")));
        legacy.save();

        Path sharedFile = directory.resolve("shared/profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("current.action", binding("key.keyboard.c")));
        assertTrue(shared.importLegacyFile(legacyFile));
        Profile migrated = shared.profiles().stream()
                .filter(profile -> profile.bindings().containsKey("legacy.action"))
                .findFirst().orElseThrow();
        shared.delete(migrated.id());
        assertTrue(shared.removeRedundantImportedCopies().isEmpty());
        shared.save();

        Path exportedFile = directory.resolve("transfer/profile.json");
        shared.exportProfile(shared.active().id(), exportedFile);
        String exportedJson = Files.readString(exportedFile);
        assertFalse(exportedJson.contains("migratedLegacyProfileIds"));
        assertFalse(exportedJson.contains("legacyDuplicateRepairComplete"));

        ProfileStore importedPortable = new ProfileStore(directory.resolve("portable/profiles.json"));
        importedPortable.load(Map.of());
        assertEquals(1, importedPortable.importFile(exportedFile));
    }

    private static Binding binding(String key) {
        return new Binding(key, false, false, false);
    }
}
