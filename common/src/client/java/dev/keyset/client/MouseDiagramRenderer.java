package dev.keyset.client;

import dev.keyset.editor.MouseDiagramLayout;
import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.editor.MouseDiagramPainter;
import dev.keyset.editor.MouseDiagramPainter.State;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.profile.AppearancePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Minecraft text bridge for the shared mouse diagram geometry. */
public final class MouseDiagramRenderer {
    private MouseDiagramRenderer() {}

    public static void shell(GuiGraphicsExtractor graphics, MouseDiagramLayout layout, MouseTextureCache cache) {
        shell(graphics, layout, UiTheme.currentPalette(), cache);
    }

    /** Draws the shared top/side mouse artwork and its view headings in a supplied palette. */
    public static void shell(GuiGraphicsExtractor graphics, MouseDiagramLayout layout, AppearancePalette palette,
                             MouseTextureCache cache) {
        cache.shell(graphics, layout, palette);
        Font font = Minecraft.getInstance().font;
        heading(graphics, font, Component.translatable("keyset.mouse_view_top"), layout.topViewLabel(), palette.muted());
        heading(graphics, font, Component.translatable("keyset.mouse_view_side"), layout.sideViewLabel(), palette.muted());
        heading(graphics, font, Component.translatable("keyset.mouse_extra"), layout.extraLabel(), palette.muted());
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, Component caption, Control control,
                              Rect bounds, State state, MouseTextureCache cache) {
        button(graphics, font, caption, control, bounds, state, UiTheme.currentPalette(), cache);
    }

    /** Draws a hitbox using a supplied palette, for previews outside the active UI theme. */
    public static void button(GuiGraphicsExtractor graphics, Font font, Component caption, Control control,
                              Rect bounds, State state, AppearancePalette palette, MouseTextureCache cache) {
        if (control.ordinal() >= Control.EXTRA6.ordinal()) {
            MouseDiagramPainter.button(graphics::fill, control, bounds, palette, state);
            KeycapRenderer.drawLabel(graphics, font, caption.getString(),
                KeycapPainter.faceBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()),
                MouseDiagramPainter.labelColor(control, palette, state), .3f);
            return;
        } else {
            cache.button(graphics, control, bounds, palette, state);
        }
        int foreground = MouseDiagramPainter.labelColor(control, palette, state);
        String label = caption.getString();
        int available = Math.max(1, bounds.width() - (control == Control.MIDDLE ? 3 : 6));
        int textY = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        float scale = Math.min(1f, available / (float) Math.max(1, font.width(label)));
        scale = Math.min(scale, Math.max(1, bounds.height() - 4) / (float) font.lineHeight);
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x() + (bounds.width() - font.width(label) * scale) / 2f,
            textY + (font.lineHeight - font.lineHeight * scale) / 2f);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, 0, 0, foreground, false);
        graphics.pose().popMatrix();
    }

    private static void heading(GuiGraphicsExtractor graphics, Font font, Component component, Rect bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        String label = component.getString();
        float scale = Math.min(1f, Math.min((bounds.width() - 2f) / Math.max(1, font.width(label)),
            (bounds.height() - 1f) / font.lineHeight));
        if (scale < 0.5f) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x() + (bounds.width() - font.width(label) * scale) / 2f,
            bounds.y() + (bounds.height() - font.lineHeight * scale) / 2f);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, 0, 0, color, false);
        graphics.pose().popMatrix();
    }
}
