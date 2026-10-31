package dev.keyset.editor;

import dev.keyset.profile.AppearancePalette;

/** Square pixel keycaps shared by the editor and its live appearance preview. */
public final class KeycapPainter {
    @FunctionalInterface
    public interface Canvas { void fill(int left, int top, int right, int bottom, int color); }

    /** Visible top-face rectangle, excluding the shadow and front bevel. */
    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Keycap face dimensions must be positive");
        }
    }

    public record State(boolean hovered, boolean selected, boolean assigned, boolean conflict,
                        boolean focused, boolean free) {}

    private KeycapPainter() {}

    public static void draw(Canvas canvas, int x, int y, int width, int height,
                            AppearancePalette palette, State state) {
        if (width < 1 || height < 1) return;
        var colors = palette.keyColors(state.assigned(), state.conflict(), state.free());
        boolean lit = state.hovered() || state.selected() || state.focused();
        int edge = state.selected() || state.focused() ? palette.text()
                : lit ? mix(colors.edge(), palette.text(), .32) : colors.edge();
        Bounds face = faceBounds(x, y, width, height);
        // Keep the base and sloping front inside the hitbox, even on tiny previews.
        block(canvas, x, y + 1, width, height - 1, palette.keyShadow());
        if (width <= 4 || height <= 5) {
            block(canvas, face.x(), face.y(), face.width(), face.height(), colors.face());
            return;
        }
        int base = mix(colors.face(), 0x000000, .28);
        block(canvas, x, y, width, height - 1, edge);
        block(canvas, x + 1, y + 1, width - 2, height - 2, base);
        // A smaller top face and darker front bevel make the physical key shape visible.
        block(canvas, face.x(), face.y(), face.width(), face.height(), colors.face());
        int rim = mix(colors.face(), palette.text(), .16);
        canvas.fill(face.x(), face.y(), face.x() + face.width(), face.y() + 1, rim);
        canvas.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, base);
        if (state.assigned() || state.conflict() || state.selected()) {
            canvas.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, colors.edge());
        }
    }

    /** Returns the same top-face rectangle filled by {@link #draw}. */
    public static Bounds faceBounds(int x, int y, int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (safeWidth <= 4 || safeHeight <= 5) {
            return new Bounds(x, y, safeWidth, Math.max(1, safeHeight - 1));
        }
        int depth = safeHeight >= 14 ? 3 : 2;
        int inset = safeWidth >= 18 && safeHeight >= 16 ? 2 : 1;
        return new Bounds(x + inset, y + 1, safeWidth - inset * 2, safeHeight - depth - 2);
    }

    public static int labelColor(AppearancePalette palette, State state) {
        return palette.keyLegendColor(state.assigned(), state.conflict(), state.free(),
            state.hovered() || state.selected() || state.focused());
    }

    private static void block(Canvas canvas, int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0) canvas.fill(x, y, x + width, y + height, color);
    }

    private static int mix(int a, int b, double amount) {
        return 0xFF000000 | AppearancePalette.composite(a, b, amount);
    }
}
