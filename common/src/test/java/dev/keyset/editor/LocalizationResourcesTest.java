package dev.keyset.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationResourcesTest {
    private static final String ASSETS = "/assets/keyset/";
    private static final Pattern FORMAT = Pattern.compile("%(?:[1-9]\\d*\\$)?s|%%");

    @Test
    void everyMinecraftLocaleHasCompleteKeysAndIntactFormatting() throws Exception {
        Map<String, String> english = locale("en_us");
        JsonObject manifest;
        try (var stream = getClass().getResourceAsStream(ASSETS + "localization_manifest.json")) {
            assertNotNull(stream);
            manifest = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject locales = manifest.getAsJsonObject("locales");
        assertEquals(143, locales.size(), "Minecraft 26.3 locale catalog including en_us");
        assertTrue(manifest.getAsJsonObject("unavailableProviders").isEmpty());
        for (String id : locales.keySet()) {
            Map<String, String> translated = locale(id);
            assertEquals(english.keySet(), translated.keySet(), id + " translation keys");
            for (var entry : english.entrySet()) {
                String value = translated.get(entry.getKey());
                assertFalse(value.isBlank(), id + ":" + entry.getKey());
                assertEquals(parameters(entry.getValue()), parameters(value), id + ":" + entry.getKey());
                assertFalse(value.matches("(?s).*\\d{4}\\s*\\|\\|.*"), "Translation batch marker leaked: " + id);
                assertFalse(value.contains("ZXQPH"), "Translation placeholder leaked: " + id);
            }
        }
    }

    private List<String> parameters(String value) {
        return FORMAT.matcher(value).results().map(result -> result.group()).sorted().toList();
    }

    private Map<String, String> locale(String id) throws Exception {
        try (var stream = getClass().getResourceAsStream(ASSETS + "lang/" + id + ".json")) {
            assertNotNull(stream, "Missing locale: " + id);
            try (var reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                Map<String, String> result = new HashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertNull(result.put(key, reader.nextString()), "Duplicate key in " + id + ": " + key);
                }
                reader.endObject();
                return result;
            }
        }
    }
}
