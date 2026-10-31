package dev.keyset.profile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeStoreTest {
    private Path directory;

    @BeforeEach
    void createWorkspaceLocalTempDirectory() throws IOException {
        Path buildDirectory = Path.of("build");
        Files.createDirectories(buildDirectory);
        directory = Files.createTempDirectory(buildDirectory, "theme-store-test-");
    }

    @AfterEach
    void removeWorkspaceLocalTempDirectory() throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void savesAndReloadsThemes() throws IOException {
        Path file = directory.resolve("themes.json");
        ThemeStore store = new ThemeStore(file);
        SavedTheme theme = theme("Deep Sea", 0x235F70);

        store.save(theme);

        assertEquals(List.of(theme), new ThemeStore(file).themes());
        store.remove(theme.id());
        assertTrue(store.themes().isEmpty());
    }

    @Test
    void exportAndImportRoundTripOnlyThemeFields() throws IOException {
        Path sourceFile = directory.resolve("source.json");
        SavedTheme theme = theme("Amber", 0xE7A63B);
        ThemeStore source = new ThemeStore(sourceFile);
        source.save(theme);

        String exported = source.exportTheme(theme.id());
        assertTrue(exported.contains("\"format\": \"keyset-theme\""));
        assertFalse(exported.contains("profiles"));
        assertFalse(exported.contains("bindings"));
        assertFalse(exported.contains("personalPath"));

        ThemeStore destination = new ThemeStore(directory.resolve("destination.json"));
        assertEquals(theme, destination.importTheme(exported));
        assertEquals(List.of(theme), destination.themes());
        assertEquals(theme, destination.importTheme(exported));
        assertEquals(1, destination.themes().size());
    }

    @Test
    void exportsMouseColorAndReadsLegacyExportsWithDefaultMouseColor() throws IOException {
        ThemeStore source = new ThemeStore(directory.resolve("source.json"));
        SavedTheme theme = new SavedTheme(UUID.randomUUID().toString(), "Mouse", 0x112233, 0x223344, 40,
                0x334455, 0x445566, 0x556677, 0xABCDEF);
        source.save(theme);
        String exported = source.exportTheme(theme.id());
        assertTrue(exported.contains("\"mouseBodyColor\": 11259375"));

        ThemeStore imported = new ThemeStore(directory.resolve("imported.json"));
        assertEquals(theme, imported.importTheme(exported));

        String legacyExport = exported.replace(",\n    \"mouseBodyColor\": " + theme.mouseBodyColor(), "");
        SavedTheme legacy = imported.importTheme(legacyExport);
        assertEquals(0x30363B, legacy.mouseBodyColor());
    }

    @Test
    void importDeduplicatesNameIgnoringCaseOnlyWhenPaletteAlsoMatches() throws IOException {
        Path sourceFile = directory.resolve("source.json");
        ThemeStore source = new ThemeStore(sourceFile);
        SavedTheme saved = theme("Ocean", 0x235F70);
        SavedTheme caseVariant = new SavedTheme(UUID.randomUUID().toString(), "  oCEAN ",
                saved.accentColor(), saved.panelColor(), saved.panelOpacity(), saved.boundKeyColor(),
                saved.conflictKeyColor(), saved.unboundKeyColor());
        SavedTheme samePaletteNewName = new SavedTheme(UUID.randomUUID().toString(), "Sea Glass",
                saved.accentColor(), saved.panelColor(), saved.panelOpacity(), saved.boundKeyColor(),
                saved.conflictKeyColor(), saved.unboundKeyColor());
        source.save(caseVariant);
        source.save(samePaletteNewName);

        ThemeStore destination = new ThemeStore(directory.resolve("destination.json"));
        destination.save(saved);
        assertEquals(saved, destination.importTheme(source.exportTheme(caseVariant.id())));
        SavedTheme importedNewName = destination.importTheme(source.exportTheme(samePaletteNewName.id()));

        assertEquals(samePaletteNewName, importedNewName);
        assertEquals(2, destination.themes().size());
    }

    @Test
    void applyingThemeKeepsAllLayoutPreferences() {
        UiAppearance appearance = new UiAppearance(73, 29, true, 1, 2, 25,
                3, 4, 5, KeyboardFormFactor.values()[1]);
        SavedTheme theme = theme("Custom", 0x123456);

        UiAppearance applied = theme.applyTo(appearance);

        assertEquals(appearance.keyboardScale(), applied.keyboardScale());
        assertEquals(appearance.rowHeight(), applied.rowHeight());
        assertEquals(appearance.inputOnRight(), applied.inputOnRight());
        assertEquals(appearance.keyboardFormFactor(), applied.keyboardFormFactor());
        assertEquals(theme.accentColor(), applied.accentColor());
        assertEquals(theme.panelColor(), applied.panelColor());
        assertEquals(theme.panelOpacity(), applied.panelOpacity());
        assertEquals(theme.boundKeyColor(), applied.boundKeyColor());
        assertEquals(theme.conflictKeyColor(), applied.conflictKeyColor());
        assertEquals(theme.unboundKeyColor(), applied.unboundKeyColor());
        assertEquals(theme.mouseBodyColor(), applied.mouseBodyColor());
    }

    @Test
    void separateStoreInstancesPreserveConcurrentAdditions() throws Exception {
        Path file = directory.resolve("themes.json");
        ThemeStore firstStore = new ThemeStore(file);
        ThemeStore secondStore = new ThemeStore(file);
        SavedTheme first = theme("First", 0x102030);
        SavedTheme second = theme("Second", 0x405060);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstSave = executor.submit(() -> saveAfterGate(firstStore, first, ready, start));
            Future<?> secondSave = executor.submit(() -> saveAfterGate(secondStore, second, ready, start));
            ready.await();
            start.countDown();
            firstSave.get();
            secondSave.get();
        }

        List<SavedTheme> themes = new ThemeStore(file).themes();
        assertEquals(2, themes.size());
        assertTrue(themes.containsAll(List.of(first, second)));
    }

    @Test
    void malformedAndOversizedFilesAreRejectedWithoutBeingOverwritten() throws IOException {
        Path file = directory.resolve("themes.json");
        String malformed = "{ broken theme file";
        Files.writeString(file, malformed);
        ThemeStore store = new ThemeStore(file);

        assertThrows(IOException.class, store::themes);
        assertThrows(IOException.class, () -> store.save(theme("Keep", 0x123456)));
        assertThrows(IOException.class, () -> store.remove("00000000-0000-0000-0000-000000000001"));
        assertEquals(malformed, Files.readString(file));

        byte[] oversized = new byte[1024 * 1024 + 1];
        Files.write(file, oversized);
        assertThrows(IOException.class, store::themes);
        assertThrows(IOException.class, () -> store.save(theme("Keep", 0x123456)));
        assertEquals(oversized.length, Files.size(file));
    }

    @Test
    void rejectsMalformedOrOversizedExports() throws IOException {
        ThemeStore store = new ThemeStore(directory.resolve("themes.json"));
        String tooLarge = " ".repeat(64 * 1024 + 1);
        assertThrows(IOException.class, () -> store.importTheme(tooLarge));
        assertThrows(IOException.class, () -> store.importTheme(null));
        assertThrows(IOException.class, () -> store.importTheme("null"));
        assertThrows(IOException.class, () -> store.importTheme("{} {}"));
        assertThrows(IOException.class, () -> store.importTheme("{\"format\":\"keyset-theme\",\"format\":\"keyset-theme\"}"));
        assertTrue(store.themes().isEmpty());

        SavedTheme validTheme = theme("Strict", 0x123456);
        ThemeStore source = new ThemeStore(directory.resolve("source.json"));
        source.save(validTheme);
        String valid = source.exportTheme(validTheme.id());
        assertThrows(IOException.class, () -> store.importTheme(valid.replace(
                "\"panelOpacity\": 40", "\"panelOpacity\": 40.5")));
        assertThrows(IOException.class, () -> store.importTheme(valid.replace(
                "\"unboundKeyColor\": 7833753", "\"unboundKeyColor\": 7833753, \"extra\": true")));
        assertTrue(store.themes().isEmpty());
    }

    @Test
    void importingWithSameIdButDifferentThemeDoesNotReplaceExistingTheme() throws IOException {
        Path sourceFile = directory.resolve("source.json");
        ThemeStore source = new ThemeStore(sourceFile);
        SavedTheme original = theme("Original", 0x123456);
        SavedTheme imported = new SavedTheme(original.id(), "Imported", 0x654321, original.panelColor(),
                original.panelOpacity(), original.boundKeyColor(), original.conflictKeyColor(), original.unboundKeyColor());
        source.save(imported);

        ThemeStore destination = new ThemeStore(directory.resolve("destination.json"));
        destination.save(original);
        SavedTheme copy = destination.importTheme(source.exportTheme(imported.id()));

        assertNotEquals(original.id(), copy.id());
        assertEquals("Imported", copy.name());
        assertEquals(List.of(original, copy), destination.themes());
    }

    @Test
    void directoryImportSkipsInvalidFilesButContinuesThroughLaterValidDuplicates() throws IOException {
        Path exchangeDirectory = Files.createDirectory(directory.resolve("exchange"));
        Path sourceFile = directory.resolve("source.json");
        ThemeStore source = new ThemeStore(sourceFile);
        SavedTheme shared = theme("Shared", 0x123456);
        source.save(shared);
        String validExport = source.exportTheme(shared.id());
        Files.writeString(exchangeDirectory.resolve("a-invalid.json"), "{ broken");
        Files.writeString(exchangeDirectory.resolve("b-valid.json"), validExport);
        Files.writeString(exchangeDirectory.resolve("c-oversized.json"), " ".repeat(64 * 1024 + 1));
        Files.writeString(exchangeDirectory.resolve("d-duplicate.json"), validExport);

        Path destinationFile = directory.resolve("destination.json");
        ThemeStore destination = new ThemeStore(destinationFile);
        ThemeStore.ImportResult result = destination.importDirectory(exchangeDirectory);

        assertEquals(List.of(shared, shared), result.imported());
        assertEquals(2, result.skipped());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().get(0).contains("a-invalid.json"));
        assertTrue(result.errors().get(1).contains("c-oversized.json"));
        assertEquals(List.of(shared), destination.themes());
    }

    @Test
    void directoryImportDoesNotTreatMalformedStoreAsAnInvalidExchangeFile() throws IOException {
        Path exchangeDirectory = Files.createDirectory(directory.resolve("exchange"));
        Path sourceFile = directory.resolve("source.json");
        ThemeStore source = new ThemeStore(sourceFile);
        SavedTheme theme = theme("Valid", 0x123456);
        source.save(theme);
        Files.writeString(exchangeDirectory.resolve("valid.json"), source.exportTheme(theme.id()));

        Path destinationFile = directory.resolve("broken-store.json");
        String malformedStore = "{ malformed stored themes";
        Files.writeString(destinationFile, malformedStore);
        ThemeStore destination = new ThemeStore(destinationFile);

        assertThrows(IOException.class, () -> destination.importDirectory(exchangeDirectory));
        assertEquals(malformedStore, Files.readString(destinationFile));
    }

    @Test
    void directoryImportPropagatesThemeStoreWritePathFailures() throws IOException {
        Path exchangeDirectory = Files.createDirectory(directory.resolve("exchange"));
        Path sourceFile = directory.resolve("source.json");
        ThemeStore source = new ThemeStore(sourceFile);
        SavedTheme theme = theme("Valid", 0x123456);
        source.save(theme);
        Files.writeString(exchangeDirectory.resolve("valid.json"), source.exportTheme(theme.id()));

        Path parentFile = directory.resolve("not-a-directory");
        Files.writeString(parentFile, "blocker");
        ThemeStore destination = new ThemeStore(parentFile.resolve("themes.json"));

        assertThrows(IOException.class, () -> destination.importDirectory(exchangeDirectory));
    }

    private static void saveAfterGate(ThemeStore store, SavedTheme theme,
                                      CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            store.save(theme);
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static SavedTheme theme(String name, int accent) {
        return new SavedTheme(UUID.randomUUID().toString(), name, accent, 0x0A0B0C, 40,
                0x112233, 0x445566, 0x778899);
    }
}
