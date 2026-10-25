package dev.keyset.profile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Shared client-only pins and world/server-to-profile rules. */
public final class ClientPreferences {
    private static final double DEFAULT_SIDEBAR_RATIO = 0.52;
    private static final String SIDEBAR_RATIO_KEY = "ui.sidebarRatio";
    private static final String KEYBOARD_SCALE_KEY = "ui.keyboardScale";
    private static final String ROW_HEIGHT_KEY = "ui.rowHeight";
    private static final String INPUT_ON_RIGHT_KEY = "ui.inputOnRight";
    private static final String ACCENT_COLOR_KEY = "ui.accentColor";
    private static final String PANEL_COLOR_KEY = "ui.panelColor";
    private static final String PANEL_OPACITY_KEY = "ui.panelOpacity";
    private static final String HIDDEN_CATEGORY_PREFIX = "ui.hiddenCategory.";
    private static final String HIDDEN_MOD_PREFIX = "ui.hiddenMod.";

    private final Path file;
    private final Set<String> favorites = new TreeSet<>();
    private final Map<String, String> routes = new TreeMap<>();
    private final Set<String> hiddenCategories = new TreeSet<>();
    private final Set<String> hiddenMods = new TreeSet<>();
    private double sidebarRatio = DEFAULT_SIDEBAR_RATIO;
    private UiAppearance appearance = UiAppearance.defaults();
    private State baseline = new State(Set.of(), Map.of(), DEFAULT_SIDEBAR_RATIO);

    public ClientPreferences(Path file) { this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize(); }

    public void load() throws IOException {
        State state = SharedSettingsFiles.withFileLock(file, () -> readState(file));
        apply(state);
        baseline = state;
    }

    /** Reloads newer shared preferences when this instance has no unsaved changes. */
    public boolean reloadIfChanged() throws IOException {
        return SharedSettingsFiles.withFileLock(file, () -> {
            State disk = readState(file);
            if (disk.equals(baseline)) return false;
            if (!checkpoint().equals(baseline)) return false;
            apply(disk);
            baseline = disk;
            return true;
        });
    }

    private static State readState(Path file) throws IOException {
        if (!Files.exists(file)) return defaultState();
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        Set<String> favorites = new TreeSet<>();
        Map<String, String> routes = new TreeMap<>();
        Set<String> hiddenCategories = new TreeSet<>();
        Set<String> hiddenMods = new TreeSet<>();
        double sidebarRatio = DEFAULT_SIDEBAR_RATIO;
        UiAppearance defaults = UiAppearance.defaults();
        int keyboardScale = parseIntOrDefault(values, KEYBOARD_SCALE_KEY, defaults.keyboardScale());
        int rowHeight = parseIntOrDefault(values, ROW_HEIGHT_KEY, defaults.rowHeight());
        boolean inputOnRight = parseBooleanOrDefault(values, INPUT_ON_RIGHT_KEY, defaults.inputOnRight());
        int accentColor = parseIntOrDefault(values, ACCENT_COLOR_KEY, defaults.accentColor());
        int panelColor = parseIntOrDefault(values, PANEL_COLOR_KEY, defaults.panelColor());
        int panelOpacity = parseIntOrDefault(values, PANEL_OPACITY_KEY, defaults.panelOpacity());
        for (String key : values.stringPropertyNames()) {
            String value = values.getProperty(key);
            if (key.startsWith("favorite.")) {
                try {
                    favorites.add(new String(Base64.getUrlDecoder().decode(key.substring(9)),
                            java.nio.charset.StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ignored) { }
            } else if (key.startsWith("route.")) {
                String contextHash = key.substring(6);
                if (isValidRouteKey(contextHash) && isCanonicalUuid(value)) routes.put(contextHash, value);
            } else if (SIDEBAR_RATIO_KEY.equals(key)) {
                try {
                    sidebarRatio = clampSidebarRatio(Double.parseDouble(value));
                } catch (NumberFormatException invalidRatio) {
                    throw new IOException("Invalid sidebar ratio in preferences file: " + file, invalidRatio);
                }
            } else if (key.startsWith(HIDDEN_CATEGORY_PREFIX)) {
                if ("true".equalsIgnoreCase(value)) decodeHiddenKey(key.substring(HIDDEN_CATEGORY_PREFIX.length()), hiddenCategories);
            } else if (key.startsWith(HIDDEN_MOD_PREFIX)) {
                if ("true".equalsIgnoreCase(value)) decodeHiddenKey(key.substring(HIDDEN_MOD_PREFIX.length()), hiddenMods);
            }
        }
        UiAppearance appearance = new UiAppearance(keyboardScale, rowHeight, inputOnRight,
                accentColor, panelColor, panelOpacity);
        return new State(favorites, routes, sidebarRatio, appearance, hiddenCategories, hiddenMods);
    }

    private static State defaultState() {
        return new State(Set.of(), Map.of(), DEFAULT_SIDEBAR_RATIO);
    }

    private static int parseIntOrDefault(Properties values, String key, int fallback) {
        String value = values.getProperty(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalidValue) { return fallback; }
    }

    private static boolean parseBooleanOrDefault(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static void decodeHiddenKey(String encoded, Set<String> destination) {
        try {
            String decoded = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(encoded))).toString();
            destination.add(decoded);
        } catch (IllegalArgumentException | CharacterCodingException ignored) { }
    }

    public boolean isFavorite(String actionId) { return favorites.contains(actionId); }
    public boolean toggleFavorite(String actionId) {
        if (favorites.add(actionId)) return true;
        favorites.remove(actionId);
        return false;
    }
    public String profileFor(String context) { return routes.get(contextHash(context)); }
    public String profileFor(String context, String scope) { return routes.get(routeKey(context, scope)); }
    public double sidebarRatio() { return sidebarRatio; }
    public void setSidebarRatio(double value) { sidebarRatio = clampSidebarRatio(value); }
    public UiAppearance appearance() { return appearance; }
    public void setAppearance(UiAppearance value) { appearance = Objects.requireNonNull(value, "appearance"); }
    public Set<String> hiddenCategories() { return Set.copyOf(hiddenCategories); }
    public Set<String> hiddenMods() { return Set.copyOf(hiddenMods); }
    public boolean isCategoryHidden(String categoryId) { return hiddenCategories.contains(categoryId); }
    public boolean isModHidden(String modId) { return hiddenMods.contains(modId); }
    public void setCategoryHidden(String categoryId, boolean hidden) {
        setHidden(hiddenCategories, categoryId, hidden);
    }
    public void setModHidden(String modId, boolean hidden) { setHidden(hiddenMods, modId, hidden); }
    public void clearHiddenSections() {
        hiddenCategories.clear();
        hiddenMods.clear();
    }

    private static void setHidden(Set<String> hiddenItems, String id, boolean hidden) {
        Objects.requireNonNull(id, "id");
        if (hidden) hiddenItems.add(id);
        else hiddenItems.remove(id);
    }

    public boolean toggleRoute(String context, String profileId) {
        return toggleRouteKey(contextHash(context), profileId);
    }

    /** Stores a profile route in a namespace that is private to one Minecraft installation. */
    public boolean toggleRoute(String context, String profileId, String scope) {
        return toggleRouteKey(routeKey(context, scope), profileId);
    }

    private boolean toggleRouteKey(String hash, String profileId) {
        if (profileId.equals(routes.get(hash))) {
            routes.remove(hash);
            return false;
        }
        routes.put(hash, profileId);
        return true;
    }

    public void removeProfileRoutes(String profileId) {
        routes.entrySet().removeIf(entry -> entry.getKey().matches("[0-9a-f]{64}")
                && entry.getValue().equals(profileId));
    }

    public void removeProfileRoutes(String profileId, String scope) {
        String prefix = routeScopePrefix(scope);
        routes.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix)
                && entry.getValue().equals(profileId));
    }

    public void remapProfileRoutes(Map<String, String> replacements) {
        remapProfileRoutes(replacements, null);
    }

    public void remapProfileRoutes(Map<String, String> replacements, String scope) {
        String prefix = scope == null ? null : routeScopePrefix(scope);
        routes.replaceAll((context, profileId) -> {
            boolean selectedScope = prefix == null ? context.matches("[0-9a-f]{64}") : context.startsWith(prefix);
            return selectedScope ? replacements.getOrDefault(profileId, profileId) : profileId;
        });
    }

    public State checkpoint() {
        return new State(favorites, routes, sidebarRatio, appearance, hiddenCategories, hiddenMods);
    }

    /** Captures memory and the disk baseline so a failed external reload can be rolled back. */
    public ReloadCheckpoint reloadCheckpoint() {
        return new ReloadCheckpoint(checkpoint(), baseline);
    }

    /** Restores a reload checkpoint, including its prior disk baseline. */
    public void restoreReloadCheckpoint(ReloadCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        restore(checkpoint.currentState);
        baseline = checkpoint.baseline;
    }

    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        favorites.clear();
        favorites.addAll(state.favorites());
        routes.clear();
        routes.putAll(state.routes());
        sidebarRatio = state.sidebarRatio();
        appearance = state.appearance();
        hiddenCategories.clear();
        hiddenCategories.addAll(state.hiddenCategories());
        hiddenMods.clear();
        hiddenMods.addAll(state.hiddenMods());
    }

    /** Rebase an undo checkpoint onto current preferences after a save, preserving merged remote edits. */
    public State rebaseCheckpoint(State before, State attempted) throws IOException {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(attempted, "attempted");
        return merge(attempted, before, checkpoint());
    }

    public void save() throws IOException {
        SharedSettingsFiles.withFileLock(file, () -> {
            State merged = merge(baseline, checkpoint(), readState(file));
            SharedSettingsFiles.writeBytesAtomically(file, () -> serialize(merged));
            apply(merged);
            baseline = merged;
            return null;
        });
    }

    private static State merge(State baseline, State local, State remote) throws SharedSettingsConflictException {
        Set<String> allFavorites = new LinkedHashSet<>();
        allFavorites.addAll(baseline.favorites());
        allFavorites.addAll(local.favorites());
        allFavorites.addAll(remote.favorites());
        Set<String> mergedFavorites = new TreeSet<>();
        for (String favorite : allFavorites) {
            boolean selected = mergeValue(baseline.favorites().contains(favorite),
                    local.favorites().contains(favorite), remote.favorites().contains(favorite),
                    "favorite '" + favorite + "'");
            if (selected) mergedFavorites.add(favorite);
        }

        Set<String> allRoutes = new LinkedHashSet<>();
        allRoutes.addAll(baseline.routes().keySet());
        allRoutes.addAll(local.routes().keySet());
        allRoutes.addAll(remote.routes().keySet());
        Map<String, String> mergedRoutes = new TreeMap<>();
        for (String context : allRoutes) {
            Optional<String> selected = mergeValue(Optional.ofNullable(baseline.routes().get(context)),
                    Optional.ofNullable(local.routes().get(context)), Optional.ofNullable(remote.routes().get(context)),
                    "profile route for context " + context);
            selected.ifPresent(profileId -> mergedRoutes.put(context, profileId));
        }

        double ratio = mergeValue(baseline.sidebarRatio(), local.sidebarRatio(), remote.sidebarRatio(),
                "sidebar width");
        UiAppearance baseAppearance = baseline.appearance();
        UiAppearance localAppearance = local.appearance();
        UiAppearance remoteAppearance = remote.appearance();
        UiAppearance mergedAppearance = new UiAppearance(
                mergeValue(baseAppearance.keyboardScale(), localAppearance.keyboardScale(),
                        remoteAppearance.keyboardScale(), "keyboard scale"),
                mergeValue(baseAppearance.rowHeight(), localAppearance.rowHeight(),
                        remoteAppearance.rowHeight(), "keyboard row height"),
                mergeValue(baseAppearance.inputOnRight(), localAppearance.inputOnRight(),
                        remoteAppearance.inputOnRight(), "input position"),
                mergeValue(baseAppearance.accentColor(), localAppearance.accentColor(),
                        remoteAppearance.accentColor(), "accent color"),
                mergeValue(baseAppearance.panelColor(), localAppearance.panelColor(),
                        remoteAppearance.panelColor(), "panel color"),
                mergeValue(baseAppearance.panelOpacity(), localAppearance.panelOpacity(),
                        remoteAppearance.panelOpacity(), "panel opacity"));
        Set<String> mergedHiddenCategories = mergeSetMembership(baseline.hiddenCategories(),
                local.hiddenCategories(), remote.hiddenCategories(), "hidden category");
        Set<String> mergedHiddenMods = mergeSetMembership(baseline.hiddenMods(),
                local.hiddenMods(), remote.hiddenMods(), "hidden mod");
        return new State(mergedFavorites, mergedRoutes, ratio, mergedAppearance,
                mergedHiddenCategories, mergedHiddenMods);
    }

    private static Set<String> mergeSetMembership(Set<String> baseline, Set<String> local, Set<String> remote,
                                                   String description) throws SharedSettingsConflictException {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(baseline);
        all.addAll(local);
        all.addAll(remote);
        Set<String> merged = new TreeSet<>();
        for (String item : all) {
            if (mergeValue(baseline.contains(item), local.contains(item), remote.contains(item),
                    description + " '" + item + "'")) merged.add(item);
        }
        return merged;
    }

    private static <T> T mergeValue(T baseline, T local, T remote, String field)
            throws SharedSettingsConflictException {
        if (Objects.equals(local, baseline)) return remote;
        if (Objects.equals(remote, baseline)) return local;
        if (Objects.equals(local, remote)) return local;
        throw new SharedSettingsConflictException("Shared preferences conflict: " + field
                + " changed differently in two clients");
    }

    private static byte[] serialize(State state) throws IOException {
        Properties values = new Properties();
        for (String favorite : state.favorites()) {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(favorite.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            values.setProperty("favorite." + encoded, "true");
        }
        state.routes().forEach((hash, profileId) -> values.setProperty("route." + hash, profileId));
        values.setProperty(SIDEBAR_RATIO_KEY, Double.toString(state.sidebarRatio()));
        UiAppearance appearance = state.appearance();
        values.setProperty(KEYBOARD_SCALE_KEY, Integer.toString(appearance.keyboardScale()));
        values.setProperty(ROW_HEIGHT_KEY, Integer.toString(appearance.rowHeight()));
        values.setProperty(INPUT_ON_RIGHT_KEY, Boolean.toString(appearance.inputOnRight()));
        values.setProperty(ACCENT_COLOR_KEY, Integer.toString(appearance.accentColor()));
        values.setProperty(PANEL_COLOR_KEY, Integer.toString(appearance.panelColor()));
        values.setProperty(PANEL_OPACITY_KEY, Integer.toString(appearance.panelOpacity()));
        for (String category : state.hiddenCategories()) {
            values.setProperty(HIDDEN_CATEGORY_PREFIX + encodeHiddenKey(category), "true");
        }
        for (String mod : state.hiddenMods()) {
            values.setProperty(HIDDEN_MOD_PREFIX + encodeHiddenKey(mod), "true");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            values.store(output, "Keyset shared client preferences");
            return output.toByteArray();
        }
    }

    private void apply(State state) {
        favorites.clear();
        favorites.addAll(state.favorites());
        routes.clear();
        routes.putAll(state.routes());
        sidebarRatio = state.sidebarRatio();
        appearance = state.appearance();
        hiddenCategories.clear();
        hiddenCategories.addAll(state.hiddenCategories());
        hiddenMods.clear();
        hiddenMods.addAll(state.hiddenMods());
    }

    private static String encodeHiddenKey(String id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static double clampSidebarRatio(double ratio) {
        if (Double.isNaN(ratio)) return DEFAULT_SIDEBAR_RATIO;
        return Math.max(0.35, Math.min(0.65, ratio));
    }

    private static String contextHash(String context) {
        return hash(context);
    }

    private static String routeKey(String context, String scope) {
        return scope == null ? contextHash(context) : routeScopePrefix(scope) + contextHash(context);
    }

    private static String routeScopePrefix(String scope) {
        return "i-" + hash(scope) + "-";
    }

    private static boolean isValidRouteKey(String key) {
        return key.matches("[0-9a-f]{64}|i-[0-9a-f]{64}-[0-9a-f]{64}");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isCanonicalUuid(String value) {
        try { return java.util.UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException | NullPointerException exception) { return false; }
    }

    public record State(Set<String> favorites, Map<String, String> routes, double sidebarRatio,
                        UiAppearance appearance, Set<String> hiddenCategories, Set<String> hiddenMods) {
        /** Binding/favorite undo must not undo later changes to the editor's presentation. */
        public State withDisplayFrom(State current) {
            return new State(favorites, routes, current.sidebarRatio(), current.appearance(),
                    current.hiddenCategories(), current.hiddenMods());
        }

        public State(Set<String> favorites, Map<String, String> routes) {
            this(favorites, routes, DEFAULT_SIDEBAR_RATIO);
        }

        public State(Set<String> favorites, Map<String, String> routes, double sidebarRatio) {
            this(favorites, routes, sidebarRatio, UiAppearance.defaults(), Set.of(), Set.of());
        }

        public State {
            favorites = Set.copyOf(new LinkedHashSet<>(favorites));
            routes = Map.copyOf(routes);
            sidebarRatio = clampSidebarRatio(sidebarRatio);
            appearance = Objects.requireNonNullElseGet(appearance, UiAppearance::defaults);
            hiddenCategories = Set.copyOf(new LinkedHashSet<>(hiddenCategories));
            hiddenMods = Set.copyOf(new LinkedHashSet<>(hiddenMods));
        }
    }

    /** Opaque memory-and-baseline snapshot for rolling back a failed shared-settings reload. */
    public static final class ReloadCheckpoint {
        private final State currentState;
        private final State baseline;

        private ReloadCheckpoint(State currentState, State baseline) {
            this.currentState = currentState;
            this.baseline = baseline;
        }
    }
}
