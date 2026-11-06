package dev.keyset.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import dev.keyset.editor.BindingIndex;
import dev.keyset.editor.ActionSearch;
import dev.keyset.editor.SidebarSearch;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.KeyboardDiagramLayout;
import dev.keyset.editor.ChordCapture;
import dev.keyset.editor.EditorViewport;
import dev.keyset.editor.EditorPanelLayout;
import dev.keyset.editor.EditorFooterLayout;
import dev.keyset.editor.MouseDiagramLayout;
import dev.keyset.editor.MouseDiagramLayout.Control;
import dev.keyset.editor.MouseDiagramLayout.Rect;
import dev.keyset.editor.MouseDiagramPainter.State;
import dev.keyset.profile.UiAppearance;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.profile.Binding;
import dev.keyset.profile.Profile;
import java.io.IOException;
import java.nio.file.Path;
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
public final class KeysetScreen extends ScaledScreen {
    private static final Set<String> COLLAPSED_SECTIONS = new HashSet<>();
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
    private final MouseTextureCache mouseArtwork = new MouseTextureCache();
    private EditBox searchBox;
    private Button searchClearButton;
    private Button allCategoryButton, categoryFilterButton, modFilterButton;
    private Button conflictsButton, unboundButton, keyboardTabButton, mouseTabButton;
    private Button profileCycleButton, newProfileButton, renameProfileButton, deleteProfileButton;
    private Button layoutButton, unbindButton, resetButton, resetAllButton, undoButton, doneButton;
    private Button ctrlButton, shiftButton, altButton, deselectButton;
    private Button compactStatusButton;
    private Button appearanceButton;
    private EditBox sidebarSearchBox;
    private final List<Button> filterChipButtons = new ArrayList<>();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<Button> categoryHideButtons = new ArrayList<>();
    private Button hideAllButton, showAllButton;
    private boolean leftBarDragging, actionsBarDragging;
    private final Map<Button, Integer> sidebarButtonCounts = new IdentityHashMap<>();
    private final List<Button> rowNameButtons = new ArrayList<>();
    private final List<Button> rowBindingButtons = new ArrayList<>();
    private final List<Button> rowUnbindButtons = new ArrayList<>();
    private final List<Button> rowConflictButtons = new ArrayList<>();
    private final List<Button> rowSectionButtons = new ArrayList<>();
    private final List<Button> rowSectionHideButtons = new ArrayList<>();
    private final List<Button> rowSectionUnbindButtons = new ArrayList<>();
    private final List<Button> decorativeInputButtons = new ArrayList<>();
    private final Map<Button, VirtualKey> keyboardButtonKeys = new IdentityHashMap<>();
    private List<KeyMapping> filteredMappings = List.of();
    private List<KeyMapping> allMappings = List.of();
    private final Set<KeyMapping.Category> hiddenSections = new HashSet<>();
    private List<ActionListEntry> displayEntries = List.of();
    private BindingIndex<KeyMapping> bindingIndex;
    private BindingIndex<KeyMapping> diagramIndex;
    private final Map<KeyMapping, MappingView> mappingViews = new IdentityHashMap<>();
    private final Map<KeyMapping.Category, Integer> categoryCounts = new HashMap<>();
    private FilterState appliedFilter;
    private KeyboardLayouts.Layout keyboardLayout;
    private List<KeyMapping.Category> categories = List.of();
    private List<KeyMapping.Category> searchedCategories = List.of();
    private List<ModFilter> mods = List.of();
    private List<ModFilter> searchedMods = List.of();
    private KeyMapping.Category category;
    private String modFilter;
    private KeyMapping selectedMapping;
    /** The action highlighted with the arrow keys; Enter starts changing it. */
    private KeyMapping cursorMapping;
    private int bindingScroll, categoryScroll, modScroll;
    private int collapseRevision;
    private int layoutIndex = -1;
    private boolean modFilterMode, conflictsOnly, unboundOnly, capturing, settingVirtualKeyFilter, mouseMode;
    private boolean virtualCtrl, virtualShift, virtualAlt;
    private String searchQuery = "";
    private String sidebarSearchQuery = "";
    private String virtualKeyFilter;
    private InputConstants.Key pendingModifier;
    private final ChordCapture nativeCapture = new ChordCapture();
    private Component statusMessage;
    private boolean statusIsError;
    private int sidebarX, sidebarWidth, mainX, mainWidth;
    private int searchY, listTitleY, listTop, listBottom;
    private int keyboardPanelY, inputCardBottom, categoryCardY, summaryY;
    private int categoryListTop, categoryListBottom;
    private int leftScroll, maxLeftScroll, sidebarShown, inputCardBase;
    private boolean reuseModel;
    private EditorViewport viewport;
    private EditorPanelLayout panels;
    private UiAppearance appearance;
    private double layoutSidebarRatio;
    private MouseDiagramLayout mouseDiagram;
    private KeyboardDiagramLayout keyboardDiagram;
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
        InterfaceScale.apply(this);
        KeycapRenderer.resetFontMetrics();
        KeysetClient.ensureLoaded();
        observedRevision = KeysetClient.revision();
        if (statusMessage == null && KeysetClient.error() != null && KeysetClient.error().startsWith("keyset.error.")) {
            statusMessage = Component.translatable(KeysetClient.error());
            statusIsError = !KeysetClient.error().endsWith("recovered");
        }
        clearWidgets();
        categoryButtons.clear();
        categoryHideButtons.clear();
        rowNameButtons.clear();
        rowBindingButtons.clear();
        rowUnbindButtons.clear();
        rowConflictButtons.clear();
        rowSectionButtons.clear();
        rowSectionHideButtons.clear();
        rowSectionUnbindButtons.clear();
        decorativeInputButtons.clear();
        keyboardButtonKeys.clear();
        compactTabs.clear();
        viewport = EditorViewport.forSize(width, height);
        appearance = KeysetClient.appearance();
        singlePane = viewport.singlePane();
        rowHeight = singlePane ? Math.max(14, Math.min(20,
            Math.round(viewport.rowHeight() * appearance.rowHeight() / 25f))) : appearance.rowHeight();
        compact = singlePane;
        layoutSidebarRatio = KeysetClient.sidebarRatio();
        panels = EditorPanelLayout.forWidth(width, layoutSidebarRatio, appearance.inputOnRight(), singlePane);
        sidebarX = panels.sidebarX();
        sidebarWidth = panels.sidebarWidth();
        mainX = panels.mainX();
        mainWidth = panels.mainWidth();
        keyboardLayout = layoutIndex < 0 ? KeyboardLayouts.forLanguage(minecraft.getLanguageManager().getSelected())
            : KeyboardLayouts.manual(layoutIndex);
        if (!reuseModel) rebuildModel();
        if (category != null && !categories.contains(category)) category = null;
        // The input column (keyboard + categories) scrolls as one piece.
        int listRows = Math.max(6, Math.max(categories.size(), mods.size()));
        var form = appearance.keyboardFormFactor().resolve(Math.max(1, sidebarWidth - 24), height);
        int keyPitch = Math.min(30, Math.max(1, sidebarWidth - 24) / KeyboardDiagramLayout.columns(form));
        inputCardBase = Math.max(312, 174 + KeyboardDiagramLayout.rows(form) * keyPitch);
        int categoryListBottomBase = singlePane ? viewport.categoryListBottom()
            : categoryListBase() + listRows * 20 - 2;
        sidebarShown = shownSidebarItems();
        maxLeftScroll = singlePane ? 0 : Math.max(0, leftContentEnd0() - (height - 54));
        leftScroll = Math.max(0, Math.min(leftScroll, maxLeftScroll));
        keyboardPanelY = singlePane ? viewport.keyboardPanelY() : viewport.keyboardPanelY() - leftScroll;
        keyboardDiagram = null;
        mouseDiagram = null;
        inputCardBottom = singlePane ? viewport.rowsBottom() : inputCardBase - leftScroll;
        summaryY = singlePane ? viewport.inputControlsY() - 16 : inputCardBottom - 44;
        categoryCardY = singlePane ? viewport.contentTop() : inputCardBottom + 12;
        categoryListTop = viewport.categoryListTop();
        categoryListBottom = categoryListBottomBase - (singlePane ? 0 : leftScroll);
        searchY = viewport.searchY();
        listTitleY = 78;
        listTop = viewport.rowsTop() - (singlePane || hasActiveFilterChips() ? 0 : 24);
        listBottom = viewport.rowsBottom();
        if (singlePane) {
            listTop = viewport.actionRowsTop(hasActiveFilterChips());
        }
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
        clipLeftColumn();
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
        return virtualKeyFilter != null || category != null || modFilter != null || conflictsOnly || unboundOnly;
    }

    private boolean showInput() { return !singlePane || compactPage == EditorViewport.Page.INPUT; }
    private boolean showActions() { return !singlePane || compactPage == EditorViewport.Page.ACTIONS; }
    private boolean showCategories() { return !singlePane || compactPage == EditorViewport.Page.FILTERS; }

    private void applyCompactVisibility() {
        applyCompactVisibilityBase();
        clipLeftColumn();
    }

    /** Widgets of the scrolled input column that fall outside its viewport are hidden, never half-drawn. */
    private void clipLeftColumn() {
        if (singlePane) return;
        int top = 64, bottom = height - 52;
        List<Button> column = new ArrayList<>(keyboardButtonKeys.keySet());
        column.addAll(decorativeInputButtons);
        column.addAll(categoryButtons);
        column.addAll(categoryHideButtons);
        column.addAll(java.util.Arrays.asList(keyboardTabButton, mouseTabButton, layoutButton, ctrlButton, shiftButton,
            altButton, deselectButton, categoryFilterButton, modFilterButton, allCategoryButton, hideAllButton, showAllButton));
        for (Button button : column) {
            if (button == null) continue;
            if (button.getY() < top || button.getY() + button.getHeight() > bottom) button.visible = false;
        }
        if (sidebarSearchBox != null && (sidebarSearchBox.getY() < top
            || sidebarSearchBox.getY() + sidebarSearchBox.getHeight() > bottom)) {
            sidebarSearchBox.visible = false;
            if (sidebarSearchBox.isFocused()) sidebarSearchBox.setFocused(false);
        }
    }

    /** Scrollbar of the action list and the input column: a dark path with a flat thumb that lights up on hover. */
    private static void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int track,
                                      int thumbTop, int thumbHeight, boolean hot) {
        if (track <= 0) return;
        graphics.fill(x, top, x + 3, top + track, 0x90000000);
        graphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, hot ? UiTheme.accent() : UiTheme.muted());
    }

    private int shownSidebarItems() {
        if (modFilterMode) {
            return SidebarSearch.filter(mods, sidebarSearchQuery, value -> value.name() + " " + value.id()).size();
        }
        return SidebarSearch.filter(categories, sidebarSearchQuery,
            value -> KeysetClient.categoryLabel(value).getString() + " " + value.id()).size();
    }

    /** Unscrolled top of the category rows: card gap, filter tabs, search and the hide/show row. */
    private int categoryListBase() { return inputCardBase + 84; }

    private int leftContentEnd0() { return categoryListBase() + Math.max(1, sidebarShown) * 20 + 6; }

    /** Called when the listed categories or mods change count: keeps the column from scrolling past its end. */
    private void settleLeftScroll() {
        if (singlePane) return;
        sidebarShown = shownSidebarItems();
        maxLeftScroll = Math.max(0, leftContentEnd0() - (height - 54));
        if (leftScroll > maxLeftScroll) {
            leftScroll = maxLeftScroll;
            relayoutForScroll();
        } else {
            // The list may have grown or shrunk: re-apply which rows fit in the column.
            refreshCategoryButtons();
        }
    }

    private int actionsTrack() { return visibleRowCapacity() * rowHeight - 2; }

    private int actionsThumb() {
        return Math.max(18, actionsTrack() * visibleRowCapacity() / Math.max(1, displayEntries.size()));
    }

    private boolean actionsBarHit(double x, double y) {
        return showActions() && displayEntries.size() > visibleRowCapacity() && x >= mainX + mainWidth - 12
            && x < mainX + mainWidth - 3 && y >= listTop && y < listTop + actionsTrack();
    }

    private void dragActionsBarTo(double y) {
        int track = actionsTrack(), thumb = actionsThumb();
        double ratio = (y - listTop - thumb / 2.0) / Math.max(1, track - thumb);
        int next = (int) Math.round(Math.max(0, Math.min(1, ratio)) * (displayEntries.size() - visibleRowCapacity()));
        if (next != bindingScroll) {
            bindingScroll = next;
            refreshVisibleRows();
        }
    }

    private int leftBarX() { return sidebarX + sidebarWidth - 8; }

    private int leftTrackTop() { return 72; }

    private int leftTrackHeight() { return Math.max(1, height - 62 - 72); }

    private int leftThumbHeight() {
        int track = leftTrackHeight();
        return Math.max(24, track * track / (track + Math.max(1, maxLeftScroll)));
    }

    private boolean leftBarHit(double x, double y) {
        return !singlePane && maxLeftScroll > 0 && x >= leftBarX() - 4 && x < leftBarX() + 7 && y >= 64 && y < height - 52;
    }

    private void dragLeftBarTo(double y) {
        int track = leftTrackHeight();
        int thumb = leftThumbHeight();
        double ratio = (y - leftTrackTop() - thumb / 2.0) / Math.max(1, track - thumb);
        int next = (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxLeftScroll);
        if (next != leftScroll) { leftScroll = next; relayoutForScroll(); }
    }

    /** Re-lays out the widgets for a new column offset without rebuilding the (unchanged) action model. */
    private void relayoutForScroll() {
        boolean actionsSearch = searchBox != null && searchBox.isFocused();
        boolean sidebarSearch = sidebarSearchBox != null && sidebarSearchBox.isFocused();
        reuseModel = true;
        try { init(); } finally { reuseModel = false; }
        EditBox typing = actionsSearch ? searchBox : sidebarSearch ? sidebarSearchBox : null;
        if (typing != null && typing.visible) {
            setFocused(typing);
            typing.setFocused(true);
        }
    }

    private boolean scrollLeftBy(int delta) {
        int next = Math.max(0, Math.min(maxLeftScroll, leftScroll + delta));
        if (next == leftScroll) return false;
        leftScroll = next;
        relayoutForScroll();
        return true;
    }

    private void applyCompactVisibilityBase() {
        boolean input = showInput();
        keyboardButtonKeys.keySet().forEach(button -> button.visible = input);
        decorativeInputButtons.forEach(button -> button.visible = input);
        if (!singlePane) return;
        boolean actions = showActions(), filters = showCategories();
        keyboardTabButton.visible = mouseTabButton.visible = input;
        layoutButton.visible = input && !mouseMode;
        for (Button button : List.of(ctrlButton, shiftButton, altButton, deselectButton)) button.visible &= input;
        searchBox.visible = actions;
        searchClearButton.visible = actions && searchBox.getWidth() >= 40;
        conflictsButton.visible = unboundButton.visible = actions;
        sidebarSearchBox.visible = filters;
        for (List<Button> buttons : List.of(rowNameButtons, rowBindingButtons, rowUnbindButtons,
                rowConflictButtons, rowSectionButtons, rowSectionHideButtons, rowSectionUnbindButtons, filterChipButtons)) {
            buttons.forEach(button -> button.visible &= actions);
        }
        categoryButtons.forEach(button -> button.visible &= filters);
        categoryHideButtons.forEach(button -> button.visible &= filters);
        allCategoryButton.visible = categoryFilterButton.visible = modFilterButton.visible = filters;
        for (int i = 0; i < compactTabs.size(); i++) {
            setButtonState(compactTabs.get(i), compactPage.ordinal() == i ? VisualState.SELECTED : VisualState.NORMAL);
        }
    }

    private void initProfileBar() {
        if (singlePane) {
            int right = width - 8;
            int widthPerAction = 22, gap = 4;
            int newX = right - (4 * widthPerAction + 3 * gap);
            profileCycleButton = addButton(Component.empty(), 100, 6, Math.max(40, newX - 106), 20,
                button -> openProfilePicker());
            newProfileButton = addButton(Component.literal("+"), newX, 6, widthPerAction, 20,
                button -> openCreateDialog());
            renameProfileButton = styled(addButton(Component.literal("✎"), newX + widthPerAction + gap, 6,
                widthPerAction, 20, button -> openRenameDialog()), ButtonStyle.QUIET, true);
            deleteProfileButton = styled(addButton(Component.literal("×"), newX + 2 * (widthPerAction + gap), 6,
                widthPerAction, 20, button -> confirmDeleteProfile()), ButtonStyle.QUIET, true);
            appearanceButton = styled(addButton(Component.literal("..."), right - widthPerAction, 6,
                widthPerAction, 20, button -> openPersonalization()), ButtonStyle.QUIET, true);
            appearanceButton.setTooltip(Tooltip.create(Component.translatable("keyset.appearance_title")));
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
        int actionGap = narrow ? 4 : 6;
        int groupWidth = newWidth + renameWidth + deleteWidth + 2 * actionGap;
        appearanceButton = styled(addButton(Component.translatable("keyset.appearance_short"), width - 76, 26,
            60, 24, button -> openPersonalization()), ButtonStyle.QUIET, true);
        appearanceButton.setTooltip(Tooltip.create(Component.translatable("keyset.appearance_title")));
        // The profile actions sit right next to the picker they act on, not at the far edge of the window.
        int available = width - 82 - x;
        int pickerWidth = Math.max(48, Math.min(240, available - groupWidth - 8));
        int newX = x + pickerWidth + 6;
        profileCycleButton = addButton(Component.empty(), x, 26, pickerWidth, 24, button -> openProfilePicker());
        newProfileButton = addButton(narrow ? Component.literal("+") : Component.translatable("keyset.profile_new_short"),
            newX, 26, newWidth, 24, button -> openCreateDialog());
        renameProfileButton = styled(addButton(Component.translatable("keyset.profile_rename_short"),
            newX + newWidth + actionGap, 26, renameWidth, 24, button -> openRenameDialog()), ButtonStyle.QUIET, true);
        deleteProfileButton = styled(addButton(Component.translatable("keyset.profile_delete_short"),
            newX + newWidth + renameWidth + 2 * actionGap, 26, deleteWidth, 24,
            button -> confirmDeleteProfile()), ButtonStyle.QUIET, true);
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

    private void openPersonalization() {
        clearSelection();
        minecraft.setScreenAndShow(new PersonalizationScreen(this));
    }

    private void updateCompactStatus() {
        if (compactStatusButton == null) return;
        compactStatusButton.visible = singlePane && statusIsError && statusMessage != null;
        compactStatusButton.setTooltip(statusMessage == null ? null : Tooltip.create(statusMessage));
        compactStatusButton.setTooltipDelay(java.time.Duration.ZERO);
        setButtonState(compactStatusButton, VisualState.CONFLICT);
    }

    private void initMainControls() {
        int innerWidth = Math.max(1, mainWidth - 24);
        int filterGap = singlePane ? 3 : 4;
        int desiredFilterGroupWidth = singlePane
            ? Math.min(160, Math.max(96, innerWidth * 36 / 100))
            : Math.min(240, Math.max(150, innerWidth * 32 / 100));
        int filterWidth = Math.max(1, (desiredFilterGroupWidth - filterGap) / 2);
        int filterGroupWidth = 2 * filterWidth + filterGap;
        int searchWidth = Math.max(1, innerWidth - filterGroupWidth - 6);
        boolean showSearchClear = searchWidth >= 64;
        searchBox = new EditBox(font, mainX + (singlePane ? 8 : 12), searchY,
            Math.max(1, searchWidth - (showSearchClear ? 24 : 0)), viewport.searchHeight(), Component.translatable("keyset.search_hint"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(ActionSearch.MAX_QUERY_LENGTH);
        searchBox.setTextColor(UiTheme.text());
        searchBox.setValue(searchQuery);
        searchBox.setHint(Component.translatable("keyset.search_hint"));
        searchBox.setTooltip(Tooltip.create(Component.translatable("keyset.search_syntax_tip")));
        searchBox.setTooltipDelay(java.time.Duration.ofMillis(750));
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
        int filterY = singlePane ? searchY : searchY - 2;
        int filterHeight = singlePane ? viewport.controlHeight() + 4 : 24;
        conflictsButton = styled(addButton(Component.empty(), filterX, filterY,
            filterWidth, filterHeight, button -> { conflictsOnly = !conflictsOnly; bindingScroll = 0; refreshRows(); }), ButtonStyle.TAB, true);
        int unboundX = filterX + filterWidth + filterGap;
        unboundButton = styled(addButton(Component.empty(), unboundX, filterY,
            filterWidth, filterHeight, button -> { unboundOnly = !unboundOnly; bindingScroll = 0; refreshRows(); }), ButtonStyle.TAB, true);
        if (sidebarWidth < 320) {
            int sidebarInnerWidth = Math.max(1, sidebarWidth - 24);
            int layoutWidth = Math.max(40, Math.min(88, sidebarInnerWidth / 4));
            int tabGap = 4;
            int tabsWidth = Math.max(2, sidebarInnerWidth - layoutWidth - tabGap * 2);
            int keyboardWidth = Math.max(1, tabsWidth * 52 / 100);
            int mouseWidth = Math.max(1, tabsWidth - keyboardWidth);
            keyboardTabButton = styled(addButton(Component.translatable("keyset.input_keyboard"),
                sidebarX + 12, 76 - leftScroll, keyboardWidth, 24, button -> setMouseMode(false)), ButtonStyle.TAB, true);
            mouseTabButton = styled(addButton(Component.translatable("keyset.input_mouse"),
                sidebarX + 12 + keyboardWidth + tabGap, 76 - leftScroll, mouseWidth, 24, button -> setMouseMode(true)),
                ButtonStyle.TAB, true);
            layoutButton = styled(addButton(Component.empty(), sidebarX + sidebarWidth - 12 - layoutWidth,
                76 - leftScroll, layoutWidth, 24, button -> cycleLayout()), ButtonStyle.QUIET, true);
            keyboardTabButton.setTooltip(Tooltip.create(Component.translatable("keyset.input_keyboard")));
            mouseTabButton.setTooltip(Tooltip.create(Component.translatable("keyset.input_mouse")));
        } else {
            keyboardTabButton = styled(addButton(Component.translatable("keyset.input_keyboard"), sidebarX + 12, 76 - leftScroll, 104, 24,
                button -> setMouseMode(false)), ButtonStyle.TAB, true);
            mouseTabButton = styled(addButton(Component.translatable("keyset.input_mouse"), sidebarX + 122, 76 - leftScroll, 78, 24,
                button -> setMouseMode(true)), ButtonStyle.TAB, true);
            layoutButton = styled(addButton(Component.empty(), sidebarX + sidebarWidth - 100, 76 - leftScroll, 88, 24,
                button -> cycleLayout()), ButtonStyle.QUIET, true);
        }
    }

    private void initFilterChips() {
        filterChipButtons.clear();
        for (int i = 0; i < 5; i++) {
            final int filter = i;
            filterChipButtons.add(styled(addButton(Component.empty(), mainX + 12, viewport.chipY(),
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
            case 4 -> unboundOnly = false;
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
        if (unboundOnly) {
            active.add(new FilterChip(4, Component.translatable("keyset.filter_chip_unbound"),
                Component.translatable("keyset.filter_clear_unbound")));
        }
        int available = Math.max(1, mainWidth - 24);
        int maxWidth = active.isEmpty() ? available
            : Math.max(22, (available - (active.size() - 1) * 4) / active.size());
        if (singlePane) listTop = viewport.actionRowsTop(!active.isEmpty());
        else listTop = viewport.rowsTop() - (active.isEmpty() ? 24 : 0);
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
            button.setY(viewport.chipY());
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
        int left = sidebarX + 12;
        int innerWidth = Math.max(1, sidebarWidth - 24);
        int tabGap = 4;
        int controlGap = 4;
        int tabsWidth = Math.max(2, (innerWidth - 2 * controlGap) * 3 / 4);
        int tabWidth = Math.max(1, (tabsWidth - tabGap) / 2);
        int resetWidth = Math.max(1, innerWidth - tabsWidth - controlGap);
        int filterControlY = singlePane ? viewport.categoryControlsY() : categoryCardY + 8;
        int filterControlHeight = singlePane ? viewport.controlHeight() : 22;
        categoryFilterButton = styled(addButton(Component.empty(), left, filterControlY, tabWidth, filterControlHeight,
            button -> {
                modFilterMode = false;
                updateControlLabels();
                refreshRows();
                settleLeftScroll();
            }), ButtonStyle.TAB, true);
        modFilterButton = styled(addButton(Component.empty(), left + tabWidth + tabGap, filterControlY, tabWidth, filterControlHeight,
            button -> {
                modFilterMode = true;
                updateControlLabels();
                refreshRows();
                settleLeftScroll();
            }), ButtonStyle.TAB, true);
        allCategoryButton = styled(addButton(Component.translatable("keyset.category_all"),
            left + tabsWidth + controlGap, filterControlY, resetWidth, filterControlHeight,
            button -> {
                if (modFilterMode) { modFilter = null; modScroll = 0; }
                else { category = null; categoryScroll = 0; }
                bindingScroll = 0;
                refreshRows();
            }), ButtonStyle.TAB, true);
        int searchY = filterControlY + filterControlHeight + 2;
        int searchHeight = 18;
        sidebarSearchBox = new EditBox(font, left, searchY, innerWidth, searchHeight,
            Component.translatable("keyset.sidebar_search"));
        sidebarSearchBox.setBordered(false);
        sidebarSearchBox.setMaxLength(256);
        sidebarSearchBox.setTextColor(UiTheme.text());
        sidebarSearchBox.setValue(sidebarSearchQuery);
        sidebarSearchBox.setHint(Component.translatable("keyset.sidebar_search"));
        sidebarSearchBox.setResponder(value -> {
            sidebarSearchQuery = value;
            categoryScroll = 0;
            modScroll = 0;
            refreshCategoryButtons();
            settleLeftScroll();
        });
        addRenderableWidget(sidebarSearchBox);
        categoryListTop = searchY + searchHeight + 2;
        hideAllButton = showAllButton = null;
        if (!singlePane) {
            int bulkY = categoryListTop;
            int half = Math.max(1, (innerWidth - 4) / 2);
            hideAllButton = styled(addButton(Component.translatable("keyset.visibility_hide_all"), left, bulkY, half, 18,
                button -> setAllHidden(true)), ButtonStyle.TAB, true);
            showAllButton = styled(addButton(Component.translatable("keyset.visibility_restore_all"), left + half + 4, bulkY,
                Math.max(1, innerWidth - half - 4), 18, button -> setAllHidden(false)), ButtonStyle.TAB, true);
            hideAllButton.setTooltip(Tooltip.create(Component.translatable("keyset.visibility_bulk_tip")));
            showAllButton.setTooltip(Tooltip.create(Component.translatable("keyset.visibility_bulk_tip")));
            categoryListTop = bulkY + 20;
        }
        int rows = Math.max(1, (categoryListBottom - categoryListTop + 2) / 20);
        for (int i = 0; i < rows; i++) {
            final int slot = i;
            Button button = styled(addButton(Component.empty(), left, categoryListTop + i * 20,
                Math.max(1, innerWidth - 24), 18, clicked -> selectCategoryAt(slot)), ButtonStyle.LIST, false);
            categoryButtons.add(button);
            categoryHideButtons.add(styled(addButton(Component.literal("-"), left + innerWidth - 20,
                categoryListTop + i * 20, 20, 18, clicked -> toggleHiddenAt(slot)), ButtonStyle.QUIET, true));
        }
    }

    private void initFooter() {
        int y = viewport.footerY();
        int footerX = mainX;
        int footerWidth = mainWidth;
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
        EditorFooterLayout footer = EditorFooterLayout.forWidth(footerWidth);
        int doneWidth = footer.doneWidth(), allWidth = footer.resetAllWidth(), undoWidth = footer.undoWidth();
        doneButton = styled(addButton(Component.translatable("keyset.done"), footerX + footer.doneX(), y, doneWidth, 26,
            button -> onClose()), ButtonStyle.PRIMARY, true);
        int resetAllX = footerX + footer.resetAllX();
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
        int actionWidth = footer.actionWidth();
        Component unbindLabel = Component.translatable("keyset.unbind"), resetLabel = Component.translatable("keyset.reset");
        unbindButton = styled(addButton(font.width(unbindLabel) + 8 > actionWidth ? Component.literal("×") : unbindLabel,
            footerX, y, actionWidth, 26,
            button -> unbindSelected()), ButtonStyle.QUIET, true);
        resetButton = styled(addButton(font.width(resetLabel) + 8 > actionWidth ? Component.literal("↻") : resetLabel,
            footerX + footer.resetX(), y, actionWidth, 26,
            button -> resetSelected()), ButtonStyle.QUIET, true);
        unbindButton.setTooltip(Tooltip.create(unbindLabel));
        resetButton.setTooltip(Tooltip.create(resetLabel));
    }

    private void initRowSlots() {
        int rowButtonHeight = rowHeight - 2;
        int inner = Math.max(1, mainWidth - (singlePane ? 22 : 30));
        int bindWidth = Math.min(160, inner * 31 / 100);
        if (inner < 230) bindWidth = Math.max(1, Math.min(inner / 3, inner - 78));
        int nameWidth = Math.max(26, inner - bindWidth - 52);
        int actionWidth = nameWidth;
        int slots = Math.max(visibleRowCapacity(), (listBottom - viewport.rowsTop() + (singlePane ? 0 : 24)) / rowHeight);
        for (int i = 0; i < slots; i++) {
            final int slot = i;
            int y = listTop + i * rowHeight;
            rowNameButtons.add(styled(addButton(Component.empty(), mainX + 12, y, actionWidth, rowButtonHeight,
                button -> selectMappingAt(slot)), ButtonStyle.ROW, false));
            rowBindingButtons.add(styled(addButton(Component.empty(), mainX + 12 + nameWidth + 4, y + 1, bindWidth, Math.min(19, rowHeight - 4),
                button -> captureMappingAt(slot)), ButtonStyle.BINDING, true));
            rowConflictButtons.add(styled(addButton(Component.literal("!"),
                mainX + 12 + nameWidth + 8 + bindWidth, y, 18, rowButtonHeight,
                button -> inspectConflictAt(slot)), ButtonStyle.QUIET, true));
            rowUnbindButtons.add(styled(addButton(Component.literal("×"), mainX + mainWidth - 34, y, 20, rowButtonHeight,
                button -> unbindMappingAt(slot)), ButtonStyle.QUIET, true));
            rowSectionButtons.add(styled(addButton(Component.empty(), mainX + 8, y, mainWidth - 70, rowButtonHeight,
                button -> toggleSectionAt(slot)), ButtonStyle.SECTION, false));
            Button hide = styled(addButton(Component.literal("-"), mainX + mainWidth - 34, y, 20, rowButtonHeight,
                button -> hideSectionAt(slot)), ButtonStyle.SECTION_ACTION, true);
            hide.setTooltip(Tooltip.create(Component.translatable("keyset.visibility_hide_section")));
            rowSectionHideButtons.add(hide);
            Button unbindSection = styled(addButton(Component.literal("×"), mainX + mainWidth - 58, y, 20, rowButtonHeight,
                button -> confirmUnbindSectionAt(slot)), ButtonStyle.SECTION_ACTION, true);
            unbindSection.setTooltip(Tooltip.create(Component.translatable("keyset.section_unbind")));
            rowSectionUnbindButtons.add(unbindSection);
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
        int bottom = singlePane ? viewport.inputControlsY() - 6 : summaryY - 22;
        keyboardDiagram = KeyboardDiagramLayout.forArea(sidebarX + 12, keyboardPanelY,
            Math.max(1, sidebarWidth - 24), Math.max(1, bottom - keyboardPanelY), appearance.keyboardScale(),
            appearance.keyboardFormFactor(), keyboardLayout, appearance.keyboardPresentation());
        for (KeyboardDiagramLayout.Cell cell : keyboardDiagram.keys()) {
            if (cell.input().equals("key.keyboard.fn")) {
                var rect = cell.bounds();
                var fn = (FlatButton) styled(addButton(Component.literal(cell.label()), rect.x(), rect.y(),
                    rect.width(), rect.height(), ignored -> {}), ButtonStyle.KEYCAP, true);
                fn.keyboardInput = cell.input();
                fn.freeKey = true;
                fn.setTooltip(Tooltip.create(Component.translatable("keyset.keyboard_fn_hint")));
                decorativeInputButtons.add(fn);
                continue;
            }
            VirtualKey key = new VirtualKey(InputConstants.getKey(cell.input()).getValue(), cell.label(), false);
            KeyboardDiagramLayout.Rect bounds = cell.bounds();
            Button button = styled(addButton(Component.literal(cell.label()), bounds.x(), bounds.y(),
                bounds.width(), bounds.height(), ignored -> onVirtualKey(key)), ButtonStyle.KEYCAP, true);
            ((FlatButton) button).keyboardInput = cell.input();
            keyboardButtonKeys.put(button, key);
        }
    }

    private void initMouseButtons() {
        int bottom = singlePane ? viewport.inputControlsY() - 6 : summaryY - 22;
        mouseDiagram = MouseDiagramLayout.forArea(sidebarX + 12, keyboardPanelY,
            Math.max(1, sidebarWidth - 24), Math.max(1, bottom - keyboardPanelY), appearance.mouseScale());
        for (var part : mouseDiagram.buttons()) {
            int code = switch (part.control()) {
                case LEFT -> InputConstants.MOUSE_BUTTON_LEFT;
                case RIGHT -> InputConstants.MOUSE_BUTTON_RIGHT;
                case MIDDLE -> InputConstants.MOUSE_BUTTON_MIDDLE;
                case SIDE4 -> InputConstants.MOUSE_BUTTON_4;
                case SIDE5 -> InputConstants.MOUSE_BUTTON_5;
                case EXTRA6 -> InputConstants.MOUSE_BUTTON_6;
                case EXTRA7 -> InputConstants.MOUSE_BUTTON_7;
                case EXTRA8 -> InputConstants.MOUSE_BUTTON_8;
            };
            String label = switch (part.control()) {
                case LEFT -> "keyset.mouse_left";
                case RIGHT -> "keyset.mouse_right";
                case MIDDLE -> "M3";
                case SIDE4 -> "M4";
                case SIDE5 -> "M5";
                case EXTRA6 -> "M6";
                case EXTRA7 -> "M7";
                case EXTRA8 -> "M8";
            };
            Rect bounds = part.bounds();
            addMouseButton(code, label, bounds.x(), bounds.y(), bounds.width(), bounds.height(), part.control());
        }
    }

    private void addMouseButton(int code, String label, int x, int y, int w, int h, Control control) {
        VirtualKey key = new VirtualKey(code, Component.translatable(label).getString(), true);
        Component caption = Component.translatable(label);
        if ((control == Control.LEFT || control == Control.RIGHT) && font.width(caption) > w - 6) {
            caption = Component.literal(control == Control.LEFT ? "M1" : "M2");
        }
        FlatButton button = (FlatButton) addButton(caption, x, y, w, h, ignored -> onVirtualKey(key));
        button.mouseControl = control;
        button.mouseLayout = mouseDiagram;
        button.mouseArtwork = mouseArtwork;
        button.setTooltipDelay(java.time.Duration.ofMillis(250));
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
        allCategoryButton.setMessage(Component.translatable("keyset.sidebar_all"));
        allCategoryButton.setTooltip(Tooltip.create(Component.translatable(
            modFilterMode ? "keyset.mods_all" : "keyset.category_all")));
        boolean shortFilterLabels = singlePane || conflictsButton.getWidth() < 56;
        conflictsButton.setMessage(shortFilterLabels ? Component.literal("!")
            : Component.translatable("keyset.conflicts_filter"));
        conflictsButton.setTooltip(Tooltip.create(Component.translatable("keyset.conflicts_filter")));
        setButtonState(conflictsButton, conflictsOnly ? VisualState.SELECTED : VisualState.NORMAL);
        unboundButton.setMessage(shortFilterLabels ? Component.literal("U") : Component.translatable("keyset.filter_unbound"));
        unboundButton.setTooltip(Tooltip.create(Component.translatable("keyset.filter_unbound")));
        setButtonState(unboundButton, unboundOnly ? VisualState.SELECTED : VisualState.NORMAL);
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
            var search = new ActionSearch.Document(name.getString(), mapping.getName(),
                KeysetClient.categoryLabel(mapping.getCategory()).getString(), mapping.getCategory().id().toString(),
                modName.getString(), owner.id(), bindingLabel.getString(), String.join(" ", value.keys()), modifiers);
            mappingViews.put(mapping, new MappingView(name, bindingLabel, owner.id(), search, tooltip));
            categoryCounts.merge(mapping.getCategory(), 1, Integer::sum);
            ModFilter previous = modCounts.get(owner.id());
            modCounts.put(owner.id(), new ModFilter(owner.id(), modName.getString(), previous == null ? 1 : previous.count() + 1));
        }
        allMappings = mappings.stream().sorted(Comparator
            .comparing((KeyMapping mapping) -> KeysetClient.categoryLabel(mapping.getCategory()).getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(mapping -> mapping.getCategory().id().toString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(mapping -> mappingViews.get(mapping).search().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        diagramIndex = new BindingIndex<>(allMappings.stream().filter(this::isMappingVisible).toList(),
            bindingIndex::binding, KeysetClient::bindingContextsConflict);
        hiddenSections.clear();
        hiddenSections.addAll(categoryCounts.keySet());
        for (KeyMapping mapping : allMappings) if (isMappingVisible(mapping)) hiddenSections.remove(mapping.getCategory());
        categories = categoryCounts.keySet().stream().sorted(Comparator.comparing(value -> KeysetClient.categoryLabel(value).getString(),
            String.CASE_INSENSITIVE_ORDER)).toList();
        if (category != null && !categories.contains(category)) category = null;
        mods = modCounts.values().stream().sorted(Comparator.comparing(ModFilter::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ModFilter::id, String.CASE_INSENSITIVE_ORDER)).toList();
        if (modFilter != null && mods.stream().noneMatch(value -> value.id().equals(modFilter))) modFilter = null;
        appliedFilter = null;
    }

    private boolean isMappingVisible(KeyMapping mapping) {
        return !KeysetClient.isCategoryHidden(mapping.getCategory().id().toString())
            && !KeysetClient.isModHidden(mappingViews.get(mapping).modId());
    }

    private void refreshRows() {
        if (searchBox == null) return;
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        FilterState filter = new FilterState(category, modFilter, conflictsOnly, unboundOnly,
            query, virtualKeyFilter, collapseRevision);
        if (!filter.equals(appliedFilter)) {
            ActionSearch.Query parsedQuery = ActionSearch.parse(query);
            List<KeyMapping> virtualKeyMappings = virtualKeyFilter == null ? List.of()
                : bindingIndex.forInput(virtualKeyFilter);
            filteredMappings = allMappings.stream()
                .filter(mapping -> virtualKeyFilter == null || isMappingVisible(mapping))
                .filter(mapping -> category == null || category.equals(mapping.getCategory()))
                .filter(mapping -> modFilter == null || modFilter.equals(mappingViews.get(mapping).modId()))
                .filter(mapping -> !conflictsOnly || bindingIndex.conflicts(mapping))
                .filter(mapping -> !unboundOnly || bindingIndex.binding(mapping).keys().isEmpty())
                .filter(mapping -> (virtualKeyFilter != null || parsedQuery.matches(mappingViews.get(mapping).search()))
                    && (virtualKeyFilter == null || virtualKeyMappings.contains(mapping)))
                .toList();
            displayEntries = makeDisplayEntries(filteredMappings);
            appliedFilter = filter;
        }
        if (cursorMapping != null && !filteredMappings.contains(cursorMapping)) cursorMapping = null;
        if (selectedMapping != null && (!filteredMappings.contains(selectedMapping) || !isMappingVisible(selectedMapping))) {
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
            rowBindingButtons.get(slot).setY(rowY + 1);
            rowUnbindButtons.get(slot).setY(rowY);
            rowConflictButtons.get(slot).setY(rowY);
            rowSectionButtons.get(slot).setY(rowY);
            rowSectionHideButtons.get(slot).setY(rowY);
            rowSectionUnbindButtons.get(slot).setY(rowY);
            ActionListEntry entry = slot < visibleRowCapacity() ? entryAt(slot) : null;
            KeyMapping mapping = entry == null || entry.isSection() ? null : entry.mapping();
            Button name = rowNameButtons.get(slot);
            Button binding = rowBindingButtons.get(slot), clear = rowUnbindButtons.get(slot);
            Button conflict = rowConflictButtons.get(slot);
            Button section = rowSectionButtons.get(slot);
            boolean sectionRow = entry != null && entry.isSection();
            section.visible = sectionRow;
            rowSectionHideButtons.get(slot).visible = sectionRow;
            rowSectionUnbindButtons.get(slot).visible = sectionRow;
            name.visible = binding.visible = clear.visible = mapping != null;
            conflict.visible = mapping != null && bindingIndex.conflicts(mapping);
            if (sectionRow) {
                Component sectionName = KeysetClient.categoryLabel(entry.category());
                boolean collapsed = isSectionCollapsed(entry.category());
                boolean hidden = isSectionHidden(entry.category());
                ((FlatButton) section).inactiveSection = hidden;
                ((FlatButton) rowSectionUnbindButtons.get(slot)).inactiveSection = hidden;
                Button hide = rowSectionHideButtons.get(slot);
                hide.setMessage(Component.literal(hidden ? "+" : "-"));
                hide.setTooltip(Tooltip.create(Component.translatable(hidden ? "keyset.section_restore" : "keyset.visibility_hide_section")));
                String arrow = collapsed ? "▸" : "▾";
                section.setMessage(Component.literal(arrow + "  " + sectionName.getString() + "  ·  " + entry.count())
                    .append(hidden ? Component.translatable("keyset.section_hidden_suffix") : Component.empty()));
                section.setTooltip(Tooltip.create(Component.translatable(hidden ? "keyset.section_hidden_tip" : "keyset.section_toggle_tip", sectionName)));
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
                for (KeyMapping mapping : sectionMappings) if (isMappingVisible(mapping)) entries.add(new ActionListEntry(mapping, section, 0));
            }
        });
        return List.copyOf(entries);
    }

    private boolean isSectionCollapsed(KeyMapping.Category section) {
        return isSectionHidden(section) || COLLAPSED_SECTIONS.contains(section.id().toString());
    }

    private boolean isSectionHidden(KeyMapping.Category section) {
        return hiddenSections.contains(section);
    }

    private int visibleRowCapacity() {
        return Math.max(1, (listBottom - listTop) / rowHeight);
    }

    private void setButtonState(Button button, VisualState state) {
        if (button instanceof FlatButton flat) flat.visualState = state;
    }

    private void refreshCategoryButtons() {
        searchedCategories = SidebarSearch.filter(categories, sidebarSearchQuery,
            value -> KeysetClient.categoryLabel(value).getString() + " " + value.id());
        searchedMods = SidebarSearch.filter(mods, sidebarSearchQuery,
            value -> value.name() + " " + value.id());
        int visibleCount = Math.max(1, categoryButtons.size());
        if (modFilterMode) modScroll = clampScroll(modScroll, searchedMods.size(), visibleCount);
        else categoryScroll = clampScroll(categoryScroll, searchedCategories.size(), visibleCount);
        setButtonState(allCategoryButton, (modFilterMode ? modFilter == null : category == null)
            ? VisualState.SELECTED : VisualState.NORMAL);
        sidebarButtonCounts.clear();
        for (int slot = 0; slot < categoryButtons.size(); slot++) {
            Button button = categoryButtons.get(slot);
            button.setX(sidebarX + 12);
            button.setY(categoryListTop + slot * 20);
            button.setSize(Math.max(1, sidebarWidth - 48), 18);
            Button hideButton = categoryHideButtons.get(slot);
            hideButton.setX(sidebarX + sidebarWidth - 32);
            hideButton.setY(categoryListTop + slot * 20);
            hideButton.visible = false;
            boolean itemHidden;
            int scroll = modFilterMode ? modScroll : categoryScroll;
            int index = scroll + slot;
            int count;
            boolean selected;
            String tooltip;
            if (modFilterMode) {
                button.visible = index < searchedMods.size();
                if (!button.visible) continue;
                ModFilter value = searchedMods.get(index);
                count = value.count();
                selected = value.id().equals(modFilter);
                tooltip = value.name() + " · " + value.id();
                itemHidden = KeysetClient.isModHidden(value.id());
                button.setMessage(Component.literal(fitSidebarName(value.name(), count, button.getWidth())));
            } else {
                button.visible = index < searchedCategories.size();
                if (!button.visible) continue;
                KeyMapping.Category value = searchedCategories.get(index);
                String name = KeysetClient.categoryLabel(value).getString();
                count = categoryCounts.getOrDefault(value, 0);
                selected = value.equals(category);
                tooltip = name;
                itemHidden = KeysetClient.isCategoryHidden(value.id().toString());
                button.setMessage(Component.literal(fitSidebarName(name, count, button.getWidth())));
            }
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
            if (button instanceof FlatButton flat) flat.inactiveSection = itemHidden;
            hideButton.visible = true;
            hideButton.setMessage(Component.literal(itemHidden ? "+" : "-"));
            hideButton.setTooltip(Tooltip.create(Component.translatable(itemHidden
                ? "keyset.visibility_show_tip" : "keyset.visibility_hide_tip")));
            sidebarButtonCounts.put(button, count);
            setButtonState(button, selected ? VisualState.SELECTED : VisualState.NORMAL);
        }
        // The category and action lists overlap in compact mode, so every category refresh
        // must restore the visibility mask for the currently selected page.
        applyCompactVisibility();
    }

    private String fitSidebarName(String name, int count, int width) {
        int availableNameWidth = Math.max(1, width - font.width(Integer.toString(count)) - 26);
        if (font.width(name) <= availableNameWidth) return name;
        return font.plainSubstrByWidth(name, Math.max(1, availableNameWidth - font.width("…"))) + "…";
    }

    private void selectCategoryAt(int slot) {
        if (modFilterMode) {
            int index = modScroll + slot;
            if (slot >= 0 && slot < categoryButtons.size() && index >= 0 && index < searchedMods.size()) {
                modFilter = searchedMods.get(index).id();
                bindingScroll = 0;
                if (singlePane) compactPage = EditorViewport.Page.ACTIONS;
                refreshRows();
            }
        } else {
            int index = categoryScroll + slot;
            if (index >= 0 && index < searchedCategories.size()) {
                category = searchedCategories.get(index);
                bindingScroll = 0;
                if (singlePane) compactPage = EditorViewport.Page.ACTIONS;
                refreshRows();
            }
        }
    }

    private void refreshKeyboardButtons() {
        for (Map.Entry<Button, VirtualKey> entry : keyboardButtonKeys.entrySet()) {
            String key = keyName(entry.getValue());
            List<KeyMapping> visibleInputs = diagramIndex.forInput(key);
            boolean free = bindingIndex.forInput(key).isEmpty();
            boolean selected = selectedMapping == null ? key.equals(virtualKeyFilter)
                : bindingIndex.forInput(key).contains(selectedMapping);
            setButtonState(entry.getKey(), selected ? VisualState.SELECTED
                : diagramIndex.keyConflicts(key) ? VisualState.CONFLICT
                : visibleInputs.isEmpty() ? VisualState.NORMAL : VisualState.ASSIGNED);
            if (entry.getKey() instanceof FlatButton button) {
                button.freeKey = free;
                button.assignedKey = !visibleInputs.isEmpty();
                button.inputConflict = diagramIndex.keyConflicts(key);
            }
            Component tip = inputLabel(key).copy();
            if (free) tip = tip.copy().append("\n").append(Component.translatable("keyset.key_free"));
            else if (visibleInputs.size() > 1) tip = tip.copy().append("\n")
                .append(Component.translatable("keyset.key_multiple", visibleInputs.size()));
            for (KeyMapping mapping : visibleInputs.stream().limit(4).toList()) {
                tip = tip.copy().append("\n").append(KeysetClient.name(mapping));
            }
            entry.getKey().setTooltip(Tooltip.create(tip));
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

    private final class ProfilePicker extends ScaledScreen {
        private static final int ROW_STEP = 24;
        private final List<Button> rows = new ArrayList<>();
        private final List<Button> compareButtons = new ArrayList<>();
        private final List<Button> autoButtons = new ArrayList<>();
        private final List<Button> renameButtons = new ArrayList<>();
        private final List<Button> deleteButtons = new ArrayList<>();
        private int scroll, listTop, listBottom;
        private int panelX, panelY, panelWidth, panelHeight;
        private Component pickerStatus;
        private boolean pickerStatusError;

        private ProfilePicker() {
            super(Component.translatable("keyset.profile_picker_title"));
        }

        @Override
        protected void init() {
            InterfaceScale.apply(this);
            clearWidgets();
            rows.clear();
            compareButtons.clear();
            autoButtons.clear();
            renameButtons.clear();
            deleteButtons.clear();
            panelWidth = Math.max(1, Math.min(460, width - 24));
            panelHeight = Math.max(1, Math.min(height - 24, Math.max(230, currentProfiles().size() * ROW_STEP + 130)));
            panelX = (width - panelWidth) / 2;
            panelY = (height - panelHeight) / 2;
            int inner = panelWidth - 24;
            int left = panelX + 12;
            listTop = panelY + 62;
            listBottom = panelY + panelHeight - 58;
            int count = Math.max(1, (listBottom - listTop) / ROW_STEP);

            Button scope = styled(new FlatButton(left, panelY + 34, inner, 20,
                Component.translatable(KeysetClient.profilesShared()
                    ? "keyset.profile_scope_shared" : "keyset.profile_scope_local"), ignored -> toggleScope()),
                ButtonStyle.TAB, true);
            scope.setTooltip(Tooltip.create(Component.translatable(KeysetClient.profilesShared()
                ? "keyset.profile_scope_detach_tip" : "keyset.profile_scope_attach_tip")));
            addRenderableWidget(scope);

            int icon = 24, gap = 4;
            int compareWidth = Math.min(78, Math.max(60, inner / 6));
            int rowWidth = inner - compareWidth - 3 * icon - 4 * gap - 6;
            for (int i = 0; i < count; i++) {
                final int slot = i;
                int y = listTop + i * ROW_STEP;
                int x = left + rowWidth + gap;
                rows.add(addRenderableWidget(styled(new FlatButton(left, y, rowWidth, 22, Component.empty(),
                    ignored -> select(slot)), ButtonStyle.LIST, false)));
                compareButtons.add(addIcon(styled(new FlatButton(x, y, compareWidth, 22,
                    Component.translatable("keyset.compare_profile"), ignored -> compare(slot)), ButtonStyle.TAB, true),
                    "keyset.compare_profile"));
                x += compareWidth + gap;
                autoButtons.add(addIcon(styled(new FlatButton(x, y, icon, 22, Component.empty(),
                    ignored -> toggleAuto(slot)), ButtonStyle.QUIET, true), null));
                x += icon + gap;
                renameButtons.add(addIcon(styled(new FlatButton(x, y, icon, 22, Component.literal("✎"),
                    ignored -> rename(slot)), ButtonStyle.QUIET, true), "keyset.profile_rename_full"));
                x += icon + gap;
                deleteButtons.add(addIcon(styled(new FlatButton(x, y, icon, 22, Component.literal("×"),
                    ignored -> delete(slot)), ButtonStyle.QUIET, true), "keyset.profile_delete_short"));
            }

            int buttonGap = 4;
            int buttonWidth = Math.max(1, (inner - 2 * buttonGap) / 3);
            int buttonY = panelY + panelHeight - 29;
            addRenderableWidget(styled(new FlatButton(left, buttonY, buttonWidth, 20,
                Component.translatable("keyset.profile_new_short"), ignored -> createNew()), ButtonStyle.PRIMARY, true));
            addRenderableWidget(new FlatButton(left + buttonWidth + buttonGap, buttonY, buttonWidth, 20,
                Component.translatable("keyset.transfer_button"), ignored -> minecraft.setScreenAndShow(new TransferScreen())));
            addRenderableWidget(new FlatButton(left + 2 * (buttonWidth + buttonGap), buttonY,
                inner - 2 * (buttonWidth + buttonGap), 20, Component.translatable("keyset.done"), ignored -> close()));
            refresh();
        }

        private Button addIcon(Button button, String tooltipKey) {
            if (tooltipKey != null) button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            return addRenderableWidget(button);
        }

        private Profile profileAt(int slot) {
            int index = scroll + slot;
            List<Profile> profiles = currentProfiles();
            return index < 0 || index >= profiles.size() ? null : profiles.get(index);
        }

        private String activeId() {
            Profile active = KeysetClient.profiles().active();
            return active == null ? null : active.id();
        }

        private void refresh() {
            List<Profile> profiles = currentProfiles();
            scroll = clampScroll(scroll, profiles.size(), rows.size());
            String activeId = activeId();
            KeysetClient.CurrentContext context = KeysetClient.currentContext();
            for (int i = 0; i < rows.size(); i++) {
                Profile profile = profileAt(i);
                boolean visible = profile != null && listTop + i * ROW_STEP + 22 <= listBottom;
                Button row = rows.get(i);
                row.visible = visible;
                compareButtons.get(i).visible = autoButtons.get(i).visible = visible;
                renameButtons.get(i).visible = deleteButtons.get(i).visible = visible;
                if (!visible) continue;
                boolean active = Objects.equals(activeId, profile.id());
                int badgeWidth = active ? font.width("✓ " + Component.translatable("keyset.profile_active_badge").getString()) + 14 : 0;
                int nameWidth = Math.max(1, row.getWidth() - badgeWidth - 14);
                String name = profile.name();
                if (font.width(name) > nameWidth) {
                    name = font.plainSubstrByWidth(name, Math.max(1, nameWidth - font.width("…"))) + "…";
                }
                row.setMessage(Component.literal(name));
                setButtonState(row, active ? VisualState.SELECTED : VisualState.NORMAL);
                Button compare = compareButtons.get(i);
                compare.visible = !active && activeId != null;
                Button auto = autoButtons.get(i);
                boolean routed = context != null && profile.id().equals(KeysetClient.routedProfile(context.id()));
                auto.active = context != null;
                auto.setMessage(Component.literal(routed ? "✓" : "↗"));
                auto.setTooltip(Tooltip.create(context == null
                    ? Component.translatable("keyset.auto_profile_requires_world")
                    : Component.translatable(routed ? "keyset.auto_profile_remove_tip" : "keyset.auto_profile_set_tip",
                        context.label())));
                setButtonState(auto, routed ? VisualState.SELECTED : VisualState.NORMAL);
                deleteButtons.get(i).active = profiles.size() > 1;
            }
        }

        private void report(Component message, boolean error) {
            pickerStatus = message;
            pickerStatusError = error;
        }

        @Override
        public void tick() {
            super.tick();
            if (refreshForSharedRevision()) init();
        }

        private void select(int slot) {
            Profile profile = profileAt(slot);
            if (profile == null) return;
            String id = profile.id();
            runAction(() -> KeysetClient.switchProfile(id));
            close();
        }

        private void compare(int slot) {
            Profile source = profileAt(slot);
            Profile active = KeysetClient.profiles().active();
            if (source == null || active == null || active.id().equals(source.id())) return;
            minecraft.setScreenAndShow(new ProfileComparisonScreen(KeysetScreen.this, source.id()));
        }

        private void rename(int slot) {
            Profile profile = profileAt(slot);
            if (profile == null) return;
            String id = profile.id();
            minecraft.setScreenAndShow(new NameDialog(Component.translatable("keyset.profile_rename_title"),
                profile.name(), name -> KeysetClient.renameProfile(id, name), ProfilePicker::new));
        }

        private void createNew() {
            minecraft.setScreenAndShow(new NameDialog(Component.translatable("keyset.profile_new_title"), "",
                KeysetClient::createProfile, ProfilePicker::new));
        }

        private void delete(int slot) {
            Profile profile = profileAt(slot);
            if (profile == null || currentProfiles().size() <= 1) return;
            String id = profile.id();
            minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
                if (confirmed) runAction(() -> KeysetClient.deleteProfile(id));
                minecraft.setScreenAndShow(new ProfilePicker());
            }, Component.translatable("keyset.profile_delete_title"),
                Component.translatable("keyset.profile_delete_prompt", profile.name()),
                Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
        }

        private void toggleAuto(int slot) {
            Profile profile = profileAt(slot);
            if (profile == null) return;
            KeysetClient.RouteResult[] result = new KeysetClient.RouteResult[1];
            if (runAction(() -> result[0] = KeysetClient.toggleAutoProfile(profile.id()))) {
                report(Component.translatable(result[0].enabled()
                    ? "keyset.auto_profile_enabled" : "keyset.auto_profile_removed",
                    result[0].profileName(), result[0].contextLabel()), false);
            } else {
                report(statusMessage, true);
            }
            refresh();
        }

        private void toggleScope() {
            boolean shared = !KeysetClient.profilesShared();
            if (!runAction(() -> KeysetClient.setProfilesShared(shared))) {
                close();
                return;
            }
            scroll = 0;
            statusMessage = Component.translatable(shared
                ? "keyset.profile_scope_attached" : "keyset.profile_scope_detached");
            statusIsError = false;
            report(statusMessage, false);
            rebuildModel();
            observedRevision = KeysetClient.revision();
            init();
        }

        private void close() {
            minecraft.setScreenAndShow(KeysetScreen.this);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            scroll = clampScroll(scroll - (int) Math.signum(scrollY), currentProfiles().size(), rows.size());
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
            drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
            graphics.fill(panelX + 12, panelY + 29, panelX + panelWidth - 12, panelY + 30, UiTheme.border());
            graphics.text(font, Component.translatable("keyset.profile_picker_title"), panelX + 14, panelY + 13, UiTheme.text());
            Component status = pickerStatus;
            if (status == null && KeysetClient.currentContext() == null) {
                status = Component.translatable("keyset.auto_profile_requires_world");
            }
            if (status != null) {
                drawText(graphics, status, panelX + 14, panelY + panelHeight - 45, panelWidth - 28,
                    pickerStatus == null ? UiTheme.muted() : pickerStatusError ? UiTheme.red() : UiTheme.accentText());
            }
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            String activeId = activeId();
            for (int i = 0; i < rows.size(); i++) {
                Profile profile = profileAt(i);
                Button row = rows.get(i);
                if (!row.visible || profile == null || !Objects.equals(activeId, profile.id())) continue;
                String badge = "✓ " + Component.translatable("keyset.profile_active_badge").getString();
                int badgeWidth = font.width(badge) + 10;
                int badgeX = row.getX() + row.getWidth() - badgeWidth - 4;
                int badgeY = row.getY() + 4;
                graphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 14, 0xA0152622);
                graphics.outline(badgeX, badgeY, badgeWidth, 14, UiTheme.accent());
                graphics.text(font, badge, badgeX + 5, badgeY + 3, UiTheme.accentText(), false);
            }
        }
    }

    /** Plain-language export and import: where the files go, what each button does, and a way to open the folder. */
    private final class TransferScreen extends ScaledScreen {
        private int panelX, panelY, panelWidth, panelHeight, textTop;
        private Component transferStatus;
        private boolean transferStatusError;
        private Button importButton;
        private int pendingFiles;

        private TransferScreen() {
            super(Component.translatable("keyset.transfer_title"));
        }

        @Override
        protected void init() {
            InterfaceScale.apply(this);
            clearWidgets();
            panelWidth = Math.max(1, Math.min(500, width - 24));
            panelHeight = Math.max(1, Math.min(height - 16, 292));
            panelX = (width - panelWidth) / 2;
            panelY = (height - panelHeight) / 2;
            int inner = panelWidth - 24, left = panelX + 12;
            textTop = panelY + 38;
            try {
                pendingFiles = KeysetClient.transferFileCount();
            } catch (IOException exception) {
                pendingFiles = 0;
            }
            int y = panelY + panelHeight - 29 - 4 * 24 - 8;
            addAction(left, y, inner, "keyset.transfer_export_active", ButtonStyle.PRIMARY, this::exportActive);
            addAction(left, y + 24, inner, "keyset.transfer_export_all", ButtonStyle.ACTION, this::exportAll);
            addAction(left, y + 48, inner, "keyset.transfer_open_folder", ButtonStyle.ACTION, this::openFolder);
            importButton = addAction(left, y + 72, inner, "keyset.transfer_import_files", ButtonStyle.ACTION, this::confirmImport);
            importButton.setMessage(Component.translatable("keyset.transfer_import_files").append(" (" + pendingFiles + ")"));
            addRenderableWidget(styled(new FlatButton(left, panelY + panelHeight - 29, inner, 20,
                Component.translatable("keyset.done"), ignored -> back()), ButtonStyle.TAB, true));
        }

        private Button addAction(int x, int y, int w, String key, ButtonStyle style, Runnable action) {
            return addRenderableWidget(styled(new FlatButton(x, y, w, 20, Component.translatable(key),
                ignored -> action.run()), style, true));
        }

        private void report(Component message, boolean error) {
            transferStatus = message;
            transferStatusError = error;
        }

        private void exportActive() {
            try {
                Profile active = KeysetClient.profiles().active();
                Path file = KeysetClient.exportActiveProfile();
                report(Component.translatable("keyset.transfer_exported_to", active == null ? "" : active.name(),
                    file.toString()), false);
            } catch (IOException | IllegalArgumentException exception) {
                report(localizedError(exception), true);
            }
            init();
        }

        private void exportAll() {
            try {
                int count = KeysetClient.exportAllProfiles();
                report(Component.translatable("keyset.transfer_exported_all", count,
                    KeysetClient.transferDirectory().toString()), false);
            } catch (IOException | IllegalArgumentException exception) {
                report(localizedError(exception), true);
            }
            init();
        }

        private void openFolder() {
            try {
                Path directory = KeysetClient.transferDirectory();
                java.nio.file.Files.createDirectories(directory);
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                String command = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
                new ProcessBuilder(command, directory.toAbsolutePath().toString()).start();
                report(Component.translatable("keyset.transfer_folder_opened", directory.toString()), false);
            } catch (IOException | RuntimeException exception) {
                report(localizedError(new IOException(exception)), true);
            }
        }

        private void confirmImport() {
            if (pendingFiles == 0) {
                report(Component.translatable("keyset.transfer_empty"), true);
                return;
            }
            minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
                if (confirmed) importNow();
                minecraft.setScreenAndShow(new TransferScreen());
            }, Component.translatable("keyset.transfer_import_title"),
                Component.translatable("keyset.transfer_import_prompt", pendingFiles),
                Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
        }

        private void importNow() {
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

        private void back() {
            minecraft.setScreenAndShow(new ProfilePicker());
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                back();
                return true;
            }
            return super.keyPressed(event);
        }

        private List<String> wrap(String text, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (font.width(next) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
            graphics.fill(panelX + 12, panelY + 29, panelX + panelWidth - 12, panelY + 30, UiTheme.border());
            graphics.text(font, title, panelX + 14, panelY + 13, UiTheme.text());
            int textWidth = panelWidth - 28;
            int y = textTop;
            for (String line : wrap(Component.translatable("keyset.transfer_how").getString(), textWidth)) {
                graphics.text(font, line, panelX + 14, y, UiTheme.muted(), false);
                y += 11;
            }
            y += 4;
            drawText(graphics, Component.translatable("keyset.transfer_folder", KeysetClient.transferDirectory().toString()),
                panelX + 14, y, textWidth, UiTheme.text());
            if (transferStatus != null) {
                List<String> lines = wrap(transferStatus.getString(), textWidth);
                int statusY = panelY + panelHeight - 29 - 4 * 24 - 8 - 4 - Math.min(2, lines.size()) * 11;
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    graphics.text(font, lines.get(i), panelX + 14, statusY + i * 11,
                        transferStatusError ? UiTheme.red() : UiTheme.accentText(), false);
                }
            }
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void cycleLayout() {
        layoutIndex++;
        if (layoutIndex >= KeyboardLayouts.MANUAL.size()) layoutIndex = -1;
        init();
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
        String override = dev.keyset.editor.KeyboardKeyNames.translationKey(keyName,
            KeysetClient.appearance().keyboardPresentation().platform());
        if (override != null) return Component.translatable(override);
        InputConstants.Key key = InputConstants.getKey(keyName);
        if (keyName.startsWith("key.keyboard.")) {
            String physical = physicalKey(key.getValue());
            if (physical != null) return Component.literal(keyboardLayout.label(physical, key.getDisplayName().getString()));
        }
        return key.getDisplayName();
    }

    private Component bindingDisplay(Binding value) {
        String prefix = KeysetClient.modifierPrefix(value);
        var result = Component.literal(prefix).append(inputLabel(value.key()));
        for (String extra : value.extraKeys()) result.append(" + ").append(inputLabel(extra));
        return result;
    }

    private void onVirtualKey(VirtualKey key) {
        if (selectedMapping != null) {
            if (!key.mouse() && key.code() == InputConstants.KEY_ESCAPE) unbindSelected();
            else bindCaptured(keyName(key), virtualCtrl, virtualShift, virtualAlt);
        } else {
            filterByKey(keyName(key));
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
        if (mapping == null || !bindingIndex.conflicts(mapping)) return;
        // No dialog: show every action that uses the same key, right in the list.
        List<String> keys = bindingIndex.binding(mapping).keys();
        if (keys.isEmpty()) return;
        if (selectedMapping != null) clearSelection();
        category = null;
        modFilter = null;
        conflictsOnly = false;
        unboundOnly = false;
        filterByKey(keys.get(keys.size() - 1));
        String names = KeysetClient.conflictsFor(mapping, bindingIndex.binding(mapping)).stream()
            .map(value -> KeysetClient.name(value).getString()).collect(java.util.stream.Collectors.joining(", "));
        statusMessage = Component.translatable("keyset.conflict_inspect", names);
        statusIsError = false;
    }

    private void filterByKey(String input) {
        virtualKeyFilter = input;
        settingVirtualKeyFilter = true;
        searchQuery = "";
        searchBox.setValue("");
        settingVirtualKeyFilter = false;
        bindingScroll = 0;
        if (singlePane) compactPage = EditorViewport.Page.ACTIONS;
        refreshRows();
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
        if (isSectionHidden(entry.category())) return;
        boolean collapsed = COLLAPSED_SECTIONS.add(entry.category().id().toString());
        if (!collapsed) COLLAPSED_SECTIONS.remove(entry.category().id().toString());
        collapseRevision++;
        if (collapsed && selectedMapping != null && selectedMapping.getCategory().equals(entry.category())) {
            clearSelection();
        } else {
            refreshRows();
        }
    }

    private void setAllHidden(boolean hide) {
        boolean modsMode = modFilterMode;
        List<ModFilter> listedMods = List.copyOf(searchedMods);
        List<KeyMapping.Category> listedCategories = List.copyOf(searchedCategories);
        if (runAction(() -> {
            if (modsMode) for (ModFilter value : listedMods) KeysetClient.setModHidden(value.id(), hide);
            else for (KeyMapping.Category value : listedCategories) KeysetClient.setSectionHidden(value, hide);
        }) && hide) {
            if (modsMode && listedMods.stream().anyMatch(value -> value.id().equals(modFilter))) modFilter = null;
            if (!modsMode && listedCategories.contains(category)) category = null;
        }
        refreshRows();
    }

    private void toggleHiddenAt(int slot) {
        int index = (modFilterMode ? modScroll : categoryScroll) + slot;
        if (modFilterMode) {
            if (index < 0 || index >= searchedMods.size()) return;
            ModFilter value = searchedMods.get(index);
            boolean hidden = KeysetClient.isModHidden(value.id());
            if (runAction(() -> KeysetClient.setModHidden(value.id(), !hidden))) {
                if (!hidden && value.id().equals(modFilter)) modFilter = null;
                statusMessage = Component.translatable(hidden ? "keyset.section_restored_notice"
                    : "keyset.visibility_hidden_notice", value.name());
                statusIsError = false;
            }
        } else {
            if (index < 0 || index >= searchedCategories.size()) return;
            KeyMapping.Category value = searchedCategories.get(index);
            boolean hidden = KeysetClient.isCategoryHidden(value.id().toString());
            if (runAction(() -> KeysetClient.setSectionHidden(value, !hidden))) {
                if (!hidden && value.equals(category)) category = null;
                statusMessage = Component.translatable(hidden ? "keyset.section_restored_notice"
                    : "keyset.visibility_hidden_notice", KeysetClient.categoryLabel(value));
                statusIsError = false;
            }
        }
        refreshRows();
    }

    private void hideSectionAt(int slot) {
        ActionListEntry entry = entryAt(slot);
        if (entry == null || !entry.isSection()) return;
        int anchor = bindingScroll;
        boolean hidden = isSectionHidden(entry.category());
        if (runAction(() -> KeysetClient.setSectionHidden(entry.category(), !hidden))) {
            if (hidden) COLLAPSED_SECTIONS.remove(entry.category().id().toString());
            else if (entry.category().equals(category)) category = null;
            bindingScroll = clampScroll(anchor, displayEntries.size(), visibleRowCapacity());
            statusMessage = Component.translatable(hidden ? "keyset.section_restored_notice" : "keyset.visibility_hidden_notice", KeysetClient.categoryLabel(entry.category()));
            statusIsError = false;
            refreshVisibleRows();
        }
    }

    private void confirmUnbindSectionAt(int slot) {
        ActionListEntry entry = entryAt(slot);
        if (entry == null || !entry.isSection()) return;
        long revision = KeysetClient.revision();
        int count = (int) allMappings.stream().filter(mapping -> mapping.getCategory().equals(entry.category())).count();
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) runAction(() -> KeysetClient.unbindCategory(entry.category().id().toString(), revision));
            minecraft.setScreenAndShow(this);
        }, Component.translatable("keyset.section_unbind_title"), Component.translatable("keyset.section_unbind_prompt",
            KeysetClient.categoryLabel(entry.category()), count), Component.translatable("keyset.confirm"), Component.translatable("keyset.cancel")));
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
        if (!KeysetClient.appearance().equals(appearance) || KeysetClient.sidebarRatio() != layoutSidebarRatio) {
            init();
            return true;
        }
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
        graphics.fill(x, y, x + w, y + h, UiTheme.panel());
        graphics.fill(x, y, x + w, y + 1, UiTheme.border());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (singlePane) {
            KeysetBranding.drawLogo(graphics, 8, 6, 20, 20);
            graphics.text(font, "KEYSET", 32, 7, 0xFFFFFFFF, true);
            graphics.text(font, KeysetBranding.VERSION, 32, 19, 0xFFB2B7B5, true);
            graphics.fill(8, viewport.contentTop() - 2, width - 8, viewport.contentTop() - 1, UiTheme.border());
            graphics.fill(8, viewport.footerY() - 6, width - 8, viewport.footerY() - 5, UiTheme.border());
        } else {
            KeysetBranding.drawLogo(graphics, 16, 17, 30, 30);
            graphics.text(font, "KEYSET", 53, 20, 0xFFFFFFFF, true);
            graphics.text(font, KeysetBranding.VERSION + "+mc26.3", 53, 36, 0xFFB2B7B5, true);
            graphics.text(font, Component.translatable("keyset.profile"), profileCycleButton.getX(), 12, 0xFFB2B7B5, true);
            graphics.fill(16, 57, width - 16, 58, UiTheme.border());
            graphics.fill(16, height - 48, width - 16, height - 47, UiTheme.border());
        }

        if (!singlePane) graphics.enableScissor(sidebarX, 64, sidebarX + sidebarWidth, height - 52);
        if (showInput()) {
            int cardTop = singlePane ? viewport.contentTop() : 64 - leftScroll;
            drawCard(graphics, sidebarX, cardTop, sidebarWidth,
                (singlePane ? inputCardBottom : categoryCardY) - cardTop);
            if (mouseMode) {
                int bottom = singlePane ? viewport.inputControlsY() - 6 : summaryY - 22;
                graphics.fill(sidebarX + 8, keyboardPanelY - 4, sidebarX + sidebarWidth - (maxLeftScroll > 0 ? 14 : 8),
                    bottom + 4, 0x38000000);
            } else if (keyboardDiagram != null) {
                KeyboardDiagramLayout.Rect bounds = keyboardDiagram.bounds();
                graphics.fill(bounds.x() - 4, bounds.y() - 4, bounds.x() + bounds.width() + 4,
                    bounds.y() + bounds.height() + 4, 0x38000000);
            }
        }
        if (showCategories() && singlePane) {
            drawCard(graphics, sidebarX, categoryCardY, sidebarWidth, inputCardBottom - categoryCardY);
        } else if (showCategories()) {
            // Same panel as the keyboard; its separator stops short of the scrollbar instead of crossing it.
            int bottom = Math.max(height - 54, categoryListTop + Math.max(1, sidebarShown) * 20 + 6);
            graphics.fill(sidebarX, categoryCardY, sidebarX + sidebarWidth, bottom, UiTheme.panel());
            int lineEnd = maxLeftScroll > 0 ? leftBarX() - 6 : sidebarX + sidebarWidth - 12;
            graphics.fill(sidebarX + 12, categoryCardY, lineEnd, categoryCardY + 1, UiTheme.border());
        }
        if (!singlePane) graphics.disableScissor();
        if (showActions()) drawCard(graphics, mainX, singlePane ? viewport.contentTop() : 64, mainWidth,
            singlePane ? listBottom - viewport.contentTop() : height - 54 - 64);
        if (!singlePane) {
        int dividerX = dividerDragging ? draggedDividerX : panels.dividerX();
        boolean dividerHover = mouseX >= dividerX - 5 && mouseX <= dividerX + 5
            && mouseY >= 64 && mouseY <= height - 52;
        graphics.fill(dividerX - 1, 64, dividerX + 1, height - 52,
            dividerDragging || dividerHover ? UiTheme.accent() : UiTheme.border());
        graphics.fill(dividerX - 3, height / 2 - 14, dividerX + 3, height / 2 + 14,
            dividerDragging || dividerHover ? (UiTheme.accent() & 0xFFFFFF) | 0xC0000000 : UiTheme.keyEdge());
        }
        if (showActions()) {
        if (!singlePane && viewport.searchY() > listTitleY) {
        Component listTitle = virtualKeyFilter == null ? Component.translatable("keyset.bindings")
            : Component.translatable("keyset.bindings_for_key", inputLabel(virtualKeyFilter));
        drawText(graphics, listTitle, mainX + 12, listTitleY, mainWidth - 110, UiTheme.text());
        Component count = Component.translatable("keyset.binding_count", filteredMappings.size());
        graphics.text(font, count, mainX + mainWidth - 12 - font.width(count), listTitleY, UiTheme.muted(), false);
        }
        int searchLeft = mainX + (singlePane ? 4 : 8);
        int searchRight = Math.min(mainX + mainWidth - 8, searchLeft + searchBox.getWidth() + 30);
        graphics.fill(searchLeft, searchY - (singlePane ? 2 : 4), searchRight,
            searchY + viewport.searchHeight() + (singlePane ? 2 : -1), UiTheme.control());
        graphics.fill(searchLeft, searchY + viewport.searchHeight() - 1, searchRight,
            searchY + viewport.searchHeight(), searchBox.isFocused() ? UiTheme.accent() : UiTheme.border());
        if (!singlePane) {
            graphics.text(font, Component.translatable("keyset.action_column"), mainX + 16, listTop - 16, UiTheme.muted(), false);
            if (!rowBindingButtons.isEmpty()) graphics.text(font, Component.translatable("keyset.key_column"),
                rowBindingButtons.get(0).getX() + 5, listTop - 16, UiTheme.muted(), false);
        }

        for (int i = 0; i < visibleRowCapacity(); i++) {
            ActionListEntry entry = entryAt(i);
            if (entry != null && entry.isSection()) {
                int y = listTop + i * rowHeight;
                int left = mainX + 8, right = mainX + mainWidth - 12;
                boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + rowHeight - 2;
                graphics.fill(left, y, right, y + rowHeight - 2, hovered ? UiTheme.control() : UiTheme.panel());
                graphics.fill(left + 6, y + rowHeight - 3, right - 6, y + rowHeight - 2,
                    Objects.equals(category, entry.category()) ? UiTheme.accent() : UiTheme.border());
                continue;
            }
            KeyMapping mapping = mappingAt(i);
            if (mapping == null) continue;
            int y = listTop + i * rowHeight;
            boolean hovered = mouseX >= mainX + 8 && mouseX < mainX + mainWidth - 12
                && mouseY >= y && mouseY < y + rowHeight - 2;
            boolean cursor = cursorMapping == mapping;
            int color = selectedMapping == mapping ? UiTheme.selected() : hovered || cursor ? UiTheme.panelLight()
                : (bindingScroll + i) % 2 == 0 ? UiTheme.row() : UiTheme.rowAlternate();
            graphics.fill(mainX + 8, y, mainX + mainWidth - 12, y + rowHeight - 2, color);
            if (selectedMapping == mapping) graphics.fill(mainX + 8, y, mainX + 10, y + rowHeight - 2, UiTheme.accent());
            else if (cursor) graphics.outline(mainX + 8, y, mainX + mainWidth - 20, rowHeight - 2, UiTheme.accent());
        }
        if (filteredMappings.isEmpty()) {
            Component empty = virtualKeyFilter == null ? Component.translatable("keyset.no_bindings")
                : Component.translatable("keyset.no_bindings_for_key", inputLabel(virtualKeyFilter));
            drawText(graphics, empty, mainX + 16, listTop + (singlePane ? 3 : 18), mainWidth - 32, UiTheme.muted());
        }
        if (displayEntries.size() > visibleRowCapacity()) {
            int trackHeight = visibleRowCapacity() * rowHeight - 2;
            int thumbHeight = Math.max(18, trackHeight * visibleRowCapacity() / displayEntries.size());
            int thumbY = listTop + (trackHeight - thumbHeight) * bindingScroll
                / Math.max(1, displayEntries.size() - visibleRowCapacity());
            drawScrollbar(graphics, mainX + mainWidth - 8, listTop, trackHeight, thumbY, thumbHeight,
                actionsBarDragging || actionsBarHit(mouseX, mouseY));
        }
        }

        if (!singlePane) graphics.enableScissor(sidebarX, 64, sidebarX + sidebarWidth, height - 52);
        if (mouseMode && showInput() && mouseDiagram != null) {
            MouseDiagramRenderer.shell(graphics, mouseDiagram, mouseArtwork);
        }
        if (!compact && showInput()) {
            int legendY = summaryY - 16;
            graphics.fill(sidebarX + 16, legendY + 2, sidebarX + 20, legendY + 6,
                UiTheme.keyColors(true, false, false).edge());
            graphics.text(font, Component.translatable("keyset.legend_assigned"), sidebarX + 26, legendY, UiTheme.muted(), false);
            graphics.fill(sidebarX + 140, legendY + 2, sidebarX + 144, legendY + 6,
                UiTheme.keyColors(false, true, false).edge());
            graphics.text(font, Component.translatable("keyset.legend_conflict"), sidebarX + 150, legendY, UiTheme.muted(), false);
        }
        if (!singlePane) {
        graphics.fill(sidebarX + 12, summaryY - 4, maxLeftScroll > 0 ? leftBarX() - 6 : sidebarX + sidebarWidth - 12,
            summaryY - 3, UiTheme.border());
        if (selectedMapping == null) {
            drawText(graphics, Component.translatable("keyset.selection_empty"), sidebarX + 14, summaryY + 8, sidebarWidth - 28, UiTheme.text());
            drawText(graphics, Component.translatable("keyset.selection_hint"), sidebarX + 14, summaryY + 24, sidebarWidth - 28, UiTheme.muted());
        } else {
            graphics.text(font, Component.translatable(capturing ? "keyset.listening" : "keyset.selected_action"),
                sidebarX + 14, summaryY + 6, UiTheme.text(), false);
            drawText(graphics, KeysetClient.name(selectedMapping), sidebarX + 14, summaryY + 22,
                sidebarWidth - 212, UiTheme.text());
        }
        }
        int sidebarItemCount = modFilterMode ? searchedMods.size() : searchedCategories.size();
        int sidebarVisibleCount = Math.max(1, categoryButtons.size());
        int sidebarScroll = modFilterMode ? modScroll : categoryScroll;
        if (showCategories() && sidebarItemCount > sidebarVisibleCount && !categoryButtons.isEmpty()) {
            graphics.fill(sidebarX + sidebarWidth - 11, categoryListTop, sidebarX + sidebarWidth - 9, categoryListBottom, UiTheme.border());
            int total = categoryListBottom - categoryListTop;
            int thumb = Math.max(12, total * sidebarVisibleCount / sidebarItemCount);
            int y = categoryListTop + (total - thumb) * sidebarScroll / Math.max(1, sidebarItemCount - sidebarVisibleCount);
            graphics.fill(sidebarX + sidebarWidth - 11, y, sidebarX + sidebarWidth - 9, y + thumb, UiTheme.muted());
        }
        if (!singlePane) {
            graphics.disableScissor();
            if (maxLeftScroll > 0) {
                int track = leftTrackHeight();
                int thumb = leftThumbHeight();
                drawScrollbar(graphics, leftBarX(), leftTrackTop(), track,
                    leftTrackTop() + (track - thumb) * leftScroll / maxLeftScroll, thumb,
                    leftBarDragging || leftBarHit(mouseX, mouseY));
            }
        }
        Component footer = statusMessage != null ? statusMessage : Component.translatable("keyset.footer_hint");
        if (!singlePane) drawText(graphics, footer, sidebarX + 2, height - 31, sidebarWidth - 4,
            statusIsError ? 0xFFFF8A8A : 0xFFB2B7B5);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (showCategories()) {
            int visibleItems = modFilterMode ? searchedMods.size() : searchedCategories.size();
            if (visibleItems == 0) {
                drawText(graphics, Component.translatable("keyset.sidebar_empty"), sidebarX + 14,
                    categoryListTop + 4, sidebarWidth - 28, UiTheme.muted());
            }
            for (Map.Entry<Button, Integer> entry : sidebarButtonCounts.entrySet()) {
                Button button = entry.getKey();
                if (!button.visible) continue;
                String count = Integer.toString(entry.getValue());
                int countX = button.getX() + button.getWidth() - 6 - font.width(count);
                int countY = button.getY() + (button.getHeight() - font.lineHeight) / 2;
                int color = button instanceof FlatButton flat && flat.visualState == VisualState.SELECTED
                    ? UiTheme.accentText() : UiTheme.muted();
                graphics.text(font, count, countX, countY, color, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && actionsBarHit(event.x(), event.y())) {
            actionsBarDragging = true;
            dragActionsBarTo(event.y());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && leftBarHit(event.x(), event.y())) {
            leftBarDragging = true;
            dragLeftBarTo(event.y());
            return true;
        }
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
        if (leftBarDragging || actionsBarDragging) {
            leftBarDragging = actionsBarDragging = false;
            return true;
        }
        if (dividerDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dividerDragging = false;
            setDragging(false);
            double ratio = panels.ratioAtDivider(draggedDividerX);
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
        if (actionsBarDragging) {
            dragActionsBarTo(event.y());
            return true;
        }
        if (leftBarDragging) {
            dragLeftBarTo(event.y());
            return true;
        }
        if (dividerDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggedDividerX = clampDividerX((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private boolean isDividerAt(double x, double y) {
        if (singlePane) return false;
        int dividerX = panels.dividerX();
        return y >= 64 && y <= height - 52 && x >= dividerX - 6 && x <= dividerX + 6;
    }

    private int clampDividerX(int x) {
        return panels.clampDividerX(x);
    }

    /** True while the screen is handling a local binding capture or splitter drag. */
    public boolean isCapturingBindings() {
        return capturing || dividerDragging;
    }

    private boolean isNavigationAt(double x, double y) {
        if (compactTabs.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (searchBox.isMouseOver(x, y) || conflictsButton.isMouseOver(x, y)
            || unboundButton.isMouseOver(x, y) || (sidebarSearchBox.visible && sidebarSearchBox.isMouseOver(x, y))
            || allCategoryButton.isMouseOver(x, y) || categoryFilterButton.isMouseOver(x, y)
            || modFilterButton.isMouseOver(x, y) || profileCycleButton.isMouseOver(x, y)
            || newProfileButton.isMouseOver(x, y) || renameProfileButton.isMouseOver(x, y)
            || deleteProfileButton.isMouseOver(x, y) || appearanceButton.isMouseOver(x, y)) return true;
        if (categoryHideButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (rowSectionButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (rowSectionHideButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (rowConflictButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        if (filterChipButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y))) return true;
        return categoryButtons.stream().anyMatch(button -> button.visible && button.isMouseOver(x, y));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overCategoryList = mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
            && mouseY >= categoryListTop && mouseY <= categoryListBottom;
        // The whole actions card scrolls the list, not only the rows themselves.
        boolean overActionList = mouseX >= mainX && mouseX <= mainX + mainWidth
            && mouseY >= (singlePane ? viewport.contentTop() : 64) && mouseY <= (singlePane ? listBottom : height - 54);

        boolean overLeftColumn = !singlePane && maxLeftScroll > 0 && mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
            && mouseY >= 64 && mouseY <= height - 52;
        if (overLeftColumn) {
            scrollLeftBy(-(int) Math.signum(scrollY) * 28);
            return true;
        }
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
                    searchedMods.size(), Math.max(1, categoryButtons.size()));
            } else {
                categoryScroll = clampScroll(categoryScroll - (int) Math.signum(scrollY) * 2,
                    searchedCategories.size(), Math.max(1, categoryButtons.size()));
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
        boolean control = (event.modifiers() & InputConstants.MOD_CONTROL) != 0;
        boolean shift = (event.modifiers() & InputConstants.MOD_SHIFT) != 0;
        if (control && event.key() == InputConstants.KEY_Z && !shift) { undoShortcut(false); return true; }
        if (control && (event.key() == InputConstants.KEY_Y || event.key() == InputConstants.KEY_Z && shift)) {
            undoShortcut(true);
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!control && showActions() && handleListKey(event)) return true;
        return super.keyPressed(event);
    }

    private void undoShortcut(boolean redo) {
        if (!(redo ? KeysetClient.canRedo() : KeysetClient.canUndo())) return;
        if (runAction(redo ? KeysetClient::redo : KeysetClient::undo)) {
            statusMessage = Component.translatable(redo ? "keyset.redo_done" : "keyset.undo_done");
            statusIsError = false;
        }
    }

    /** Arrow keys walk the action list, Page keys jump a screen, Enter starts changing the highlighted action. */
    private boolean handleListKey(KeyEvent event) {
        boolean typing = getFocused() instanceof EditBox box && box.isFocused();
        int key = event.key();
        int page = Math.max(1, visibleRowCapacity() - 1);
        int delta = switch (key) {
            case InputConstants.KEY_DOWN -> 1;
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_PAGEDOWN -> typing ? 0 : page;
            case InputConstants.KEY_PAGEUP -> typing ? 0 : -page;
            default -> 0;
        };
        if (delta != 0) {
            moveCursor(delta);
            if (typing) { setFocused(null); }
            return true;
        }
        if (!typing && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            KeyMapping target = cursorMapping != null ? cursorMapping : selectedMapping;
            if (target == null) return false;
            int index = indexOfMapping(target);
            if (index < 0) return false;
            if (index < bindingScroll || index >= bindingScroll + visibleRowCapacity()) {
                bindingScroll = clampScroll(index, displayEntries.size(), visibleRowCapacity());
                refreshVisibleRows();
            }
            captureMappingAt(index - bindingScroll);
            return true;
        }
        return false;
    }

    private int indexOfMapping(KeyMapping mapping) {
        for (int i = 0; i < displayEntries.size(); i++) {
            if (displayEntries.get(i).mapping() == mapping) return i;
        }
        return -1;
    }

    private void moveCursor(int delta) {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < displayEntries.size(); i++) if (!displayEntries.get(i).isSection()) rows.add(i);
        if (rows.isEmpty()) return;
        int current = -1;
        KeyMapping from = cursorMapping != null ? cursorMapping : selectedMapping;
        for (int i = 0; i < rows.size(); i++) {
            if (displayEntries.get(rows.get(i)).mapping() == from) { current = i; break; }
        }
        int target = current < 0 ? (delta > 0 ? 0 : rows.size() - 1)
            : Math.max(0, Math.min(rows.size() - 1, current + delta));
        int entryIndex = rows.get(target);
        cursorMapping = displayEntries.get(entryIndex).mapping();
        int capacity = visibleRowCapacity();
        int first = entryIndex > 0 && displayEntries.get(entryIndex - 1).isSection() ? entryIndex - 1 : entryIndex;
        if (first < bindingScroll) bindingScroll = first;
        else if (entryIndex >= bindingScroll + capacity) bindingScroll = entryIndex - capacity + 1;
        bindingScroll = clampScroll(bindingScroll, displayEntries.size(), capacity);
        refreshVisibleRows();
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
    public void removed() {
        mouseArtwork.close();
        super.removed();
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

    private final class NameDialog extends ScaledScreen {
        private final Component dialogTitle;
        private final String initialValue;
        private final NameAction action;
        private final java.util.function.Supplier<Screen> returnTo;
        private EditBox nameBox;
        private Component error;

        private NameDialog(Component title, String initialValue, NameAction action) {
            this(title, initialValue, action, () -> KeysetScreen.this);
        }

        private NameDialog(Component title, String initialValue, NameAction action,
                           java.util.function.Supplier<Screen> returnTo) {
            super(title);
            this.dialogTitle = title;
            this.initialValue = initialValue;
            this.action = action;
            this.returnTo = returnTo;
        }

        @Override
        protected void init() {
            InterfaceScale.apply(this);
            clearWidgets();
            String currentValue = nameBox == null ? initialValue : nameBox.getValue();
            int panelWidth = Math.min(310, width - 20);
            int panelHeight = 102;
            int x = (width - panelWidth) / 2;
            int y = (height - panelHeight) / 2;
            nameBox = new EditBox(font, x + 16, y + 38, panelWidth - 32, 16,
                Component.translatable("keyset.profile_name_hint"));
            nameBox.setBordered(false);
            nameBox.setTextColor(UiTheme.text());
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
            minecraft.setScreenAndShow(returnTo.get());
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
            graphics.fill(x + 12, y + 31, x + panelWidth - 12, y + 55, UiTheme.control());
            graphics.fill(x + 12, y + 54, x + panelWidth - 12, y + 55, nameBox.isFocused() ? UiTheme.accent() : UiTheme.border());
            graphics.text(font, dialogTitle, x + 13, y + 12, UiTheme.text());
            if (error != null) graphics.text(font, error, x + 13, y + 87, UiTheme.red());
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private enum VisualState { NORMAL, ASSIGNED, SELECTED, CONFLICT }
    private enum ButtonStyle { ACTION, PRIMARY, TAB, KEYCAP, ROW, SECTION, SECTION_ACTION, BINDING, QUIET, LIST }

    private static final class FlatButton extends Button {
        private VisualState visualState = VisualState.NORMAL;
        private ButtonStyle style = ButtonStyle.ACTION;
        private boolean centered = true;
        private Control mouseControl;
        private MouseDiagramLayout mouseLayout;
        private MouseTextureCache mouseArtwork;
        private boolean freeKey, assignedKey, inputConflict;
        private boolean inactiveSection;
        private String keyboardInput;

        private FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            if (mouseControl != null && mouseLayout != null) {
                return visible && active && mouseLayout.hitTest(mouseControl, x, y);
            }
            return super.isMouseOver(x, y);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused() && active;
            boolean selected = visualState == VisualState.SELECTED;
            boolean conflict = visualState == VisualState.CONFLICT || inputConflict;
            int color = !active ? 0x70FFFFFF : conflict ? UiTheme.red()
                : selected && (style == ButtonStyle.TAB || style == ButtonStyle.QUIET || style == ButtonStyle.LIST) ? UiTheme.accentText() : UiTheme.text();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (mouseControl != null) {
                isHovered = isHovered && isMouseOver(mouseX, mouseY);
                hover = active && (isHovered || isFocused());
                MouseDiagramRenderer.button(graphics, Minecraft.getInstance().font, getMessage(), mouseControl,
                    new Rect(x, y, w, h), new State(hover, selected, assignedKey,
                        conflict, isFocused(), freeKey), mouseArtwork);
                return;
            }
            if (style == ButtonStyle.LIST && inactiveSection) color = (UiTheme.muted() & 0xFFFFFF) | 0x88000000;
            if (style == ButtonStyle.KEYCAP) {
                var state = new KeycapPainter.State(hover, selected, assignedKey, conflict, isFocused(), freeKey);
                KeycapPainter.draw(graphics::fill, x, y, w, h, UiTheme.currentPalette(), state);
                color = KeycapPainter.labelColor(UiTheme.currentPalette(), state);
            } else if (style == ButtonStyle.ROW) {
                // The screen highlights the whole action row, including its controls.
            } else if (style == ButtonStyle.SECTION || style == ButtonStyle.SECTION_ACTION) {
                // One background and underline span the title and both section controls.
                color = selected ? UiTheme.accentText() : hover ? UiTheme.text() : UiTheme.muted();
                if (inactiveSection) color = (UiTheme.muted() & 0xFFFFFF) | 0x88000000;
            } else {
                int fill = selected ? UiTheme.selected() : hover ? UiTheme.hover() : UiTheme.control();
                if (style == ButtonStyle.BINDING && !selected) {
                    fill = conflict ? hover ? UiTheme.conflictHover() : UiTheme.conflictSurface() : hover ? UiTheme.hover() : UiTheme.row();
                }
                if (!active) fill = 0x40000000;
                fill = UiTheme.widgetSurface(fill);
                if (style != ButtonStyle.LIST || hover || selected || UiTheme.text() == 0xFF000000) {
                    graphics.fill(x, y, x + w, y + h, fill);
                }
                if (style == ButtonStyle.BINDING) {
                    graphics.fill(x, y + h - 1, x + w, y + h, conflict ? 0xFF865858 : UiTheme.keyShadow());
                }
                if (active && (selected || style == ButtonStyle.PRIMARY)) {
                    graphics.fill(x, y + h - 2, x + w, y + h, UiTheme.accent());
                }
            }
            if (active && isFocused()) graphics.outline(x, y, w, h, UiTheme.text());
            Font font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            if (style == ButtonStyle.KEYCAP) {
                KeycapRenderer.drawKey(graphics, font, keyboardInput, label,
                    KeycapPainter.faceBounds(x, y, w, h), color, 0.25f, KeysetClient.appearance().keyboardPresentation());
                return;
            }
            int inset = style == ButtonStyle.SECTION ? 18 : centered ? 8 : 12;
            int available = Math.max(1, w - inset);
            if (font.width(label) > available) {
                label = font.plainSubstrByWidth(label, Math.max(1, available - font.width("…"))) + "…";
            }
            int textX = centered ? x + (w - font.width(label)) / 2
                : x + (style == ButtonStyle.SECTION ? 10 : 6);
            int textY = y + (h - font.lineHeight) / 2;
            graphics.text(font, label, textX, textY, color, false);
        }
    }

    private record VirtualKey(int code, String label, boolean mouse) {}

    private record FilterState(KeyMapping.Category category, String modFilter, boolean conflictsOnly,
                               boolean unboundOnly, String query, String key, int collapseRevision) {}
    private record FilterChip(int index, Component label, Component tooltip) {}
    private record ActionListEntry(KeyMapping mapping, KeyMapping.Category category, int count) {
        private boolean isSection() { return mapping == null; }
    }
    private record MappingView(Component name, Component bindingLabel, String modId,
                               ActionSearch.Document search, Tooltip tooltip) {}
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
