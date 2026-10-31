package dev.keyset.profile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyboardPlatformTest {
    @Test
    void operatingSystemSelectsMacOrPcWithoutDependingOnTheGameLanguage() {
        assertEquals(KeyboardPlatform.MAC, KeyboardPlatform.forOperatingSystem("Mac OS X"));
        assertEquals(KeyboardPlatform.MAC, KeyboardPlatform.forOperatingSystem("macOS"));
        assertEquals(KeyboardPlatform.MAC, KeyboardPlatform.forOperatingSystem("Darwin"));
        assertEquals(KeyboardPlatform.PC, KeyboardPlatform.forOperatingSystem("Windows 11"));
        assertEquals(KeyboardPlatform.PC, KeyboardPlatform.forOperatingSystem("Linux"));
        assertEquals(KeyboardPlatform.PC, KeyboardPlatform.forOperatingSystem(null));
    }

    @Test
    void missingSettingsUseAutoAndExplicitSelectionsStayExplicit() {
        assertEquals(KeyboardPlatform.AUTO, KeyboardPresentation.defaults().platform());
        assertEquals(KeyboardPlatform.AUTO, KeyboardPlatform.fromId(null));
        assertEquals(KeyboardPlatform.AUTO, KeyboardPlatform.fromId("unknown"));
        assertEquals(KeyboardPlatform.AUTO, KeyboardPlatform.fromId(" AUTO "));
        for (var platform : new KeyboardPlatform[] {KeyboardPlatform.PC, KeyboardPlatform.MAC, KeyboardPlatform.LAPTOP}) {
            assertEquals(platform, platform.resolved());
            assertEquals(platform, KeyboardPlatform.fromId(platform.id()));
        }
    }
}
