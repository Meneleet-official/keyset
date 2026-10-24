package dev.keyset.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class KeysetBranding {
    public static final String VERSION = "1.0.0";
    public static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("keyset", "textures/gui/logo.png");

    private static final int LOGO_TEXTURE_WIDTH = 15;
    private static final int LOGO_TEXTURE_HEIGHT = 15;

    private KeysetBranding() {
    }

    public static void drawLogo(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, x, y, 0.0F, 0.0F,
            width, height, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);
    }
}
