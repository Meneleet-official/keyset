package dev.keyset.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persists client key-binding profiles in a versioned JSON file. */
public final class ProfileStore {
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_NAME_LENGTH = 48;

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private List<Profile> profiles = new ArrayList<>();
    private String activeProfileId;
    private final Set<String> migratedLegacyProfileIds = new LinkedHashSet<>();
    private boolean legacyDuplicateRepairComplete;
    private boolean loaded;
    private PersistedState baseline;

    public ProfileStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    /**
     * Loads the persisted profiles, or creates and saves a default profile from {@code current}.
     * A corrupt or unsupported file is reported as an IOException and left untouched.
     */
    public void load(Map<String, Binding> current) throws IOException {
        load(current, null);
    }

    /** Keeps the editor usable when neither the shared file nor its recovery file can be opened. */
    public void initializeTransient(Map<String, Binding> current) {
        Profile fallback = new Profile(UUID.randomUUID().toString(), "Default", copyBindings(current));
        apply(new PersistedState(fallback.id(), List.of(fallback), Set.of(), false));
        baseline = null;
        loaded = true;
    }

    /**
     * Loads the shared store or initializes it from a legacy profile file while holding the shared-file lock.
     * The legacy file is considered only if the shared file does not yet exist.
     */
    public void load(Map<String, Binding> current, Path initialLegacyFile) throws IOException {
        Map<String, Binding> initialBindings = copyBindings(current);
        SharedSettingsFiles.withFileLock(file, () -> {
            if (Files.exists(file)) {
                PersistedState state = readFile(file);
                apply(state);
                baseline = state;
                loaded = true;
                return null;
            }

            PersistedState initialState;
            if (initialLegacyFile != null && Files.isRegularFile(initialLegacyFile)) {
                initialState = readFile(initialLegacyFile);
            } else {
                Profile initial = new Profile(UUID.randomUUID().toString(), "Default", initialBindings);
                initialState = new PersistedState(initial.id(), List.of(initial), Set.of(), false);
            }

            validateMergedState(initialState);
            writeJsonLocked(file, toJson(initialState));
            apply(initialState);
            baseline = initialState;
            loaded = true;
            return null;
        });
    }

    /** Returns an immutable snapshot of profiles in their stored order. */
    public List<Profile> profiles() {
        requireLoaded();
        return List.copyOf(profiles);
    }

    /** Returns the immutable active profile snapshot. */
    public Profile active() {
        requireLoaded();
        return findProfile(activeProfileId);
    }

    /** Returns an immutable in-memory checkpoint of the current profile selection and data. */
    public State checkpoint() {
        requireLoaded();
        return new State(profiles, activeProfileId, migratedLegacyProfileIds, legacyDuplicateRepairComplete);
    }

    /** Captures memory and the disk baseline so a failed external reload can be rolled back. */
    public ReloadCheckpoint reloadCheckpoint() {
        requireLoaded();
        return new ReloadCheckpoint(currentState(), baseline);
    }

    /** Restores a reload checkpoint, including its prior disk baseline. */
    public void restoreReloadCheckpoint(ReloadCheckpoint checkpoint) {
        requireLoaded();
        Objects.requireNonNull(checkpoint, "checkpoint");
        apply(checkpoint.currentState);
        baseline = checkpoint.baseline;
    }

    /** Restores a checkpoint in memory without writing to disk. */
    public void restore(State checkpoint) {
        requireLoaded();
        Objects.requireNonNull(checkpoint, "checkpoint");
        List<Profile> restoredProfiles = new ArrayList<>(checkpoint.profiles());
        validateLoadedProfiles(restoredProfiles, checkpoint.activeProfileId());
        checkpoint.migratedLegacyProfileIds().forEach(id -> requireUuid(id, "migrated legacy profile id"));

        profiles = restoredProfiles;
        activeProfileId = checkpoint.activeProfileId();
        migratedLegacyProfileIds.clear();
        migratedLegacyProfileIds.addAll(checkpoint.migratedLegacyProfileIds());
        legacyDuplicateRepairComplete = checkpoint.legacyDuplicateRepairComplete();
    }

    /**
     * Rebases an undo checkpoint onto the current merged state after a save. The attempted operation is
     * inverted against current memory, preserving unrelated changes merged from another client.
     */
    public State rebaseCheckpoint(State before, State attempted) throws IOException {
        requireLoaded();
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(attempted, "attempted");
        PersistedState rebased = mergeStates(toPersistedState(attempted), toPersistedState(before), currentState());
        validateMergedState(rebased);
        return new State(rebased.profiles(), rebased.activeProfileId(), rebased.migratedLegacyProfileIds(),
                rebased.legacyDuplicateRepairComplete());
    }

    /** Creates a profile from the current bindings and makes it active. */
    public Profile create(String name, Map<String, Binding> current) {
        requireLoaded();
        String normalizedName = normalizeName(name);
        requireUniqueName(normalizedName, null);
        Map<String, Binding> bindings = copyBindings(current);

        Profile created = new Profile(UUID.randomUUID().toString(), normalizedName, bindings);
        profiles.add(created);
        activeProfileId = created.id();
        return created;
    }

    /** Replaces known bindings from the live configuration while retaining missing entries. */
    public void updateActive(Map<String, Binding> current) {
        requireLoaded();
        Map<String, Binding> merged = new LinkedHashMap<>(active().bindings());
        merged.putAll(copyBindings(current));
        replaceProfile(new Profile(activeProfileId, active().name(), merged));
    }

    /** Selects a profile. The caller should save the current live bindings before selecting. */
    public Profile select(String id) {
        requireLoaded();
        Profile selected = findProfile(Objects.requireNonNull(id, "id"));
        activeProfileId = selected.id();
        return selected;
    }

    /** Renames a profile after trimming surrounding whitespace. */
    public void rename(String id, String name) {
        requireLoaded();
        Profile existing = findProfile(Objects.requireNonNull(id, "id"));
        String normalizedName = normalizeName(name);
        requireUniqueName(normalizedName, existing.id());
        replaceProfile(new Profile(existing.id(), normalizedName, existing.bindings()));
    }

    /** Deletes a profile, selecting the first remaining profile if the active one was deleted. */
    public void delete(String id) {
        requireLoaded();
        Profile toDelete = findProfile(Objects.requireNonNull(id, "id"));
        if (profiles.size() == 1) {
            throw new IllegalStateException("Cannot delete the final profile");
        }

        profiles.remove(toDelete);
        migratedLegacyProfileIds.add(toDelete.id());
        if (toDelete.id().equals(activeProfileId)) {
            activeProfileId = profiles.get(0).id();
        }
    }

    /** Writes the current state using a temporary sibling file and an atomic move when available. */
    public void save() throws IOException {
        requireLoaded();
        SharedSettingsFiles.withFileLock(file, () -> {
            PersistedState local = currentState();
            PersistedState disk = Files.exists(file) ? readFile() : null;
            PersistedState merged;
            if (disk == null) {
                if (baseline != null) {
                    throw new SharedSettingsConflictException("The shared profile file was removed after it was loaded");
                }
                merged = local;
            } else if (baseline == null) {
                merged = mergeInitialStates(local, disk);
            } else {
                merged = mergeStates(baseline, local, disk);
            }

            validateMergedState(merged);
            writeJsonLocked(file, toJson(merged));
            apply(merged);
            baseline = merged;
            return null;
        });
    }

    /** Reloads a newer shared file when this instance has no unsaved profile changes. */
    public boolean reloadIfChanged() throws IOException {
        requireLoaded();
        return SharedSettingsFiles.withFileLock(file, () -> {
            if (!Files.exists(file)) {
                if (baseline == null) return false;
                throw new IOException("The shared profile file was removed after it was loaded: " + file);
            }
            PersistedState disk = readFile();
            if (disk.equals(baseline)) return false;
            if (!currentState().equals(baseline)) return false;
            apply(disk);
            baseline = disk;
            return true;
        });
    }

    /** Exports one profile in the same versioned format used by the persistent profile store. */
    public void exportProfile(String id, Path destination) throws IOException {
        requireLoaded();
        Profile profile = findProfile(Objects.requireNonNull(id, "id"));
        writeJson(destination, toJson(List.of(profile), profile.id()));
    }

    /** Imports all JSON profile files in a portable exchange directory. Existing bindings are retained. */
    public int importDirectory(Path directory) throws IOException {
        requireLoaded();
        if (!Files.isDirectory(directory)) throw new IOException("Profile exchange directory does not exist: " + directory);
        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        if (files.isEmpty()) throw new IOException("No profile JSON files were found in: " + directory);

        // Parse every file before changing the store; a broken file cannot partially import a batch.
        List<Profile> incoming = new ArrayList<>();
        for (Path path : files) incoming.addAll(readFile(path).profiles());
        return mergeImported(incoming);
    }

    /** Merges the profiles in a legacy or portable store file into this store. */
    public int importFile(Path file) throws IOException {
        requireLoaded();
        return mergeImported(readFile(file).profiles());
    }

    /** Consumes legacy profile identities once, preserving subsequent shared edits and deletions. */
    public boolean importLegacyFile(Path legacyFile) throws IOException {
        requireLoaded();
        List<Profile> incoming = readFile(legacyFile).profiles();
        boolean changed = false;
        for (Profile source : incoming) {
            if (!migratedLegacyProfileIds.add(source.id())) continue;
            changed = true;
            boolean exists = profiles.stream().anyMatch(existing -> existing.id().equals(source.id())
                    || ((existing.name().equalsIgnoreCase(source.name())
                        || isImportedCopyName(source.name(), existing.name())
                        || isImportedCopyName(existing.name(), source.name()))
                        && existing.bindings().entrySet().containsAll(source.bindings().entrySet())));
            if (!exists) {
                profiles.add(new Profile(source.id(), uniqueImportedName(source.name()), source.bindings()));
            }
        }
        return changed;
    }

    /** Repairs old numbered imports once, retaining every binding and returning route replacements. */
    public Map<String, String> removeRedundantImportedCopies() {
        requireLoaded();
        if (legacyDuplicateRepairComplete) return Map.of();
        Map<String, String> replacements = new LinkedHashMap<>();
        for (Profile candidate : List.copyOf(profiles)) {
            if (candidate.bindings().isEmpty()) continue;
            Profile original = profiles.stream()
                    .filter(existing -> !existing.id().equals(candidate.id())
                            && isImportedCopyName(existing.name(), candidate.name())
                            && existing.bindings().entrySet().containsAll(candidate.bindings().entrySet()))
                    .findFirst().orElse(null);
            if (original == null) continue;
            profiles.remove(candidate);
            migratedLegacyProfileIds.add(candidate.id());
            replacements.replaceAll((removed, kept) -> kept.equals(candidate.id()) ? original.id() : kept);
            replacements.put(candidate.id(), original.id());
            if (activeProfileId.equals(candidate.id())) activeProfileId = original.id();
        }
        legacyDuplicateRepairComplete = true;
        return Map.copyOf(replacements);
    }

    private static boolean isImportedCopyName(String original, String candidate) {
        int suffixStart = candidate.lastIndexOf(" (");
        if (suffixStart < 0 || !candidate.endsWith(")")) return false;
        String number = candidate.substring(suffixStart + 2, candidate.length() - 1);
        try {
            if (Integer.parseInt(number) < 2 || !number.equals(Integer.toString(Integer.parseInt(number)))) return false;
        } catch (NumberFormatException invalidSuffix) {
            return false;
        }
        String suffix = candidate.substring(suffixStart);
        int maxBase = Math.max(1, MAX_NAME_LENGTH - suffix.codePointCount(0, suffix.length()));
        int end = original.offsetByCodePoints(0, Math.min(original.codePointCount(0, original.length()), maxBase));
        return candidate.equalsIgnoreCase(original.substring(0, end).stripTrailing() + suffix);
    }

    private int mergeImported(List<Profile> incoming) {
        int imported = 0;
        for (Profile source : incoming) {
            if (profiles.stream().anyMatch(existing -> existing.bindings().equals(source.bindings())
                    && (existing.id().equals(source.id()) || existing.name().equals(source.name())
                        || existing.name().startsWith(source.name() + " (")))) continue;
            String name = uniqueImportedName(source.name());
            profiles.add(new Profile(UUID.randomUUID().toString(), name, source.bindings()));
            imported++;
        }
        return imported;
    }

    private void writeJson(Path destination, JsonObject json) throws IOException {
        Path target = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        SharedSettingsFiles.withFileLock(target, () -> {
            writeJsonLocked(target, json);
            return null;
        });
    }

    private void writeJsonLocked(Path target, JsonObject json) throws IOException {
        writeJsonLocked(target, writer -> gson.toJson(json, writer));
    }

    void writeJsonLocked(Path target, SharedSettingsFiles.JsonWriter writer) throws IOException {
        try {
            SharedSettingsFiles.writeJsonAtomically(target, writer);
        } catch (JsonIOException serializationFailure) {
            throw new IOException("Could not serialize the shared profile file: " + target, serializationFailure);
        }
    }

    private PersistedState readFile() throws IOException {
        return readFile(file);
    }

    private static PersistedState readFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("The root value must be an object");
            }

            JsonObject root = parsed.getAsJsonObject();
            int schemaVersion = requireInteger(root, "schemaVersion");
            if (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported profile schema version: " + schemaVersion);
            }

            String activeId = requireString(root, "activeProfileId");
            requireUuid(activeId, "activeProfileId");
            JsonElement profilesValue = requireMember(root, "profiles");
            if (!profilesValue.isJsonArray()) {
                throw new IllegalArgumentException("profiles must be an array");
            }

            List<Profile> loadedProfiles = new ArrayList<>();
            for (JsonElement profileValue : profilesValue.getAsJsonArray()) {
                loadedProfiles.add(readProfile(profileValue));
            }
            if (loadedProfiles.isEmpty()) {
                throw new IllegalArgumentException("At least one profile is required");
            }

            validateLoadedProfiles(loadedProfiles, activeId);
            Set<String> migratedIds = new LinkedHashSet<>(optionalStringList(root, "migratedLegacyProfileIds", List.of()));
            migratedIds.forEach(id -> requireUuid(id, "migrated legacy profile id"));
            return new PersistedState(activeId, loadedProfiles, migratedIds,
                    optionalBoolean(root, "legacyDuplicateRepairComplete", false));
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            throw new IOException("Invalid profile file: " + file, exception);
        }
    }

    private static Profile readProfile(JsonElement profileValue) {
        if (!profileValue.isJsonObject()) {
            throw new IllegalArgumentException("Each profile must be an object");
        }
        JsonObject profileObject = profileValue.getAsJsonObject();
        String id = requireString(profileObject, "id");
        requireUuid(id, "profile id");
        String name = requireString(profileObject, "name");
        String normalizedName = normalizeName(name);
        if (!name.equals(normalizedName)) {
            throw new IllegalArgumentException("Profile names must already be trimmed");
        }

        JsonElement bindingsValue = requireMember(profileObject, "bindings");
        if (!bindingsValue.isJsonObject()) {
            throw new IllegalArgumentException("bindings must be an object");
        }
        Map<String, Binding> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : bindingsValue.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Each binding must be an object");
            }
            JsonObject bindingObject = entry.getValue().getAsJsonObject();
            bindings.put(entry.getKey(), new Binding(
                    requireString(bindingObject, "key"),
                    requireBoolean(bindingObject, "ctrl"),
                    requireBoolean(bindingObject, "shift"),
                    requireBoolean(bindingObject, "alt"),
                    optionalBoolean(bindingObject, "command", false),
                    optionalStringList(bindingObject, "extraKeys", List.of()),
                    optionalString(bindingObject, "nativeSettings")));
        }
        return new Profile(id, name, bindings);
    }

    private static void validateLoadedProfiles(List<Profile> loadedProfiles, String activeId) {
        requireUuid(activeId, "activeProfileId");
        Map<String, Boolean> ids = new LinkedHashMap<>();
        Map<String, Boolean> names = new LinkedHashMap<>();
        boolean hasActiveProfile = false;
        for (Profile profile : loadedProfiles) {
            requireUuid(profile.id(), "profile id");
            if (!profile.name().equals(normalizeName(profile.name()))) {
                throw new IllegalArgumentException("Profile names must already be trimmed");
            }
            if (ids.put(profile.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate profile id: " + profile.id());
            }
            String nameKey = profile.name().toLowerCase(Locale.ROOT);
            if (names.put(nameKey, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate profile name: " + profile.name());
            }
            hasActiveProfile |= profile.id().equals(activeId);
        }
        if (!hasActiveProfile) {
            throw new IllegalArgumentException("activeProfileId does not identify a stored profile");
        }
    }

    private static PersistedState mergeStates(PersistedState base, PersistedState local,
                                             PersistedState remote) throws SharedSettingsConflictException {
        Map<String, Profile> baseProfiles = profilesById(base.profiles());
        Map<String, Profile> localProfiles = profilesById(local.profiles());
        Map<String, Profile> remoteProfiles = profilesById(remote.profiles());
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(baseProfiles.keySet());
        ids.addAll(localProfiles.keySet());
        ids.addAll(remoteProfiles.keySet());

        Map<String, Profile> mergedById = new LinkedHashMap<>();
        for (String id : ids) {
            Profile before = baseProfiles.get(id);
            Profile localProfile = localProfiles.get(id);
            Profile remoteProfile = remoteProfiles.get(id);
            if (before == null) {
                if (localProfile == null) {
                    if (remoteProfile != null) mergedById.put(id, remoteProfile);
                } else if (remoteProfile == null || localProfile.equals(remoteProfile)) {
                    mergedById.put(id, localProfile);
                } else {
                    throw conflict("profile " + id + " was added differently");
                }
                continue;
            }

            if (localProfile == null && remoteProfile == null) continue;
            if (localProfile == null) {
                if (!remoteProfile.equals(before)) throw conflict("profile " + id + " was deleted and edited");
                continue;
            }
            if (remoteProfile == null) {
                if (!localProfile.equals(before)) throw conflict("profile " + id + " was edited and deleted");
                continue;
            }

            String name = mergeValue(before.name(), localProfile.name(), remoteProfile.name(),
                    "name of profile " + id);
            Map<String, Binding> bindings = mergeMap(before.bindings(), localProfile.bindings(),
                    remoteProfile.bindings(), "binding in profile " + id);
            mergedById.put(id, new Profile(id, name, bindings));
        }

        List<Profile> mergedProfiles = new ArrayList<>();
        Set<String> orderedIds = new LinkedHashSet<>();
        addProfilesInOrder(remote.profiles(), mergedById, orderedIds, mergedProfiles);
        addProfilesInOrder(local.profiles(), mergedById, orderedIds, mergedProfiles);
        addProfilesInOrder(base.profiles(), mergedById, orderedIds, mergedProfiles);

        String activeId = mergeValue(base.activeProfileId(), local.activeProfileId(), remote.activeProfileId(),
                "active profile selection");
        if (mergedById.containsKey(activeId)) {
            return new PersistedState(activeId, mergedProfiles,
                    union(base.migratedLegacyProfileIds(), local.migratedLegacyProfileIds(),
                            remote.migratedLegacyProfileIds()),
                    base.legacyDuplicateRepairComplete() || local.legacyDuplicateRepairComplete()
                            || remote.legacyDuplicateRepairComplete());
        }

        // Deleting the selected profile on one side must not discard a still-valid selection from the other.
        if (mergedById.containsKey(local.activeProfileId())) activeId = local.activeProfileId();
        else if (mergedById.containsKey(remote.activeProfileId())) activeId = remote.activeProfileId();
        else if (!mergedProfiles.isEmpty()) activeId = mergedProfiles.get(0).id();
        else throw conflict("all profiles were deleted by concurrent edits");
        return new PersistedState(activeId, mergedProfiles,
                union(base.migratedLegacyProfileIds(), local.migratedLegacyProfileIds(),
                        remote.migratedLegacyProfileIds()),
                base.legacyDuplicateRepairComplete() || local.legacyDuplicateRepairComplete()
                        || remote.legacyDuplicateRepairComplete());
    }

    /** Handles two instances that both loaded before the settings file was first created. */
    private static PersistedState mergeInitialStates(PersistedState local, PersistedState remote)
            throws SharedSettingsConflictException {
        Map<String, Profile> mergedById = profilesById(remote.profiles());
        Map<String, String> localIdToMergedId = new LinkedHashMap<>();
        for (Profile candidate : local.profiles()) {
            Profile sameId = mergedById.get(candidate.id());
            if (sameId != null) {
                if (!sameId.equals(candidate)) throw conflict("profile " + candidate.id() + " was added differently");
                localIdToMergedId.put(candidate.id(), candidate.id());
                continue;
            }

            Profile sameName = mergedById.values().stream()
                    .filter(existing -> existing.name().equalsIgnoreCase(candidate.name()))
                    .findFirst().orElse(null);
            if (sameName != null) {
                if (!sameName.bindings().equals(candidate.bindings())) {
                    throw conflict("profile name '" + candidate.name() + "' was added with different bindings");
                }
                localIdToMergedId.put(candidate.id(), sameName.id());
                continue;
            }
            mergedById.put(candidate.id(), candidate);
            localIdToMergedId.put(candidate.id(), candidate.id());
        }

        String localActive = localIdToMergedId.get(local.activeProfileId());
        String activeId = localActive == null ? remote.activeProfileId() : localActive;
        List<Profile> mergedProfiles = new ArrayList<>(remote.profiles());
        for (Profile candidate : local.profiles()) {
            String mergedId = localIdToMergedId.get(candidate.id());
            if (!containsProfile(mergedProfiles, mergedId)) mergedProfiles.add(mergedById.get(mergedId));
        }
        return new PersistedState(activeId, mergedProfiles,
                union(local.migratedLegacyProfileIds(), remote.migratedLegacyProfileIds()),
                local.legacyDuplicateRepairComplete() || remote.legacyDuplicateRepairComplete());
    }

    private static Map<String, Profile> profilesById(List<Profile> profiles) {
        Map<String, Profile> result = new LinkedHashMap<>();
        for (Profile profile : profiles) result.put(profile.id(), profile);
        return result;
    }

    private static void addProfilesInOrder(List<Profile> ordered, Map<String, Profile> mergedById,
                                           Set<String> seen, List<Profile> result) {
        for (Profile profile : ordered) {
            Profile merged = mergedById.get(profile.id());
            if (merged != null && seen.add(merged.id())) result.add(merged);
        }
    }

    private static boolean containsProfile(List<Profile> profiles, String id) {
        return profiles.stream().anyMatch(profile -> profile.id().equals(id));
    }

    private static Map<String, Binding> mergeMap(Map<String, Binding> base, Map<String, Binding> local,
                                                 Map<String, Binding> remote, String field)
            throws SharedSettingsConflictException {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(base.keySet());
        keys.addAll(local.keySet());
        keys.addAll(remote.keySet());
        Map<String, Binding> merged = new LinkedHashMap<>();
        for (String key : keys) {
            Presence<Binding> selected = mergeValue(presence(base, key), presence(local, key),
                    presence(remote, key), field + " '" + key + "'");
            if (selected.present()) merged.put(key, selected.value());
        }
        return merged;
    }

    private static <T> Presence<T> presence(Map<String, T> values, String key) {
        return new Presence<>(values.containsKey(key), values.get(key));
    }

    private static <T> T mergeValue(T base, T local, T remote, String field)
            throws SharedSettingsConflictException {
        if (Objects.equals(local, base)) return remote;
        if (Objects.equals(remote, base)) return local;
        if (Objects.equals(local, remote)) return local;
        throw conflict(field + " changed differently in two clients");
    }

    @SafeVarargs
    private static <T> Set<T> union(Set<T>... values) {
        Set<T> result = new LinkedHashSet<>();
        for (Set<T> set : values) result.addAll(set);
        return result;
    }

    private static SharedSettingsConflictException conflict(String message) {
        return new SharedSettingsConflictException("Shared profile conflict: " + message);
    }

    private static void validateMergedState(PersistedState state) throws SharedSettingsConflictException {
        try {
            validateLoadedProfiles(new ArrayList<>(state.profiles()), state.activeProfileId());
        } catch (IllegalArgumentException invalidMerge) {
            throw new SharedSettingsConflictException("Shared profile conflict: " + invalidMerge.getMessage());
        }
    }

    private PersistedState currentState() {
        return new PersistedState(activeProfileId, profiles, migratedLegacyProfileIds, legacyDuplicateRepairComplete);
    }

    private static PersistedState toPersistedState(State state) {
        return new PersistedState(state.activeProfileId(), state.profiles(), state.migratedLegacyProfileIds(),
                state.legacyDuplicateRepairComplete());
    }

    private void apply(PersistedState state) {
        profiles = new ArrayList<>(state.profiles());
        activeProfileId = state.activeProfileId();
        migratedLegacyProfileIds.clear();
        migratedLegacyProfileIds.addAll(state.migratedLegacyProfileIds());
        legacyDuplicateRepairComplete = state.legacyDuplicateRepairComplete();
    }

    private JsonObject toJson(PersistedState state) {
        JsonObject root = toJson(state.profiles(), state.activeProfileId());
        JsonArray migratedIds = new JsonArray();
        state.migratedLegacyProfileIds().stream().sorted().forEach(migratedIds::add);
        root.add("migratedLegacyProfileIds", migratedIds);
        root.addProperty("legacyDuplicateRepairComplete", state.legacyDuplicateRepairComplete());
        return root;
    }

    private JsonObject toJson(List<Profile> profilesToWrite, String activeId) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("activeProfileId", activeId);
        JsonArray profilesArray = new JsonArray();
        for (Profile profile : profilesToWrite) {
            JsonObject profileObject = new JsonObject();
            profileObject.addProperty("id", profile.id());
            profileObject.addProperty("name", profile.name());
            JsonObject bindingsObject = new JsonObject();
            for (Map.Entry<String, Binding> entry : profile.bindings().entrySet()) {
                Binding binding = entry.getValue();
                JsonObject bindingObject = new JsonObject();
                bindingObject.addProperty("key", binding.key());
                bindingObject.addProperty("ctrl", binding.ctrl());
                bindingObject.addProperty("shift", binding.shift());
                bindingObject.addProperty("alt", binding.alt());
                bindingObject.addProperty("command", binding.command());
                if (!binding.extraKeys().isEmpty()) {
                    JsonArray extraKeys = new JsonArray();
                    binding.extraKeys().forEach(extraKeys::add);
                    bindingObject.add("extraKeys", extraKeys);
                }
                if (binding.nativeSettings() != null) {
                    bindingObject.addProperty("nativeSettings", binding.nativeSettings());
                }
                bindingsObject.add(entry.getKey(), bindingObject);
            }
            profileObject.add("bindings", bindingsObject);
            profilesArray.add(profileObject);
        }
        root.add("profiles", profilesArray);
        return root;
    }

    private String uniqueImportedName(String sourceName) {
        if (isNameAvailable(sourceName)) return sourceName;
        int suffixNumber = 2;
        while (true) {
            String suffix = " (" + suffixNumber++ + ")";
            int maxBaseCodePoints = Math.max(1, MAX_NAME_LENGTH - suffix.codePointCount(0, suffix.length()));
            int end = sourceName.offsetByCodePoints(0, Math.min(sourceName.codePointCount(0, sourceName.length()), maxBaseCodePoints));
            String candidate = sourceName.substring(0, end).stripTrailing() + suffix;
            if (isNameAvailable(candidate)) return candidate;
        }
    }

    private boolean isNameAvailable(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return profiles.stream().noneMatch(profile -> profile.name().toLowerCase(Locale.ROOT).equals(normalized));
    }

    private void replaceProfile(Profile replacement) {
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).id().equals(replacement.id())) {
                profiles.set(index, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No profile with id: " + replacement.id());
    }

    private Profile findProfile(String id) {
        return profiles.stream()
                .filter(profile -> profile.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No profile with id: " + id));
    }

    private void requireUniqueName(String name, String ignoredId) {
        String nameKey = name.toLowerCase(Locale.ROOT);
        for (Profile profile : profiles) {
            if (!profile.id().equals(ignoredId) && profile.name().toLowerCase(Locale.ROOT).equals(nameKey)) {
                throw new IllegalArgumentException("A profile named '" + name + "' already exists");
            }
        }
    }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A profile name cannot be empty");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A profile name cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private static Map<String, Binding> copyBindings(Map<String, Binding> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        Map<String, Binding> copy = new LinkedHashMap<>();
        bindings.forEach((id, binding) -> {
            Objects.requireNonNull(id, "binding id");
            Objects.requireNonNull(binding, "binding");
            if (id.isBlank()) {
                throw new IllegalArgumentException("A binding id cannot be blank");
            }
            copy.put(id, binding);
        });
        return copy;
    }

    private void requireLoaded() {
        if (!loaded) {
            throw new IllegalStateException("ProfileStore.load must be called first");
        }
    }

    private static JsonElement requireMember(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }
        return value;
    }

    private static String requireString(JsonObject object, String name) {
        JsonElement value = requireMember(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean requireBoolean(JsonObject object, String name) {
        JsonElement value = requireMember(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static boolean optionalBoolean(JsonObject object, String name, boolean defaultValue) {
        JsonElement value = object.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String optionalString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static List<String> optionalStringList(JsonObject object, String name, List<String> defaultValue) {
        JsonElement value = object.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }

        List<String> strings = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " must contain only strings");
            }
            String key = element.getAsString();
            if (key.isBlank()) {
                throw new IllegalArgumentException(name + " cannot contain blank keys");
            }
            strings.add(key);
        }
        return strings;
    }

    private static int requireInteger(JsonObject object, String name) {
        JsonElement value = requireMember(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try {
            return new java.math.BigDecimal(value.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static void requireUuid(String value, String fieldName) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException(fieldName + " must be a canonical UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be a canonical UUID", exception);
        }
    }

    private record PersistedState(String activeProfileId, List<Profile> profiles,
                                  Set<String> migratedLegacyProfileIds, boolean legacyDuplicateRepairComplete) {
        private PersistedState {
            Objects.requireNonNull(activeProfileId, "activeProfileId");
            profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
            migratedLegacyProfileIds = Set.copyOf(
                    Objects.requireNonNull(migratedLegacyProfileIds, "migratedLegacyProfileIds"));
        }
    }

    private record Presence<T>(boolean present, T value) {
    }

    /** Immutable checkpoint data for restoring profile state after a failed operation. */
    public record State(List<Profile> profiles, String activeProfileId,
                        Set<String> migratedLegacyProfileIds, boolean legacyDuplicateRepairComplete) {
        public State(List<Profile> profiles, String activeProfileId) {
            this(profiles, activeProfileId, Set.of(), false);
        }

        public State {
            profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
            Objects.requireNonNull(activeProfileId, "activeProfileId");
            migratedLegacyProfileIds = Set.copyOf(Objects.requireNonNull(migratedLegacyProfileIds, "migratedLegacyProfileIds"));
        }
    }

    /** Opaque memory-and-baseline snapshot for rolling back a failed shared-settings reload. */
    public static final class ReloadCheckpoint {
        private final PersistedState currentState;
        private final PersistedState baseline;

        private ReloadCheckpoint(PersistedState currentState, PersistedState baseline) {
            this.currentState = currentState;
            this.baseline = baseline;
        }
    }
}
