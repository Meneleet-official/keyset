package dev.keyset.editor;

import java.util.List;

/** Keeps a readable device preview beside controls, or on its own page in smaller windows. */
public record ColorPickerLayout(int x, int y, int width, int height, int innerX, int innerWidth,
                                int contentTop, int contentBottom, int footerY, boolean pagedPreview,
                                Rect color, Rect hue, Rect hex, Rect samples, List<Rect> channels,
                                Rect preview, Rect pageTabs, Rect deviceTabs) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    public ColorPickerLayout { channels = List.copyOf(channels); }

    public static ColorPickerLayout forScreen(int screenWidth, int screenHeight, boolean hasPreview) {
        int width = Math.max(1, Math.min(hasPreview ? 760 : 420, screenWidth - 16));
        int height = Math.max(1, Math.min(hasPreview ? 420 : 256, screenHeight - 16));
        int x = (screenWidth - width) / 2, y = (screenHeight - height) / 2;
        int innerX = x + 12, innerWidth = Math.max(1, width - 24);
        boolean side = hasPreview && width >= 720 && height >= 320;
        boolean paged = hasPreview && !side;
        int footer = y + height - 28;
        int top = y + (paged ? 58 : 34), bottom = footer - 14;
        int bodyHeight = Math.max(1, bottom - top);
        int controlsWidth = side ? 300 : innerWidth;
        int leftWidth = Math.max(1, (controlsWidth - 12) / 2);
        int rightX = innerX + leftWidth + 12, rightWidth = Math.max(1, controlsWidth - leftWidth - 12);
        int areaHeight = Math.max(24, Math.min(224, bodyHeight - 48));
        Rect color = new Rect(innerX, top, leftWidth, areaHeight);
        Rect hue = new Rect(innerX, color.bottom() + 6, leftWidth, 14);
        Rect hex = new Rect(innerX, color.bottom() + 28, leftWidth, 20);
        Rect samples = new Rect(rightX, top, rightWidth, 20);
        int sliderY = samples.bottom() + 12;
        int sliderGap = Math.max(4, Math.min(40, (bodyHeight - 104) / 2));
        List<Rect> channels = List.of(new Rect(rightX, sliderY, rightWidth, 24),
            new Rect(rightX, sliderY + 24 + sliderGap, rightWidth, 24),
            new Rect(rightX, sliderY + 2 * (24 + sliderGap), rightWidth, 24));
        Rect preview = side ? new Rect(innerX + controlsWidth + 16, top,
            innerWidth - controlsWidth - 16, bodyHeight) : new Rect(innerX, top, innerWidth, bodyHeight);
        return new ColorPickerLayout(x, y, width, height, innerX, innerWidth, top, bottom, footer, paged,
            color, hue, hex, samples, channels, preview,
            new Rect(innerX, y + 34, innerWidth, 18),
            new Rect(preview.x(), preview.y(), preview.width(), 18));
    }
}
