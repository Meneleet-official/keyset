# Keyset

A client-side Minecraft mod for managing keyboard and mouse bindings, shortcuts, and separate control profiles.

## Download and install

Download the JAR for your mod loader from [Releases](https://github.com/Meneleet-official/keyset/releases/latest). Each release lists its supported Minecraft, loader, and Java versions. Install the matching Fabric Loader or NeoForge version, then place the JAR in the `mods` folder.

Keyset runs on the client. Server installation is not required.

## Usage

Open **Options -> Keyset**. In-game, open the editor with the key below Esc (usually called Grave). You can change this key in the controls settings.

On Fabric, you can also open Keyset with its configuration button in Mod Menu, when installed.

- Select an action, then press a key, key combination, or mouse button. Press **Esc** to unbind it. Changes are saved immediately.
- Use the visual keyboard and mouse diagrams to inspect bindings. Search and filter actions by category, mod, conflicts, or favorites.
- Search with `#category` or `@mod`, using names or IDs. Quote names with spaces, for example `#"Builder Tools" @axiom`. Add ordinary words to find a specific action. `$key`, `(category)`, `{modifier}`, and `[key]` searches are also supported.
- Use **Sections** to hide any mod or category from the action list. All keyboard and mouse buttons stay visible; inputs used only by hidden actions look unassigned. Shared inputs keep the highlights of their visible actions. Hidden actions stay bound and active; actual conflict checks still include them. A **-** button in a category header hides it directly.
- Open **Style** in the top bar to change panel position, keyboard/mouse size, action row height, colors, and opacity. Drag the divider to adjust panel widths. These settings are shared across your game instances.
- Create, select, rename, and delete profiles. Assign **Next Key Profile** or **Previous Key Profile** to cycle through them with shortcuts.
- Set a profile to **Auto** for a world or server. Keyset activates it the next time you join.
- The interface follows Minecraft's GUI scale and adapts to smaller windows. Key labels use the Minecraft language, and you can select a keyboard layout manually.
- Keyset can manage Litematica and MaLiLib hotkeys when those mods are installed.

The menu is translated into Minecraft's available languages. English and Russian have been reviewed; other translations may contain errors.

## Profiles and transfer

Profiles are stored separately from the mod JAR in `config/keyset/profiles.json` in the shared Minecraft directory. By default, game instances on the same computer use the same file. Set a custom shared directory with the `KEYSET_CONFIG_DIR` environment variable or the `-Dkeyset.configDir=...` JVM option.

To use independent profiles, open the profile menu and switch **Profiles: shared across instances** to **Profiles: this instance only**. The first switch creates a separate copy; later switches restore that instance's saved profiles. Reconnecting to shared profiles keeps both sets without merging them. Local profiles are stored in the instance's `config/keyset/profiles-local.json`; its choice is saved in `profile-storage.properties`. Appearance settings remain shared.

To transfer profiles to another computer, choose **Export** in the profile menu. Copy the generated JSON files from `config/keyset/transfer/` to the other computer, then choose **Import**.

## Build from source

Use the Gradle Wrapper:

```powershell
.\gradlew.bat build
```

The JAR files are placed in `build/mods/`.

## License and credits

Keyset is distributed under the **ISC License**. The original ISC copyright and permission notice is retained in `LICENSE`. Keyboard layout data is distributed under a separate Unicode license; see `LICENSE-KEYBOARD-DATA.txt`.
