package dev.keyset;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.profile.Binding;
import net.minecraft.client.KeyMapping;

/** Preserve loader-native modifiers while sharing the multi-modifier chord engine. */
public interface BindingPlatform {
    default Binding read(KeyMapping mapping) { return new Binding(mapping.saveString(), false, false, false); }
    default Binding defaults(KeyMapping mapping) { return new Binding(mapping.getDefaultKey().getName(), false, false, false); }
    default void assign(KeyMapping mapping, InputConstants.Key key) { mapping.setKey(key); }
    default boolean active(KeyMapping mapping) { return true; }
    default boolean conflicts(KeyMapping first, KeyMapping second) { return true; }
}
