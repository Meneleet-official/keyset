package dev.keyset.editor;

import dev.keyset.editor.MouseArtworkCache.Layer;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseArtworkCacheTest {
    @Test
    void oneThousandStableGetsRasterizeOnlyOnce() {
        var cache = new MouseArtworkCache();
        var paints = new AtomicInteger();
        var first = cache.get(Layer.SHELL, new String("paper-theme"), () -> {
            paints.incrementAndGet();
            return image(0xFF123456);
        });

        for (int i = 0; i < 1_000; i++) {
            var same = cache.get(Layer.SHELL, new String("paper-theme"), () -> {
                paints.incrementAndGet();
                return image(0xFFABCDEF);
            });
            assertSame(first, same);
        }

        assertEquals(1, paints.get());
        assertEquals(1, cache.size());
    }

    @Test
    void changingOneLayerReplacesOnlyThatLayerImage() {
        var cache = new MouseArtworkCache();
        var shell = cache.get(Layer.SHELL, "shell-a", () -> image(0xFF112233));
        var left = cache.get(Layer.LEFT, "left-a", () -> image(0xFF445566));

        var updatedShell = cache.get(Layer.SHELL, "shell-b", () -> image(0xFF778899));
        assertNotSame(shell, updatedShell);
        assertSame(left, cache.get(Layer.LEFT, "left-a", () -> fail("unchanged layer should be cached")));
        assertSame(updatedShell, cache.get(Layer.SHELL, "shell-b", () -> fail("new key should be cached")));
        assertEquals(2, cache.size());
    }

    @Test
    void clearReleasesEveryLayerAndSubsequentGetsRegenerate() {
        var cache = new MouseArtworkCache();
        var beforeClear = new EnumMap<Layer, MouseArtworkRaster.Image>(Layer.class);
        for (Layer layer : Layer.values()) {
            beforeClear.put(layer, cache.get(layer, layer.name(), () -> image(0xFF010203 + layer.ordinal())));
        }
        assertEquals(6, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        for (Layer layer : Layer.values()) {
            var regenerated = cache.get(layer, layer.name(), () -> image(0xFF010203 + layer.ordinal()));
            assertNotSame(beforeClear.get(layer), regenerated);
        }
        assertEquals(6, cache.size());
    }

    @Test
    void factoryFailureLeavesPreviousEntryAvailableAndDoesNotStoreFailedKey() {
        var cache = new MouseArtworkCache();
        var paints = new AtomicInteger();
        var previous = cache.get(Layer.SIDE4, "old", () -> {
            paints.incrementAndGet();
            return image(0xFF112233);
        });

        assertThrows(IllegalStateException.class,
            () -> cache.get(Layer.SIDE4, "failed", () -> { throw new IllegalStateException("raster failed"); }));
        assertSame(previous, cache.get(Layer.SIDE4, "old", () -> fail("previous entry should survive")));
        var afterFailure = cache.get(Layer.SIDE4, "failed", () -> {
            paints.incrementAndGet();
            return image(0xFF445566);
        });

        assertNotSame(previous, afterFailure);
        assertEquals(2, paints.get());
        assertEquals(1, cache.size());
    }

    @Test
    void nullFactoryResultDoesNotReplacePreviousEntry() {
        var cache = new MouseArtworkCache();
        var previous = cache.get(Layer.MIDDLE, "old", () -> image(0xFF123456));

        assertThrows(NullPointerException.class, () -> cache.get(Layer.MIDDLE, "empty", () -> null));
        assertSame(previous, cache.get(Layer.MIDDLE, "old", () -> fail("previous entry should survive")));
        assertEquals(1, cache.size());
    }

    @Test
    void manyColorChangesStillRetainAtMostOneImagePerLayer() {
        var cache = new MouseArtworkCache();
        for (int change = 0; change < 200; change++) {
            for (Layer layer : Layer.values()) {
                int color = 0xFF000000 | (change << 8) | layer.ordinal();
                cache.get(layer, color, () -> image(color));
            }
            assertTrue(cache.size() <= Layer.values().length);
        }

        assertEquals(6, cache.size());
        for (Layer layer : Layer.values()) {
            int color = 0xFF000000 | (199 << 8) | layer.ordinal();
            assertEquals(color, cache.get(layer, color, () -> fail("latest color key should be cached")).pixel(0, 0));
        }
    }

    private static MouseArtworkRaster.Image image(int color) {
        return MouseArtworkRaster.paint(1, 1, canvas -> canvas.fill(0, 0, 1, 1, color));
    }
}
