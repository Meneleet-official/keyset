package dev.keyset.neoforge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;

@Mod("keyset")
public final class KeysetNeoForge {
    public KeysetNeoForge() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> KeysetNeoForgeClient::initialize);
    }
}
