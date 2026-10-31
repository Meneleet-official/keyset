package dev.keyset.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An immutable snapshot of a named set of key bindings. */
public record Profile(String id, String name, Map<String, Binding> bindings) {
    public Profile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bindings, "bindings");
        if (id.isBlank()) {
            throw new IllegalArgumentException("A profile id cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("A profile name cannot be blank");
        }

        LinkedHashMap<String, Binding> copy = new LinkedHashMap<>();
        bindings.forEach((bindingId, binding) -> {
            Objects.requireNonNull(bindingId, "binding id");
            Objects.requireNonNull(binding, "binding");
            if (bindingId.isBlank()) {
                throw new IllegalArgumentException("A binding id cannot be blank");
            }
            copy.put(bindingId, binding);
        });
        bindings = Collections.unmodifiableMap(copy);
    }
}
