package dev.keyset.editor;

import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.profile.AppearancePalette;

/** Pixel geometry shared by the game renderer and offline visual previews. */
public final class MouseDiagramPainter {
    @FunctionalInterface
    public interface Canvas { void fill(int left, int top, int right, int bottom, int color); }
    public record State(boolean hovered, boolean selected, boolean assigned, boolean conflict, boolean focused) {}

    private MouseDiagramPainter() {}

    public static void shell(Canvas canvas, Rect body, AppearancePalette palette) {
        int face = shellFace(palette);
        int edge = mix(face, palette.text(), 0.24);
        silhouette(canvas, body.x(), body.y() + 2, body.width(), body.height(), 0x65000000);
        silhouette(canvas, body.x(), body.y(), body.width(), body.height(), opaque(edge));
        int innerWidth = body.width() - 2, innerHeight = body.height() - 2;
        for (int row = 0; row < innerHeight; row++) {
            int inset = silhouetteInset(innerWidth, innerHeight, row);
            double amount = row / (double) Math.max(1, innerHeight - 1);
            int color = mix(face, palette.text() == 0xFF000000 ? 0xFFFFFF : 0x000000, amount * 0.09);
            canvas.fill(body.x() + 1 + inset, body.y() + 1 + row,
                body.right() - 1 - inset, body.y() + 2 + row, opaque(color));
        }
    }

    public static void button(Canvas canvas, Control control, Rect bounds, AppearancePalette palette, State state) {
        int foreground = labelColor(control, palette, state);
        int overlay = state.conflict() ? state.hovered() ? palette.conflictHover() : palette.conflictSurface()
            : state.selected() ? palette.selected() : state.hovered() ? palette.hover() : palette.keyFace();
        int face = readable(composite(palette.effectiveSurface(), overlay), foreground);
        int edge = mix(shellFace(palette), palette.text(), state.hovered() ? 0.44 : 0.26);
        int mark = state.conflict() ? palette.red() : palette.accent();
        if (state.selected() || state.focused()) edge = mark;

        if (control == Control.MIDDLE) {
            int wheelWidth = Math.max(6, Math.min(12, bounds.width() - 4));
            int wheelX = bounds.x() + (bounds.width() - wheelWidth) / 2;
            int wheelHeight = wheelHeight(bounds);
            pill(canvas, wheelX, bounds.y() + 1, wheelWidth, wheelHeight, 3,
                state.assigned() ? mark : opaque(edge));
            pill(canvas, wheelX + 1, bounds.y() + 2, wheelWidth - 2, wheelHeight - 2, 2, opaque(face));
            int rib = mix(face, foreground, 0.40);
            int ribInset = wheelWidth <= 8 ? 2 : 3;
            for (int y = bounds.y() + 5; y < bounds.y() + wheelHeight - 2; y += 4) {
                canvas.fill(wheelX + ribInset, y, wheelX + wheelWidth - ribInset, y + 1, opaque(rib));
            }
            return;
        }

        boolean main = control == Control.LEFT || control == Control.RIGHT;
        if (main) {
            boolean right = control == Control.RIGHT;
            mainCap(canvas, bounds.x(), bounds.y() + 2, bounds.width(), bounds.height(), right, 0x70000000);
            mainCap(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height() - 1, right, opaque(edge));
            mainCap(canvas, bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 3, right, opaque(face));
        } else {
            pill(canvas, bounds.x(), bounds.y() + 2, bounds.width(), bounds.height(), 4, 0x70000000);
            pill(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height() - 1, 4, opaque(edge));
            pill(canvas, bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 3, 3, opaque(face));
            if (bounds.width() > 12) canvas.fill(bounds.x() + 5, bounds.y() + 2,
                bounds.right() - 5, bounds.y() + 3, opaque(mix(face, foreground, 0.12)));
        }
        if (state.assigned() || state.selected() || state.conflict()) {
            int markWidth = Math.max(4, Math.min(14, bounds.width() / 3));
            int center = bounds.x() + bounds.width() / 2;
            canvas.fill(center - markWidth / 2, bounds.bottom() - 5,
                center + (markWidth + 1) / 2, bounds.bottom() - 3, mark);
        }
    }

    public static int wheelHeight(Rect bounds) { return Math.max(8, bounds.height() - 16); }

    public static int labelColor(Control control, AppearancePalette palette, State state) {
        // M3 is printed below the wheel on the palm shell, not on the selected cap.
        if (control == Control.MIDDLE) return palette.text();
        return state.conflict() ? palette.red() : state.selected() ? palette.accentText() : palette.text();
    }

    /** A superellipse has continuous, symmetric shoulders and a rounded palm end. */
    public static int silhouetteInset(int width, int height, int row) {
        if (width <= 2 || height <= 2) return 0;
        double y = Math.abs(2 * row + 1 - height) / (double) height;
        double span = Math.pow(Math.max(0.0, 1.0 - Math.pow(y, 3.2)), 1.0 / 3.2);
        return Math.max(0, Math.min((width - 1) / 2, (int) Math.ceil((1.0 - span) * width / 2.0)));
    }

    private static int shellFace(AppearancePalette palette) {
        int neutral = palette.text() == 0xFF000000 ? 0xD2D3D1 : 0x181C1E;
        return readable(mix(palette.effectiveSurface(), neutral, 0.65), palette.text());
    }

    private static void silhouette(Canvas canvas, int x, int y, int width, int height, int color) {
        for (int row = 0; row < height; row++) {
            int inset = silhouetteInset(width, height, row);
            canvas.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void mainCap(Canvas canvas, int x, int y, int width, int height, boolean right, int color) {
        if (width <= 0 || height <= 0) return;
        int radius = Math.max(2, Math.min(16, Math.min(width - 1, height / 2)));
        for (int row = 0; row < height; row++) {
            int outer = cornerInset(radius, row);
            int bottom = cornerInset(3, height - 1 - row);
            int inner = Math.max(cornerInset(3, row), bottom);
            outer = Math.max(outer, bottom);
            int left = right ? inner : outer, rightInset = right ? outer : inner;
            if (width > left + rightInset) canvas.fill(x + left, y + row, x + width - rightInset, y + row + 1, color);
        }
    }

    private static void pill(Canvas canvas, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int inset = Math.max(cornerInset(r, row), cornerInset(r, height - 1 - row));
            if (width > inset * 2) canvas.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int cornerInset(int radius, int row) {
        if (row < 0 || row >= radius) return 0;
        double dy = radius - row - 0.5;
        return Math.max(0, (int) Math.ceil(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy))));
    }

    private static int readable(int color, int foreground) {
        int endpoint = AppearancePalette.contrastRatio(foreground, 0xFFFFFF)
            > AppearancePalette.contrastRatio(foreground, 0x000000) ? 0xFFFFFF : 0x000000;
        for (int step = 0; step <= 100; step++) {
            int candidate = mix(color, endpoint, step / 100.0);
            if (AppearancePalette.contrastRatio(candidate, foreground) >= 4.5) return candidate;
        }
        return endpoint;
    }

    private static int composite(int base, int overlay) {
        return AppearancePalette.composite(base, overlay, ((overlay >>> 24) & 0xFF) / 255.0);
    }

    private static int mix(int first, int second, double amount) { return AppearancePalette.composite(first, second, amount); }
    private static int opaque(int color) { return 0xFF000000 | (color & 0xFFFFFF); }
}
