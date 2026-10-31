package dev.keyset.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import dev.keyset.KeysetClient;

/** All behavior lives in client mixins shared with Fabric. */
@Mod(value = "keyset", dist = Dist.CLIENT)
public final class KeysetNeoForge {
    public KeysetNeoForge() { KeysetClient.setBindingPlatform(new NeoBindingPlatform()); }
}
