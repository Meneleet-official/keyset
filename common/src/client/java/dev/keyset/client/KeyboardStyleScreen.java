package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.editor.KeyboardDiagramLayout;
import dev.keyset.editor.KeyboardKeyNames;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.profile.KeyLegendMode;
import dev.keyset.profile.KeyboardPlatform;
import dev.keyset.profile.KeyboardPresentation;
import dev.keyset.profile.UiAppearance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Clickable, per-key legend editor opened from personalization. */
public final class KeyboardStyleScreen extends Screen {
    private static final String FN_INPUT = "key.keyboard.fn";
    private static final int PANEL_MARGIN = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int PLATFORM_HEIGHT = 18;
    private static final int SELECTED_HEIGHT = 16;
    private static final int MODE_HEIGHT = 20;

    private final Screen parent;
    private String selectedInput;
    private int panelX, panelY, panelWidth, panelHeight, left, right, top, bottom;
    private int platformY, keyboardX, keyboardY, keyboardWidth, keyboardHeight, selectedY, modesY;
    private KeyboardDiagramLayout keyboardLayout;
    private final List<KeyCellButton> keyButtons = new ArrayList<>();
    private final KeysetWidgets.FlatButton[] modeButtons = new KeysetWidgets.FlatButton[KeyLegendMode.values().length];
    private KeysetWidgets.FlatButton resetAllButton, resetKeyButton;
    private PreviewKeyButton selectedPreview;
    private Component error;

    public KeyboardStyleScreen(Screen parent) {
        this(parent, null);
    }

    /** Opens directly on a key chosen in the personalization preview. */
    public KeyboardStyleScreen(Screen parent, String selectedInput) {
        super(Component.translatable("keyset.legend_title"));
        this.parent = parent;
        this.selectedInput = selectedInput;
    }

    @Override
    protected void init() {
        KeysetClient.ensureLoaded();
        clearWidgets();
        keyButtons.clear();
        panelWidth = Math.max(1, Math.min(1200, width - PANEL_MARGIN * 2));
        panelHeight = Math.max(1, Math.min(720, height - PANEL_MARGIN * 2));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        left = panelX + PANEL_MARGIN;
        right = panelX + panelWidth - PANEL_MARGIN;
        top = panelY + PANEL_MARGIN;
        bottom = panelY + panelHeight - PANEL_MARGIN;

        addButton(Component.translatable("keyset.done"), right - 58, top,
            58, HEADER_HEIGHT, ignored -> onClose(), KeysetWidgets.ButtonStyle.PRIMARY);
        resetAllButton = addButton(Component.literal("↺"), right - 58 - 4 - 28, top,
            28, HEADER_HEIGHT, ignored -> resetAll(), KeysetWidgets.ButtonStyle.ACTION);
        resetAllButton.setTooltip(Tooltip.create(Component.translatable("keyset.legend_reset_all")));

        platformY = top + HEADER_HEIGHT + 2;
        int platformGap = 3;
        int platformWidth = Math.max(1, (right - left - platformGap * (KeyboardPlatform.values().length - 1)) / KeyboardPlatform.values().length);
        KeyboardPlatform currentPlatform = KeysetClient.appearance().keyboardPresentation().platform();
        for (KeyboardPlatform platform : KeyboardPlatform.values()) {
            int index = platform.ordinal();
            int x = left + index * (platformWidth + platformGap);
            int width = index == KeyboardPlatform.values().length - 1 ? right - x : platformWidth;
            var button = addButton(Component.translatable(platform.translationKey()), x, platformY,
                width, PLATFORM_HEIGHT, ignored -> setPlatform(platform), KeysetWidgets.ButtonStyle.TAB);
            button.setSelected(platform == currentPlatform);
        }

        keyboardX = left + 2;
        keyboardWidth = Math.max(1, right - left - 4);
        int modeY = bottom - MODE_HEIGHT;
        modesY = modeY;
        selectedY = modeY - SELECTED_HEIGHT - 2;
        keyboardY = platformY + PLATFORM_HEIGHT + 3;
        int keyboardBottom = selectedY - 4;
        keyboardHeight = Math.max(1, keyboardBottom - keyboardY);

        UiAppearance appearance = KeysetClient.appearance();
        keyboardLayout = KeyboardDiagramLayout.forArea(keyboardX, keyboardY, keyboardWidth, keyboardHeight,
            100, appearance.keyboardFormFactor(), KeyboardLayouts.forLanguage(minecraft.options.languageCode),
            appearance.keyboardPresentation());
        ensureSelectedKey();
        createKeyButtons();
        createSelectedControls();
        updateModeButtons();
        updateErrorTooltip();
    }

    private void createKeyButtons() {
        KeyboardPlatform platform = KeysetClient.appearance().keyboardPresentation().platform();
        for (KeyboardDiagramLayout.Cell cell : keyboardLayout.keys()) {
            var rect = cell.bounds();
            Component displayName = keyName(cell.input(), cell.label(), platform);
            Component tooltipText = cell.input().equals(FN_INPUT)
                ? Component.translatable("keyset.legend_fn_hint")
                : displayName.copy().append("\n").append(Component.literal(cell.input()));
            KeyCellButton button = addRenderableWidget(new KeyCellButton(rect.x(), rect.y(), rect.width(), rect.height(),
                displayName, cell.input(), cell.label(), ignored -> select(cell.input())));
            button.setTooltip(Tooltip.create(tooltipText));
            keyButtons.add(button);
        }
    }

    private void createSelectedControls() {
        int previewWidth = Math.min(38, Math.max(22, keyboardWidth / 8));
        int resetWidth = Math.min(58, Math.max(42, keyboardWidth / 6));
        int previewX = right - PANEL_MARGIN - resetWidth - 3 - previewWidth;
        int resetX = right - PANEL_MARGIN - resetWidth;
        resetKeyButton = addButton(Component.translatable("keyset.legend_reset"), resetX, selectedY,
            resetWidth, SELECTED_HEIGHT, ignored -> resetSelected(), KeysetWidgets.ButtonStyle.ACTION);
        selectedPreview = new PreviewKeyButton(previewX, selectedY, previewWidth, SELECTED_HEIGHT);
        addRenderableWidget(selectedPreview);

        int gap = 3;
        int modeWidth = Math.max(1, (right - left - gap * (KeyLegendMode.values().length - 1))
            / KeyLegendMode.values().length);
        for (KeyLegendMode mode : KeyLegendMode.values()) {
            int x = left + mode.ordinal() * (modeWidth + gap);
            int width = mode == KeyLegendMode.values()[KeyLegendMode.values().length - 1]
                ? right - x : modeWidth;
            KeysetWidgets.FlatButton button = addButton(Component.translatable(mode.translationKey()), x, modesY,
                width, MODE_HEIGHT, ignored -> setMode(selectedInput, mode), KeysetWidgets.ButtonStyle.TAB);
            modeButtons[mode.ordinal()] = button;
        }
    }

    private void ensureSelectedKey() {
        if (selectedInput == null || keyboardLayout.keys().stream().noneMatch(cell -> cell.input().equals(selectedInput))) {
            selectedInput = keyboardLayout.keys().stream().filter(cell -> cell.input().equals("key.keyboard.escape"))
                .map(KeyboardDiagramLayout.Cell::input).findFirst()
                .orElseGet(() -> keyboardLayout.keys().isEmpty() ? null : keyboardLayout.keys().get(0).input());
        }
    }

    private void select(String input) {
        selectedInput = input;
        updateModeButtons();
    }

    private void setMode(String input, KeyLegendMode mode) {
        if (input == null) return;
        UiAppearance current = KeysetClient.appearance();
        save(current.withKeyboardPresentation(current.keyboardPresentation().withMode(input, mode)));
        updateModeButtons();
    }

    private void setPlatform(KeyboardPlatform platform) {
        UiAppearance current = KeysetClient.appearance();
        save(current.withKeyboardPresentation(current.keyboardPresentation().withPlatform(platform)));
        if (error == null) init();
    }

    private void resetSelected() {
        if (selectedInput == null) return;
        UiAppearance current = KeysetClient.appearance();
        KeyLegendMode defaultMode = KeyboardPresentation.defaults().modeFor(selectedInput);
        save(current.withKeyboardPresentation(current.keyboardPresentation().withMode(selectedInput, defaultMode)));
        updateModeButtons();
    }

    private void resetAll() {
        UiAppearance current = KeysetClient.appearance();
        save(current.withKeyboardPresentation(new KeyboardPresentation(current.keyboardPresentation().platform(),
            java.util.Map.of(), current.keyboardPresentation().bottomRow())));
        if (error == null) {
            selectedInput = "key.keyboard.escape";
            init();
        }
    }

    private void updateModeButtons() {
        KeyboardPresentation presentation = KeysetClient.appearance().keyboardPresentation();
        KeyLegendMode selectedMode = selectedInput == null ? KeyLegendMode.TEXT : presentation.modeFor(selectedInput);
        for (KeyLegendMode mode : KeyLegendMode.values()) {
            KeysetWidgets.FlatButton button = modeButtons[mode.ordinal()];
            if (button != null) button.setSelected(mode == selectedMode);
        }
        for (KeyCellButton button : keyButtons) button.setSelected(button.input.equals(selectedInput));
        if (selectedPreview != null) selectedPreview.setSelectedKey(selectedCell());
        if (resetKeyButton != null) resetKeyButton.setTooltip(Tooltip.create(Component.translatable(
            "keyset.legend_mode_for", selectedCell() == null ? Component.empty() : keyName(selectedCell().input(),
                selectedCell().label(), presentation.platform()), Component.translatable("keyset.legend_reset"))));
    }

    private KeyboardDiagramLayout.Cell selectedCell() {
        if (keyboardLayout == null || selectedInput == null) return null;
        return keyboardLayout.keys().stream().filter(cell -> cell.input().equals(selectedInput)).findFirst().orElse(null);
    }

    private void save(UiAppearance appearance) {
        try {
            KeysetClient.setAppearance(appearance);
            error = null;
        } catch (IOException | RuntimeException failure) {
            error = KeysetClient.operationError(failure);
        }
        updateErrorTooltip();
    }

    private void updateErrorTooltip() {
        if (resetAllButton != null) {
            resetAllButton.setTooltip(Tooltip.create(error == null
                ? Component.translatable("keyset.legend_reset_all") : error));
        }
    }

    private KeysetWidgets.FlatButton addButton(Component label, int x, int y, int width, int height,
                                                Button.OnPress action, KeysetWidgets.ButtonStyle style) {
        return addRenderableWidget(KeysetWidgets.flatButton(x, y, width, height, label, action, style));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        int titleWidth = Math.max(1, right - left - 96);
        KeysetWidgets.drawText(graphics, font, title, left, top + 6, titleWidth,
            error == null ? UiTheme.text() : UiTheme.red());

        graphics.fill(keyboardX - 2, keyboardY - 2, keyboardX + keyboardWidth + 2,
            keyboardY + keyboardHeight + 2, UiTheme.panelLight());

        KeyboardDiagramLayout.Cell selected = selectedCell();
        int labelWidth = Math.max(1, right - left - 12 - (selectedPreview == null ? 0 : selectedPreview.getWidth())
            - (resetKeyButton == null ? 0 : resetKeyButton.getWidth()));
        if (selected != null) {
            Component name = keyName(selected.input(), selected.label(), KeysetClient.appearance().keyboardPresentation().platform());
            KeysetWidgets.drawText(graphics, font, name, left + 2, selectedY + 4, labelWidth, UiTheme.text());
            KeycapPainter.State selectedState = new KeycapPainter.State(false, true, false, false, false, false);
            KeycapPainter.draw(graphics::fill, selectedPreview.getX(), selectedPreview.getY(),
                selectedPreview.getWidth(), selectedPreview.getHeight(), UiTheme.currentPalette(), selectedState);
            KeycapRenderer.drawKey(graphics, font, selected.input(), selected.label(),
                KeycapPainter.faceBounds(selectedPreview.getX(), selectedPreview.getY(),
                    selectedPreview.getWidth(), selectedPreview.getHeight()),
                KeycapPainter.labelColor(UiTheme.currentPalette(), selectedState), .2f,
                KeysetClient.appearance().keyboardPresentation());
        }
        if (error != null) {
            graphics.text(font, "!", right - 93, top + 6, UiTheme.red(), false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private static Component keyName(String input, String label, KeyboardPlatform platform) {
        String translation = KeyboardKeyNames.translationKey(input, platform);
        if (translation != null) return Component.translatable(translation);
        if (input.equals(FN_INPUT)) return Component.translatable("keyset.keyboard_fn");
        return Component.literal(label);
    }

    private final class KeyCellButton extends Button {
        private final String input;
        private final String label;
        private boolean selected;

        private KeyCellButton(int x, int y, int width, int height, Component name,
                              String input, String label, OnPress onPress) {
            super(x, y, width, height, name, onPress, DEFAULT_NARRATION);
            this.input = input;
            this.label = label;
        }

        private void setSelected(boolean selected) { this.selected = selected; }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = active && isHoveredOrFocused();
            KeycapPainter.State state = new KeycapPainter.State(hovered, selected, false, false,
                active && isFocused(), false);
            KeycapPainter.draw(graphics::fill, getX(), getY(), getWidth(), getHeight(), UiTheme.currentPalette(), state);
            KeycapRenderer.drawKey(graphics, Minecraft.getInstance().font, input, label,
                KeycapPainter.faceBounds(getX(), getY(), getWidth(), getHeight()),
                KeycapPainter.labelColor(UiTheme.currentPalette(), state), .18f,
                KeysetClient.appearance().keyboardPresentation());
        }
    }

    private final class PreviewKeyButton extends Button {
        private KeyboardDiagramLayout.Cell selectedCell;

        private PreviewKeyButton(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), ignored -> {}, DEFAULT_NARRATION);
            this.selectedCell = null;
            active = false;
        }

        private void setSelectedKey(KeyboardDiagramLayout.Cell cell) { selectedCell = cell; }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // The selected key is rendered by the screen so the mode preview always matches the current palette.
        }
    }
}
