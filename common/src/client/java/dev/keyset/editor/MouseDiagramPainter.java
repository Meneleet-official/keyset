package dev.keyset.editor;

import dev.keyset.editor.MouseContours.Shape;
import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.profile.AppearancePalette;

/** Stepped pixel mouse artwork; all colors and hit regions remain configurable. */
public final class MouseDiagramPainter {
    @FunctionalInterface
    public interface Canvas { void fill(int left, int top, int right, int bottom, int color); }
    public record State(boolean hovered, boolean selected, boolean assigned, boolean conflict, boolean focused,
                        boolean free) {
        public State(boolean hovered, boolean selected, boolean assigned, boolean conflict, boolean focused) {
            this(hovered, selected, assigned, conflict, focused, false);
        }
    }

    private MouseDiagramPainter() {}

    public static void shell(Canvas canvas, MouseDiagramLayout layout, AppearancePalette palette) {
        shell(canvas, layout.body(), palette);
        Rect side = layout.sideBody();
        if (side.width() < 1 || side.height() < 1) return;
        shellSurface(canvas, Shape.SIDE_BODY, side, palette);
        // A short inset seam marks the primary button at the nose without a protruding overlay.
        int frontX = side.x() + Math.max(2, side.width() / 24);
        int frontY = side.y() + Math.max(3, side.height() / 5);
        int frontEnd = side.x() + Math.max(3, side.width() / 3);
        if (frontEnd > frontX && frontY < side.bottom()) {
            canvas.fill(frontX, frontY, frontEnd, frontY + 1,
                opaque(mix(shellFace(palette), shellInk(palette), .30)));
        }
        // A straight seam gives the rectangular side panel a shallow base.
        int seamColor = opaque(mix(shellFace(palette), shellInk(palette), .16));
        int seamY = side.y() + side.height() * 88 / 100;
        int seamStart = side.x() + side.width() * 4 / 100;
        int seamEnd = side.x() + side.width() * 96 / 100;
        canvas.fill(seamStart, seamY, seamEnd, seamY + Math.max(1, side.height() / 28), seamColor);
    }

    public static void shell(Canvas canvas, Rect body, AppearancePalette palette) {
        if (body.width() < 1 || body.height() < 1) return;
        shellSurface(canvas, Shape.BODY, body, palette);
    }

    private static void shellSurface(Canvas canvas, Shape shape, Rect body, AppearancePalette palette) {
        int face = shellFace(palette);
        int edge = opaque(mix(face, shellInk(palette), .30));
        MouseContours.fill(canvas, shape, translated(body, 0, 1), 0x65000000);
        MouseContours.fill(canvas, shape, body, edge);
        MouseContours.fill(canvas, shape, inset(body, 2), face);
        // A short hard pixel highlight gives the shell depth without a soft gradient.
        if (shape != Shape.SIDE_BODY) {
            Rect highlight = relative(body, .38, .015, .24, .018);
            MouseContours.fill(canvas, shape, highlight, opaque(mix(face, shellInk(palette), .2)));
        }
    }

    public static void button(Canvas canvas, Control control, Rect bounds, AppearancePalette palette, State state) {
        var colors = palette.keyColors(state.assigned(), state.conflict(), state.free());
        if (control.ordinal() >= Control.EXTRA6.ordinal()) {
            KeycapPainter.draw(canvas::fill, bounds.x(), bounds.y(), bounds.width(), bounds.height(), palette,
                new KeycapPainter.State(state.hovered(), state.selected(), state.assigned(), state.conflict(),
                    state.focused(), state.free()));
            return;
        }
        Shape shape = switch (control) {
            case LEFT -> Shape.LEFT;
            case RIGHT -> Shape.RIGHT;
            case MIDDLE -> Shape.WHEEL;
            case SIDE5 -> Shape.SIDE_FRONT;
            case SIDE4 -> Shape.SIDE_REAR;
            default -> throw new IllegalStateException("Unexpected control");
        };
        int edge = state.selected() || state.focused() ? palette.text()
            : state.hovered() ? opaque(mix(colors.edge(), palette.text(), .35)) : colors.edge();
        boolean sideButton = control == Control.SIDE4 || control == Control.SIDE5;
        MouseContours.fill(canvas, shape, translated(bounds, 0, 1), opaque(mix(colors.face(), 0x000000, .65)));
        if (sideButton) {
            // Neutral outer rim separates the raised key from the shell in every binding state.
            int rim = opaque(mix(shellFace(palette), shellInk(palette),
                state.hovered() || state.selected() || state.focused() ? .85 : .58));
            MouseContours.fill(canvas, shape, bounds, rim);
            MouseContours.fill(canvas, shape, inset(bounds, 1), edge);
        } else MouseContours.fill(canvas, shape, bounds, edge);
        Rect face = inset(bounds, control == Control.MIDDLE ? 1 : 2);
        MouseContours.fill(canvas, shape, face, colors.face());
        if (sideButton && bounds.width() >= 8 && bounds.height() >= 8) {
            canvas.fill(face.x(), face.y(), face.right(), face.y() + 1,
                opaque(mix(colors.face(), palette.text(), .22)));
            canvas.fill(face.x(), bounds.bottom() - 2, face.right(), bounds.bottom() - 1,
                opaque(mix(colors.face(), 0, .45)));
        }
        if (control == Control.MIDDLE) {
            int rib = opaque(mix(colors.face(), labelColor(control, palette, state), .32));
            ribs(canvas, face, rib, true);
        }
    }

    /** The full recessed wheel is clickable; its small caption sits on the wheel itself. */
    public static int wheelHeight(Rect bounds) { return bounds.height(); }

    public static int labelColor(Control control, AppearancePalette palette, State state) {
        return palette.keyLegendColor(state.assigned(), state.conflict(), state.free(),
            state.hovered() || state.selected() || state.focused());
    }

    private static void ribs(Canvas canvas, Rect wheel, int color, boolean caption) {
        int inset = Math.max(1, wheel.width() / 5);
        int gap = Math.max(2, wheel.height() / 12);
        int center = wheel.y() + wheel.height() / 2;
        int captionHalfHeight = Math.max(4, Math.min(10, wheel.height() / 5));
        for (int y = wheel.y() + 3; y < wheel.bottom() - 2; y += gap) {
            if (caption && y >= center - captionHalfHeight && y <= center + captionHalfHeight) continue;
            int left = wheel.x() + inset, right = wheel.right() - inset;
            if (right > left && MouseContours.contains(Shape.WHEEL, wheel, left + .5, y + .5)
                    && MouseContours.contains(Shape.WHEEL, wheel, right - .5, y + .5))
                canvas.fill(left, y, right, y + 1, color);
        }
    }

    private static int shellFace(AppearancePalette palette) {
        return palette.mouseBodyColor();
    }

    private static int shellInk(AppearancePalette palette) {
        return AppearancePalette.contrastRatio(shellFace(palette), 0xFFFFFF)
            >= AppearancePalette.contrastRatio(shellFace(palette), 0) ? 0xFFFFFF : 0;
    }

    private static Rect relative(Rect rect, double x, double y, double width, double height) {
        return new Rect(rect.x() + (int) Math.round(rect.width() * x),
            rect.y() + (int) Math.round(rect.height() * y),
            Math.max(1, (int) Math.round(rect.width() * width)),
            Math.max(1, (int) Math.round(rect.height() * height)));
    }
    private static Rect inset(Rect rect, int amount) {
        if (rect.width() <= amount * 2 || rect.height() <= amount * 2) return rect;
        return new Rect(rect.x() + amount, rect.y() + amount, rect.width() - amount * 2, rect.height() - amount * 2);
    }
    private static Rect translated(Rect rect, int x, int y) {
        return new Rect(rect.x() + x, rect.y() + y, rect.width(), rect.height());
    }
    private static int mix(int a, int b, double amount) { return AppearancePalette.composite(a, b, amount); }
    private static int opaque(int color) { return 0xFF000000 | color & 0xFFFFFF; }
}

