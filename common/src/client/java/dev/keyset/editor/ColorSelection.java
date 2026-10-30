package dev.keyset.editor;

/**
 * Immutable HSV color selection used by the editor's color picker.
 *
 * <p>Hue is represented as a turn, with red at {@code 0.0}; saturation and brightness are in
 * {@code [0.0, 1.0]}. The RGB conversion methods use packed {@code 0xRRGGBB} values.</p>
 */
public record ColorSelection(double hue, double saturation, double brightness) {
    public ColorSelection {
        hue = normalizeHue(hue);
        saturation = clampUnit(saturation);
        brightness = clampUnit(brightness);
    }

    /** Creates a selection from a packed {@code 0xRRGGBB} color. */
    public static ColorSelection fromRgb(int rgb) {
        double red = ((rgb >>> 16) & 0xFF) / 255.0;
        double green = ((rgb >>> 8) & 0xFF) / 255.0;
        double blue = (rgb & 0xFF) / 255.0;
        double maximum = Math.max(red, Math.max(green, blue));
        double minimum = Math.min(red, Math.min(green, blue));
        double delta = maximum - minimum;

        double saturation = maximum == 0.0 ? 0.0 : delta / maximum;
        double hue;
        if (delta == 0.0) {
            hue = 0.0;
        } else if (maximum == red) {
            hue = ((green - blue) / delta) % 6.0;
        } else if (maximum == green) {
            hue = (blue - red) / delta + 2.0;
        } else {
            hue = (red - green) / delta + 4.0;
        }

        return new ColorSelection(hue / 6.0, saturation, maximum);
    }

    /** Returns this selection as a packed {@code 0xRRGGBB} color. */
    public int rgb() {
        double chroma = brightness * saturation;
        double sector = hue * 6.0;
        double secondary = chroma * (1.0 - Math.abs((sector % 2.0) - 1.0));
        double match = brightness - chroma;

        double red;
        double green;
        double blue;
        if (sector < 1.0) {
            red = chroma;
            green = secondary;
            blue = 0.0;
        } else if (sector < 2.0) {
            red = secondary;
            green = chroma;
            blue = 0.0;
        } else if (sector < 3.0) {
            red = 0.0;
            green = chroma;
            blue = secondary;
        } else if (sector < 4.0) {
            red = 0.0;
            green = secondary;
            blue = chroma;
        } else if (sector < 5.0) {
            red = secondary;
            green = 0.0;
            blue = chroma;
        } else {
            red = chroma;
            green = 0.0;
            blue = secondary;
        }

        int redByte = toByte(red + match);
        int greenByte = toByte(green + match);
        int blueByte = toByte(blue + match);
        return (redByte << 16) | (greenByte << 8) | blueByte;
    }

    /** Returns a copy with its hue replaced and wrapped into one turn. */
    public ColorSelection withHue(double hue) {
        return new ColorSelection(hue, saturation, brightness);
    }

    /** Returns a copy with its saturation and brightness clamped to the unit interval. */
    public ColorSelection withSaturationAndBrightness(double saturation, double brightness) {
        return new ColorSelection(hue, saturation, brightness);
    }

    /**
     * Returns a copy with its RGB color replaced. Achromatic colors retain this selection's hue
     * so the hue control remains useful when saturation or brightness is zero.
     */
    public ColorSelection withRgb(int rgb) {
        ColorSelection converted = fromRgb(rgb);
        double retainedHue = converted.saturation == 0.0 ? hue : converted.hue;
        return new ColorSelection(retainedHue, converted.saturation, converted.brightness);
    }

    /** Returns the selected byte at bit {@code shift} (16 for red, 8 for green, 0 for blue). */
    public int channel(int shift) {
        requireChannelShift(shift);
        return (rgb() >>> shift) & 0xFF;
    }

    /** Replaces the selected RGB byte, clamping its value to {@code [0, 255]}. */
    public ColorSelection withChannel(int shift, int value) {
        requireChannelShift(shift);
        int mask = 0xFF << shift;
        int updatedRgb = (rgb() & ~mask) | (clampByte(value) << shift);
        return withRgb(updatedRgb);
    }

    private static double normalizeHue(double hue) {
        if (!Double.isFinite(hue)) return 0.0;
        double normalized = hue % 1.0;
        return normalized < 0.0 ? normalized + 1.0 : normalized;
    }

    private static double clampUnit(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int toByte(double value) {
        return clampByte((int) Math.round(value * 255.0));
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void requireChannelShift(int shift) {
        if (shift != 16 && shift != 8 && shift != 0) {
            throw new IllegalArgumentException("RGB channel shift must be 16, 8, or 0");
        }
    }
}
