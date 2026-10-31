package dev.keyset.editor;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChordCaptureTest {
    @Test void capturesOrderedMAndCInsteadOfPrematurelyBindingOnlyM() {
        var capture = new ChordCapture();
        capture.press("key.keyboard.m");
        capture.press("key.keyboard.c");
        capture.press("key.keyboard.c"); // key repeat
        assertTrue(capture.release("key.keyboard.c").isEmpty());
        var result = capture.release("key.keyboard.m").orElseThrow();
        assertEquals(List.of("key.keyboard.m", "key.keyboard.c"), result.keys());
        assertTrue(capture.isEmpty());
        assertTrue(capture.release("key.keyboard.m").isEmpty());
    }
    @Test void supportsMouseInputsAndModifiersHeldBeforeSelection() {
        var capture = new ChordCapture();
        capture.include("key.keyboard.left.control");
        capture.press("key.keyboard.m");
        capture.press("key.mouse.right");
        assertTrue(capture.release("key.keyboard.m").isEmpty());
        assertEquals(List.of("key.keyboard.left.control", "key.keyboard.m", "key.mouse.right"),
            capture.release("key.mouse.right").orElseThrow().keys());
    }
    @Test void cancelClearsHeldInputsSoEscapeReleaseCannotCommitThePreviousChord() {
        var capture = new ChordCapture();
        capture.press("key.keyboard.m");
        capture.clear();
        assertTrue(capture.release("key.keyboard.m").isEmpty());
        capture.press("key.keyboard.c");
        assertEquals(List.of("key.keyboard.c"), capture.release("key.keyboard.c").orElseThrow().keys());
    }
}
