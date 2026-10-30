package dev.keyset.profile;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePackExampleTest {
    @Test
    void shippedPackContainsAValidPortableThemeAndMatchingMetadata() throws Exception {
        SavedTheme theme;
        try (var input = getClass().getResourceAsStream("/assets/keyset/themes/black.json")) {
            assertNotNull(input);
            theme = ThemeStore.decodeTheme(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        UiAppearance target = UiAppearance.defaults().withKeyboardFormFactor(KeyboardFormFactor.COMPACT_75)
            .withMouseScale(60);
        assertEquals(AppearancePreset.BLACK.applyTo(target), theme.applyTo(target));
        try (var input = getClass().getResourceAsStream("/pack.mcmeta")) {
            assertNotNull(input);
            var metadata = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("pack");
            assertEquals(metadata.get("min_format"), metadata.get("max_format"));
            assertEquals(97, metadata.getAsJsonArray("min_format").get(0).getAsInt());
            assertEquals(1, metadata.getAsJsonArray("min_format").get(1).getAsInt());
        }
    }
}
