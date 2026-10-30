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
    void undoRestoresProfileRoutesWhileKeepingLaterPersonalizationAndHiddenSections() throws Exception {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        String context = "server:current";
        String originalProfile = "00000000-0000-0000-0000-000000000001";
        String attemptedProfile = "00000000-0000-0000-0000-000000000002";
        preferences.toggleRoute(context, originalProfile);
        ClientPreferences.State undoTarget = preferences.checkpoint();
        preferences.toggleRoute(context, attemptedProfile);
        UiAppearance appearance = new UiAppearance(80, 32, true, 0x78BDF2, 0x101A2C, 70,
                0x304050, 0xD0A060, 0x506070);
        preferences.setAppearance(appearance);
        preferences.setSidebarRatio(0.6);
        preferences.setCategoryHidden("example:tools", true);
        preferences.setModHidden("another_mod", true);
        preferences.save();

        preferences.restore(undoTarget.withDisplayFrom(preferences.checkpoint()));
        preferences.save();
        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(originalProfile, reloaded.profileFor(context));
        assertEquals(appearance, reloaded.appearance());
        assertEquals(0.6, reloaded.sidebarRatio());
        assertTrue(reloaded.isCategoryHidden("example:tools"));
        assertTrue(reloaded.isModHidden("another_mod"));
    }

    @Test
    void displayCopyRetainsTheUndoTargetsProfileRoutes() {
        var target = new ClientPreferences.State(Map.of("world", "profile"));
        var current = new ClientPreferences.State(Map.of(), 0.6,
            new UiAppearance(60, 18, true, 0x123456, 0x202020, 80,
                    0x405060, 0xD1B080, 0x607080), Set.of("mod:tools"), Set.of("mod"));
        var restored = target.withDisplayFrom(current);
        assertEquals(target.routes(), restored.routes());
        assertEquals(current.appearance(), restored.appearance());
        assertEquals(current.hiddenCategories(), restored.hiddenCategories());
    }
}
