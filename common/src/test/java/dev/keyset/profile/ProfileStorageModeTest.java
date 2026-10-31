package dev.keyset.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileStorageModeTest {
    @TempDir Path directory;

    @Test
    void modeDefaultsToSharedAndPersistsPerInstallation() throws IOException {
        Path file = ProfilePaths.instanceStorageModeFile(directory.resolve("game"));
        ProfileStorageMode mode = new ProfileStorageMode(file);
        mode.load();
        assertTrue(mode.isShared());
        mode.setShared(false);

        ProfileStorageMode reloaded = new ProfileStorageMode(file);
        reloaded.load();
        assertFalse(reloaded.isShared());
        reloaded.setShared(true);
        ProfileStorageMode attached = new ProfileStorageMode(file);
        attached.load();
        assertTrue(attached.isShared());
    }

    @Test
    void failedModeWriteDoesNotChangeCurrentMode() throws IOException {
        Path blockedDirectory = directory.resolve("blocked");
        Path file = blockedDirectory.resolve("mode.properties");
        ProfileStorageMode mode = new ProfileStorageMode(file);
        mode.load();
        Files.delete(file.resolveSibling("mode.properties.lock"));
        Files.delete(blockedDirectory);
        Files.writeString(blockedDirectory, "file blocks directory");
        assertThrows(IOException.class, () -> mode.setShared(false));
        assertTrue(mode.isShared());
    }

    @Test
    void detachedStorePathNeverUsesLegacyImportFile() {
        Path game = directory.resolve("game");
        assertNotEquals(game.resolve("config/keyset/profiles.json").toAbsolutePath().normalize(),
                ProfilePaths.instanceProfilesFile(game));
    }

    @Test
    void profileSnapshotRemainsIndependentAndReattachesToSharedChanges() throws IOException {
        Path sharedFile = directory.resolve("shared/profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("move", bind("key.keyboard.w")));
        Profile initial = shared.active();
        Profile alternate = shared.create("Game", Map.of("move", bind("key.keyboard.up")));
        shared.save();

        Path localFile = directory.resolve("instance/profiles-local.json");
        shared.copyTo(localFile);
        ProfileStore local = new ProfileStore(localFile);
        local.load(Map.of());
        assertEquals(shared.profiles(), local.profiles());
        assertEquals(alternate, local.active());
        local.updateActive(Map.of("move", bind("key.keyboard.i")));
        local.save();

        ProfileStore reattached = new ProfileStore(sharedFile);
        reattached.load(Map.of());
        assertEquals(alternate, reattached.active());
        assertEquals(bind("key.keyboard.up"), reattached.active().bindings().get("move"));

        shared.select(initial.id());
        shared.updateActive(Map.of("move", bind("key.keyboard.a")));
        shared.save();
        reattached = new ProfileStore(sharedFile);
        reattached.load(Map.of());
        assertEquals(initial.id(), reattached.active().id());
        assertEquals(bind("key.keyboard.a"), reattached.active().bindings().get("move"));

        ProfileStore detachedAgain = new ProfileStore(localFile);
        detachedAgain.load(Map.of());
        assertEquals(bind("key.keyboard.i"), detachedAgain.active().bindings().get("move"));
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> shared.copyTo(localFile));
    }

    private static Binding bind(String key) { return new Binding(key, false, false, false); }
}
