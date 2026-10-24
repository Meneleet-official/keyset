package dev.keyset.editor;

import dev.keyset.profile.Binding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Immutable assignment snapshot. Viewport scrolling only reads this index. */
public final class BindingIndex<T> {
    private final Map<T, Binding> bindings = new IdentityHashMap<>();
    private final Map<String, List<T>> byKey = new HashMap<>();
    private final Set<T> conflicting = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> conflictKeys = new HashSet<>();

    public BindingIndex(List<T> actions, Function<T, Binding> reader, BiPredicate<T, T> contextsConflict) {
        Map<Chord, List<T>> groups = new HashMap<>();
        for (T action : actions) {
            Binding binding = reader.apply(action);
            bindings.put(action, binding);
            for (String key : binding.keys()) byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(action);
            if (!binding.keys().isEmpty()) {
                groups.computeIfAbsent(Chord.of(binding),
                    ignored -> new ArrayList<>()).add(action);
            }
        }
        byKey.replaceAll((key, values) -> List.copyOf(values));
        for (Map.Entry<Chord, List<T>> entry : groups.entrySet()) {
            List<T> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    T first = group.get(i), second = group.get(j);
                    if (contextsConflict.test(first, second)) {
                        conflicting.add(first);
                        conflicting.add(second);
                        conflictKeys.addAll(entry.getKey().keys());
                    }
                }
            }
        }
    }

    public Binding binding(T action) { return bindings.get(action); }
    public List<T> forKey(String key) { return byKey.getOrDefault(key, List.of()); }
    public boolean conflicts(T action) { return conflicting.contains(action); }
    public boolean keyConflicts(String key) { return conflictKeys.contains(key); }
    public static boolean sameInputs(Binding first, Binding second) { return Chord.of(first).equals(Chord.of(second)); }

    private record Chord(List<String> keys, boolean ctrl, boolean shift, boolean alt, boolean command) {
        private static Chord of(Binding binding) {
            boolean ctrl = binding.ctrl(), shift = binding.shift(), alt = binding.alt(), command = binding.command();
            List<String> keys = new ArrayList<>();
            List<String> physicalKeys = binding.keys();
            for (String key : physicalKeys) {
                // Native providers store modifiers as physical inputs; vanilla uses modifier flags.
                // A modifier bound on its own remains a primary input.
                if (physicalKeys.size() > 1) {
                    switch (key) {
                        case "key.keyboard.left.control", "key.keyboard.right.control" -> { ctrl = true; continue; }
                        case "key.keyboard.left.shift", "key.keyboard.right.shift" -> { shift = true; continue; }
                        case "key.keyboard.left.alt", "key.keyboard.right.alt" -> { alt = true; continue; }
                        case "key.keyboard.left.win", "key.keyboard.right.win" -> { command = true; continue; }
                    }
                }
                keys.add(key);
            }
            return new Chord(List.copyOf(keys), ctrl, shift, alt, command);
        }
    }
}
