package dev.keyset.editor;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/** Builds a bounded, transparent ARGB image from the mouse artwork's rectangle canvas. */
public final class MouseArtworkRaster {
    /** A hard pixel budget keeps bad or accidentally unbounded preview sizes from exhausting memory. */
    private static final int MAX_PIXELS = 4_000_000;

    private MouseArtworkRaster() {}

    /**
     * Paints into a transparent image, compositing each fill source-over the pixels already drawn.
     * Dimensions must be positive and the image may contain at most {@value #MAX_PIXELS} pixels.
     */
    public static Image paint(int width, int height, Consumer<MouseDiagramPainter.Canvas> painter) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        long pixelCount = (long) width * height;
        if (pixelCount > MAX_PIXELS) {
            throw new IllegalArgumentException("Image exceeds the " + MAX_PIXELS + " pixel budget");
        }
        Objects.requireNonNull(painter, "painter");

        int[] pixels = new int[(int) pixelCount];
        painter.accept((left, top, right, bottom, color) -> {
            if (right <= left || bottom <= top || (color >>> 24) == 0) return;

            int clippedLeft = Math.max(0, left);
            int clippedTop = Math.max(0, top);
            int clippedRight = Math.min(width, right);
            int clippedBottom = Math.min(height, bottom);
            if (clippedLeft >= clippedRight || clippedTop >= clippedBottom) return;

            int alpha = color >>> 24;
            if (alpha == 255) {
                for (int y = clippedTop; y < clippedBottom; y++) {
                    int rowStart = y * width + clippedLeft;
                    Arrays.fill(pixels, rowStart, rowStart + clippedRight - clippedLeft, color);
                }
                return;
            }

            for (int y = clippedTop; y < clippedBottom; y++) {
                int index = y * width + clippedLeft;
                for (int x = clippedLeft; x < clippedRight; x++, index++) {
                    pixels[index] = sourceOver(color, pixels[index]);
                }
            }
        });
        return new Image(width, height, pixels);
    }

    private static int sourceOver(int source, int destination) {
        int sourceAlpha = source >>> 24;
        int destinationAlpha = destination >>> 24;
        int inverseSourceAlpha = 255 - sourceAlpha;
        int alphaNumerator = sourceAlpha * 255 + destinationAlpha * inverseSourceAlpha;
        int outputAlpha = (alphaNumerator + 127) / 255;

        int red = composeChannel(source >>> 16 & 0xFF, destination >>> 16 & 0xFF,
            sourceAlpha, destinationAlpha, inverseSourceAlpha, alphaNumerator);
        int green = composeChannel(source >>> 8 & 0xFF, destination >>> 8 & 0xFF,
            sourceAlpha, destinationAlpha, inverseSourceAlpha, alphaNumerator);
        int blue = composeChannel(source & 0xFF, destination & 0xFF,
            sourceAlpha, destinationAlpha, inverseSourceAlpha, alphaNumerator);
        return outputAlpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int composeChannel(int source, int destination, int sourceAlpha,
                                      int destinationAlpha, int inverseSourceAlpha, int alphaNumerator) {
        int numerator = source * sourceAlpha * 255
            + destination * destinationAlpha * inverseSourceAlpha;
        return (numerator + alphaNumerator / 2) / alphaNumerator;
    }

    /** Immutable ARGB pixels; pixel reads return the stored value without copying image data. */
    public static final class Image {
        private final int width;
        private final int height;
        private final int[] pixels;

        private Image(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }

        public int width() { return width; }
        public int height() { return height; }

        public int pixel(int x, int y) {
            if (x < 0 || x >= width || y < 0 || y >= height) {
                throw new IndexOutOfBoundsException("Pixel (" + x + ", " + y + ") outside " + width + "x" + height);
            }
            return pixels[y * width + x];
        }
    }
}
