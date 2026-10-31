package dev.keyset.profile;

import com.google.gson.JsonIOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedSettingsTest {
    private Path directory;

    @BeforeEach
    void createWorkspaceLocalTempDirectory() throws IOException {
        Path buildDirectory = Path.of("build");
        Files.createDirectories(buildDirectory);
        directory = Files.createTempDirectory(buildDirectory, "shared-settings-test-");
    }

    @AfterEach
    void removeWorkspaceLocalTempDirectory() throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void instanceRoutesStaySeparateAndDeletingLocalRoutePreservesSharedRoute() throws IOException {
        Path file = directory.resolve("preferences.properties");
        String instanceA = directory.resolve("game-a").toString();
        String instanceB = directory.resolve("game-b").toString();
        String sharedId = java.util.UUID.randomUUID().toString();
        String localId = java.util.UUID.randomUUID().toString();
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        preferences.toggleRoute("server:one", sharedId);
        preferences.toggleRoute("server:two", sharedId);
        preferences.toggleRoute("server:one", localId, instanceA);
        preferences.toggleRoute("server:one", localId, instanceB);
        preferences.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(sharedId, reloaded.profileFor("server:one"));
        assertEquals(localId, reloaded.profileFor("server:one", instanceA));
        assertEquals(localId, reloaded.profileFor("server:one", instanceB));
        String replacementId = java.util.UUID.randomUUID().toString();
        reloaded.remapProfileRoutes(Map.of(sharedId, replacementId));
        assertEquals(replacementId, reloaded.profileFor("server:one"));
        assertEquals(localId, reloaded.profileFor("server:one", instanceA));
        reloaded.removeProfileRoutes(replacementId);
        reloaded.removeProfileRoutes(localId, instanceA);
        reloaded.save();

        ClientPreferences verified = new ClientPreferences(file);
        verified.load();
        assertNull(verified.profileFor("server:one"));
        assertNull(verified.profileFor("server:two"));
        assertNull(verified.profileFor("server:one", instanceA));
        assertEquals(localId, verified.profileFor("server:one", instanceB));
    }

    @Test
    void staleProfileStoresMergeDifferentActionsAndAddedProfiles() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore first = new ProfileStore(file);
        first.load(Map.of(
                "action.one", binding("key.keyboard.a"),
                "action.two", binding("key.keyboard.b")));
        ProfileStore second = new ProfileStore(file);
        second.load(Map.of());
        String originalId = first.active().id();

        first.updateActive(Map.of("action.one", binding("key.keyboard.x")));
        first.create("First addition", Map.of());
        first.select(originalId);
        second.updateActive(Map.of("action.two", binding("key.keyboard.y")));
        second.create("Second addition", Map.of());
        second.select(originalId);

        first.save();
        second.save();

        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(binding("key.keyboard.x"), reloaded.active().bindings().get("action.one"));
        assertEquals(binding("key.keyboard.y"), reloaded.active().bindings().get("action.two"));
        assertTrue(reloaded.profiles().stream().anyMatch(profile -> profile.name().equals("First addition")));
        assertTrue(reloaded.profiles().stream().anyMatch(profile -> profile.name().equals("Second addition")));
    }

    @Test
    void concurrentFirstLoadsAdoptTheStoreCreatedUnderTheSharedLock() throws Exception {
        Path file = directory.resolve("profiles.json");
        Map<String, Binding> firstBindings = Map.of("first.action", binding("key.keyboard.a"));
        Map<String, Binding> secondBindings = Map.of("second.action", binding("key.keyboard.b"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var firstFuture = workers.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out awaiting load start");
                ProfileStore store = new ProfileStore(file);
                store.load(firstBindings);
                return store;
            });
            var secondFuture = workers.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out awaiting load start");
                ProfileStore store = new ProfileStore(file);
                store.load(secondBindings);
                return store;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            ProfileStore first = firstFuture.get(10, TimeUnit.SECONDS);
            ProfileStore second = secondFuture.get(10, TimeUnit.SECONDS);
            assertEquals(first.checkpoint(), second.checkpoint());

            ProfileStore persisted = new ProfileStore(file);
            persisted.load(Map.of());
            assertTrue(Set.of(firstBindings, secondBindings).contains(persisted.active().bindings()));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void firstSharedLoadAdoptsAnExistingLegacyProfileFile() throws IOException {
        Path legacyFile = directory.resolve("legacy/profiles.json");
        Files.createDirectories(legacyFile.getParent());
        ProfileStore legacy = new ProfileStore(legacyFile);
        legacy.load(Map.of("legacy.action", binding("key.keyboard.l")));
        legacy.create("Legacy game", Map.of("game.action", binding("key.keyboard.g")));
        legacy.save();

        Path sharedFile = directory.resolve("shared/profiles.json");
        ProfileStore shared = new ProfileStore(sharedFile);
        shared.load(Map.of("current.action", binding("key.keyboard.c")), legacyFile);

        assertEquals(legacy.profiles(), shared.profiles());
        assertEquals(legacy.active().id(), shared.active().id());
        ProfileStore reloaded = new ProfileStore(sharedFile);
        reloaded.load(Map.of());
        assertEquals(legacy.profiles(), reloaded.profiles());
    }

    @Test
    void conflictingBindingEditFailsWithoutReplacingEitherMemoryOrDisk() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore first = new ProfileStore(file);
        first.load(Map.of("action", binding("key.keyboard.a")));
        ProfileStore second = new ProfileStore(file);
        second.load(Map.of());

        first.updateActive(Map.of("action", binding("key.keyboard.x")));
        second.updateActive(Map.of("action", binding("key.keyboard.y")));
        first.save();

        assertThrows(SharedSettingsConflictException.class, second::save);
        assertEquals(binding("key.keyboard.y"), second.active().bindings().get("action"));
        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(binding("key.keyboard.x"), reloaded.active().bindings().get("action"));
    }

    @Test
    void profileDeleteAndRemoteRenameMergeWithUnrelatedAdditionAndMigrationMetadata() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore first = new ProfileStore(file);
        first.load(Map.of());
        Profile removed = first.create("To remove", Map.of());
        first.select(first.profiles().get(0).id());
        first.save();

        ProfileStore deleting = new ProfileStore(file);
        deleting.load(Map.of());
        ProfileStore editing = new ProfileStore(file);
        editing.load(Map.of());
        String activeId = deleting.active().id();

        deleting.delete(removed.id());
        editing.rename(removed.id(), "Renamed remotely");
        editing.create("Unrelated addition", Map.of());
        editing.select(activeId);
        editing.save();

        assertThrows(SharedSettingsConflictException.class, deleting::save);
        assertTrue(deleting.profiles().stream().noneMatch(profile -> profile.id().equals(removed.id())));

        // A stale deletion must conflict with an edit to the very same profile.
        ProfileStore remote = new ProfileStore(file);
        remote.load(Map.of());
        remote.rename(removed.id(), "Renamed remotely");
        remote.save();
        ProfileStore deleted = new ProfileStore(file);
        deleted.load(Map.of());
        deleted.delete(removed.id());
        ProfileStore unrelated = new ProfileStore(file);
        unrelated.load(Map.of());
        Profile extra = unrelated.create("Concurrent addition", Map.of());
        unrelated.select(activeId);
        unrelated.save();
        deleted.save();

        assertTrue(deleted.profiles().stream().noneMatch(profile -> profile.id().equals(removed.id())));
        assertTrue(deleted.profiles().stream().anyMatch(profile -> profile.id().equals(extra.id())));
        assertTrue(deleted.checkpoint().migratedLegacyProfileIds().contains(removed.id()));
    }

    @Test
    void preferencesMergeIndependentRoutesAndWidth() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences first = new ClientPreferences(file);
        first.load();
        ClientPreferences second = new ClientPreferences(file);
        second.load();
        String profileA = "00000000-0000-0000-0000-000000000001";
        String profileB = "00000000-0000-0000-0000-000000000002";

        first.toggleRoute("server:one", profileA);
        first.setSidebarRatio(0.61);
        second.toggleRoute("server:two", profileB);

        first.save();
        second.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(profileA, reloaded.profileFor("server:one"));
        assertEquals(profileB, reloaded.profileFor("server:two"));
        assertEquals(0.61, reloaded.sidebarRatio(), 0.000001);
    }

    @Test
    void preferencesPersistAppearanceAndHiddenSectionsAsImmutableValues() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        UiAppearance appearance = new UiAppearance(73, 29, true, 0x123456, 0xABCDEF, 68,
                0x102030, 0xA0B0C0, 0xE0D0C0);
        preferences.setAppearance(appearance);
        preferences.setCategoryHidden("core:movement", true);
        preferences.setCategoryHidden("category.with spaces/and punctuation", true);
        preferences.setModHidden("example:mod.id", true);
        preferences.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(appearance, reloaded.appearance());
        assertTrue(reloaded.isCategoryHidden("core:movement"));
        assertEquals(Set.of("core:movement", "category.with spaces/and punctuation"), reloaded.hiddenCategories());
        assertTrue(reloaded.isModHidden("example:mod.id"));
        assertThrows(UnsupportedOperationException.class, () -> reloaded.hiddenCategories().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> reloaded.hiddenMods().clear());

        reloaded.clearHiddenSections();
        assertTrue(reloaded.hiddenCategories().isEmpty());
        assertTrue(reloaded.hiddenMods().isEmpty());
    }

    @Test
    void preferencesMergeIndependentAppearanceFieldsAndHiddenIds() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences first = new ClientPreferences(file);
        first.load();
        ClientPreferences second = new ClientPreferences(file);
        second.load();

        UiAppearance defaults = UiAppearance.defaults();
        first.setAppearance(new UiAppearance(80, 25, true, 0x123456, 0, 35,
                0x304050, defaults.conflictKeyColor(), defaults.unboundKeyColor()));
        first.setCategoryHidden("category:one", true);
        first.setModHidden("mod:one", true);
        second.setAppearance(new UiAppearance(100, 22, false, 0x94E4D3, 0x224466, 60,
                defaults.boundKeyColor(), 0xD0A060, defaults.unboundKeyColor()));
        second.setCategoryHidden("category:two", true);
        second.setModHidden("mod:two", true);

        first.save();
        second.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(new UiAppearance(80, 22, true, 0x123456, 0x224466, 60,
                0x304050, 0xD0A060, defaults.unboundKeyColor()), reloaded.appearance());
        assertEquals(Set.of("category:one", "category:two"), reloaded.hiddenCategories());
        assertEquals(Set.of("mod:one", "mod:two"), reloaded.hiddenMods());
    }

    @Test
    void preferencesConflictWhenTheSameAppearanceFieldChangesDifferently() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences first = new ClientPreferences(file);
        first.load();
        ClientPreferences second = new ClientPreferences(file);
        second.load();
        first.setAppearance(new UiAppearance(75, 25, false, 0x94E4D3, 0, 35));
        second.setAppearance(new UiAppearance(90, 25, false, 0x94E4D3, 0, 35));
        first.save();
        assertThrows(SharedSettingsConflictException.class, second::save);
    }

    @Test
    void preferencesMergeBoundConflictAndUnboundColorsIndependently() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences boundEditor = new ClientPreferences(file);
        ClientPreferences conflictEditor = new ClientPreferences(file);
        ClientPreferences unboundEditor = new ClientPreferences(file);
        boundEditor.load();
        conflictEditor.load();
        unboundEditor.load();

        UiAppearance defaults = UiAppearance.defaults();
        boundEditor.setAppearance(withKeyAppearance(defaults, 0x123456,
                defaults.conflictKeyColor(), defaults.unboundKeyColor()));
        conflictEditor.setAppearance(withKeyAppearance(defaults, defaults.boundKeyColor(),
                0x234567, defaults.unboundKeyColor()));
        unboundEditor.setAppearance(withKeyAppearance(defaults, defaults.boundKeyColor(),
                defaults.conflictKeyColor(), 0x345678));

        boundEditor.save();
        conflictEditor.save();
        unboundEditor.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(withKeyAppearance(defaults, 0x123456, 0x234567, 0x345678), reloaded.appearance());
    }

    @Test
    void preferencesConflictWhenTheSameKeyAppearanceColorChangesDifferently() throws IOException {
        for (String field : Set.of("bound-color", "conflict-color", "unbound-color")) {
            Path file = directory.resolve("preferences-" + field + ".properties");
            ClientPreferences first = new ClientPreferences(file);
            ClientPreferences second = new ClientPreferences(file);
            first.load();
            second.load();
            UiAppearance defaults = UiAppearance.defaults();
            first.setAppearance(withChangedKeyColor(defaults, field, 0x112233));
            second.setAppearance(withChangedKeyColor(defaults, field, 0x445566));
            first.save();
            assertThrows(SharedSettingsConflictException.class, second::save, field);
        }
    }

    @Test
    void oldPreferencesFileUsesDefaultsForNewKeyAppearanceFields() throws IOException {
        Path file = directory.resolve("legacy-preferences.properties");
        Files.writeString(file, "ui.keyboardScale=80\n"
                + "ui.rowHeight=29\n"
                + "ui.inputOnRight=true\n"
                + "ui.accentColor=1193046\n"
                + "ui.panelColor=6636321\n"
                + "ui.panelOpacity=68\n");

        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();

        assertEquals(new UiAppearance(80, 29, true, 0x123456, 0x654321, 68,
                0x819D96, 0xD77979, 0x546760), preferences.appearance());
    }

    @Test
    void exactPreviousPresetPalettesRefreshAndPersistAcrossReload() throws IOException {
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (preset == AppearancePreset.BLACK) continue;
            Path file = directory.resolve("legacy-preset-" + preset.name().toLowerCase(java.util.Locale.ROOT)
                    + ".properties");
            UiAppearance previous = previousPalette(preset);
            Files.writeString(file, appearanceProperties(previous));

            ClientPreferences preferences = new ClientPreferences(file);
            preferences.load();
            UiAppearance expected = preset.applyTo(previous);
            assertEquals(expected, preferences.appearance(), preset.name());

            preferences.save();
            String saved = Files.readString(file);
            assertTrue(saved.contains("ui.accentColor=" + expected.accentColor()), preset.name());
            assertTrue(saved.contains("ui.boundKeyColor=" + expected.boundKeyColor()), preset.name());
            assertTrue(saved.contains("ui.unboundKeyColor=" + expected.unboundKeyColor()), preset.name());

            ClientPreferences reloaded = new ClientPreferences(file);
            reloaded.load();
            assertEquals(expected, reloaded.appearance(), preset.name());
        }
    }

    @Test
    void manualAccentBoundAndFreeEditsPreventLegacyPresetRefresh() throws IOException {
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (preset == AppearancePreset.BLACK) continue;
            UiAppearance previous = previousPalette(preset);
            for (String field : new String[] {"accent", "bound", "free", "mouse-body"}) {
                Path file = directory.resolve("custom-legacy-" + preset.name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + field + ".properties");
                UiAppearance custom = withManualLegacyEdit(previous, field);
                Files.writeString(file, appearanceProperties(custom));

                ClientPreferences preferences = new ClientPreferences(file);
                preferences.load();
                assertEquals(custom, preferences.appearance(), preset.name() + " " + field);
                preferences.save();

                ClientPreferences reloaded = new ClientPreferences(file);
                reloaded.load();
                assertEquals(custom, reloaded.appearance(), preset.name() + " " + field);
            }
        }
    }

    @Test
    void legacyFreeKeyColorMigratesOnlyWhenItWasCustomized() throws IOException {
        Path defaultsFile = directory.resolve("legacy-default-color.properties");
        Files.writeString(defaultsFile, "ui.freeKeyColor=" + 0x78BDF2 + "\n"
                + "ui.highlightFreeKeys=false\n"
                + "ui.multiBindColor=123456\n");
        ClientPreferences defaults = new ClientPreferences(defaultsFile);
        defaults.load();
        assertEquals(UiAppearance.defaults(), defaults.appearance());

        Path customFile = directory.resolve("legacy-custom-color.properties");
        Files.writeString(customFile, "ui.freeKeyColor=" + 0x233445 + "\n"
                + "ui.highlightFreeKeys=false\n"
                + "ui.multiBindColor=123456\n");
        ClientPreferences custom = new ClientPreferences(customFile);
        custom.load();
        assertEquals(withKeyAppearance(UiAppearance.defaults(), 0x819D96, 0xD77979, 0x233445),
                custom.appearance());

        custom.save();
        String saved = Files.readString(customFile);
        assertTrue(saved.contains("ui.boundKeyColor=" + Integer.toString(0x819D96)));
        assertTrue(saved.contains("ui.conflictKeyColor=" + Integer.toString(0xD77979)));
        assertTrue(saved.contains("ui.unboundKeyColor=" + Integer.toString(0x233445)));
        assertFalse(saved.contains("ui.freeKeyColor="));
        assertFalse(saved.contains("ui.highlightFreeKeys="));
        assertFalse(saved.contains("ui.multiBindColor="));
    }

    @Test
    void malformedNewPreferenceFieldsFallBackToDefaultsWithoutLosingExistingSettings() throws IOException {
        Path file = directory.resolve("preferences.properties");
        Files.writeString(file, "ui.keyboardScale=not-a-number\n"
                + "ui.rowHeight=1000\n"
                + "ui.inputOnRight=maybe\n"
                + "ui.accentColor=-1\n"
                + "ui.panelColor=oops\n"
                + "ui.panelOpacity=also-not-a-number\n"
                + "ui.boundKeyColor=not-a-number\n"
                + "ui.conflictKeyColor=1193046\n"
                + "ui.unboundKeyColor=not-a-number\n"
                + "ui.highlightFreeKeys=false\n"
                + "ui.freeKeyColor=2241348\n"
                + "ui.multiBindColor=123456\n"
                + "ui.hiddenCategory.not-base64!=true\n"
                + "ui.hiddenMod.bW9kOnRydWU=maybe\n");

        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(new UiAppearance(100, 32, false, 0xFFFFFF, 0x000000, 35,
                0x819D96, 0x123456, 0x546760), preferences.appearance());
        assertTrue(preferences.hiddenCategories().isEmpty());
        assertTrue(preferences.hiddenMods().isEmpty());
    }

    @Test
    void legacyFavoritePropertiesAreIgnoredAndDroppedOnSave() throws IOException {
        Path file = directory.resolve("legacy-favorites.properties");
        String hiddenCategory = "legacy:category";
        String encodedHiddenCategory = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hiddenCategory.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.writeString(file, "favorite.YWN0aW9uLm9uZQ=true\n"
                + "favorite.bGVnYWN5LmFjdGlvbg=true\n"
                + "ui.sidebarRatio=0.61\n"
                + "ui.keyboardScale=73\n"
                + "ui.hiddenCategory." + encodedHiddenCategory + "=true\n");

        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(73, preferences.appearance().keyboardScale());
        assertEquals(0.61, preferences.sidebarRatio(), 0.000001);
        assertTrue(preferences.isCategoryHidden(hiddenCategory));
        String routeProfile = "00000000-0000-0000-0000-000000000001";
        preferences.toggleRoute("server:legacy", routeProfile);
        preferences.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertFalse(Files.readString(file).contains("favorite."));
        assertEquals(routeProfile, reloaded.profileFor("server:legacy"));
        assertTrue(reloaded.isCategoryHidden(hiddenCategory));
        assertEquals(73, reloaded.appearance().keyboardScale());
        assertEquals(0.61, reloaded.sidebarRatio(), 0.000001);
    }

    @Test
    void preferencesConflictWhenSameRouteOrWidthChangesDifferently() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences first = new ClientPreferences(file);
        first.load();
        ClientPreferences second = new ClientPreferences(file);
        second.load();
        String profileA = "00000000-0000-0000-0000-000000000001";
        String profileB = "00000000-0000-0000-0000-000000000002";

        first.toggleRoute("server:one", profileA);
        second.toggleRoute("server:one", profileB);
        first.save();
        assertThrows(SharedSettingsConflictException.class, second::save);

        ClientPreferences third = new ClientPreferences(file);
        third.load();
        ClientPreferences fourth = new ClientPreferences(file);
        fourth.load();
        third.setSidebarRatio(0.4);
        fourth.setSidebarRatio(0.6);
        third.save();
        assertThrows(SharedSettingsConflictException.class, fourth::save);
    }

    @Test
    void reloadAdoptsExternalEditsAndProfileDeletionButLeavesUnsavedStateAlone() throws IOException {
        Path profileFile = directory.resolve("profiles.json");
        ProfileStore writer = new ProfileStore(profileFile);
        writer.load(Map.of());
        Profile removable = writer.create("Removable", Map.of());
        writer.save();
        ProfileStore reader = new ProfileStore(profileFile);
        reader.load(Map.of());

        writer.delete(removable.id());
        writer.save();
        assertTrue(reader.reloadIfChanged());
        assertFalse(reader.profiles().stream().anyMatch(profile -> profile.id().equals(removable.id())));
        assertFalse(reader.reloadIfChanged());

        Path preferenceFile = directory.resolve("preferences.properties");
        ClientPreferences preferenceWriter = new ClientPreferences(preferenceFile);
        preferenceWriter.load();
        ClientPreferences preferenceReader = new ClientPreferences(preferenceFile);
        preferenceReader.load();
        String externalProfile = "00000000-0000-0000-0000-000000000003";
        String localProfile = "00000000-0000-0000-0000-000000000004";
        String anotherProfile = "00000000-0000-0000-0000-000000000005";
        preferenceWriter.toggleRoute("server:external", externalProfile);
        preferenceWriter.save();
        assertTrue(preferenceReader.reloadIfChanged());
        assertEquals(externalProfile, preferenceReader.profileFor("server:external"));
        preferenceReader.toggleRoute("server:local", localProfile);
        preferenceWriter.toggleRoute("server:another", anotherProfile);
        preferenceWriter.save();
        assertFalse(preferenceReader.reloadIfChanged());
        assertEquals(localProfile, preferenceReader.profileFor("server:local"));
    }

    @Test
    void failedProfileReloadRestoresOldBaselineForLaterMergeAndRetry() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore writer = new ProfileStore(file);
        writer.load(Map.of(
                "action.remote", binding("key.keyboard.a"),
                "action.local", binding("key.keyboard.b")));
        ProfileStore reader = new ProfileStore(file);
        reader.load(Map.of());
        ProfileStore retryReader = new ProfileStore(file);
        retryReader.load(Map.of());

        writer.updateActive(Map.of("action.remote", binding("key.keyboard.x")));
        writer.save();

        ProfileStore.ReloadCheckpoint rollback = reader.reloadCheckpoint();
        assertTrue(reader.reloadIfChanged());
        assertEquals(binding("key.keyboard.x"), reader.active().bindings().get("action.remote"));
        reader.restoreReloadCheckpoint(rollback);
        assertEquals(binding("key.keyboard.a"), reader.active().bindings().get("action.remote"));

        reader.updateActive(Map.of("action.local", binding("key.keyboard.y")));
        reader.save();
        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(binding("key.keyboard.x"), reloaded.active().bindings().get("action.remote"));
        assertEquals(binding("key.keyboard.y"), reloaded.active().bindings().get("action.local"));

        ProfileStore.ReloadCheckpoint retryRollback = retryReader.reloadCheckpoint();
        assertTrue(retryReader.reloadIfChanged());
        retryReader.restoreReloadCheckpoint(retryRollback);
        assertTrue(retryReader.reloadIfChanged());
        assertEquals(binding("key.keyboard.x"), retryReader.active().bindings().get("action.remote"));
    }

    @Test
    void failedPreferencesReloadRestoresOldBaselineForLaterMergeAndRetry() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences writer = new ClientPreferences(file);
        writer.load();
        ClientPreferences reader = new ClientPreferences(file);
        reader.load();
        ClientPreferences retryReader = new ClientPreferences(file);
        retryReader.load();
        String profileId = "00000000-0000-0000-0000-000000000001";
        String localProfileId = "00000000-0000-0000-0000-000000000002";
        UiAppearance externalAppearance = new UiAppearance(80, 27, true, 0x123456, 0x654321, 60,
                0x112233, 0x445566, 0x778899, KeyboardFormFactor.TKL);

        writer.toggleRoute("server:external", profileId);
        writer.setSidebarRatio(0.61);
        writer.setAppearance(externalAppearance);
        writer.save();

        ClientPreferences.ReloadCheckpoint rollback = reader.reloadCheckpoint();
        assertTrue(reader.reloadIfChanged());
        assertEquals(profileId, reader.profileFor("server:external"));
        assertEquals(externalAppearance, reader.appearance());
        reader.restoreReloadCheckpoint(rollback);
        assertNull(reader.profileFor("server:external"));
        assertEquals(UiAppearance.defaults(), reader.appearance());

        reader.toggleRoute("server:local", localProfileId);
        reader.save();
        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertEquals(profileId, reloaded.profileFor("server:external"));
        assertEquals(localProfileId, reloaded.profileFor("server:local"));
        assertEquals(0.61, reloaded.sidebarRatio(), 0.000001);
        assertEquals(externalAppearance, reloaded.appearance());

        ClientPreferences.ReloadCheckpoint retryRollback = retryReader.reloadCheckpoint();
        assertTrue(retryReader.reloadIfChanged());
        retryReader.restoreReloadCheckpoint(retryRollback);
        assertTrue(retryReader.reloadIfChanged());
        assertEquals(profileId, retryReader.profileFor("server:external"));
        assertEquals(localProfileId, retryReader.profileFor("server:local"));
        assertEquals(externalAppearance, retryReader.appearance());
    }

    @Test
    void rebasedProfileUndoPreservesRemoteProfileAddition() throws IOException {
        Path file = directory.resolve("profiles.json");
        ProfileStore local = new ProfileStore(file);
        local.load(Map.of("action.local", binding("key.keyboard.a")));
        ProfileStore remote = new ProfileStore(file);
        remote.load(Map.of());
        String activeId = local.active().id();

        ProfileStore.State before = local.checkpoint();
        local.updateActive(Map.of("action.local", binding("key.keyboard.x")));
        ProfileStore.State attempted = local.checkpoint();
        Profile remoteProfile = remote.create("Remote addition", Map.of());
        remote.select(activeId);
        remote.save();
        local.save();

        ProfileStore.State rebased = local.rebaseCheckpoint(before, attempted);
        assertTrue(rebased.profiles().stream().anyMatch(profile -> profile.id().equals(remoteProfile.id())));
        local.restore(rebased);
        local.save();

        ProfileStore reloaded = new ProfileStore(file);
        reloaded.load(Map.of());
        assertEquals(binding("key.keyboard.a"), reloaded.active().bindings().get("action.local"));
        assertTrue(reloaded.profiles().stream().anyMatch(profile -> profile.id().equals(remoteProfile.id())));
    }

    @Test
    void rebasedPreferencesUndoPreservesRemoteRouteAndAppearance() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences local = new ClientPreferences(file);
        local.load();
        ClientPreferences remote = new ClientPreferences(file);
        remote.load();
        String localProfile = "00000000-0000-0000-0000-000000000008";
        String remoteProfile = "00000000-0000-0000-0000-000000000009";
        UiAppearance remoteAppearance = new UiAppearance(75, 24, true, 0x345678, 0x101820, 55,
                0x123456, 0x654321, 0xABCDEF, KeyboardFormFactor.COMPACT_75);

        ClientPreferences.State before = local.checkpoint();
        local.toggleRoute("server:local", localProfile);
        ClientPreferences.State attempted = local.checkpoint();
        remote.toggleRoute("server:remote", remoteProfile);
        remote.setAppearance(remoteAppearance);
        remote.save();
        local.save();

        ClientPreferences.State rebased = local.rebaseCheckpoint(before, attempted);
        local.restore(rebased);
        local.save();

        ClientPreferences reloaded = new ClientPreferences(file);
        reloaded.load();
        assertNull(reloaded.profileFor("server:local"));
        assertEquals(remoteProfile, reloaded.profileFor("server:remote"));
        assertEquals(remoteAppearance, reloaded.appearance());
    }

    @Test
    void preferencesSidebarRatioClampsAndOldStateConstructorUsesDefault() {
        ClientPreferences preferences = new ClientPreferences(directory.resolve("preferences.properties"));
        preferences.setSidebarRatio(0.1);
        assertEquals(0.35, preferences.sidebarRatio(), 0.000001);
        preferences.setSidebarRatio(0.9);
        assertEquals(0.65, preferences.sidebarRatio(), 0.000001);
        assertEquals(0.52, new ClientPreferences.State(Map.of()).sidebarRatio(), 0.000001);
        assertEquals(UiAppearance.defaults(), new ClientPreferences.State(Map.of(), 0.5).appearance());
        assertEquals(UiAppearance.defaults(), new ClientPreferences(directory.resolve("fresh.properties")).appearance());
        assertEquals(new UiAppearance(100, 25, false, 0x94E4D3, 0, 35,
                0x819D96, 0xD77979, 0x546760), UiAppearance.defaults());
        assertEquals(new UiAppearance(60, 32, true, 0xFFFFFF, 0, 15,
                        0x819D96, 0xD77979, 0x546760),
                new UiAppearance(20, 100, true, -1, 0x1000000, 2));
        assertEquals(new UiAppearance(60, 32, true, 0xFFFFFF, 0, 15, 0xFFFFFF, 0xFFFFFF, 0),
                new UiAppearance(20, 100, true, -1, 0x1000000, 2, -1, -1, 0x1000000));
    }

    private static UiAppearance withKeyAppearance(UiAppearance base, int boundKeyColor,
                                                  int conflictKeyColor, int unboundKeyColor) {
        return new UiAppearance(base.keyboardScale(), base.rowHeight(), base.inputOnRight(), base.accentColor(),
                base.panelColor(), base.panelOpacity(), boundKeyColor, conflictKeyColor, unboundKeyColor);
    }

    private static UiAppearance withChangedKeyColor(UiAppearance base, String field, int color) {
        return switch (field) {
            case "bound-color" -> withKeyAppearance(base, color, base.conflictKeyColor(), base.unboundKeyColor());
            case "conflict-color" -> withKeyAppearance(base, base.boundKeyColor(), color, base.unboundKeyColor());
            case "unbound-color" -> withKeyAppearance(base, base.boundKeyColor(), base.conflictKeyColor(), color);
            default -> throw new IllegalArgumentException("Unknown key color field: " + field);
        };
    }

    private static UiAppearance previousPalette(AppearancePreset preset) {
        return switch (preset) {
            case DEFAULT -> new UiAppearance(80, 29, true, 0x94E4D3, 0x000000, 68,
                    0x8DAFA6, 0xD77979, 0x48515A);
            case OCEAN -> new UiAppearance(80, 29, true, 0x78BDF2, 0x101A2C, 68,
                    0x78BDF2, 0xD77979, 0x4A627B);
            case EMBER -> new UiAppearance(80, 29, true, 0xF2A66F, 0x24150E, 68,
                    0xF2A66F, 0xD77979, 0x6B4B38);
            case PAPER -> new UiAppearance(80, 29, true, 0x25594C, 0xEEECE4, 68,
                    0x25594C, 0xC74444, 0x747C78);
            case BLACK -> throw new IllegalArgumentException("Black has no legacy palette");
        };
    }

    private static UiAppearance withManualLegacyEdit(UiAppearance base, String field) {
        return switch (field) {
            case "accent" -> withColors(base, base.accentColor() ^ 1,
                    base.boundKeyColor(), base.unboundKeyColor());
            case "bound" -> withColors(base, base.accentColor(),
                    base.boundKeyColor() ^ 1, base.unboundKeyColor());
            case "free" -> withColors(base, base.accentColor(),
                    base.boundKeyColor(), base.unboundKeyColor() ^ 1);
            case "mouse-body" -> base.withMouseBodyColor(base.mouseBodyColor() ^ 1);
            default -> throw new IllegalArgumentException("Unknown legacy color field: " + field);
        };
    }

    private static UiAppearance withColors(UiAppearance base, int accent, int bound, int unbound) {
        return new UiAppearance(base.keyboardScale(), base.rowHeight(), base.inputOnRight(),
                accent, base.panelColor(), base.panelOpacity(), bound, base.conflictKeyColor(), unbound);
    }

    private static String appearanceProperties(UiAppearance appearance) {
        return "ui.keyboardScale=" + appearance.keyboardScale() + "\n"
                + "ui.rowHeight=" + appearance.rowHeight() + "\n"
                + "ui.inputOnRight=" + appearance.inputOnRight() + "\n"
                + "ui.accentColor=" + appearance.accentColor() + "\n"
                + "ui.panelColor=" + appearance.panelColor() + "\n"
                + "ui.panelOpacity=" + appearance.panelOpacity() + "\n"
                + "ui.boundKeyColor=" + appearance.boundKeyColor() + "\n"
                + "ui.conflictKeyColor=" + appearance.conflictKeyColor() + "\n"
                + "ui.unboundKeyColor=" + appearance.unboundKeyColor() + "\n"
                + "ui.mouseBodyColor=" + appearance.mouseBodyColor() + "\n";
    }

    @Test
    void failedGsonWriteKeepsExistingFileAndRemovesTemporaryFile() throws IOException {
        Path file = directory.resolve("profiles.json");
        Files.writeString(file, "original profile contents");
        ProfileStore store = new ProfileStore(file);
        String largePayload = "binding-entry".repeat(4096);

        IOException failure = assertThrows(IOException.class, () -> store.writeJsonLocked(file, writer -> {
            writer.write(largePayload);
            throw new JsonIOException("injected mid-write Gson failure", new IOException("injected cause"));
        }));

        assertTrue(failure.getCause() instanceof JsonIOException);
        assertEquals("original profile contents", Files.readString(file));
        try (Stream<Path> paths = Files.list(directory)) {
            assertEquals(java.util.List.of(file), paths.toList());
        }
    }

    private static Binding binding(String key) {
        return new Binding(key, false, false, false);
    }
}
