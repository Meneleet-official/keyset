package dev.keyset.editor;

/** Relative control bounds for the action panel footer, including narrow split panels. */
public record EditorFooterLayout(int width, int doneWidth, int resetAllWidth, int undoWidth, int actionWidth) {
    public static EditorFooterLayout forWidth(int width) {
        boolean compact = width < 390;
        int done = compact ? 64 : 80;
        int resetAll = compact ? 80 : 96;
        int undo = compact ? 30 : 64;
        int undoX = width - done - resetAll - undo - 12;
        return new EditorFooterLayout(width, done, resetAll, undo, Math.max(1, (undoX - 10) / 2));
    }
    public int doneX() { return width - doneWidth; }
    public int resetAllX() { return doneX() - 6 - resetAllWidth; }
    public int undoX() { return resetAllX() - 6 - undoWidth; }
    public int resetX() { return actionWidth + 4; }
}
