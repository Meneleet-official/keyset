package dev.keyset.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount") int keyset$getClickCount();
    @Accessor("clickCount") void keyset$setClickCount(int value);
}
