package dev.keyset.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import dev.keyset.editor.BindingIndex;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.ChordCapture;
import dev.keyset.editor.EditorViewport;
import dev.keyset.profile.Binding;
import dev.keyset.profile.Profile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Profile-aware key binding editor. */
public final class KeysetScreen extends Screen {
    private static final Set<String> COLLAPSED_SECTIONS = new HashSet<>();
    private static final int PANEL = 0x58000000;
    private static final int PANEL_LIGHT = 0x78000000;
    private static final int BORDER = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB2B7B5;
    private static final int TEAL = 0xFF94E4D3;
    private static final int RED = 0xFFFF8A8A;
    private static final int CONTROL = 0x90000000;
    private static final int HOVER = 0xE0000000;
    private static final int SELECTED = 0xA0152622;
    private static final int ROW = 0x30000000;
    private static final int ROW_ALTERNATE = 0x18000000;
    private static final int KEY_SHADOW = 0xD0080A09;
    private static final int KEY_FACE = 0xE0181B1A;
    private static final int KEY_EDGE = 0xFF5C6561;
    private int rowHeight = 25;
    private static final String[][] PHYSICAL_LETTERS = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l", "semicolon"},
        {"z", "x", "c", "v", "b", "n", "m", "comma", "period", "slash"}
    };
    private static final int[][] LETTER_CODES = {
        {InputConstants.KEY_Q, InputConstants.KEY_W, InputConstants.KEY_E, InputConstants.KEY_R, InputConstants.KEY_T,
            InputConstants.KEY_Y, InputConstants.KEY_U, InputConstants.KEY_I, InputConstants.KEY_O, InputConstants.KEY_P},
        {InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F, InputConstants.KEY_G,
            InputConstants.KEY_H, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_SEMICOLON},
        {InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C, InputConstants.KEY_V,
            InputConstants.KEY_B, InputConstants.KEY_N, InputConstants.KEY_M, InputConstants.KEY_COMMA,
            InputConstants.KEY_PERIOD, InputConstants.KEY_SLASH}
    };

    private final Screen parent;
    private EditBox searchBox;
    private Button searchClearButton;
    private Button allCategoryButton, categoryFilterButton, modFilterButton;
    private Button conflictsButton, favoritesButton, keyboardTabButton, mouseTabButton;
    private Button profileCycleButton, newProfileButton, renameProfileButton, deleteProfileButton;
    private Button layoutButton, unbindButton, resetButton, resetAllButton, undoButton, doneButton;
    private Button ctrlButton, shiftButton, altButton, deselectButton;
    private Button compactStatusButton;
    private final List<Button> filterChipButtons = new ArrayList<>();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<Button> rowNameButtons = new ArrayList<>();
    private final List<Button> rowBindingButtons = new ArrayList<>();
    private final List<Button> rowUnbindButtons = new ArrayList<>();
    private final List<Button> rowConflictButtons = new ArrayList<>();
    private final List<Button> rowFavoriteButtons = new ArrayList<>();
    private final List<Button> rowSectionButtons = new ArrayList<>();
    private final Map<Button, VirtualKey> keyboardButtonKeys = new IdentityHashMap<>();
    private List<KeyMapping> filteredMappings = List.of();
    private List<KeyMapping> allMappings = List.of();
    private List<ActionListEntry> displayEntries = List.of();
    private BindingIndex<KeyMapping> bindingIndex;
    private final Map<KeyMapping, MappingView> mappingViews = new IdentityHashMap<>();
    private final Map<KeyMapping.Category, Integer> categoryCounts = new HashMap<>();
    private FilterState appliedFilter;
    private KeyboardLayouts.Layout keyboardLayout;
    private List<KeyMapping.Category> categories = List.of();
    private List<ModFilter> mods = List.of();
    private KeyMapping.Category category;
    private String modFilter;
    private KeyMapping selectedMapping;
    private int bindingScroll, categoryScroll, modScroll;
    private int collapseRevision;
    private int layoutIndex = -1;
    private boolean modFilterMode, conflictsOnly, favoritesOnly, capturing, settingVirtualKeyFilter, mouseMode;
    private boolean virtualCtrl, virtualShift, virtualAlt;
    private String searchQuery = "";
    private String virtualKeyFilter;
    private InputConstants.Key pendingModifier;
    private final ChordCapture nativeCapture = new ChordCapture();
    private Component statusMessage;
    private boolean statusIsError;
    private int sidebarX, sidebarWidth, mainX, mainWidth;
    private int searchY, listTitleY, listTop, listBottom;
    private int keyboardPanelY, keyStep, keyHeight, inputCardBottom, categoryCardY, summaryY;
    private int categoryListTop, categoryListBottom, keyboardUnit;
    private EditorViewport viewport;
    private int mouseBodyX, mouseBodyY, mouseBodyWidth, mouseBodyHeight;
    private int draggedDividerX;
    private long observedRevision = Long.MIN_VALUE;
    private String observedSharedError = "";
    private boolean dividerDragging;
    private boolean compact;
    private boolean singlePane;
    private EditorViewport.Page compactPage = EditorViewport.Page.ACTIONS;
    private final List<Button> compactTabs = new ArrayList<>();

    public KeysetScreen(Screen parent) {
        super(Component.translatable("keyset.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KeysetClient.ensureLoaded();
        observedRevision = KeysetClient.revision();
        if (statusMessage == null && KeysetClient.error() != null && KeysetClient.error().startsWith("keyset.error.")) {
            statusMessage = Component.translatable(KeysetClient.error());
            statusIsError = !KeysetClient.error().endsWith("recovered");
        }
        clearWidgets();
        categoryButtons.clear();
        rowNameButtons.clear();
        rowBindingButtons.clear();
        rowUnbindButtons.clear();
        rowConflictButtons.clear();
        rowFavoriteButtons.clear();
        rowSectionButtons.clear();
        keyboardButtonKeys.clear();
        compactTabs.clear();
        viewport = EditorViewport.forSize(width, height);
        singlePane = viewport.singlePane();
        rowHeight = viewport.rowHeight();
        compact = singlePane;
        sidebarX = singlePane ? 8 : 16;
        int availablePanels = Math.max(1, width - 44);
        int[] minimumWidths = minimumPanelWidths(availablePanels);
        int minimumSidebar = minimumWidths[0];
        int minimumMain = minimumWidths[1];
        double ratio = Math.max(0.35, Math.min(0.65, KeysetClient.sidebarRatio()));
        sidebarWidth = Math.max(minimumSidebar, Math.min(availablePanels - minimumMain,
            (int) Math.round(availablePanels * ratio)));
        mainX = sidebarX + sidebarWidth + 12;
        mainWidth = Math.max(1, width - mainX - 16);
        if (singlePane) {
            mainX = sidebarX;
            mainWidth = sidebarWidth = Math.max(1, width - 16);
        }
        keyboardPanelY = viewport.keyboardPanelY();
        keyStep = viewport.keyStep();
        keyHeight = Math.max(6, keyStep - 2);
        inputCardBottom = singlePane ? viewport.rowsBottom() : 312;
        summaryY = singlePane ? viewport.inputControlsY() - 16 : inputCardBottom - 44;
        categoryCardY = singlePane ? viewport.contentTop() : inputCardBottom + 12;
        categoryListTop = viewport.categoryListTop();
        categoryListBottom = viewport.categoryListBottom();
        searchY = viewport.searchY();
        listTitleY = 78;
        listTop = viewport.rowsTop();
        listBottom = viewport.rowsBottom();
        if (singlePane) {
            listTop = viewport.actionRowsTop(hasActiveFilterChips());
        }
        keyboardLayout = layoutIndex < 0 ? KeyboardLayouts.forLanguage(minecraft.getLanguageManager().getSelected())
            : KeyboardLayouts.manual(layoutIndex);
        rebuildModel();
        if (category != null && !categories.contains(category)) category = null;
        initProfileBar();
        initMainControls();
        initFilterChips();
        initCategoryButtons();
        initRowSlots();
        initFooter();
        initInputButtons();
        initSelectionControls();
        if (singlePane) initCompactTabs();
        refreshRows();
        refreshProfileButtons();
    }

    private void initCompactTabs() {
        int tabWidth = Math.max(1, (width - 24) / 3);
        for (EditorViewport.Page page : EditorViewport.Page.values()) {
            Button tab = styled(addButton(Component.translatable("keyset.compact_" + page.name().toLowerCase(Locale.ROOT)),
                sidebarX + page.ordinal() * (tabWidth + 4), viewport.tabsY(), tabWidth, 18,
                ignored -> { compactPage = page; refreshRows(); }), ButtonStyle.TAB, true);
            compactTabs.add(tab);
        }
        keyboardTabButton.setY(viewport.categoryControlsY());
        mouseTabButton.setY(viewport.categoryControlsY());
        keyboardTabButton.setHeight(viewport.controlHeight());
        mouseTabButton.setHeight(viewport.controlHeight());
        layoutButton.setY(viewport.categoryControlsY());
        layoutButton.setHeight(viewport.controlHeight());
        for (Button button : List.of(ctrlButton, shiftButton, altButton, deselectButton)) {
            button.setY(viewport.inputControlsY());
            button.setHeight(viewport.controlHeight());
        }
        int left = sidebarX + 12;
        ctrlButton.setX(left);
        int available = sidebarX + sidebarWidth - left - 12;
        int gap = 4;
        int[] widths = {Math.min(42, Math.max(1, (available - 3 * gap) / 4)),
            Math.min(46, Math.max(1, (available - 3 * gap) / 4)),
            Math.min(38, Math.max(1, (available - 3 * gap) / 4)),
            Math.min(30, Math.max(1, (available - 3 * gap) / 4))};
        int x = left;
        Button[] controls = {ctrlButton, shiftButton, altButton, deselectButton};
        for (int i = 0; i < controls.length; i++) {
            controls[i].setX(x);
            controls[i].setWidth(widths[i]);
            x += widths[i] + gap;
        }
    }

    private boolean hasActiveFilterChips() {
        return virtualKeyFilter != null || category != null || modFilter != null || conflictsOnly || favoritesOnly;
    }

    private boolean showInput() { return !singlePane || compactPage == EditorViewport.Page.INPUT; }
    private boolean showActions() { return !singlePane || compactPage == EditorViewport.Page.ACTIONS; }
    private boolean showCategories() { return !singlePane || compactPage == EditorViewport.Page.FILTERS; }

    private void applyCompactVisibility() {
        if (!singlePane) return;
        boolean input = showInput(), actions = showActions(), filters = showCategories();
        keyboardButtonKeys.keySet().forEach(button -> button.visible = input);
        keyboardTabButton.visible = mouseTabButton.visible = input;
        layoutButton.visible = input && !mouseMode;
        for (Button button : List.of(ctrlButton, shiftButton, altButton, deselectButton)) button.visible &= input;
        searchBox.visible = actions;
        searchClearButton.visible = actions && searchBox.getWidth() >= 40;
        conflictsButton.visible = favoritesButton.visible = actions;
        for (List<Button> buttons : List.of(rowNameButtons, rowBindingButtons, rowUnbindButtons,
                rowConflictButtons, rowFavoriteButtons, rowSectionButtons, filterChipButtons)) {
            buttons.forEach(button -> button.visible &= actions);
        }
        categoryButtons.forEach(button -> button.visible &= filters);
        allCategoryButton.visible = categoryFilterButton.visible = modFilterButton.visible = filters;
        for (int i = 0; i < compactTabs.size(); i++) {
            setButtonState(compactTabs.get(i), compactPage.ordinal() == i ? VisualState.SELECTED : VisualState.NORMAL);
        }
    }

    private void initProfileBar() {
        if (singlePane) {
            int right = width - 8;
            int widthPerAction = 22, gap = 4;
            int newX = right - (3 * widthPerAction + 2 * gap);
            profileCycleButton = addButton(Component.empty(), 100, 6, Math.max(40, newX - 106), 20,
                button -> openProfilePicker());
            newProfileButton = addButton(Component.literal("+"), newX, 6, widthPerAction, 20,
                button -> openCreateDialog());
            renameProfileButton = styled(addButton(Component.literal("✎"), newX + widthPerAction + gap, 6,
                widthPerAction, 20, button -> openRenameDialog()), ButtonStyle.QUIET, true);
            deleteProfileButton = styled(addButton(Component.literal("×"), newX + 2 * (widthPerAction + gap), 6,
                widthPerAction, 20, button -> confirmDeleteProfile()), ButtonStyle.QUIET, true);
            newProfileButton.setTooltip(Tooltip.create(Component.translatable("keyset.profile_new_short")));
            renameProfileButton.setTooltip(Tooltip.create(Component.translatable("keyset.profile_rename_short")));
            deleteProfileButton.setTooltip(Tooltip.create(Component.translatable("keyset.profile_delete_short")));
            compactStatusButton = styled(addButton(Component.literal("!"), 74, 6, 20, 20,
                ignored -> {}), ButtonStyle.QUIET, true);
            updateCompactStatus();
            return;
        }
        int x = width < 720 ? 122 : 180;
        boolean narrow = width < 520;
        int deleteWidth = narrow ? 28 : 64, renameWidth = narrow ? 32 : 68, newWidth = narrow ? 36 : 80;
        int right = width - 16;
        int newX = right - deleteWidth - renameWidth - newWidth - (narrow ? 8 : 12);
        int pickerWidth = Math.max(48, Math.min(280, newX - x - 6));
        profileCycleButton = addButton(Component.empty(), x, 26, pickerWidth, 24, button -> openProfilePicker());
        deleteProfileButton = styled(addButton(Component.translatable("keyset.profile_delete_short"), right - deleteWidth,
            26, deleteWidth, 24, button -> confirmDeleteProfile()), ButtonStyle.QUIET, true);
        renameProfileButton = styled(addButton(Component.translatable("keyset.profile_rename_short"), right - deleteWidth - renameWidth - 6,
            26, renameWidth, 24, button -> openRenameDialog()), ButtonStyle.QUIET, true);
        newProfileButton = addButton(narrow ? Component.literal("+") : Component.translatable("keyset.profile_new_short"),
            newX, 26, newWidth, 24, button -> openCreateDialog());
        if (narrow) {
            deleteProfileButton.setMessage(Component.literal("×"));
            renameProfileButton.setMessage(Component.literal("✎"));
            deleteProfileButton.setTooltip(Tooltip.create(Component.translatable("keyset.profile_delete_short")));
            renameProfileButton.setTooltip(Tooltip.create(Component.translatable("keyset.profile_rename_short")));
            newProfileButton.setTooltip(Tooltip.create(Component.translatable("keyset.profile_new_short")));
        }
        compactStatusButton = styled(addButton(Component.literal("!"), 100, 26, 16, 22,
            ignored -> {}), ButtonStyle.QUIET, true);
        updateCompactStatus();
    }

    private void updateCompactStatus() {
        if (compactStatusButton == null) return;
        compactStatusButton.visible = singlePane && statusIsError && statusMessage != null;
        compactStatusButton.setTooltip(statusMessage == null ? null : Tooltip.create(statusMessage));
        compactStatusButton.setTooltipDelay(java.time.Duration.ZERO);
        setButtonState(compactStatusButton, VisualState.CONFLICT);
    }

    private void initMainControls() {
        int filterGroupWidth = Math.min(170, Math.max(76, mainWidth / 2));
        int filterWidth = Math.max(36, (filterGroupWidth - 4) / 2);
        int searchWidth = mainWidth - 24 - filterGroupWidth - 6;
        boolean showSearchClear = searchWidth >= 64;
        searchBox = new EditBox(font, mainX + (singlePane ? 8 : 12), searchY,
            Math.max(1, searchWidth - (showSearchClear ? 24 : 0)), viewport.searchHeight(), Component.translatable("keyset.search_hint"));
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT);
        searchBox.setValue(searchQuery);
        searchBox.setHint(Component.translatable("keyset.search_hint"));
        searchBox.setResponder(value -> {
            searchQuery = value;
            if (!settingVirtualKeyFilter) virtualKeyFilter = null;
            bindingScroll = 0;
            refreshRows();
        });
        addRenderableWidget(searchBox);
        searchClearButton = styled(addButton(Component.literal("×"), mainX + 12 + searchWidth - 22,
            singlePane ? searchY : searchY - 2, 22, viewport.searchHeight(),
            button -> clearSearch()), ButtonStyle.QUIET, true);
        searchClearButton.visible = showSearchClear;
        int filterX = mainX + mainWidth - 12 - filterGroupWidth;
        int filterY = searchY - 2;
        int filterHeight = singlePane ? viewport.controlHeight() + 4 : 24;
        conflictsButton = styled(addButton(Component.empty(), filterX, filterY,
            filterWidth, filterHeight, button -> { conflictsOnly = !conflictsOnly; bindingScroll = 0; refreshRows(); }), ButtonStyle.TAB, true);
        favoritesButton = styled(addButton(Component.empty(), filterX + filterWidth + 4, filterY,
            filterWidth, filterHeight, button -> { favoritesOnly = !favoritesOnly; bindingScroll = 0; refreshRows(); }), ButtonStyle.TAB, true);
        if (sidebarWidth < 320) {
            int innerWidth = Math.max(1, sidebarWidth - 24);
            int layoutWidth = Math.max(40, Math.min(88, innerWidth / 4));
            int tabGap = 4;
            int tabsWidth = Math.max(2, innerWidth - layoutWidth - tabGap * 2);
            int keyboardWidth = Math.max(1, tabsWidth * 52 / 100);
            int mouseWidth = Math.max(1, tabsWidth - keyboardWidth);
            keyboardTabButton = styled(addButton(Component.translatable("keyset.input_keyboard"),
                sidebarX + 12, 76, keyboardWidth, 24, button -> setMouseMode(false)), ButtonStyle.TAB, true);
            mouseTabButton = styled(addButton(Component.translatable("keyset.input_mouse"),
                sidebarX + 12 + keyboardWidth + tabGap, 76, mouseWidth, 24, button -> setMouseMode(true)),
                ButtonStyle.TAB, true);
            layoutButton = styled(addButton(Component.empty(), sidebarX + sidebarWidth - 12 - layoutWidth,
                76, layoutWidth, 24, button -> cycleLayout()), ButtonStyle.QUIET, true);
            keyboardTabButton.setTooltip(Tooltip.create(Component.translatable("keyset.input_keyboard")));
            mouseTabButton.setTooltip(Tooltip.create(Component.translatable("keyset.input_mouse")));
        } else {
            keyboardTabButton = styled(addButton(Component.translatable("keyset.input_keyboard"), sidebarX + 12, 76, 104, 24,
                button -> setMouseMode(false)), ButtonStyle.TAB, true);
            mouseTabButton = styled(addButton(Component.translatable("keyset.input_mouse"), sidebarX + 122, 76, 78, 24,
                button -> setMouseMode(true)), ButtonStyle.TAB, true);
            layoutButton = styled(addButton(Component.empty(), sidebarX + sidebarWidth - 100, 76, 88, 24,
                button -> cycleLayout()), ButtonStyle.QUIET, true);
        }
    }

    private void initFilterChips() {
        filterChipButtons.clear();
        for (int i = 0; i < 5; i++) {
            final int filter = i;
            filterChipButtons.add(styled(addButton(Component.empty(), mainX + 12, singlePane ? viewport.chipY() : 128,
                32, singlePane ? 16 : 20,
                button -> clearFilter(filter)), ButtonStyle.QUIET, true));
        }
    }

    private void clearFilter(int filter) {
        switch (filter) {
            case 0 -> {
                virtualKeyFilter = null;
                settingVirtualKeyFilter = true;
                searchQuery = "";
                searchBox.setValue("");
                settingVirtualKeyFilter = false;
            }
            case 1 -> { category = null; categoryScroll = 0; }
            case 2 -> { modFilter = null; modScroll = 0; }
            case 3 -> conflictsOnly = false;
            case 4 -> favoritesOnly = false;
            default -> { return; }
        }
        bindingScroll = 0;
        refreshRows();
    }

    private void refreshFilterChips() {
        List<FilterChip> active = new ArrayList<>(5);
        if (virtualKeyFilter != null) {
            Component key = inputLabel(virtualKeyFilter);
            active.add(new FilterChip(0, Component.translatable("keyset.filter_chip_key", key),
                Component.translatable("keyset.filter_clear_key", key)));
        }
        if (category != null) {
            Component label = KeysetClient.categoryLabel(category);
            active.add(new FilterChip(1, Component.translatable("keyset.filter_chip_category", label),
                Component.translatable("keyset.filter_clear_category", label)));
        }
        if (modFilter != null) {
            ModFilter selectedMod = mods.stream().filter(value -> value.id().equals(modFilter)).findFirst().orElse(null);
            String modName = selectedMod == null ? modFilter : selectedMod.name();
            active.add(new FilterChip(2, Component.translatable("keyset.filter_chip_mod", modName),
                Component.translatable("keyset.filter_clear_mod", modName)));
        }
        if (conflictsOnly) {
            active.add(new FilterChip(3, Component.translatable("keyset.filter_chip_conflicts"),
                Component.translatable("keyset.filter_clear_conflicts")));
        }
        if (favoritesOnly) {
            active.add(new FilterChip(4, Component.translatable("keyset.filter_chip_favorites"),
                Component.translatable("keyset.filter_clear_favorites")));
        }
        int available = Math.max(1, mainWidth - 24);
        int maxWidth = active.isEmpty() ? available
            : Math.max(22, (available - (active.size() - 1) * 4) / active.size());
        if (singlePane) listTop = viewport.actionRowsTop(!active.isEmpty());
        int x = mainX + 12;
        FilterChip[] chipsByFilter = new FilterChip[filterChipButtons.size()];
        for (FilterChip chip : active) chipsByFilter[chip.index()] = chip;
        for (int i = 0; i < filterChipButtons.size(); i++) {
            Button button = filterChipButtons.get(i);
            FilterChip chip = chipsByFilter[i];
            button.visible = chip != null;
            if (chip == null) continue;
            int chipWidth = Math.max(22, Math.min(maxWidth, font.width(chip.label().getString()) + 14));
            button.setX(x);
            button.setY(singlePane ? viewport.chipY() : 128);
            button.setHeight(singlePane ? 16 : 20);
            button.setWidth(chipWidth);
            button.setMessage(chip.label());
            button.setTooltip(Tooltip.create(chip.tooltip()));
            button.setTooltipDelay(java.time.Duration.ofMillis(300));
            x += chipWidth + 4;
        }
    }

    private void clearSearch() {
        virtualKeyFilter = null;
        searchQuery = "";
        searchBox.setValue("");
        bindingScroll = 0;
        refreshRows();
    }

    private void setMouseMode(boolean enabled) {
        if (mouseMode == enabled) return;
        mouseMode = enabled;
        init();
    }

    private void initSelectionControls() {
        if (singlePane) {
            int left = sidebarX + 12;
            int available = Math.max(1, sidebarWidth - 24);
            int gap = 4;
            int ctrlWidth = Math.min(42, (available - 3 * gap) / 4);
            int shiftWidth = Math.min(46, (available - 3 * gap) / 4);
            int altWidth = Math.min(38, (available - 3 * gap) / 4);
            int clearWidth = Math.min(30, (available - 3 * gap) / 4);
            int y = viewport.inputControlsY();
            ctrlButton = styled(addButton(Component.literal("Ctrl"), left, y, ctrlWidth, viewport.controlHeight(),
                button -> { virtualCtrl = !virtualCtrl; refreshSelection(); }), ButtonStyle.TAB, true);
            shiftButton = styled(addButton(Component.literal("Shift"), left + ctrlWidth + gap, y,
                shiftWidth, viewport.controlHeight(), button -> { virtualShift = !virtualShift; refreshSelection(); }), ButtonStyle.TAB, true);
            altButton = styled(addButton(Component.literal("Alt"), left + ctrlWidth + shiftWidth + 2 * gap, y,
                altWidth, viewport.controlHeight(), button -> { virtualAlt = !virtualAlt; refreshSelection(); }), ButtonStyle.TAB, true);
            deselectButton = styled(addButton(Component.literal("×"), left + ctrlWidth + shiftWidth + altWidth + 3 * gap,
                y, clearWidth, viewport.controlHeight(), button -> clearSelection()), ButtonStyle.QUIET, true);
            return;
        }
        int x = sidebarX + sidebarWidth - 188;
        int y = summaryY + 12;
        ctrlButton = styled(addButton(Component.literal("Ctrl"), x, y, 42, 22,
            button -> { virtualCtrl = !virtualCtrl; refreshSelection(); }), ButtonStyle.TAB, true);
        shiftButton = styled(addButton(Component.literal("Shift"), x + 46, y, 46, 22,
            button -> { virtualShift = !virtualShift; refreshSelection(); }), ButtonStyle.TAB, true);
        altButton = styled(addButton(Component.literal("Alt"), x + 96, y, 38, 22,
            button -> { virtualAlt = !virtualAlt; refreshSelection(); }), ButtonStyle.TAB, true);
        deselectButton = styled(addButton(Component.literal("×"), x + 142, y, 30, 22,
            button -> clearSelection()), ButtonStyle.QUIET, true);
    }

    private void refreshSelection() {
        boolean selected = selectedMapping != null && showInput();
        if (ctrlButton != null) {
            ctrlButton.visible = shiftButton.visible = altButton.visible = deselectButton.visible = selected;
            setButtonState(ctrlButton, virtualCtrl ? VisualState.SELECTED : VisualState.NORMAL);
            setButtonState(shiftButton, virtualShift ? VisualState.SELECTED : VisualState.NORMAL);
            setButtonState(altButton, virtualAlt ? VisualState.SELECTED : VisualState.NORMAL);
        }
    }

    private void clearSelection() {
        selectedMapping = null;
        capturing = false;
        pendingModifier = null;
        nativeCapture.clear();
        virtualCtrl = virtualShift = virtualAlt = false;
        statusMessage = null;
        refreshRows();
    }

    private void initCategoryButtons() {
        int buttonWidth = (sidebarWidth - 30) / 2;
        boolean compactHeader = sidebarWidth < 320;
        int categoryTabWidth = compactHeader ? 54 : 82;
        int modTabWidth = compactHeader ? 42 : 58;
        int allWidth = compactHeader ? 76 : 114;
        if (sidebarWidth < 240) {
            int availableWidth = Math.max(1, sidebarWidth - 44);
            categoryTabWidth = availableWidth * 36 / 100;
            modTabWidth = availableWidth * 28 / 100;
            allWidth = Math.max(1, availableWidth - categoryTabWidth - modTabWidth);
        }
        int filterControlY = singlePane ? viewport.categoryControlsY() : categoryCardY + 8;
        int filterControlHeight = singlePane ? viewport.controlHeight() : 22;
        categoryFilterButton = styled(addButton(Component.empty(), sidebarX + 12, filterControlY, categoryTabWidth, filterControlHeight,
            button -> {
                modFilterMode = false;
                modFilter = null;
                bindingScroll = 0;
                updateControlLabels();
                refreshRows();
            }), ButtonStyle.TAB, true);
        modFilterButton = styled(addButton(Component.empty(), sidebarX + 16 + categoryTabWidth, filterControlY, modTabWidth, filterControlHeight,
            button -> {
                modFilterMode = true;
                category = null;
                bindingScroll = 0;
                updateControlLabels();
                refreshRows();
            }), ButtonStyle.TAB, true);
        allCategoryButton = styled(addButton(Component.translatable("keyset.category_all"),
            sidebarX + sidebarWidth - 12 - allWidth, filterControlY, allWidth, filterControlHeight,
            button -> {
                if (modFilterMode) { modFilter = null; modScroll = 0; }
                else { category = null; categoryScroll = 0; }
                bindingScroll = 0;
                refreshRows();
            }), ButtonStyle.TAB, true);
        int rows = Math.max(0, (categoryListBottom - categoryListTop + 2) / 24);
        for (int i = 0; i < rows * 2; i++) {
            final int slot = i;
            Button button = styled(addButton(Component.empty(), sidebarX + 12 + i % 2 * (buttonWidth + 6),
                categoryListTop + i / 2 * 24, buttonWidth, 22, clicked -> selectCategoryAt(slot)), ButtonStyle.QUIET, false);
            categoryButtons.add(button);
        }
    }

    private void initFooter() {
        int y = viewport.footerY();
        int footerWidth = width - 16 - mainX;
        if (singlePane) {
            int left = sidebarX, right = width - sidebarX, gap = 2;
            int doneWidth = Math.min(58, Math.max(50, (right - left) / 5));
            Component resetAllLabel = Component.translatable("keyset.reset_all");
            int allWidth = Math.min((right - left) / 5, Math.max(54, font.width(resetAllLabel) + 10));
            boolean showResetAllLabel = font.width(resetAllLabel) + 8 <= allWidth;
            int undoWidth = 24;
            int actionWidth = Math.max(1, (right - left - doneWidth - allWidth - undoWidth - 4 * gap) / 2);
            unbindButton = styled(addButton(Component.translatable("keyset.unbind"), left, y,
                actionWidth, viewport.controlHeight(), button -> unbindSelected()), ButtonStyle.QUIET, true);
            resetButton = styled(addButton(Component.translatable("keyset.reset"), left + actionWidth + gap, y,
                actionWidth, viewport.controlHeight(), button -> resetSelected()), ButtonStyle.QUIET, true);
            undoButton = styled(addButton(Component.literal("↶"), right - doneWidth - allWidth - undoWidth - 3 * gap, y,
                undoWidth, viewport.controlHeight(), button -> {
                    if (runAction(KeysetClient::undo)) {
                        statusMessage = Component.translatable("keyset.undo_done");
                        statusIsError = false;
                    }
                }), ButtonStyle.QUIET, true);
            undoButton.setTooltip(Tooltip.create(Component.translatable("keyset.undo")));
            resetAllButton = styled(addButton(showResetAllLabel ? resetAllLabel : Component.literal("↻"),
                right - doneWidth - allWidth - gap, y,
                allWidth, viewport.controlHeight(), button -> confirmResetAll()), ButtonStyle.QUIET, true);
            resetAllButton.setTooltip(Tooltip.create(Component.translatable("keyset.reset_all")));
            doneButton = styled(addButton(Component.translatable("keyset.done"), right - doneWidth, y,
                doneWidth, viewport.controlHeight(), button -> onClose()), ButtonStyle.PRIMARY, true);
            return;
        }
        boolean compactFooter = footerWidth < 390;
        int doneWidth = compactFooter ? 64 : 80;
        int allWidth = compactFooter ? 80 : 96;
        int undoWidth = compactFooter ? 30 : 64;
        doneButton = styled(addButton(Component.translatable("keyset.done"), width - 16 - doneWidth, y, doneWidth, 26,
            button -> onClose()), ButtonStyle.PRIMARY, true);
        int resetAllX = width - 22 - doneWidth - allWidth;
        resetAllButton = styled(addButton(Component.translatable("keyset.reset_all"), resetAllX,
            y, allWidth, 26, button -> confirmResetAll()), ButtonStyle.QUIET, true);
        undoButton = styled(addButton(compactFooter ? Component.literal("↶") : Component.translatable("keyset.undo"), resetAllX - undoWidth - 6,
            y, undoWidth, 26, button -> {
                if (runAction(KeysetClient::undo)) {
                    statusMessage = Component.translatable("keyset.undo_done");
                    statusIsError = false;
                }
            }), ButtonStyle.QUIET, true);
        undoButton.setTooltip(Tooltip.create(Component.translatable("keyset.undo")));
        int actionWidth = Math.max(36, (undoButton.getX() - mainX - 12) / 2);
        unbindButton = styled(addButton(Component.translatable("keyset.unbind"), mainX, y, actionWidth, 26,
            button -> unbindSelected()), ButtonStyle.QUIET, true);
        resetButton = styled(addButton(Component.translatable("keyset.reset"), mainX + actionWidth + 4, y, actionWidth, 26,
            button -> resetSelected()), ButtonStyle.QUIET, true);
    }

    private void initRowSlots() {
        int rowButtonHeight = rowHeight - 2;
        int inner = Math.max(1, mainWidth - (singlePane ? 22 : 30));
        int bindWidth = Math.min(160, inner * 31 / 100);
        if (inner < 230) bindWidth = Math.max(1, Math.min(inner / 3, inner - 78));
        int nameWidth = Math.max(26, inner - bindWidth - 52);
        int actionWidth = Math.max(1, nameWidth - (singlePane ? 24 : 26));
        int slots = singlePane ? Math.max(visibleRowCapacity(), viewport.visibleActionRows(false)) : visibleRowCapacity();
        for (int i = 0; i < slots; i++) {
            final int slot = i;
            int y = listTop + i * rowHeight;
            rowNameButtons.add(styled(addButton(Component.empty(), mainX + 12, y, actionWidth, rowButtonHeight,
                button -> selectMappingAt(slot)), ButtonStyle.ROW, false));
            rowFavoriteButtons.add(styled(addButton(Component.literal("☆"), mainX + 12 + actionWidth + 2, y,
                22, rowButtonHeight, button -> toggleFavoriteAt(slot)), ButtonStyle.QUIET, true));
            rowBindingButtons.add(styled(addButton(Component.empty(), mainX + 12 + nameWidth + 4, y + 1, bindWidth, Math.min(19, rowHeight - 4),
                button -> captureMappingAt(slot)), ButtonStyle.BINDING, true));
            rowConflictButtons.add(styled(addButton(Component.literal("!"),
                mainX + 12 + nameWidth + 8 + bindWidth, y, 18, rowButtonHeight,
                button -> inspectConflictAt(slot)), ButtonStyle.QUIET, true));
            rowUnbindButtons.add(styled(addButton(Component.literal("×"), mainX + mainWidth - 34, y, 20, rowButtonHeight,
                button -> unbindMappingAt(slot)), ButtonStyle.QUIET, true));
            rowSectionButtons.add(styled(addButton(Component.empty(), mainX + 8, y, mainWidth - 20, rowButtonHeight,
                button -> toggleSectionAt(slot)), ButtonStyle.SECTION, false));
        }
    }

    private void initInputButtons() {
        if (mouseMode) initMouseButtons();
        else initKeyboardButtons();
        setButtonState(keyboardTabButton, mouseMode ? VisualState.NORMAL : VisualState.SELECTED);
        setButtonState(mouseTabButton, mouseMode ? VisualState.SELECTED : VisualState.NORMAL);
        layoutButton.visible = !mouseMode;
    }

    private void initKeyboardButtons() {
        keyboardUnit = Math.max(1, Math.min(18, (sidebarWidth - 70) / 27));
        int groupMainWidth = 18 * keyboardUnit + 20;
        int navWidth = 4 * keyboardUnit + 8;
        int padWidth = Math.round(4.8f * keyboardUnit) + 10;
        int total = groupMainWidth + navWidth + padWidth + 16;
        int x = sidebarX + Math.max(8, (sidebarWidth - total) / 2);
        int y = keyboardPanelY;
        for (KeyboardRow row : virtualKeyboardRows()) {
            addKeyGroup(row.main(), x, y, groupMainWidth, keyboardUnit, 2, row.mainOffset());
            addKeyGroup(row.navigation(), x + groupMainWidth + 8, y, navWidth, keyboardUnit, 2, 0);
            addKeyGroup(row.numpad(), x + groupMainWidth + navWidth + 16, y, padWidth, keyboardUnit, 2, 0);
            y += keyStep;
        }
    }

    private void addKeyGroup(List<VirtualKey> keys, int groupX, int y, int groupWidth, int unit, int gap, float offset) {
        if (keys.isEmpty()) return;
        int rowWidth = keys.stream().mapToInt(key -> Math.max(1, Math.round(unit * key.width()))).sum()
            + (keys.size() - 1) * gap;
        int x = groupX + Math.max(0, (groupWidth - rowWidth) / 2) + Math.round(unit * offset);
        for (VirtualKey key : keys) {
            int w = Math.max(1, Math.round(unit * key.width()));
            Button button = styled(addButton(Component.literal(key.label()), x, y, w, keyHeight,
                ignored -> onVirtualKey(key)), ButtonStyle.KEYCAP, true);
            keyboardButtonKeys.put(button, key);
            x += w + gap;
        }
    }

    private void initMouseButtons() {
        mouseBodyY = keyboardPanelY + 4;
        mouseBodyHeight = Math.max(64, Math.min(compact ? 84 : 132,
            summaryY - (compact ? 8 : 26) - mouseBodyY));
        mouseBodyWidth = Math.round(mouseBodyHeight * 0.66f);
        int extraWidth = compact ? 56 : 74;
        int gap = compact ? 16 : 26;
        int totalWidth = mouseBodyWidth + gap + extraWidth + 24;
        mouseBodyX = sidebarX + Math.max(16, (sidebarWidth - totalWidth) / 2 + 20);
        int half = mouseBodyWidth / 2;
        int centerGap = compact ? 16 : 20;
        int mainWidth = Math.max(12, half - centerGap / 2 - 4);
        int mainHeight = Math.round(mouseBodyHeight * 0.43f);
        addMouseButton(InputConstants.MOUSE_BUTTON_LEFT, "keyset.mouse_left", mouseBodyX + 4, mouseBodyY + 6,
            mainWidth, mainHeight, ButtonStyle.MOUSE_LEFT);
        addMouseButton(InputConstants.MOUSE_BUTTON_RIGHT, "keyset.mouse_right", mouseBodyX + half + centerGap / 2, mouseBodyY + 6,
            mainWidth, mainHeight, ButtonStyle.MOUSE_RIGHT);
        addMouseButton(InputConstants.MOUSE_BUTTON_MIDDLE, "M3", mouseBodyX + half - centerGap / 2, mouseBodyY + 10,
            centerGap, compact ? 31 : 38, ButtonStyle.MOUSE_WHEEL);
        int sideHeight = compact ? 16 : 20;
        for (int i = 0; i < 2; i++) {
            addMouseButton(InputConstants.MOUSE_BUTTON_4 + i, "M" + (i + 4), mouseBodyX - 5,
                mouseBodyY + Math.round(mouseBodyHeight * 0.43f) + i * (sideHeight + 5),
                compact ? 16 : 18, sideHeight, ButtonStyle.MOUSE_SIDE);
        }
        for (int i = 0; i < 3; i++) {
            addMouseButton(InputConstants.MOUSE_BUTTON_6 + i, "M" + (i + 6), mouseBodyX + mouseBodyWidth + gap,
                mouseBodyY + (compact ? 12 : 36) + i * (compact ? 20 : 27),
                extraWidth, compact ? 18 : 23, ButtonStyle.MOUSE_EXTRA);
        }
    }

    private void addMouseButton(int code, String label, int x, int y, int w, int h, ButtonStyle style) {
        VirtualKey key = new VirtualKey(code, Component.translatable(label).getString(), true);
        Component caption = Component.translatable(label);
        if ((style == ButtonStyle.MOUSE_LEFT || style == ButtonStyle.MOUSE_RIGHT) && font.width(caption) > w - 4) {
            caption = Component.literal(style == ButtonStyle.MOUSE_LEFT ? "M1" : "M2");
        }
        Button button = styled(addButton(caption, x, y, w, h, ignored -> onVirtualKey(key)),
            style, true);
        keyboardButtonKeys.put(button, key);
    }

    private Button addButton(Component label, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(new FlatButton(x, y, Math.max(1, w), h, label, action));
    }

    private static Button styled(Button button, ButtonStyle style, boolean centered) {
        FlatButton flat = (FlatButton) button;
        flat.style = style;
        flat.centered = centered;
        return button;
    }

    private void updateControlLabels() {
        categoryFilterButton.setMessage(Component.translatable(sidebarWidth < 320
            ? "keyset.filter_categories_short" : "keyset.filter_categories"));
        modFilterButton.setMessage(Component.translatable(sidebarWidth < 320
            ? "keyset.filter_mods_short" : "keyset.filter_mods"));
        categoryFilterButton.setTooltip(Tooltip.create(Component.translatable("keyset.filter_categories")));
        modFilterButton.setTooltip(Tooltip.create(Component.translatable("keyset.filter_mods")));
        setButtonState(categoryFilterButton, modFilterMode ? VisualState.NORMAL : VisualState.SELECTED);
        setButtonState(modFilterButton, modFilterMode ? VisualState.SELECTED : VisualState.NORMAL);
        allCategoryButton.setMessage(Component.translatable(modFilterMode ? "keyset.mods_all" : "keyset.category_all"));
        conflictsButton.setMessage(Component.translatable("keyset.conflicts_filter"));
        conflictsButton.setTooltip(Tooltip.create(Component.translatable("keyset.conflicts_filter")));
        setButtonState(conflictsButton, conflictsOnly ? VisualState.SELECTED : VisualState.NORMAL);
        favoritesButton.setMessage(Component.translatable("keyset.favorites_filter"));
        favoritesButton.setTooltip(Tooltip.create(Component.translatable("keyset.favorites_filter")));
        setButtonState(favoritesButton, favoritesOnly ? VisualState.SELECTED : VisualState.NORMAL);
        layoutButton.setMessage(layoutIndex < 0
            ? Component.translatable("keyset.layout_auto", keyboardLayout.id().toUpperCase(Locale.ROOT))
            : Component.literal(keyboardLayout.shortName() + "  ▾"));
    }

    private void updateFooterState() {
        unbindButton.active = resetButton.active = selectedMapping != null;
        resetAllButton.active = !allMappings.isEmpty();
        undoButton.active = KeysetClient.canUndo();
        refreshSelection();
    }

    private void rebuildModel() {
        List<KeyMapping> mappings = KeysetClient.mappings();
        bindingIndex = new BindingIndex<>(mappings, KeysetClient::binding, KeysetClient::bindingContextsConflict);
        mappingViews.clear();
        categoryCounts.clear();
        Map<String, ModFilter> modCounts = new HashMap<>();
        for (KeyMapping mapping : mappings) {
            Binding value = bindingIndex.binding(mapping);
            Component name = KeysetClient.name(mapping);
            Component bindingLabel = bindingDisplay(value);
            var owner = KeysetClient.owner(mapping);
            Component modName = owner.id().equals("unknown") ? Component.translatable("keyset.owner_unknown")
                : Component.literal(owner.name());
            var tooltipText = name.copy().append("\n").append(Component.translatable("keyset.action_mod", modName));
            if (KeysetClient.multiKeyCapture(mapping)) tooltipText.append("\n").append(Component.translatable("keyset.native_hint"));
            Tooltip tooltip = Tooltip.create(tooltipText);
            String modifiers = (value.ctrl() ? "ctrl control " : "") + (value.shift() ? "shift " : "")
                + (value.alt() ? "alt " : "") + (value.command() ? "cmd command " : "");
            mappingViews.put(mapping, new MappingView(name, bindingLabel, name.getString().toLowerCase(Locale.ROOT),
                (KeysetClient.categoryLabel(mapping.getCategory()).getString() + " " + owner.id() + " " + modName.getString()).toLowerCase(Locale.ROOT),
                mapping.getCategory().id().toString().toLowerCase(Locale.ROOT), String.join(" ", value.keys()).toLowerCase(Locale.ROOT),
                bindingLabel.getString().toLowerCase(Locale.ROOT), modifiers, owner.id(), modName.getString(), tooltip));
            categoryCounts.merge(mapping.getCategory(), 1, Integer::sum);
            ModFilter previous = modCounts.get(owner.id());
            modCounts.put(owner.id(), new ModFilter(owner.id(), modName.getString(), previous == null ? 1 : previous.count() + 1));
        }
        allMappings = mappings.stream().sorted(Comparator
            .comparing((KeyMapping mapping) -> KeysetClient.categoryLabel(mapping.getCategory()).getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(mapping -> mapping.getCategory().id().toString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(mapping -> mappingViews.get(mapping).searchName(), String.CASE_INSENSITIVE_ORDER)).toList();
        categories = categoryCounts.keySet().stream().sorted(Comparator.comparing(value -> KeysetClient.categoryLabel(value).getString(),
            String.CASE_INSENSITIVE_ORDER)).toList();
        mods = modCounts.values().stream().sorted(Comparator.comparing(ModFilter::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ModFilter::id, String.CASE_INSENSITIVE_ORDER)).toList();
        if (modFilter != null && mods.stream().noneMatch(value -> value.id().equals(modFilter))) modFilter = null;
        appliedFilter = null;
    }

    private void refreshRows() {
        if (searchBox == null) return;
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        FilterState filter = new FilterState(category, modFilter, conflictsOnly, favoritesOnly, query, virtualKeyFilter, collapseRevision);
        if (!filter.equals(appliedFilter)) {
            filteredMappings = allMappings.stream()
                .filter(mapping -> category == null || category.equals(mapping.getCategory()))
                .filter(mapping -> modFilter == null || modFilter.equals(mappingViews.get(mapping).modId()))
                .filter(mapping -> !conflictsOnly || bindingIndex.conflicts(mapping))
                .filter(mapping -> !favoritesOnly || KeysetClient.isFavorite(mapping))
                .filter(mapping -> (virtualKeyFilter != null || query.isEmpty() || matches(mapping, query))
                    && (virtualKeyFilter == null || bindingIndex.binding(mapping).keys().contains(virtualKeyFilter)))
                .toList();
            displayEntries = makeDisplayEntries(filteredMappings);
            appliedFilter = filter;
        }
        if (selectedMapping != null && !filteredMappings.contains(selectedMapping)) {
            selectedMapping = null;
            capturing = false;
            pendingModifier = null;
            nativeCapture.clear();
        }
        refreshFilterChips();
        updateControlLabels();
        refreshVisibleRows();
        updateFooterState();
        refreshCategoryButtons();
        refreshKeyboardButtons();
        applyCompactVisibility();
        updateCompactStatus();
    }

    /** Scroll path: update existing viewport widgets, without sorting or rebuilding assignment data. */
    private void refreshVisibleRows() {
        bindingScroll = clampScroll(bindingScroll, displayEntries.size(), visibleRowCapacity());
        for (int slot = 0; slot < rowNameButtons.size(); slot++) {
            int rowY = listTop + slot * rowHeight;
            rowNameButtons.get(slot).setY(rowY);
            rowFavoriteButtons.get(slot).setY(rowY);
            rowBindingButtons.get(slot).setY(rowY + 1);
            rowUnbindButtons.get(slot).setY(rowY);
            rowConflictButtons.get(slot).setY(rowY);
            rowSectionButtons.get(slot).setY(rowY);
            ActionListEntry entry = slot < visibleRowCapacity() ? entryAt(slot) : null;
            KeyMapping mapping = entry == null || entry.isSection() ? null : entry.mapping();
            Button name = rowNameButtons.get(slot), favorite = rowFavoriteButtons.get(slot);
            Button binding = rowBindingButtons.get(slot), clear = rowUnbindButtons.get(slot);
            Button conflict = rowConflictButtons.get(slot);
            Button section = rowSectionButtons.get(slot);
            boolean sectionRow = entry != null && entry.isSection();
            section.visible = sectionRow;
            name.visible = favorite.visible = binding.visible = clear.visible = mapping != null;
            conflict.visible = mapping != null && bindingIndex.conflicts(mapping);
            if (sectionRow) {
                Component sectionName = KeysetClient.categoryLabel(entry.category());
                boolean collapsed = isSectionCollapsed(entry.category());
                String arrow = collapsed ? "▸" : "▾";
                section.setMessage(Component.literal(arrow + "  " + sectionName.getString() + "  ·  " + entry.count()));
                section.setTooltip(Tooltip.create(Component.translatable("keyset.section_toggle_tip", sectionName)));
                setButtonState(section, Objects.equals(category, entry.category()) ? VisualState.SELECTED : VisualState.NORMAL);
                continue;
            }
            if (mapping == null) continue;
            MappingView view = mappingViews.get(mapping);
            name.setMessage(view.name());
            name.setTooltip(view.tooltip());
            binding.setTooltip(view.tooltip());
            name.setTooltipDelay(java.time.Duration.ofMillis(350));
            binding.setTooltipDelay(java.time.Duration.ofMillis(350));
            binding.setMessage(capturing && selectedMapping == mapping
                ? nativeCapture.isEmpty() ? Component.translatable("keyset.press_key") : bindingDisplay(nativeCapture.preview())
                : view.bindingLabel());
            setButtonState(name, selectedMapping == mapping ? VisualState.SELECTED : VisualState.NORMAL);
            setButtonState(binding, selectedMapping == mapping ? VisualState.SELECTED
                : bindingIndex.conflicts(mapping) ? VisualState.CONFLICT : VisualState.NORMAL);
            if (conflict.visible) {
                List<KeyMapping> conflictingMappings = KeysetClient.conflictsFor(mapping, bindingIndex.binding(mapping));
                String names = conflictingMappings.stream().map(value -> KeysetClient.name(value).getString())
                    .collect(java.util.stream.Collectors.joining(", "));
                conflict.setTooltip(Tooltip.create(Component.translatable("keyset.conflict_inspect", names)));
                conflict.setTooltipDelay(java.time.Duration.ofMillis(300));
                setButtonState(conflict, VisualState.CONFLICT);
            }
            boolean isFavorite = KeysetClient.isFavorite(mapping);
            favorite.setMessage(Component.literal(isFavorite ? "★" : "☆"));
            favorite.setTooltip(Tooltip.create(Component.translatable(isFavorite ? "keyset.favorite_remove" : "keyset.favorite_add")));
            favorite.setTooltipDelay(java.time.Duration.ofMillis(350));
            setButtonState(favorite, isFavorite ? VisualState.SELECTED : VisualState.NORMAL);
        }
        // Scroll and capture paths update this viewport without going through refreshRows.
        // Keep buttons from hidden compact pages hidden on those paths too.
        applyCompactVisibility();
    }

    private List<ActionListEntry> makeDisplayEntries(List<KeyMapping> mappings) {
        if (virtualKeyFilter != null) {
            return mappings.stream().map(mapping -> new ActionListEntry(mapping, mapping.getCategory(), 0)).toList();
        }
        Map<KeyMapping.Category, List<KeyMapping>> groups = new LinkedHashMap<>();
        for (KeyMapping mapping : mappings) groups.computeIfAbsent(mapping.getCategory(), ignored -> new ArrayList<>()).add(mapping);
        List<ActionListEntry> entries = new ArrayList<>(mappings.size() + groups.size());
        groups.forEach((section, sectionMappings) -> {
            entries.add(new ActionListEntry(null, section, sectionMappings.size()));
            if (!isSectionCollapsed(section)) {
                for (KeyMapping mapping : sectionMappings) entries.add(new ActionListEntry(mapping, section, 0));
            }
        });
        return List.copyOf(entries);
    }

    private boolean isSectionCollapsed(KeyMapping.Category section) {
        return COLLAPSED_SECTIONS.contains(section.id().toString());
    }

    private void toggleFavoriteAt(int slot) {
        KeyMapping mapping = mappingAt(slot);
        if (mapping == null) return;
        if (runAction(() -> KeysetClient.toggleFavorite(mapping))) {
            statusMessage = Component.translatable(KeysetClient.isFavorite(mapping)
                ? "keyset.favorite_added" : "keyset.favorite_removed");
            statusIsError = false;
        }
    }

    private int visibleRowCapacity() {
        return singlePane ? viewport.visibleActionRows(hasActiveFilterChips())
            : Math.max(1, (listBottom - listTop) / rowHeight);
    }

    private boolean matches(KeyMapping mapping, String query) {
        MappingView view = mappingViews.get(mapping);
        String name = view.searchName(), categoryName = view.categoryName(), categoryId = view.categoryId();
        String keyName = view.keyName(), keyLabel = view.keyLabel(), modifiers = view.modifiers();

        for (String rawTerm : query.split("\\s+")) {
            if (rawTerm.isBlank()) continue;
            String term = rawTerm;
            boolean categoryOnly = term.startsWith("(") && term.endsWith(")");
            boolean modifierOnly = term.startsWith("{") && term.endsWith("}");
            boolean keyOnly = term.startsWith("[") && term.endsWith("]");
            if (categoryOnly || modifierOnly || keyOnly) {
                term = term.substring(1, term.length() - 1);
            }
            if (categoryOnly || term.startsWith("@")) {
                if (!categoryOnly) term = term.substring(1);
                if (!categoryName.contains(term) && !categoryId.contains(term)) return false;
            } else if (term.startsWith("#")) {
                if (!modifiers.contains(term.substring(1))) return false;
            } else if (term.startsWith("$")) {
                term = term.substring(1);
                if (!keyName.contains(term) && !keyLabel.contains(term)) return false;
            } else if (modifierOnly) {
                if (!modifiers.contains(term)) return false;
            } else if (keyOnly) {
                if (!keyName.contains(term) && !keyLabel.contains(term)) return false;
            } else if (!name.contains(term) && !mapping.getName().toLowerCase(Locale.ROOT).contains(term)
                && !categoryName.contains(term) && !keyLabel.contains(term) && !keyName.contains(term)
                && !modifiers.contains(term)) return false;
        }
        return true;
    }

    private void setButtonState(Button button, VisualState state) {
        if (button instanceof FlatButton flat) flat.visualState = state;
    }

    private void refreshCategoryButtons() {
        int buttonWidth = (sidebarWidth - 30) / 2;
        int visibleMods = Math.max(1, categoryButtons.size() / 2);
        if (modFilterMode) modScroll = clampScroll(modScroll, mods.size(), visibleMods);
        else categoryScroll = clampScroll(categoryScroll, categories.size(), categoryButtons.size());
        setButtonState(allCategoryButton, (modFilterMode ? modFilter == null : category == null)
            ? VisualState.SELECTED : VisualState.NORMAL);
        for (int slot = 0; slot < categoryButtons.size(); slot++) {
            Button button = categoryButtons.get(slot);
            if (modFilterMode) {
                int index = modScroll + slot;
                button.visible = slot < visibleMods && index < mods.size();
                if (!button.visible) continue;
                ModFilter value = mods.get(index);
                button.setX(sidebarX + 12);
                button.setY(categoryListTop + slot * 24);
                button.setSize(sidebarWidth - 24, 22);
                button.setMessage(Component.literal(value.name() + "  ·  " + value.count()));
                button.setTooltip(Tooltip.create(Component.literal(value.name() + "  ·  " + value.id())));
                setButtonState(button, value.id().equals(modFilter) ? VisualState.SELECTED : VisualState.NORMAL);
            } else {
                int index = categoryScroll + slot;
                button.visible = index < categories.size();
                if (!button.visible) continue;
                KeyMapping.Category value = categories.get(index);
                // LayoutElement.setRectangle takes width/height first. Keep position and size explicit.
                button.setX(sidebarX + 12 + slot % 2 * (buttonWidth + 6));
                button.setY(categoryListTop + slot / 2 * 24);
                button.setSize(buttonWidth, 22);
                int count = categoryCounts.getOrDefault(value, 0);
                button.setMessage(Component.literal(KeysetClient.categoryLabel(value).getString() + "  ·  " + count));
                button.setTooltip(null);
                setButtonState(button, value.equals(category) ? VisualState.SELECTED : VisualState.NORMAL);
            }
        }
        // The category and action lists overlap in compact mode, so every category refresh
        // must restore the visibility mask for the currently selected page.
        applyCompactVisibility();
    }

    private void selectCategoryAt(int slot) {
        if (modFilterMode) {
            int index = modScroll + slot;
            if (slot >= 0 && slot < Math.max(1, categoryButtons.size() / 2) && index >= 0 && index < mods.size()) {
                modFilter = mods.get(index).id();
                bindingScroll = 0;
                if (singlePane) compactPage = EditorViewport.Page.ACTIONS;
                refreshRows();
            }
        } else {
            int index = categoryScroll + slot;
            if (index >= 0 && index < categories.size()) {
                category = categories.get(index);
                bindingScroll = 0;
                if (singlePane) compactPage = EditorViewport.Page.ACTIONS;
                refreshRows();
            }
        }
    }

    private void refreshKeyboardButtons() {
        for (Map.Entry<Button, VirtualKey> entry : keyboardButtonKeys.entrySet()) {
            String key = keyName(entry.getValue());
            boolean selected = selectedMapping == null ? key.equals(virtualKeyFilter)
                : bindingIndex.binding(selectedMapping).keys().contains(key);
            setButtonState(entry.getKey(), selected ? VisualState.SELECTED
                : bindingIndex.keyConflicts(key) ? VisualState.CONFLICT
                : bindingIndex.forKey(key).isEmpty() ? VisualState.NORMAL : VisualState.ASSIGNED);
        }
    }

    private void refreshProfileButtons() {
        List<Profile> profiles = currentProfiles();
        Profile active = KeysetClient.profiles().active();
        profileCycleButton.setMessage(Component.literal((active == null ? "" : active.name()) + "  ▾"));
        profileCycleButton.active = !profiles.isEmpty();
        renameProfileButton.active = active != null;
        deleteProfileButton.active = active != null && profiles.size() > 1;
    }

    private List<Profile> currentProfiles() {
        return KeysetClient.profiles().profiles();
    }

    private void openProfilePicker() {
        minecraft.setScreenAndShow(new ProfilePicker());
    }

    private final class ProfilePicker extends Screen {
        private final List<Button> rows = new ArrayList<>();
        private final List<Button> compareButtons = new ArrayList<>();
        private final List<Button> autoButtons = new ArrayList<>();
        private int scroll;
        private int listTop;
        private int listBottom;

        private ProfilePicker() {
            super(Component.translatable("keyset.profile_picker_title"));
        }

        @Override
        protected void init() {
            clearWidgets();
            rows.clear();
            compareButtons.clear();
            autoButtons.clear();
            int panelWidth = Math.max(1, Math.min(480, width - 24));
            boolean hasContext = KeysetClient.currentContext() != null;
            int panelHeight = Math.min(height - 32,
                Math.max(190, currentProfiles().size() * 24 + 108 + (hasContext ? 0 : 18)));
            int panelTop = (height - panelHeight) / 2;
            listTop = panelTop + 40;
            listBottom = panelTop + panelHeight - (hasContext ? 64 : 82);
            int count = Math.max(1, (listBottom - listTop) / 23);
            int panelX = (width - panelWidth) / 2;
            int compareWidth = Math.min(78, Math.max(62, panelWidth / 6));
            int autoWidth = hasContext ? 26 : 0;
            int actionGap = hasContext ? 10 : 6;
            int rowWidth = panelWidth - 24 - compareWidth - autoWidth - actionGap;
            int compareX = panelX + 12 + rowWidth + 6;
            for (int i = 0; i < count; i++) {
                final int slot = i;
                Button row = styled(new FlatButton(panelX + 12, listTop + i * 23,
                    rowWidth, 20, Component.empty(), ignored -> select(slot)), ButtonStyle.QUIET, false);
                rows.add(addRenderableWidget(row));
                Button compare = styled(new FlatButton(compareX, listTop + i * 23,
                    compareWidth, 20, Component.translatable("keyset.compare_profile"), ignored -> compare(slot)),
                    ButtonStyle.TAB, true);
                compareButtons.add(addRenderableWidget(compare));
                Button auto = styled(new FlatButton(panelX + panelWidth - 12 - 26, listTop + i * 23,
                    26, 20, Component.empty(), ignored -> toggleAuto(slot)), ButtonStyle.QUIET, true);
                autoButtons.add(addRenderableWidget(auto));
            }
            int closeWidth = Math.min(84, Math.max(64, (panelWidth - 36) / 4));
            int transferWidth = Math.max(1, Math.min(132, (panelWidth - 36 - closeWidth - 12) / 2));
            int buttonY = panelTop + panelHeight - 29;
            Button export = new FlatButton(panelX + 12, buttonY, transferWidth, 20,
                Component.translatable("keyset.transfer_export"), ignored -> exportActive());
            addRenderableWidget(export);
            Button importButton = new FlatButton(panelX + 18 + transferWidth, buttonY, transferWidth, 20,
                Component.translatable("keyset.transfer_import"), ignored -> confirmImport());
            addRenderableWidget(importButton);
            Button close = new FlatButton(panelX + panelWidth - 12 - closeWidth,
                buttonY, closeWidth, 20, Component.translatable("keyset.cancel"), ignored -> close());
            addRenderableWidget(close);
            refresh();
        }

        private void refresh() {
            List<Profile> profiles = currentProfiles();
            scroll = clampScroll(scroll, profiles.size(), rows.size());
            String activeId = KeysetClient.profiles().active() == null ? null : KeysetClient.profiles().active().id();
            KeysetClient.CurrentContext context = KeysetClient.currentContext();
            for (int i = 0; i < rows.size(); i++) {
                int index = scroll + i;
                Button row = rows.get(i);
                Button compare = compareButtons.get(i);
                Button auto = autoButtons.get(i);
                boolean visible = index < profiles.size() && listTop + i * 23 + 20 <= listBottom;
                row.visible = visible;
                compare.visible = auto.visible = false;
                if (!visible) continue;
                Profile profile = profiles.get(index);
                boolean active = Objects.equals(activeId, profile.id());
                String activeBadge = Component.translatable("keyset.profile_active_badge").getString();
                int badgeWidth = active ? font.width("✓ " + activeBadge) + 10 : 0;
                int nameWidth = Math.max(1, row.getWidth() - badgeWidth - (active ? 22 : 14));
                String displayName = profile.name();
                if (font.width(displayName) > nameWidth) {
                    displayName = font.plainSubstrByWidth(displayName, Math.max(1, nameWidth - font.width("…"))) + "…";
                }
                row.setMessage(Component.literal(displayName));
                setButtonState(row, active ? VisualState.SELECTED : VisualState.NORMAL);
                compare.visible = !active && activeId != null;
                compare.active = compare.visible;
                compare.setTooltip(Tooltip.create(Component.translatable("keyset.compare_profile")));
                String routeId = context == null ? null : KeysetClient.routedProfile(context.id());
                boolean routed = profile.id().equals(routeId);
                auto.visible = context != null;
                auto.active = context != null;
                auto.setMessage(Component.literal(routed ? "✓" : "↗"));
                auto.setTooltip(Tooltip.create(Component.translatable(routed ? "keyset.auto_profile_remove_tip"
                    : "keyset.auto_profile_set_tip", context == null ? "" : context.label())));
                auto.setTooltipDelay(java.time.Duration.ofMillis(350));
                setButtonState(auto, routed ? VisualState.SELECTED : VisualState.NORMAL);
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (refreshForSharedRevision()) init();
        }

        private void compare(int slot) {
            int index = scroll + slot;
            List<Profile> profiles = currentProfiles();
            if (index < 0 || index >= profiles.size()) return;
            Profile source = profiles.get(index);
            Profile active = KeysetClient.profiles().active();
            if (active == null || active.id().equals(source.id())) return;
            minecraft.setScreenAndShow(new ProfileComparisonScreen(KeysetScreen.this, source.id()));
        }

        private void select(int slot) {
            int index = scroll + slot;
            List<Profile> profiles = currentProfiles();
            if (index < 0 || index >= profiles.size()) return;
            String id = profiles.get(index).id();
            runAction(() -> KeysetClient.switchProfile(id));
            close();
        }

        private void toggleAuto(int slot) {
            int index = scroll + slot;
            List<Profile> profiles = currentProfiles();
            if (index < 0 || index >= profiles.size()) return;
            Profile profile = profiles.get(index);
            KeysetClient.RouteResult[] result = new KeysetClient.RouteResult[1];
            close();
            if (runAction(() -> result[0] = KeysetClient.toggleAutoProfile(profile.id()))) {
                statusMessage = Component.translatable(result[0].enabled()
                    ? "keyset.auto_profile_enabled" : "keyset.auto_profile_removed",
                    result[0].profileName(), result[0].contextLabel());
                statusIsError = false;
            }
        }

        private void close() {
            minecraft.setScreenAndShow(KeysetScreen.this);
        }

        private void exportActive() {
            close();
            if (runAction(KeysetClient::exportActiveProfile)) {
                statusMessage = Component.translatable("keyset.transfer_exported");
                statusIsError = false;
            }
        }

        private void confirmImport() {
            try {
                int count = KeysetClient.transferFileCount();
                if (count == 0) {
                    close();
                    statusMessage = Component.translatable("keyset.transfer_empty");
                    statusIsError = false;
                    return;
                }
                minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
                    minecraft.setScreenAndShow(KeysetScreen.this);
                    if (confirmed) importTransferred();
                }, Component.translatable("keyset.transfer_import_title"),
                    Component.translatable("keyset.transfer_import_prompt", count),
                    Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
            } catch (IOException exception) {
                close();
                statusMessage = localizedError(exception);
                statusIsError = true;
            }
        }

        private void importTransferred() {
            try {
                int imported = KeysetClient.importTransferredProfiles();
                statusMessage = imported == 0 ? Component.translatable("keyset.transfer_duplicate")
                    : Component.translatable("keyset.transfer_imported", imported);
                statusIsError = false;
            } catch (IOException | IllegalArgumentException exception) {
                statusMessage = localizedError(exception);
                statusIsError = true;
            }
            rebuildModel();
            refreshRows();
            refreshProfileButtons();
            observedRevision = KeysetClient.revision();
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int count = currentProfiles().size();
            int visible = rows.size();
            scroll = clampScroll(scroll - (int) Math.signum(scrollY), count, visible);
            refresh();
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                close();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int panelWidth = Math.max(1, Math.min(480, width - 24));
            boolean hasContext = KeysetClient.currentContext() != null;
            int panelHeight = Math.min(height - 32,
                Math.max(190, currentProfiles().size() * 24 + 108 + (hasContext ? 0 : 18)));
            int x = (width - panelWidth) / 2;
            int y = (height - panelHeight) / 2;
            drawCard(graphics, x, y, panelWidth, panelHeight);
            graphics.fill(x + 12, y + 29, x + panelWidth - 12, y + 30, BORDER);
            graphics.text(font, Component.translatable("keyset.profile_picker_title"), x + 14, y + 13, TEXT);
            if (!hasContext) {
                Component hint = Component.translatable("keyset.auto_profile_requires_world");
                drawText(graphics, hint, x + 14, y + panelHeight - 47, panelWidth - 28, MUTED);
            }
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            String activeId = KeysetClient.profiles().active() == null ? null : KeysetClient.profiles().active().id();
            for (int i = 0; i < rows.size(); i++) {
                int index = scroll + i;
                if (index < 0 || index >= currentProfiles().size()
                    || !Objects.equals(activeId, currentProfiles().get(index).id())) continue;
                Button row = rows.get(i);
                if (!row.visible) continue;
                String badge = Component.translatable("keyset.profile_active_badge").getString();
                int badgeWidth = font.width("✓ " + badge) + 10;
                int nameWidth = row.getWidth() - badgeWidth - 22;
                String displayName = currentProfiles().get(index).name();
                if (font.width(displayName) > nameWidth) {
                    displayName = font.plainSubstrByWidth(displayName, Math.max(1, nameWidth - font.width("…"))) + "…";
                }
                int badgeX = row.getX() + 8 + font.width(displayName) + 5;
                int badgeY = row.getY() + 3;
                rounded(graphics, badgeX, badgeY, badgeWidth, 14, 0x804A9C8C);
                graphics.text(font, "✓ " + badge, badgeX + 5, badgeY + 3, TEAL, false);
            }
        }
    }

    private void cycleLayout() {
        layoutIndex++;
        if (layoutIndex >= KeyboardLayouts.MANUAL.size()) layoutIndex = -1;
        init();
    }

    private List<KeyboardRow> virtualKeyboardRows() {
        List<KeyboardRow> rows = new ArrayList<>();
        List<VirtualKey> function = new ArrayList<>();
        function.add(key(InputConstants.KEY_ESCAPE, "ESC", 1.5f));
        int[] functionCodes = {InputConstants.KEY_F1, InputConstants.KEY_F2, InputConstants.KEY_F3, InputConstants.KEY_F4,
            InputConstants.KEY_F5, InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8,
            InputConstants.KEY_F9, InputConstants.KEY_F10, InputConstants.KEY_F11, InputConstants.KEY_F12};
        for (int i = 0; i < functionCodes.length; i++) function.add(key(functionCodes[i], "F" + (i + 1), 1.3f));
        rows.add(new KeyboardRow(function,
            weightedKeys(new String[] {"PRT", "SCR", "PAU"},
                new int[] {InputConstants.KEY_PRINTSCREEN, InputConstants.KEY_SCROLLLOCK, InputConstants.KEY_PAUSE},
                new float[] {1.4f, 1.4f, 1.4f}),
            List.of(), 0));

        rows.add(new KeyboardRow(weightedKeys(
            new String[] {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "BS"},
            new int[] {InputConstants.KEY_GRAVE, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
                InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7,
                InputConstants.KEY_8, InputConstants.KEY_9, InputConstants.KEY_0, InputConstants.KEY_MINUS,
                InputConstants.KEY_EQUALS, InputConstants.KEY_BACKSPACE},
            new float[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.8f}),
            keys(new String[] {"IN", "HM", "PU"}, new int[] {InputConstants.KEY_INSERT, InputConstants.KEY_HOME, InputConstants.KEY_PAGEUP}),
            keys(new String[] {"NM", "/", "*", "-"}, new int[] {InputConstants.KEY_NUMLOCK,
                InputConstants.getKey("key.keyboard.keypad.divide").getValue(),
                InputConstants.getKey("key.keyboard.keypad.multiply").getValue(),
                InputConstants.getKey("key.keyboard.keypad.subtract").getValue()}), 0));

        List<VirtualKey> topLetters = layoutLetterKeys(0);
        List<VirtualKey> qrow = new ArrayList<>();
        qrow.add(key(InputConstants.KEY_TAB, "TAB", 1.6f));
        qrow.addAll(topLetters);
        qrow.addAll(keys(new String[] {"[", "]", "\\"},
            new int[] {InputConstants.KEY_LBRACKET, InputConstants.KEY_RBRACKET, InputConstants.KEY_BACKSLASH}));
        rows.add(new KeyboardRow(qrow,
            keys(new String[] {"DL", "EN", "PD"}, new int[] {InputConstants.KEY_DELETE, InputConstants.KEY_END, InputConstants.KEY_PAGEDOWN}),
            keys(new String[] {"7", "8", "9", "+"}, new int[] {InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8,
                InputConstants.KEY_NUMPAD9, InputConstants.getKey("key.keyboard.keypad.add").getValue()}), 0.4f));

        List<VirtualKey> arow = new ArrayList<>();
        arow.add(key(InputConstants.KEY_CAPSLOCK, "CAPS", 1.9f));
        arow.addAll(layoutLetterKeys(1));
        arow.add(key(InputConstants.KEY_APOSTROPHE, "'", 1));
        arow.add(key(InputConstants.KEY_RETURN, "ENT", 2));
        rows.add(new KeyboardRow(arow,
            keys(new String[] {"↑"}, new int[] {InputConstants.KEY_UP}),
            keys(new String[] {"4", "5", "6"}, new int[] {InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6}),
            0.7f));

        List<VirtualKey> zrow = new ArrayList<>();
        zrow.add(key(InputConstants.KEY_LSHIFT, "SHIFT", 2.3f));
        zrow.addAll(layoutLetterKeys(2));
        zrow.add(key(InputConstants.KEY_RSHIFT, "SHIFT", 2.3f));
        rows.add(new KeyboardRow(zrow,
            keys(new String[] {"←", "↓", "→"}, new int[] {InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_RIGHT}),
            keys(new String[] {"1", "2", "3", "EN"}, new int[] {InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
                InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPADENTER}), 0.8f));

        List<VirtualKey> bottom = weightedKeys(
            new String[] {"CTRL", "WIN", "ALT", "SPACE", "ALT", "WIN", "CTRL"},
            new int[] {InputConstants.KEY_LCONTROL, InputConstants.KEY_LGUI, InputConstants.KEY_LALT, InputConstants.KEY_SPACE,
                InputConstants.KEY_RALT, InputConstants.KEY_RGUI, InputConstants.KEY_RCONTROL},
            new float[] {1.8f, 1.3f, 1.3f, 4.3f, 1.3f, 1.3f, 1.8f});
        rows.add(new KeyboardRow(bottom, List.of(),
            weightedKeys(new String[] {"0", ","}, new int[] {InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPADCOMMA},
                new float[] {2, 1}), 0.4f));
        return rows;
    }

    private List<VirtualKey> layoutLetterKeys(int row) {
        List<VirtualKey> result = new ArrayList<>();
        for (int i = 0; i < LETTER_CODES[row].length; i++) {
            result.add(key(LETTER_CODES[row][i], PHYSICAL_LETTERS[row][i].toUpperCase(Locale.ROOT), 1));
        }
        return result;
    }

    private VirtualKey key(int code, String label, float width) {
        String physical = physicalKey(code);
        return new VirtualKey(code, physical == null ? label : keyboardLayout.label(physical, label), false, width);
    }

    private static String physicalKey(int code) {
        for (int row = 0; row < LETTER_CODES.length; row++) {
            for (int col = 0; col < LETTER_CODES[row].length; col++) {
                if (LETTER_CODES[row][col] == code) return PHYSICAL_LETTERS[row][col];
            }
        }
        return switch (code) {
            case InputConstants.KEY_0 -> "0"; case InputConstants.KEY_1 -> "1"; case InputConstants.KEY_2 -> "2";
            case InputConstants.KEY_3 -> "3"; case InputConstants.KEY_4 -> "4"; case InputConstants.KEY_5 -> "5";
            case InputConstants.KEY_6 -> "6"; case InputConstants.KEY_7 -> "7"; case InputConstants.KEY_8 -> "8";
            case InputConstants.KEY_9 -> "9"; case InputConstants.KEY_GRAVE -> "grave";
            case InputConstants.KEY_MINUS -> "minus"; case InputConstants.KEY_EQUALS -> "equal";
            case InputConstants.KEY_LBRACKET -> "left_bracket"; case InputConstants.KEY_RBRACKET -> "right_bracket";
            case InputConstants.KEY_APOSTROPHE -> "apostrophe"; case InputConstants.KEY_BACKSLASH -> "backslash";
            default -> null;
        };
    }

    private Component inputLabel(String keyName) {
        InputConstants.Key key = InputConstants.getKey(keyName);
        if (keyName.startsWith("key.keyboard.")) {
            String physical = physicalKey(key.getValue());
            if (physical != null) return Component.literal(keyboardLayout.label(physical, key.getDisplayName().getString()));
        }
        return key.getDisplayName();
    }

    private Component bindingDisplay(Binding value) {
        String prefix = (value.ctrl() ? "Ctrl + " : "") + (value.shift() ? "Shift + " : "")
            + (value.alt() ? "Alt + " : "") + (value.command() ? "Cmd + " : "");
        var result = Component.literal(prefix).append(inputLabel(value.key()));
        for (String extra : value.extraKeys()) result.append(" + ").append(inputLabel(extra));
        return result;
    }

    private List<VirtualKey> weightedKeys(String[] labels, int[] codes, float[] widths) {
        List<VirtualKey> result = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) result.add(key(codes[i], labels[i], widths[i]));
        return result;
    }

    private List<VirtualKey> keys(String[] labels, int[] codes) {
        List<VirtualKey> result = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) result.add(key(codes[i], labels[i], 1));
        return result;
    }

    private void onVirtualKey(VirtualKey key) {
        if (selectedMapping != null) {
            if (!key.mouse() && key.code() == InputConstants.KEY_ESCAPE) unbindSelected();
            else bindCaptured(keyName(key), virtualCtrl, virtualShift, virtualAlt);
        } else {
            virtualKeyFilter = keyName(key);
            settingVirtualKeyFilter = true;
            searchQuery = "";
            searchBox.setValue("");
            settingVirtualKeyFilter = false;
            bindingScroll = 0;
            if (singlePane) compactPage = EditorViewport.Page.ACTIONS;
            refreshRows();
        }
    }

    private String keyName(VirtualKey key) {
        return (key.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYBOARD).getOrCreate(key.code()).getName();
    }

    private void captureMappingAt(int slot) {
        KeyMapping mapping = mappingAt(slot);
        if (mapping == null) return;
        selectedMapping = mapping;
        nativeCapture.clear();
        virtualCtrl = virtualShift = virtualAlt = false;
        capturing = true;
        pendingModifier = null;
        searchBox.setFocused(false);
        setFocused(null);
        statusMessage = Component.translatable(KeysetClient.multiKeyCapture(mapping) ? "keyset.native_capture_prompt" : "keyset.capture_prompt",
            KeysetClient.name(mapping));
        statusIsError = false;
        if (singlePane) compactPage = EditorViewport.Page.INPUT;
        refreshRows();
    }

    private void selectMappingAt(int slot) {
        captureMappingAt(slot);
    }

    private void unbindMappingAt(int slot) {
        KeyMapping mapping = mappingAt(slot);
        if (mapping == null) return;
        selectedMapping = mapping;
        unbindSelected();
    }

    private void inspectConflictAt(int slot) {
        KeyMapping mapping = mappingAt(slot);
        if (mapping != null && bindingIndex.conflicts(mapping)) {
            minecraft.setScreenAndShow(new BindingConflictScreen(this, mapping));
        }
    }

    private KeyMapping mappingAt(int slot) {
        ActionListEntry entry = entryAt(slot);
        return entry == null || entry.isSection() ? null : entry.mapping();
    }

    private ActionListEntry entryAt(int slot) {
        if (slot < 0 || slot >= visibleRowCapacity()) return null;
        int index = bindingScroll + slot;
        return index >= 0 && index < displayEntries.size() ? displayEntries.get(index) : null;
    }

    private void toggleSectionAt(int slot) {
        ActionListEntry entry = entryAt(slot);
        if (entry == null || !entry.isSection()) return;
        boolean collapsed = COLLAPSED_SECTIONS.add(entry.category().id().toString());
        if (!collapsed) COLLAPSED_SECTIONS.remove(entry.category().id().toString());
        collapseRevision++;
        if (collapsed && selectedMapping != null && selectedMapping.getCategory().equals(entry.category())) {
            clearSelection();
        } else {
            refreshRows();
        }
    }

    private void bindCaptured(String key, boolean ctrl, boolean shift, boolean alt) {
        bindCaptured(key, ctrl, shift, alt, false);
    }

    private void bindCaptured(String key, boolean ctrl, boolean shift, boolean alt, boolean command) {
        bindCaptured(new Binding(key, ctrl, shift, alt, command));
    }

    private void bindCaptured(Binding value) {
        if (selectedMapping == null) {
            capturing = false;
            statusMessage = null;
            return;
        }
        KeyMapping mapping = selectedMapping;
        pendingModifier = null;
        // Keep the selected input pinned, including when Escape removes its last action.
        if (runAction(() -> KeysetClient.bind(mapping, value))) {
            finishAssignment(mapping, value);
        } else {
            capturing = true;
        }
    }

    private void finishAssignment(KeyMapping mapping, Binding value) {
        clearSelection();
        setFocused(null);
        statusMessage = Component.translatable("keyset.saved_binding", KeysetClient.name(mapping),
            bindingDisplay(value));
        statusIsError = false;
        if (singlePane) {
            compactPage = EditorViewport.Page.ACTIONS;
            refreshRows();
        }
    }

    private void unbindSelected() {
        if (selectedMapping == null) return;
        bindCaptured(InputConstants.UNKNOWN.getName(), false, false, false, false);
    }

    private void resetSelected() {
        if (selectedMapping == null) return;
        KeyMapping mapping = selectedMapping;
        if (runAction(() -> KeysetClient.reset(mapping))) finishAssignment(mapping, KeysetClient.binding(mapping));
    }

    private void confirmResetAll() {
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) runAction(KeysetClient::resetAll);
            minecraft.setScreenAndShow(this);
        }, Component.translatable("keyset.reset_all_title"), Component.translatable("keyset.reset_all_prompt"),
            Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
    }

    private void confirmDeleteProfile() {
        Profile active = KeysetClient.profiles().active();
        if (active == null || currentProfiles().size() <= 1) return;
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) runAction(() -> KeysetClient.deleteProfile(active.id()));
            minecraft.setScreenAndShow(this);
        }, Component.translatable("keyset.profile_delete_title"),
            Component.translatable("keyset.profile_delete_prompt", active.name()),
            Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
    }

    private void openCreateDialog() {
        openNameDialog(Component.translatable("keyset.profile_new_title"), "", KeysetClient::createProfile);
    }

    private void openRenameDialog() {
        Profile active = KeysetClient.profiles().active();
        if (active == null) return;
        openNameDialog(Component.translatable("keyset.profile_rename_title"), active.name(),
            name -> KeysetClient.renameProfile(active.id(), name));
    }

    private void openNameDialog(Component title, String initial, NameAction action) {
        minecraft.setScreenAndShow(new NameDialog(title, initial, action));
    }

    private boolean runAction(IoAction action) {
        boolean success;
        try {
            action.run();
            statusMessage = null;
            statusIsError = false;
            success = true;
        } catch (IOException | IllegalArgumentException exception) {
            statusMessage = localizedError(exception);
            statusIsError = true;
            success = false;
        }
        rebuildModel();
        refreshRows();
        refreshProfileButtons();
        observedRevision = KeysetClient.revision();
        return success;
    }

    @Override
    public void tick() {
        super.tick();
        String sharedError = Objects.toString(KeysetClient.error(), "");
        if (!sharedError.equals(observedSharedError)) {
            if (sharedError.startsWith("keyset.error.")) {
                statusMessage = Component.translatable(sharedError);
                statusIsError = true;
            } else if (!observedSharedError.isEmpty() && statusIsError) {
                statusMessage = null;
                statusIsError = false;
            }
            observedSharedError = sharedError;
        }
        updateCompactStatus();
        refreshForSharedRevision();
    }

    private boolean refreshForSharedRevision() {
        long revision = KeysetClient.revision();
        if (revision == observedRevision || isCapturingBindings()) return false;
        observedRevision = revision;
        rebuildModel();
        if (category != null && !categories.contains(category)) category = null;
        refreshRows();
        refreshProfileButtons();
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    private void drawText(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
        graphics.text(font, value, x, y, color, false);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x, y, x + w, y + 1, BORDER);
    }

    private static void rounded(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        if (w < 4 || h < 4) { graphics.fill(x, y, x + w, y + h, color); return; }
        graphics.fill(x + 2, y, x + w - 2, y + h, color);
        graphics.fill(x, y + 2, x + w, y + h - 2, color);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    private static void curved(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        graphics.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            graphics.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    private static void mouseSilhouette(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        for (int row = 0; row < h; row++) {
            double t = row / (double) Math.max(1, h - 1);
            double half;
            if (t < 0.18) {
                double arc = (0.18 - t) / 0.18;
                half = 0.28 + 0.22 * Math.sqrt(Math.max(0, 1 - arc * arc));
            } else if (t < 0.55) {
                half = 0.5;
            } else if (t < 0.82) {
                half = 0.5 - 0.06 * Math.sin((t - 0.55) / 0.27 * Math.PI / 2);
            } else {
                double arc = (t - 0.82) / 0.18;
                half = 0.26 + 0.18 * Math.sqrt(Math.max(0, 1 - arc * arc));
            }
            int inset = (int) Math.ceil((0.5 - half) * w);
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private static void mouseMainCap(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean right, int color) {
        int radius = Math.min(12, Math.max(1, w / 2));
        for (int row = 0; row < h; row++) {
            int outerInset = 0;
            if (row < radius) {
                double dy = radius - row - 0.5;
                outerInset = (int) Math.ceil(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            } else if (row >= h - 3) {
                outerInset = row - (h - 3);
            }
            int innerInset = row == 0 || row == h - 1 ? 2 : 0;
            graphics.fill(x + (right ? innerInset : outerInset), y + row,
                x + w - (right ? outerInset : innerInset), y + row + 1, color);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (singlePane) {
            KeysetBranding.drawLogo(graphics, 8, 6, 20, 20);
            graphics.text(font, "KEYSET", 32, 7, TEXT, false);
            graphics.text(font, KeysetBranding.VERSION, 32, 19, MUTED, false);
            graphics.fill(8, viewport.contentTop() - 2, width - 8, viewport.contentTop() - 1, BORDER);
            graphics.fill(8, viewport.footerY() - 6, width - 8, viewport.footerY() - 5, BORDER);
        } else {
            KeysetBranding.drawLogo(graphics, 16, 17, 30, 30);
            graphics.text(font, "KEYSET", 53, 20, TEXT, false);
            graphics.text(font, KeysetBranding.VERSION + "  /  26.3", 53, 36, MUTED, false);
            graphics.text(font, Component.translatable("keyset.profile"), profileCycleButton.getX(), 12, MUTED, false);
            graphics.fill(16, 57, width - 16, 58, BORDER);
            graphics.fill(16, height - 48, width - 16, height - 47, BORDER);
        }

        if (showInput()) {
            int cardTop = singlePane ? viewport.contentTop() : 64;
            drawCard(graphics, sidebarX, cardTop, sidebarWidth, inputCardBottom - cardTop);
            graphics.fill(sidebarX + 8, keyboardPanelY - 4, sidebarX + sidebarWidth - 8,
                keyboardPanelY + keyStep * 6 + 4, 0x38000000);
        }
        if (showCategories()) drawCard(graphics, sidebarX, categoryCardY, sidebarWidth,
            singlePane ? inputCardBottom - categoryCardY : height - 54 - categoryCardY);
        if (showActions()) drawCard(graphics, mainX, singlePane ? viewport.contentTop() : 64, mainWidth,
            singlePane ? listBottom - viewport.contentTop() : height - 118);
        if (!singlePane) {
        int dividerX = dividerDragging ? draggedDividerX : sidebarX + sidebarWidth + 6;
        boolean dividerHover = mouseX >= dividerX - 5 && mouseX <= dividerX + 5
            && mouseY >= 64 && mouseY <= height - 52;
        graphics.fill(dividerX - 1, 64, dividerX + 1, height - 52,
            dividerDragging || dividerHover ? TEAL : BORDER);
        rounded(graphics, dividerX - 3, height / 2 - 14, 6, 28,
            dividerDragging || dividerHover ? 0xC094E4D3 : 0x805C6561);
        }
        if (showActions()) {
        if (!singlePane) {
        Component listTitle = virtualKeyFilter == null ? Component.translatable("keyset.bindings")
            : Component.translatable("keyset.bindings_for_key", inputLabel(virtualKeyFilter));
        drawText(graphics, listTitle, mainX + 12, listTitleY, mainWidth - 110, TEXT);
        Component count = Component.translatable("keyset.binding_count", filteredMappings.size());
        graphics.text(font, count, mainX + mainWidth - 12 - font.width(count), listTitleY, MUTED, false);
        }
        int searchLeft = mainX + (singlePane ? 4 : 8);
        int searchRight = Math.min(mainX + mainWidth - 8, searchLeft + searchBox.getWidth() + 30);
        graphics.fill(searchLeft, searchY - (singlePane ? 2 : 4), searchRight,
            searchY + viewport.searchHeight() + (singlePane ? 2 : -1), KEY_SHADOW);
        graphics.fill(searchLeft, searchY + viewport.searchHeight() - 1, searchRight,
            searchY + viewport.searchHeight(), searchBox.isFocused() ? TEAL : BORDER);
        if (!singlePane) {
            graphics.text(font, Component.translatable("keyset.action_column"), mainX + 16, listTop - 16, MUTED, false);
            if (!rowBindingButtons.isEmpty()) graphics.text(font, Component.translatable("keyset.key_column"),
                rowBindingButtons.get(0).getX() + 5, listTop - 16, MUTED, false);
        }

        for (int i = 0; i < visibleRowCapacity(); i++) {
            KeyMapping mapping = mappingAt(i);
            if (mapping == null) continue;
            int y = listTop + i * rowHeight;
            boolean hovered = mouseX >= mainX + 8 && mouseX < mainX + mainWidth - 12
                && mouseY >= y && mouseY < y + rowHeight - 2;
            int color = selectedMapping == mapping ? SELECTED : hovered ? PANEL_LIGHT
                : (bindingScroll + i) % 2 == 0 ? ROW : ROW_ALTERNATE;
            graphics.fill(mainX + 8, y, mainX + mainWidth - 12, y + rowHeight - 2, color);
            if (selectedMapping == mapping) graphics.fill(mainX + 8, y, mainX + 10, y + rowHeight - 2, TEAL);
        }
        if (filteredMappings.isEmpty()) {
            Component empty = virtualKeyFilter == null ? Component.translatable("keyset.no_bindings")
                : Component.translatable("keyset.no_bindings_for_key", inputLabel(virtualKeyFilter));
            drawText(graphics, empty, mainX + 16, listTop + (singlePane ? 3 : 18), mainWidth - 32, MUTED);
        }
        if (displayEntries.size() > visibleRowCapacity()) {
            int trackHeight = visibleRowCapacity() * rowHeight - 2;
            int thumbHeight = Math.max(18, trackHeight * visibleRowCapacity() / displayEntries.size());
            int thumbY = listTop + (trackHeight - thumbHeight) * bindingScroll
                / Math.max(1, displayEntries.size() - visibleRowCapacity());
            graphics.fill(mainX + mainWidth - 6, listTop, mainX + mainWidth - 4, listTop + trackHeight, BORDER);
            graphics.fill(mainX + mainWidth - 6, thumbY, mainX + mainWidth - 4, thumbY + thumbHeight, MUTED);
        }
        }

        if (mouseMode && showInput()) {
            mouseSilhouette(graphics, mouseBodyX, mouseBodyY + 3, mouseBodyWidth, mouseBodyHeight, 0x70000000);
            mouseSilhouette(graphics, mouseBodyX, mouseBodyY, mouseBodyWidth, mouseBodyHeight, 0xFF53615B);
            mouseSilhouette(graphics, mouseBodyX + 1, mouseBodyY + 1, mouseBodyWidth - 2, mouseBodyHeight - 3, 0xF0151B19);
            int center = mouseBodyX + mouseBodyWidth / 2;
            int seamY = mouseBodyY + Math.round(mouseBodyHeight * 0.46f);
            graphics.fill(center, seamY, center + 1, mouseBodyY + Math.round(mouseBodyHeight * 0.54f), 0xFF323D38);
            rounded(graphics, center - 2, mouseBodyY + Math.round(mouseBodyHeight * 0.78f), 4, 7, 0xFF7DB6A7);
            int extraX = mouseBodyX + mouseBodyWidth + (compact ? 16 : 26);
            int extraY = mouseBodyY + (compact ? 12 : 36);
            int extraWidth = compact ? 56 : 74;
            int extraStep = compact ? 20 : 27;
            drawText(graphics, Component.translatable("keyset.mouse_extra"), extraX,
                extraY - 15, extraWidth, MUTED);
            graphics.fill(extraX - 6, extraY, extraX - 5, extraY + extraStep * 3 - 4, BORDER);
        }
        if (!compact && showInput()) {
            int legendY = summaryY - 16;
            graphics.fill(sidebarX + 16, legendY + 2, sidebarX + 20, legendY + 6, TEAL);
            graphics.text(font, Component.translatable("keyset.legend_assigned"), sidebarX + 26, legendY, MUTED, false);
            graphics.fill(sidebarX + 140, legendY + 2, sidebarX + 144, legendY + 6, RED);
            graphics.text(font, Component.translatable("keyset.legend_conflict"), sidebarX + 150, legendY, MUTED, false);
        }
        if (!singlePane) {
        graphics.fill(sidebarX + 12, summaryY - 4, sidebarX + sidebarWidth - 12, summaryY - 3, BORDER);
        if (selectedMapping == null) {
            drawText(graphics, Component.translatable("keyset.selection_empty"), sidebarX + 14, summaryY + 8, sidebarWidth - 28, TEXT);
            drawText(graphics, Component.translatable("keyset.selection_hint"), sidebarX + 14, summaryY + 24, sidebarWidth - 28, MUTED);
        } else {
            graphics.text(font, Component.translatable(capturing ? "keyset.listening" : "keyset.selected_action"),
                sidebarX + 14, summaryY + 6, TEAL, false);
            drawText(graphics, KeysetClient.name(selectedMapping), sidebarX + 14, summaryY + 22,
                sidebarWidth - 212, TEXT);
        }
        }
        int sidebarItemCount = modFilterMode ? mods.size() : categories.size();
        int sidebarVisibleCount = modFilterMode ? Math.max(1, categoryButtons.size() / 2) : categoryButtons.size();
        int sidebarScroll = modFilterMode ? modScroll : categoryScroll;
        if (showCategories() && sidebarItemCount > sidebarVisibleCount && !categoryButtons.isEmpty()) {
            graphics.fill(sidebarX + sidebarWidth - 5, categoryListTop, sidebarX + sidebarWidth - 3, categoryListBottom, BORDER);
            int total = categoryListBottom - categoryListTop;
            int thumb = Math.max(12, total * sidebarVisibleCount / sidebarItemCount);
            int y = categoryListTop + (total - thumb) * sidebarScroll / Math.max(1, sidebarItemCount - sidebarVisibleCount);
            graphics.fill(sidebarX + sidebarWidth - 5, y, sidebarX + sidebarWidth - 3, y + thumb, MUTED);
        }
        Component footer = statusMessage != null ? statusMessage : Component.translatable("keyset.footer_hint");
        if (!singlePane) drawText(graphics, footer, sidebarX + 2, height - 31, sidebarWidth - 4, statusIsError ? RED : MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && isDividerAt(event.x(), event.y())) {
            dividerDragging = true;
            draggedDividerX = clampDividerX((int) event.x());
            setDragging(true);
            setFocused(null);
            return true;
        }
        if (capturing && event.button() == InputConstants.MOUSE_BUTTON_LEFT && isNavigationAt(event.x(), event.y())) {
            clearSelection();
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (capturing && !handled) {
            int button = event.button();
            String key = InputConstants.Type.MOUSE.getOrCreate(button).getName();
            int mods = event.modifiers();
            if (KeysetClient.multiKeyCapture(selectedMapping)) {
                collectNativeInput(key, mods);
                return true;
            }
            bindCaptured(key, (mods & InputConstants.MOD_CONTROL) != 0, (mods & InputConstants.MOD_SHIFT) != 0,
                (mods & InputConstants.MOD_ALT) != 0, (mods & InputConstants.MOD_SUPER) != 0);
            return true;
        }
        return handled;
    }

    private void collectNativeInput(String key, int modifiers) {
        // Include modifiers already held when the action was clicked; new presses retain their order.
        includeHeldModifier(key, modifiers, InputConstants.MOD_CONTROL, InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
        includeHeldModifier(key, modifiers, InputConstants.MOD_SHIFT, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
        includeHeldModifier(key, modifiers, InputConstants.MOD_ALT, InputConstants.KEY_LALT, InputConstants.KEY_RALT);
        includeHeldModifier(key, modifiers, InputConstants.MOD_SUPER, InputConstants.KEY_LGUI, InputConstants.KEY_RGUI);
        nativeCapture.press(key);
        statusMessage = Component.translatable("keyset.native_release", bindingDisplay(nativeCapture.preview()));
        refreshVisibleRows();
    }

    private void includeHeldModifier(String key, int modifiers, int mask, int leftCode, int rightCode) {
        String left = InputConstants.Type.KEYBOARD.getOrCreate(leftCode).getName();
        String right = InputConstants.Type.KEYBOARD.getOrCreate(rightCode).getName();
        if ((modifiers & mask) != 0 && !key.equals(left) && !key.equals(right)
                && !nativeCapture.contains(left) && !nativeCapture.contains(right)) {
            nativeCapture.include(InputConstants.isKeyDown(rightCode) ? right : left);
        }
    }

    private void bindNativeCaptured(Binding value) {
        bindCaptured(new Binding(value.key(), virtualCtrl, virtualShift, virtualAlt, false, value.extraKeys()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dividerDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dividerDragging = false;
            setDragging(false);
            double ratio = Math.max(0.35, Math.min(0.65,
                (draggedDividerX - sidebarX - 6) / (double) Math.max(1, width - 44)));
            try {
                KeysetClient.setSidebarRatio(ratio);
                observedRevision = KeysetClient.revision();
            } catch (IOException exception) {
                statusMessage = localizedError(exception);
                statusIsError = true;
            }
            init();
            return true;
        }
        if (capturing && selectedMapping != null && KeysetClient.multiKeyCapture(selectedMapping)) {
            nativeCapture.release(InputConstants.Type.MOUSE.getOrCreate(event.button()).getName()).ifPresent(this::bindNativeCaptured);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dividerDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggedDividerX = clampDividerX((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private boolean isDividerAt(double x, double y) {
        if (singlePane) return false;
        int dividerX = sidebarX + sidebarWidth + 6;
        return y >= 64 && y <= height - 52 && x >= dividerX - 6 && x <= dividerX + 6;
    }

    private int clampDividerX(int x) {
        int availablePanels = Math.max(1, width - 44);
        int[] minimumWidths = minimumPanelWidths(availablePanels);
        int minimumSidebar = minimumWidths[0];
        int minimumMain = minimumWidths[1];
        int minimumByRatio = (int) Math.ceil(availablePanels * 0.35);
        int maximumByRatio = (int) Math.floor(availablePanels * 0.65);
        int minimum = Math.max(minimumSidebar, minimumByRatio);
        int maximum = Math.min(availablePanels - minimumMain, maximumByRatio);
        return Math.max(sidebarX + minimum + 6,
            Math.min(sidebarX + maximum + 6, x));
    }

    private int[] minimumPanelWidths(int available) {
        if (available >= 640) return new int[] {380, 260};
        int minimumSidebar = Math.min(280, Math.max(120, available * 32 / 100));
        int minimumMain = Math.min(240, Math.max(160, available * 42 / 100));
        if (minimumSidebar + minimumMain > available) {
            minimumSidebar = Math.max(1, available * minimumSidebar / (minimumSidebar + minimumMain));
            minimumMain = Math.max(1, available - minimumSidebar);
        }
        return new int[] {minimumSidebar, minimumMain};
    }

    /** True while the screen is handling a local binding capture or splitter drag. */
    public boolean isCapturingBindings() {
        return capturing || dividerDragging;
    }

    private boolean isNavigationAt(double x, double y) {
        if (compactTabs.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (searchBox.isMouseOver(x, y) || conflictsButton.isMouseOver(x, y) || favoritesButton.isMouseOver(x, y)
            || allCategoryButton.isMouseOver(x, y) || categoryFilterButton.isMouseOver(x, y)
            || modFilterButton.isMouseOver(x, y) || profileCycleButton.isMouseOver(x, y)
            || newProfileButton.isMouseOver(x, y) || renameProfileButton.isMouseOver(x, y)
            || deleteProfileButton.isMouseOver(x, y)) return true;
        if (rowSectionButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (rowFavoriteButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (rowConflictButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (filterChipButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        return categoryButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overCategoryList = mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
            && mouseY >= categoryListTop && mouseY <= categoryListBottom;
        boolean overActionList = mouseX >= mainX && mouseX <= mainX + mainWidth
            && mouseY >= listTop && mouseY <= listBottom;

        EditorViewport.ScrollTarget target = EditorViewport.scrollTarget(singlePane, compactPage,
            overActionList, overCategoryList);
        if (target == EditorViewport.ScrollTarget.ACTIONS) {
            int next = clampScroll(bindingScroll - (int) Math.signum(scrollY) * 3, displayEntries.size(), visibleRowCapacity());
            if (next != bindingScroll) {
                bindingScroll = next;
                refreshVisibleRows();
            }
            return true;
        }
        if (target == EditorViewport.ScrollTarget.FILTERS) {
            if (modFilterMode) {
                modScroll = clampScroll(modScroll - (int) Math.signum(scrollY) * 2,
                    mods.size(), Math.max(1, categoryButtons.size() / 2));
            } else {
                categoryScroll = clampScroll(categoryScroll - (int) Math.signum(scrollY) * 2,
                    categories.size(), categoryButtons.size());
            }
            refreshCategoryButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int clampScroll(int value, int itemCount, int visibleCount) {
        return Math.max(0, Math.min(Math.max(0, itemCount - visibleCount), value));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE) {
                nativeCapture.clear();
                unbindSelected();
                return true;
            }
            if (KeysetClient.multiKeyCapture(selectedMapping)) {
                collectNativeInput(InputConstants.getKey(event).getName(), event.modifiers());
                return true;
            }
            if (isModifierKey(key)) {
                pendingModifier = InputConstants.getKey(event);
                return true;
            }
            String name = InputConstants.getKey(event).getName();
            int mods = event.modifiers();
            pendingModifier = null;
            bindCaptured(name, (mods & InputConstants.MOD_CONTROL) != 0,
                (mods & InputConstants.MOD_SHIFT) != 0, (mods & InputConstants.MOD_ALT) != 0,
                (mods & InputConstants.MOD_SUPER) != 0);
            return true;
        }
        if (event.key() == InputConstants.KEY_F && (event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
            if (searchBox != null) {
                if (singlePane && compactPage != EditorViewport.Page.ACTIONS) {
                    compactPage = EditorViewport.Page.ACTIONS;
                    refreshRows();
                }
                searchBox.setFocused(true);
                setFocused(searchBox);
                searchBox.moveCursorToEnd(false);
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (capturing && selectedMapping != null && KeysetClient.multiKeyCapture(selectedMapping)) {
            nativeCapture.release(InputConstants.getKey(event).getName()).ifPresent(this::bindNativeCaptured);
            return true;
        }
        if (capturing && pendingModifier != null && event.key() == pendingModifier.getValue()) {
            String modifierKey = pendingModifier.getName();
            pendingModifier = null;
            bindCaptured(modifierKey, false, false, false);
            return true;
        }
        if (capturing) return true;
        return super.keyReleased(event);
    }

    private boolean isModifierKey(int key) {
        return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
            || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
            || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
            || key == InputConstants.KEY_LGUI || key == InputConstants.KEY_RGUI;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        try {
            KeysetClient.save();
            minecraft.setScreenAndShow(parent);
        } catch (IOException exception) {
            statusMessage = localizedError(exception);
            statusIsError = true;
        }
    }

    private Component localizedError(Exception exception) {
        if (exception instanceof dev.keyset.profile.SharedSettingsConflictException) {
            return KeysetClient.operationError(exception);
        }
        String detail = KeysetClient.error();
        if (detail != null && detail.startsWith("keyset.error.") && !detail.endsWith("recovered")) {
            return Component.translatable(detail);
        }
        if (detail == null || detail.isBlank() || detail.endsWith("recovered")) detail = exception.getMessage();
        return detail == null || detail.isBlank()
            ? Component.translatable("keyset.error_generic")
            : Component.translatable("keyset.error", detail);
    }

    private final class NameDialog extends Screen {
        private final Component dialogTitle;
        private final String initialValue;
        private final NameAction action;
        private EditBox nameBox;
        private Component error;

        private NameDialog(Component title, String initialValue, NameAction action) {
            super(title);
            this.dialogTitle = title;
            this.initialValue = initialValue;
            this.action = action;
        }

        @Override
        protected void init() {
            clearWidgets();
            String currentValue = nameBox == null ? initialValue : nameBox.getValue();
            int panelWidth = Math.min(310, width - 20);
            int panelHeight = 102;
            int x = (width - panelWidth) / 2;
            int y = (height - panelHeight) / 2;
            nameBox = new EditBox(font, x + 16, y + 38, panelWidth - 32, 16,
                Component.translatable("keyset.profile_name_hint"));
            nameBox.setBordered(false);
            nameBox.setTextColor(TEXT);
            nameBox.setMaxLength(40);
            nameBox.setValue(currentValue);
            addRenderableWidget(nameBox);
            Button cancel = new FlatButton(x + 12, y + 62, (panelWidth - 30) / 2, 22, Component.translatable("keyset.cancel"), button -> returnToEditor());
            addRenderableWidget(cancel);
            Button confirm = styled(new FlatButton(x + 18 + (panelWidth - 30) / 2, y + 62, (panelWidth - 30) / 2, 22, Component.translatable("keyset.confirm"), button -> submit()), ButtonStyle.PRIMARY, true);
            addRenderableWidget(confirm);
            setInitialFocus(nameBox);
        }

        private void submit() {
            String value = nameBox.getValue().trim();
            try {
                action.apply(value);
                returnToEditor();
            } catch (IOException | IllegalArgumentException exception) {
                error = localizedError(exception);
            }
        }

        private void returnToEditor() {
            minecraft.setScreenAndShow(KeysetScreen.this);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                returnToEditor();
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                submit();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int panelWidth = Math.min(310, width - 20);
            int panelHeight = 102;
            int x = (width - panelWidth) / 2;
            int y = (height - panelHeight) / 2;
            drawCard(graphics, x, y, panelWidth, panelHeight);
            graphics.fill(x + 12, y + 31, x + panelWidth - 12, y + 55, KEY_SHADOW);
            graphics.fill(x + 12, y + 54, x + panelWidth - 12, y + 55, nameBox.isFocused() ? TEAL : BORDER);
            graphics.text(font, dialogTitle, x + 13, y + 12, TEXT);
            if (error != null) graphics.text(font, error, x + 13, y + 87, RED);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private enum VisualState { NORMAL, ASSIGNED, SELECTED, CONFLICT }
    private enum ButtonStyle { ACTION, PRIMARY, TAB, KEYCAP, MOUSE_LEFT, MOUSE_RIGHT, MOUSE_WHEEL, MOUSE_SIDE, MOUSE_EXTRA, ROW, SECTION, BINDING, QUIET }

    private static final class FlatButton extends Button {
        private VisualState visualState = VisualState.NORMAL;
        private ButtonStyle style = ButtonStyle.ACTION;
        private boolean centered = true;

        private FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused() && active;
            boolean selected = visualState == VisualState.SELECTED;
            boolean conflict = visualState == VisualState.CONFLICT;
            int color = !active ? 0x70FFFFFF : conflict ? RED
                : selected && (style == ButtonStyle.TAB || style == ButtonStyle.QUIET) ? TEAL : TEXT;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean mouseMain = style == ButtonStyle.MOUSE_LEFT || style == ButtonStyle.MOUSE_RIGHT;
            boolean mouseStyle = mouseMain || style == ButtonStyle.MOUSE_SIDE || style == ButtonStyle.MOUSE_WHEEL || style == ButtonStyle.MOUSE_EXTRA;
            if (mouseStyle) {
                int accent = conflict ? RED : selected ? TEAL : visualState == VisualState.ASSIGNED ? 0xFF7DB6A7 : KEY_EDGE;
                int face = selected ? 0xFF263D35 : hover ? 0xFF35433D : 0xFF222B26;
                color = !active ? 0x70FFFFFF : selected ? TEAL : TEXT;
                if (style == ButtonStyle.MOUSE_WHEEL) {
                    int wheelWidth = Math.min(10, w - 4);
                    int wheelX = x + (w - wheelWidth) / 2;
                    int wheelHeight = h - 13;
                    curved(graphics, wheelX - 1, y - 1, wheelWidth + 2, wheelHeight + 2, 4, 0xFF0A100D);
                    curved(graphics, wheelX, y, wheelWidth, wheelHeight, 3, selected || hover ? accent : KEY_EDGE);
                    curved(graphics, wheelX + 1, y + 1, wheelWidth - 2, wheelHeight - 2, 2, face);
                    for (int rib = y + 4; rib < y + wheelHeight - 2; rib += 4) {
                        graphics.fill(wheelX + 2, rib, wheelX + wheelWidth - 2, rib + 1, 0xFF718078);
                    }
                    if (visualState != VisualState.NORMAL) {
                        graphics.fill(x + 3, y + h - 1, x + w - 3, y + h, accent);
                    }
                } else if (mouseMain) {
                    boolean right = style == ButtonStyle.MOUSE_RIGHT;
                    mouseMainCap(graphics, x, y + 1, w, h, right, 0xFF0A100D);
                    mouseMainCap(graphics, x, y, w, h - 1, right, selected || isFocused() ? TEAL : 0xFF52635A);
                    mouseMainCap(graphics, x + 1, y + 1, w - 2, h - 3, right, face);
                    if (visualState != VisualState.NORMAL) {
                        int markWidth = Math.min(10, w - 6);
                        graphics.fill(x + (w - markWidth) / 2, y + h - 6,
                            x + (w + markWidth) / 2, y + h - 4, accent);
                    }
                } else if (style == ButtonStyle.MOUSE_EXTRA) {
                    graphics.fill(x, y, x + w, y + h, hover || selected ? face : 0x48000000);
                    graphics.fill(x + 7, y + h - 1, x + w - 5, y + h, BORDER);
                    graphics.fill(x + 2, y + h / 2 - 2, x + 4, y + h / 2 + 2, accent);
                } else {
                    curved(graphics, x, y + 1, w, h, 3, 0xFF0A100D);
                    curved(graphics, x, y, w, h - 1, 3, selected || hover ? accent : 0xFF52635A);
                    curved(graphics, x + 1, y + 1, w - 2, h - 3, 2, face);
                    if (visualState != VisualState.NORMAL) {
                        graphics.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, accent);
                    }
                }
            } else if (style == ButtonStyle.KEYCAP) {
                rounded(graphics, x, y + 2, w, h - 2, KEY_SHADOW);
                rounded(graphics, x, y, w, h - 3, selected ? TEAL : conflict ? 0xFF865858 : KEY_EDGE);
                rounded(graphics, x + 1, y + 1, w - 2, h - 5, selected ? SELECTED : hover ? HOVER : KEY_FACE);
                if (visualState != VisualState.NORMAL) {
                    int stateColor = conflict ? RED : selected ? TEAL : 0xFF74AA9E;
                    graphics.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, stateColor);
                }
            } else if (style == ButtonStyle.ROW) {
                // The screen highlights the whole action row, including its controls.
            } else if (style == ButtonStyle.SECTION) {
                graphics.fill(x, y, x + w, y + h, hover ? CONTROL : PANEL);
                graphics.fill(x + 6, y + h - 1, x + w - 6, y + h, selected ? TEAL : BORDER);
                color = selected ? TEAL : hover ? TEXT : MUTED;
            } else {
                int fill = selected ? SELECTED : hover ? HOVER : CONTROL;
                if (style == ButtonStyle.BINDING && !selected) {
                    fill = conflict ? hover ? 0xA0451C1C : 0x603A1515 : hover ? HOVER : ROW;
                }
                if (!active) fill = 0x40000000;
                if (style != ButtonStyle.QUIET || hover || selected) {
                    graphics.fill(x, y, x + w, y + h, fill);
                }
                if (style == ButtonStyle.BINDING) {
                    graphics.fill(x, y + h - 1, x + w, y + h, conflict ? 0xFF865858 : KEY_SHADOW);
                }
                if (active && (selected || style == ButtonStyle.PRIMARY)) {
                    graphics.fill(x, y + h - 2, x + w, y + h, TEAL);
                }
            }
            if (active && isFocused() && !mouseStyle) graphics.outline(x, y, w, h, TEXT);
            Font font = Minecraft.getInstance().font;
            int inset = style == ButtonStyle.KEYCAP || style == ButtonStyle.MOUSE_WHEEL ? 2
                : style == ButtonStyle.SECTION ? 18 : centered ? 8 : 12;
            int available = Math.max(1, w - inset);
            String label = getMessage().getString();
            if ((style == ButtonStyle.KEYCAP || mouseStyle)
                    && (font.width(label) > available || font.lineHeight > h - 3)) {
                float scale = Math.min(available / (float) Math.max(1, font.width(label)),
                    Math.max(1, h - 3) / (float) font.lineHeight);
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + (w - font.width(label) * scale) / 2,
                        y + (h - font.lineHeight * scale) / 2 - 1);
                graphics.pose().scale(scale, scale);
                graphics.text(font, label, 0, 0, color, false);
                graphics.pose().popMatrix();
                return;
            }
            if (font.width(label) > available) {
                String tail = style == ButtonStyle.KEYCAP ? "" : "…";
                label = font.plainSubstrByWidth(label, Math.max(1, available - font.width(tail))) + tail;
            }
            int textX = centered ? x + (w - font.width(label)) / 2
                : x + (style == ButtonStyle.SECTION ? 10 : 6);
            int textY = y + (h - font.lineHeight) / 2 - (style == ButtonStyle.KEYCAP ? 1 : 0);
            if (style == ButtonStyle.MOUSE_WHEEL) textY = y + h - 13;
            graphics.text(font, label, textX, textY, color, false);
        }
    }

    private record VirtualKey(int code, String label, boolean mouse, float width) {
        private VirtualKey(int code, String label, boolean mouse) {
            this(code, label, mouse, 1);
        }
    }

    private record KeyboardRow(List<VirtualKey> main, List<VirtualKey> navigation, List<VirtualKey> numpad,
                               float mainOffset) {}

    private record FilterState(KeyMapping.Category category, String modFilter, boolean conflictsOnly, boolean favoritesOnly,
                               String query, String key, int collapseRevision) {}
    private record FilterChip(int index, Component label, Component tooltip) {}
    private record ActionListEntry(KeyMapping mapping, KeyMapping.Category category, int count) {
        private boolean isSection() { return mapping == null; }
    }
    private record MappingView(Component name, Component bindingLabel, String searchName, String categoryName,
                               String categoryId, String keyName, String keyLabel, String modifiers,
                               String modId, String modName, Tooltip tooltip) {}
    private record ModFilter(String id, String name, int count) {}

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface NameAction {
        void apply(String name) throws IOException;
    }
}
