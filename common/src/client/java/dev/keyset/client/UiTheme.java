package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.UiAppearance;

/** Colors derived from the current Keyset appearance preferences. */
public final class UiTheme {
    private static AppearancePalette cachedPalette;
    private static long cachedRevision = Long.MIN_VALUE;

    private UiTheme() {}

    private static AppearancePalette palette() {
        long revision = KeysetClient.revision();
        if (cachedPalette != null && cachedRevision == revision) return cachedPalette;
        try {
            cachedPalette = AppearancePalette.from(KeysetClient.appearance());
            cachedRevision = KeysetClient.revision();
            return cachedPalette;
        } catch (RuntimeException ignored) {
            return AppearancePalette.from(UiAppearance.defaults());
        }
    }

    public static int panel() { return palette().panel(); }
    public static int panelLight() { return palette().panelLight(); }
    public static int border() { return palette().border(); }
    public static int text() { return palette().text(); }
    public static int muted() { return palette().muted(); }
    public static int accent() { return palette().accent(); }
    public static int accentText() { return palette().accentText(); }
    public static int red() { return palette().red(); }
    public static int conflictSurface() { return palette().conflictSurface(); }
    public static int conflictHover() { return palette().conflictHover(); }
    public static int control() { return palette().control(); }
    public static int hover() { return palette().hover(); }
    public static int selected() { return palette().selected(); }
    public static int row() { return palette().row(); }
    public static int rowAlternate() { return palette().rowAlternate(); }
    public static int keyShadow() { return palette().keyShadow(); }
    public static int keyFace() { return palette().keyFace(); }
    public static int keyEdge() { return palette().keyEdge(); }
    public static AppearancePalette.KeyColors keyColors(boolean assigned, boolean conflict, boolean unbound) {
        return palette().keyColors(assigned, conflict, unbound);
    }
    public static int effectiveSurface() { return palette().effectiveSurface(); }
    public static int keycapSurface(int overlay) { return palette().keycapSurface(overlay); }
    public static int widgetSurface(int overlay) { return palette().widgetSurface(overlay); }
    public static AppearancePalette currentPalette() { return palette(); }
}
