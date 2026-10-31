package dev.keyset.client;

import dev.keyset.editor.KeycapLabelLayout;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.editor.KeycapSymbols;
import dev.keyset.profile.KeyboardPresentation;
import dev.keyset.profile.KeyLegendMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared top-face label renderer for the editor keyboard and appearance preview. */
public final class KeycapRenderer {
    private static Font metricFont;
    private static CyrillicLine cyrillicLine;
    private KeycapRenderer() {}

    public static void resetFontMetrics() {
        metricFont = null;
        cyrillicLine = null;
        BitmapGlyphMetrics.reset();
    }

    public static void drawLabel(GuiGraphicsExtractor graphics, Font font, String label,
                                 KeycapPainter.Bounds face, int color, float minimumScale) {
        var bounds = font.prepareText(label, 0, 0, color, false, 0).bounds();
        if (bounds == null) return;
        var fitted = KeycapLabelLayout.fitGlyphBounds(face, bounds.left(), bounds.top(), bounds.width(), bounds.height(), minimumScale);
        if (label.codePointCount(0, label.length()) == 1
                && Character.UnicodeScript.of(label.codePointAt(0)) == Character.UnicodeScript.CYRILLIC) {
            var ink = BitmapGlyphMetrics.measure(font, label, bounds);
            var line = cyrillicLine(font, color);
            fitted = KeycapLabelLayout.fitGlyphLine(face, ink.left(), ink.top(), ink.width(), ink.height(),
                line.top(), line.bottom(), minimumScale);
        }
        fitted.ifPresent(placement -> {
            graphics.pose().pushMatrix();
            graphics.pose().translate(placement.x(), placement.y());
            graphics.pose().scale(placement.scale(), placement.scale());
            graphics.text(font, label, 0, 0, color, false);
            graphics.pose().popMatrix();
        });
    }

    /** Physical legends remain independent of translations and font resource packs. */
    public static void drawKey(GuiGraphicsExtractor graphics, Font font, String input, String label,
                               KeycapPainter.Bounds face, int color, float minimumScale) {
        drawKey(graphics, font, input, label, face, color, minimumScale, KeyboardPresentation.defaults());
    }

    public static void drawKey(GuiGraphicsExtractor graphics, Font font, String input, String label,
                               KeycapPainter.Bounds face, int color, float minimumScale,
                               KeyboardPresentation presentation) {
        var symbol = KeycapSymbols.forInput(input, presentation.platform());
        var mode = presentation.modeFor(input);
        if (symbol.isEmpty() || mode == KeyLegendMode.TEXT) {
            drawLabel(graphics, font, label, face, color, minimumScale);
            return;
        }
        var icon = symbol.get();
        var text = mode == KeyLegendMode.ICON_TEXT ? font.prepareText(label, 0, 0, color, false, 0).bounds() : null;
        boolean stacked = text != null && (face.width() - 2f) / (icon.width() + 2 + text.width()) < .65f;
        int groupWidth = text == null ? icon.width() : stacked ? Math.max(icon.width(), text.width()) : icon.width() + 2 + text.width();
        int groupHeight = text == null ? icon.height() : stacked ? icon.height() + 2 + text.height() : Math.max(icon.height(), text.height());
        KeycapLabelLayout.fit(face, groupWidth, groupHeight, minimumScale).ifPresent(placement -> {
            graphics.pose().pushMatrix();
            graphics.pose().translate(placement.x(), placement.y());
            graphics.pose().scale(placement.scale(), placement.scale());
            graphics.pose().pushMatrix();
            graphics.pose().translate(stacked ? (groupWidth - icon.width()) / 2f : 0,
                text != null && !stacked ? (groupHeight - icon.height()) / 2f : 0);
            icon.draw(graphics::fill, color);
            graphics.pose().popMatrix();
            if (text != null) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(
                    (stacked ? (groupWidth - text.width()) / 2f : icon.width() + 2) - text.left(),
                    (stacked ? icon.height() + 2 : (groupHeight - text.height()) / 2f) - text.top());
                graphics.text(font, label, 0, 0, color, false);
                graphics.pose().popMatrix();
            }
            graphics.pose().popMatrix();
        });
    }

    private static CyrillicLine cyrillicLine(Font font, int color) {
        if (metricFont == font && cyrillicLine != null) return cyrillicLine;
        metricFont = font;
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        // One baseline and scale envelope for base letters, accents and descenders.
        for (String label : new String[] {"И", "Й", "Ц"}) {
            var ink = BitmapGlyphMetrics.measure(font, label,
                font.prepareText(label, 0, 0, color, false, 0).bounds());
            if (ink != null) {
                top = Math.min(top, ink.top());
                bottom = Math.max(bottom, ink.bottom());
            }
        }
        cyrillicLine = top < bottom ? new CyrillicLine(top, bottom) : new CyrillicLine(0, font.lineHeight);
        return cyrillicLine;
    }

    private record CyrillicLine(int top, int bottom) {}
}
