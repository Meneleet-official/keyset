package dev.keyset.client;

import dev.keyset.editor.EditorScale;
import dev.keyset.profile.UiAppearance;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smoke test for development builds, inert unless the game starts with {@code -Dkeyset.selftest=true}.
 * Once the title screen is up it opens every Keyset screen under every interface size, lets each render a few
 * frames, scrolls it, and then closes the game. A screen that throws while it is built or drawn crashes the
 * game, which the surrounding script detects; problems it can catch itself are logged as {@code KEYSET-SELFTEST FAIL}.
 */
public final class KeysetSelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("Keyset/SelfTest");
    private static final boolean ENABLED = Boolean.getBoolean("keyset.selftest");
    /** Ticks each screen stays up; raise it with -Dkeyset.selftest.frames to leave time for screenshots. */
    private static final int FRAMES_PER_SCREEN = Integer.getInteger("keyset.selftest.frames", 4);
    /** With -Dkeyset.selftest.window=true only the window's own sizes are tried (the others cannot be seen). */
    /** With -Dkeyset.selftest.shots=true every screen is saved as run/screenshots/<step>.png by the game itself. */
    private static final boolean SHOTS = Boolean.getBoolean("keyset.selftest.shots");
    private static final boolean WINDOW_ONLY = Boolean.getBoolean("keyset.selftest.window");

    private record Step(String name, Supplier<Screen> screen) {}

    private static List<Step> steps;
    private static int index = -1, waited, titleTicks;
    private static int failures;
    private static EditorScale.Mode original;

    private KeysetSelfTest() {}

    public static void tick() {
        if (!ENABLED) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (steps == null) {
            if (minecraft.gui.screen() instanceof TitleScreen && ++titleTicks > 60) {
                original = InterfaceScale.mode();
                steps = buildSteps();
                index = 0;
                LOG.info("KEYSET-SELFTEST start: {} steps", steps.size());
            }
            return;
        }
        if (index >= steps.size()) return;
        if (waited < FRAMES_PER_SCREEN) {
            waited++;
            if (waited == FRAMES_PER_SCREEN) {
                if (SHOTS) screenshot(minecraft, steps.get(index).name().replace('/', '_'));
                scroll(minecraft);
            }
            return;
        }
        waited = 0;
        index++;
        if (index >= steps.size()) { finish(minecraft); return; }
        open(minecraft, steps.get(index));
    }

    /** Uses the game's own screenshot code, looked up by shape because its signature differs between versions. */
    private static void screenshot(Minecraft minecraft, String name) {
        try {
            Object target;
            try {
                target = Minecraft.class.getMethod("getMainRenderTarget").invoke(minecraft);
            } catch (NoSuchMethodException newer) {
                target = minecraft.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(minecraft.gameRenderer);
            }
            Consumer<Component> ignore = message -> { };
            for (Method method : net.minecraft.client.Screenshot.class.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("grab") || !Modifier.isStatic(method.getModifiers())
                        || types.length < 4 || types[0] != File.class || types[1] != String.class) continue;
                Object[] arguments = types.length == 5
                    ? new Object[] {minecraft.gameDirectory, name, target, 1, ignore}
                    : new Object[] {minecraft.gameDirectory, name, target, ignore};
                method.invoke(null, arguments);
                return;
            }
            LOG.warn("KEYSET-SELFTEST no screenshot method found");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("KEYSET-SELFTEST screenshot failed for {}", name, failure);
        }
    }

    private static void scroll(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (screen == null) return;
        try {
            double x = minecraft.getWindow().getGuiScaledWidth() / 4.0, y = minecraft.getWindow().getGuiScaledHeight() / 2.0;
            screen.mouseScrolled(x, y, 0.0, -1.0);
            screen.mouseScrolled(x * 3, y, 0.0, -1.0);
            screen.mouseScrolled(x, y, 0.0, 1.0);
        } catch (RuntimeException failure) {
            fail("scroll on " + screen.getClass().getSimpleName(), failure);
        }
    }

    private static void open(Minecraft minecraft, Step step) {
        try {
            Screen screen = step.screen().get();
            minecraft.setScreenAndShow(screen);
            LOG.info("KEYSET-SELFTEST open {} ({}x{} gui)", step.name(), minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
        } catch (RuntimeException failure) {
            fail(step.name(), failure);
        }
    }

    private static void finish(Minecraft minecraft) {
        InterfaceScale.setMode(original);
        LOG.info("KEYSET-SELFTEST DONE failures={}", failures);
        minecraft.stop();
    }

    private static void fail(String what, Throwable failure) {
        failures++;
        LOG.error("KEYSET-SELFTEST FAIL {}", what, failure);
    }

    private static final int[][] FORCED_SIZES = {{854, 480}, {640, 360}, {960, 540}, {1280, 720}, {1920, 1080},
        {3840, 2160}, {480, 270}, {427, 240}, {320, 240}, {700, 330}};

    private static List<Step> buildSteps() {
        List<Step> result = new ArrayList<>();
        for (EditorScale.Mode mode : EditorScale.Mode.values()) {
            if (WINDOW_ONLY && (mode == EditorScale.Mode.SMALLER || mode == EditorScale.Mode.GAME)) continue;
            String prefix = mode.name().toLowerCase() + "/";
            result.add(new Step(prefix + "mode", () -> {
                InterfaceScale.forceSize(0, 0);
                InterfaceScale.setMode(mode);
                return new TitleScreen();
            }));
            addScreens(result, prefix);
        }
        for (int[] size : WINDOW_ONLY ? new int[0][] : FORCED_SIZES) {
            String prefix = size[0] + "x" + size[1] + "/";
            result.add(new Step(prefix + "size", () -> {
                InterfaceScale.forceSize(size[0], size[1]);
                return new TitleScreen();
            }));
            addScreens(result, prefix);
        }
        result.add(new Step("reset", () -> {
            InterfaceScale.forceSize(0, 0);
            return new TitleScreen();
        }));
        return result;
    }

    private static void addScreens(List<Step> result, String prefix) {
        result.add(new Step(prefix + "editor", () -> new KeysetScreen(null)));
        result.add(new Step(prefix + "profiles", () -> inner("ProfilePicker")));
        result.add(new Step(prefix + "editor-again", () -> new KeysetScreen(null)));
        result.add(new Step(prefix + "transfer", () -> inner("TransferScreen")));
        result.add(new Step(prefix + "personalization", () -> new PersonalizationScreen(null)));
        result.add(new Step(prefix + "themes", () -> new ThemeLibraryScreen(new TitleScreen(), () -> { })));
        result.add(new Step(prefix + "legends", () -> new KeyboardStyleScreen(null)));
        for (AppearancePreviewRenderer.Focus focus : new AppearancePreviewRenderer.Focus[] {
                AppearancePreviewRenderer.Focus.PANEL, AppearancePreviewRenderer.Focus.ACCENT,
                AppearancePreviewRenderer.Focus.BOUND, AppearancePreviewRenderer.Focus.CONFLICT,
                AppearancePreviewRenderer.Focus.FREE, AppearancePreviewRenderer.Focus.MOUSE}) {
            result.add(new Step(prefix + "color-" + focus.name().toLowerCase(), () ->
                new ColorPickerScreen(null, Component.literal("color"), 0x112B26, color -> { },
                    color -> UiAppearance.defaults()).withFocus(focus)));
        }
    }

    /** The profile menu and the transfer screen are inner classes of the editor; build them through it. */
    private static Screen inner(String name) {
        try {
            // Built from the editor that is on screen, as in play: an editor that was never shown has no font yet.
            KeysetScreen editor = Minecraft.getInstance().gui.screen() instanceof KeysetScreen shown ? shown : new KeysetScreen(null);
            Class<?> type = Class.forName("dev.keyset.client.KeysetScreen$" + name);
            Constructor<?> constructor = type.getDeclaredConstructor(KeysetScreen.class);
            constructor.setAccessible(true);
            return (Screen) constructor.newInstance(editor);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
