package dev.keyset.editor;

/** Keeps settings and the live preview in disjoint areas at every supported GUI size. */
public record PersonalizationLayout(int x, int y, int width, int height, int innerX, int totalInnerWidth,
                                    int settingsWidth, int contentY, int settingsBottom, int footerY,
                                    Rect preview, boolean sidePreview) {
    public record Rect(int x, int y, int width, int height) {}

    public static PersonalizationLayout forScreen(int width, int height) {
        int panelWidth = Math.max(1, Math.min(760, width - 16));
        int panelHeight = Math.max(1, Math.min(420, height - 16));
        int x = (width - panelWidth) / 2, y = (height - panelHeight) / 2;
        int innerX = x + 12, innerWidth = Math.max(1, panelWidth - 24);
        int footerY = y + panelHeight - 28;
        boolean side = innerWidth >= 420;
        int settingsWidth = side ? Math.min(360, innerWidth - 172) : innerWidth;
        Rect preview = side
                ? new Rect(innerX + settingsWidth + 12, y + 34,
                        innerWidth - settingsWidth - 12, footerY - 16 - (y + 34))
                : new Rect(innerX, footerY - 60, innerWidth, 48);
        int settingsBottom = side ? footerY - 16 : preview.y() - 10;
        return new PersonalizationLayout(x, y, panelWidth, panelHeight, innerX, innerWidth,
                settingsWidth, y + 58, settingsBottom, footerY, preview, side);
    }
}
