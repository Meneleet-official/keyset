package dev.keyset.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.keyset.client.KeysetScreen;

/** Optional entrypoint requested only when Mod Menu is installed. */
public final class KeysetModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return KeysetScreen::new;
    }
}
