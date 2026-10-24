# Keyset

A client-side Minecraft mod for managing keyboard and mouse bindings, shortcuts, and separate control profiles.

## Download and install

Download the JAR for your mod loader from [Releases](https://github.com/Meneleet-official/keyset/releases/latest). Each release lists its supported Minecraft, loader, and Java versions. Install the matching Fabric Loader or NeoForge version, then place the JAR in the `mods` folder.

Keyset runs on the client. Fabric API and server installation are not required.

## Usage

Open **Options -> Keyset**. In-game, open the editor with the key below Esc, usually ` or Grave. You can change this key in the controls settings.

- Select an action, then press a key, key combination, or mouse button. Press **Esc** to unbind it. Changes are saved immediately.
- Use the visual keyboard and mouse diagrams to inspect bindings. Search and filter actions by category, mod, conflicts, or favorites.
- Create, select, rename, and delete profiles. Assign the **Next Key Profile** action to switch profiles with a shortcut.
- Set a profile to **Auto** for a world or server. Keyset activates it the next time you join.
- The interface follows Minecraft's GUI scale and adapts to smaller windows. Key labels use the Minecraft language, and you can select a keyboard layout manually.
- Keyset can manage Litematica and MaLiLib hotkeys when those mods are installed.

The menu is translated into Minecraft's available languages. English and Russian have been reviewed; other translations may contain errors.

## Profiles and transfer

Profiles are stored separately from the mod JAR in `config/keyset/profiles.json` in the shared Minecraft directory. Game instances on the same computer use the same file. Set a custom directory with the `KEYSET_CONFIG_DIR` environment variable or the `-Dkeyset.configDir=...` JVM option.

To transfer profiles to another computer, choose **Export** in the profile menu. Copy the generated JSON files from `config/keyset/transfer/` to the other computer, then choose **Import**.

## Build from source

Use the Gradle Wrapper:

```powershell
.\gradlew.bat build
```

The JAR files are placed in `build/mods/`.

## License and credits

Keyset is distributed under the **ISC License**. It is based on [Keymap by Einjerjar](https://github.com/Einjerjar/keymap), and the original copyright notice is retained. Keyboard layout data is distributed under a separate Unicode license; see `LICENSE-KEYBOARD-DATA.txt`.
