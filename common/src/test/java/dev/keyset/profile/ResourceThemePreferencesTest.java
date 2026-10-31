package dev.keyset.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceThemePreferencesTest {
    @TempDir Path directory;

    @Test
    void missingAndInvalidThemePropertiesUseNoSelection() throws IOException {
        Path missingFile = directory.resolve("missing.properties");
        ClientPreferences missing = new ClientPreferences(missingFile);
        missing.load();
        assertNull(missing.resourceTheme());

        Path invalidFile = directory.resolve("invalid.properties");
        Files.writeString(invalidFile, "ui.resourceTheme=Invalid:theme\n");
        ClientPreferences invalid = new ClientPreferences(invalidFile);
        invalid.load();
        assertNull(invalid.resourceTheme());

        Path longFile = directory.resolve("long.properties");
        Files.writeString(longFile, "ui.resourceTheme=example:" + "a".repeat(250) + "\n");
        ClientPreferences tooLong = new ClientPreferences(longFile);
        tooLong.load();
        assertNull(tooLong.resourceTheme());
    }

    @Test
    void validThemeRoundTripsAndNullClearsIt() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        preferences.setResourceTheme("example:keyboard/compact.v2");
        preferences.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals("example:keyboard/compact.v2", reloaded.resourceTheme());

        reloaded.setResourceTheme(null);
        reloaded.save();
        ClientPreferences cleared = new ClientPreferences(file);
        cleared.load();
        assertNull(cleared.resourceTheme());
        assertThrows(IllegalArgumentException.class, () -> cleared.setResourceTheme(" "));
        assertThrows(IllegalArgumentException.class, () -> cleared.setResourceTheme("example:Bad"));
        assertThrows(IllegalArgumentException.class,
                () -> cleared.setResourceTheme("example:" + "a".repeat(250)));
    }

    @Test
    void resourceThemeMergesWithAnIndependentAppearanceEdit() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences themeEditor = new ClientPreferences(file);
        themeEditor.load();
        ClientPreferences appearanceEditor = new ClientPreferences(file);
        appearanceEditor.load();

        UiAppearance changedAppearance = appearanceEditor.appearance()
            .withKeyboardFormFactor(KeyboardFormFactor.TKL).withMouseScale(80);
        themeEditor.setResourceTheme("example:blue");
        appearanceEditor.setAppearance(changedAppearance);
        themeEditor.save();
        appearanceEditor.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals("example:blue", reloaded.resourceTheme());
        assertEquals(changedAppearance, reloaded.appearance());
    }

    @Test
    void distinctConcurrentThemeSelectionsConflict() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences first = new ClientPreferences(file);
        first.load();
        ClientPreferences second = new ClientPreferences(file);
        second.load();

        first.setResourceTheme("example:first");
        second.setResourceTheme("example:second");
        first.save();

        assertThrows(SharedSettingsConflictException.class, second::save);
        assertEquals("example:second", second.resourceTheme());
        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals("example:first", reloaded.resourceTheme());
    }

    @Test
    void checkpointsAndDisplayCopiesRetainTheTheme() {
        ClientPreferences preferences = new ClientPreferences(directory.resolve("preferences.properties"));
        preferences.setResourceTheme("example:checkpoint");
        ClientPreferences.State checkpoint = preferences.checkpoint();
        preferences.setResourceTheme(null);
        preferences.restore(checkpoint);
        assertEquals("example:checkpoint", preferences.resourceTheme());

        ClientPreferences.State undoTarget = new ClientPreferences.State(Map.of());
        ClientPreferences.State currentDisplay = new ClientPreferences.State(Map.of(), 0.6,
                UiAppearance.defaults(), Set.of(), Set.of(), "example:current");
        assertEquals("example:current", undoTarget.withDisplayFrom(currentDisplay).resourceTheme());
    }

    @Test
    void concurrentResourceSelectionAndManualPaletteEditConflictInEitherSaveOrder() throws IOException {
        for (boolean themeFirst : new boolean[] {true, false}) {
            Path file = directory.resolve("palette-race-" + themeFirst + ".properties");
            ClientPreferences theme = new ClientPreferences(file), manual = new ClientPreferences(file);
            theme.load(); manual.load();
            UiAppearance base = theme.appearance();
            theme.setAppearance(new UiAppearance(100, 25, false, 0x123456, base.panelColor(), base.panelOpacity(),
                base.boundKeyColor(), base.conflictKeyColor(), base.unboundKeyColor()));
            theme.setResourceTheme("keyset:themes/a.json");
            manual.setAppearance(base.withMouseBodyColor(0x123456));
            manual.setResourceTheme(null);
            ClientPreferences first = themeFirst ? theme : manual;
            ClientPreferences second = themeFirst ? manual : theme;
            first.save();
            assertThrows(SharedSettingsConflictException.class, second::save);
            ClientPreferences stored = new ClientPreferences(file);
            stored.load();
            assertEquals(first.appearance(), stored.appearance());
            assertEquals(first.resourceTheme(), stored.resourceTheme());
        }
    }

    @Test
    void explicitResourcePaletteBypassesLegacyColorMigrationOnSaveAndSharedReload() throws IOException {
        Path file = directory.resolve("legacy-resource-colors.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        UiAppearance legacy = new UiAppearance(100, 25, false, 0x78BDF2, 0x101A2C, 35,
            0x78BDF2, 0xD77979, 0x4A627B);
        preferences.setAppearance(legacy);
        preferences.setResourceTheme("keyset:themes/legacy.json");
        preferences.save();
        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(legacy, reloaded.appearance());
        reloaded.setSidebarRatio(0.6);
        reloaded.save();
        assertTrue(preferences.reloadIfChanged());
        assertEquals(legacy, preferences.appearance());
        assertEquals("keyset:themes/legacy.json", preferences.resourceTheme());
    }
}
