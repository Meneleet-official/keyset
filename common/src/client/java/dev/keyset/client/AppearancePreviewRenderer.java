package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.editor.BindingIndex;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.editor.KeyboardDiagramLayout;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.MouseDiagramLayout;
import dev.keyset.editor.MouseDiagramPainter;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.UiAppearance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A read-only miniature of the current inputs, using the same geometry and colors as the editor. */
public final class AppearancePreviewRenderer implements AutoCloseable {
    private final MouseTextureCache mouseArtwork = new MouseTextureCache();
    /** The "after" mouse of the color editor needs its own textures, or both halves would show the same color. */
    private final MouseTextureCache afterArtwork = new MouseTextureCache();
    private long revision = Long.MIN_VALUE;
    private BindingIndex<KeyMapping> allInputs;
    private BindingIndex<KeyMapping> visibleInputs;
    private UiAppearance cachedAppearance;
    private AppearancePalette cachedPalette;
    private KeyboardDiagramLayout clickableKeyboard;
    public void clearHitTest() { clickableKeyboard = null; }

    private int[] visibleArea;

    public String keyAt(double x, double y) {
        if (clickableKeyboard == null) return null;
        if (visibleArea != null && (x < visibleArea[0] || x >= visibleArea[2] || y < visibleArea[1] || y >= visibleArea[3])) {
            return null;
        }
        return clickableKeyboard.keys().stream().filter(cell -> {
            var rect = cell.bounds();
            return x >= rect.x() && x < rect.x() + rect.width() && y >= rect.y() && y < rect.y() + rect.height();
        }).map(KeyboardDiagramLayout.Cell::input).findFirst().orElse(null);
    }

    private AppearancePalette palette(UiAppearance appearance) {
        refreshBindings();
        if (!appearance.equals(cachedAppearance)) {
            cachedAppearance = appearance;
            cachedPalette = AppearancePalette.from(appearance);
        }
        return cachedPalette;
    }

    /** What the settings under the cursor change; the preview shows only that. */
    public enum Focus { LAYOUT, ROWS, KEYBOARD, MOUSE, ALL, PANEL, ACCENT, BOUND, CONFLICT, FREE }

    private static final int SCHEMATIC_HEIGHT = 62;
    private static final int MOUSE_HEIGHT = 110;

    private static int keyboardHeight(int width) {
        return Math.max(42, Math.min(140, width * 2 / 5));
    }

    /** Height of the full (tall) preview content, so callers can scroll it inside a smaller pane. */
    public static int contentHeight(int width) {
        return 22 + SCHEMATIC_HEIGHT + 6 + 12 + keyboardHeight(width) + 12 + 12 + MOUSE_HEIGHT + 8;
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                     UiAppearance appearance, KeyboardLayouts.Layout language, Focus focus, int scroll) {
        AppearancePalette palette = palette(appearance);
        visibleArea = new int[] {x, y, x + width, y + height};
        clickableKeyboard = null;
        graphics.fill(x, y, x + width, y + height, palette.panel());
        if (height <= 80) {
            int keyboardWidth = width * 3 / 5;
            keyboard(graphics, font, x + 2, y + 2, keyboardWidth - 4, height - 4, appearance, language, palette);
            mouse(graphics, font, x + keyboardWidth, y + 2, width - keyboardWidth - 2, height - 4,
                    appearance.mouseScale(), palette);
            return;
        }
        if (focus != Focus.ALL) {
            drawFocused(graphics, font, x, y, width, height, appearance, language, palette, focus);
            return;
        }
        int content = contentHeight(width);
        int offset = Math.max(0, Math.min(scroll, Math.max(0, content - height)));
        graphics.enableScissor(x, y, x + width, y + height);
        int top = y - offset;
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_preview"),
                x + 6, top + 6, width - 12, palette.text());
        int schematicTop = top + 22;
        layoutSchematic(graphics, font, x + 4, schematicTop, width - 8, SCHEMATIC_HEIGHT, appearance, palette);
        int keyboardLabelY = schematicTop + SCHEMATIC_HEIGHT + 6;
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.input_keyboard")
                        .append(" · " + appearance.keyboardFormFactor().label()),
                x + 6, keyboardLabelY, width - 12, palette.muted());
        int keyboardHeight = keyboardHeight(width);
        keyboard(graphics, font, x + 4, keyboardLabelY + 12, width - 8, keyboardHeight, appearance, language, palette);
        int mouseTop = keyboardLabelY + 12 + keyboardHeight + 12;
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.input_mouse"),
                x + 6, mouseTop, width - 12, palette.muted());
        mouse(graphics, font, x + 4, mouseTop + 12, width - 8, MOUSE_HEIGHT, appearance.mouseScale(), palette);
        graphics.disableScissor();
        if (content > height) {
            int thumb = Math.max(12, height * height / content);
            int thumbY = y + (height - thumb) * offset / Math.max(1, content - height);
            graphics.fill(x + width - 3, y, x + width, y + height, palette.row());
            graphics.fill(x + width - 3, thumbY, x + width, thumbY + thumb, palette.muted());
        }
    }

    /** Just the element a color changes, for the color editor's before and after views. */
    public void drawSample(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           UiAppearance appearance, Focus focus, boolean after) {
        drawSampleOne(graphics, font, x, y, width, height, appearance, focus, palette(appearance),
            after ? afterArtwork : mouseArtwork);
    }

    private void drawSampleOne(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                               UiAppearance appearance, Focus focus, AppearancePalette palette,
                               MouseTextureCache artwork) {
        if (width < 8 || height < 8) return;
        graphics.enableScissor(x, y, x + width, y + height);
        switch (focus) {
            case PANEL -> panelMock(graphics, font, x, y, width, height, palette);
            case ACCENT -> accentMock(graphics, font, x, y, width, height, palette);
            case BOUND, CONFLICT, FREE -> keysMock(graphics, font, x, y, width, height, appearance, focus, palette);
            default -> {
                graphics.fill(x, y, x + width, y + height, palette.panel());
                mouse(graphics, font, x + 4, y + 4, width - 8, height - 8, appearance.mouseScale(), palette, artwork);
            }
        }
        graphics.disableScissor();
    }

    /** The two editor panels over a checkered backdrop, so color and opacity both read clearly. */
    private void panelMock(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           AppearancePalette palette) {
        for (int cy = 0; cy < height; cy += 8) {
            for (int cx = 0; cx < width; cx += 8) {
                graphics.fill(x + cx, y + cy, Math.min(x + cx + 8, x + width), Math.min(y + cy + 8, y + height),
                    ((cx / 8) + (cy / 8)) % 2 == 0 ? 0xFF2B2B2B : 0xFF4A4A4A);
            }
        }
        int pad = 4, gap = 6;
        int leftWidth = Math.max(8, (width - 2 * pad - gap) * 2 / 5);
        int rightWidth = Math.max(8, width - 2 * pad - gap - leftWidth);
        int top = y + pad, panelHeight = height - 2 * pad;
        graphics.fill(x + pad, top, x + pad + leftWidth, top + panelHeight, palette.panel());
        graphics.fill(x + pad, top, x + pad + leftWidth, top + 1, palette.border());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.input_keyboard"),
            x + pad + 6, top + 6, leftWidth - 12, palette.text());
        int actionsX = x + pad + leftWidth + gap;
        graphics.fill(actionsX, top, actionsX + rightWidth, top + panelHeight, palette.panel());
        graphics.fill(actionsX, top, actionsX + rightWidth, top + 1, palette.border());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.bindings"),
            actionsX + 6, top + 6, rightWidth - 12, palette.text());
        for (int i = 0, rowY = top + 22; rowY + 12 <= top + panelHeight - 4; i++, rowY += 14) {
            graphics.fill(actionsX + 4, rowY, actionsX + rightWidth - 4, rowY + 12,
                i % 2 == 0 ? palette.row() : palette.rowAlternate());
            graphics.fill(actionsX + 8, rowY + 4, actionsX + 8 + rightWidth / 3, rowY + 8, palette.muted());
        }
    }

    /** The places the accent color shows up: selected tab, primary button, search underline, badge, scroll thumb. */
    private void accentMock(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                            AppearancePalette palette) {
        graphics.fill(x, y, x + width, y + height, palette.panel());
        int pad = 8, tabWidth = Math.max(10, (width - 2 * pad - 4) / 2);
        int tabY = y + pad;
        graphics.fill(x + pad, tabY, x + pad + tabWidth, tabY + 18, palette.selected());
        graphics.fill(x + pad, tabY + 16, x + pad + tabWidth, tabY + 18, palette.accent());
        Component tab = Component.translatable("keyset.input_keyboard");
        graphics.text(font, tab, x + pad + (tabWidth - font.width(tab)) / 2, tabY + 5, palette.accentText(), false);
        graphics.fill(x + pad + tabWidth + 4, tabY, x + width - pad, tabY + 18, palette.control());
        Component other = Component.translatable("keyset.input_mouse");
        graphics.text(font, other, x + pad + tabWidth + 4 + (width - pad - (x + pad + tabWidth + 4 - x) - font.width(other)) / 2,
            tabY + 5, palette.text(), false);
        int buttonY = tabY + 26;
        graphics.fill(x + pad, buttonY, x + width - pad, buttonY + 20, palette.control());
        graphics.fill(x + pad, buttonY + 18, x + width - pad, buttonY + 20, palette.accent());
        Component done = Component.translatable("keyset.done");
        graphics.text(font, done, x + (width - font.width(done)) / 2, buttonY + 6, palette.text(), false);
        int searchY = buttonY + 30;
        graphics.fill(x + pad, searchY, x + width - pad, searchY + 18, palette.control());
        graphics.fill(x + pad, searchY + 17, x + width - pad, searchY + 18, palette.accent());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.search_hint"), x + pad + 4, searchY + 5,
            width - 2 * pad - 8, palette.muted());
        int badgeY = searchY + 28;
        String badge = "✓ " + Component.translatable("keyset.profile_active_badge").getString();
        int badgeWidth = font.width(badge) + 10;
        graphics.fill(x + pad, badgeY, x + pad + badgeWidth, badgeY + 14, palette.selected());
        graphics.outline(x + pad, badgeY, badgeWidth, 14, palette.accent());
        graphics.text(font, badge, x + pad + 5, badgeY + 3, palette.accentText(), false);
        int trackX = x + width - pad - 3;
        graphics.fill(trackX, badgeY, trackX + 3, badgeY + 40, 0x90000000);
        graphics.fill(trackX, badgeY + 6, trackX + 3, badgeY + 22, palette.accent());
    }

    /**
     * Twelve keys scattered in a fixed order across all three states, so the color being edited can be judged
     * against the others: four keys in its own state, four in each of the other two.
     */
    private void keysMock(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                          UiAppearance appearance, Focus focus, AppearancePalette palette) {
        graphics.fill(x, y, x + width, y + height, palette.panel());
        String[] letters = {"W", "A", "S", "D", "Q", "E", "R", "F", "Z", "X", "C", "V"};
        List<Focus> states = new ArrayList<>();
        for (Focus state : new Focus[] {Focus.BOUND, Focus.CONFLICT, Focus.FREE}) {
            for (int i = 0; i < 4; i++) states.add(state);
        }
        // Same shuffle every frame, and for the "before" and "after" views alike.
        Collections.shuffle(states, new Random(11));
        // Make sure the edited state is not hidden in a corner: it always owns the first key.
        int own = states.indexOf(focus);
        Collections.swap(states, 0, own);
        int columns = 4, rows = 3;
        int pitch = Math.max(8, Math.min(Math.min((width - 8) / columns, (height - 8) / rows), 44));
        int startX = x + (width - pitch * columns) / 2, startY = y + (height - pitch * rows) / 2;
        for (int i = 0; i < letters.length; i++) {
            Focus kind = states.get(i);
            var state = new KeycapPainter.State(false, false, kind == Focus.BOUND, kind == Focus.CONFLICT, false,
                kind == Focus.FREE);
            int keyX = startX + (i % columns) * pitch, keyY = startY + (i / columns) * pitch, size = pitch - 2;
            KeycapPainter.draw(graphics::fill, keyX, keyY, size, size, palette, state);
            KeycapRenderer.drawKey(graphics, font, "key.keyboard." + letters[i].toLowerCase(Locale.ROOT), letters[i],
                KeycapPainter.faceBounds(keyX, keyY, size, size), KeycapPainter.labelColor(palette, state), 0.3f,
                appearance.keyboardPresentation());
        }
    }

    private void drawFocused(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                             UiAppearance appearance, KeyboardLayouts.Layout language, AppearancePalette palette, Focus focus) {
        Component heading = switch (focus) {
            case LAYOUT -> Component.translatable("keyset.appearance_input_label");
            case ROWS -> Component.translatable("keyset.appearance_row_height_label");
            case MOUSE -> Component.translatable("keyset.input_mouse");
            case PANEL -> Component.translatable("keyset.appearance_panel_label");
            case ACCENT -> Component.translatable("keyset.appearance_accent_label");
            case BOUND -> Component.translatable("keyset.appearance_bound_color_label");
            case CONFLICT -> Component.translatable("keyset.appearance_conflict_color_label");
            case FREE -> Component.translatable("keyset.appearance_free_color_label");
            default -> {
                var form = appearance.keyboardFormFactor();
                var resolved = form.resolve(Math.max(1, width - 8), height);
                Component name = form == dev.keyset.profile.KeyboardFormFactor.AUTO
                    ? Component.translatable("keyset.appearance_auto").append(" · " + resolved.label())
                    : Component.literal(form.label());
                yield Component.translatable("keyset.input_keyboard").append(" · ").append(name);
            }
        };
        KeysetWidgets.drawText(graphics, font, heading, x + 6, y + 6, width - 12, palette.text());
        int top = y + 22, areaHeight = Math.max(1, height - 26);
        switch (focus) {
            case LAYOUT -> layoutSchematic(graphics, font, x + 4, top, width - 8,
                Math.max(SCHEMATIC_HEIGHT, Math.min(areaHeight, 150)), appearance, palette);
            case ROWS -> rowsDemo(graphics, font, x + 4, top, width - 8, areaHeight, appearance, palette);
            case MOUSE -> mouse(graphics, font, x + 4, top, width - 8, areaHeight, appearance.mouseScale(), palette);
            case PANEL, ACCENT, BOUND, CONFLICT, FREE ->
                drawSampleOne(graphics, font, x + 4, top, width - 8, areaHeight, appearance, focus, palette, mouseArtwork);
            default -> keyboard(graphics, font, x + 4, top, width - 8, areaHeight, appearance, language, palette);
        }
    }

    /** Action rows at their real height, so the chosen row height can be judged as it will look. */
    private void rowsDemo(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                          UiAppearance appearance, AppearancePalette palette) {
        String[] names = {"key.jump", "key.sneak", "key.sprint", "key.inventory", "key.attack", "key.use", "key.drop", "key.chat"};
        String[] keys = {"SPACE", "SHIFT", "CTRL", "E", "LMB", "RMB", "Q", "T"};
        int rowPx = appearance.rowHeight();
        int chipWidth = Math.min(54, Math.max(30, width / 4));
        int rowY = y;
        for (int i = 0; i < names.length && rowY + rowPx - 2 <= y + height - 12; i++, rowY += rowPx) {
            graphics.fill(x, rowY, x + width, rowY + rowPx - 2, i % 2 == 0 ? palette.row() : palette.rowAlternate());
            int textY = rowY + (rowPx - 2 - font.lineHeight) / 2;
            KeysetWidgets.drawText(graphics, font, Component.translatable(names[i]), x + 6, textY,
                Math.max(1, width - chipWidth - 20), palette.text());
            int chipY = rowY + 1;
            graphics.fill(x + width - chipWidth - 6, chipY, x + width - 6, rowY + rowPx - 3, palette.keyFace());
            graphics.text(font, keys[i], x + width - 6 - chipWidth / 2 - font.width(keys[i]) / 2, textY, palette.text(), false);
        }
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_pixels", Integer.toString(rowPx)),
            x, y + height - 10, width, palette.muted());
    }

    /** A miniature of the editor window: where the input panel sits and how tall one action row is. */
    private void layoutSchematic(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                                 UiAppearance appearance, AppearancePalette palette) {
        int gap = 4;
        int inputWidth = Math.max(8, (width - gap) * 2 / 5);
        int actionsWidth = Math.max(8, width - gap - inputWidth);
        boolean right = appearance.inputOnRight();
        int inputX = right ? x + width - inputWidth : x;
        int actionsX = right ? x : x + inputWidth + gap;
        int paneHeight = height - 14;
        graphics.fill(actionsX, y, actionsX + actionsWidth, y + paneHeight, palette.row());
        int rowPx = appearance.rowHeight();
        int rowHeight = Math.max(3, Math.round(rowPx * 0.45f));
        for (int row = 0, rowY = y + 3; rowY + rowHeight <= y + paneHeight - 2; row++, rowY += rowHeight + 1) {
            graphics.fill(actionsX + 3, rowY, actionsX + actionsWidth - 3, rowY + rowHeight,
                    row % 2 == 0 ? palette.control() : palette.rowAlternate());
            graphics.fill(actionsX + actionsWidth - 3 - actionsWidth / 4, rowY + 1,
                    actionsX + actionsWidth - 5, rowY + rowHeight - 1, palette.keyFace());
        }
        graphics.fill(inputX, y, inputX + inputWidth, y + paneHeight, palette.panelLight());
        graphics.outline(inputX, y, inputWidth, paneHeight, palette.accent());
        int keySize = Math.max(2, Math.min(4, inputWidth / 12));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 7; col++) {
                int kx = inputX + 4 + col * (keySize + 1), ky = y + 4 + row * (keySize + 1);
                if (kx + keySize > inputX + inputWidth - 3 || ky + keySize > y + paneHeight - 3) continue;
                graphics.fill(kx, ky, kx + keySize, ky + keySize, palette.keyEdge());
            }
        }
        Component caption = Component.translatable("keyset.appearance_preview_layout",
                Component.translatable(right ? "keyset.appearance_input_right" : "keyset.appearance_input_left"),
                Integer.toString(rowPx));
        KeysetWidgets.drawText(graphics, font, caption, x, y + height - 10, width, palette.muted());
    }

    private void refreshBindings() {
        long current = KeysetClient.revision();
        if (allInputs != null && current == revision) return;
        var mappings = KeysetClient.mappings();
        allInputs = new BindingIndex<>(mappings, KeysetClient::binding, KeysetClient::bindingContextsConflict);
        visibleInputs = new BindingIndex<>(mappings.stream().filter(mapping ->
                !KeysetClient.isModHidden(KeysetClient.owner(mapping).id())
                && !KeysetClient.isCategoryHidden(mapping.getCategory().id().toString())).toList(),
                KeysetClient::binding, KeysetClient::bindingContextsConflict);
        revision = current;
    }

    private MouseDiagramPainter.State state(String input) {
        return new MouseDiagramPainter.State(false, false, !visibleInputs.forInput(input).isEmpty(),
                visibleInputs.keyConflicts(input), false, allInputs.forInput(input).isEmpty());
    }

    private void keyboard(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                          UiAppearance appearance, KeyboardLayouts.Layout language, AppearancePalette palette) {
        var layout = KeyboardDiagramLayout.forArea(x, y, Math.max(1, width), Math.max(1, height),
                appearance.keyboardScale(), appearance.keyboardFormFactor(), language, appearance.keyboardPresentation());
        clickableKeyboard = layout;
        for (var cell : layout.keys()) {
            var rect = cell.bounds();
            var state = state(cell.input());
            var keyState = new KeycapPainter.State(false, false, state.assigned(), state.conflict(), false, state.free());
            KeycapPainter.draw(graphics::fill, rect.x(), rect.y(), rect.width(), rect.height(), palette, keyState);
            KeycapRenderer.drawKey(graphics, font, cell.input(), cell.label(),
                    KeycapPainter.faceBounds(rect.x(), rect.y(), rect.width(), rect.height()),
                    KeycapPainter.labelColor(palette, keyState), 0.3f, appearance.keyboardPresentation());
        }
    }

    private void mouse(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                       int mouseScale, AppearancePalette palette) {
        mouse(graphics, font, x, y, width, height, mouseScale, palette, mouseArtwork);
    }

    private void mouse(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                       int mouseScale, AppearancePalette palette, MouseTextureCache artwork) {
        if (width < 1 || height < 1) return;
        final int virtualWidth = 280, virtualHeight = 148;
        float scale = Math.min(width / (float) virtualWidth, height / (float) virtualHeight);
        var layout = MouseDiagramLayout.forArea(0, 0, virtualWidth, virtualHeight, mouseScale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (width - virtualWidth * scale) / 2f,
                y + (height - virtualHeight * scale) / 2f);
        graphics.pose().scale(scale, scale);
        MouseDiagramRenderer.shell(graphics, layout, palette, artwork);
        for (var part : layout.buttons()) {
            String input = switch (part.control()) {
                case LEFT -> "key.mouse.left";
                case RIGHT -> "key.mouse.right";
                case MIDDLE -> "key.mouse.middle";
                case SIDE4 -> "key.mouse.4";
                case SIDE5 -> "key.mouse.5";
                case EXTRA6 -> "key.mouse.6";
                case EXTRA7 -> "key.mouse.7";
                case EXTRA8 -> "key.mouse.8";
            };
            var state = state(input);
            String label = switch (part.control()) {
                case LEFT -> Component.translatable("keyset.mouse_left").getString();
                case RIGHT -> Component.translatable("keyset.mouse_right").getString();
                case MIDDLE -> "M3";
                case SIDE4 -> "M4";
                case SIDE5 -> "M5";
                case EXTRA6 -> "M6";
                case EXTRA7 -> "M7";
                case EXTRA8 -> "M8";
            };
            var rect = part.bounds();
            if ((part.control() == MouseDiagramLayout.Control.LEFT || part.control() == MouseDiagramLayout.Control.RIGHT)
                    && font.width(label) > rect.width() - 6) {
                label = part.control() == MouseDiagramLayout.Control.LEFT ? "M1" : "M2";
            }
            MouseDiagramRenderer.button(graphics, font, Component.literal(label), part.control(), rect, state, palette, artwork);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public void close() { mouseArtwork.close(); afterArtwork.close(); }

}
