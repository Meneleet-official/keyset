package dev.keyset;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.client.KeysetScreen;
import dev.keyset.compat.ExternalHotkeys;
import dev.keyset.compat.ModOrigins;
import dev.keyset.editor.BindingIndex;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.mixin.KeyMappingAccessor;
import dev.keyset.mixin.MinecraftServerAccessor;
import dev.keyset.profile.Binding;
import dev.keyset.profile.ClientPreferences;
import dev.keyset.profile.Profile;
import dev.keyset.profile.ProfileStore;
import dev.keyset.profile.ProfilePaths;
import dev.keyset.profile.SharedSettingsConflictException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared client implementation; no loader API or world/server state is required. */
public final class KeysetClient {
    private static final Logger LOG = LoggerFactory.getLogger("Keyset");
    private static final Map<String, Binding> chords = new HashMap<>();
    private static ProfileStore store;
    private static ClientPreferences preferences;
    private static Path settingsFile;
    private static boolean loaded;
    private static String error = "";
    private static KeyMapping open;
    private static BindingPlatform platform = new BindingPlatform() {};
    private static boolean assigning;
    private static UndoState undoState;
    private static String lastGameContext;
    private static KeyMapping nextProfile;
    private static KeyMapping.Category generalCategory;
    private static Map<String, Binding> liveBaseline = Map.of();
    private static boolean saving, refreshing;
    private static long revision, nextSharedPoll;
    private static LastAssignment lastAssignment;
    private static Map<String, Component> cachedActionNames = Map.of();
    private static long cachedActionRevision = Long.MIN_VALUE;
    private static String cachedActionLanguage = "";
    private static UndoState pendingUndo;
    private static boolean pendingUndoInvalidated;

    private KeysetClient() {}

    public static void setBindingPlatform(BindingPlatform value) { platform = value; }

    public static KeyMapping openMapping() {
        if (open == null) {
            open = new KeyMapping("keyset.open", InputConstants.KEY_GRAVE, generalCategory());
        }
        return open;
    }

    public static KeyMapping nextProfileMapping() {
        if (nextProfile == null) {
            nextProfile = new KeyMapping("keyset.next_profile", InputConstants.UNKNOWN.getValue(), generalCategory());
        }
        return nextProfile;
    }

    private static KeyMapping.Category generalCategory() {
        if (generalCategory == null) {
            generalCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("keyset", "general"));
        }
        return generalCategory;
    }

    public static List<KeyMapping> mappings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return List.of();
        List<KeyMapping> mappings = new ArrayList<>(Arrays.asList(mc.options.keyMappings));
        mappings.addAll(ExternalHotkeys.mappings());
        return mappings.stream().sorted(Comparator
                .comparing((KeyMapping k) -> categoryLabel(k.getCategory()).getString())
                .thenComparing(k -> name(k).getString())).toList();
    }

    public static Component name(KeyMapping mapping) {
        var external = ExternalHotkeys.get(mapping);
        return external == null ? Component.translatable(mapping.getName()) : external.name();
    }

    public static Component categoryLabel(KeyMapping.Category category) { return ExternalHotkeys.categoryLabel(category); }
    public static boolean multiKeyCapture(KeyMapping mapping) { return ExternalHotkeys.get(mapping) != null; }
    public static ModOrigins.Owner owner(KeyMapping mapping) {
        var external = ExternalHotkeys.get(mapping);
        return external == null ? ModOrigins.owner(mapping) : new ModOrigins.Owner(external.modId(), external.modName());
    }

    public static void ensureLoaded() {
        if (loaded || Minecraft.getInstance().options == null) return;
        ExternalHotkeys.initialize();
        loaded = true;
        Path file = profileFile();
        Path localFile = localProfileFile();
        store = new ProfileStore(file);
        Path preferencesFile = file.getParent().resolve("preferences.properties");
        preferences = new ClientPreferences(preferencesFile);
        try { preferences.load(); }
        catch (IOException | IllegalArgumentException preferenceFailure) {
            LOG.warn("Cannot load shared Keyset preferences", preferenceFailure);
            preferences = new ClientPreferences(preferencesFile);
            try {
                if (Files.isRegularFile(preferencesFile)) {
                    Files.copy(preferencesFile, preferencesFile.resolveSibling(
                            "preferences.broken-" + System.currentTimeMillis() + ".properties"));
                }
            } catch (IOException backupFailure) {
                preferenceFailure.addSuppressed(backupFailure);
            }
        }
        Map<String, Binding> initial = snapshot();
        try {
            store.load(initial, file.equals(localFile) ? null : localFile);
            migrateLegacyProfiles(file, localFile);
            apply(store.active());
        } catch (IOException | IllegalArgumentException ex) {
            LOG.error("Cannot load key profiles", ex);
            restoreLive(initial, ex);
            // Preserve the exact unreadable file; never overwrite it with defaults.
            try {
                if (Files.exists(file)) {
                    Path backup = file.resolveSibling("profiles.broken-" + System.currentTimeMillis() + ".json");
                    Files.copy(file, backup);
                }
            } catch (IOException recoveryFailure) {
                ex.addSuppressed(recoveryFailure);
            }
            // A valid file may have changed concurrently or a native provider may have failed.
            // Keep the original intact in all cases instead of treating every I/O error as corruption.
            Path recoveryFile = file.resolveSibling("profiles-recovery.json");
            store = new ProfileStore(recoveryFile);
            settingsFile = recoveryFile;
            try { store.load(snapshot()); apply(store.active()); }
            catch (IOException | RuntimeException fatal) {
                LOG.error("Profile recovery unavailable", fatal);
                restoreLive(initial, fatal);
                store.initializeTransient(initial);
            }
            error = "keyset.error.storage";
        }
        liveBaseline = snapshot();
        revision++;
    }

    public static ProfileStore profiles() { ensureLoaded(); return store; }

    private static void migrateLegacyProfiles(Path file, Path localFile) {
        ProfileStore.State checkpoint = store.checkpoint();
        ClientPreferences.State preferenceCheckpoint = preferences.checkpoint();
        boolean preferencesWritten = false;
        pendingUndo = new UndoState(checkpoint, snapshot(), preferenceCheckpoint);
        pendingUndoInvalidated = false;
        try {
            // Repair only persisted profiles, so route targets exist before either file is rewritten.
            Map<String, String> replacements = store.removeRedundantImportedCopies();
            boolean changed = !file.equals(localFile) && Files.isRegularFile(localFile)
                    && store.importLegacyFile(localFile);
            if (changed || !checkpoint.legacyDuplicateRepairComplete()) {
                createBackup("migration");
                if (!replacements.isEmpty()) {
                    preferences.remapProfileRoutes(replacements);
                    // Retained profile IDs exist in both the old and repaired store if saving is interrupted.
                    persistPreferences();
                    preferencesWritten = true;
                }
                store.save();
            }
        } catch (IOException | IllegalArgumentException migrationFailure) {
            store.restore(checkpoint);
            preferences.restore(pendingUndo == null ? preferenceCheckpoint : pendingUndo.preferences());
            if (preferencesWritten) {
                try { persistPreferences(); }
                catch (IOException rollbackFailure) { migrationFailure.addSuppressed(rollbackFailure); }
            }
            LOG.warn("Cannot migrate or repair legacy key profiles", migrationFailure);
        } finally {
            pendingUndo = null;
            pendingUndoInvalidated = false;
        }
    }
    public static String error() { return error; }
    public static long revision() { return revision; }
    public static double sidebarRatio() { ensureLoaded(); return preferences.sidebarRatio(); }

    public static void setSidebarRatio(double ratio) throws IOException {
        ensureLoaded();
        pendingUndo = null;
        pendingUndoInvalidated = false;
        ClientPreferences.State before = preferences.checkpoint();
        try {
            preferences.setSidebarRatio(ratio);
            persistPreferences();
            revision++;
        } catch (IOException failure) {
            preferences.restore(before);
            throw failure;
        }
    }

    public static Component operationError(Exception exception) {
        if (exception instanceof SharedSettingsConflictException) return Component.translatable("keyset.error.shared_conflict");
        return Component.translatable("keyset.error", Objects.toString(exception.getMessage(), ""));
    }

    public static Component actionName(String actionId) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (cachedActionRevision != revision || !Objects.equals(cachedActionLanguage, language)) {
            Map<String, Component> names = new HashMap<>();
            for (KeyMapping mapping : mappings()) names.put(mapping.getName(), name(mapping));
            cachedActionNames = Map.copyOf(names);
            cachedActionRevision = revision;
            cachedActionLanguage = language;
        }
        return cachedActionNames.getOrDefault(actionId, Component.literal(actionId));
    }

    public static Component displayBinding(Binding value) {
        if (value == null || value.keys().isEmpty()) return Component.translatable("keyset.compare_unbound");
        KeyboardLayouts.Layout layout = KeyboardLayouts.forLanguage(Minecraft.getInstance().getLanguageManager().getSelected());
        String prefix = (value.ctrl() ? "Ctrl + " : "") + (value.shift() ? "Shift + " : "")
                + (value.alt() ? "Alt + " : "") + (value.command() ? "Cmd + " : "");
        Component result = Component.literal(prefix);
        for (String key : value.keys()) {
            if (!key.equals(value.key())) result = result.copy().append(" + ");
            String physical = key.startsWith("key.keyboard.") ? key.substring("key.keyboard.".length()) : "";
            String fallback = InputConstants.getKey(key).getDisplayName().getString();
            result = result.copy().append(layout.label(physical, fallback));
        }
        return result;
    }

    private static Map<String, Binding> liveChanges(Map<String, Binding> current) {
        Map<String, Binding> changed = new LinkedHashMap<>();
        current.forEach((id, value) -> {
            if (!Objects.equals(value, liveBaseline.get(id))) changed.put(id, value);
        });
        return changed;
    }

    public static boolean refreshFromSharedSettings() throws IOException {
        ensureLoaded();
        if (saving || refreshing || !liveChanges(snapshot()).isEmpty()) return false;
        refreshing = true;
        Map<String, Binding> previousLive = snapshot();
        var profileCheckpoint = store.reloadCheckpoint();
        var preferenceCheckpoint = preferences.reloadCheckpoint();
        CurrentContext context = currentContext();
        String previousRoute = context == null ? null : preferences.profileFor(context.id());
        try {
            boolean profilesChanged = store.reloadIfChanged();
            boolean preferencesChanged = preferences.reloadIfChanged();
            if (profilesChanged) {
                apply(store.active());
                ExternalHotkeys.saveDirty();
                Minecraft.getInstance().options.save();
            }
            if (profilesChanged || preferencesChanged) {
                liveBaseline = snapshot();
                undoState = null;
                lastAssignment = null;
                revision++;
                error = "";
                if (context != null && !Objects.equals(previousRoute, preferences.profileFor(context.id()))) lastGameContext = null;
            }
            return profilesChanged || preferencesChanged;
        } catch (IOException | RuntimeException failure) {
            store.restoreReloadCheckpoint(profileCheckpoint);
            preferences.restoreReloadCheckpoint(preferenceCheckpoint);
            restoreLive(previousLive, failure);
            throw failure;
        } finally {
            refreshing = false;
        }
    }
    public static boolean canUndo() { return undoState != null; }
    public static boolean isFavorite(KeyMapping mapping) {
        ensureLoaded();
        return preferences != null && preferences.isFavorite(mapping.getName());
    }

    public static void toggleFavorite(KeyMapping mapping) throws IOException {
        try {
            ensureLoaded();
            UndoState before = captureUndo();
            preferences.toggleFavorite(mapping.getName());
            try {
                persistPreferences();
                rememberUndo(before);
                revision++;
            } catch (IOException failure) {
                rollbackOperation(before, failure);
                throw failure;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static CurrentContext currentContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            try {
                String levelId = ((MinecraftServerAccessor) mc.getSingleplayerServer()).keyset$getStorageSource().getLevelId();
                if (levelId != null && !levelId.isBlank()) return new CurrentContext("single:" + levelId, levelId);
            } catch (RuntimeException exception) {
                LOG.debug("Cannot read the current single-player world id", exception);
            }
        }
        var server = mc.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank()) return null;
        String address = server.ip.trim().toLowerCase(Locale.ROOT);
        return new CurrentContext("server:" + address, server.name == null || server.name.isBlank() ? address : server.name);
    }

    public static String routedProfile(String context) {
        ensureLoaded();
        return preferences == null ? null : preferences.profileFor(context);
    }

    public static RouteResult toggleAutoProfile(String profileId) throws IOException {
        try {
            ensureLoaded();
            CurrentContext context = currentContext();
            if (context == null) throw new IOException("Join a world or server to set an automatic profile");
            Profile target = store.profiles().stream().filter(profile -> profile.id().equals(profileId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown profile"));
            UndoState before = captureUndo();
            boolean enabled = preferences.toggleRoute(context.id(), profileId);
            try {
                persistPreferences();
                boolean switched = enabled && !store.active().id().equals(profileId);
                if (switched) switchProfileInternal(profileId);
                rememberUndo(before);
                return new RouteResult(enabled, switched, target.name(), context.label());
            } catch (IOException | RuntimeException failure) {
                rollbackOperation(before, failure);
                throw failure;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static Binding binding(KeyMapping mapping) {
        var external = ExternalHotkeys.get(mapping);
        if (external != null) return external.read();
        Binding chord = chords.get(mapping.getName());
        return chord != null ? chord : platform.read(mapping);
    }

    public static Binding chord(KeyMapping mapping) { return chords.get(mapping.getName()); }
    private static Binding defaults(KeyMapping mapping) {
        var external = ExternalHotkeys.get(mapping);
        return external == null ? platform.defaults(mapping) : external.defaults();
    }
    public static boolean isDefault(KeyMapping mapping) { return binding(mapping).equals(defaults(mapping)); }

    public static Component bindingLabel(Binding binding) {
        String prefix = (binding.ctrl() ? "Ctrl + " : "") + (binding.shift() ? "Shift + " : "")
                + (binding.alt() ? "Alt + " : "") + (binding.command() ? "Cmd + " : "");
        var label = Component.literal(prefix).append(InputConstants.getKey(binding.key()).getDisplayName());
        for (String key : binding.extraKeys()) label.append(Component.literal(" + ").append(InputConstants.getKey(key).getDisplayName()));
        return label;
    }

    public static boolean conflicts(KeyMapping mapping) {
        Binding value = binding(mapping);
        if (value.key().equals(InputConstants.UNKNOWN.getName())) return false;
        return mappings().stream().anyMatch(other -> other != mapping && same(mapping, other));
    }

    public static List<KeyMapping> conflictsFor(KeyMapping mapping, Binding value) {
        if (value == null || value.keys().isEmpty()) return List.of();
        return mappings().stream().filter(other -> other != mapping && bindingContextsConflict(mapping, other)
                && BindingIndex.sameInputs(value, binding(other))).toList();
    }

    public static Binding previousBindingForSwap(String actionId) {
        if (lastAssignment == null || !lastAssignment.profileId().equals(store.active().id())
                || !lastAssignment.actionId().equals(actionId) || lastAssignment.previous().keys().isEmpty()) return null;
        KeyMapping mapping = mappings().stream().filter(value -> value.getName().equals(actionId)).findFirst().orElse(null);
        return mapping != null && binding(mapping).equals(lastAssignment.assigned())
                && !BindingIndex.sameInputs(lastAssignment.previous(), lastAssignment.assigned())
                ? lastAssignment.previous() : null;
    }

    public static void resolveBindingConflict(String actionId, ConflictResolution resolution, String counterpartId) throws IOException {
        resolveBindingConflict(actionId, resolution, counterpartId, revision);
    }

    public static void resolveBindingConflict(String actionId, ConflictResolution resolution, String counterpartId,
                                              long expectedRevision) throws IOException {
        try {
            UndoState before = captureUndo();
            if (revision != expectedRevision) throw new SharedSettingsConflictException("Bindings changed while reviewing this conflict");
            KeyMapping mapping = mappings().stream().filter(value -> value.getName().equals(actionId)).findFirst()
                    .orElseThrow(() -> new IOException("Action is no longer available"));
            List<KeyMapping> peers = conflictsFor(mapping, binding(mapping));
            if (peers.isEmpty()) throw new IOException("The conflict has already changed");
            Binding previous = previousBindingForSwap(actionId);
            if (resolution == ConflictResolution.SWAP && (previous == null || peers.size() != 1
                    || !peers.getFirst().getName().equals(counterpartId))) throw new IOException("This binding can no longer be swapped");
            try {
                for (KeyMapping peer : peers) assign(peer, resolution == ConflictResolution.SWAP ? previous
                        : new Binding(InputConstants.UNKNOWN.getName(), false, false, false));
                KeyMapping.resetMapping();
                save();
                rememberUndo(before);
                lastAssignment = null;
            } catch (IOException failure) {
                rollbackOperation(before, failure);
                throw failure;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static void copyProfileBindings(String sourceProfileId, Set<String> actionIds) throws IOException {
        copyProfileBindings(sourceProfileId, actionIds, revision);
    }

    public static void copyProfileBindings(String sourceProfileId, Set<String> actionIds, long expectedRevision) throws IOException {
        try {
            UndoState before = captureUndo();
            if (revision != expectedRevision) throw new SharedSettingsConflictException("Profiles changed while reviewing differences");
            Profile source = store.profiles().stream().filter(profile -> profile.id().equals(sourceProfileId)).findFirst()
                    .orElseThrow(() -> new IOException("Source profile is no longer available"));
            if (source.id().equals(store.active().id())) throw new IOException("Choose another source profile");
            Map<String, Binding> selected = new LinkedHashMap<>();
            for (String id : actionIds) {
                Binding value = source.bindings().get(id);
                if (value != null) selected.put(id, value);
            }
            if (selected.isEmpty()) return;
            try {
                createBackup("copy-bindings");
                store.updateActive(selected);
                apply(store.active());
                save();
                rememberUndo(before);
                lastAssignment = null;
            } catch (IOException failure) {
                rollbackOperation(before, failure);
                throw failure;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static boolean same(KeyMapping first, KeyMapping second) {
        return BindingIndex.sameInputs(binding(first), binding(second)) && bindingContextsConflict(first, second);
    }

    public static boolean bindingContextsConflict(KeyMapping first, KeyMapping second) {
        if (ExternalHotkeys.get(first) != null || ExternalHotkeys.get(second) != null) return true;
        return platform.conflicts(first, second);
    }

    private static Map<String, Binding> snapshot() {
        Map<String, Binding> result = new LinkedHashMap<>();
        for (KeyMapping mapping : mappings()) result.put(mapping.getName(), binding(mapping));
        return result;
    }

    private static boolean modified(Binding binding) { return binding.ctrl() || binding.shift() || binding.alt() || binding.command(); }

    private static void assign(KeyMapping mapping, Binding binding) throws IOException {
        if (ExternalHotkeys.get(mapping) != null) {
            ExternalHotkeys.assign(mapping, binding);
            return;
        }
        if (!binding.extraKeys().isEmpty()) throw new IOException("Multiple primary keys require a native hotkey provider");
        InputConstants.Key key = InputConstants.getKey(binding.key());
        assigning = true;
        try {
            if (modified(binding) && !key.equals(InputConstants.UNKNOWN)) {
                chords.put(mapping.getName(), binding);
                platform.assign(mapping, InputConstants.UNKNOWN);
            } else {
                chords.remove(mapping.getName());
                platform.assign(mapping, key);
            }
        } finally {
            assigning = false;
        }
        mapping.setDown(false);
        ((KeyMappingAccessor) mapping).keyset$setClickCount(0);
    }

    private static void apply(Profile profile) throws IOException {
        // Validate every key before touching live mappings.
        try { profile.bindings().values().forEach(b -> b.keys().forEach(InputConstants::getKey)); }
        catch (RuntimeException invalidKey) { throw new IOException("Invalid key in profile", invalidKey); }
        KeyMapping.releaseAll();
        for (KeyMapping mapping : mappings()) {
            // A newly installed integration has no entry in older profiles. Preserve its own config.
            Binding value = profile.bindings().getOrDefault(mapping.getName(), binding(mapping));
            assign(mapping, value);
        }
        KeyMapping.resetMapping();
    }

    public static void save() throws IOException {
        ensureLoaded();
        if (saving || refreshing) return;
        boolean ownOperation = pendingUndo == null;
        UndoState ownCheckpoint = ownOperation
                ? new UndoState(store.checkpoint(), Map.copyOf(liveBaseline), preferences.checkpoint()) : null;
        if (ownOperation) {
            pendingUndo = ownCheckpoint;
            pendingUndoInvalidated = false;
        }
        saving = true;
        var before = store.checkpoint();
        try {
            Map<String, Binding> currentBindings = snapshot();
            Map<String, Binding> changes = liveChanges(currentBindings);
            currentBindings.forEach((id, value) -> {
                if (!store.active().bindings().containsKey(id)) changes.put(id, value);
            });
            store.updateActive(changes);
            if (ownOperation && !changes.isEmpty()) { undoState = null; lastAssignment = null; }
            ExternalHotkeys.saveDirty();
            persistProfiles();
            if (!before.equals(store.checkpoint())) revision++;
        } catch (IOException | RuntimeException failure) {
            if (ownOperation) rollbackOperation(ownCheckpoint, failure);
            throw failure;
        } finally {
            saving = false;
            if (ownOperation) { pendingUndo = null; pendingUndoInvalidated = false; }
        }
    }

    private static void persistProfiles() throws IOException {
        ProfileStore.State attempted = store.checkpoint();
        store.save();
        if (!attempted.equals(store.checkpoint())) { undoState = null; lastAssignment = null; }
        if (pendingUndo != null) {
            try {
                ProfileStore.State rebased = store.rebaseCheckpoint(pendingUndo.profiles(), attempted);
                Profile active = rebased.profiles().stream().filter(p -> p.id().equals(rebased.activeProfileId())).findFirst().orElseThrow();
                Map<String, Binding> previousLive = new LinkedHashMap<>(pendingUndo.liveBindings());
                previousLive.replaceAll((id, value) -> active.bindings().getOrDefault(id, value));
                pendingUndo = new UndoState(rebased, Map.copyOf(previousLive), pendingUndo.preferences());
            } catch (IOException conflict) {
                pendingUndo = null;
                pendingUndoInvalidated = true;
            }
        }
        Map<String, Binding> current = snapshot();
        if (current.entrySet().stream().anyMatch(entry -> store.active().bindings().containsKey(entry.getKey())
                && !Objects.equals(entry.getValue(), store.active().bindings().get(entry.getKey())))) {
            apply(store.active());
            ExternalHotkeys.saveDirty();
        }
        liveBaseline = snapshot();
        boolean wasSaving = saving;
        saving = true;
        try { Minecraft.getInstance().options.save(); }
        finally { saving = wasSaving; }
        error = "";
    }

    private static void persistPreferences() throws IOException {
        ClientPreferences.State attempted = preferences.checkpoint();
        CurrentContext context = currentContext();
        String previousRoute = context == null ? null : preferences.profileFor(context.id());
        preferences.save();
        if (!attempted.equals(preferences.checkpoint())) { undoState = null; lastAssignment = null; }
        if (context != null && !Objects.equals(previousRoute, preferences.profileFor(context.id()))) lastGameContext = null;
        if (pendingUndo != null) {
            try {
                pendingUndo = new UndoState(pendingUndo.profiles(), pendingUndo.liveBindings(),
                        preferences.rebaseCheckpoint(pendingUndo.preferences(), attempted));
            } catch (IOException conflict) {
                pendingUndo = null;
                pendingUndoInvalidated = true;
            }
        }
        error = "";
    }

    private static void rememberUndo(UndoState fallback) {
        undoState = pendingUndoInvalidated ? null : pendingUndo == null ? fallback : pendingUndo;
        pendingUndo = null;
        pendingUndoInvalidated = false;
    }

    private static void clearPendingOperation() {
        pendingUndo = null;
        pendingUndoInvalidated = false;
    }

    private static void rollbackOperation(UndoState fallback, Exception failure) {
        UndoState rollback = pendingUndoInvalidated ? null : pendingUndo == null ? fallback : pendingUndo;
        pendingUndo = null;
        pendingUndoInvalidated = false;
        undoState = null;
        if (rollback != null) {
            store.restore(rollback.profiles());
            preferences.restore(rollback.preferences());
            restoreLive(rollback.liveBindings(), failure);
            liveBaseline = snapshot();
            try { persistPreferences(); }
            catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            try { persistProfiles(); }
            catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
        }
        try { refreshFromSharedSettings(); }
        catch (IOException | RuntimeException refreshFailure) { failure.addSuppressed(refreshFailure); }
        revision++;
    }

    /** Folder for portable profile files that can be copied to another game installation. */
    public static Path transferDirectory() {
        return profileFile().getParent().resolve("transfer");
    }

    private static Path profileFile() {
        if (settingsFile != null) return settingsFile;
        Path local = localProfileFile();
        try {
            Map<String, String> properties = Map.of(
                    "keyset.configDir", System.getProperty("keyset.configDir", ""),
                    "user.home", System.getProperty("user.home", "."),
                    "os.name", System.getProperty("os.name", ""));
            Path shared = ProfilePaths.sharedDirectory(System.getenv(), properties).resolve("profiles.json");
            settingsFile = shared;
        } catch (RuntimeException exception) {
            LOG.warn("Cannot resolve shared Keyset settings; using the current Minecraft folder", exception);
            settingsFile = local;
        }
        return settingsFile;
    }

    private static Path localProfileFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/keyset/profiles.json")
                .toAbsolutePath().normalize();
    }

    public static int transferFileCount() throws IOException {
        Path directory = transferDirectory();
        if (!Files.isDirectory(directory)) return 0;
        try (var files = Files.list(directory)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .count();
        }
    }

    /** Saves one portable JSON file containing the active profile and its native settings. */
    public static void exportActiveProfile() throws IOException {
        ensureLoaded();
        save();
        Profile active = store.active();
        String slug = active.name().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-").replaceAll("^[.-]+|[.-]+$", "");
        if (slug.isBlank()) slug = "profile";
        if (slug.length() > 32) slug = slug.substring(0, 32);
        Path destination = transferDirectory().resolve("keyset-" + slug + "-"
                + active.id().replace("-", "").substring(0, 8) + ".json");
        store.exportProfile(active.id(), destination);
    }

    /** Merges portable profiles into this installation while leaving its active profile selected. */
    public static int importTransferredProfiles() throws IOException {
        try {
            ensureLoaded();
            UndoState before = captureUndo();
            createBackup("import");
            try {
                int imported = store.importDirectory(transferDirectory());
                if (imported > 0) { persistProfiles(); rememberUndo(before); revision++; }
                return imported;
            } catch (IOException | IllegalArgumentException exception) {
                rollbackOperation(before, exception);
                throw exception;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static void bind(KeyMapping mapping, Binding value) throws IOException {
        try {
            ensureLoaded();
            UndoState before = captureUndo();
            var checkpoint = store.checkpoint();
            Binding previous = binding(mapping);
            try {
                assign(mapping, value);
                KeyMapping.resetMapping();
                save();
                rememberUndo(before);
                lastAssignment = new LastAssignment(store.active().id(), mapping.getName(), previous, binding(mapping));
            }
            catch (IOException ex) {
                rollbackOperation(before, ex);
                throw ex;
            }
        } finally {
            clearPendingOperation();
        }
    }

    private static void restoreLive(Map<String, Binding> previous, Exception failure) {
        for (KeyMapping mapping : mappings()) {
            Binding binding = previous.get(mapping.getName());
            if (binding != null) {
                try { assign(mapping, binding); }
                catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
        }
        KeyMapping.resetMapping();
        try { ExternalHotkeys.saveDirty(); }
        catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }

    public static void reset(KeyMapping mapping) throws IOException {
        bind(mapping, defaults(mapping));
    }

    public static void resetAll() throws IOException {
        try {
            ensureLoaded();
            UndoState before = captureUndo();
            createBackup("reset-all");
            var checkpoint = store.checkpoint();
            Map<String, Binding> previous = snapshot();
            try {
                for (KeyMapping mapping : mappings()) assign(mapping, defaults(mapping));
                KeyMapping.resetMapping();
                save();
                rememberUndo(before);
            }
            catch (IOException ex) {
                rollbackOperation(before, ex);
                throw ex;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static void createProfile(String name) throws IOException {
        try {
            UndoState before = captureUndo();
            var checkpoint = store.checkpoint();
            try {
                store.create(name, store.active().bindings());
                persistProfiles();
                rememberUndo(before);
                revision++;
                lastAssignment = null;
            } catch (IOException ex) { rollbackOperation(before, ex); throw ex; }
        } finally {
            clearPendingOperation();
        }
    }

    public static void switchProfile(String id) throws IOException {
        try {
            UndoState before = captureUndo();
            try {
                switchProfileInternal(id);
                rememberUndo(before);
            } catch (IOException | RuntimeException failure) {
                rollbackOperation(before, failure);
                throw failure;
            }
        } finally {
            clearPendingOperation();
        }
    }

    private static void switchProfileInternal(String id) throws IOException {
        save();
        var checkpoint = store.checkpoint();
        Profile previous = store.active();
        Profile next = store.profiles().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown profile"));
        try {
            apply(next);
            store.select(id);
            ExternalHotkeys.saveDirty();
            persistProfiles();
            Minecraft.getInstance().options.save();
            liveBaseline = snapshot();
            lastAssignment = null;
            revision++;
        } catch (IOException ex) {
            throw ex;
        }
    }

    public static void renameProfile(String id, String name) throws IOException {
        try {
            ensureLoaded();
            UndoState before = captureUndo();
            var checkpoint = store.checkpoint();
            try { store.rename(id, name); persistProfiles(); rememberUndo(before); revision++; }
            catch (IOException ex) { rollbackOperation(before, ex); throw ex; }
        } finally {
            clearPendingOperation();
        }
    }

    public static void deleteProfile(String id) throws IOException {
        try {
            UndoState before = captureUndo();
            createBackup("delete");
            var checkpoint = store.checkpoint();
            Profile previous = store.active();
            try {
                store.delete(id);
                preferences.removeProfileRoutes(id);
                apply(store.active());
                persistPreferences();
                ExternalHotkeys.saveDirty();
                persistProfiles();
                Minecraft.getInstance().options.save();
                rememberUndo(before);
                liveBaseline = snapshot();
                lastAssignment = null;
                revision++;
            } catch (IOException ex) {
                rollbackOperation(before, ex);
                throw ex;
            } catch (RuntimeException ex) {
                IOException wrapped = new IOException("Cannot delete profile", ex);
                rollbackOperation(before, wrapped);
                throw wrapped;
            }
        } finally {
            clearPendingOperation();
        }
    }

    public static void undo() throws IOException {
        try {
            ensureLoaded();
            refreshFromSharedSettings();
            if (undoState == null) return;
            UndoState target = undoState;
            createBackup("undo");
            ProfileStore.State priorStore = store.checkpoint();
            ClientPreferences.State priorPreferences = preferences.checkpoint();
            Map<String, Binding> priorBindings = snapshot();
            UndoState before = new UndoState(priorStore, priorBindings, priorPreferences);
            pendingUndo = before;
            pendingUndoInvalidated = false;
            try {
                store.restore(target.profiles());
                preferences.restore(target.preferences());
                restoreLiveOrThrow(target.liveBindings());
                ExternalHotkeys.saveDirty();
                persistPreferences();
                persistProfiles();
                Minecraft.getInstance().options.save();
                undoState = null;
                pendingUndo = null;
                pendingUndoInvalidated = false;
                liveBaseline = snapshot();
                lastAssignment = null;
                revision++;
            } catch (IOException | RuntimeException failure) {
                IOException exception = failure instanceof IOException io ? io : new IOException("Cannot undo change", failure);
                rollbackOperation(before, exception);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw exception;
            }
        } finally {
            clearPendingOperation();
        }
    }

    private static UndoState captureUndo() throws IOException {
        pendingUndo = null;
        pendingUndoInvalidated = false;
        refreshFromSharedSettings();
        save();
        pendingUndo = new UndoState(store.checkpoint(), snapshot(), preferences.checkpoint());
        return pendingUndo;
    }

    private static void restoreLiveOrThrow(Map<String, Binding> bindings) throws IOException {
        KeyMapping.releaseAll();
        for (KeyMapping mapping : mappings()) {
            Binding value = bindings.get(mapping.getName());
            if (value != null) assign(mapping, value);
        }
        KeyMapping.resetMapping();
    }

    private static void createBackup(String reason) throws IOException {
        Path source = profileFile();
        if (!Files.isRegularFile(source)) return;
        Path directory = source.getParent().resolve("backups");
        Files.createDirectories(directory);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path backup = directory.resolve("profiles-" + timestamp + "-" + reason + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".json");
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "profiles-*.json")) {
            List<Path> sorted = new ArrayList<>();
            files.forEach(sorted::add);
            sorted.sort(Comparator.comparingLong(KeysetClient::lastModified).reversed());
            for (int i = 20; i < sorted.size(); i++) Files.deleteIfExists(sorted.get(i));
        }
    }

    private static long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0; }
    }

    public static void tick() {
        ensureLoaded();
        if (!loaded) return;
        long now = System.nanoTime();
        if (now >= nextSharedPoll && !(Minecraft.getInstance().gui.screen() instanceof KeysetScreen screen && screen.isCapturingBindings())) {
            nextSharedPoll = now + 1_000_000_000L;
            try { refreshFromSharedSettings(); }
            catch (IOException | RuntimeException failure) {
                error = failure instanceof SharedSettingsConflictException ? "keyset.error.shared_conflict" : "keyset.error.shared_sync";
                LOG.debug("Cannot synchronize shared key profiles", failure);
            }
        }
        // Releasing a modifier before the primary key must not leave a chord held.
        for (var entry : chords.entrySet()) {
            KeyMapping mapping = KeyMapping.get(entry.getKey());
            if (mapping == null) continue;
            Binding chord = entry.getValue();
            if (chord != null && (Minecraft.getInstance().gui.screen() != null || !modifiersMatch(chord) || !platform.active(mapping))) {
                mapping.setDown(false);
            }
        }
        while (openMapping().consumeClick()) {
            if (Minecraft.getInstance().gui.screen() == null) Minecraft.getInstance().setScreenAndShow(new KeysetScreen(null));
        }
        while (nextProfileMapping().consumeClick()) {
            if (Minecraft.getInstance().gui.screen() == null) cycleProfile();
        }
        updateContextRoute();
    }

    private static void cycleProfile() {
        List<Profile> profiles = store.profiles();
        if (profiles.size() < 2) return;
        int active = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id().equals(store.active().id())) { active = i; break; }
        }
        Profile next = profiles.get((active + 1) % profiles.size());
        try {
            switchProfile(next.id());
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(
                        Component.translatable("keyset.profile_switched", next.name()));
            }
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Cannot switch to the next profile", exception);
        }
    }

    private static void updateContextRoute() {
        CurrentContext context = currentContext();
        String contextId = context == null ? null : context.id();
        if (Objects.equals(lastGameContext, contextId)) return;
        lastGameContext = contextId;
        if (context == null || preferences == null) return;
        String profileId = preferences.profileFor(context.id());
        if (profileId == null || store.active().id().equals(profileId)) return;
        Profile profile = store.profiles().stream().filter(value -> value.id().equals(profileId)).findFirst().orElse(null);
        if (profile == null) return;
        try {
            switchProfile(profileId);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(
                        Component.translatable("keyset.profile_auto_applied", profile.name()));
            }
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Cannot apply the automatic profile for {}", context.label(), exception);
        }
    }

    private static boolean modifiersMatch(Binding b) {
        return modifiersMatch(b, currentModifiers());
    }

    private static int currentModifiers() {
        boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
        boolean command = InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI);
        return (ctrl ? InputConstants.MOD_CONTROL : 0) | (shift ? InputConstants.MOD_SHIFT : 0)
                | (alt ? InputConstants.MOD_ALT : 0) | (command ? InputConstants.MOD_SUPER : 0);
    }

    private static boolean modifiersMatch(Binding b, int modifiers) {
        return b.ctrl() == ((modifiers & InputConstants.MOD_CONTROL) != 0)
                && b.shift() == ((modifiers & InputConstants.MOD_SHIFT) != 0)
                && b.alt() == ((modifiers & InputConstants.MOD_ALT) != 0)
                && b.command() == ((modifiers & InputConstants.MOD_SUPER) != 0);
    }

    /** Instance matching is also used by inventory, hotbar and other GUI shortcuts. */
    public static Boolean matches(KeyMapping mapping, InputConstants.Key key, int modifiers) {
        if (!loaded) return null;
        Binding chord = chords.get(mapping.getName());
        if (chord != null) return chord.key().equals(key.getName()) && modifiersMatch(chord, modifiers) && platform.active(mapping);
        if (mapping.saveString().equals(key.getName()) && chords.entrySet().stream()
                .anyMatch(e -> e.getValue().key().equals(key.getName()) && modifiersMatch(e.getValue(), modifiers)
                        && KeyMapping.get(e.getKey()) != null && platform.active(KeyMapping.get(e.getKey())))) return false;
        return null;
    }

    public static Boolean matches(KeyMapping mapping, InputConstants.Key key) {
        return matches(mapping, key, currentModifiers());
    }

    /** Return true only when an active chord consumes this primary input. */
    public static boolean route(InputConstants.Key key, boolean down, boolean click) {
        if (!loaded || chords.isEmpty()) return false;
        List<KeyMapping> matches = new ArrayList<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            Binding chord = chords.get(mapping.getName());
            if (chord == null || !chord.key().equals(key.getName())) continue;
            if (!down && !click) mapping.setDown(false);
            if (modifiersMatch(chord) && platform.active(mapping)) matches.add(mapping);
        }
        // Always allow vanilla to release a plain binding, including when the
        // modifier was pressed after its primary key was already held.
        if (!down && !click) return false;
        if (matches.isEmpty() || Minecraft.getInstance().gui.screen() != null) return false;
        for (KeyMapping mapping : matches) {
            if (click) {
                var access = (KeyMappingAccessor) mapping;
                access.keyset$setClickCount(access.keyset$getClickCount() + 1);
            } else mapping.setDown(down);
        }
        // Suppress the plain primary binding when a modified binding is active.
        if (down && !click) for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (!chords.containsKey(mapping.getName()) && mapping.saveString().equals(key.getName())) mapping.setDown(false);
        }
        return true;
    }

    public static void externalKeyChanged(KeyMapping mapping) {
        if (!assigning) chords.remove(mapping.getName());
    }

    public static void optionsSaved() {
        if (!loaded || assigning || saving || refreshing || store == null) return;
        try {
            Map<String, Binding> current = snapshot();
            if (store.active().bindings().entrySet().containsAll(current.entrySet())) return;
            save();
        } catch (IOException ex) {
            error = ex instanceof SharedSettingsConflictException ? "keyset.error.shared_conflict" : "keyset.error.storage";
            revision++;
            LOG.error("Cannot save key profiles", ex);
        }
    }

    public record CurrentContext(String id, String label) {}
    public enum ConflictResolution { REPLACE, SWAP }
    private record LastAssignment(String profileId, String actionId, Binding previous, Binding assigned) {}
    public record RouteResult(boolean enabled, boolean switched, String profileName, String contextLabel) {}
    private record UndoState(ProfileStore.State profiles, Map<String, Binding> liveBindings,
                             ClientPreferences.State preferences) {}
}
