package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.profile.SavedTheme;
import dev.keyset.profile.ThemeStore;
import dev.keyset.profile.UiAppearance;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only themes supplied by active resource packs. ResourceManager resolves pack priority. */
public final class ResourcePackThemes {
    private static final Logger LOG = LoggerFactory.getLogger("Keyset/ResourceThemes");
    private static final AtomicLong REVISIONS = new AtomicLong();
    private static volatile Snapshot snapshot = new Snapshot(0, List.of());
    private static ResourceManager registeredManager;
    private static long appliedRevision = -1;
    private static String appliedTheme;
    private static long retryRevision = -1, retryAfter;
    private static String retryTheme;

    private ResourcePackThemes() {}

    public static void initialize() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager == registeredManager) return;
        if (manager instanceof ReloadableResourceManager reloadable) {
            reloadable.registerReloadListener(new SimplePreparableReloadListener<List<Entry>>() {
                @Override protected List<Entry> prepare(ResourceManager resources, ProfilerFiller profiler) {
                    return read(resources);
                }
                @Override protected void apply(List<Entry> entries, ResourceManager resources, ProfilerFiller profiler) {
                    publish(entries);
                }
            });
        }
        registeredManager = manager;
        publish(read(manager));
    }

    public static Snapshot snapshot() {
        initialize();
        return snapshot;
    }

    private static List<Entry> read(ResourceManager manager) {
        List<Entry> entries = new ArrayList<>();
        manager.listResources("themes", id -> id.getNamespace().equals("keyset") && id.getPath().endsWith(".json"))
            .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(resource -> {
                try (var input = resource.getValue().open()) {
                    byte[] bytes = input.readNBytes(65537);
                    if (bytes.length > 65536) throw new IOException("Theme exceeds 64 KiB");
                    String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                    entries.add(new Entry(resource.getKey().toString(), resource.getValue().sourcePackId(), ThemeStore.decodeTheme(json)));
                } catch (IOException | RuntimeException failure) {
                    LOG.warn("Ignoring invalid resource-pack theme {}", resource.getKey(), failure);
                }
            });
        return List.copyOf(entries);
    }

    private static void publish(List<Entry> entries) {
        snapshot = new Snapshot(REVISIONS.incrementAndGet(), entries);
    }

    /** Called on the client thread after shared preferences are synchronized. */
    public static void tick() {
        Snapshot current = snapshot();
        String id = KeysetClient.resourceThemeId();
        if (appliedRevision == current.revision() && Objects.equals(id, appliedTheme)) return;
        if (retryRevision == current.revision() && Objects.equals(id, retryTheme) && System.nanoTime() < retryAfter) return;
        Entry entry = id == null ? null : current.entries().stream().filter(value -> value.resourceId().equals(id)).findFirst().orElse(null);
        if (entry != null && !samePalette(KeysetClient.appearance(), entry.theme().applyTo(KeysetClient.appearance()))) {
            try { KeysetClient.applyResourceTheme(entry.resourceId(), entry.theme()); }
            catch (IOException | RuntimeException failure) {
                retryRevision = current.revision();
                retryTheme = id;
                retryAfter = System.nanoTime() + 5_000_000_000L;
                LOG.warn("Cannot apply resource-pack theme {}; retrying in five seconds", id, failure);
                return;
            }
        }
        appliedRevision = current.revision();
        appliedTheme = id;
        retryRevision = -1;
    }

    public static boolean samePalette(UiAppearance first, UiAppearance second) {
        return first.accentColor() == second.accentColor() && first.panelColor() == second.panelColor()
            && first.panelOpacity() == second.panelOpacity() && first.boundKeyColor() == second.boundKeyColor()
            && first.conflictKeyColor() == second.conflictKeyColor() && first.unboundKeyColor() == second.unboundKeyColor()
            && first.mouseBodyColor() == second.mouseBodyColor();
    }

    public record Entry(String resourceId, String packName, SavedTheme theme) {}
    public record Snapshot(long revision, List<Entry> entries) {
        public Snapshot { entries = List.copyOf(entries); }
    }
}
