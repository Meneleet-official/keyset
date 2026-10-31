package dev.keyset.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedSettingsWatcherTest {
    private static final long HOUR = 3_600_000_000_000L;

    @TempDir Path directory;

    private static boolean waitUntilDue(SharedSettingsWatcher watcher) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (watcher.due(System.nanoTime())) return true;
            Thread.sleep(20);
        }
        return false;
    }

    @Test
    void firstCheckAfterWatchingAlwaysReads() throws Exception {
        try (SharedSettingsWatcher watcher = new SharedSettingsWatcher(HOUR, HOUR)) {
            watcher.watch(java.util.List.of(directory));
            assertTrue(watcher.due(System.nanoTime()));
        }
    }

    @Test
    void staysQuietUntilAFileChanges() throws Exception {
        try (SharedSettingsWatcher watcher = new SharedSettingsWatcher(HOUR, HOUR)) {
            watcher.watch(java.util.List.of(directory));
            assertTrue(watcher.isWatching());
            watcher.acknowledge(System.nanoTime());
            assertFalse(watcher.due(System.nanoTime()));

            Files.writeString(directory.resolve("profiles.json"), "{}");
            assertTrue(waitUntilDue(watcher));

            watcher.acknowledge(System.nanoTime());
            assertFalse(watcher.due(System.nanoTime()));
        }
    }

    @Test
    void ignoresTemporaryAndUnrelatedFiles() throws Exception {
        try (SharedSettingsWatcher watcher = new SharedSettingsWatcher(HOUR, HOUR)) {
            watcher.watch(java.util.List.of(directory));
            watcher.acknowledge(System.nanoTime());
            Files.writeString(directory.resolve("keyset-settings-1.tmp"), "x");
            Files.writeString(directory.resolve("notes.txt"), "x");
            Thread.sleep(500);
            assertFalse(watcher.due(System.nanoTime()));
        }
    }

    @Test
    void periodicFallbackStillFires() throws Exception {
        try (SharedSettingsWatcher watcher = new SharedSettingsWatcher(50_000_000L, 50_000_000L)) {
            watcher.watch(java.util.List.of(directory));
            long now = System.nanoTime();
            watcher.acknowledge(now);
            assertFalse(watcher.due(now));
            assertTrue(watcher.due(now + 100_000_000L));
        }
    }

    @Test
    void markChangedForcesARead() throws Exception {
        try (SharedSettingsWatcher watcher = new SharedSettingsWatcher(HOUR, HOUR)) {
            watcher.watch(java.util.List.of(directory));
            watcher.acknowledge(System.nanoTime());
            watcher.markChanged();
            assertTrue(watcher.due(System.nanoTime()));
        }
    }
}
