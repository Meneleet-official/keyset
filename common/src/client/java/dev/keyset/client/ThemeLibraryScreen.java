package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.profile.AppearancePreset;
import dev.keyset.profile.SavedTheme;
import dev.keyset.profile.ThemeStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lets players save, share, and apply custom keyboard themes. */
public final class ThemeLibraryScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final List<AppearancePreset> PRESETS = List.of(AppearancePreset.values());

    private final Screen parent;
    private final Runnable onApplied;
    private final List<ThemeRowButton> rowButtons = new ArrayList<>();

    private List<SavedTheme> themes = List.of();
    private final Map<String, ResourcePackThemes.Entry> resourceSources = new HashMap<>();
    private long resourceRevision;
    private String selectedId;
    private String nameDraft = "";
    private String lastExportPath;
    private Component statusMessage;
    private boolean statusIsError;
    private EditBox nameBox;
    private KeysetWidgets.FlatButton saveButton;
    private KeysetWidgets.FlatButton[] presetButtons = new KeysetWidgets.FlatButton[0];
    private KeysetWidgets.FlatButton exportButton, importButton, deleteButton, applyButton;
    private int panelX, panelY, panelWidth, panelHeight, innerX, innerWidth;
    private int nameInputX, nameInputY, nameInputWidth;
    private int listTop, listBottom, statusY, transferY, footerY, scroll, capacity;

    public ThemeLibraryScreen(Screen parent, Runnable onApplied) {
        super(Component.translatable("keyset.themes_title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.onApplied = Objects.requireNonNull(onApplied, "onApplied");
    }

    @Override
    protected void init() {
        KeysetClient.ensureLoaded();
        clearWidgets();
        rowButtons.clear();
        loadThemes(true);

        panelWidth = Math.max(1, Math.min(560, width - 16));
        panelHeight = Math.max(1, Math.min(430, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        innerX = panelX + 12;
        innerWidth = Math.max(1, panelWidth - 24);
        footerY = panelY + panelHeight - 26;
        transferY = footerY - 24;
        statusY = transferY - 15;
        nameInputY = panelY + 33 + ((PRESETS.size() + 1) / 2) * 21 + 3;
        listTop = nameInputY + 23;
        listBottom = statusY - 5;
        capacity = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = clampScroll(scroll);

        initPresets();
        initNameEditor();
        initThemeRows();
        initFooter();
        refreshRows();
    }

    private void initPresets() {
        int gap = 4;
        int buttonWidth = Math.max(1, (innerWidth - gap) / 2);
        int firstY = panelY + 33;
        presetButtons = new KeysetWidgets.FlatButton[PRESETS.size()];
        for (int index = 0; index < PRESETS.size(); index++) {
            AppearancePreset preset = PRESETS.get(index);
            int column = index % 2;
            int row = index / 2;
            int x = innerX + column * (buttonWidth + gap);
            int width = column == 1 ? innerX + innerWidth - x : buttonWidth;
            presetButtons[index] = addButton(Component.translatable(preset.translationKey()),
                    x, firstY + row * 21, width, 19, ignored -> applyPreset(preset),
                    KeysetWidgets.ButtonStyle.TAB);
            presetButtons[index].setTooltip(Tooltip.create(Component.translatable(preset.translationKey())));
            presetButtons[index].setSelected(preset.matches(KeysetClient.appearance()));
        }
    }

    private void initNameEditor() {
        int y = nameInputY;
        int gap = 4;
        int saveWidth = Math.min(88, Math.max(68, innerWidth / 3));
        int nameWidth = Math.max(1, innerWidth - saveWidth - gap);
        nameInputX = innerX;
        nameInputY = y;
        nameInputWidth = nameWidth;
        nameBox = new EditBox(font, nameInputX + 5, nameInputY + (20 - font.lineHeight) / 2,
                Math.max(1, nameInputWidth - 10), font.lineHeight,
                Component.translatable("keyset.themes_name_hint"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(64);
        nameBox.setTextColor(UiTheme.text());
        nameBox.setHint(Component.translatable("keyset.themes_name_hint"));
        nameBox.setTooltip(Tooltip.create(Component.translatable("keyset.themes_name_hint")));
        nameBox.setValue(nameDraft);
        nameBox.setResponder(value -> {
            nameDraft = value;
            if (saveButton != null) saveButton.active = !value.isBlank();
        });
        addRenderableWidget(nameBox);
        saveButton = addButton(Component.translatable("keyset.themes_save"),
                innerX + nameWidth + gap, y, saveWidth, 20, ignored -> saveCurrentTheme(),
                KeysetWidgets.ButtonStyle.PRIMARY);
        saveButton.active = !nameDraft.isBlank();
    }

    private void initThemeRows() {
        for (int slot = 0; slot < capacity; slot++) {
            int row = slot;
            ThemeRowButton button = addRenderableWidget(new ThemeRowButton(
                    innerX, listTop + slot * ROW_HEIGHT, innerWidth, ROW_HEIGHT - 2,
                    ignored -> selectThemeAt(row)));
            rowButtons.add(button);
        }
    }

    private void initFooter() {
        int gap = 4;
        int buttonWidth = Math.max(1, (innerWidth - gap * 2) / 3);
        int rightWidth = innerWidth - 2 * buttonWidth - 2 * gap;
        exportButton = addButton(Component.translatable("keyset.transfer_export"),
                innerX, transferY, buttonWidth, 20, ignored -> exportSelected(), KeysetWidgets.ButtonStyle.ACTION);
        importButton = addButton(Component.translatable("keyset.transfer_import"),
                innerX + buttonWidth + gap, transferY, buttonWidth, 20,
                ignored -> importTheme(), KeysetWidgets.ButtonStyle.ACTION);
        deleteButton = addButton(Component.translatable("keyset.profile_delete_short"),
                innerX + 2 * (buttonWidth + gap), transferY, rightWidth, 20,
                ignored -> confirmDeleteSelected(), KeysetWidgets.ButtonStyle.ACTION);
        exportButton.active = deleteButton.active = false;
        importButton.active = true;

        int half = Math.max(1, (innerWidth - gap) / 2);
        addButton(Component.translatable("keyset.compare_back"), innerX, footerY,
                half, 20, ignored -> onClose(), KeysetWidgets.ButtonStyle.ACTION);
        applyButton = addButton(Component.translatable("keyset.themes_apply"), innerX + half + gap,
                footerY, innerWidth - half - gap, 20,
                ignored -> applySelectedTheme(), KeysetWidgets.ButtonStyle.PRIMARY);
        applyButton.active = false;

        String transferDirectory = KeysetClient.themeTransferDirectory().toString();
        setImportTooltip(transferDirectory);
        if (lastExportPath != null) {
            exportButton.setTooltip(Tooltip.create(
                    Component.translatable("keyset.themes_exported", lastExportPath)));
        }
        if (statusIsError && statusMessage != null) {
            importButton.setTooltip(Tooltip.create(statusMessage));
            exportButton.setTooltip(Tooltip.create(statusMessage));
        }
    }

    private boolean loadThemes(boolean preserveSelection) {
        String previousSelection = preserveSelection ? selectedId : null;
        List<SavedTheme> combined = new ArrayList<>();
        boolean success = true;
        try {
            ThemeStore store = KeysetClient.themeStore();
            combined.addAll(store.themes());
        } catch (IOException | RuntimeException failure) {
            setError(failure);
            success = false;
        }
        resourceSources.clear();
        ResourcePackThemes.Snapshot resources = ResourcePackThemes.snapshot();
        resourceRevision = resources.revision();
        Set<String> usedIds = new HashSet<>(combined.stream().map(SavedTheme::id).toList());
        for (ResourcePackThemes.Entry entry : resources.entries()) {
            int discriminator = 0;
            String id;
            do { id = UUID.nameUUIDFromBytes(("keyset-resource-theme:" + entry.resourceId() + ":" + discriminator++)
                    .getBytes(StandardCharsets.UTF_8)).toString(); } while (!usedIds.add(id));
            SavedTheme theme = entry.theme();
            combined.add(new SavedTheme(id, theme.name(), theme.accentColor(), theme.panelColor(), theme.panelOpacity(),
                theme.boundKeyColor(), theme.conflictKeyColor(), theme.unboundKeyColor(), theme.mouseBodyColor()));
            resourceSources.put(id, entry);
        }
        themes = List.copyOf(combined);
        selectedId = previousSelection != null && themes.stream()
            .anyMatch(theme -> theme.id().equals(previousSelection)) ? previousSelection : null;
        return success;
    }

    private void refreshRows() {
        scroll = clampScroll(scroll);
        for (int slot = 0; slot < rowButtons.size(); slot++) {
            int index = scroll + slot;
            ThemeRowButton button = rowButtons.get(slot);
            button.visible = index < themes.size();
            if (!button.visible) {
                button.setTheme(null);
                if (button.isFocused()) {
                    button.setFocused(false);
                    if (getFocused() == button) setFocused(null);
                }
                continue;
            }
            SavedTheme theme = themes.get(index);
            button.setTheme(theme);
            button.setSelected(theme.id().equals(selectedId));
            button.setTooltip(Tooltip.create(themeTooltip(theme)));
            button.setTooltipDelay(java.time.Duration.ofMillis(500));
        }
        SavedTheme selected = selectedTheme();
        boolean hasSelection = selected != null;
        if (exportButton != null) exportButton.active = hasSelection;
        if (deleteButton != null) deleteButton.active = hasSelection;
        if (deleteButton != null) {
            boolean resource = selected != null && resourceSources.containsKey(selected.id());
            deleteButton.setMessage(Component.translatable(resource ? "keyset.themes_copy" : "keyset.profile_delete_short"));
            deleteButton.setTooltip(Tooltip.create(Component.translatable(resource ? "keyset.themes_copy_hint" : "keyset.profile_delete_short")));
        }
        if (applyButton != null) applyButton.active = hasSelection;
    }

    private int clampScroll(int requested) {
        return Math.max(0, Math.min(requested, Math.max(0, themes.size() - capacity)));
    }

    private void ensureVisible(String id) {
        int index = -1;
        for (int i = 0; i < themes.size(); i++) {
            if (themes.get(i).id().equals(id)) { index = i; break; }
        }
        if (index < 0) return;
        if (index < scroll) scroll = index;
        else if (index >= scroll + capacity) scroll = index - capacity + 1;
    }

    private void saveCurrentTheme() {
        if (nameBox == null || nameBox.getValue().isBlank()) return;
        try {
            ThemeStore store = KeysetClient.themeStore();
            SavedTheme captured = SavedTheme.capture(nameBox.getValue(), KeysetClient.appearance());
            SavedTheme existing = store.themes().stream()
                    .filter(theme -> theme.name().equalsIgnoreCase(captured.name()))
                    .findFirst().orElse(null);
            SavedTheme theme = existing == null ? captured : new SavedTheme(existing.id(), captured.name(),
                    captured.accentColor(), captured.panelColor(), captured.panelOpacity(),
                    captured.boundKeyColor(), captured.conflictKeyColor(), captured.unboundKeyColor(), captured.mouseBodyColor());
            store.save(theme);
            selectedId = theme.id();
            if (!reloadThemes()) return;
            resetTransferTooltips();
            setStatus(Component.translatable("keyset.themes_saved"), false);
        } catch (IOException | RuntimeException failure) {
            setError(failure);
        }
    }

    private void applyPreset(AppearancePreset preset) {
        try {
            KeysetClient.applySavedTheme(SavedTheme.capture("preset", preset.applyTo(KeysetClient.appearance())));
            onApplied.run();
            minecraft.setScreenAndShow(parent);
        } catch (IOException | RuntimeException failure) {
            setError(failure);
        }
    }

    private void applySelectedTheme() {
        SavedTheme theme = selectedTheme();
        if (theme == null) return;
        try {
            ResourcePackThemes.Entry source = resourceSources.get(theme.id());
            if (source != null) KeysetClient.applyResourceTheme(source.resourceId(), source.theme());
            else KeysetClient.applySavedTheme(theme);
            onApplied.run();
            minecraft.setScreenAndShow(parent);
        } catch (IOException | RuntimeException failure) {
            setError(failure);
        }
    }

    private void exportSelected() {
        SavedTheme theme = selectedTheme();
        if (theme == null) return;
        Path destination = null;
        try {
            String json = resourceSources.containsKey(theme.id())
                ? ThemeStore.encodeTheme(resourceSources.get(theme.id()).theme())
                : KeysetClient.themeStore().exportTheme(theme.id());
            Path directory = KeysetClient.themeTransferDirectory();
            Files.createDirectories(directory);
            destination = directory.resolve("keyset-theme-" + UUID.randomUUID() + ".json");
            writeNewExport(destination, json);
            lastExportPath = destination.toString();
            Component exported = Component.translatable("keyset.themes_exported", destination.toString());
            exportButton.setTooltip(Tooltip.create(exported));
            try {
                minecraft.keyboardHandler.setClipboard(json);
            } catch (RuntimeException clipboardFailure) {
                Component message = KeysetClient.operationError(clipboardFailure).copy().append("\n").append(exported);
                setStatus(message, true);
                exportButton.setTooltip(Tooltip.create(exported));
                return;
            }
            setStatus(exported, false);
        } catch (IOException | RuntimeException failure) {
            setError(failure);
        }
    }

    private void importTheme() {
        Set<String> knownIds = Set.of();
        try {
            String clipboard = minecraft.keyboardHandler.getClipboard();
            ThemeStore store = KeysetClient.themeStore();
            knownIds = Set.copyOf(store.themes().stream().map(SavedTheme::id)
                    .collect(java.util.stream.Collectors.toSet()));
            if (looksLikeJson(clipboard)) {
                selectedId = store.importTheme(clipboard).id();
            } else {
                Path directory = KeysetClient.themeTransferDirectory();
                if (!Files.isDirectory(directory)) {
                    setStatus(Component.translatable("keyset.themes_no_import").append(" ")
                            .append(Component.translatable("keyset.themes_import_hint", directory.toString())), false);
                    return;
                }
                ThemeStore.ImportResult result = store.importDirectory(directory);
                Set<String> knownBeforeImport = knownIds;
                SavedTheme selection = result.imported().stream().filter(theme -> !knownBeforeImport.contains(theme.id()))
                        .reduce((first, next) -> next).orElseGet(() -> result.imported().isEmpty()
                                ? null : result.imported().get(result.imported().size() - 1));
                if (selection != null) selectedId = selection.id();
                if (result.imported().isEmpty() && result.skipped() == 0) {
                    setStatus(Component.translatable("keyset.themes_no_import").append(" ")
                            .append(Component.translatable("keyset.themes_import_hint", directory.toString())), false);
                    return;
                }

                if (!reloadThemes()) return;
                resetTransferTooltips();
                Component summary = Component.translatable("keyset.themes_import_summary",
                        Integer.toString(result.imported().size()), Integer.toString(result.skipped()));
                setStatus(summary, result.skipped() > 0);
                if (!result.errors().isEmpty()) {
                    importButton.setTooltip(Tooltip.create(Component.literal(String.join("\n", result.errors()))));
                }
                return;
            }

            if (!reloadThemes()) return;
            resetTransferTooltips();
            setStatus(Component.translatable("keyset.themes_import_summary", "1", "0"), false);
        } catch (IOException | RuntimeException failure) {
            reloadThemes();
            Set<String> knownBeforeFailure = knownIds;
            SavedTheme partialNewImport = themes.stream().filter(theme -> !knownBeforeFailure.contains(theme.id()))
                    .reduce((first, next) -> next).orElse(null);
            if (partialNewImport != null) {
                selectedId = partialNewImport.id();
                refreshRows();
            }
            setError(failure);
        }
    }

    private void confirmDeleteSelected() {
        SavedTheme theme = selectedTheme();
        if (theme == null) return;
        ResourcePackThemes.Entry source = resourceSources.get(theme.id());
        if (source != null) {
            try {
                SavedTheme copy = KeysetClient.themeStore().importTheme(ThemeStore.encodeTheme(source.theme()));
                selectedId = copy.id();
                if (reloadThemes()) setStatus(Component.translatable("keyset.themes_copied"), false);
            } catch (IOException | RuntimeException failure) { setError(failure); }
            return;
        }
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            minecraft.setScreenAndShow(this);
            if (confirmed) deleteTheme(theme);
        }, Component.translatable("keyset.themes_delete_title"),
                Component.translatable("keyset.themes_delete_prompt", theme.name()),
                Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
    }

    private void deleteTheme(SavedTheme theme) {
        try {
            KeysetClient.themeStore().remove(theme.id());
            selectedId = null;
            if (!reloadThemes()) return;
            statusMessage = null;
            statusIsError = false;
            resetTransferTooltips();
        } catch (IOException | RuntimeException failure) {
            setError(failure);
        }
    }

    private boolean reloadThemes() {
        boolean loaded = loadThemes(true);
        ensureVisible(selectedId);
        refreshRows();
        return loaded;
    }

    private SavedTheme selectedTheme() {
        if (selectedId == null) return null;
        return themes.stream().filter(theme -> theme.id().equals(selectedId)).findFirst().orElse(null);
    }

    private void selectTheme(SavedTheme theme) {
        selectedId = theme == null ? null : theme.id();
        statusMessage = null;
        statusIsError = false;
        resetTransferTooltips();
        refreshRows();
    }

    private void setStatus(Component message, boolean error) {
        statusMessage = message;
        statusIsError = error;
        if (importButton != null) {
            if (error) importButton.setTooltip(Tooltip.create(message));
            else setImportTooltip(KeysetClient.themeTransferDirectory().toString());
        }
    }

    private void setError(Exception failure) {
        setStatus(KeysetClient.operationError(failure), true);
        if (exportButton != null) exportButton.setTooltip(Tooltip.create(statusMessage));
    }

    private void setImportTooltip(String directory) {
        importButton.setTooltip(Tooltip.create(Component.translatable("keyset.themes_import_hint", directory)));
    }

    private void resetTransferTooltips() {
        lastExportPath = null;
        if (exportButton != null) exportButton.setTooltip(Tooltip.create(Component.translatable("keyset.transfer_export")));
        if (importButton != null) setImportTooltip(KeysetClient.themeTransferDirectory().toString());
    }

    private static boolean looksLikeJson(String value) {
        if (value == null) return false;
        String trimmed = value.stripLeading();
        return trimmed.startsWith("{") && trimmed.contains("\"format\"")
                && trimmed.contains("\"keyset-theme\"");
    }

    private static void writeNewExport(Path file, String json) throws IOException {
        boolean created = false;
        try {
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                writer.write(json);
            }
        } catch (IOException | RuntimeException failure) {
            if (created) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private Component themeTooltip(SavedTheme theme) {
        Component result = Component.literal(theme.name() + "\n"
                + "#" + hex(theme.accentColor()) + "  #" + hex(theme.panelColor()) + "  "
                + theme.panelOpacity() + "%\n"
                + "#" + hex(theme.boundKeyColor()) + "  #" + hex(theme.conflictKeyColor()) + "  #"
                + hex(theme.unboundKeyColor()));
        ResourcePackThemes.Entry source = resourceSources.get(theme.id());
        if (source != null) result = result.copy().append("\n")
            .append(Component.translatable("keyset.themes_resource_source", source.packName(), source.resourceId()))
            .append("\n").append(Component.translatable("keyset.themes_copy_hint"));
        return result;
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0xFFFFFF);
    }

    private KeysetWidgets.FlatButton addButton(Component label, int x, int y, int width, int height,
                                               Button.OnPress action, KeysetWidgets.ButtonStyle style) {
        return addRenderableWidget(KeysetWidgets.flatButton(x, y, width, height, label, action, style));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < innerX || mouseX > innerX + innerWidth || mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = clampScroll(scroll - (int) Math.signum(scrollY));
        if (next == scroll) return true;
        scroll = next;
        refreshRows();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, innerX, panelY + 9, innerWidth, UiTheme.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_presets_label"),
                innerX, panelY + 22, innerWidth, UiTheme.muted());
        graphics.fill(innerX, listTop - 3, innerX + innerWidth, listTop - 2, UiTheme.border());
        graphics.fill(nameInputX, nameInputY, nameInputX + nameInputWidth, nameInputY + 20,
                UiTheme.widgetSurface(UiTheme.control()));
        graphics.outline(nameInputX, nameInputY, nameInputWidth, 20,
                nameBox != null && nameBox.isFocused() ? UiTheme.accent() : UiTheme.border());
        if (themes.isEmpty()) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.themes_empty"),
                    innerX + 5, listTop + 6, Math.max(1, innerWidth - 10), UiTheme.muted());
        }
        if (themes.size() > capacity) {
            int trackHeight = capacity * ROW_HEIGHT - 2;
            int thumbHeight = Math.max(12, trackHeight * capacity / themes.size());
            int thumbY = listTop + (trackHeight - thumbHeight) * scroll / (themes.size() - capacity);
            int trackX = panelX + panelWidth - 6;
            graphics.fill(trackX, listTop, trackX + 2, listTop + trackHeight, UiTheme.border());
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, UiTheme.muted());
        }
        if (statusMessage != null) {
            KeysetWidgets.drawText(graphics, font, statusMessage, innerX, statusY,
                    innerWidth, statusIsError ? UiTheme.red() : UiTheme.muted());
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (ResourcePackThemes.snapshot().revision() != resourceRevision) {
            loadThemes(true);
            refreshRows();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private final class ThemeRowButton extends Button {
        private SavedTheme theme;
        private boolean selected;

        private ThemeRowButton(int x, int y, int width, int height, Button.OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        private void setTheme(SavedTheme theme) {
            this.theme = theme;
            setMessage(theme == null ? Component.empty() : Component.literal(theme.name())
                .append(resourceSources.containsKey(theme.id()) ? Component.translatable("keyset.themes_resource_badge") : Component.empty()));
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (theme == null) return;
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            boolean hover = isHoveredOrFocused() && active;
            if (selected || hover) graphics.fill(x, y, x + width, y + height,
                    selected ? UiTheme.selected() : UiTheme.control());
            graphics.fill(x + 5, y + height - 1, x + width - 5, y + height,
                    selected ? UiTheme.accent() : hover ? UiTheme.border() : UiTheme.row());
            if (active && isFocused()) graphics.outline(x, y, width, height, UiTheme.text());

            int chipSize = 13;
            int chipGap = 3;
            int chipsWidth = chipSize * 5 + chipGap * 4;
            int chipStart = x + width - 5 - chipsWidth;
            int opacityX = chipStart - 39;
            int nameWidth = Math.max(1, opacityX - x - 14);
            String name = getMessage().getString();
            if (font.width(name) > nameWidth) {
                name = font.plainSubstrByWidth(name, Math.max(1, nameWidth - font.width("…"))) + "…";
            }
            int textColor = selected ? UiTheme.accentText() : UiTheme.text();
            graphics.text(font, name, x + 6, y + (height - font.lineHeight) / 2, textColor, false);
            graphics.text(font, theme.panelOpacity() + "%", opacityX,
                    y + (height - font.lineHeight) / 2, UiTheme.muted(), false);
            int[] colors = {theme.accentColor(), theme.panelColor(), theme.boundKeyColor(),
                    theme.conflictKeyColor(), theme.unboundKeyColor()};
            for (int index = 0; index < colors.length; index++) {
                int colorX = chipStart + index * (chipSize + chipGap);
                graphics.fill(colorX, y + (height - chipSize) / 2, colorX + chipSize,
                        y + (height + chipSize) / 2, 0xFF000000 | colors[index]);
                graphics.outline(colorX, y + (height - chipSize) / 2, chipSize, chipSize, UiTheme.border());
            }
        }
    }

    private void selectThemeAt(int slot) {
        int index = scroll + slot;
        if (index < 0 || index >= themes.size()) return;
        selectTheme(themes.get(index));
    }
}
