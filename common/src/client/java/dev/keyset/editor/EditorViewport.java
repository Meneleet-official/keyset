package dev.keyset.editor;

/** Layout in GUI units at the scale chosen by {@link EditorScale}. */
public record EditorViewport(boolean singlePane, int headerHeight, int tabsY, int contentTop,
                             int searchY, int chipY, int rowsTop, int rowsBottom, int rowHeight,
                             int footerY, int keyboardPanelY, int inputControlsY,
                             int categoryControlsY, int categoryListTop, int categoryListBottom,
                             int searchHeight, int controlHeight, int keyStep) {
    public enum Page { ACTIONS, INPUT, FILTERS }
    public enum ScrollTarget { NONE, ACTIONS, FILTERS }

    public static EditorViewport forSize(int width, int height) {
        boolean single = width < 640 || height < 360;
        if (single) {
            int step = Math.max(8, Math.min(14, (height - 144) / 6));
            return new EditorViewport(true, 32, 34, 56, 58, 80, 82, height - 30, 16,
                height - 24, 82, height - 50, 58, 82, height - 30, 18, 18, step);
        }
        int keys = 22;
        // Short windows give the title row to the action list; the input column scrolls on its own.
        int titleSpace = height < 440 ? 28 : 0;
        return new EditorViewport(false, 58, 60, 64, 100 - titleSpace, 128 - titleSpace, 174 - titleSpace,
            height - 64, 25, height - 40, 108, height - 72, 332, 362,
            height - 64, 24, 24, keys);
    }

    public int actionRowsTop(boolean hasFilterChips) {
        return singlePane && hasFilterChips ? chipY + 20 : rowsTop;
    }

    public int visibleActionRows(boolean hasFilterChips) {
        return Math.max(1, (rowsBottom - actionRowsTop(hasFilterChips)) / rowHeight);
    }

    /** Resolves the scroll target when compact pages share the same screen rectangle. */
    public static ScrollTarget scrollTarget(boolean singlePane, Page page, boolean overActions, boolean overFilters) {
        if (singlePane) {
            return switch (page) {
                case ACTIONS -> overActions ? ScrollTarget.ACTIONS : ScrollTarget.NONE;
                case FILTERS -> overFilters ? ScrollTarget.FILTERS : ScrollTarget.NONE;
                case INPUT -> ScrollTarget.NONE;
            };
        }
        if (overActions) return ScrollTarget.ACTIONS;
        if (overFilters) return ScrollTarget.FILTERS;
        return ScrollTarget.NONE;
    }
}
