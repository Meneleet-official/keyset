package dev.keyset.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.BindingPlatform;
import dev.keyset.profile.Binding;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputQuirks;
import net.neoforged.neoforge.client.settings.KeyModifier;

final class NeoBindingPlatform implements BindingPlatform {
    private Binding value(String key, KeyModifier modifier) {
        boolean command = modifier == KeyModifier.CONTROL_OR_COMMAND && InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY;
        boolean control = modifier == KeyModifier.CONTROL || (modifier == KeyModifier.CONTROL_OR_COMMAND && !command);
        return new Binding(key, control, modifier == KeyModifier.SHIFT, modifier == KeyModifier.ALT, command);
    }
    @Override public Binding read(KeyMapping mapping) { return value(mapping.saveString(), mapping.getKeyModifier()); }
    @Override public Binding defaults(KeyMapping mapping) { return value(mapping.getDefaultKey().getName(), mapping.getDefaultKeyModifier()); }
    @Override public void assign(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKeyModifierAndCode(KeyModifier.NONE, key);
    }
    @Override public boolean active(KeyMapping mapping) { return mapping.getKeyConflictContext().isActive(); }
    @Override public boolean conflicts(KeyMapping first, KeyMapping second) {
        return first.getKeyConflictContext().conflicts(second.getKeyConflictContext())
                || second.getKeyConflictContext().conflicts(first.getKeyConflictContext());
    }
}
