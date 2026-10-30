package dev.keyset.editor;

import java.util.Optional;

/** Fits a font label inside a keycap's visible top face with a one-pixel safety inset. */
public final class KeycapLabelLayout {
    private static final float TARGET_FACE_HEIGHT_RATIO = 0.78f;

    private KeycapLabelLayout() {}

    public record Placement(float x, float y, float scale) {}

    /** Glyph quads can rise above a font's nominal line, for example accented Cyrillic letters. */
    public static Optional<Placement> fitGlyphBounds(KeycapPainter.Bounds face, int left, int top,
                                                     int width, int height, float minimumScale) {
        return fit(face, width, height, minimumScale).map(placement -> new Placement(
            placement.x() - left * placement.scale(), placement.y() - top * placement.scale(), placement.scale()));
    }

    /** Aligns letters with different ascenders to the same readable scale and baseline. */
    public static Optional<Placement> fitGlyphLine(KeycapPainter.Bounds face, int left, int top,
                                                   int width, int height, int lineTop, int lineBottom,
                                                   float minimumScale) {
        int envelopeTop = Math.min(top, lineTop);
        int envelopeBottom = Math.max(top + height, lineBottom);
        return fit(face, width, envelopeBottom - envelopeTop, minimumScale, 1f)
            .map(placement -> new Placement(placement.x() - left * placement.scale(),
                placement.y() - envelopeTop * placement.scale(), placement.scale()));
    }

    /**
     * Returns a centered placement for a label, or empty when the face is too small
     * to fit the text with the requested minimum readable scale.
     */
    public static Optional<Placement> fit(KeycapPainter.Bounds face, int textWidth, int fontHeight,
                                         float minimumScale) {
        return fit(face, textWidth, fontHeight, minimumScale, TARGET_FACE_HEIGHT_RATIO);
    }

    private static Optional<Placement> fit(KeycapPainter.Bounds face, int textWidth, int fontHeight,
                                           float minimumScale, float targetHeightRatio) {
        if (face == null || textWidth < 1 || fontHeight < 1 || !Float.isFinite(minimumScale)
                || minimumScale < 0f || minimumScale > 1f) {
            return Optional.empty();
        }
        int availableWidth = face.width() - 2;
        int availableHeight = face.height() - 2;
        if (availableWidth < 1 || availableHeight < 1) return Optional.empty();

        float scale = Math.min(1f, Math.min(availableWidth / (float) textWidth,
            Math.min(availableHeight / (float) fontHeight,
                face.height() * targetHeightRatio / fontHeight)));
        if (!Float.isFinite(scale) || scale < minimumScale) return Optional.empty();

        return Optional.of(new Placement(face.x() + (face.width() - textWidth * scale) / 2f,
            face.y() + (face.height() - fontHeight * scale) / 2f, scale));
    }
}
