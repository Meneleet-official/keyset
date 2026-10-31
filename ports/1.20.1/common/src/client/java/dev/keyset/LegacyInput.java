package dev.keyset;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** 1.20.1 input helpers for modifier keys exposed differently by newer Minecraft releases. */
public final class LegacyInput {
    public static final int KEY_LGUI = GLFW.GLFW_KEY_LEFT_SUPER;
    public static final int KEY_RGUI = GLFW.GLFW_KEY_RIGHT_SUPER;
    public static final int MOD_CONTROL = GLFW.GLFW_MOD_CONTROL;
    public static final int MOD_SHIFT = GLFW.GLFW_MOD_SHIFT;
    public static final int MOD_ALT = GLFW.GLFW_MOD_ALT;
    public static final int MOD_SUPER = GLFW.GLFW_MOD_SUPER;

    private LegacyInput() {}

    public static boolean isKeyDown(int keyCode) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), keyCode);
    }
}
