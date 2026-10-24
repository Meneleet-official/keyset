package dev.keyset.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import dev.keyset.compat.ModOrigins;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V", at = @At("RETURN"))
    private void keyset$trackOrigin(String name, InputConstants.Type type, int key, KeyMapping.Category category, int order, CallbackInfo ci) {
        ModOrigins.track((KeyMapping) (Object) this);
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void keyset$same(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        KeyMapping self = (KeyMapping) (Object) this;
        if (KeysetClient.chord(self) != null || KeysetClient.chord(other) != null) cir.setReturnValue(KeysetClient.same(self, other));
    }
    @Inject(method = "isUnbound", at = @At("HEAD"), cancellable = true)
    private void keyset$bound(CallbackInfoReturnable<Boolean> cir) {
        if (KeysetClient.chord((KeyMapping) (Object) this) != null) cir.setReturnValue(false);
    }
    @Inject(method = "isDefault", at = @At("HEAD"), cancellable = true)
    private void keyset$default(CallbackInfoReturnable<Boolean> cir) {
        if (KeysetClient.chord((KeyMapping) (Object) this) != null) cir.setReturnValue(KeysetClient.isDefault((KeyMapping) (Object) this));
    }
    @Inject(method = "getTranslatedKeyMessage", at = @At("HEAD"), cancellable = true)
    private void keyset$label(CallbackInfoReturnable<Component> cir) {
        var chord = KeysetClient.chord((KeyMapping) (Object) this);
        if (chord != null) cir.setReturnValue(KeysetClient.bindingLabel(chord));
    }
    @Inject(method = "setKey", at = @At("HEAD"))
    private void keyset$externalKey(InputConstants.Key key, CallbackInfo ci) {
        KeysetClient.externalKeyChanged((KeyMapping) (Object) this);
    }
    @Inject(method = "matches(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void keyset$matchesKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = KeysetClient.matches((KeyMapping) (Object) this, InputConstants.getKey(event), event.modifiers());
        if (result != null) cir.setReturnValue(result);
    }
    @Inject(method = "matchesMouse", at = @At("HEAD"), cancellable = true)
    private void keyset$matchesMouse(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = KeysetClient.matches((KeyMapping) (Object) this,
                InputConstants.Type.MOUSE.getOrCreate(event.button()), event.modifiers());
        if (result != null) cir.setReturnValue(result);
    }
    @Inject(method = "matches(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z", at = @At("HEAD"), cancellable = true)
    private void keyset$matchesInput(InputConstants.Key key, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = KeysetClient.matches((KeyMapping) (Object) this, key);
        if (result != null) cir.setReturnValue(result);
    }
    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void keyset$click(InputConstants.Key key, CallbackInfo ci) {
        if (KeysetClient.route(key, true, true)) ci.cancel();
    }
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void keyset$set(InputConstants.Key key, boolean down, CallbackInfo ci) {
        if (KeysetClient.route(key, down, false)) ci.cancel();
    }
}
