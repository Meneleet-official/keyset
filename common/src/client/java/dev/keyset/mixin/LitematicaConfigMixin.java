package dev.keyset.mixin;

import dev.keyset.KeysetClient;
import dev.keyset.compat.ExternalHotkeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep native UI edits in the active profile, including when the user quits directly. */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.config.Configs", remap = false)
public abstract class LitematicaConfigMixin {
    @Inject(method = "saveToFile", at = @At("TAIL"), require = 0, remap = false)
    private static void keyset$nativeConfigSaved(CallbackInfo ci) {
        if (!ExternalHotkeys.isSaving()) KeysetClient.optionsSaved();
    }
}
