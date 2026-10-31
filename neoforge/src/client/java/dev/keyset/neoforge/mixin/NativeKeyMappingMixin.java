package dev.keyset.neoforge.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class NativeKeyMappingMixin {
    @Inject(method = "setKeyModifierAndCode", at = @At("HEAD"))
    private void keyset$externalModifier(KeyModifier modifier, InputConstants.Key key, CallbackInfo ci) {
        KeysetClient.externalKeyChanged((KeyMapping) (Object) this);
    }
}
