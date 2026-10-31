package dev.keyset.editor;

import java.util.List;

/** Uniformly scaled artwork and shape-aware hit regions for the two mouse views. */
public record MouseDiagramLayout(Rect body, Rect sideBody, Rect topViewLabel, Rect sideViewLabel,
                                List<Part> buttons, Rect extraLabel) {
    private static final int ART_WIDTH = 286, ART_HEIGHT = 172;

    public MouseDiagramLayout { buttons = List.copyOf(buttons); }

    public MouseDiagramLayout(Rect body, List<Part> buttons, Rect extraLabel) {
        this(body, new Rect(0, 0, 0, 0), new Rect(0, 0, 0, 0), new Rect(0, 0, 0, 0), buttons, extraLabel);
    }

    public enum Control { LEFT, RIGHT, MIDDLE, SIDE4, SIDE5, EXTRA6, EXTRA7, EXTRA8 }
    public record Part(Control control, Rect bounds) {}
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < right() && py < bottom();
        }
        public Rect scaled(int factor) { return new Rect(x * factor, y * factor, width * factor, height * factor); }
    }

    public static MouseDiagramLayout forArea(int x, int y, int width, int height, int scalePercent) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Mouse area must be positive");
        double fit = Math.min(Math.max(1, width - 8) / (double) ART_WIDTH,
                             Math.max(1, height - 6) / (double) ART_HEIGHT);
        double requested = fit * Math.max(1, Math.min(100, scalePercent)) / 100.0;
        double scale = requested;
        int left = x + (width - (int) Math.round(ART_WIDTH * scale)) / 2;
        int top = y + (height - (int) Math.round(ART_HEIGHT * scale)) / 2;
        Transform t = new Transform(left, top, scale);
        Rect body = t.rect(0, 18, 124, 146);
        Rect side = t.rect(132, 43, 154, 70);
        return new MouseDiagramLayout(body, side, t.rect(0, 0, 124, 12), t.rect(132, 23, 154, 12), List.of(
            new Part(Control.LEFT, t.rect(3, 20, 58, 66)),
            new Part(Control.RIGHT, t.rect(63, 20, 58, 66)),
            new Part(Control.MIDDLE, t.rect(55, 29, 14, 35)),
            new Part(Control.SIDE5, t.rect(174, 72, 43, 22)),
            new Part(Control.SIDE4, t.rect(221, 72, 43, 22)),
            new Part(Control.EXTRA6, t.rect(145, 137, 36, 22)),
            new Part(Control.EXTRA7, t.rect(188, 137, 36, 22)),
            new Part(Control.EXTRA8, t.rect(231, 137, 36, 22))), t.rect(136, 119, 140, 12));
    }

    /** The wheel reserves its actual shape, rather than stealing either primary button's rectangle. */
    public Control controlAt(double x, double y) {
        Part wheel = part(Control.MIDDLE);
        if (wheel != null && contains(wheel, x, y)) return Control.MIDDLE;
        for (Part part : buttons) {
            if (part.control() != Control.MIDDLE && contains(part, x, y)) return part.control();
        }
        return null;
    }

    public boolean hitTest(Control control, double x, double y) { return controlAt(x, y) == control; }

    public Part part(Control control) {
        return buttons.stream().filter(part -> part.control() == control).findFirst().orElse(null);
    }

    private boolean contains(Part part, double x, double y) {
        if (!part.bounds().contains(x, y)) return false;
        var shape = switch (part.control()) {
            case LEFT -> MouseContours.Shape.LEFT;
            case RIGHT -> MouseContours.Shape.RIGHT;
            case MIDDLE -> MouseContours.Shape.WHEEL;
            case SIDE5 -> MouseContours.Shape.SIDE_FRONT;
            case SIDE4 -> MouseContours.Shape.SIDE_REAR;
            default -> null;
        };
        return shape == null || MouseContours.contains(shape, part.bounds(), x, y);
    }

    /** Artwork is sampled at a higher resolution while hitboxes remain in GUI coordinates. */
    public MouseDiagramLayout scaled(int factor) {
        return new MouseDiagramLayout(body.scaled(factor), sideBody.scaled(factor), topViewLabel.scaled(factor),
            sideViewLabel.scaled(factor), buttons.stream().map(part -> new Part(part.control(),
                part.bounds().scaled(factor))).toList(), extraLabel.scaled(factor));
    }

    private record Transform(int x, int y, double scale) {
        Rect rect(int left, int top, int width, int height) {
            int firstX = (int) Math.round(left * scale), firstY = (int) Math.round(top * scale);
            return new Rect(x + firstX, y + firstY,
                Math.max(1, (int) Math.round((left + width) * scale) - firstX),
                Math.max(1, (int) Math.round((top + height) * scale) - firstY));
        }
    }
}

