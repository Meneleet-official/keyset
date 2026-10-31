package dev.keyset.mixin;

import dev.keyset.client.KeysetScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) { super(title); }

    @Inject(method = "openScreenButton", at = @At("HEAD"), cancellable = true)
    private void keyset$replaceControlsButton(Component title, Supplier<Screen> target,
                                               CallbackInfoReturnable<Button> cir) {
        if (title.equals(OptionsScreen.CONTROLS)) {
            cir.setReturnValue(Button.builder(Component.translatable("keyset.open"),
                button -> minecraft.setScreenAndShow(new KeysetScreen(this))).build());
        }
    }
}
