package dev.keyset.client;

import com.mojang.blaze3d.platform.Window;
import dev.keyset.editor.EditorScale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Keyset's screens at their own GUI scale while they are open. Changing the window's scale keeps mouse input,
 * tooltips and text fields consistent, and the game's own scale returns as soon as a Keyset screen closes.
 * The choice belongs to this device, so it lives in the instance folder, not in the shared settings.
 */
public final class InterfaceScale {
    private static final Logger LOG = LoggerFactory.getLogger("Keyset/InterfaceScale");
    private static EditorScale.Mode mode;
    private static int[] forcedSize;

    private InterfaceScale() {}

    /** Call first in a screen's {@code init}: Minecraft passes the screen a size at the game's scale. */
    public static void apply(Screen screen) {
        if (forcedSize != null) {
            screen.width = forcedSize[0];
            screen.height = forcedSize[1];
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        int target = EditorScale.forMode(mode(), window.getWidth(), window.getHeight(), gameScale(minecraft));
        if (window.getGuiScale() != target) window.setGuiScale(target);
        screen.width = window.getGuiScaledWidth();
        screen.height = window.getGuiScaledHeight();
    }

    /** For the development self-test only: lay screens out at a GUI size the window cannot show. */
    static void forceSize(int width, int height) {
        forcedSize = width <= 0 ? null : new int[] {width, height};
    }

    public static void restore() {
        Minecraft minecraft = Minecraft.getInstance();
        int scale = gameScale(minecraft);
        if (minecraft.getWindow().getGuiScale() != scale) minecraft.getWindow().setGuiScale(scale);
    }

    private static int gameScale(Minecraft minecraft) {
        return minecraft.getWindow().calculateScale(minecraft.options.guiScale().get(),
            minecraft.options.forceUnicodeFont().get());
    }

    public static EditorScale.Mode mode() {
        if (mode == null) mode = load();
        return mode;
    }

    public static void setMode(EditorScale.Mode value) {
        mode = value;
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, value.name().toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException failure) {
            LOG.warn("Cannot save the interface size", failure);
        }
    }

    private static EditorScale.Mode load() {
        try {
            Path file = file();
            if (Files.isRegularFile(file)) {
                return EditorScale.Mode.valueOf(Files.readString(file, StandardCharsets.UTF_8).trim().toUpperCase(Locale.ROOT));
            }
        } catch (IOException | RuntimeException failure) {
            LOG.warn("Cannot read the interface size; using automatic", failure);
        }
        return EditorScale.Mode.AUTO;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/keyset/interface-scale.txt");
    }
}
