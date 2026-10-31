package dev.keyset.editor;

/** Responsive panel geometry, including mirrored splitter coordinates. */
public record EditorPanelLayout(int sidebarX, int sidebarWidth, int mainX, int mainWidth,
                                int screenWidth, boolean inputOnRight, boolean singlePane) {
    public static EditorPanelLayout forWidth(int width, double ratio, boolean inputOnRight, boolean singlePane) {
        if (singlePane) return new EditorPanelLayout(8, Math.max(1, width - 16), 8,
                Math.max(1, width - 16), width, inputOnRight, true);
        int available = Math.max(1, width - 44);
        int side = clampSidebarWidth(available, (int) Math.round(available * clampRatio(ratio)));
        int main = available - side;
        return new EditorPanelLayout(inputOnRight ? 28 + main : 16, side,
                inputOnRight ? 16 : 28 + side, main, width, inputOnRight, false);
    }

    public int dividerX() { return inputOnRight ? sidebarX - 6 : sidebarX + sidebarWidth + 6; }

    public int clampDividerX(int x) {
        int available = Math.max(1, screenWidth - 44);
        int requestedSide = inputOnRight ? screenWidth - 22 - x : x - 22;
        int side = clampSidebarWidth(available, requestedSide);
        return inputOnRight ? screenWidth - 22 - side : 22 + side;
    }

    public double ratioAtDivider(int x) {
        int clamped = clampDividerX(x);
        int side = inputOnRight ? screenWidth - 22 - clamped : clamped - 22;
        return clampRatio(side / (double) Math.max(1, screenWidth - 44));
    }

    private static int clampSidebarWidth(int available, int width) {
        int minSide = available >= 640 ? 380 : Math.min(280, Math.max(120, available * 32 / 100));
        int minMain = available >= 640 ? 260 : Math.min(240, Math.max(160, available * 42 / 100));
        if (minSide + minMain > available) {
            minSide = Math.max(1, available * minSide / (minSide + minMain));
            minMain = Math.max(1, available - minSide);
        }
        int minimum = Math.max(minSide, (int) Math.ceil(available * 0.35));
        int maximum = Math.min(available - minMain, (int) Math.floor(available * 0.65));
        return Math.max(minimum, Math.min(maximum, width));
    }

    private static double clampRatio(double ratio) {
        return Double.isFinite(ratio) ? Math.max(0.35, Math.min(0.65, ratio)) : 0.52;
    }
}
