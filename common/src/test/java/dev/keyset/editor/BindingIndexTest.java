package dev.keyset.editor;

import dev.keyset.profile.Binding;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BindingIndexTest {
    @Test
    void freePhysicalInputsIncludeGenericModifiersAndHiddenAssignments() {
        Action hidden = action("key.keyboard.x", true, "game");
        Action visible = action("key.mouse.4", false, "game");
        Action command = new Action(new Binding("key.keyboard.c", false, false, false, true), "game");
        var actual = new BindingIndex<>(List.of(hidden, visible, command), Action::binding, (a, b) -> true);
        var diagram = new BindingIndex<>(List.of(visible), actual::binding, (a, b) -> true);
        assertTrue(actual.forInput("key.keyboard.z").isEmpty(), "An unused input can be highlighted as free");
        assertTrue(diagram.forKey("key.keyboard.x").isEmpty(), "Hidden-only inputs remain neutral");
        assertFalse(actual.forInput("key.keyboard.x").isEmpty(), "Hidden inputs are not free");
        assertEquals(List.of(hidden), actual.forInput("key.keyboard.left.control"));
        assertEquals(List.of(hidden), actual.forInput("key.keyboard.right.control"));
        assertEquals(List.of(command), actual.forInput("key.keyboard.left.win"));
        assertFalse(actual.forInput("key.keyboard.right.win").isEmpty());
        assertEquals(List.of(visible), diagram.forKey("key.mouse.4"));
    }

    @Test
    void physicalInputIndexCountsEachActionOnceAndDoesNotInventModifiersForUnboundKeys() {
        Action duplicate = new Action(new Binding("key.keyboard.left.control", true, true, true, true,
            List.of("key.keyboard.c", "key.keyboard.right.control")), "game");
        Action unbound = action("key.keyboard.unknown", true, "game");
        var index = new BindingIndex<>(List.of(duplicate, unbound), Action::binding, (a, b) -> true);
        assertEquals(List.of(duplicate), index.forInput("key.keyboard.left.control"));
        assertEquals(List.of(duplicate), index.forInput("key.keyboard.right.control"));
        assertEquals(List.of(duplicate), index.forInput("key.keyboard.left.shift"));
        assertEquals(List.of(duplicate), index.forInput("key.keyboard.right.alt"));
        assertTrue(index.forInput("key.keyboard.unknown").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> index.forInput("key.keyboard.c").clear());
    }

    @Test
    void hidingASectionChangesTheDiagramWithoutRemovingBindingsOrActualConflicts() {
        Action shown = action("key.keyboard.x", false, "game");
        Action hidden = action("key.keyboard.x", false, "game");
        Action hiddenOnly = action("key.keyboard.y", false, "game");
        var actual = new BindingIndex<>(List.of(shown, hidden, hiddenOnly), Action::binding, (a, b) -> true);
        var diagram = new BindingIndex<>(List.of(shown), actual::binding, (a, b) -> true);
        assertEquals(List.of(shown), diagram.forKey("key.keyboard.x"));
        assertTrue(diagram.forKey("key.keyboard.y").isEmpty());
        assertEquals(List.of(shown), diagram.forKey("key.keyboard.x"), "Shared keys keep their visible assignment");
        assertTrue(diagram.forKey("key.keyboard.z").isEmpty(), "Free and hidden-only keys both have a neutral diagram state");
        assertFalse(diagram.keyConflicts("key.keyboard.x"));
        assertTrue(actual.conflicts(shown), "Real conflicts still include hidden actions");
        assertEquals(List.of(shown, hidden), actual.forKey("key.keyboard.x"));
        assertEquals("key.keyboard.y", actual.binding(hiddenOnly).key(), "Hiding must not unbind");
    }

    @Test
    void showingSectionsAgainRestoresHighlightsWithoutChangingBindings() {
        Action action = action("key.keyboard.keypad.7", false, "game");
        var actual = new BindingIndex<>(List.of(action), Action::binding, (a, b) -> true);
        var hidden = new BindingIndex<Action>(List.of(), Action::binding, (a, b) -> true);
        assertTrue(hidden.forKey("key.keyboard.keypad.7").isEmpty(), "Hidden bindings do not highlight a key");
        var restored = new BindingIndex<>(List.of(action), actual::binding, (a, b) -> true);
        assertEquals(List.of(action), restored.forKey("key.keyboard.keypad.7"));
        assertEquals(action.binding(), actual.binding(action));
    }

    @Test
    void hiddenNativeModifiersDoNotContributeToTheDiagram() {
        Action visible = action("key.keyboard.v", true, "game");
        Action hiddenNative = new Action(new Binding("key.keyboard.right.control", false, false, false,
            false, List.of("key.keyboard.c")), "game");
        var actual = new BindingIndex<>(List.of(visible, hiddenNative), Action::binding, (a, b) -> true);
        var diagram = new BindingIndex<>(List.of(visible), actual::binding, (a, b) -> true);
        assertTrue(diagram.binding(visible).ctrl(), "A visible modifier chord stays bound");
        assertTrue(diagram.forKey("key.keyboard.right.control").isEmpty());
        assertTrue(diagram.forKey("key.keyboard.c").isEmpty());
        assertEquals(List.of(hiddenNative), actual.forKey("key.keyboard.right.control"));
    }

    @Test
    void hiddenNativeMouseChordsLeaveTheirExclusiveInputsLookingUnassigned() {
        Action shownMouse = action("key.mouse.left", false, "game");
        Action hiddenMouseChord = new Action(new Binding("key.mouse.left", false, false, false,
            false, List.of("key.keyboard.keypad.2", "key.mouse.4")), "game");
        var actual = new BindingIndex<>(List.of(shownMouse, hiddenMouseChord), Action::binding, (a, b) -> true);
        var diagram = new BindingIndex<>(List.of(shownMouse), actual::binding, (a, b) -> true);
        assertEquals(List.of(shownMouse), diagram.forKey("key.mouse.left"));
        assertTrue(diagram.forKey("key.keyboard.keypad.2").isEmpty());
        assertTrue(diagram.forKey("key.mouse.4").isEmpty());
        assertTrue(diagram.forKey("key.mouse.right").isEmpty());
        assertEquals(hiddenMouseChord.binding(), actual.binding(hiddenMouseChord));
    }

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
