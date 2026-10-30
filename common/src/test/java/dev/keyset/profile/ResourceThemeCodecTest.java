package dev.keyset.profile;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceThemeCodecTest {
    @Test
    void roundTripsPortableThemeIncludingMouseBodyColor() throws IOException {
        SavedTheme theme = new SavedTheme(UUID.randomUUID().toString(), "Resource palette", 0x112233,
                0x223344, 42, 0x334455, 0x445566, 0x556677, 0xABCDEF);

        String encoded = ThemeStore.encodeTheme(theme);

        assertTrue(encoded.contains("\"format\": \"keyset-theme\""));
        assertEquals(theme, ThemeStore.decodeTheme(encoded));
    }

    @Test
    void rejectsUnsupportedSchemaAndOversizedPayloadWithoutFilesystemAccess() throws IOException {
        SavedTheme theme = new SavedTheme(UUID.randomUUID().toString(), "Strict", 0x123456,
                0x234567, 40, 0x345678, 0x456789, 0x56789A, 0x6789AB);
        String encoded = ThemeStore.encodeTheme(theme);
        String unsupportedSchema = encoded.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        assertThrows(IOException.class, () -> ThemeStore.decodeTheme(unsupportedSchema));
        assertThrows(IOException.class, () -> ThemeStore.decodeTheme(" ".repeat(64 * 1024 + 1)));
    }
}
