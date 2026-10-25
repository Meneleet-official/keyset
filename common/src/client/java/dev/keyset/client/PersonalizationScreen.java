package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.profile.UiAppearance;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Controls the keyboard editor's size, input position, and color palette. */
public final class PersonalizationScreen extends Screen {
    private static final List<Preset> PRESETS = List.of(
            new Preset("keyset.appearance_preset_default", 0x94E4D3, 0x000000),
            new Preset("keyset.appearance_preset_ocean", 0x78BDF2, 0x101A2C),
            new Preset("keyset.appearance_preset_ember", 0xF2A66F, 0x24150E),
            new Preset("keyset.appearance_preset_paper", 0x25594C, 0xEEECE4));

    private final Screen parent;
    private Tab tab = Tab.LAYOUT;
    private String accentDraft;
    private String panelDraft;
    private EditBox accentBox;
    private EditBox panelBox;
    private Component error;
    private int panelX, panelY, panelWidth, panelHeight, contentY, footerY, innerX, innerWidth;
    private KeysetWidgets.FlatButton layoutTab, colorsTab, leftButton, rightButton, opacityValueButton;
    private final KeysetWidgets.FlatButton[] scaleButtons = new KeysetWidgets.FlatButton[3];
    private final KeysetWidgets.FlatButton[] rowHeightButtons = new KeysetWidgets.FlatButton[3];
    private final KeysetWidgets.FlatButton[] presetButtons = new KeysetWidgets.FlatButton[PRESETS.size()];

    public PersonalizationScreen(Screen parent) {
        super(Component.translatable("keyset.appearance_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KeysetClient.ensureLoaded();
        clearWidgets();
        UiAppearance appearance = KeysetClient.appearance();
        if (accentDraft == null) accentDraft = hex(appearance.accentColor());
        if (panelDraft == null) panelDraft = hex(appearance.panelColor());

        panelWidth = Math.max(1, Math.min(520, width - 16));
        panelHeight = Math.max(1, Math.min(360, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        innerX = panelX + 12;
        innerWidth = Math.max(1, panelWidth - 24);
        contentY = panelY + 58;
        footerY = panelY + panelHeight - 28;

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
        int footerWidth = Math.max(1, (innerWidth - footerGap) / 2);
        addButton(Component.translatable("keyset.appearance_reset"), innerX, footerY,
                footerWidth, 20, ignored -> resetAppearance());
        addButton(Component.translatable("keyset.done"), innerX + footerWidth + footerGap, footerY,
                innerWidth - footerWidth - footerGap, 20, ignored -> onClose());
    }

    private void initLayout(UiAppearance appearance) {
        int buttonY = contentY + 14;
        int gap = 4;
        int half = Math.max(1, (innerWidth - gap) / 2);
        leftButton = addButton(Component.translatable("keyset.appearance_input_left"), innerX,
                buttonY, half, 20, ignored -> setInputOnRight(false));
        rightButton = addButton(Component.translatable("keyset.appearance_input_right"), innerX + half + gap,
                buttonY, innerWidth - half - gap, 20, ignored -> setInputOnRight(true));
        leftButton.setSelected(!appearance.inputOnRight());
        rightButton.setSelected(appearance.inputOnRight());

        int buttonHeight = 20;
        int sizeY = contentY + 54;
        int itemGap = 4;
        int itemWidth = Math.max(1, (innerWidth - 2 * itemGap) / 3);
        int[] scales = {60, 80, 100};
        for (int i = 0; i < scales.length; i++) {
            int scale = scales[i];
            scaleButtons[i] = addButton(Component.translatable("keyset.appearance_percent", Integer.toString(scale)),
                    innerX + i * (itemWidth + itemGap), sizeY, i == 2 ? innerWidth - 2 * (itemWidth + itemGap) : itemWidth,
                    buttonHeight, ignored -> setKeyboardScale(scale), KeysetWidgets.ButtonStyle.TAB);
            scaleButtons[i].setSelected(scale == appearance.keyboardScale());
        }

        int rowY = contentY + 94;
        int[] heights = {18, 25, 32};
        for (int i = 0; i < heights.length; i++) {
            int rowHeight = heights[i];
            rowHeightButtons[i] = addButton(Component.translatable("keyset.appearance_pixels", Integer.toString(rowHeight)),
                    innerX + i * (itemWidth + itemGap), rowY, i == 2 ? innerWidth - 2 * (itemWidth + itemGap) : itemWidth,
                    buttonHeight, ignored -> setRowHeight(rowHeight), KeysetWidgets.ButtonStyle.TAB);
            rowHeightButtons[i].setSelected(rowHeight == appearance.rowHeight());
        }
    }

    private void initColors(UiAppearance appearance) {
        int presetY = contentY + 11;
        int gap = 3;
        int presetWidth = Math.max(1, (innerWidth - (PRESETS.size() - 1) * gap) / PRESETS.size());
        for (int i = 0; i < PRESETS.size(); i++) {
            Preset preset = PRESETS.get(i);
            presetButtons[i] = addButton(Component.translatable(preset.translationKey()),
                    innerX + i * (presetWidth + gap), presetY,
                    i == PRESETS.size() - 1 ? innerWidth - i * (presetWidth + gap) : presetWidth, 18,
                    ignored -> applyPreset(preset), KeysetWidgets.ButtonStyle.TAB);
            presetButtons[i].setSelected(preset.accent() == appearance.accentColor()
                    && preset.panel() == appearance.panelColor());
        }

        int fieldTop = contentY + 40;
        accentBox = createHexField(innerX, fieldTop, accentDraft, "keyset.appearance_accent_hint", true);
        panelBox = createHexField(innerX, fieldTop + 30, panelDraft, "keyset.appearance_panel_hint", false);

        int opacityY = contentY + 101;
        int controlWidth = Math.min(56, Math.max(34, innerWidth / 4));
        int valueWidth = Math.max(1, innerWidth - 2 * controlWidth - 8);
        addButton(Component.literal("-"), innerX, opacityY, controlWidth, 20,
                ignored -> adjustOpacity(-5));
        opacityValueButton = addButton(Component.translatable("keyset.appearance_opacity_percent",
                Integer.toString(appearance.panelOpacity())),
                innerX + controlWidth + 4, opacityY, valueWidth, 20, ignored -> {}, KeysetWidgets.ButtonStyle.QUIET);
        addButton(Component.literal("+"), innerX + controlWidth + valueWidth + 8, opacityY,
                controlWidth, 20, ignored -> adjustOpacity(5));
    }

    private EditBox createHexField(int x, int y, String value, String hint, boolean accentField) {
        EditBox box = new EditBox(font, x, y, innerWidth, 19, Component.translatable(hint));
        box.setMaxLength(7);
        box.setBordered(false);
        box.setTextColor(UiTheme.text());
        box.setHint(Component.translatable(hint));
        box.setValue(value);
        box.setResponder(next -> {
            if (accentField) accentDraft = next;
            else panelDraft = next;
            updateHex(accentField);
        });
        addRenderableWidget(box);
        return box;
    }

    private void updateHex(boolean accentField) {
        Integer color = parseHex(accentField ? accentDraft : panelDraft);
        if (color == null) {
            error = Component.translatable("keyset.appearance_invalid_hex");
            return;
        }
        UiAppearance current = KeysetClient.appearance();
        error = null;
        boolean saved;
        if (accentField) saved = update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                color, current.panelColor(), current.panelOpacity()));
        else saved = update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                current.accentColor(), color, current.panelOpacity()));
        if (saved) {
            accentBox.setTextColor(UiTheme.text());
            panelBox.setTextColor(UiTheme.text());
        }
    }

    private void applyPreset(Preset preset) {
        UiAppearance current = KeysetClient.appearance();
        if (update(appearance(current.keyboardScale(), current.rowHeight(), current.inputOnRight(),
                preset.accent(), preset.panel(), current.panelOpacity()))) {
            accentDraft = hex(preset.accent());
            panelDraft = hex(preset.panel());
        }
        init();
    }

    private void resetAppearance() {
        try {
            KeysetClient.resetAppearance();
            UiAppearance defaults = KeysetClient.appearance();
            accentDraft = hex(defaults.accentColor());
            panelDraft = hex(defaults.panelColor());
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

    private void setRowHeight(int rowHeight) {
        UiAppearance current = KeysetClient.appearance();
        if (update(appearance(current.keyboardScale(), rowHeight, current.inputOnRight(),
                current.accentColor(), current.panelColor(), current.panelOpacity()))) {
            for (int i = 0; i < rowHeightButtons.length; i++) rowHeightButtons[i].setSelected(new int[]{18, 25, 32}[i] == rowHeight);
        }
    }

    private boolean update(UiAppearance next) {
        try {
            KeysetClient.setAppearance(next);
            error = null;
            return true;
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
            return false;
        }
    }

    private UiAppearance appearance(int scale, int rowHeight, boolean inputOnRight,
                                    int accent, int panel, int opacity) {
        return new UiAppearance(scale, rowHeight, inputOnRight, accent, panel, opacity);
    }

    private void selectTab(Tab next) {
        tab = next;
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, innerX, panelY + 11, innerWidth, UiTheme.text());
        if (tab == Tab.LAYOUT) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_input_label"),
                    innerX, contentY, innerWidth, UiTheme.muted());
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_scale_label"),
                    innerX, contentY + 40, innerWidth, UiTheme.muted());
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_row_height_label"),
                    innerX, contentY + 80, innerWidth, UiTheme.muted());
        } else {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_presets_label"),
                    innerX, contentY, innerWidth, UiTheme.muted());
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_accent_label"),
                    innerX, contentY + 29, innerWidth, UiTheme.muted());
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_panel_label"),
                    innerX, contentY + 59, innerWidth, UiTheme.muted());
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_opacity_label"),
                    innerX, contentY + 89, innerWidth, UiTheme.muted());
            if (accentBox != null) graphics.fill(accentBox.getX(), accentBox.getY(), accentBox.getX() + accentBox.getWidth(),
                    accentBox.getY() + accentBox.getHeight(), UiTheme.control());
            if (panelBox != null) graphics.fill(panelBox.getX(), panelBox.getY(), panelBox.getX() + panelBox.getWidth(),
                    panelBox.getY() + panelBox.getHeight(), UiTheme.control());
        }
        if (error != null) {
            int errorY = footerY - 12;
            KeysetWidgets.drawText(graphics, font, error, innerX, errorY, innerWidth, UiTheme.red());
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private enum Tab { LAYOUT, COLORS }
    private record Preset(String translationKey, int accent, int panel) {}
}
