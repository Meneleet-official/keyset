package dev.keyset.client;

import dev.keyset.editor.MouseDiagramLayout;
import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.editor.MouseDiagramPainter;
import dev.keyset.editor.MouseDiagramPainter.State;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Minecraft text bridge for the shared mouse diagram geometry. */
public final class MouseDiagramRenderer {
    private MouseDiagramRenderer() {}

    public static void shell(GuiGraphicsExtractor graphics, MouseDiagramLayout layout) {
        MouseDiagramPainter.shell(graphics::fill, layout.body(), UiTheme.currentPalette());
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, Component caption, Control control,
                              Rect bounds, State state) {
        var palette = UiTheme.currentPalette();
        MouseDiagramPainter.button(graphics::fill, control, bounds, palette, state);
        int foreground = MouseDiagramPainter.labelColor(control, palette, state);
        String label = caption.getString();
        int available = Math.max(1, bounds.width() - (control == Control.MIDDLE ? 2 : 6));
        int textY = control == Control.MIDDLE ? bounds.bottom() - 10
            : bounds.y() + (bounds.height() - font.lineHeight) / 2 - 1;
        float scale = Math.min(1f, available / (float) Math.max(1, font.width(label)));
        scale = Math.min(scale, Math.max(1, bounds.height() - 7) / (float) font.lineHeight);
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x() + (bounds.width() - font.width(label) * scale) / 2f,
            textY + (font.lineHeight - font.lineHeight * scale) / 2f);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, 0, 0, foreground, false);
        graphics.pose().popMatrix();
    }
}
