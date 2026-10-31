package dev.keyset.fabric;

import dev.keyset.KeysetClient;
import net.fabricmc.api.ClientModInitializer;

public final class KeysetFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeysetClient.setBindingPlatform(new FabricBindingPlatform());
    }
}
