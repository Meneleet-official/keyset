# Keyset resource-pack themes

Enable this folder or its ZIP as a Minecraft resource pack, then open Keyset -> Style -> Colors -> Themes and apply **Pack Black [Pack]**.

Edit `assets/keyset/themes/black.json` to change the colors. Colors are decimal RGB integers; `panelOpacity` is a percentage from 15 to 90. Keep the `format`, `schemaVersion`, and valid UUID `theme.id` fields. Standard Keyset theme exports can be placed here unchanged, with a lowercase `.json` filename.

After editing the file, use Minecraft's resource reload (F3+T). The selected resource-pack theme updates automatically. Selection follows the resource path, so keep the filename stable when changing its colors or name.

Themes with different paths are listed separately, even if their names or UUIDs match. If multiple packs provide the same path, the highest-priority resource pack wins. Invalid themes are skipped and logged. Removing or disabling a selected pack retains its last applied colors; enabling it again restores automatic updates.

In Keyset, **Copy** creates an independent saved theme. Select and apply that copy before editing it. Applying a saved theme or preset, or manually changing a color/opacity, stops following the resource pack. Keyboard/mouse size, legends, bottom-row layout, and bindings are not supplied by the pack.

This pack targets Minecraft 26.3's resource format 97.1. Change `pack.mcmeta` as needed when using it with a different game version.
