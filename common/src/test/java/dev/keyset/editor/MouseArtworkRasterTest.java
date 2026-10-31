package dev.keyset.editor;

import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.UiAppearance;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseArtworkRasterTest {
    @Test
    void compositesTranslucentLayersSourceOverAndSkipsTransparentSources() {
        var image = MouseArtworkRaster.paint(2, 1, canvas -> {
            canvas.fill(0, 0, 2, 1, 0x80FF0000);
            canvas.fill(0, 0, 1, 1, 0x800000FF);
            canvas.fill(1, 0, 2, 1, 0x0000FF00);
        });

        assertEquals(0xC05500AA, image.pixel(0, 0));
        assertEquals(0x80FF0000, image.pixel(1, 0));
    }

    @Test
    void opaqueFillReplacesPreviousPixelsExactly() {
        var image = MouseArtworkRaster.paint(2, 1, canvas -> {
            canvas.fill(0, 0, 2, 1, 0x80654321);
            canvas.fill(0, 0, 1, 1, 0xFF123456);
        });

        assertEquals(0xFF123456, image.pixel(0, 0));
        assertEquals(0x80654321, image.pixel(1, 0));
    }

    @Test
    void clipsFillsAndIgnoresEmptyOrTransparentRectangles() {
        var image = MouseArtworkRaster.paint(4, 3, canvas -> {
            canvas.fill(-2, 1, 3, 5, 0xFF336699);
            canvas.fill(3, 0, 2, 2, 0xFFFFFFFF);
            canvas.fill(0, 0, 4, 0, 0xFFFFFFFF);
            canvas.fill(0, 0, 4, 3, 0x00335577);
        });

        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int expected = y >= 1 && x < 3 ? 0xFF336699 : 0;
                assertEquals(expected, image.pixel(x, y), "clipped pixel " + x + "," + y);
            }
        }
    }

    @Test
    void clipsExtremeCoordinatesWithoutIntegerOverflow() {
        var image = MouseArtworkRaster.paint(3, 2,
            canvas -> canvas.fill(Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0xFF102030));

        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) assertEquals(0xFF102030, image.pixel(x, y));
        }
    }

    @Test
    void rejectsNonpositiveDimensionsAndImagesAboveThePixelBudget() {
        Consumer<MouseDiagramPainter.Canvas> noOp = ignored -> {};
        assertThrows(IllegalArgumentException.class, () -> MouseArtworkRaster.paint(0, 1, noOp));
        assertThrows(IllegalArgumentException.class, () -> MouseArtworkRaster.paint(1, -1, noOp));
        assertThrows(IllegalArgumentException.class,
            () -> MouseArtworkRaster.paint(2_001, 2_000, noOp));
        assertThrows(IllegalArgumentException.class,
            () -> MouseArtworkRaster.paint(Integer.MAX_VALUE, Integer.MAX_VALUE, noOp));
    }

    @Test
    void imagePixelReadsAreStableAndBoundsChecked() {
        var image = MouseArtworkRaster.paint(1, 1, canvas -> canvas.fill(0, 0, 1, 1, 0x7FABCDEF));

        assertEquals(1, image.width());
        assertEquals(1, image.height());
        int firstRead = image.pixel(0, 0);
        for (int i = 0; i < 100; i++) assertEquals(firstRead, image.pixel(0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> image.pixel(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> image.pixel(0, 1));
    }

    @Test
    void actualMouseShellAndControlMatchAnIndependentSourceOverCanvas() {
        var layout = MouseDiagramLayout.forArea(0, 0, 356, 138, 100);
        var palette = AppearancePalette.from(UiAppearance.defaults());
        var shell = MouseArtworkRaster.paint(356, 138,
            canvas -> MouseDiagramPainter.shell(canvas, layout, palette));
        var shellReference = referencePixels(356, 138,
            canvas -> MouseDiagramPainter.shell(canvas, layout, palette));
        assertImageMatches(shell, shellReference);

        Rect controlBounds = layout.part(Control.LEFT).bounds();
        var state = new MouseDiagramPainter.State(false, false, false, false, false, true);
        var control = MouseArtworkRaster.paint(356, 138,
            canvas -> MouseDiagramPainter.button(canvas, Control.LEFT, controlBounds, palette, state));
        var controlReference = referencePixels(356, 138,
            canvas -> MouseDiagramPainter.button(canvas, Control.LEFT, controlBounds, palette, state));
        assertImageMatches(control, controlReference);
    }

    private static void assertImageMatches(MouseArtworkRaster.Image image, int[] reference) {
        assertEquals(image.width() * image.height(), reference.length);
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                assertEquals(reference[y * image.width() + x], image.pixel(x, y),
                    "pixel " + x + "," + y);
            }
        }
    }

    private static int[] referencePixels(int width, int height, Consumer<MouseDiagramPainter.Canvas> painter) {
        int[] pixels = new int[width * height];
        painter.accept((left, top, right, bottom, source) -> {
            int clippedLeft = Math.max(0, left), clippedTop = Math.max(0, top);
            int clippedRight = Math.min(width, right), clippedBottom = Math.min(height, bottom);
            if (right <= left || bottom <= top || clippedLeft >= clippedRight || clippedTop >= clippedBottom) return;
            for (int y = clippedTop; y < clippedBottom; y++) {
                for (int x = clippedLeft; x < clippedRight; x++) {
                    int index = y * width + x;
                    pixels[index] = referenceOver(source, pixels[index]);
                }
            }
        });
        return pixels;
    }

    private static int referenceOver(int source, int destination) {
        double sourceAlpha = (source >>> 24) / 255.0;
        if (sourceAlpha == 0) return destination;
        double destinationAlpha = (destination >>> 24) / 255.0;
        double outputAlpha = sourceAlpha + destinationAlpha * (1 - sourceAlpha);
        int roundedAlpha = (int) Math.round(outputAlpha * 255);
        int red = referenceChannel(source >>> 16 & 0xFF, destination >>> 16 & 0xFF,
            sourceAlpha, destinationAlpha, outputAlpha);
        int green = referenceChannel(source >>> 8 & 0xFF, destination >>> 8 & 0xFF,
            sourceAlpha, destinationAlpha, outputAlpha);
        int blue = referenceChannel(source & 0xFF, destination & 0xFF,
            sourceAlpha, destinationAlpha, outputAlpha);
        return roundedAlpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int referenceChannel(int source, int destination, double sourceAlpha,
                                        double destinationAlpha, double outputAlpha) {
        return (int) Math.round((source * sourceAlpha
            + destination * destinationAlpha * (1 - sourceAlpha)) / outputAlpha);
    }
}
