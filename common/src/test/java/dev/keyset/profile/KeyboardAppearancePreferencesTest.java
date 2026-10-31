package dev.keyset.profile;

import java.util.List;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class KeyboardAppearancePreferencesTest {
    @TempDir Path directory;

    @Test
    void formatRoundTripsAndInvalidOrMissingFormatUsesFullSize() throws IOException {
        Path file = directory.resolve("preferences.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(KeyboardFormFactor.FULL, preferences.appearance().keyboardFormFactor());
        for (KeyboardFormFactor form : KeyboardFormFactor.values()) {
            preferences.setAppearance(preferences.appearance().withKeyboardFormFactor(form));
            preferences.save();
            ClientPreferences loaded = new ClientPreferences(file);
            loaded.load();
            assertEquals(form, loaded.appearance().keyboardFormFactor());
        }
        Files.writeString(file, "ui.keyboardFormFactor=unknown-layout\n");
        preferences.load();
        assertEquals(KeyboardFormFactor.FULL, preferences.appearance().keyboardFormFactor());
    }

    @Test
    void mouseBodyColorRoundTripsAndOldPreferencesUseDefault() throws IOException {
        Path file = directory.resolve("mouse-color.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(0x30363B, preferences.appearance().mouseBodyColor());

        preferences.setAppearance(preferences.appearance().withMouseBodyColor(0x12ABEF));
        preferences.save();
        assertTrue(Files.readString(file).contains("ui.mouseBodyColor=1223663"));
        ClientPreferences loaded = new ClientPreferences(file);
        loaded.load();
        assertEquals(0x12ABEF, loaded.appearance().mouseBodyColor());

        Files.writeString(file, "ui.accentColor=123456\n");
        ClientPreferences old = new ClientPreferences(file);
        old.load();
        assertEquals(0x30363B, old.appearance().mouseBodyColor());
    }

    @Test
    void mouseBodyColorMergesWithUnrelatedAppearanceEditsAndConflictsOnConcurrentEdits() throws IOException {
        Path file = directory.resolve("merge-mouse-color.properties");
        ClientPreferences first = new ClientPreferences(file), second = new ClientPreferences(file);
        first.load();
        second.load();
        first.setAppearance(first.appearance().withMouseBodyColor(0x123456));
        second.setAppearance(second.appearance().withKeyboardFormFactor(KeyboardFormFactor.TKL));
        first.save();
        second.save();
        ClientPreferences merged = new ClientPreferences(file);
        merged.load();
        assertEquals(0x123456, merged.appearance().mouseBodyColor());
        assertEquals(KeyboardFormFactor.TKL, merged.appearance().keyboardFormFactor());

        ClientPreferences third = new ClientPreferences(file), fourth = new ClientPreferences(file);
        third.load();
        fourth.load();
        third.setAppearance(third.appearance().withMouseBodyColor(0x111111));
        fourth.setAppearance(fourth.appearance().withMouseBodyColor(0x222222));
        third.save();
        assertThrows(SharedSettingsConflictException.class, fourth::save);
    }

    @Test
    void mouseScaleAndBottomRowGeometryRoundTripAndOldSettingsUseDefaults() throws IOException {
        Path file = directory.resolve("device-geometry.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(100, preferences.appearance().mouseScale());
        assertEquals(KeyboardBottomRow.defaults(), preferences.appearance().keyboardPresentation().bottomRow());

        KeyboardBottomRow bottomRow = new KeyboardBottomRow(List.of(
                "key.keyboard.left.control", "key.keyboard.fn", "key.keyboard.space"), 18);
        preferences.setAppearance(preferences.appearance().withMouseScale(77).withKeyboardPresentation(
                preferences.appearance().keyboardPresentation().withBottomRow(bottomRow)));
        preferences.save();

        String serialized = Files.readString(file);
        assertTrue(serialized.contains("ui.mouseScale=77"));
        assertTrue(serialized.contains("ui.bottomRow.order=left.control,fn,space"));
        assertTrue(serialized.contains("ui.bottomRow.spaceWidth=18"));
        ClientPreferences loaded = new ClientPreferences(file);
        loaded.load();
        assertEquals(77, loaded.appearance().mouseScale());
        assertEquals(bottomRow, loaded.appearance().keyboardPresentation().bottomRow());

        Files.writeString(file, "ui.accentColor=123456\n");
        ClientPreferences old = new ClientPreferences(file);
        old.load();
        assertEquals(100, old.appearance().mouseScale());
        assertEquals(KeyboardBottomRow.defaults(), old.appearance().keyboardPresentation().bottomRow());
    }

    @Test
    void mouseScaleOrderAndSpaceWidthMergeIndependently() throws IOException {
        Path file = directory.resolve("merge-device-geometry.properties");
        ClientPreferences first = new ClientPreferences(file), second = new ClientPreferences(file);
        first.load();
        second.load();
        first.setAppearance(first.appearance().withMouseScale(80).withKeyboardPresentation(
                first.appearance().keyboardPresentation().withBottomRow(
                        KeyboardBottomRow.defaults().withOrder(List.of("key.keyboard.fn", "key.keyboard.space")))));
        second.setAppearance(second.appearance().withKeyboardPresentation(
                second.appearance().keyboardPresentation().withBottomRow(
                        KeyboardBottomRow.defaults().withSpaceWidth(20))));
        first.save();
        second.save();

        ClientPreferences loaded = new ClientPreferences(file);
        loaded.load();
        assertEquals(80, loaded.appearance().mouseScale());
        assertEquals(List.of("key.keyboard.fn", "key.keyboard.space"),
                loaded.appearance().keyboardPresentation().bottomRow().order());
        assertEquals(20, loaded.appearance().keyboardPresentation().bottomRow().spaceWidth());
    }

    @Test
    void mouseScaleClampsAndAppearanceCopiesRetainIt() {
        UiAppearance appearance = UiAppearance.defaults().withMouseScale(72);
        assertEquals(72, appearance.withKeyboardFormFactor(KeyboardFormFactor.TKL).mouseScale());
        assertEquals(72, appearance.withKeyboardPresentation(KeyboardPresentation.defaults()).mouseScale());
        assertEquals(60, appearance.withMouseScale(1).mouseScale());
        assertEquals(100, appearance.withMouseScale(120).mouseScale());
        assertEquals(100, new UiAppearance(100, 25, false, 0, 0, 35,
                0, 0, 0, KeyboardFormFactor.FULL, KeyboardPresentation.defaults(), 0).mouseScale());
    }

    @Test
    void formatMergesWithIndependentColorsAndSurvivesApplyingPresets() throws IOException {
        Path file = directory.resolve("shared.properties");
        ClientPreferences first = new ClientPreferences(file), second = new ClientPreferences(file);
        first.load();
        second.load();
        first.setAppearance(first.appearance().withKeyboardFormFactor(KeyboardFormFactor.COMPACT_75));
        second.setAppearance(AppearancePreset.OCEAN.applyTo(second.appearance()));
        first.save();
        second.save();
        ClientPreferences loaded = new ClientPreferences(file);
        loaded.load();
        assertEquals(KeyboardFormFactor.COMPACT_75, loaded.appearance().keyboardFormFactor());
        assertTrue(AppearancePreset.OCEAN.matches(loaded.appearance()));
        for (AppearancePreset preset : AppearancePreset.values()) {
            assertEquals(KeyboardFormFactor.COMPACT_75,
                    preset.applyTo(loaded.appearance()).keyboardFormFactor());
            assertEquals(loaded.appearance().keyboardPresentation(),
                    preset.applyTo(loaded.appearance()).keyboardPresentation());
        }
    }

    @Test
    void platformAndPerKeyLegendModesRoundTripIndependently() throws IOException {
        Path file = directory.resolve("presentation.properties");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(KeyLegendMode.TEXT, preferences.appearance().keyboardPresentation().modeFor("key.keyboard.escape"));
        assertEquals(KeyLegendMode.TEXT, preferences.appearance().keyboardPresentation().modeFor("key.keyboard.left.control"));
        assertEquals(KeyLegendMode.ICON_TEXT, preferences.appearance().keyboardPresentation().modeFor("key.keyboard.left.shift"));
        assertEquals(KeyLegendMode.ICON, preferences.appearance().keyboardPresentation().modeFor("key.keyboard.tab"));

        for (KeyboardPlatform platform : KeyboardPlatform.values()) {
            var presentation = KeyboardPresentation.defaults().withPlatform(platform)
                .withMode("key.keyboard.escape", KeyLegendMode.ICON)
                .withMode("key.keyboard.left.shift", KeyLegendMode.TEXT)
                .withMode("key.keyboard.fn", KeyLegendMode.ICON_TEXT);
            preferences.setAppearance(preferences.appearance().withKeyboardPresentation(presentation));
            preferences.save();
            ClientPreferences loaded = new ClientPreferences(file);
            loaded.load();
            assertEquals(presentation, loaded.appearance().keyboardPresentation());
        }
    }

    @Test
    void independentLegendEditsMergeAndPresetOrFormChangesKeepThem() throws IOException {
        Path file = directory.resolve("merge-presentation.properties");
        ClientPreferences first = new ClientPreferences(file), second = new ClientPreferences(file);
        first.load();
        second.load();
        first.setAppearance(first.appearance().withKeyboardPresentation(
            first.appearance().keyboardPresentation().withMode("key.keyboard.tab", KeyLegendMode.TEXT)));
        second.setAppearance(second.appearance().withKeyboardPresentation(
            second.appearance().keyboardPresentation().withMode("key.keyboard.enter", KeyLegendMode.ICON_TEXT)));
        first.save();
        second.save();
        ClientPreferences loaded = new ClientPreferences(file);
        loaded.load();
        assertEquals(KeyLegendMode.TEXT, loaded.appearance().keyboardPresentation().modeFor("key.keyboard.tab"));
        assertEquals(KeyLegendMode.ICON_TEXT, loaded.appearance().keyboardPresentation().modeFor("key.keyboard.enter"));

        var presentation = loaded.appearance().keyboardPresentation().withPlatform(KeyboardPlatform.MAC);
        var macAppearance = loaded.appearance().withKeyboardPresentation(presentation);
        assertEquals(presentation, AppearancePreset.EMBER.applyTo(macAppearance).keyboardPresentation());
        assertEquals(presentation, macAppearance.withKeyboardFormFactor(KeyboardFormFactor.COMPACT_75)
            .keyboardPresentation());
    }

    @Test
    void invalidLegendEntriesAreIgnoredAndUntrustedInputsAreFiltered() throws IOException {
        Path file = directory.resolve("invalid-presentation.properties");
        Files.writeString(file, "ui.keyboardPlatform=unknown\n"
            + "ui.keyLegend.escape=icon\n"
            + "ui.keyLegend.left.shift=invalid\n"
            + "ui.keyLegend.key.keyboard.left.alt=text\n"
            + "ui.keyLegend.fn=icon_text\n");
        ClientPreferences preferences = new ClientPreferences(file);
        preferences.load();
        assertEquals(KeyboardPlatform.AUTO, preferences.appearance().keyboardPresentation().platform());
        assertEquals(KeyLegendMode.ICON, preferences.appearance().keyboardPresentation().modeFor("key.keyboard.escape"));
        assertEquals(KeyLegendMode.ICON_TEXT, preferences.appearance().keyboardPresentation().modeFor("key.keyboard.fn"));
        assertFalse(preferences.appearance().keyboardPresentation().overrides().containsKey("key.keyboard.left.shift"));

        var sanitized = new KeyboardPresentation(KeyboardPlatform.PC, java.util.Map.of(
            "key.keyboard.a", KeyLegendMode.ICON, "key.mouse.left", KeyLegendMode.TEXT,
            "garbage", KeyLegendMode.TEXT, "key.keyboard.escape", KeyLegendMode.TEXT));
        assertEquals(java.util.Map.of(), sanitized.overrides());
    }
}
