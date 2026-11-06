package dev.keyset.editor;

/**
 * Picks the GUI scale for Keyset's screens from the physical window, independent of the game's GUI scale.
 * Only whole scales are used, so the pixel font stays crisp.
 */
public final class EditorScale {
    public enum Mode { AUTO, SMALLER, LARGER, GAME }

    /** The smallest GUI size that keeps the two-panel editor. */
    private static final int TWO_PANEL_WIDTH = 640, TWO_PANEL_HEIGHT = 360;
    /** The smallest GUI size Minecraft itself allows. */
    private static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    /** The GUI height the automatic scale aims for; the bias rounds up before the text gets too small. */
    private static final double COMFORTABLE_HEIGHT = 540, ROUND_UP_BIAS = 0.67;

    private EditorScale() {}

    public static int automatic(int windowWidth, int windowHeight) {
        int largest = Math.max(1, Math.min(windowWidth / TWO_PANEL_WIDTH, windowHeight / TWO_PANEL_HEIGHT));
        int wanted = (int) Math.floor(windowHeight / COMFORTABLE_HEIGHT + ROUND_UP_BIAS);
        return Math.max(1, Math.min(wanted, largest));
    }

    public static int forMode(Mode mode, int windowWidth, int windowHeight, int gameScale) {
        int automatic = automatic(windowWidth, windowHeight);
        return switch (mode) {
            case AUTO -> automatic;
            case SMALLER -> Math.max(1, automatic - 1);
            case LARGER -> Math.min(automatic + 1,
                Math.max(automatic, Math.min(windowWidth / MIN_WIDTH, windowHeight / MIN_HEIGHT)));
            case GAME -> gameScale;
        };
    }
}
