package dev.keyset.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.keyset.editor.GlyphInkBounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.Identifier;

/** Reads visible Cyrillic ink once from the active bitmap providers, including resource-pack overrides. */
final class BitmapGlyphMetrics {
    private static List<Source> sources;

    record Ink(int left, int top, int width, int height) {
        int bottom() { return top + height; }
    }

    private record Candidate(Ink ink, int quadTop, int quadWidth, int quadHeight, int advance) {
        boolean matches(ScreenRectangle actual, int actualAdvance) {
            return actual.left() == 0 && actual.top() == quadTop && actual.width() == quadWidth
                && actual.height() == quadHeight && actualAdvance == advance;
        }
    }
    private record Source(boolean known, Map<Integer, Candidate> glyphs) {}

    static void reset() { sources = null; }

    static Ink measure(Font font, String label, ScreenRectangle actual) {
        if (actual == null) return null;
        if (sources == null) {
            sources = new ArrayList<>();
            load(Identifier.withDefaultNamespace("default"), new HashSet<>(), 0, sources);
        }
        int codepoint = label.codePointAt(0);
        for (Source source : sources) {
            if (!source.known()) break;
            Candidate candidate = source.glyphs().get(codepoint);
            if (candidate != null) {
                if (candidate.matches(actual, font.width(label))) return candidate.ink();
                break;
            }
        }
        return new Ink(actual.left(), actual.top(), actual.width(), actual.height());
    }

    private static void load(Identifier font, Set<Identifier> path, int depth, List<Source> result) {
        if (depth > 8 || !path.add(font)) { result.add(new Source(false, Map.of())); return; }
        var manager = Minecraft.getInstance().getResourceManager();
        try {
            var resources = manager.getResourceStack(font.withPrefix("font/").withSuffix(".json"));
            if (resources.isEmpty()) { result.add(new Source(false, Map.of())); return; }
            // Font JSON files are additive; higher-priority packs supply providers first.
            for (int pack = resources.size() - 1; pack >= 0; pack--) {
            try (var reader = resources.get(pack).openAsReader()) {
            for (var value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers")) {
                JsonObject provider = value.getAsJsonObject();
                if (provider.has("filter") && provider.getAsJsonObject("filter").has("uniform")
                        && provider.getAsJsonObject("filter").get("uniform").getAsBoolean()
                            != Minecraft.getInstance().options.forceUnicodeFont().get()) continue;
                switch (provider.get("type").getAsString().replace("minecraft:", "")) {
                    case "reference" -> load(Identifier.parse(provider.get("id").getAsString()), path, depth + 1, result);
                    case "bitmap" -> result.add(new Source(true, bitmap(provider)));
                    case "space" -> {
                        // Non-drawing glyphs have no prepared bounds and are skipped by the caller.
                    }
                    default -> result.add(new Source(false, Map.of()));
                }
            }
            }
            }
        } catch (Exception unavailable) {
            // Unknown/unsupported providers retain the font's own conservative quad bounds.
            result.add(new Source(false, Map.of()));
        } finally { path.remove(font); }
    }

    private static Map<Integer, Candidate> bitmap(JsonObject provider) throws java.io.IOException {
        var rows = provider.getAsJsonArray("chars");
        boolean relevant = false;
        for (var row : rows) {
            if (row.getAsString().codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp)
                    == Character.UnicodeScript.CYRILLIC)) { relevant = true; break; }
        }
        if (!relevant || rows.isEmpty()) return Map.of();
        Identifier texture = Identifier.parse(provider.get("file").getAsString()).withPrefix("textures/");
        var manager = Minecraft.getInstance().getResourceManager();
        try (var stream = manager.open(texture); NativeImage image = NativeImage.read(stream)) {
            if ((long) image.getWidth() * image.getHeight() > 4_000_000) return Map.of();
            int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            if (columns < 1) return Map.of();
            int cellWidth = image.getWidth() / columns, cellHeight = image.getHeight() / rows.size();
            if (cellWidth < 1 || cellHeight < 1) return Map.of();
            int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
            float scale = height / (float) cellHeight;
            int quadTop = 7 - provider.get("ascent").getAsInt();
            Map<Integer, Candidate> glyphs = new HashMap<>();
            for (int y = 0; y < rows.size(); y++) {
                int[] letters = rows.get(y).getAsString().codePoints().toArray();
                for (int x = 0; x < Math.min(columns, letters.length); x++) {
                    int cp = letters[x];
                    if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.CYRILLIC) continue;
                    int offsetX = x * cellWidth, offsetY = y * cellHeight;
                    var ink = GlyphInkBounds.scan(cellWidth, cellHeight,
                        (px, py) -> image.getPixel(offsetX + px, offsetY + py));
                    if (ink.isEmpty()) continue;
                    var visible = ink.get();
                    int left = (int) Math.floor(visible.x() * scale);
                    int top = (int) Math.floor(quadTop + visible.y() * scale);
                    int right = (int) Math.ceil((visible.x() + visible.width()) * scale);
                    int bottom = (int) Math.ceil(quadTop + (visible.y() + visible.height()) * scale);
                    int advance = (int) (0.5 + (visible.x() + visible.width()) * scale) + 1;
                    glyphs.putIfAbsent(cp, new Candidate(new Ink(left, top, right - left, bottom - top), quadTop,
                        (int) Math.ceil(cellWidth * scale), height, advance));
                }
            }
            return Map.copyOf(glyphs);
        }
    }
}

