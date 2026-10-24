package dev.keyset.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline keycap labels, selected by the Minecraft locale, with stable physical key identities. */
public final class KeyboardLayouts {
    private static final Map<String, Layout> CATALOG = load();
    public static final List<String> MANUAL = List.of("en", "ru", "uk", "fr", "de", "en-dvorak");

    private KeyboardLayouts() {}

    public record Layout(String id, String name, Map<String, String> labels) {
        public Layout { labels = Map.copyOf(labels); }
        public String label(String physicalKey, String fallback) {
            return labels.getOrDefault(physicalKey, fallback);
        }
        public String shortName() {
            return switch (id) {
                case "en" -> "QWERTY";
                case "ru", "uk" -> "ЙЦУКЕН";
                case "fr" -> "AZERTY";
                case "de" -> "QWERTZ";
                case "en-dvorak" -> "DVORAK";
                default -> id.toUpperCase(Locale.ROOT);
            };
        }
    }

    public static Layout forLanguage(String language) {
        String code = language == null ? "en" : language.toLowerCase(Locale.ROOT).replace('_', '-');
        Layout exact = CATALOG.get(code);
        if (exact != null) return exact;
        String base = code.split("-", 2)[0];
        base = switch (base) { case "nn", "no" -> "nb"; case "iw" -> "he"; default -> base; };
        return CATALOG.getOrDefault(base, CATALOG.get("en"));
    }

    public static Layout manual(int index) { return CATALOG.get(MANUAL.get(index)); }
    public static int size() { return CATALOG.size(); }

    private static Map<String, Layout> load() {
        var input = KeyboardLayouts.class.getResourceAsStream("/assets/keyset/keyboard_layouts.json");
        if (input == null) throw new IllegalStateException("Missing bundled keyboard layouts");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject layouts = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("layouts");
            Map<String, Layout> result = new HashMap<>();
            layouts.entrySet().forEach(entry -> {
                JsonObject value = entry.getValue().getAsJsonObject();
                Map<String, String> labels = new HashMap<>();
                value.getAsJsonObject("labels").entrySet().forEach(label -> labels.put(label.getKey(), label.getValue().getAsString()));
                result.put(entry.getKey(), new Layout(entry.getKey(), value.get("name").getAsString(), labels));
            });
            Layout english = result.get("en");
            if (english == null || english.labels().size() != 47) throw new IllegalStateException("Incomplete base keyboard layout");
            // A few upstream layouts omit a punctuation key; retain a usable physical label for it.
            result.replaceAll((id, layout) -> {
                Map<String, String> labels = new HashMap<>(english.labels());
                labels.putAll(layout.labels());
                return new Layout(id, layout.name(), labels);
            });
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read bundled keyboard layouts", exception);
        }
    }
}
