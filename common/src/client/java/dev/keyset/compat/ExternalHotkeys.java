package dev.keyset.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.profile.Binding;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/** Unbound editor proxies. They never participate in vanilla key routing. */
public final class ExternalHotkeys {
    private static final Map<KeyMapping, ExternalHotkey> HOTKEYS = new IdentityHashMap<>();
    private static final Map<String, KeyMapping.Category> CATEGORIES = new LinkedHashMap<>();
    private static final Map<KeyMapping.Category, Component> CATEGORY_LABELS = new LinkedHashMap<>();
    private static final List<KeyMapping> MAPPINGS = new ArrayList<>();
    private static final Set<ExternalHotkey> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean initialized;
    private static boolean saving;

    private ExternalHotkeys() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            for (ExternalHotkey hotkey : MalilibIntegration.discover()) {
                KeyMapping.Category category = CATEGORIES.computeIfAbsent(hotkey.modId(), id ->
                    KeyMapping.Category.register(Identifier.fromNamespaceAndPath(id, "keyset_hotkeys")));
                KeyMapping proxy = new KeyMapping("keyset.external." + hotkey.modId() + "." + hotkey.id(),
                    InputConstants.UNKNOWN.getValue(), category);
                CATEGORY_LABELS.put(category, Component.literal(hotkey.modName()));
                HOTKEYS.put(proxy, hotkey);
                MAPPINGS.add(proxy);
            }
        } catch (LinkageError | RuntimeException exception) {
            LoggerFactory.getLogger("Keyset").warn("Optional hotkey integration is unavailable", exception);
        }
    }

    public static List<KeyMapping> mappings() { return List.copyOf(MAPPINGS); }
    public static ExternalHotkey get(KeyMapping mapping) { return HOTKEYS.get(mapping); }
    public static Component categoryLabel(KeyMapping.Category category) {
        return CATEGORY_LABELS.getOrDefault(category, category.label());
    }

    public static void assign(KeyMapping mapping, Binding binding) throws IOException {
        ExternalHotkey hotkey = HOTKEYS.get(mapping);
        // Mark first so rollback also persists a provider that failed after a partial mutation.
        DIRTY.add(hotkey);
        hotkey.assign(binding);
    }

    public static void saveDirty() throws IOException {
        Map<String, ExternalHotkey> providers = new LinkedHashMap<>();
        for (ExternalHotkey hotkey : DIRTY) providers.putIfAbsent(hotkey.modId(), hotkey);
        saving = true;
        try {
            for (ExternalHotkey hotkey : providers.values()) hotkey.save();
            DIRTY.clear();
        } finally { saving = false; }
    }

    public static boolean isSaving() { return saving; }
}
