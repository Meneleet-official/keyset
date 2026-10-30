package dev.keyset.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorSelectionTest {
    @Test
    void knownColorsRoundTripThroughHsv() {
        int[] colors = {
                0x000000, 0xFFFFFF, 0x808080, 0x010101, 0xFEFEFE,
                0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF,
                0x123456, 0xC08020, 0x7F8081
        };

        for (int color : colors) {
            assertEquals(color, ColorSelection.fromRgb(color).rgb(),
                    () -> String.format("RGB round trip for #%06X", color));
        }
        assertEquals(0.0, ColorSelection.fromRgb(0x808080).saturation(), 1.0e-12);
        assertEquals(0.0, ColorSelection.fromRgb(0x000000).saturation(), 1.0e-12);
        assertEquals(0.0, ColorSelection.fromRgb(0xFFFFFF).saturation(), 1.0e-12);
    }

    @Test
    void deterministicRgbGridRoundTrips() {
        for (int red = 0; red <= 255; red += 17) {
            for (int green = 0; green <= 255; green += 17) {
                for (int blue = 0; blue <= 255; blue += 17) {
                    int color = (red << 16) | (green << 8) | blue;
                    assertEquals(color, ColorSelection.fromRgb(color).rgb(),
                            () -> String.format("RGB round trip for #%06X", color));
                }
            }
        }
    }

    @Test
    void hsvEndpointsUseRedAtZeroAndClampComponents() {
        assertEquals(0xFF0000, new ColorSelection(0.0, 1.0, 1.0).rgb());
        assertEquals(0xFF0000, new ColorSelection(1.0, 1.0, 1.0).rgb());
        assertEquals(0xFFFF00, new ColorSelection(1.0 / 6.0, 1.0, 1.0).rgb());
        assertEquals(0x00FF00, new ColorSelection(1.0 / 3.0, 1.0, 1.0).rgb());
        assertEquals(0x00FFFF, new ColorSelection(0.5, 1.0, 1.0).rgb());
        assertEquals(0x0000FF, new ColorSelection(2.0 / 3.0, 1.0, 1.0).rgb());
        assertEquals(0xFFFFFF, new ColorSelection(0.5, 0.0, 1.0).rgb());
        assertEquals(0x000000, new ColorSelection(0.5, 1.0, 0.0).rgb());

        ColorSelection clamped = new ColorSelection(0.0, -2.0, 4.0);
        assertEquals(0.0, clamped.saturation(), 1.0e-12);
        assertEquals(1.0, clamped.brightness(), 1.0e-12);
        assertEquals(0xFFFFFF, clamped.rgb());
    }

    @Test
    void hueWrapsInBothDirections() {
        assertEquals(0.25, new ColorSelection(1.25, 0.5, 0.5).hue(), 1.0e-12);
        assertEquals(0.75, new ColorSelection(-0.25, 0.5, 0.5).hue(), 1.0e-12);
        assertEquals(0.0, new ColorSelection(2.0, 0.5, 0.5).hue(), 1.0e-12);
        assertEquals(0.25, new ColorSelection(0.5, 1.0, 1.0).withHue(2.25).hue(), 1.0e-12);
    }

    @Test
    void achromaticRgbUpdatesRetainLatentHue() {
        ColorSelection selection = new ColorSelection(0.72, 1.0, 1.0);

        ColorSelection white = selection.withRgb(0xFFFFFF);
        assertEquals(0.72, white.hue(), 1.0e-12);
        assertEquals(0.0, white.saturation(), 1.0e-12);
        assertEquals(0xFFFFFF, white.rgb());
        assertEquals(new ColorSelection(0.72, 1.0, 1.0).rgb(),
                white.withSaturationAndBrightness(1.0, 1.0).rgb());

        ColorSelection black = selection.withRgb(0x000000);
        assertEquals(0.72, black.hue(), 1.0e-12);
        assertEquals(0x000000, black.rgb());
        assertEquals(0xFFFFFF, black.withSaturationAndBrightness(0.0, 1.0).rgb());
    }

    @Test
    void channelUpdatesOnlyChangeRequestedByteAndClampValues() {
        ColorSelection original = ColorSelection.fromRgb(0x123456);
        assertEquals(0xFF3456, original.withChannel(16, 999).rgb());
        assertEquals(0x120056, original.withChannel(8, -1).rgb());
        assertEquals(0x1234FF, original.withChannel(0, 300).rgb());
        assertEquals(0x123456, original.rgb());
        assertEquals(0x34, original.channel(8));
        assertEquals(0x56, original.channel(0));
        assertEquals(0x12, original.channel(16));

        ColorSelection achromatic = new ColorSelection(1.0 / 3.0, 1.0, 1.0).withChannel(8, 0);
        assertEquals(1.0 / 3.0, achromatic.hue(), 1.0e-12);
        assertEquals(0x000000, achromatic.rgb());
    }

    @Test
    void onlyRgbChannelShiftsAreAccepted() {
        assertThrows(IllegalArgumentException.class, () -> ColorSelection.fromRgb(0).channel(24));
        assertThrows(IllegalArgumentException.class, () -> ColorSelection.fromRgb(0).channel(4));
        assertThrows(IllegalArgumentException.class, () -> ColorSelection.fromRgb(0).withChannel(24, 0));
        assertThrows(IllegalArgumentException.class, () -> ColorSelection.fromRgb(0).withChannel(4, 0));
    }
}
