package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.compat.ModOrigins;
import dev.keyset.editor.ActionSearch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lets users hide action groups from the editor without changing their bindings. */
public final class HiddenSectionsScreen extends Screen {
    private static final int ROW_HEIGHT = 26;

    private final Screen parent;
    private Tab tab = Tab.MODS;
    private String searchQuery = "";
    private List<SectionEntry> modEntries = List.of();
    private List<SectionEntry> categoryEntries = List.of();
    private List<SectionEntry> entries = List.of();
    private List<SectionEntry> filteredEntries = List.of();
    private final List<KeysetWidgets.FlatButton> toggleButtons = new ArrayList<>();
    private EditBox searchBox;
    private KeysetWidgets.FlatButton modsTab, categoriesTab;
    private Component error;
    private int panelX, panelY, panelWidth, panelHeight, innerX, innerWidth;
    private int listTop, listBottom, footerY, scroll, capacity;

    public HiddenSectionsScreen(Screen parent) {
        super(Component.translatable("keyset.visibility_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KeysetClient.ensureLoaded();
        clearWidgets();
        toggleButtons.clear();
        panelWidth = Math.max(1, Math.min(620, width - 16));
        panelHeight = Math.max(1, Math.min(420, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        innerX = panelX + 12;
        innerWidth = Math.max(1, panelWidth - 24);
        footerY = panelY + panelHeight - 28;

        int gap = 4;
        int tabWidth = Math.max(1, (innerWidth - gap) / 2);
        modsTab = addButton(tabLabel(Tab.MODS), innerX, panelY + 34, tabWidth, 18,
                ignored -> selectTab(Tab.MODS), KeysetWidgets.ButtonStyle.TAB);
        categoriesTab = addButton(tabLabel(Tab.CATEGORIES), innerX + tabWidth + gap, panelY + 34,
                innerWidth - tabWidth - gap, 18, ignored -> selectTab(Tab.CATEGORIES), KeysetWidgets.ButtonStyle.TAB);
        modsTab.setSelected(tab == Tab.MODS);
        categoriesTab.setSelected(tab == Tab.CATEGORIES);

        searchBox = new EditBox(font, innerX, panelY + 58, innerWidth, 18,
                Component.translatable("keyset.visibility_search_hint"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(ActionSearch.MAX_QUERY_LENGTH);
        searchBox.setTextColor(UiTheme.text());
        searchBox.setHint(Component.translatable("keyset.visibility_search_hint"));
        searchBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("keyset.visibility_search_syntax_tip")));
        searchBox.setTooltipDelay(java.time.Duration.ofMillis(750));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            scroll = 0;
            rebuildFilteredEntries();
            refreshRows();
        });
        addRenderableWidget(searchBox);

        listTop = panelY + 82;
        listBottom = footerY - 10;
        capacity = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filteredEntries.size() - capacity)));
        for (int slot = 0; slot < capacity; slot++) {
            final int row = slot;
            int toggleWidth = Math.min(84, Math.max(62, innerWidth / 3));
            int buttonX = innerX + innerWidth - toggleWidth;
            toggleButtons.add(addButton(Component.empty(), buttonX, listTop + slot * ROW_HEIGHT + 2,
                    toggleWidth, 20, ignored -> toggleAt(row), KeysetWidgets.ButtonStyle.TAB));
        }

        int footerGap = 4;
        int footerWidth = Math.max(1, (innerWidth - footerGap) / 2);
        addButton(Component.translatable("keyset.visibility_restore_all"), innerX, footerY,
                footerWidth, 20, ignored -> restoreAll());
        addButton(Component.translatable("keyset.done"), innerX + footerWidth + footerGap, footerY,
                innerWidth - footerWidth - footerGap, 20, ignored -> onClose());

        rebuildEntries();
        rebuildFilteredEntries();
        refreshRows();
    }

    private void rebuildEntries() {
        List<KeyMapping> mappings = KeysetClient.mappings();
        Map<String, ModGroup> mods = new LinkedHashMap<>();
        Map<String, CategoryGroup> categories = new LinkedHashMap<>();
        for (KeyMapping mapping : mappings) {
            ModOrigins.Owner owner = KeysetClient.owner(mapping);
            ModGroup previousMod = mods.get(owner.id());
            mods.put(owner.id(), new ModGroup(owner.id(), owner.name(), previousMod == null ? 1 : previousMod.count() + 1));

            KeyMapping.Category category = mapping.getCategory();
            String categoryId = category.id().toString();
            CategoryGroup previousCategory = categories.get(categoryId);
            categories.put(categoryId, new CategoryGroup(categoryId, KeysetClient.categoryLabel(category),
                    previousCategory == null ? 1 : previousCategory.count() + 1));
        }
        modEntries = mods.values().stream()
                .sorted(Comparator.comparing(ModGroup::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ModGroup::id, String.CASE_INSENSITIVE_ORDER))
                .map(group -> new SectionEntry(group.id(), Component.literal(group.name()), group.count(), true))
                .toList();
        categoryEntries = categories.values().stream()
                .sorted(Comparator.comparing((CategoryGroup group) -> group.name().getString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CategoryGroup::id, String.CASE_INSENSITIVE_ORDER))
                .map(group -> new SectionEntry(group.id(), group.name(), group.count(), false))
                .toList();
        entries = tab == Tab.MODS ? modEntries : categoryEntries;
    }

    private void rebuildFilteredEntries() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.startsWith("#")) tab = Tab.CATEGORIES;
        else if (query.startsWith("@")) tab = Tab.MODS;
        entries = tab == Tab.MODS ? modEntries : categoryEntries;
        modsTab.setSelected(tab == Tab.MODS);
        categoriesTab.setSelected(tab == Tab.CATEGORIES);
        ActionSearch.Query parsed = ActionSearch.parse(query);
        filteredEntries = entries.stream().filter(entry -> parsed.matches(entry.search())).toList();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filteredEntries.size() - capacity)));
    }

    private void refreshRows() {
        modsTab.setMessage(tabLabel(Tab.MODS));
        categoriesTab.setMessage(tabLabel(Tab.CATEGORIES));

        for (int slot = 0; slot < toggleButtons.size(); slot++) {
            int index = scroll + slot;
            KeysetWidgets.FlatButton button = toggleButtons.get(slot);
            button.visible = index < filteredEntries.size();
            if (!button.visible) continue;
            SectionEntry entry = filteredEntries.get(index);
            boolean hidden = isHidden(entry);
            button.setMessage(Component.translatable(hidden ? "keyset.visibility_show" : "keyset.visibility_hide"));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(entry.name().copy()
                    .append(" · " + entry.id()).append("\n").append(Component.translatable(
                    hidden ? "keyset.visibility_show_tip" : "keyset.visibility_hide_tip"))));
            button.setSelected(hidden);
        }
    }

    private Component tabLabel(Tab value) {
        List<SectionEntry> source = value == Tab.MODS ? modEntries : categoryEntries;
        int hidden = 0;
        for (SectionEntry entry : source) if (isHidden(entry)) hidden++;
        return Component.translatable(value == Tab.MODS ? "keyset.visibility_mods_count" : "keyset.visibility_categories_count",
                Integer.toString(hidden), Integer.toString(source.size()));
    }

    private boolean isHidden(SectionEntry entry) {
        return entry.mod() ? KeysetClient.isModHidden(entry.id()) : KeysetClient.isCategoryHidden(entry.id());
    }

    private void toggleAt(int row) {
        int index = scroll + row;
        if (index < 0 || index >= filteredEntries.size()) return;
        SectionEntry entry = filteredEntries.get(index);
        try {
            boolean hidden = isHidden(entry);
            if (entry.mod()) KeysetClient.setModHidden(entry.id(), !hidden);
            else KeysetClient.setCategoryHidden(entry.id(), !hidden);
            error = null;
            refreshRows();
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
        }
    }

    private void restoreAll() {
        try {
            KeysetClient.showAllSections();
            error = null;
            refreshRows();
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
        }
    }

    private void selectTab(Tab value) {
        if (tab == value) return;
        String query = searchQuery.stripLeading();
        if (query.startsWith("#") || query.startsWith("@")) {
            searchQuery = (value == Tab.MODS ? "@" : "#") + query.substring(1);
        }
        tab = value;
        scroll = 0;
        rebuildEntries();
        rebuildFilteredEntries();
        init();
    }

    private KeysetWidgets.FlatButton addButton(Component label, int x, int y, int width, int height,
                                               Button.OnPress action) {
        return addButton(label, x, y, width, height, action, KeysetWidgets.ButtonStyle.ACTION);
    }

    private KeysetWidgets.FlatButton addButton(Component label, int x, int y, int width, int height,
                                               Button.OnPress action, KeysetWidgets.ButtonStyle style) {
        return addRenderableWidget(KeysetWidgets.flatButton(x, y, width, height, label, action, style));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < listTop || mouseY > listBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int next = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY),
                Math.max(0, filteredEntries.size() - capacity)));
        if (next == scroll) return true;
        scroll = next;
        refreshRows();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, innerX, panelY + 10, innerWidth, UiTheme.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.visibility_explanation"),
                innerX, panelY + 24, innerWidth, UiTheme.muted());
        graphics.fill(innerX, panelY + 55, innerX + innerWidth, panelY + 77, UiTheme.control());

        if (entries.isEmpty()) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.visibility_empty"),
                    innerX, listTop + 8, innerWidth, UiTheme.muted());
        }
        for (int slot = 0; slot < capacity; slot++) {
            int index = scroll + slot;
            if (index >= filteredEntries.size()) break;
            SectionEntry entry = filteredEntries.get(index);
            int rowY = listTop + slot * ROW_HEIGHT;
            boolean hidden = isHidden(entry);
            graphics.fill(innerX, rowY, innerX + innerWidth, rowY + ROW_HEIGHT - 2,
                    slot % 2 == 0 ? UiTheme.row() : UiTheme.rowAlternate());
            int textWidth = Math.max(1, innerWidth - Math.min(94, Math.max(70, innerWidth / 3)) - 12);
            KeysetWidgets.drawText(graphics, font, entry.name(), innerX + 6, rowY + 2, textWidth,
                    hidden ? UiTheme.muted() : UiTheme.text());
            Component details = Component.translatable("keyset.visibility_action_count", Integer.toString(entry.count()));
            KeysetWidgets.drawText(graphics, font, details, innerX + 6, rowY + 14, textWidth, UiTheme.muted());
        }
        if (filteredEntries.isEmpty() && !entries.isEmpty()) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.visibility_no_results"),
                    innerX, listTop + 8, innerWidth, UiTheme.muted());
        }
        if (filteredEntries.size() > capacity) {
            int trackHeight = capacity * ROW_HEIGHT - 2;
            int thumbHeight = Math.max(12, trackHeight * capacity / filteredEntries.size());
            int thumbY = listTop + (trackHeight - thumbHeight) * scroll / (filteredEntries.size() - capacity);
            int trackX = panelX + panelWidth - 6;
            graphics.fill(trackX, listTop, trackX + 2, listTop + trackHeight, UiTheme.border());
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, UiTheme.muted());
        }
        if (error != null) {
            KeysetWidgets.drawText(graphics, font, error, innerX, footerY - 14, innerWidth, UiTheme.red());
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private enum Tab { MODS, CATEGORIES }
    private record SectionEntry(String id, Component name, int count, boolean mod, ActionSearch.Document search) {
        private SectionEntry(String id, Component name, int count, boolean mod) {
            this(id, name, count, mod, new ActionSearch.Document("", "", mod ? "" : name.getString(),
                mod ? "" : id, mod ? name.getString() : "", mod ? id : "", "", "", ""));
        }
    }
    private record ModGroup(String id, String name, int count) {}
    private record CategoryGroup(String id, Component name, int count) {}
}
