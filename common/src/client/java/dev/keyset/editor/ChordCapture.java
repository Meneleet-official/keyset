package dev.keyset.editor;

import dev.keyset.profile.Binding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Native hotkey capture finishes once all newly pressed inputs are released. */
public final class ChordCapture {
    private final LinkedHashSet<String> keys = new LinkedHashSet<>();
    private final Set<String> held = new HashSet<>();

    public void include(String key) { keys.add(key); }
    public void press(String key) { keys.add(key); held.add(key); }
    public boolean isEmpty() { return keys.isEmpty(); }
    public boolean contains(String key) { return keys.contains(key); }
    public void clear() { keys.clear(); held.clear(); }
    public Binding preview() {
        if (keys.isEmpty()) return new Binding("key.keyboard.unknown", false, false, false);
        var ordered = new ArrayList<>(keys);
        return new Binding(ordered.getFirst(), false, false, false, false, ordered.subList(1, ordered.size()));
    }
    public Optional<Binding> release(String key) {
        if (!held.remove(key) || !held.isEmpty()) return Optional.empty();
        Binding value = preview();
        clear();
        return Optional.of(value);
    }
}
