package dev.keyset.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.keyset.profile.KeyboardFormFactor;
import dev.keyset.profile.KeyboardPlatform;
import dev.keyset.profile.KeyboardPresentation;
import dev.keyset.profile.KeyboardBottomRow;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeyboardDiagramLayoutTest {
    @Test
    void customBottomRowsReorderInputsAndResizeSpaceWithoutOverlapsOrLosingKeys() {
        for (var form : KeyboardFormFactor.values()) for (var platform : KeyboardPlatform.values()) {
            var defaults = KeyboardPresentation.defaults().withPlatform(platform);
            var original = KeyboardDiagramLayout.forArea(0, 0, 480, 240, 100, form, KeyboardLayouts.forLanguage("en_us"), defaults);
            for (int quarters : new int[] {8, 25, 32}) for (int width : new int[] {240, 960}) {
                var preference = new KeyboardBottomRow(java.util.List.of("key.keyboard.space", "key.keyboard.fn", "key.keyboard.right.win"), quarters);
                var changed = KeyboardDiagramLayout.forArea(0, 0, width, 240, 100, form,
                    KeyboardLayouts.forLanguage("en_us"), defaults.withBottomRow(preference));
                assertEquals(original.keys().stream().map(KeyboardDiagramLayout.Cell::input).collect(java.util.stream.Collectors.toSet()),
                    changed.keys().stream().map(KeyboardDiagramLayout.Cell::input).collect(java.util.stream.Collectors.toSet()));
                assertEquals(changed.bounds().x(), key(changed, "key.keyboard.space").bounds().x());
                for (var cell : changed.keys()) assertTrue(right(cell.bounds()) <= right(changed.bounds()));
                for (int a = 0; a < changed.keys().size(); a++) for (int b = a + 1; b < changed.keys().size(); b++) {
                    assertFalse(overlaps(changed.keys().get(a).bounds(), changed.keys().get(b).bounds()), form + ":" + platform);
                }
            }
        }
    }
    @Test
    void laptopAndMacHaveFnAndCorrectModifierLabelsWithoutOverlaps() {
        for (var platform : KeyboardPlatform.values()) for (var form : KeyboardFormFactor.values()) {
            for (int width : new int[] {240, 480, 960}) {
                var board = KeyboardDiagramLayout.forArea(0, 0, width, 240, 100, form,
                    KeyboardLayouts.forLanguage("en_us"), platform);
                assertEquals(platform.resolved() != KeyboardPlatform.PC,
                    board.keys().stream().anyMatch(k -> k.input().equals("key.keyboard.fn")));
                var inputs = new HashSet<String>();
                for (var cell : board.keys()) {
                    assertTrue(inputs.add(cell.input()));
                    assertTrue(right(cell.bounds()) <= right(board.bounds()));
                }
                if (platform.resolved() == KeyboardPlatform.MAC) {
                    assertEquals("Cmd", key(board, "key.keyboard.left.win").label());
                    assertEquals("Opt", key(board, "key.keyboard.left.alt").label());
                    assertTrue(key(board, "key.keyboard.left.alt").bounds().x() < key(board, "key.keyboard.left.win").bounds().x());
                }
                for (int a = 0; a < board.keys().size(); a++) for (int b = a + 1; b < board.keys().size(); b++) {
                    assertFalse(overlaps(board.keys().get(a).bounds(), board.keys().get(b).bounds()), platform + ":" + form);
                }
            }
        }
    }
    @Test
    void compact75PacksFunctionNavigationAndArrowsIntoOneRectangularBoard() {
        for (int width : new int[] {240, 296, 480, 960}) {
            for (int scale : new int[] {60, 100}) {
                var board = KeyboardDiagramLayout.forArea(7, 11, width, 220, scale,
                    KeyboardFormFactor.COMPACT_75, KeyboardLayouts.forLanguage("en_us"));
                int pitch = key(board, "key.keyboard.home").bounds().y()
                    - key(board, "key.keyboard.delete").bounds().y();
                for (int i = 1; i <= 12; i++) {
                    assertEquals(board.bounds().x() + i * pitch, key(board, "key.keyboard.f" + i).bounds().x());
                }
                for (String input : new String[] {"delete", "home", "end", "page.up", "page.down", "right"}) {
                    assertEquals(right(board.bounds()), right(key(board, "key.keyboard." + input).bounds()));
                }
                assertEquals(key(board, "key.keyboard.up").bounds().x(), key(board, "key.keyboard.down").bounds().x());
                assertTrue(right(key(board, "key.keyboard.right.shift").bounds())
                    < key(board, "key.keyboard.up").bounds().x());
                assertTrue(right(key(board, "key.keyboard.right.control").bounds())
                    < key(board, "key.keyboard.left").bounds().x());
                for (int a = 0; a < board.keys().size(); a++) {
                    for (int b = a + 1; b < board.keys().size(); b++) {
                        assertFalse(overlaps(board.keys().get(a).bounds(), board.keys().get(b).bounds()));
                    }
                }
            }
        }
    }

    @Test
    void physicalRowsShareStraightEdgesAndStandardStaggerAtEveryScale() {
        for (var form : KeyboardFormFactor.values()) {
            for (int width : new int[] {296, 480, 960}) {
                for (int scale : new int[] {60, 100}) {
                    var layout = KeyboardDiagramLayout.forArea(0, 0, width, 240, scale,
                        form, KeyboardLayouts.forLanguage("ru_ru"));
                    var first = key(layout, form == KeyboardFormFactor.COMPACT_60
                        ? "key.keyboard.escape" : "key.keyboard.grave.accent").bounds();
                    int right = right(key(layout, "key.keyboard.backspace").bounds());
                    for (String input : new String[] {"tab", "caps.lock", "left.shift", "left.control"}) {
                        assertEquals(first.x(), key(layout, "key.keyboard." + input).bounds().x(), input);
                    }
                    for (String input : new String[] {"backslash", "enter"}) {
                        assertEquals(right, right(key(layout, "key.keyboard." + input).bounds()), input);
                    }
                    if (form != KeyboardFormFactor.COMPACT_75) {
                        assertEquals(right, right(key(layout, "key.keyboard.right.shift").bounds()));
                        assertEquals(right, right(key(layout, "key.keyboard.right.control").bounds()));
                    }
                    assertTrue(key(layout, "key.keyboard.q").bounds().x() < key(layout, "key.keyboard.a").bounds().x());
                    assertTrue(key(layout, "key.keyboard.a").bounds().x() < key(layout, "key.keyboard.z").bounds().x());
                    if (form != KeyboardFormFactor.COMPACT_60) {
                        var f1 = key(layout, "key.keyboard.f1").bounds();
                        assertEquals(first.width(), f1.width());
                        if (form != KeyboardFormFactor.COMPACT_75) {
                            assertEquals(right, right(key(layout, "key.keyboard.f12").bounds()));
                            assertTrue(key(layout, "key.keyboard.f5").bounds().x()
                                - right(key(layout, "key.keyboard.f4").bounds()) > 2);
                        }
                    }
                }
            }
        }
    }

    private static int right(KeyboardDiagramLayout.Rect rect) { return rect.x() + rect.width(); }

    @Test
    void everyFormFactorFitsTheCompactEditorAreaWithoutOverlaps() {
        for (KeyboardFormFactor formFactor : KeyboardFormFactor.values()) {
            var layout = KeyboardDiagramLayout.forArea(12, 82, 296, 102, 100,
                formFactor, KeyboardLayouts.forLanguage("ru_ru"));
            Set<String> inputs = new HashSet<>();
            for (var key : layout.keys()) {
                assertTrue(inputs.add(key.input()), "duplicate input in " + formFactor + ": " + key.input());
                assertTrue(key.bounds().x() >= 12);
                assertTrue(key.bounds().y() >= 82);
                assertTrue(key.bounds().x() + key.bounds().width() <= 308);
                assertTrue(key.bounds().y() + key.bounds().height() <= 184);
                assertTrue(key.bounds().x() >= layout.bounds().x());
                assertTrue(key.bounds().y() >= layout.bounds().y());
                assertTrue(key.bounds().x() + key.bounds().width() <= layout.bounds().x() + layout.bounds().width());
                assertTrue(key.bounds().y() + key.bounds().height() <= layout.bounds().y() + layout.bounds().height());
            }
            var semicolon = layout.keys().stream()
                .filter(key -> key.input().equals("key.keyboard.semicolon"))
                .findFirst().orElseThrow(() -> new AssertionError("missing semicolon key in " + formFactor));
            assertTrue(semicolon.label().equals("Ж"), "unexpected Russian semicolon label in " + formFactor);
            for (int first = 0; first < layout.keys().size(); first++) {
                for (int second = first + 1; second < layout.keys().size(); second++) {
                    assertFalse(overlaps(layout.keys().get(first).bounds(), layout.keys().get(second).bounds()),
                        "overlapping cells in " + formFactor + ": " + layout.keys().get(first) + " and "
                            + layout.keys().get(second));
                }
            }
        }
    }

    @Test
    void compactSizesRemoveTheExpectedKeyboardSectionsAndKeepEscape() {
        var full = layout(KeyboardFormFactor.FULL);
        var tkl = layout(KeyboardFormFactor.TKL);
        var compact75 = layout(KeyboardFormFactor.COMPACT_75);
        var compact60 = layout(KeyboardFormFactor.COMPACT_60);
        assertTrue(full.keys().size() > tkl.keys().size());
        assertTrue(tkl.keys().size() > compact75.keys().size());
        assertTrue(compact75.keys().size() > compact60.keys().size());
        for (var layout : new KeyboardDiagramLayout[] {full, tkl, compact75, compact60}) {
            assertTrue(layout.keys().stream().anyMatch(key -> key.input().equals("key.keyboard.escape")));
        }
        assertFalse(compact60.keys().stream().anyMatch(key -> key.input().matches("key\\.keyboard\\.f\\d+")));
        assertFalse(compact60.keys().stream().anyMatch(key -> key.input().startsWith("key.keyboard.keypad.")));
        assertFalse(compact60.keys().stream().anyMatch(key -> key.input().equals("key.keyboard.grave.accent")));
        var escape = key(compact60, "key.keyboard.escape");
        var one = key(compact60, "key.keyboard.1");
        var space = key(compact60, "key.keyboard.space");
        assertEquals(escape.bounds().y(), one.bounds().y());
        assertTrue(space.bounds().y() > one.bounds().y());
        assertEquals(compact60.bounds().y() + compact60.bounds().height(),
            space.bounds().y() + space.bounds().height());

        assertTrue(compact75.keys().stream().anyMatch(key -> key.input().equals("key.keyboard.insert")));
        assertTrue(compact75.keys().stream().anyMatch(key -> key.input().equals("key.keyboard.print.screen")));
        assertFalse(compact75.keys().stream().anyMatch(key -> key.input().equals("key.keyboard.scroll.lock")));
        assertFalse(compact75.keys().stream().anyMatch(key -> key.input().equals("key.keyboard.pause")));
        var delete = key(compact75, "key.keyboard.delete");
        var home = key(compact75, "key.keyboard.home");
        var pageUp = key(compact75, "key.keyboard.page.up");
        var pageDown = key(compact75, "key.keyboard.page.down");
        var end = key(compact75, "key.keyboard.end");
        var up = key(compact75, "key.keyboard.up");
        var left = key(compact75, "key.keyboard.left");
        var down = key(compact75, "key.keyboard.down");
        var right = key(compact75, "key.keyboard.right");
        assertTrue(delete.bounds().y() < home.bounds().y());
        assertTrue(home.bounds().y() < end.bounds().y());
        assertTrue(end.bounds().y() < pageUp.bounds().y());
        assertTrue(pageUp.bounds().y() < pageDown.bounds().y());
        assertEquals(pageDown.bounds().y(), up.bounds().y());
        assertTrue(up.bounds().x() < pageDown.bounds().x());
        assertEquals(left.bounds().y(), down.bounds().y());
        assertEquals(down.bounds().y(), right.bounds().y());
        assertTrue(left.bounds().x() < down.bounds().x());
        assertTrue(down.bounds().x() < right.bounds().x());

        var numLock = key(full, "key.keyboard.num.lock");
        var oneKey = key(full, "key.keyboard.1");
        var keypadZero = key(full, "key.keyboard.keypad.0");
        var fullSpace = key(full, "key.keyboard.space");
        assertEquals(oneKey.bounds().y(), numLock.bounds().y());
        assertEquals(fullSpace.bounds().y(), keypadZero.bounds().y());
    }

    @Test
    void labelsFollowTheSelectedPhysicalLayoutAndScalingStaysInsideArea() {
        var russian = KeyboardDiagramLayout.forArea(3, 4, 640, 180, 60,
            KeyboardFormFactor.COMPACT_75, KeyboardLayouts.forLanguage("ru_ru"));
        var qKey = russian.keys().stream().filter(key -> key.input().equals("key.keyboard.q")).findFirst().orElseThrow();
        assertTrue(qKey.label().equals("Й"));
        assertTrue(russian.bounds().x() >= 3);
        assertTrue(russian.bounds().y() >= 4);
        assertTrue(russian.bounds().x() + russian.bounds().width() <= 643);
        assertTrue(russian.bounds().y() + russian.bounds().height() <= 184);
    }

    private static KeyboardDiagramLayout layout(KeyboardFormFactor formFactor) {
        return KeyboardDiagramLayout.forArea(0, 0, 480, 180, 100,
            formFactor, KeyboardLayouts.forLanguage("en_us"));
    }

    private static KeyboardDiagramLayout.Cell key(KeyboardDiagramLayout layout, String input) {
        return layout.keys().stream().filter(key -> key.input().equals(input)).findFirst().orElseThrow();
    }

    private static boolean overlaps(KeyboardDiagramLayout.Rect first, KeyboardDiagramLayout.Rect second) {
        return first.x() < second.x() + second.width() && second.x() < first.x() + first.width()
            && first.y() < second.y() + second.height() && second.y() < first.y() + first.height();
    }
}
