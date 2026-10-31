package dev.keyset.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.PersonalizationLayout;
import dev.keyset.profile.AppearancePreset;
import dev.keyset.profile.KeyboardFormFactor;
import dev.keyset.profile.UiAppearance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Controls the keyboard editor's size, input position, and color palette. */
public final class PersonalizationScreen extends Screen {
    private static final int COLOR_ROW_HEIGHT = 26;
    private static final int COLOR_ROW_COUNT = HexColorField.values().length + 1;
    private static final String[] LAYOUT_LABELS = {"keyset.appearance_input_label", "keyset.appearance_keyboard_scale_label",
            "keyset.appearance_mouse_scale_label", "keyset.appearance_row_height_label",
            "keyset.appearance_keyboard_format", "keyset.appearance_legends_label",
            "keyset.appearance_bottom_row_label"};

    private final Screen parent;
    private Tab tab = Tab.LAYOUT;
    private String accentDraft;
    private String panelDraft;
    private String mouseBodyDraft;
    private String boundKeyDraft;
    private String conflictKeyDraft;
    private String unboundKeyDraft;
    private final EnumMap<HexColorField, EditBox> colorBoxes = new EnumMap<>(HexColorField.class);
    private final EnumMap<HexColorField, ColorSwatchButton> colorSwatches = new EnumMap<>(HexColorField.class);
    private Component error;
    private PersonalizationLayout layout;
    private final AppearancePreviewRenderer preview = new AppearancePreviewRenderer();
    private final List<LayoutControl> layoutControls = new ArrayList<>();
    private int layoutScroll, layoutCapacity, layoutControlX;
    private int panelX, panelY, panelWidth, panelHeight, contentY, footerY, innerX, innerWidth;
    private int colorListTop, colorListBottom, colorCapacity, colorScroll, colorInputX, colorInputWidth, opacityInputX;
    private KeysetWidgets.FlatButton layoutTab, colorsTab, leftButton, rightButton,
            opacityMinusButton, opacityValueButton, opacityPlusButton, themesButton;
    private final KeysetWidgets.FlatButton[] scaleButtons = new KeysetWidgets.FlatButton[3];
    private final KeysetWidgets.FlatButton[] mouseScaleButtons = new KeysetWidgets.FlatButton[3];
    private final KeysetWidgets.FlatButton[] rowHeightButtons = new KeysetWidgets.FlatButton[3];
    private final KeysetWidgets.FlatButton[] formButtons = new KeysetWidgets.FlatButton[KeyboardFormFactor.values().length];

    public PersonalizationScreen(Screen parent) {
        super(Component.translatable("keyset.appearance_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KeycapRenderer.resetFontMetrics();
        KeysetClient.ensureLoaded();
        clearWidgets();
        preview.clearHitTest();
        colorBoxes.clear();
        colorSwatches.clear();
        layoutControls.clear();
        themesButton = null;
        opacityMinusButton = opacityValueButton = opacityPlusButton = null;
        UiAppearance appearance = KeysetClient.appearance();
        if (accentDraft == null) accentDraft = hex(appearance.accentColor());
        if (panelDraft == null) panelDraft = hex(appearance.panelColor());
        if (mouseBodyDraft == null) mouseBodyDraft = hex(appearance.mouseBodyColor());
        if (boundKeyDraft == null) boundKeyDraft = hex(appearance.boundKeyColor());
        if (conflictKeyDraft == null) conflictKeyDraft = hex(appearance.conflictKeyColor());
        if (unboundKeyDraft == null) unboundKeyDraft = hex(appearance.unboundKeyColor());

        layout = PersonalizationLayout.forScreen(width, height);
        panelWidth = layout.width();
        panelHeight = layout.height();
        panelX = layout.x();
        panelY = layout.y();
        innerX = layout.innerX();
        innerWidth = layout.settingsWidth();
        contentY = layout.contentY();
        footerY = layout.footerY();

        int tabGap = 4;
        int tabWidth = Math.max(1, (innerWidth - tabGap) / 2);
        layoutTab = addButton(Component.translatable("keyset.appearance_layout_tab"),
                innerX, panelY + 34, tabWidth, 18, ignored -> selectTab(Tab.LAYOUT), KeysetWidgets.ButtonStyle.TAB);
        colorsTab = addButton(Component.translatable("keyset.appearance_colors_tab"),
                innerX + tabWidth + tabGap, panelY + 34, innerWidth - tabWidth - tabGap, 18,
                ignored -> selectTab(Tab.COLORS), KeysetWidgets.ButtonStyle.TAB);
        layoutTab.setSelected(tab == Tab.LAYOUT);
        colorsTab.setSelected(tab == Tab.COLORS);

        if (tab == Tab.LAYOUT) initLayout(appearance);
        else initColors(appearance);

        int footerGap = 4;
        int footerWidth = Math.max(1, (layout.totalInnerWidth() - footerGap) / 2);
        addButton(Component.translatable("keyset.appearance_reset"), innerX, footerY,
                footerWidth, 20, ignored -> resetAppearance());
        addButton(Component.translatable("keyset.done"), innerX + footerWidth + footerGap, footerY,
                layout.totalInnerWidth() - footerWidth - footerGap, 20, ignored -> onClose());
    }

    private void initLayout(UiAppearance appearance) {
        int gap = 4;
        int controlsWidth = Math.min(180, Math.max(150, innerWidth / 2));
        layoutControlX = innerX + innerWidth - controlsWidth - 7;
        int half = Math.max(1, (controlsWidth - gap) / 2);
        leftButton = layoutButton(0, Component.translatable("keyset.appearance_input_left"), layoutControlX,
                half, ignored -> setInputOnRight(false));
        rightButton = layoutButton(0, Component.translatable("keyset.appearance_input_right"), layoutControlX + half + gap,
                controlsWidth - half - gap, ignored -> setInputOnRight(true));
        leftButton.setSelected(!appearance.inputOnRight());
        rightButton.setSelected(appearance.inputOnRight());

        int itemGap = 4;
        int itemWidth = Math.max(1, (controlsWidth - 2 * itemGap) / 3);
        int[] scales = {60, 80, 100};
        for (int i = 0; i < scales.length; i++) {
            int scale = scales[i];
            scaleButtons[i] = layoutButton(1, Component.translatable("keyset.appearance_percent", Integer.toString(scale)),
                    layoutControlX + i * (itemWidth + itemGap),
                    i == 2 ? controlsWidth - 2 * (itemWidth + itemGap) : itemWidth, ignored -> setKeyboardScale(scale));
            scaleButtons[i].setSelected(scale == appearance.keyboardScale());

            mouseScaleButtons[i] = layoutButton(2,
                    Component.translatable("keyset.appearance_percent", Integer.toString(scale)),
                    layoutControlX + i * (itemWidth + itemGap),
                    i == 2 ? controlsWidth - 2 * (itemWidth + itemGap) : itemWidth,
                    ignored -> setMouseScale(scale));
            mouseScaleButtons[i].setSelected(scale == appearance.mouseScale());
        }

        int[] heights = {18, 25, 32};
        for (int i = 0; i < heights.length; i++) {
            int rowHeight = heights[i];
            rowHeightButtons[i] = layoutButton(3, Component.translatable("keyset.appearance_pixels", Integer.toString(rowHeight)),
                    layoutControlX + i * (itemWidth + itemGap),
                    i == 2 ? controlsWidth - 2 * (itemWidth + itemGap) : itemWidth, ignored -> setRowHeight(rowHeight));
            rowHeightButtons[i].setSelected(rowHeight == appearance.rowHeight());
        }
        int formWidth = (controlsWidth - 9) / 4;
        for (int i = 0; i < formButtons.length; i++) {
            KeyboardFormFactor form = KeyboardFormFactor.values()[i];
            formButtons[i] = layoutButton(4, Component.literal(form.label()), layoutControlX + i * (formWidth + 3),
                    i == 3 ? controlsWidth - i * (formWidth + 3) : formWidth, ignored -> setFormFactor(form));
            formButtons[i].setSelected(form == appearance.keyboardFormFactor());
        }
        layoutButton(5, Component.translatable("keyset.appearance_legends_open"), layoutControlX,
                controlsWidth, ignored -> minecraft.setScreenAndShow(new KeyboardStyleScreen(this)));
        layoutButton(6, Component.translatable("keyset.appearance_bottom_row_open"), layoutControlX,
                controlsWidth, ignored -> minecraft.setScreenAndShow(new BottomRowScreen(this)));
        layoutCapacity = Math.max(1, Math.min(LAYOUT_LABELS.length,
                (layout.settingsBottom() - contentY) / COLOR_ROW_HEIGHT));
        layoutScroll = Math.max(0, Math.min(layoutScroll, LAYOUT_LABELS.length - layoutCapacity));
        refreshLayoutViewport();
    }

    private KeysetWidgets.FlatButton layoutButton(int row, Component label, int x, int width, Button.OnPress action) {
        var button = addButton(label, x, contentY, width, 20, action, KeysetWidgets.ButtonStyle.TAB);
        button.setTooltip(Tooltip.create(Component.translatable(LAYOUT_LABELS[row]).append(": ").append(label)));
        layoutControls.add(new LayoutControl(button, row));
        return button;
    }

    private void refreshLayoutViewport() {
        for (var control : layoutControls) {
            var button = control.button();
            button.visible = control.row() >= layoutScroll && control.row() < layoutScroll + layoutCapacity;
            button.setY(contentY + (control.row() - layoutScroll) * COLOR_ROW_HEIGHT + 2);
            if (!button.visible && button.isFocused()) setFocused(null);
        }
    }

    private void initColors(UiAppearance appearance) {
        themesButton = addButton(Component.empty(), innerX, contentY, innerWidth, 20,
                ignored -> minecraft.setScreenAndShow(new ThemeLibraryScreen(this, this::syncColorDrafts)));
        refreshPresetSelection();

        colorListTop = contentY + 28;
        colorListBottom = layout.settingsBottom();
        colorCapacity = Math.max(1, Math.min(COLOR_ROW_COUNT,
                (colorListBottom - colorListTop) / COLOR_ROW_HEIGHT));
        colorScroll = Math.max(0, Math.min(colorScroll, COLOR_ROW_COUNT - colorCapacity));
        colorInputWidth = Math.min(88, Math.max(68, innerWidth / 4));
        colorInputX = innerX + innerWidth - colorInputWidth - 7;
        createHexField(accentDraft, HexColorField.ACCENT);
        createHexField(panelDraft, HexColorField.PANEL);
        createHexField(mouseBodyDraft, HexColorField.MOUSE_BODY);
        createHexField(boundKeyDraft, HexColorField.BOUND_KEY);
        createHexField(conflictKeyDraft, HexColorField.CONFLICT_KEY);
        createHexField(unboundKeyDraft, HexColorField.UNBOUND_KEY);

        int controlWidth = 20;
        int opacityWidth = Math.max(120, colorInputWidth);
        opacityInputX = innerX + innerWidth - 7 - opacityWidth;
        int valueWidth = opacityWidth - 2 * controlWidth - 4;
        opacityMinusButton = addButton(Component.literal("-"), opacityInputX, colorListTop, controlWidth, 20,
                ignored -> adjustOpacity(-5));
        opacityValueButton = addButton(Component.translatable("keyset.appearance_opacity_percent",
                Integer.toString(appearance.panelOpacity())),
                opacityInputX + controlWidth + 2, colorListTop, valueWidth, 20,
                ignored -> {}, KeysetWidgets.ButtonStyle.QUIET);
        opacityPlusButton = addButton(Component.literal("+"), opacityInputX + opacityWidth - controlWidth, colorListTop,
                controlWidth, 20, ignored -> adjustOpacity(5));
        updateColorViewport();
    }

    private void refreshPresetSelection() {
        if (themesButton == null) return;
        UiAppearance current = KeysetClient.appearance();
        Component name = Component.translatable("keyset.themes_custom");
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (preset.matches(current)) { name = Component.translatable(preset.translationKey()); break; }
        }
        String resourceTheme = KeysetClient.resourceThemeId();
        if (resourceTheme != null) {
            var source = ResourcePackThemes.snapshot().entries().stream()
                .filter(entry -> entry.resourceId().equals(resourceTheme)).findFirst();
            if (source.isPresent()) name = Component.literal(source.get().theme().name())
                .append(Component.translatable("keyset.themes_resource_badge"));
        }
        themesButton.setMessage(Component.translatable("keyset.themes_title").append(" · ").append(name).append(" ▾"));
    }

    private void updateColorViewport() {
        for (var entry : colorBoxes.entrySet()) {
            int row = entry.getKey().ordinal();
            EditBox box = entry.getValue();
            box.visible = isColorRowVisible(row);
            if (!box.visible && box.isFocused()) {
                box.setFocused(false);
                if (getFocused() == box) setFocused(null);
            }
            box.setY(colorRowY(row) + 7);
            ColorSwatchButton swatch = colorSwatches.get(entry.getKey());
            swatch.visible = box.visible;
            swatch.setY(colorRowY(row) + 2);
            if (!swatch.visible && swatch.isFocused()) {
                swatch.setFocused(false);
                if (getFocused() == swatch) setFocused(null);
            }
        }
        int opacityRow = COLOR_ROW_COUNT - 1;
        for (var button : List.of(opacityMinusButton, opacityValueButton, opacityPlusButton)) {
            button.visible = isColorRowVisible(opacityRow);
            button.setY(colorRowY(opacityRow) + 2);
            if (!button.visible && button.isFocused()) {
                button.setFocused(false);
                if (getFocused() == button) setFocused(null);
            }
        }
    }

    private boolean isColorRowVisible(int row) {
        return row >= colorScroll && row < colorScroll + colorCapacity;
    }

    private int colorRowY(int row) {
        return colorListTop + (row - colorScroll) * COLOR_ROW_HEIGHT;
    }

    private void createHexField(String value, HexColorField colorField) {
        EditBox box = new EditBox(font, colorInputX + 15, colorListTop + 7,
                Math.max(1, colorInputWidth - 21), 12, Component.translatable(colorField.labelKey));
        box.setMaxLength(7);
        box.setBordered(false);
        box.setTextColor(UiTheme.text());
        box.setHint(Component.literal("RRGGBB"));
        box.setTooltip(Tooltip.create(Component.translatable(colorField.labelKey)
                .append("\n").append(Component.translatable(colorField.hintKey))));
        box.setValue(value);
        box.setResponder(next -> {
            if (next.startsWith("#") && next.length() == 7) {
                box.setValue(next.substring(1));
                return;
            }
            switch (colorField) {
                case ACCENT -> accentDraft = next;
                case PANEL -> panelDraft = next;
                case MOUSE_BODY -> mouseBodyDraft = next;
                case BOUND_KEY -> boundKeyDraft = next;
                case CONFLICT_KEY -> conflictKeyDraft = next;
                case UNBOUND_KEY -> unboundKeyDraft = next;
            }
            updateHex(colorField);
        });
        addRenderableWidget(box);
        colorBoxes.put(colorField, box);
        colorSwatches.put(colorField, addRenderableWidget(new ColorSwatchButton(colorField)));
    }

    private void openColorPicker(HexColorField field) {
        Integer draft = parseHex(colorBoxes.get(field).getValue());
        int initialColor = draft == null ? colorValue(field, KeysetClient.appearance()) : draft;
        minecraft.setScreenAndShow(new ColorPickerScreen(this, Component.translatable(field.labelKey), initialColor,
                color -> {
                    String value = hex(color);
                    switch (field) {
                        case ACCENT -> accentDraft = value;
                        case PANEL -> panelDraft = value;
                        case MOUSE_BODY -> mouseBodyDraft = value;
                        case BOUND_KEY -> boundKeyDraft = value;
                        case CONFLICT_KEY -> conflictKeyDraft = value;
                        case UNBOUND_KEY -> unboundKeyDraft = value;
                    }
                    updateHex(field);
                }, color -> previewColor(field, color)).withMousePreview(field == HexColorField.MOUSE_BODY));
    }

    private UiAppearance previewColor(HexColorField field, int color) {
        UiAppearance current = KeysetClient.appearance();
        return new UiAppearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                field == HexColorField.ACCENT ? color : current.accentColor(),
                field == HexColorField.PANEL ? color : current.panelColor(), current.panelOpacity(),
                field == HexColorField.BOUND_KEY ? color : current.boundKeyColor(),
                field == HexColorField.CONFLICT_KEY ? color : current.conflictKeyColor(),
                field == HexColorField.UNBOUND_KEY ? color : current.unboundKeyColor(), current.keyboardFormFactor(),
                current.keyboardPresentation(),
                field == HexColorField.MOUSE_BODY ? color : current.mouseBodyColor(), current.mouseScale());
    }

    private static int colorValue(HexColorField field, UiAppearance appearance) {
        return switch (field) {
            case ACCENT -> appearance.accentColor();
            case PANEL -> appearance.panelColor();
            case MOUSE_BODY -> appearance.mouseBodyColor();
            case BOUND_KEY -> appearance.boundKeyColor();
            case CONFLICT_KEY -> appearance.conflictKeyColor();
            case UNBOUND_KEY -> appearance.unboundKeyColor();
        };
    }

    private void updateHex(HexColorField colorField) {
        String draft = switch (colorField) {
            case ACCENT -> accentDraft;
            case PANEL -> panelDraft;
            case MOUSE_BODY -> mouseBodyDraft;
            case BOUND_KEY -> boundKeyDraft;
            case CONFLICT_KEY -> conflictKeyDraft;
            case UNBOUND_KEY -> unboundKeyDraft;
        };
        Integer color = parseHex(draft);
        if (color == null) {
            error = Component.translatable("keyset.appearance_invalid_hex");
            return;
        }
        UiAppearance current = KeysetClient.appearance();
        error = null;
        boolean saved = switch (colorField) {
            case ACCENT -> update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                    color, current.panelColor(), current.panelOpacity()));
            case PANEL -> update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                    current.accentColor(), color, current.panelOpacity()));
            case MOUSE_BODY -> update(current.withMouseBodyColor(color));
            case BOUND_KEY -> update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                    current.accentColor(), current.panelColor(), current.panelOpacity(), color,
                    current.conflictKeyColor(), current.unboundKeyColor()));
            case CONFLICT_KEY -> update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                    current.accentColor(), current.panelColor(), current.panelOpacity(), current.boundKeyColor(),
                    color, current.unboundKeyColor()));
            case UNBOUND_KEY -> update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                    current.accentColor(), current.panelColor(), current.panelOpacity(), current.boundKeyColor(),
                    current.conflictKeyColor(), color));
        };
        if (saved) {
            refreshPresetSelection();
        }
    }

    private void syncColorDrafts() {
        UiAppearance appearance = KeysetClient.appearance();
        accentDraft = hex(appearance.accentColor());
        panelDraft = hex(appearance.panelColor());
        mouseBodyDraft = hex(appearance.mouseBodyColor());
        boundKeyDraft = hex(appearance.boundKeyColor());
        conflictKeyDraft = hex(appearance.conflictKeyColor());
        unboundKeyDraft = hex(appearance.unboundKeyColor());
    }

    private void resetAppearance() {
        try {
            KeysetClient.resetAppearance();
            syncColorDrafts();
            error = null;
            init();
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
        }
    }

    private void adjustOpacity(int change) {
        UiAppearance current = KeysetClient.appearance();
        int next = Math.max(15, Math.min(90, current.panelOpacity() + change));
        if (update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                current.accentColor(), current.panelColor(), next)) && opacityValueButton != null) {
            opacityValueButton.setMessage(Component.translatable("keyset.appearance_opacity_percent",
                    Integer.toString(KeysetClient.appearance().panelOpacity())));
        }
    }

    private void setInputOnRight(boolean inputOnRight) {
        UiAppearance current = KeysetClient.appearance();
        if (update(appearance(current.keyboardScale(), current.rowHeight(), inputOnRight,
                current.accentColor(), current.panelColor(), current.panelOpacity()))) {
            leftButton.setSelected(!inputOnRight);
            rightButton.setSelected(inputOnRight);
        }
    }

    private void setKeyboardScale(int scale) {
        UiAppearance current = KeysetClient.appearance();
        if (update(appearance(scale, current.rowHeight(), current.inputOnRight(),
                current.accentColor(), current.panelColor(), current.panelOpacity()))) {
            for (int i = 0; i < scaleButtons.length; i++) scaleButtons[i].setSelected(new int[]{60, 80, 100}[i] == scale);
        }
    }

    private void setMouseScale(int scale) {
        if (update(KeysetClient.appearance().withMouseScale(scale))) {
            for (int i = 0; i < mouseScaleButtons.length; i++) {
                mouseScaleButtons[i].setSelected(new int[]{60, 80, 100}[i] == scale);
            }
        }
    }

    private void setRowHeight(int rowHeight) {
        UiAppearance current = KeysetClient.appearance();
        if (update(appearance(current.keyboardScale(), rowHeight, current.inputOnRight(),
                current.accentColor(), current.panelColor(), current.panelOpacity()))) {
            for (int i = 0; i < rowHeightButtons.length; i++) rowHeightButtons[i].setSelected(new int[]{18, 25, 32}[i] == rowHeight);
        }
    }

    private void setFormFactor(KeyboardFormFactor form) {
        if (update(KeysetClient.appearance().withKeyboardFormFactor(form))) {
            for (int i = 0; i < formButtons.length; i++) {
                formButtons[i].setSelected(KeyboardFormFactor.values()[i] == form);
            }
        }
    }

    private boolean update(UiAppearance next) {
        try {
            KeysetClient.setAppearance(next);
            colorBoxes.values().forEach(box -> box.setTextColor(UiTheme.text()));
            error = null;
            return true;
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
            return false;
        }
    }

    private UiAppearance appearance(int scale, int rowHeight, boolean inputOnRight,
                                    int accent, int panel, int opacity) {
        UiAppearance current = KeysetClient.appearance();
        return appearance(scale, rowHeight, inputOnRight, accent, panel, opacity,
                current.boundKeyColor(), current.conflictKeyColor(), current.unboundKeyColor());
    }

    private UiAppearance appearance(int scale, int rowHeight, boolean inputOnRight,
                                    int accent, int panel, int opacity,
                                    int boundKeyColor, int conflictKeyColor, int unboundKeyColor) {
        return new UiAppearance(scale, rowHeight, inputOnRight, accent, panel, opacity,
                boundKeyColor, conflictKeyColor, unboundKeyColor, KeysetClient.appearance().keyboardFormFactor(),
                KeysetClient.appearance().keyboardPresentation(), KeysetClient.appearance().mouseBodyColor(),
                KeysetClient.appearance().mouseScale());
    }

    private void selectTab(Tab next) {
        if (tab == next) return;
        tab = next;
        colorScroll = 0;
        init();
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0xFFFFFF);
    }

    private static Integer parseHex(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        if (!normalized.matches("[0-9a-fA-F]{6}")) return null;
        try { return Integer.parseInt(normalized, 16); }
        catch (NumberFormatException ignored) { return null; }
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
        if (mouseX < innerX || mouseX >= innerX + innerWidth
                || mouseY < contentY || mouseY >= layout.settingsBottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollSettings(-(int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tab == Tab.COLORS && colorCapacity < COLOR_ROW_COUNT
                || tab == Tab.LAYOUT && layoutCapacity < LAYOUT_LABELS.length) {
            if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
                int capacity = tab == Tab.COLORS ? colorCapacity : layoutCapacity;
                scrollSettings(event.key() == InputConstants.KEY_PAGEDOWN ? capacity : -capacity);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            String input = preview.keyAt(event.x(), event.y());
            if (input != null) {
                minecraft.setScreenAndShow(new KeyboardStyleScreen(this, input));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void scrollSettings(int change) {
        if (tab == Tab.LAYOUT) {
            int next = Math.max(0, Math.min(layoutScroll + change, LAYOUT_LABELS.length - layoutCapacity));
            if (next != layoutScroll) { layoutScroll = next; refreshLayoutViewport(); }
            return;
        }
        int next = Math.max(0, Math.min(colorScroll + change, COLOR_ROW_COUNT - colorCapacity));
        if (next != colorScroll) {
            colorScroll = next;
            updateColorViewport();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, innerX, panelY + 11, layout.totalInnerWidth(), UiTheme.text());
        if (tab == Tab.LAYOUT) {
            for (int row = layoutScroll; row < layoutScroll + layoutCapacity; row++) {
                int y = contentY + (row - layoutScroll) * COLOR_ROW_HEIGHT;
                graphics.fill(innerX, y, innerX + innerWidth - 7, y + COLOR_ROW_HEIGHT - 2,
                        row % 2 == 0 ? UiTheme.row() : UiTheme.rowAlternate());
                KeysetWidgets.drawText(graphics, font, Component.translatable(LAYOUT_LABELS[row]), innerX + 5,
                        y + 8, Math.max(1, layoutControlX - innerX - 12), UiTheme.text());
            }
            drawScrollBar(graphics, contentY, LAYOUT_LABELS.length, layoutCapacity, layoutScroll);
        } else {
            drawColors(graphics);
        }
        var area = layout.preview();
        preview.draw(graphics, font, area.x(), area.y(), area.width(), area.height(), KeysetClient.appearance(),
                KeyboardLayouts.forLanguage(minecraft.options.languageCode));
        if (error != null) {
            int errorY = footerY - 12;
            KeysetWidgets.drawText(graphics, font, error, innerX, errorY, layout.totalInnerWidth(), UiTheme.red());
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawColors(GuiGraphicsExtractor graphics) {
        for (HexColorField field : HexColorField.values()) {
            int row = field.ordinal();
            if (!isColorRowVisible(row)) continue;
            int y = colorRowY(row);
            graphics.fill(innerX, y, innerX + innerWidth - 7, y + COLOR_ROW_HEIGHT - 2,
                    row % 2 == 0 ? UiTheme.row() : UiTheme.rowAlternate());
            int sampleX = colorInputX - 28;
            KeysetWidgets.drawText(graphics, font, Component.translatable(field.labelKey),
                    innerX + 5, y + 8, Math.max(1, sampleX - innerX - 12), UiTheme.text());
            EditBox box = colorBoxes.get(field);
            graphics.fill(colorInputX, y + 2, colorInputX + colorInputWidth, y + 22,
                    UiTheme.widgetSurface(UiTheme.control()));
            int outline = parseHex(box.getValue()) == null ? UiTheme.red()
                    : box.isFocused() ? UiTheme.accent() : UiTheme.border();
            graphics.outline(colorInputX, y + 2, colorInputWidth, 20, outline);
            graphics.text(font, "#", colorInputX + 5, y + 7, UiTheme.muted(), false);
        }
        int opacityRow = COLOR_ROW_COUNT - 1;
        if (isColorRowVisible(opacityRow)) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_opacity_label"),
                    innerX + 5, colorRowY(opacityRow) + 8,
                    Math.max(1, opacityInputX - innerX - 12), UiTheme.text());
        }
        drawScrollBar(graphics, colorListTop, COLOR_ROW_COUNT, colorCapacity, colorScroll);
    }

    private void drawScrollBar(GuiGraphicsExtractor graphics, int top, int count, int capacity, int scroll) {
        if (capacity >= count) return;
        int trackHeight = capacity * COLOR_ROW_HEIGHT - 2;
        int thumbHeight = Math.max(12, trackHeight * capacity / count);
        int thumbY = top + (trackHeight - thumbHeight) * scroll / (count - capacity);
        graphics.fill(innerX + innerWidth - 3, top, innerX + innerWidth, top + trackHeight, UiTheme.row());
        graphics.fill(innerX + innerWidth - 3, thumbY, innerX + innerWidth, thumbY + thumbHeight, UiTheme.muted());
    }

    private void drawColorSample(GuiGraphicsExtractor graphics, HexColorField field,
                                  UiAppearance appearance, int x, int y) {
        if (field == HexColorField.ACCENT || field == HexColorField.PANEL) {
            int color = field == HexColorField.ACCENT ? appearance.accentColor() : appearance.panelColor();
            graphics.fill(x, y, x + 22, y + 20, 0xFF000000 | color);
            graphics.outline(x, y, 22, 20, UiTheme.border());
            return;
        }
        if (field == HexColorField.MOUSE_BODY) {
            int body = 0xFF000000 | appearance.mouseBodyColor();
            graphics.fill(x + 3, y + 1, x + 19, y + 19, body);
            graphics.fill(x + 1, y + 5, x + 21, y + 16, body);
            graphics.outline(x + 1, y + 1, 20, 19, UiTheme.border());
            graphics.fill(x + 10, y + 3, x + 12, y + 10, UiTheme.border());
            graphics.fill(x + 9, y + 6, x + 13, y + 9, body);
            return;
        }
        var colors = UiTheme.keyColors(field == HexColorField.BOUND_KEY,
                field == HexColorField.CONFLICT_KEY, field == HexColorField.UNBOUND_KEY);
        graphics.fill(x + 1, y + 2, x + 22, y + 21, UiTheme.keyShadow());
        graphics.fill(x, y, x + 22, y + 19, colors.edge());
        graphics.fill(x + 1, y + 1, x + 21, y + 18, colors.face());
        String label = field == HexColorField.BOUND_KEY ? "A" : field == HexColorField.CONFLICT_KEY ? "!" : "-";
        graphics.text(font, label, x + (22 - font.width(label)) / 2, y + 5, colors.text(), false);
    }

    @Override
    public void removed() {
        preview.close();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private final class ColorSwatchButton extends Button {
        private final HexColorField field;

        private ColorSwatchButton(HexColorField field) {
            super(colorInputX - 28, colorListTop + 2, 22, 21, Component.translatable(field.labelKey),
                    ignored -> openColorPicker(field), DEFAULT_NARRATION);
            this.field = field;
            setTooltip(Tooltip.create(Component.translatable(field.labelKey)));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawColorSample(graphics, field, KeysetClient.appearance(), getX(), getY());
            if (isHoveredOrFocused()) graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2,
                    UiTheme.accent());
        }
    }

    private enum Tab { LAYOUT, COLORS }
    private record LayoutControl(KeysetWidgets.FlatButton button, int row) {}
    private enum HexColorField {
        ACCENT("keyset.appearance_accent_label", "keyset.appearance_accent_hint"),
        PANEL("keyset.appearance_panel_label", "keyset.appearance_panel_hint"),
        MOUSE_BODY("keyset.appearance_mouse_body_label", "keyset.appearance_mouse_body_hint"),
        BOUND_KEY("keyset.appearance_bound_color_label", "keyset.appearance_bound_color_hint"),
        CONFLICT_KEY("keyset.appearance_conflict_color_label", "keyset.appearance_conflict_color_hint"),
        UNBOUND_KEY("keyset.appearance_free_color_label", "keyset.appearance_free_color_hint");

        private final String labelKey;
        private final String hintKey;

        HexColorField(String labelKey, String hintKey) {
            this.labelKey = labelKey;
            this.hintKey = hintKey;
        }
    }
}
