package dev.keyset.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the in-game HUD (hotbar, hearts, chat) while a Keyset screen is open, so it does not show through the editor. */
@Mixin(Gui.class)
public abstract class KeysetHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void keysetHideHudBehindEditor(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null && minecraft.screen.getClass().getName().startsWith("dev.keyset.client.")) {
            ci.cancel();
        }
    }
}
