package dev.keyset.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared flat widgets and colors for Keyset's profile screens. */
public final class KeysetWidgets {
    public static final int PANEL = 0x58000000;
    public static final int PANEL_LIGHT = 0x78000000;
    public static final int BORDER = 0x40FFFFFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFB2B7B5;
    public static final int TEAL = 0xFF94E4D3;
    public static final int RED = 0xFFFF8A8A;
    public static final int CONTROL = 0x90000000;
    public static final int HOVER = 0xE0000000;
    public static final int SELECTED = 0xA0152622;
    public static final int ROW = 0x30000000;
    public static final int ROW_ALTERNATE = 0x18000000;
    public static final int KEY_SHADOW = 0xD0080A09;
    public static final int KEY_FACE = 0xE0181B1A;
    public static final int KEY_EDGE = 0xFF5C6561;

    private KeysetWidgets() {}

    public static FlatButton flatButton(int x, int y, int width, int height, Component message,
                                        Button.OnPress onPress, ButtonStyle style) {
        return new FlatButton(x, y, width, height, message, onPress, style);
    }

    public static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, BORDER);
    }

    public static void drawText(GuiGraphicsExtractor graphics, Font font, Component text,
                                int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, value, x, y, color, false);
    }

    public enum ButtonStyle { ACTION, PRIMARY, TAB, QUIET, ROW, BINDING }

    public static final class FlatButton extends Button {
        private final ButtonStyle style;
        private boolean selected;

        private FlatButton(int x, int y, int width, int height, Component message,
                           OnPress onPress, ButtonStyle style) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused() && active;
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            int color = !active ? 0x70FFFFFF : selected && style == ButtonStyle.QUIET ? TEAL : TEXT;
            if (style == ButtonStyle.ROW) {
                if (selected || hover) {
                    graphics.fill(x, y, x + width, y + height, selected ? SELECTED : CONTROL);
                }
                graphics.fill(x + 5, y + height - 1, x + width - 5, y + height,
                    selected ? TEAL : hover ? BORDER : ROW);
                if (selected) color = TEAL;
                else if (!hover) color = MUTED;
            } else {
                int fill = selected ? SELECTED : hover ? HOVER : CONTROL;
                if (style == ButtonStyle.BINDING && !selected) fill = hover ? HOVER : ROW;
                if (!active) fill = 0x40000000;
                if (style != ButtonStyle.QUIET || hover || selected) {
                    graphics.fill(x, y, x + width, y + height, fill);
                }
                if (style == ButtonStyle.BINDING) {
                    graphics.fill(x, y + height - 1, x + width, y + height, KEY_SHADOW);
                }
                if (active && (selected || style == ButtonStyle.PRIMARY)) {
                    graphics.fill(x, y + height - 2, x + width, y + height, TEAL);
                }
            }
            if (active && isFocused()) graphics.outline(x, y, width, height, TEXT);

            Font font = Minecraft.getInstance().font;
            int available = Math.max(1, width - 10);
            String label = getMessage().getString();
            if (font.width(label) > available) {
                label = font.plainSubstrByWidth(label, Math.max(1, available - font.width("…"))) + "…";
            }
            int textX = style == ButtonStyle.ROW ? x + 6 : x + (width - font.width(label)) / 2;
            int textY = y + (height - font.lineHeight) / 2;
            graphics.text(font, label, textX, textY, color, false);
        }
    }
}
