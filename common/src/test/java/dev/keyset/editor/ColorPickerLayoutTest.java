package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorPickerLayoutTest {
    @Test
    void controlsAndPreviewRemainUsableAtEveryGuiScale() {
        for (int[] screen : new int[][] {{320, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}}) {
            var layout = ColorPickerLayout.forScreen(screen[0], screen[1], true);
            for (var r : new ColorPickerLayout.Rect[] {layout.color(), layout.hue(), layout.hex(), layout.samples()}) {
                assertTrue(r.width() > 0 && r.height() > 0);
                assertTrue(r.x() >= layout.innerX() && r.right() <= layout.innerX() + layout.innerWidth());
                assertTrue(r.y() >= layout.contentTop() && r.bottom() <= layout.contentBottom());
            }
            for (var r : layout.channels()) assertTrue(r.bottom() <= layout.contentBottom());
            assertTrue(layout.hex().bottom() < layout.footerY());
            assertTrue(layout.deviceTabs().bottom() + 6 < layout.preview().bottom());
            assertTrue(layout.preview().width() >= 280, "Devices must not be squeezed into a thumbnail");
            assertTrue(layout.preview().height() - 24 >= 100);
            if (layout.pagedPreview()) {
                assertEquals(layout.innerWidth(), layout.preview().width());
                assertTrue(layout.pageTabs().bottom() < layout.contentTop());
            } else {
                assertTrue(layout.samples().right() < layout.preview().x());
                assertTrue(layout.preview().width() >= 380, "Side preview has enough room for the full keyboard");
            }
        }
    }

    @Test
    void plainColorPickerDoesNotReserveAnUnusedDevicePane() {
        var layout = ColorPickerLayout.forScreen(960, 540, false);
        assertFalse(layout.pagedPreview());
        assertEquals(420, layout.width());
        assertEquals(layout.innerX() + layout.innerWidth(), layout.samples().right());
    }
}
