package dev.keyset.profile;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DisplayPreferencesUndoTest {
    @TempDir Path directory;

    @Test
    void undoRestoresFavoriteStateWhileKeepingLaterPersonalizationAndHiddenSections() throws Exception {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        ClientPreferences.State undoTarget = preferences.checkpoint();
        preferences.toggleFavorite("key.jump");
        UiAppearance appearance = new UiAppearance(80, 32, true, 0x78BDF2, 0x101A2C, 70);
        preferences.setAppearance(appearance);
        preferences.setSidebarRatio(0.6);
        preferences.setCategoryHidden("example:tools", true);
        preferences.setModHidden("another_mod", true);
        preferences.save();

        preferences.restore(undoTarget.withDisplayFrom(preferences.checkpoint()));
        preferences.save();
        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertFalse(reloaded.isFavorite("key.jump"));
        assertEquals(appearance, reloaded.appearance());
        assertEquals(0.6, reloaded.sidebarRatio());
        assertTrue(reloaded.isCategoryHidden("example:tools"));
        assertTrue(reloaded.isModHidden("another_mod"));
    }

    @Test
    void displayCopyRetainsTheUndoTargetsProfileRoutes() {
        var target = new ClientPreferences.State(Set.of("key.jump"), Map.of("world", "profile"));
        var current = new ClientPreferences.State(Set.of(), Map.of(), 0.6,
            new UiAppearance(60, 18, true, 0x123456, 0x202020, 80), Set.of("mod:tools"), Set.of("mod"));
        var restored = target.withDisplayFrom(current);
        assertEquals(target.routes(), restored.routes());
        assertEquals(target.favorites(), restored.favorites());
        assertEquals(current.appearance(), restored.appearance());
        assertEquals(current.hiddenCategories(), restored.hiddenCategories());
    }
}
