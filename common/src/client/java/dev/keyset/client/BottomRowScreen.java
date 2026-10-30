package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.editor.KeyboardDiagramLayout;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.KeyboardKeyNames;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.profile.KeyboardBottomRow;
import dev.keyset.profile.KeyboardPlatform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Reorders existing physical modifiers without changing the inputs they represent. */
public final class BottomRowScreen extends Screen {
    private static final Set<String> INPUTS = Set.of("key.keyboard.fn", "key.keyboard.left.control", "key.keyboard.right.control",
        "key.keyboard.left.win", "key.keyboard.right.win", "key.keyboard.left.alt", "key.keyboard.right.alt", "key.keyboard.space");
    private final Screen parent;
    private String selected = "key.keyboard.space";
    private List<KeyboardDiagramLayout.Cell> row = List.of();
    private int x, y, panelWidth, panelHeight, innerX, innerWidth, rowY;
    private Component error;
    private KeysetWidgets.FlatButton moveLeft, moveRight;

    public BottomRowScreen(Screen parent) {
        super(Component.translatable("keyset.bottom_title"));
        this.parent = parent;
    }

    @Override protected void init() {
        KeysetClient.ensureLoaded();
        clearWidgets();
        panelWidth = Math.min(760, width - 16);
        panelHeight = Math.min(270, height - 16);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        innerX = x + 12; innerWidth = panelWidth - 24;
        button(Component.translatable("keyset.done"), x + panelWidth - 78, y + 10, 66, 20, b -> onClose());
        var appearance = KeysetClient.appearance();
        int platformWidth = (innerWidth - 4 * (KeyboardPlatform.values().length - 1)) / KeyboardPlatform.values().length;
        for (var platform : KeyboardPlatform.values()) {
            var control = button(Component.translatable(platform.translationKey()), innerX + platform.ordinal() * (platformWidth + 4),
                y + 36, platformWidth, 20, b -> savePlatform(platform));
            control.setSelected(platform == appearance.keyboardPresentation().platform());
        }
        var layout = KeyboardDiagramLayout.forArea(0, 0, KeyboardDiagramLayout.columns(appearance.keyboardFormFactor()) * 40,
            KeyboardDiagramLayout.rows(appearance.keyboardFormFactor()) * 40, 100, appearance.keyboardFormFactor(),
            KeyboardLayouts.forLanguage(minecraft.options.languageCode), appearance.keyboardPresentation());
        row = layout.keys().stream().filter(cell -> INPUTS.contains(cell.input())).toList();
        if (row.stream().noneMatch(cell -> cell.input().equals(selected))) selected = row.get(0).input();
        int left = row.get(0).bounds().x();
        var last = row.get(row.size() - 1).bounds();
        int contentWidth = last.x() + last.width() - left;
        float scale = Math.min(innerWidth / (float) contentWidth, 36f / row.get(0).bounds().height());
        int startX = innerX + (innerWidth - Math.round(contentWidth * scale)) / 2;
        rowY = y + 80;
        for (var cell : row) {
            var bounds = cell.bounds();
            addRenderableWidget(new BottomKeyButton(startX + Math.round((bounds.x() - left) * scale), rowY,
                Math.max(1, Math.round(bounds.width() * scale)), Math.max(1, Math.round(bounds.height() * scale)), cell));
        }
        int controlsY = y + 126;
        int third = (innerWidth - 8) / 3;
        moveLeft = button(Component.translatable("keyset.bottom_move_left"), innerX, controlsY, third, 20, b -> move(-1));
        moveRight = button(Component.translatable("keyset.bottom_move_right"), innerX + third + 4, controlsY, third, 20, b -> move(1));
        button(Component.translatable("keyset.appearance_reset"), innerX + (third + 4) * 2, controlsY, third, 20, b -> reset());
        refreshMoveButtons();
        button(Component.literal("-"), innerX, y + 154, 26, 20, b -> changeWidth(-1));
        int quarters = currentSpaceWidth();
        button(Component.translatable("keyset.bottom_space_width", String.format(java.util.Locale.ROOT, "%.2f", quarters / 4.0)),
            innerX + 30, y + 154, innerWidth - 60, 20, b -> resetWidth());
        button(Component.literal("+"), innerX + innerWidth - 26, y + 154, 26, 20, b -> changeWidth(1));
    }

    private int currentSpaceWidth() {
        int stored = KeysetClient.appearance().keyboardPresentation().bottomRow().spaceWidth();
        if (stored != 0) return stored;
        var appearance = KeysetClient.appearance();
        return appearance.keyboardPresentation().platform().resolved() == KeyboardPlatform.MAC
            ? (appearance.keyboardFormFactor() == dev.keyset.profile.KeyboardFormFactor.COMPACT_75 ? 22 : 30) : 25;
    }

    private void refreshMoveButtons() {
        int index = index();
        moveLeft.active = index > 0;
        moveRight.active = index >= 0 && index < row.size() - 1;
    }
    private int index() {
        for (int i = 0; i < row.size(); i++) if (row.get(i).input().equals(selected)) return i;
        return -1;
    }
    private void move(int direction) {
        int index = index(), destination = index + direction;
        if (index < 0 || destination < 0 || destination >= row.size()) return;
        List<String> order = new ArrayList<>(row.stream().map(KeyboardDiagramLayout.Cell::input).toList());
        Collections.swap(order, index, destination);
        save(KeysetClient.appearance().keyboardPresentation().bottomRow().withOrder(order));
    }
    private void changeWidth(int direction) {
        save(KeysetClient.appearance().keyboardPresentation().bottomRow().withSpaceWidth(currentSpaceWidth() + direction));
    }
    private void resetWidth() { save(KeysetClient.appearance().keyboardPresentation().bottomRow().withSpaceWidth(0)); }
    private void reset() { save(KeyboardBottomRow.defaults()); }
    private void save(KeyboardBottomRow bottom) {
        try {
            var current = KeysetClient.appearance();
            KeysetClient.setAppearance(current.withKeyboardPresentation(current.keyboardPresentation().withBottomRow(bottom)));
            error = null; init();
        } catch (IOException | RuntimeException failure) { error = KeysetClient.operationError(failure); }
    }
    private void savePlatform(KeyboardPlatform platform) {
        try {
            var current = KeysetClient.appearance();
            KeysetClient.setAppearance(current.withKeyboardPresentation(current.keyboardPresentation().withPlatform(platform)));
            error = null; init();
        } catch (IOException | RuntimeException failure) { error = KeysetClient.operationError(failure); }
    }
    private KeysetWidgets.FlatButton button(Component label, int bx, int by, int bw, int bh, Button.OnPress action) {
        return addRenderableWidget(KeysetWidgets.flatButton(bx, by, bw, bh, label, action, KeysetWidgets.ButtonStyle.ACTION));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        KeysetWidgets.drawCard(graphics, x, y, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, innerX, y + 15, innerWidth - 74, UiTheme.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.bottom_hint"), innerX, y + 64, innerWidth, UiTheme.muted());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.bottom_fit_hint"), innerX, y + 184, innerWidth, UiTheme.muted());
        if (error != null) KeysetWidgets.drawText(graphics, font, error, innerX, y + panelHeight - 18, innerWidth, UiTheme.red());
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.setScreenAndShow(parent); }

    private final class BottomKeyButton extends Button {
        private final KeyboardDiagramLayout.Cell cell;
        BottomKeyButton(int x, int y, int w, int h, KeyboardDiagramLayout.Cell cell) {
            super(x, y, w, h, Component.literal(cell.label()), b -> {
                selected = cell.input(); refreshMoveButtons();
            }, DEFAULT_NARRATION);
            this.cell = cell;
            String override = KeyboardKeyNames.translationKey(cell.input(), KeysetClient.appearance().keyboardPresentation().platform());
            setTooltip(Tooltip.create(override == null ? Component.translatable(cell.input()) : Component.translatable(override)));
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            var state = new KeycapPainter.State(isHovered(), selected.equals(cell.input()), false, false, isFocused(), true);
            var palette = UiTheme.currentPalette();
            KeycapPainter.draw(graphics::fill, getX(), getY(), getWidth(), getHeight(), palette, state);
            KeycapRenderer.drawKey(graphics, font, cell.input(), cell.label(), KeycapPainter.faceBounds(getX(), getY(), getWidth(), getHeight()),
                KeycapPainter.labelColor(palette, state), .2f, KeysetClient.appearance().keyboardPresentation());
        }
    }
}
