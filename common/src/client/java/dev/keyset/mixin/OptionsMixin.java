package dev.keyset.mixin;

import dev.keyset.KeysetClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void keyset$register(CallbackInfo ci) {
        keyMappings = Arrays.copyOf(keyMappings, keyMappings.length + 3);
        keyMappings[keyMappings.length - 1] = KeysetClient.openMapping();
        keyMappings[keyMappings.length - 2] = KeysetClient.nextProfileMapping();
        keyMappings[keyMappings.length - 3] = KeysetClient.previousProfileMapping();
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void keyset$saveProfiles(CallbackInfo ci) { KeysetClient.optionsSaved(); }
}
