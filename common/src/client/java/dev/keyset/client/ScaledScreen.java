package dev.keyset.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A Keyset screen: its {@code init} starts with {@link InterfaceScale#apply}, and leaving it restores the game's scale. */
public abstract class ScaledScreen extends Screen {
    protected ScaledScreen(Component title) {
        super(title);
    }

    @Override
    public void removed() {
        InterfaceScale.restore();
        super.removed();
    }
}
