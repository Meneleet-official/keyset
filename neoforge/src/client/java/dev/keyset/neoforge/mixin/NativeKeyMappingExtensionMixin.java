package dev.keyset.neoforge.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IKeyMappingExtension.class)
public interface NativeKeyMappingExtensionMixin {
    @Inject(method = "isActiveAndMatches", at = @At("HEAD"), cancellable = true)
    private void keyset$matches(InputConstants.Key key, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = KeysetClient.matches((KeyMapping) (Object) this, key);
        if (result != null) cir.setReturnValue(result);
    }
}
