package dev.keyset.profile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final Path file;
    private final Set<String> favorites = new TreeSet<>();
    private final Map<String, String> routes = new TreeMap<>();
    private double sidebarRatio = DEFAULT_SIDEBAR_RATIO;
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
        if (!Files.exists(file)) return new State(Set.of(), Map.of(), DEFAULT_SIDEBAR_RATIO);
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        Set<String> favorites = new TreeSet<>();
        Map<String, String> routes = new TreeMap<>();
        double sidebarRatio = DEFAULT_SIDEBAR_RATIO;
        for (String key : values.stringPropertyNames()) {
            String value = values.getProperty(key);
            if (key.startsWith("favorite.")) {
                try {
                    favorites.add(new String(Base64.getUrlDecoder().decode(key.substring(9)),
                            java.nio.charset.StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ignored) { }
            } else if (key.startsWith("route.")) {
                String contextHash = key.substring(6);
                if (contextHash.matches("[0-9a-f]{64}") && isCanonicalUuid(value)) routes.put(contextHash, value);
            } else if (SIDEBAR_RATIO_KEY.equals(key)) {
                try {
                    sidebarRatio = clampSidebarRatio(Double.parseDouble(value));
                } catch (NumberFormatException invalidRatio) {
                    throw new IOException("Invalid sidebar ratio in preferences file: " + file, invalidRatio);
                }
            }
        }
        return new State(favorites, routes, sidebarRatio);
    }

    public boolean isFavorite(String actionId) { return favorites.contains(actionId); }
    public boolean toggleFavorite(String actionId) {
        if (favorites.add(actionId)) return true;
        favorites.remove(actionId);
        return false;
    }
    public String profileFor(String context) { return routes.get(contextHash(context)); }
    public double sidebarRatio() { return sidebarRatio; }
    public void setSidebarRatio(double value) { sidebarRatio = clampSidebarRatio(value); }

    public boolean toggleRoute(String context, String profileId) {
        String hash = contextHash(context);
        if (profileId.equals(routes.get(hash))) {
            routes.remove(hash);
            return false;
        }
        routes.put(hash, profileId);
        return true;
    }

    public void removeProfileRoutes(String profileId) { routes.values().removeIf(profileId::equals); }

    public void remapProfileRoutes(Map<String, String> replacements) {
        routes.replaceAll((context, profileId) -> replacements.getOrDefault(profileId, profileId));
    }

    public State checkpoint() { return new State(favorites, routes, sidebarRatio); }

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
        return new State(mergedFavorites, mergedRoutes, ratio);
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
    }

    private static double clampSidebarRatio(double ratio) {
        if (Double.isNaN(ratio)) return DEFAULT_SIDEBAR_RATIO;
        return Math.max(0.35, Math.min(0.65, ratio));
    }

    private static String contextHash(String context) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(context.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isCanonicalUuid(String value) {
        try { return java.util.UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException | NullPointerException exception) { return false; }
    }

    public record State(Set<String> favorites, Map<String, String> routes, double sidebarRatio) {
        public State(Set<String> favorites, Map<String, String> routes) {
            this(favorites, routes, DEFAULT_SIDEBAR_RATIO);
        }

        public State {
            favorites = Set.copyOf(new LinkedHashSet<>(favorites));
            routes = Map.copyOf(routes);
            sidebarRatio = clampSidebarRatio(sidebarRatio);
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
