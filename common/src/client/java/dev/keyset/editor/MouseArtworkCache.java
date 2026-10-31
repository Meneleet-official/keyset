package dev.keyset.editor;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Keeps only the current rasterized mouse artwork image for each visual layer. */
public final class MouseArtworkCache {
    public enum Layer { SHELL, LEFT, RIGHT, MIDDLE, SIDE4, SIDE5 }

    private final EnumMap<Layer, Entry> images = new EnumMap<>(Layer.class);

    /**
     * Returns the cached image when its visual key is unchanged; otherwise generates and stores
     * one replacement for that layer. A failed factory leaves the current entry intact.
     */
    public synchronized MouseArtworkRaster.Image get(Layer layer, Object visualKey,
                                                       Supplier<MouseArtworkRaster.Image> factory) {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(visualKey, "visualKey");
        Objects.requireNonNull(factory, "factory");

        Entry current = images.get(layer);
        if (current != null && current.visualKey().equals(visualKey)) return current.image();

        MouseArtworkRaster.Image image = Objects.requireNonNull(factory.get(), "factory returned null");
        images.put(layer, new Entry(visualKey, image));
        return image;
    }

    /** Releases all cached CPU images. */
    public synchronized void clear() {
        images.clear();
    }

    /** Number of currently cached layers, bounded by {@link Layer#values()}.length. */
    public synchronized int size() {
        return images.size();
    }

    private record Entry(Object visualKey, MouseArtworkRaster.Image image) {}
}
