package dev.keyset.editor;

import dev.keyset.profile.KeyboardFormFactor;

/** Keeps key legends readable; small preview panes pan across a full-size diagram. */
public record KeyboardPreviewViewport(int visibleWidth, int contentWidth, int diagramHeight,
                                      int scrollbarHeight, int offset) {
    public static KeyboardPreviewViewport forArea(int width, int height, KeyboardFormFactor form, double pan) {
        int visible = Math.max(1, width - 8);
        int content = Math.max(visible, KeyboardDiagramLayout.columns(form) * 14);
        int bar = content > visible ? 8 : 0;
        double position = Double.isFinite(pan) ? Math.max(0, Math.min(1, pan)) : 0;
        return new KeyboardPreviewViewport(visible, content, Math.max(1, height - 8 - bar), bar,
            (int) Math.round((content - visible) * position));
    }
}
