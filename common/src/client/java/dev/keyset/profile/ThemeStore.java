package dev.keyset.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persists custom keyboard themes in a portable, versioned JSON file. */
public final class ThemeStore {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_EXPORT_BYTES = 64 * 1024;
    private static final String[] THEME_FIELDS = {
            "id", "name", "accentColor", "panelColor", "panelOpacity",
            "boundKeyColor", "conflictKeyColor", "unboundKeyColor", "mouseBodyColor"
    };
    private static final Set<String> LEGACY_THEME_FIELDS = Set.of(
            "id", "name", "accentColor", "panelColor", "panelOpacity",
            "boundKeyColor", "conflictKeyColor", "unboundKeyColor");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;

    public ThemeStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    /** Returns the latest immutable snapshot from disk. */
    public synchronized List<SavedTheme> themes() throws IOException {
        return SharedSettingsFiles.withFileLock(file, () -> List.copyOf(readFileIfPresent()));
    }

    /** Adds or replaces one theme by id while preserving additions made by other clients. */
    public synchronized void save(SavedTheme theme) throws IOException {
        Objects.requireNonNull(theme, "theme");
        SharedSettingsFiles.withFileLock(file, () -> {
            List<SavedTheme> themes = readFileIfPresent();
            int existingIndex = indexOfId(themes, theme.id());
            if (existingIndex >= 0 && themes.get(existingIndex).equals(theme)) return null;
            if (existingIndex >= 0) themes.set(existingIndex, theme);
            else themes.add(theme);
            writeFileLocked(themes);
            return null;
        });
    }

    /** Removes a theme by id. Removing an unknown id is a no-op. */
    public synchronized void remove(String id) throws IOException {
        Objects.requireNonNull(id, "id");
        SharedSettingsFiles.withFileLock(file, () -> {
            List<SavedTheme> themes = readFileIfPresent();
            if (!themes.removeIf(theme -> theme.id().equals(id))) return null;
            writeFileLocked(themes);
            return null;
        });
    }

    /** Exports one theme without profile data, paths, or other local settings. */
    public synchronized String exportTheme(String id) throws IOException {
        Objects.requireNonNull(id, "id");
        return SharedSettingsFiles.withFileLock(file, () -> {
            SavedTheme theme = findTheme(readFileIfPresent(), id);
            return encodeTheme(theme);
        });
    }

    /** Encodes one theme as a bounded portable export, without accessing the filesystem. */
    public static String encodeTheme(SavedTheme theme) throws IOException {
        Objects.requireNonNull(theme, "theme");
        JsonObject envelope = new JsonObject();
        envelope.addProperty("format", "keyset-theme");
        envelope.addProperty("schemaVersion", SCHEMA_VERSION);
        envelope.add("theme", themeToJson(theme));
        try {
            String json = GSON.toJson(envelope);
            requireExportSize(json);
            return json;
        } catch (RuntimeException serializationFailure) {
            throw new IOException("Could not serialize the theme export", serializationFailure);
        }
    }

    /** Decodes a bounded portable theme export, without accessing the filesystem. */
    public static SavedTheme decodeTheme(String json) throws IOException {
        if (json == null) throw new IOException("Theme export cannot be null");
        requireExportSize(json);
        try {
            JsonElement parsed = parseStrictJson(json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("The export value must be an object");
            JsonObject envelope = parsed.getAsJsonObject();
            requireExactKeys(envelope, Set.of("format", "schemaVersion", "theme"), "theme export");
            if (!"keyset-theme".equals(requireString(envelope, "format"))) {
                throw new IllegalArgumentException("Unsupported theme export format");
            }
            if (requireInteger(envelope, "schemaVersion") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported theme export schema version");
            }
            return readTheme(requireMember(envelope, "theme"));
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            throw new IOException("Invalid theme export", exception);
        }
    }

    /** Imports and saves a single portable theme, returning an existing exact duplicate when present. */
    public synchronized SavedTheme importTheme(String json) throws IOException {
        SavedTheme imported = decodeTheme(json);
        return SharedSettingsFiles.withFileLock(file, () -> {
            List<SavedTheme> themes = readFileIfPresent();
            SavedTheme duplicate = themes.stream().filter(existing -> sameNameAndPalette(existing, imported))
                    .findFirst().orElse(null);
            if (duplicate != null) return duplicate;

            SavedTheme toSave = imported;
            int idIndex = indexOfId(themes, toSave.id());
            if (idIndex >= 0) {
                SavedTheme existing = themes.get(idIndex);
                if (existing.equals(toSave)) return existing;
                toSave = withNewId(toSave);
            }
            themes.add(toSave);
            writeFileLocked(themes);
            return toSave;
        });
    }

    /** Imports each JSON file in sorted order, returning valid themes and recording invalid files. */
    public synchronized ImportResult importDirectory(Path directory) throws IOException {
        Path source = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) throw new IOException("Theme import directory does not exist: " + source);

        List<Path> candidates;
        try (var entries = Files.list(source)) {
            candidates = entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
        }

        List<SavedTheme> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path candidate : candidates) {
            String json;
            try {
                if (!Files.isRegularFile(candidate)) throw new IOException("Not a regular theme file");
                json = readBoundedThemeFile(candidate);
                decodeTheme(json);
            } catch (IOException | RuntimeException invalidFile) {
                errors.add(candidate + ": " + Objects.toString(invalidFile.getMessage(),
                        invalidFile.getClass().getSimpleName()));
                continue;
            }

            // Parsing and size validation are complete; storage errors must reach the caller.
            imported.add(importTheme(json));
        }
        return new ImportResult(imported, errors.size(), errors);
    }

    private List<SavedTheme> readFileIfPresent() throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();

        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Theme file exceeds 1 MiB: " + file);

        String contents = decodeUtf8(bytes, file.toString());
        try {
            JsonElement parsed = parseStrictJson(contents);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("The root value must be an object");
            JsonObject root = parsed.getAsJsonObject();
            requireExactKeys(root, Set.of("schemaVersion", "themes"), "theme file");
            int schemaVersion = requireInteger(root, "schemaVersion");
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported theme schema version: " + schemaVersion);
            }
            JsonElement themesValue = requireMember(root, "themes");
            if (!themesValue.isJsonArray()) throw new IllegalArgumentException("themes must be an array");

            List<SavedTheme> themes = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement themeValue : themesValue.getAsJsonArray()) {
                SavedTheme theme = readTheme(themeValue);
                if (!ids.add(theme.id())) throw new IllegalArgumentException("Theme ids must be unique");
                themes.add(theme);
            }
            return themes;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            throw new IOException("Invalid theme file: " + file, exception);
        }
    }

    private void writeFileLocked(List<SavedTheme> themes) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray values = new JsonArray();
        for (SavedTheme theme : themes) values.add(themeToJson(theme));
        root.add("themes", values);
        try {
            byte[] contents = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            if (contents.length > MAX_FILE_BYTES) throw new IOException("Theme file exceeds 1 MiB: " + file);
            SharedSettingsFiles.writeBytesAtomically(file, () -> contents);
        } catch (RuntimeException serializationFailure) {
            throw new IOException("Could not serialize the theme file: " + file, serializationFailure);
        }
    }

    private static SavedTheme readTheme(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("Each theme must be an object");
        JsonObject object = value.getAsJsonObject();
        if (!object.keySet().equals(Set.of(THEME_FIELDS)) && !object.keySet().equals(LEGACY_THEME_FIELDS)) {
            throw new IllegalArgumentException("theme has missing or unsupported fields");
        }
        return new SavedTheme(requireString(object, "id"), requireString(object, "name"),
                requireInteger(object, "accentColor"), requireInteger(object, "panelColor"),
                requireInteger(object, "panelOpacity"), requireInteger(object, "boundKeyColor"),
                requireInteger(object, "conflictKeyColor"), requireInteger(object, "unboundKeyColor"),
                object.has("mouseBodyColor") ? requireInteger(object, "mouseBodyColor") : 0x30363B);
    }

    private static JsonObject themeToJson(SavedTheme theme) {
        JsonObject object = new JsonObject();
        object.addProperty("id", theme.id());
        object.addProperty("name", theme.name());
        object.addProperty("accentColor", theme.accentColor());
        object.addProperty("panelColor", theme.panelColor());
        object.addProperty("panelOpacity", theme.panelOpacity());
        object.addProperty("boundKeyColor", theme.boundKeyColor());
        object.addProperty("conflictKeyColor", theme.conflictKeyColor());
        object.addProperty("unboundKeyColor", theme.unboundKeyColor());
        object.addProperty("mouseBodyColor", theme.mouseBodyColor());
        return object;
    }

    private static JsonElement parseStrictJson(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = readJsonValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
            return result;
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            throw new JsonParseException("Malformed JSON", exception);
        }
    }

    private static JsonElement readJsonValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException("Duplicate JSON field: " + name);
                    object.add(name, readJsonValue(reader));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(readJsonValue(reader));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Expected a JSON value");
        };
    }

    private static void requireExportSize(String json) throws IOException {
        if (json.length() > MAX_EXPORT_BYTES) throw new IOException("Theme export exceeds 64 KiB");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_EXPORT_BYTES) {
            throw new IOException("Theme export exceeds 64 KiB");
        }
    }

    private static String readBoundedThemeFile(Path file) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_EXPORT_BYTES + 1);
        }
        if (bytes.length > MAX_EXPORT_BYTES) throw new IOException("Theme export exceeds 64 KiB");
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Theme export is not valid UTF-8", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes, String source) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Theme JSON is not valid UTF-8: " + source, exception);
        }
    }

    private static SavedTheme findTheme(List<SavedTheme> themes, String id) {
        return themes.stream().filter(theme -> theme.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No theme with id: " + id));
    }

    private static int indexOfId(List<SavedTheme> themes, String id) {
        for (int index = 0; index < themes.size(); index++) {
            if (themes.get(index).id().equals(id)) return index;
        }
        return -1;
    }

    private static SavedTheme withNewId(SavedTheme theme) {
        return new SavedTheme(UUID.randomUUID().toString(), theme.name(), theme.accentColor(), theme.panelColor(),
                theme.panelOpacity(), theme.boundKeyColor(), theme.conflictKeyColor(), theme.unboundKeyColor(),
                theme.mouseBodyColor());
    }

    private static boolean sameNameAndPalette(SavedTheme first, SavedTheme second) {
        return first.name().equalsIgnoreCase(second.name())
                && first.accentColor() == second.accentColor()
                && first.panelColor() == second.panelColor()
                && first.panelOpacity() == second.panelOpacity()
                && first.boundKeyColor() == second.boundKeyColor()
                && first.conflictKeyColor() == second.conflictKeyColor()
                && first.unboundKeyColor() == second.unboundKeyColor()
                && first.mouseBodyColor() == second.mouseBodyColor();
    }

    private static void requireExactKeys(JsonObject object, Set<String> expected, String description) {
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException(description + " has missing or unsupported fields");
        }
    }

    private static JsonElement requireMember(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing required field: " + name);
        return value;
    }

    private static String requireString(JsonObject object, String name) {
        JsonElement value = requireMember(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static int requireInteger(JsonObject object, String name) {
        JsonElement value = requireMember(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        String raw = value.getAsString();
        if (!raw.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    /** Result of a directory import; valid duplicates are included in {@code imported}. */
    public record ImportResult(List<SavedTheme> imported, int skipped, List<String> errors) {
        public ImportResult {
            imported = List.copyOf(Objects.requireNonNull(imported, "imported"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            if (skipped < 0) throw new IllegalArgumentException("skipped cannot be negative");
        }
    }
}
