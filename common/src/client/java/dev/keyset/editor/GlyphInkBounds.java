package dev.keyset.editor;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

/** Visible bitmap pixels, excluding transparent cells around a glyph. */
public record GlyphInkBounds(int x, int y, int width, int height) {
    public static Optional<GlyphInkBounds> scan(int width, int height, IntBinaryOperator argb) {
        int left = width, top = height, right = 0, bottom = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((argb.applyAsInt(x, y) >>> 24) == 0) continue;
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
            }
        }
        return right > left && bottom > top
            ? Optional.of(new GlyphInkBounds(left, top, right - left, bottom - top)) : Optional.empty();
    }
}
