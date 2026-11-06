package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.editor.KeyboardDiagramLayout;
import dev.keyset.editor.KeyboardKeyNames;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.profile.KeyLegendMode;
import dev.keyset.profile.KeyboardBottomRow;
import dev.keyset.profile.KeyboardFormFactor;
import dev.keyset.profile.KeyboardPlatform;
import dev.keyset.profile.KeyboardPresentation;
import dev.keyset.profile.UiAppearance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Key legends and the bottom row, without a full keyboard: only the keys whose legend can actually change
 * are listed (left/right twins share one row), and the bottom row is a single strip of its own keys.
 */
public final class KeyboardStyleScreen extends ScaledScreen {
    private static final String P = "key.keyboard.";
    private static final Set<String> BOTTOM_INPUTS = Set.of(P + "fn", P + "left.control", P + "right.control",
        P + "left.win", P + "right.win", P + "left.alt", P + "right.alt", P + "space");
    /** Keys whose legend can be shown as text, an icon, or both; twins and clusters are edited together. */
    private static final List<List<String>> GROUPS = List.of(
        List.of(P + "escape"), List.of(P + "tab"), List.of(P + "caps.lock"), List.of(P + "backspace"),
        List.of(P + "enter", P + "keypad.enter"), List.of(P + "left.shift", P + "right.shift"),
        List.of(P + "left.control", P + "right.control"), List.of(P + "left.alt", P + "right.alt"),
        List.of(P + "left.win", P + "right.win"), List.of(P + "space"),
        List.of(P + "up", P + "down", P + "left", P + "right"),
        List.of(P + "home", P + "end", P + "page.up", P + "page.down"),
        List.of(P + "insert", P + "delete"),
        List.of(P + "print.screen", P + "pause", P + "scroll.lock", P + "num.lock"));
    private static final String[] GROUP_IDS = {"escape", "tab", "caps", "backspace", "enter", "shift", "ctrl", "alt",
        "win", "space", "arrows", "navigation", "editing", "system"};
    private static final int ROW_STEP = 28;
    private static final int PREVIEW_WIDTH = 46;

    private enum Tab { LABELS, BOTTOM_ROW }

    private final Screen parent;
    private Tab tab = Tab.LABELS;
    private String selectedBottom = P + "space";
    private int panelX, panelY, panelWidth, panelHeight, left, right, top, bottom;
    private int contentTop, contentBottom, rowCapacity, scroll, nameWidth;
    private Map<String, String> labels = Map.of();
    private KeyboardFormFactor resolvedForm = KeyboardFormFactor.FULL;
    private final List<KeysetWidgets.FlatButton[]> rowModes = new ArrayList<>();
    private final List<KeyCellButton> stripKeys = new ArrayList<>();
    private KeysetWidgets.FlatButton moveLeft, moveRight;
    private KeysetWidgets.FlatButton resetAllButton;
    private List<KeyboardDiagramLayout.Cell> bottomRow = List.of();
    private Component error;

    public KeyboardStyleScreen(Screen parent) {
        this(parent, null);
    }

    /** Opens on the key chosen in the personalization preview, when it has an editable legend. */
    public KeyboardStyleScreen(Screen parent, String selectedInput) {
        super(Component.translatable("keyset.legend_title"));
        this.parent = parent;
        if (selectedInput != null) {
            for (int i = 0; i < GROUPS.size(); i++) {
                if (GROUPS.get(i).contains(selectedInput)) { scroll = i; break; }
            }
            if (BOTTOM_INPUTS.contains(selectedInput)) selectedBottom = selectedInput;
        }
    }

    @Override
    protected void init() {
        InterfaceScale.apply(this);
        KeycapRenderer.resetFontMetrics();
        KeysetClient.ensureLoaded();
        clearWidgets();
        rowModes.clear();
        stripKeys.clear();
        moveLeft = null;
        panelWidth = Math.max(1, Math.min(640, width - 12));
        panelHeight = Math.max(1, Math.min(480, height - 12));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        left = panelX + 12;
        right = panelX + panelWidth - 12;
        top = panelY + 10;
        bottom = panelY + panelHeight - 12;

        addButton(Component.translatable("keyset.done"), right - 70, top, 70, 20,
            ignored -> onClose(), KeysetWidgets.ButtonStyle.PRIMARY);
        resetAllButton = addButton(Component.translatable("keyset.reset"), right - 70 - 4 - 80, top, 80, 20,
            ignored -> resetAll(), KeysetWidgets.ButtonStyle.ACTION);
        resetAllButton.visible = tab == Tab.LABELS;
        resetAllButton.setTooltip(Tooltip.create(Component.translatable("keyset.legend_reset_all")));

        int tabsY = top + 28;
        int gap = 4;
        int tabWidth = Math.max(1, (right - left - gap) / 2);
        addButton(Component.translatable("keyset.legend_tab_labels"), left, tabsY, tabWidth, 22,
            ignored -> switchTab(Tab.LABELS), KeysetWidgets.ButtonStyle.TAB).setSelected(tab == Tab.LABELS);
        addButton(Component.translatable("keyset.legend_tab_bottom"), left + tabWidth + gap, tabsY,
            right - left - tabWidth - gap, 22, ignored -> switchTab(Tab.BOTTOM_ROW),
            KeysetWidgets.ButtonStyle.TAB).setSelected(tab == Tab.BOTTOM_ROW);

        int platformY = tabsY + 26;
        int count = KeyboardPlatform.values().length;
        int platformWidth = Math.max(1, (right - left - 3 * (count - 1)) / count);
        KeyboardPlatform current = KeysetClient.appearance().keyboardPresentation().platform();
        for (KeyboardPlatform platform : KeyboardPlatform.values()) {
            int x = left + platform.ordinal() * (platformWidth + 3);
            addButton(Component.translatable(platform.translationKey()), x, platformY,
                platform.ordinal() == count - 1 ? right - x : platformWidth, 18,
                ignored -> setPlatform(platform), KeysetWidgets.ButtonStyle.TAB).setSelected(platform == current);
        }
        contentTop = platformY + 26;
        contentBottom = bottom;

        loadLabels();
        if (tab == Tab.LABELS) initLabels();
        else initBottomRow();
    }

    private void switchTab(Tab next) {
        if (tab == next) return;
        tab = next;
        init();
    }

    private void loadLabels() {
        UiAppearance appearance = KeysetClient.appearance();
        KeyboardFormFactor form = appearance.keyboardFormFactor().resolve(right - left, height);
        if (form == KeyboardFormFactor.AUTO) form = KeyboardFormFactor.FULL;
        resolvedForm = form;
        var layout = KeyboardDiagramLayout.forArea(0, 0, KeyboardDiagramLayout.columns(form) * 40,
            KeyboardDiagramLayout.rows(form) * 40, 100, form,
            KeyboardLayouts.forLanguage(minecraft.options.languageCode), appearance.keyboardPresentation());
        Map<String, String> found = new LinkedHashMap<>();
        for (var cell : layout.keys()) found.put(cell.input(), cell.label());
        labels = found;
        bottomRow = layout.keys().stream().filter(cell -> BOTTOM_INPUTS.contains(cell.input())).toList();
        if (bottomRow.stream().noneMatch(cell -> cell.input().equals(selectedBottom)) && !bottomRow.isEmpty()) {
            selectedBottom = bottomRow.get(0).input();
        }
    }

    // ---------------------------------------------------------------- labels tab

    private void initLabels() {
        rowCapacity = Math.max(1, (contentBottom - contentTop) / ROW_STEP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, GROUPS.size() - rowCapacity)));
        int modesWidth = Math.min(340, (right - left) * 58 / 100);
        nameWidth = Math.max(40, right - left - PREVIEW_WIDTH - 20 - modesWidth - 12);
        int modeGap = 3;
        int modeWidth = Math.max(1, (modesWidth - 2 * modeGap) / 3);
        int modesX = right - modesWidth - 8;
        for (int slot = 0; slot < rowCapacity; slot++) {
            KeysetWidgets.FlatButton[] modes = new KeysetWidgets.FlatButton[KeyLegendMode.values().length];
            for (KeyLegendMode mode : KeyLegendMode.values()) {
                int x = modesX + mode.ordinal() * (modeWidth + modeGap);
                int slotIndex = slot;
                String full = Component.translatable(mode.translationKey()).getString();
                int buttonWidth = mode.ordinal() == 2 ? modesX + modesWidth - x : modeWidth;
                Component label = font.width(full) + 10 <= buttonWidth ? Component.literal(full)
                    : Component.translatable(mode.translationKey().replace("legend_mode.", "legend_mode_short."));
                modes[mode.ordinal()] = addButton(label, x,
                    contentTop + slot * ROW_STEP + 2, mode.ordinal() == 2 ? modesX + modesWidth - x : modeWidth, 22,
                    ignored -> setGroupMode(scroll + slotIndex, mode), KeysetWidgets.ButtonStyle.TAB);
            }
            rowModes.add(modes);
        }
        refreshRows();
    }

    private void refreshRows() {
        KeyboardPresentation presentation = KeysetClient.appearance().keyboardPresentation();
        for (int slot = 0; slot < rowModes.size(); slot++) {
            int index = scroll + slot;
            boolean visible = index < GROUPS.size();
            KeyLegendMode shared = visible ? groupMode(presentation, GROUPS.get(index)) : null;
            for (KeyLegendMode mode : KeyLegendMode.values()) {
                var button = rowModes.get(slot)[mode.ordinal()];
                button.visible = visible;
                button.setSelected(mode == shared);
            }
        }
    }

    private static KeyLegendMode groupMode(KeyboardPresentation presentation, List<String> group) {
        KeyLegendMode first = presentation.modeFor(group.get(0));
        for (String input : group) if (presentation.modeFor(input) != first) return null;
        return first;
    }

    private void setGroupMode(int index, KeyLegendMode mode) {
        if (index < 0 || index >= GROUPS.size()) return;
        UiAppearance current = KeysetClient.appearance();
        KeyboardPresentation presentation = current.keyboardPresentation();
        for (String input : GROUPS.get(index)) presentation = presentation.withMode(input, mode);
        save(current.withKeyboardPresentation(presentation));
        refreshRows();
    }

    private void resetAll() {
        UiAppearance current = KeysetClient.appearance();
        save(current.withKeyboardPresentation(new KeyboardPresentation(current.keyboardPresentation().platform(),
            Map.of(), current.keyboardPresentation().bottomRow())));
        if (error == null) init();
    }

    private Component groupName(int index) {
        return Component.translatable("keyset.legend_group." + GROUP_IDS[index]);
    }

    // ---------------------------------------------------------------- bottom row tab

    private void initBottomRow() {
        if (bottomRow.isEmpty()) return;
        int innerWidth = right - left;
        int first = bottomRow.get(0).bounds().x();
        var last = bottomRow.get(bottomRow.size() - 1).bounds();
        int contentWidth = last.x() + last.width() - first;
        float scale = Math.min(innerWidth / (float) contentWidth, 44f / bottomRow.get(0).bounds().height());
        int startX = left + (innerWidth - Math.round(contentWidth * scale)) / 2;
        int rowY = contentTop + 24;
        KeyboardPlatform platform = KeysetClient.appearance().keyboardPresentation().platform();
        for (var cell : bottomRow) {
            var bounds = cell.bounds();
            KeyCellButton button = addRenderableWidget(new KeyCellButton(startX + Math.round((bounds.x() - first) * scale),
                rowY, Math.max(1, Math.round(bounds.width() * scale)),
                Math.max(1, Math.round(bounds.height() * scale)), cell.input(), cell.label(),
                ignored -> { selectedBottom = cell.input(); refreshStrip(); }));
            String translation = KeyboardKeyNames.translationKey(cell.input(), platform);
            button.setTooltip(Tooltip.create(translation == null ? Component.translatable(cell.input())
                : Component.translatable(translation)));
            stripKeys.add(button);
        }
        int controlsY = rowY + 44 + 18;
        int gap = 4;
        int third = Math.max(1, (innerWidth - 2 * gap) / 3);
        moveLeft = addButton(Component.translatable("keyset.bottom_move_left"), left, controlsY, third, 22,
            ignored -> move(-1), KeysetWidgets.ButtonStyle.ACTION);
        moveRight = addButton(Component.translatable("keyset.bottom_move_right"), left + third + gap, controlsY,
            third, 22, ignored -> move(1), KeysetWidgets.ButtonStyle.ACTION);
        addButton(Component.translatable("keyset.appearance_reset"), left + 2 * (third + gap), controlsY,
            innerWidth - 2 * (third + gap), 22, ignored -> saveBottom(KeyboardBottomRow.defaults()),
            KeysetWidgets.ButtonStyle.ACTION);
        int widthY = controlsY + 30;
        addButton(Component.literal("-"), left, widthY, 28, 22, ignored -> changeWidth(-1), KeysetWidgets.ButtonStyle.ACTION);
        addButton(Component.translatable("keyset.bottom_space_width",
                String.format(Locale.ROOT, "%.2f", currentSpaceWidth() / 4.0)),
            left + 32, widthY, Math.max(1, innerWidth - 64), 22,
            ignored -> saveBottom(KeysetClient.appearance().keyboardPresentation().bottomRow().withSpaceWidth(0)),
            KeysetWidgets.ButtonStyle.QUIET);
        addButton(Component.literal("+"), right - 28, widthY, 28, 22, ignored -> changeWidth(1), KeysetWidgets.ButtonStyle.ACTION);
        refreshStrip();
    }

    private void refreshStrip() {
        for (KeyCellButton button : stripKeys) button.selected = button.input.equals(selectedBottom);
        if (moveLeft != null) {
            int index = bottomIndex();
            moveLeft.active = index > 0;
            moveRight.active = index >= 0 && index < bottomRow.size() - 1;
        }
    }

    private int bottomIndex() {
        for (int i = 0; i < bottomRow.size(); i++) if (bottomRow.get(i).input().equals(selectedBottom)) return i;
        return -1;
    }

    private int currentSpaceWidth() {
        var appearance = KeysetClient.appearance();
        int stored = appearance.keyboardPresentation().bottomRow().spaceWidth();
        if (stored != 0) return stored;
        return appearance.keyboardPresentation().platform().resolved() == KeyboardPlatform.MAC
            ? (resolvedForm == KeyboardFormFactor.COMPACT_75 ? 22 : 30) : 25;
    }

    private void move(int direction) {
        int index = bottomIndex(), destination = index + direction;
        if (index < 0 || destination < 0 || destination >= bottomRow.size()) return;
        List<String> order = new ArrayList<>(bottomRow.stream().map(KeyboardDiagramLayout.Cell::input).toList());
        Collections.swap(order, index, destination);
        saveBottom(KeysetClient.appearance().keyboardPresentation().bottomRow().withOrder(order));
    }

    private void changeWidth(int direction) {
        saveBottom(KeysetClient.appearance().keyboardPresentation().bottomRow().withSpaceWidth(currentSpaceWidth() + direction));
    }

    private void saveBottom(KeyboardBottomRow value) {
        UiAppearance current = KeysetClient.appearance();
        save(current.withKeyboardPresentation(current.keyboardPresentation().withBottomRow(value)));
        if (error == null) init();
    }

    // ---------------------------------------------------------------- shared

    private void setPlatform(KeyboardPlatform platform) {
        UiAppearance current = KeysetClient.appearance();
        save(current.withKeyboardPresentation(current.keyboardPresentation().withPlatform(platform)));
        if (error == null) init();
    }

    private void save(UiAppearance appearance) {
        try {
            KeysetClient.setAppearance(appearance);
            error = null;
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
        }
    }

    private KeysetWidgets.FlatButton addButton(Component label, int x, int y, int width, int height,
                                                Button.OnPress action, KeysetWidgets.ButtonStyle style) {
        return addRenderableWidget(KeysetWidgets.flatButton(x, y, width, height, label, action, style));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.LABELS && GROUPS.size() > rowCapacity) {
            int next = Math.max(0, Math.min(GROUPS.size() - rowCapacity, scroll - (int) Math.signum(scrollY)));
            if (next != scroll) { scroll = next; refreshRows(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, left, top + 6, Math.max(1, right - left - 110),
            error == null ? UiTheme.text() : UiTheme.red());
        KeyboardPresentation presentation = KeysetClient.appearance().keyboardPresentation();
        var palette = UiTheme.currentPalette();
        if (tab == Tab.LABELS) {
            for (int slot = 0; slot < rowCapacity; slot++) {
                int index = scroll + slot;
                if (index >= GROUPS.size()) break;
                int y = contentTop + slot * ROW_STEP;
                graphics.fill(left, y, right, y + ROW_STEP - 2, (index % 2 == 0) ? UiTheme.row() : UiTheme.rowAlternate());
                List<String> group = GROUPS.get(index);
                String first = group.get(0);
                var state = new KeycapPainter.State(false, false, false, false, false, false);
                int keyX = left + 8, keyY = y + 3, keyH = ROW_STEP - 8;
                KeycapPainter.draw(graphics::fill, keyX, keyY, PREVIEW_WIDTH, keyH, palette, state);
                KeycapRenderer.drawKey(graphics, font, first, labels.getOrDefault(first, first.substring(P.length())),
                    KeycapPainter.faceBounds(keyX, keyY, PREVIEW_WIDTH, keyH),
                    KeycapPainter.labelColor(palette, state), .2f, presentation);
                KeysetWidgets.drawText(graphics, font, groupName(index),
                    left + PREVIEW_WIDTH + 18, y + (ROW_STEP - 2 - font.lineHeight) / 2, nameWidth, UiTheme.text());
            }
            if (GROUPS.size() > rowCapacity) {
                int track = rowCapacity * ROW_STEP - 2;
                int thumb = Math.max(14, track * rowCapacity / GROUPS.size());
                int thumbY = contentTop + (track - thumb) * scroll / Math.max(1, GROUPS.size() - rowCapacity);
                graphics.fill(right + 3, contentTop, right + 6, contentTop + track, 0x90000000);
                graphics.fill(right + 3, thumbY, right + 6, thumbY + thumb, UiTheme.muted());
            }
        } else {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.bottom_hint"), left,
                contentTop + 4, right - left, UiTheme.muted());
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.bottom_fit_hint"), left,
                contentTop + 24 + 44 + 18 + 30 + 30, right - left, UiTheme.muted());
        }
        if (error != null) KeysetWidgets.drawText(graphics, font, error, left, bottom - 10, right - left, UiTheme.red());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private final class KeyCellButton extends Button {
        private final String input;
        private final String label;
        private boolean selected;

        private KeyCellButton(int x, int y, int width, int height, String input, String label, OnPress onPress) {
            super(x, y, width, height, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.input = input;
            this.label = label;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            KeycapPainter.State state = new KeycapPainter.State(isHoveredOrFocused(), selected, false, false,
                isFocused(), false);
            KeycapPainter.draw(graphics::fill, getX(), getY(), getWidth(), getHeight(), UiTheme.currentPalette(), state);
            KeycapRenderer.drawKey(graphics, Minecraft.getInstance().font, input, label,
                KeycapPainter.faceBounds(getX(), getY(), getWidth(), getHeight()),
                KeycapPainter.labelColor(UiTheme.currentPalette(), state), .2f,
                KeysetClient.appearance().keyboardPresentation());
        }
    }
}
