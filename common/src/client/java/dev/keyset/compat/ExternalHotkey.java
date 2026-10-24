package dev.keyset.compat;

import dev.keyset.profile.Binding;
import java.io.IOException;
import net.minecraft.network.chat.Component;

/** Optional native input system; the owning mod continues to handle activation and callbacks. */
public interface ExternalHotkey {
    String id();
    String modId();
    String modName();
    Component name();
    Binding read();
    Binding defaults();
    void assign(Binding binding) throws IOException;
    void save() throws IOException;
}
