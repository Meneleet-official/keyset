package dev.keyset.neoforge;

import dev.keyset.KeysetClient;

/** Client-only setup is isolated so a dedicated server never resolves Minecraft client classes. */
public final class KeysetNeoForgeClient {
    private KeysetNeoForgeClient() {}

    public static void initialize() {
        KeysetClient.setBindingPlatform(new NeoForgeBindingPlatform());
    }
}
