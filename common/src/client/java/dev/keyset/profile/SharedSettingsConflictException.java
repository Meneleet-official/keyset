package dev.keyset.profile;

import java.io.IOException;

/** Thrown when two clients made incompatible edits to the same shared setting. */
public final class SharedSettingsConflictException extends IOException {
    public SharedSettingsConflictException(String message) {
        super(message);
    }
}
