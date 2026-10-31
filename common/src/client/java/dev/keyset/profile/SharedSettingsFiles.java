package dev.keyset.profile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Coordinates atomic settings writes between threads and independent game processes. */
final class SharedSettingsFiles {
    @FunctionalInterface
    interface IOCallable<T> {
        T call() throws IOException;
    }

    @FunctionalInterface
    interface JsonWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    private static final ConcurrentHashMap<Path, ReentrantLock> PROCESS_LOCKS = new ConcurrentHashMap<>();

    private SharedSettingsFiles() { }

    static <T> T withFileLock(Path file, IOCallable<T> operation) throws IOException {
        Path target = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Settings file needs a parent directory");
        Files.createDirectories(parent);

        Path lockFile = lockFileFor(target);
        Path lockKey = lockFile.toAbsolutePath().normalize();
        ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        processLock.lock();
        try {
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                return operation.call();
            }
        } finally {
            processLock.unlock();
        }
    }

    static void writeJsonAtomically(Path file, JsonWriter writer) throws IOException {
        Path target = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Settings file needs a parent directory");
        Files.createDirectories(parent);

        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) prefix = "settings-" + prefix;
        Path temporary = Files.createTempFile(parent, prefix + ".", ".tmp");
        boolean moved = false;
        Throwable failure = null;
        try {
            try (BufferedWriter output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(output);
            }
            moveIntoPlace(temporary, target);
            moved = true;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            if (!moved) deleteTemporary(temporary, failure);
        }
    }

    static void writeBytesAtomically(Path file, IOCallable<byte[]> content) throws IOException {
        Path target = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Settings file needs a parent directory");
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "keyset-settings-", ".tmp");
        boolean moved = false;
        Throwable failure = null;
        try {
            Files.write(temporary, content.call(), StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            moveIntoPlace(temporary, target);
            moved = true;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            if (!moved) deleteTemporary(temporary, failure);
        }
    }

    private static void deleteTemporary(Path temporary, Throwable failure) throws IOException {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupException) {
            if (failure != null) failure.addSuppressed(cleanupException);
            else throw cleanupException;
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Path lockFileFor(Path target) throws IOException {
        Path normalizedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) throw new IOException("Settings file needs a parent directory");

        Path lockDirectory;
        try {
            lockDirectory = parent.toRealPath();
        } catch (IOException unresolvedParent) {
            lockDirectory = parent;
        }
        return lockDirectory.resolve(normalizedTarget.getFileName().toString() + ".lock");
    }
}
