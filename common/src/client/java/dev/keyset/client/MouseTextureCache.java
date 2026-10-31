package dev.keyset.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.keyset.editor.MouseArtworkCache;
import dev.keyset.editor.MouseArtworkCache.Layer;
import dev.keyset.editor.MouseArtworkRaster;
import dev.keyset.editor.MouseArtworkRaster.Image;
import dev.keyset.editor.MouseDiagramLayout;
import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.editor.MouseDiagramPainter;
import dev.keyset.editor.MouseDiagramPainter.State;
import dev.keyset.profile.AppearancePalette;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Six bounded artwork layers owned by one screen, uploaded only when their pixels change. */
public final class MouseTextureCache implements AutoCloseable {
    private static final int SAMPLES = 1;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final long id = NEXT_ID.incrementAndGet();
    private final MouseArtworkCache artwork = new MouseArtworkCache();
    private final EnumMap<Layer, TextureSlot> textures = new EnumMap<>(Layer.class);

    public void shell(GuiGraphicsExtractor graphics, MouseDiagramLayout layout, AppearancePalette palette) {
        Rect body = layout.body(), side = layout.sideBody();
        boolean hasSide = side.width() > 0 && side.height() > 0;
        int x = hasSide ? Math.min(body.x(), side.x()) : body.x();
        int y = hasSide ? Math.min(body.y(), side.y()) : body.y();
        int right = hasSide ? Math.max(body.right(), side.right()) : body.right();
        int bottom = hasSide ? Math.max(body.bottom(), side.bottom()) : body.bottom();
        Rect localBody = local(body, x, y);
        Rect localSide = hasSide ? local(side, x, y) : new Rect(0, 0, 0, 0);
        ShellKey key = new ShellKey(localBody, localSide, palette.effectiveSurface(), palette.text(), palette.mouseBodyColor());
        Image image = artwork.get(Layer.SHELL, key, () -> MouseArtworkRaster.paint(
            (right - x) * SAMPLES, (bottom - y) * SAMPLES + 2,
            canvas -> MouseDiagramPainter.shell(canvas,
                new MouseDiagramLayout(localBody, localSide, localSide, localSide, List.of(), localSide), palette)));
        draw(graphics, Layer.SHELL, image, x, y);
    }

    public void button(GuiGraphicsExtractor graphics, Control control, Rect bounds, AppearancePalette palette, State state) {
        Layer layer = switch (control) {
            case LEFT -> Layer.LEFT;
            case RIGHT -> Layer.RIGHT;
            case MIDDLE -> Layer.MIDDLE;
            case SIDE4 -> Layer.SIDE4;
            case SIDE5 -> Layer.SIDE5;
            default -> throw new IllegalArgumentException("Only the five mouse plates use cached textures");
        };
        int width = bounds.width() * SAMPLES, height = bounds.height() * SAMPLES;
        ButtonKey key = new ButtonKey(width, height, palette.keyColors(state.assigned(), state.conflict(), state.free()),
            palette.text(), palette.mouseBodyColor(), state);
        Image image = artwork.get(layer, key, () -> MouseArtworkRaster.paint(width, height + 2,
            canvas -> MouseDiagramPainter.button(canvas, control, new Rect(0, 0, width, height), palette, state)));
        draw(graphics, layer, image, bounds.x(), bounds.y());
    }

    private void draw(GuiGraphicsExtractor graphics, Layer layer, Image image, int x, int y) {
        TextureSlot slot = textures.computeIfAbsent(layer, value -> new TextureSlot(
            Identifier.fromNamespaceAndPath("keyset", "generated/mouse/" + id + "/" + value.name().toLowerCase(java.util.Locale.ROOT))));
        slot.update(image);
        graphics.pose().pushMatrix();
        graphics.pose().scale(1f / SAMPLES, 1f / SAMPLES);
        graphics.blit(RenderPipelines.GUI_TEXTURED, slot.location, x * SAMPLES, y * SAMPLES, 0f, 0f,
            image.width(), image.height(), image.width(), image.height());
        graphics.pose().popMatrix();
    }

    private static Rect local(Rect rect, int originX, int originY) {
        return new Rect((rect.x() - originX) * SAMPLES, (rect.y() - originY) * SAMPLES,
            rect.width() * SAMPLES, rect.height() * SAMPLES);
    }

    @Override
    public void close() {
        textures.values().forEach(TextureSlot::close);
        textures.clear();
        artwork.clear();
    }

    private record ShellKey(Rect body, Rect side, int surface, int text, int mouseBody) {}
    private record ButtonKey(int width, int height, AppearancePalette.KeyColors colors, int text, int mouseBody, State state) {}

    private static final class TextureSlot implements AutoCloseable {
        private final Identifier location;
        private DynamicTexture texture;
        private Image uploaded;

        private TextureSlot(Identifier location) { this.location = location; }

        private void update(Image image) {
            if (uploaded == image) return;
            if (texture == null || texture.getPixels().getWidth() != image.width()
                    || texture.getPixels().getHeight() != image.height()) {
                NativeImage pixels = new NativeImage(image.width(), image.height(), false);
                DynamicTexture replacement = null;
                try {
                    copyPixels(image, pixels);
                    replacement = new DynamicTexture(() -> location.toString(), pixels);
                    Minecraft.getInstance().getTextureManager().register(location, replacement);
                    texture = replacement;
                } catch (RuntimeException | Error failure) {
                    if (replacement != null) replacement.close();
                    else pixels.close();
                    throw failure;
                }
            } else {
                copyPixels(image, texture.getPixels());
                texture.upload();
            }
            uploaded = image;
        }

        private static void copyPixels(Image source, NativeImage destination) {
            for (int y = 0; y < source.height(); y++) {
                for (int x = 0; x < source.width(); x++) destination.setPixel(x, y, source.pixel(x, y));
            }
        }

        @Override
        public void close() {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(location);
            texture = null;
            uploaded = null;
        }
    }
}

