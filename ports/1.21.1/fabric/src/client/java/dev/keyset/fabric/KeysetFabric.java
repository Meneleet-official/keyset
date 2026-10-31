package dev.keyset.fabric;

import dev.keyset.KeysetClient;
import net.fabricmc.api.ClientModInitializer;

/** Initializes Keyset after the client's options and key mappings are available. */
public final class KeysetFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeysetClient.ensureLoaded();
    }
}
