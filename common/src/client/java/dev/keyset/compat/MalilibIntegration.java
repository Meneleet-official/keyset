package dev.keyset.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.profile.Binding;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional Litematica/MaLiLib bridge. All optional classes are resolved at runtime. */
public final class MalilibIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("Keyset");
    private static final String LITEMATICA_ID = "litematica";
    private static final String LITEMATICA_ROOT = "fi.dy.masa.litematica.";
    private static final String LITEMATICA = "fi.dy.masa.litematica.config.";
    private static final String MALILIB = "fi.dy.masa.malilib.";

    private MalilibIntegration() {}

    /**
     * Finds Litematica and its MaLiLib hotkeys without introducing a hard dependency on either mod.
     * MaLiLib remains the owner of activation and callbacks; these objects only edit its keybinds.
     */
    public static List<ExternalHotkey> discover() {
        ClassLoader loader = MalilibIntegration.class.getClassLoader();
        try {
            Api api = Api.load(loader);
            Map<String, RegisteredConfig> configs = new LinkedHashMap<>();
            api.appendHotkeys(configs, LITEMATICA + "Hotkeys", "Hotkeys", true);
            api.appendHotkeys(configs, LITEMATICA + "Configs$Generic", "Generic", false);
            api.appendHotkeys(configs, LITEMATICA + "Configs$Visuals", "Visuals", false);

            List<ExternalHotkey> result = new ArrayList<>(configs.size());
            for (RegisteredConfig registered : configs.values()) {
                try {
                    result.add(new MalilibHotkey(api, registered.category(), registered.config()));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    LOGGER.warn("Skipping an unsupported Litematica hotkey", unwrap(exception));
                }
            }
            return List.copyOf(result);
        } catch (ClassNotFoundException absent) {
            // Litematica and MaLiLib are optional; a plain Keyset install is a normal case.
            return List.of();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LOGGER.warn("Litematica hotkey integration is unavailable or uses an unsupported API", unwrap(exception));
            return List.of();
        }
    }

    private static Throwable unwrap(Throwable exception) {
        if (exception instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return exception;
    }

    private record RegisteredConfig(String category, Object config) {}

    private static final class MalilibHotkey implements ExternalHotkey {
        private final Api api;
        private final Object config;
        private final Object keybind;
        private final String id;
        private final String prettyName;
        private final Binding defaults;

        private MalilibHotkey(Api api, String category, Object config) throws ReflectiveOperationException {
            this.api = api;
            this.config = config;
            this.id = api.name(config);
            this.prettyName = api.prettyName(config);
            this.keybind = api.keybind(config);
            if (id.isBlank()) throw new IllegalArgumentException("Litematica hotkey has no stable config name");

            String defaultKeys = api.defaultKeys(keybind);
            String defaultSettings = api.defaultSettings(keybind);
            this.defaults = api.bindingFromStorageString(defaultKeys, defaultSettings);
            // Validate the live value while discovering the entry. If MaLiLib introduces an
            // input type Keyset cannot represent, do not expose an editor row that would lose it.
            api.readBinding(keybind);
            api.track(category, id, config);
        }

        @Override public String id() { return id; }
        @Override public String modId() { return LITEMATICA_ID; }
        @Override public String modName() { return "Litematica"; }
        @Override public Component name() {
            try { return Component.literal(api.prettyName(config)); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return Component.literal(prettyName);
            }
        }
        @Override public Binding defaults() { return defaults; }

        @Override
        public Binding read() {
            try {
                return api.readBinding(keybind);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                LOGGER.warn("Could not read Litematica hotkey '{}'", id, unwrap(exception));
                throw new IllegalStateException("Cannot safely read native hotkey " + id, exception);
            }
        }

        @Override
        public void assign(Binding binding) throws IOException {
            try {
                List<String> storageNames = api.storageNames(binding);
                api.setKeys(keybind, String.join(",", storageNames));
                if (binding.nativeSettings() != null) api.setSettings(keybind, binding.nativeSettings());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                throw new IOException("Could not assign Litematica hotkey '" + id + "'", unwrap(exception));
            } finally {
                try {
                    api.updateUsedKeys();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    LOGGER.warn("Could not refresh MaLiLib's hotkey index after editing '{}'", id, unwrap(exception));
                }
            }
        }

        @Override
        public void save() throws IOException {
            try {
                api.saveLitematica();
            } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError exception) {
                throw new IOException("Could not save Litematica hotkeys", unwrap(exception));
            }
        }
    }

    private static final class Api {
        private final ClassLoader loader;
        private final Class<?> hotkeyType;
        private final Class<?> configBaseType;
        private final Class<?> keybindType;
        private final Class<?> keybindSettingsType;
        private final Class<?> keybindManagerType;
        private final Class<?> inputEventHandlerType;
        private final Class<?> scanCodesType;
        private final Class<?> configsType;
        private final List<HotkeyReference> trackedHotkeys = new ArrayList<>();

        private Api(ClassLoader loader) throws ReflectiveOperationException {
            this.loader = loader;
            this.hotkeyType = load(MALILIB + "hotkeys.IHotkey");
            this.configBaseType = load(MALILIB + "config.IConfigBase");
            this.keybindType = load(MALILIB + "hotkeys.IKeybind");
            this.keybindSettingsType = load(MALILIB + "hotkeys.KeybindSettings");
            this.keybindManagerType = load(MALILIB + "hotkeys.IKeybindManager");
            this.inputEventHandlerType = load(MALILIB + "event.InputEventHandler");
            this.scanCodesType = load(MALILIB + "util.input.ScanCodes");
            this.configsType = load(LITEMATICA + "Configs");
        }

        private static Api load(ClassLoader loader) throws ReflectiveOperationException {
            // Check Litematica first so a MaLiLib-only install cleanly results in no entries.
            Class.forName(LITEMATICA + "Hotkeys", false, loader);
            return new Api(loader);
        }

        private Class<?> load(String name) throws ClassNotFoundException {
            return Class.forName(name, true, loader);
        }

        private void appendHotkeys(Map<String, RegisteredConfig> target, String className, String category, boolean required)
                throws ReflectiveOperationException {
            Class<?> owner;
            try {
                owner = load(className);
            } catch (ClassNotFoundException absent) {
                if (required) throw absent;
                return;
            }
            Field field;
            try {
                field = owner.getField("HOTKEY_LIST");
            } catch (NoSuchFieldException absent) {
                if (required) throw absent;
                return;
            }
            Object value = field.get(null);
            if (!(value instanceof Iterable<?> entries)) return;
            for (Object entry : entries) {
                if (entry == null || !hotkeyType.isInstance(entry)) continue;
                String name = name(entry);
                if (!name.isBlank()) target.putIfAbsent(name, new RegisteredConfig(category, entry));
            }
        }

        private String name(Object config) throws ReflectiveOperationException {
            return String.valueOf(call(configBaseType, config, "getName"));
        }

        private String prettyName(Object config) throws ReflectiveOperationException {
            Object result = call(configBaseType, config, "getPrettyName");
            String value = result == null ? "" : result.toString().trim();
            return value.isEmpty() ? name(config) : value;
        }

        private Object keybind(Object config) throws ReflectiveOperationException {
            return call(hotkeyType, config, "getKeybind");
        }

        private String defaultKeys(Object keybind) throws ReflectiveOperationException {
            Object value = call(keybindType, keybind, "getDefaultStringValue");
            return value == null ? "" : value.toString();
        }

        private Binding readBinding(Object keybind) throws ReflectiveOperationException {
            Object rawKeys = call(keybindType, keybind, "getKeys");
            if (!(rawKeys instanceof Iterable<?> scanCodes)) {
                throw new IllegalArgumentException("MaLiLib returned a non-list keybind value");
            }
            List<String> names = new ArrayList<>();
            for (Object code : scanCodes) {
                if (!(code instanceof Number number)) throw new IllegalArgumentException("Invalid MaLiLib scan code");
                String minecraftName = minecraftNameForScanCode(number.intValue());
                if (minecraftName == null) throw new IllegalArgumentException("Unsupported MaLiLib scan code " + number.intValue());
                names.add(minecraftName);
            }
            return binding(names, settingsJson(keybind));
        }

        private Binding bindingFromStorageString(String storageKeys, String settings) throws ReflectiveOperationException {
            List<String> names = new ArrayList<>();
            if (storageKeys != null && !storageKeys.isBlank()) {
                for (String item : storageKeys.split(",")) {
                    String nativeName = item.trim();
                    if (nativeName.isEmpty()) continue;
                    int scanCode = scanCodeFromName(nativeName);
                    String activeName = scanCodeName(scanCode);
                    String minecraftName = minecraftNameForScanCode(scanCode);
                    if (minecraftName == null) throw new IllegalArgumentException("Unsupported MaLiLib key " + activeName);
                    names.add(minecraftName);
                }
            }
            return binding(names, settings);
        }

        private Binding binding(List<String> keys, String settings) {
            if (keys.isEmpty()) return new Binding("key.keyboard.unknown", false, false, false, false, List.of(), settings);
            String first = keys.get(0);
            return new Binding(first, false, false, false, false,
                    keys.size() == 1 ? List.of() : keys.subList(1, keys.size()), settings);
        }

        private String settingsJson(Object keybind) throws ReflectiveOperationException {
            Object settings = call(keybindType, keybind, "getSettings");
            Object json = call(settings.getClass(), settings, "toJson");
            return json == null ? null : json.toString();
        }

        /** Probe MaLiLib's own defaults while restoring the exact current settings object. */
        private String defaultSettings(Object keybind) throws ReflectiveOperationException {
            Object currentSettings = call(keybindType, keybind, "getSettings");
            try {
                call(keybindType, keybind, "resetSettingsToDefaults");
                Object defaults = call(keybindType, keybind, "getSettings");
                Object json = call(keybindSettingsType, defaults, "toJson");
                return json == null ? null : json.toString();
            } finally {
                call(keybindType, keybind, "setSettings", keybindSettingsType, currentSettings);
            }
        }

        private List<String> storageNames(Binding binding) throws ReflectiveOperationException {
            if ("key.keyboard.unknown".equals(binding.key())) return List.of();
            List<String> minecraftKeys = new ArrayList<>();
            if (binding.ctrl() && !containsModifier(binding, "control")) minecraftKeys.add("key.keyboard.left.control");
            if (binding.shift() && !containsModifier(binding, "shift")) minecraftKeys.add("key.keyboard.left.shift");
            if (binding.alt() && !containsModifier(binding, "alt")) minecraftKeys.add("key.keyboard.left.alt");
            if (binding.command() && !containsModifier(binding, "win")) minecraftKeys.add("key.keyboard.left.win");
            minecraftKeys.addAll(binding.keys());

            List<String> storage = new ArrayList<>();
            Set<Integer> seenScanCodes = new LinkedHashSet<>();
            for (String minecraftName : minecraftKeys) {
                InputConstants.Key input = InputConstants.getKey(minecraftName);
                int scanCode = input.getValue();
                if (input.getType() == InputConstants.Type.MOUSE) {
                    scanCode -= scanCodesType.getField("OFFSET_MOUSE").getInt(null);
                }
                if (scanCode == 0) throw new IllegalArgumentException("Unknown native input " + minecraftName);
                if (seenScanCodes.add(scanCode)) {
                    // Normalize names for MaLiLib's active keyboard type (for example, AZERTY).
                    String activeName = scanCodeName(scanCode);
                    if (activeName == null || activeName.isBlank()) {
                        throw new IllegalArgumentException("MaLiLib has no name for key " + minecraftName);
                    }
                    if (scanCodeFromName(activeName) != scanCode) {
                        throw new IllegalArgumentException("MaLiLib cannot round-trip input " + minecraftName);
                    }
                    storage.add(activeName);
                }
            }
            return List.copyOf(storage);
        }

        private static boolean containsModifier(Binding binding, String modifier) {
            for (String key : binding.keys()) {
                if (key.equals("key.keyboard.left." + modifier) || key.equals("key.keyboard.right." + modifier)) return true;
            }
            return false;
        }

        private void setKeys(Object keybind, String value) throws ReflectiveOperationException {
            call(keybindType, keybind, "setValueFromString", String.class, value);
        }

        private void setSettings(Object keybind, String json) throws ReflectiveOperationException {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            Object settings = call(keybindSettingsType, null, "fromJson", JsonObject.class, object);
            call(keybindType, keybind, "setSettings", keybindSettingsType, settings);
        }

        private int scanCodeFromName(String name) throws ReflectiveOperationException {
            Object code = call(scanCodesType, null, "getScanCodeFromName", String.class, name);
            return ((Number) code).intValue();
        }

        private String scanCodeName(int scanCode) throws ReflectiveOperationException {
            Object name = call(scanCodesType, null, "getNameForScanCode", int.class, scanCode);
            return name == null ? null : name.toString();
        }

        private String minecraftNameForScanCode(int scanCode) throws ReflectiveOperationException {
            // Both 26.3 APIs use SDL scancodes. MC supplies canonical names, including numeric
            // names for media/unlisted keys, so no layout-dependent letter translation is needed.
            int mouseOffset = scanCodesType.getField("OFFSET_MOUSE").getInt(null);
            if (scanCode >= -mouseOffset && scanCode < 0) {
                return InputConstants.Type.MOUSE.getOrCreate(scanCode + mouseOffset).getName();
            }
            if (scanCode == 0 || scanCodeName(scanCode) == null) return null;
            // MaLiLib's other negative ranges remain opaque numeric identifiers. The editor
            // proxies are never routed as vanilla keys; this also preserves native controller binds.
            return InputConstants.Type.KEYBOARD.getOrCreate(scanCode).getName();
        }

        private void updateUsedKeys() throws ReflectiveOperationException {
            Object manager = call(inputEventHandlerType, null, "getKeybindManager");
            call(keybindManagerType, manager, "updateUsedKeys");
        }

        private void saveLitematica() throws ReflectiveOperationException, IOException {
            call(configsType, null, "saveToFile");
            verifySavedHotkeys();
        }

        private void track(String category, String name, Object config) {
            trackedHotkeys.add(new HotkeyReference(category, name, config));
        }

        private void verifySavedHotkeys() throws ReflectiveOperationException, IOException {
            Class<?> fileUtils = load(MALILIB + "util.FileUtils");
            Path configDirectory = (Path) call(fileUtils, null, "getConfigDirectory");
            Class<?> reference = load(LITEMATICA_ROOT + "Reference");
            String modId = String.valueOf(reference.getField("MOD_ID").get(null));
            Path configPath = configDirectory.resolve(modId + ".json");
            JsonElement root;
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader);
            }
            if (root == null || !root.isJsonObject()) throw new IOException("Litematica config file is missing or invalid");
            JsonObject rootObject = root.getAsJsonObject();
            for (HotkeyReference hotkey : trackedHotkeys) {
                JsonElement sectionElement = rootObject.get(hotkey.category());
                JsonElement actual = sectionElement != null && sectionElement.isJsonObject()
                        ? sectionElement.getAsJsonObject().get(hotkey.name()) : null;
                JsonElement expected = (JsonElement) call(configBaseType, hotkey.config(), "getAsJsonElement");
                if (actual == null || !expected.equals(actual)) {
                    throw new IOException("Litematica did not persist hotkey '" + hotkey.name() + "'");
                }
            }
        }

        private record HotkeyReference(String category, String name, Object config) {}

        private static Object call(Class<?> owner, Object receiver, String methodName) throws ReflectiveOperationException {
            Method method = owner.getMethod(methodName);
            return invoke(method, receiver);
        }

        private static Object call(Class<?> owner, Object receiver, String methodName, Class<?> argumentType, Object argument)
                throws ReflectiveOperationException {
            Method method = owner.getMethod(methodName, argumentType);
            return invoke(method, receiver, argument);
        }

        private static Object invoke(Method method, Object receiver, Object... arguments) throws ReflectiveOperationException {
            try {
                return method.invoke(receiver, arguments);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw exception;
            }
        }
    }
}
