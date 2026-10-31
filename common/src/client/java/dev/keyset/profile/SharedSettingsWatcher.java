package dev.keyset.profile;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tells the game thread when shared profile or preference files may have changed, so it does not
 * read and parse them every second. A daemon thread listens to the containing folders; a slow
 * fallback check still fires periodically in case an event is missed. If the folders cannot be
 * watched, the fallback runs at the short interval, which matches the previous polling behaviour.
 */
public final class SharedSettingsWatcher implements AutoCloseable {
    public static final long FALLBACK_NANOS = 30_000_000_000L;
    public static final long UNWATCHED_NANOS = 1_000_000_000L;

    private final AtomicBoolean changed = new AtomicBoolean();
    private final long fallbackNanos;
    private final long unwatchedNanos;
    private WatchService service;
    private Thread thread;
    private Set<Path> directories = Set.of();
    private boolean watching;
    private long nextFallback;

    public SharedSettingsWatcher() { this(FALLBACK_NANOS, UNWATCHED_NANOS); }

    public SharedSettingsWatcher(long fallbackNanos, long unwatchedNanos) {
        this.fallbackNanos = fallbackNanos;
        this.unwatchedNanos = unwatchedNanos;
    }

    /** Watches exactly these folders, replacing any previous set. Calling it with the same set is a no-op. */
    public synchronized void watch(Collection<Path> folders) {
        Set<Path> wanted = new LinkedHashSet<>();
        for (Path folder : folders) wanted.add(folder.toAbsolutePath().normalize());
        if (wanted.equals(directories) && watching) return;
        stop();
        directories = wanted;
        try {
            WatchService created = FileSystems.getDefault().newWatchService();
            service = created;
            for (Path folder : wanted) {
                Files.createDirectories(folder);
                folder.register(created, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            }
            thread = new Thread(() -> listen(created), "Keyset settings watcher");
            thread.setDaemon(true);
            thread.start();
            watching = true;
        } catch (IOException | UnsupportedOperationException | SecurityException failure) {
            stop();
            directories = wanted;
        }
        changed.set(true); // the first check after (re)targeting always reads the files
    }

    /** Whether the files should be re-read now: an event arrived, or the periodic safety check is due. */
    public synchronized boolean due(long nowNanos) {
        return changed.get() || nowNanos - nextFallback >= 0;
    }

    /** Call just before re-reading, so a change that lands during the read is reported again. */
    public synchronized void acknowledge(long nowNanos) {
        changed.set(false);
        nextFallback = nowNanos + (watching ? fallbackNanos : unwatchedNanos);
    }

    /** Marks the files as changed, e.g. after the profiles moved to another folder. */
    public void markChanged() { changed.set(true); }

    public synchronized boolean isWatching() { return watching; }

    private void listen(WatchService active) {
        try {
            while (true) {
                WatchKey key = active.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW || relevant(event.context())) {
                        changed.set(true);
                    }
                }
                // A folder that vanished is covered by the periodic fallback.
                if (!key.reset()) changed.set(true);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException closed) {
            // stop() closed the service
        }
    }

    private static boolean relevant(Object context) {
        if (!(context instanceof Path name)) return true;
        String value = name.getFileName().toString();
        // Atomic writes go through *.tmp files and lock files; only the final rename matters.
        return (value.endsWith(".json") || value.endsWith(".properties")) && !value.endsWith(".tmp");
    }

    private void stop() {
        WatchService old = service;
        service = null;
        watching = false;
        if (thread != null) thread.interrupt();
        thread = null;
        if (old != null) {
            try { old.close(); } catch (IOException ignored) { /* nothing left to release */ }
        }
    }

    @Override
    public synchronized void close() {
        stop();
        directories = Set.of();
    }
}
