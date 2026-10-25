package dev.keyset.editor;

import java.util.List;

/** Pure geometry for the mouse diagram and its clickable controls. */
public record MouseDiagramLayout(Rect body, List<Part> buttons, Rect extraLabel) {
    private static final int BODY_MAX_HEIGHT = 128;
    private static final int BODY_MIN_HEIGHT = 72;

    public MouseDiagramLayout {
        buttons = List.copyOf(buttons);
    }

    public enum Control {
        LEFT, RIGHT, MIDDLE, SIDE4, SIDE5, EXTRA6, EXTRA7, EXTRA8
    }

    public record Part(Control control, Rect bounds) {}

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    /**
     * Creates a top-view mouse layout inside the supplied editor area.
     * Scale is clamped to 1..100 percent so the layout always fits its height.
     */
    public static MouseDiagramLayout forArea(int x, int y, int width, int height, int scalePercent) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Mouse diagram area must have positive dimensions");
        }

        int scale = Math.max(1, Math.min(100, scalePercent));
        int availableHeight = Math.max(1, height - 8);
        int nominalHeight = Math.min(BODY_MAX_HEIGHT, availableHeight);
        int scaledHeight = scaled(nominalHeight, scale);
        int minimumHeight = availableHeight >= BODY_MIN_HEIGHT
            ? BODY_MIN_HEIGHT
            : Math.min(32, availableHeight);
        int bodyHeight = Math.min(availableHeight, Math.max(minimumHeight, scaledHeight));
        int bodyWidth = even(Math.round(bodyHeight * 0.82f));

        int sideWidth = Math.max(18, scaled(22, scale));
        int sideOverhang = sideWidth - 4;
        int extraGap = Math.max(16, scaled(20, scale));
        int extraWidth = Math.max(22, Math.min(width < 224 ? 24 : 28, scaled(28, scale)));
        int extraHeight = Math.max(18, scaled(20, scale));
        int extraSpacing = scaled(5, scale);
        int extraRowWidth = extraWidth * 3 + extraSpacing * 2;
        int maximumBodyWidth = width - sideOverhang - 8 - extraRowWidth;
        while (bodyHeight > 32 && bodyWidth > maximumBodyWidth) {
            bodyHeight--;
            bodyWidth = even(Math.round(bodyHeight * 0.82f));
        }
        extraGap = Math.min(extraGap, Math.max(4, width - sideOverhang - bodyWidth - extraRowWidth));
        int groupWidth = sideOverhang + bodyWidth + extraGap + extraRowWidth;

        int groupX = x + (width - groupWidth) / 2;
        int bodyX = groupX + sideOverhang;
        int bodyY = y + (height - bodyHeight) / 2;
        Rect body = new Rect(bodyX, bodyY, bodyWidth, bodyHeight);

        int topInset = Math.max(6, Math.min(8, scaled(8, scale)));
        int horizontalInset = Math.min(topInset, Math.max(1, bodyWidth / 8));
        int wheelChannel = Math.min(Math.max(16, scaledEven(18, scale)), evenFloor(bodyWidth / 3));
        int primaryWidth = Math.max(1, (bodyWidth - horizontalInset * 2 - wheelChannel) / 2);
        int primaryHeight = Math.max(1, Math.round(bodyHeight * 0.44f));
        int primaryY = bodyY + topInset;
        int centerX = bodyX + bodyWidth / 2;

        Rect left = new Rect(bodyX + horizontalInset, primaryY, primaryWidth, primaryHeight);
        Rect right = new Rect(centerX + wheelChannel / 2, primaryY, primaryWidth, primaryHeight);
        Rect middle = new Rect(centerX - wheelChannel / 2, primaryY, wheelChannel, primaryHeight);

        int sideBottomInset = Math.min(2, bodyHeight / 10);
        int sideBottom = bodyHeight - sideBottomInset;
        int sideHeight = Math.min(Math.max(16, scaled(18, scale)), Math.max(1, (bodyHeight - 8) / 2));
        int sideOneY = bodyY + Math.round(bodyHeight * 0.49f);
        int sideTwoY = sideOneY + sideHeight + 5;
        if (sideTwoY - bodyY + sideHeight > sideBottom) {
            sideTwoY = bodyY + sideBottom - sideHeight;
            sideOneY = Math.min(sideOneY, sideTwoY - sideHeight - 4);
        }
        int sideX = bodyX - sideOverhang;
        Rect side4 = new Rect(sideX, sideOneY, sideWidth, sideHeight);
        Rect side5 = new Rect(sideX, sideTwoY, sideWidth, sideHeight);

        int extraX = body.right() + extraGap;
        int extraCenterY = bodyY + Math.round(bodyHeight * 0.58f);
        int extraY = extraCenterY - extraHeight / 2;
        int labelHeight = Math.min(scaled(12, scale), Math.max(4, bodyHeight / 6));
        int labelGap = Math.min(scaled(4, scale), Math.max(2, bodyHeight / 16));
        Rect extraLabel = new Rect(extraX, extraY - labelGap - labelHeight, extraRowWidth, labelHeight);

        Rect extra6 = new Rect(extraX, extraY, extraWidth, extraHeight);
        Rect extra7 = new Rect(extra6.right() + extraSpacing, extraY, extraWidth, extraHeight);
        Rect extra8 = new Rect(extra7.right() + extraSpacing, extraY, extraWidth, extraHeight);

        return new MouseDiagramLayout(body, List.of(
            new Part(Control.LEFT, left),
            new Part(Control.RIGHT, right),
            new Part(Control.MIDDLE, middle),
            new Part(Control.SIDE4, side4),
            new Part(Control.SIDE5, side5),
            new Part(Control.EXTRA6, extra6),
            new Part(Control.EXTRA7, extra7),
            new Part(Control.EXTRA8, extra8)), extraLabel);
    }

    private static int scaled(int value, int percent) {
        return Math.max(1, (value * percent + 50) / 100);
    }

    private static int scaledEven(int value, int percent) {
        return Math.max(2, even(scaled(value, percent)));
    }

    private static int even(int value) {
        return Math.max(2, Math.round(value / 2.0f) * 2);
    }

    private static int evenFloor(int value) {
        return Math.max(2, value - Math.floorMod(value, 2));
    }
}
