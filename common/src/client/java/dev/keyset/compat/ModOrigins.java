package dev.keyset.compat;

import net.minecraft.client.KeyMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Finds the mod which created a key mapping without linking common code to either loader. */
public final class ModOrigins {
    private static final Owner UNKNOWN = new Owner("unknown", "Unknown mod");
    private static final Owner MINECRAFT = new Owner("minecraft", "Minecraft");
    private static final Owner KEYSET = new Owner("keyset", "Keyset");

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Map<KeyMapping, Class<?>> ORIGINS = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<KeyMapping, Owner> OWNERS = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<String, Owner> MODS_BY_ID = new LinkedHashMap<>();
    private static final List<SourceRoot> SOURCE_ROOTS = new ArrayList<>();
    private static final ClassValue<Optional<Path>> CODE_SOURCES = new ClassValue<>() {
        @Override
        protected Optional<Path> computeValue(Class<?> type) {
            return Optional.ofNullable(codeSource(type));
        }
    };
    private static volatile boolean metadataLoaded;

    private ModOrigins() {}

    /** Called once by the KeyMapping constructor mixin, while its caller is still on the stack. */
    public static void track(KeyMapping mapping) {
        Class<?> caller = STACK_WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(ModOrigins::isApplicationFrame)
                .findFirst()
                .orElse(null));
        if (caller != null) {
            synchronized (ORIGINS) {
                ORIGINS.put(mapping, caller);
                OWNERS.remove(mapping);
            }
        }
    }

    /** Returns the best supported owner; unknown is used whenever evidence is ambiguous. */
    public static Owner owner(KeyMapping mapping) {
        synchronized (ORIGINS) {
            Owner cached = OWNERS.get(mapping);
            if (cached != null) return cached;

            loadMetadataIfNeeded();
            Class<?> origin = ORIGINS.get(mapping);
            Owner result = ownerFromClass(origin);
            Owner namespaced = ownerFromNamespace(mapping);
            // Mixin constructors can appear as an Options/Minecraft stack frame. A known
            // mod namespace provides stronger evidence in that case.
            if (result == null || result.equals(MINECRAFT) && namespaced != null) result = namespaced;
            if (result == null) result = UNKNOWN;
            OWNERS.put(mapping, result);
            return result;
        }
    }

    private static boolean isApplicationFrame(Class<?> type) {
        String name = type.getName();
        return type != ModOrigins.class
                && !name.startsWith("dev.keyset.mixin.")
                && !name.startsWith("net.minecraft.client.KeyMapping")
                && !name.equals("net.minecraft.client.ToggleKeyMapping")
                && !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jdk.")
                && !name.startsWith("sun.")
                && !name.startsWith("org.spongepowered.")
                && !name.startsWith("net.fabricmc.")
                && !name.startsWith("net.neoforged.")
                && !name.startsWith("net.minecraftforge.");
    }

    private static Owner ownerFromClass(Class<?> type) {
        if (type == null) return null;
        String name = type.getName();
        if (name.startsWith("dev.keyset.")) return KEYSET;

        Path source = CODE_SOURCES.get(type).orElse(null);
        Owner sourceOwner = ownerForSource(source);
        if (sourceOwner != null) return sourceOwner;

        // This is an explicit Minecraft caller, not a default for unrecognized bindings.
        if (name.startsWith("net.minecraft.")) return MINECRAFT;
        return null;
    }

    private static Owner ownerForSource(Path source) {
        if (source == null) return null;
        int mostSpecific = -1;
        Set<Owner> matches = new LinkedHashSet<>();
        for (SourceRoot root : SOURCE_ROOTS) {
            if (!root.matches(source)) continue;
            int specificity = root.path.getNameCount();
            if (specificity > mostSpecific) {
                matches.clear();
                mostSpecific = specificity;
            }
            if (specificity == mostSpecific) matches.add(root.owner);
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private static Owner ownerFromNamespace(KeyMapping mapping) {
        String categoryNamespace = mapping.getCategory().id().getNamespace();
        Owner categoryOwner = MODS_BY_ID.get(categoryNamespace);
        if (categoryOwner != null && !categoryNamespace.equals("minecraft")) return categoryOwner;

        String keyName = mapping.getName();
        String keyNamespace = namespaceFromKeyName(keyName);
        return keyNamespace == null ? null : MODS_BY_ID.get(keyNamespace);
    }

    private static String namespaceFromKeyName(String keyName) {
        int firstDot = keyName.indexOf('.');
        if (firstDot < 0) return null;
        String prefix = keyName.substring(0, firstDot);
        if (!prefix.equals("key") && !prefix.equals("keybind")) return null;
        int secondDot = keyName.indexOf('.', firstDot + 1);
        if (secondDot < 0) return null;
        return keyName.substring(firstDot + 1, secondDot);
    }

    private static Path codeSource(Class<?> type) {
        try {
            var protectionDomain = type.getProtectionDomain();
            if (protectionDomain == null || protectionDomain.getCodeSource() == null) return null;
            URL location = protectionDomain.getCodeSource().getLocation();
            return location == null ? null : pathFromUri(location.toURI());
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    private static void loadMetadataIfNeeded() {
        if (metadataLoaded) return;
        synchronized (ModOrigins.class) {
            if (metadataLoaded) return;
            // The metadata reads are small and happen at most once, when the UI first asks for an owner.
            readFabricMetadata();
            readNeoForgeMetadata();
            metadataLoaded = true;
        }
    }

    private static boolean readFabricMetadata() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false, ModOrigins.class.getClassLoader());
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object mods = invoke(loader, "getAllMods");
            if (!(mods instanceof Iterable<?> iterable)) return true;
            for (Object container : iterable) {
                try {
                    Object metadata = invoke(container, "getMetadata");
                    Owner owner = addMod(stringValue(metadata, "getId"), stringValue(metadata, "getName"));
                    if (owner == null) continue;
                    addPaths(invoke(container, "getRootPaths"), owner);
                    Object origin = invoke(container, "getOrigin");
                    addPaths(invoke(origin, "getPaths"), owner);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // Built-in or virtual containers may not expose paths; keep reading other mods.
                }
            }
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean readNeoForgeMetadata() {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList", false, ModOrigins.class.getClassLoader());
            Object modList = modListClass.getMethod("get").invoke(null);
            Object mods = invoke(modList, "getMods");
            if (!(mods instanceof Iterable<?> iterable)) return true;
            for (Object modInfo : iterable) {
                try {
                    Owner owner = addMod(stringValue(modInfo, "getModId"), stringValue(modInfo, "getDisplayName"));
                    if (owner == null) continue;
                    Object owningFile = invoke(modInfo, "getOwningFile");
                    Object file = invoke(owningFile, "getFile");
                    Object filePath = invoke(file, "getFilePath");
                    if (filePath instanceof Path path) addPath(path, owner);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // One unusual mod container must not hide metadata for all remaining mods.
                }
            }
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Owner addMod(String rawId, String rawName) {
        if (rawId == null || rawId.isBlank()) return null;
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String name = rawName == null || rawName.isBlank() ? id : rawName.trim();
        Owner owner = id.equals("minecraft") ? MINECRAFT : id.equals("keyset") ? KEYSET : new Owner(id, name);
        MODS_BY_ID.putIfAbsent(id, owner);
        return MODS_BY_ID.get(id);
    }

    private static void addPaths(Object value, Owner owner) {
        if (!(value instanceof Iterable<?> iterable)) return;
        for (Object item : iterable) if (item instanceof Path path) addPath(path, owner);
    }

    private static void addPath(Path path, Owner owner) {
        try {
            Path normalized = pathFromUri(path.toUri());
            if (normalized == null) return;
            SOURCE_ROOTS.add(new SourceRoot(normalized, isArchive(normalized), owner));
        } catch (RuntimeException ignored) {
            // A loader-specific Path implementation may not support toUri; namespace fallback still works.
        }
    }

    private static boolean isArchive(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jar") || fileName.endsWith(".zip");
    }

    private static Path pathFromUri(URI uri) {
        String raw = uri.toString();
        if (raw.startsWith("jar:")) {
            raw = raw.substring("jar:".length());
            int archiveEnd = raw.indexOf("!/");
            if (archiveEnd >= 0) raw = raw.substring(0, archiveEnd);
            uri = URI.create(raw);
        }
        if (!"file".equalsIgnoreCase(uri.getScheme())) return null;
        return Paths.get(uri).toAbsolutePath().normalize();
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = publicMethod(target.getClass(), methodName);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw ex;
        }
    }

    private static Method publicMethod(Class<?> type, String methodName) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> contract : current.getInterfaces()) {
                if (!Modifier.isPublic(contract.getModifiers())) continue;
                try {
                    return contract.getMethod(methodName);
                } catch (NoSuchMethodException ignored) {
                    // Check the remaining public API interfaces implemented by this loader type.
                }
            }
            try {
                Method method = current.getMethod(methodName);
                if (Modifier.isPublic(current.getModifiers())) return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through the superclass hierarchy.
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName);
    }

    private static String stringValue(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        return value instanceof String string ? string : null;
    }

    public record Owner(String id, String name) {}

    private record SourceRoot(Path path, boolean archive, Owner owner) {
        private boolean matches(Path source) {
            return archive ? source.equals(path) : source.startsWith(path);
        }
    }
}
