package dev.keyset.editor;

import dev.keyset.profile.Binding;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BindingIndexTest {
    private record Action(Binding binding, String context) {}
    private static Action action(String key, boolean ctrl, String context) {
        return new Action(new Binding(key, ctrl, false, false), context);
    }

    @Test
    void scrollingThroughThousandsOfActionsNeverRereadsOrRechecksAssignments() {
        List<Action> actions = IntStream.range(0, 5000)
            .mapToObj(i -> action("key.test." + i, false, "game")).toList();
        AtomicInteger reads = new AtomicInteger(), comparisons = new AtomicInteger();
        BindingIndex<Action> index = new BindingIndex<>(actions, a -> { reads.incrementAndGet(); return a.binding(); },
            (a, b) -> { comparisons.incrementAndGet(); return true; });
        for (int pass = 0; pass < 30; pass++) {
            for (int row = 0; row < actions.size(); row++) {
                Action action = actions.get(row);
                assertSame(action, index.forKey("key.test." + row).getFirst());
                assertFalse(index.conflicts(action));
            }
        }
        assertEquals(actions.size(), reads.get());
        assertEquals(0, comparisons.get(), "Distinct bindings need no pairwise conflict checks");
    }

    @Test
    void onlyComparesIdenticalChordsAndRespectsContextsAndUnboundKeys() {
        Action plain = action("key.keyboard.x", false, "game");
        Action duplicate = action("key.keyboard.x", false, "game");
        Action otherContext = action("key.keyboard.x", false, "menu");
        Action ctrl = action("key.keyboard.x", true, "game");
        Action unbound = action("key.keyboard.unknown", false, "game");
        Action otherUnbound = action("key.keyboard.unknown", false, "game");
        BindingIndex<Action> index = new BindingIndex<>(List.of(plain, duplicate, otherContext, ctrl, unbound, otherUnbound),
            Action::binding, (a, b) -> a.context().equals(b.context()));
        assertTrue(index.conflicts(plain));
        assertTrue(index.conflicts(duplicate));
        assertFalse(index.conflicts(otherContext));
        assertFalse(index.conflicts(ctrl));
        assertFalse(index.conflicts(unbound));
        assertTrue(index.keyConflicts("key.keyboard.x"));
        assertFalse(index.keyConflicts("key.keyboard.unknown"));
    }

    @Test
    void nativePhysicalModifiersConflictWithEquivalentVanillaChord() {
        Action vanilla = action("key.keyboard.m", true, "game");
        Action nativeHotkey = new Action(new Binding("key.keyboard.right.control", false, false, false, false,
            List.of("key.keyboard.m")), "game");
        Action standaloneModifier = action("key.keyboard.right.control", false, "game");
        BindingIndex<Action> index = new BindingIndex<>(List.of(vanilla, nativeHotkey, standaloneModifier),
            Action::binding, (a, b) -> true);
        assertTrue(index.conflicts(vanilla));
        assertTrue(index.conflicts(nativeHotkey));
        assertFalse(index.conflicts(standaloneModifier));
    }

    @Test
    void nativeCombinationsIndexEveryInputAndSettingsDoNotHideDuplicateKeys() {
        Action first = new Action(new Binding("key.keyboard.m", false, false, false, false,
            List.of("key.keyboard.c"), "{\"context\":\"INGAME\"}"), "game");
        Action second = new Action(new Binding("key.keyboard.m", false, false, false, false,
            List.of("key.keyboard.c"), "{\"context\":\"ANY\"}"), "game");
        Action plain = action("key.keyboard.c", false, "game");
        BindingIndex<Action> index = new BindingIndex<>(List.of(first, second, plain), Action::binding, (a, b) -> true);
        assertEquals(List.of(first, second), index.forKey("key.keyboard.m"));
        assertEquals(List.of(first, second, plain), index.forKey("key.keyboard.c"));
        assertTrue(index.conflicts(first));
        assertTrue(index.keyConflicts("key.keyboard.c"));
        assertFalse(index.conflicts(plain));
    }

    @Test
    void rebuildingAfterUnbindKeepsOtherActionsOfTheSameKeyAndClearsItsConflict() {
        Action first = action("key.keyboard.x", false, "game"), second = action("key.keyboard.x", false, "game");
        BindingIndex<Action> before = new BindingIndex<>(List.of(first, second), Action::binding, (a, b) -> true);
        Action removed = action("key.keyboard.unknown", false, "game");
        BindingIndex<Action> after = new BindingIndex<>(List.of(removed, second), Action::binding, (a, b) -> true);
        assertEquals(List.of(second), after.forKey("key.keyboard.x"));
        assertFalse(after.keyConflicts("key.keyboard.x"));
        assertTrue(before.keyConflicts("key.keyboard.x"), "Snapshots must not mutate in place");
    }
}
