package dev.keyset.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalizationLayoutTest {
    @Test
    void previewAndSettingsFitWithoutCoveringEachOtherOrTheFooter() {
        for (int width : new int[] {320, 480, 640, 960, 1920}) {
            for (int height : new int[] {240, 253, 360, 540, 1080}) {
                var layout = PersonalizationLayout.forScreen(width, height);
                var preview = layout.preview();
                assertTrue(layout.x() >= 0 && layout.y() >= 0);
                assertTrue(layout.x() + layout.width() <= width);
                assertTrue(layout.y() + layout.height() <= height);
                assertTrue(layout.settingsBottom() - layout.contentY() >= 64);
                assertTrue(preview.x() >= layout.innerX());
                assertTrue(preview.x() + preview.width() <= layout.innerX() + layout.totalInnerWidth());
                assertTrue(preview.y() + preview.height() <= layout.footerY() - 12);
                if (layout.sidePreview()) {
                    assertTrue(layout.innerX() + layout.settingsWidth() < preview.x());
                } else {
                    assertTrue(layout.settingsBottom() < preview.y());
                }
            }
        }
    }
}
