package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.editor.BindingIndex;
import dev.keyset.editor.KeycapPainter;
import dev.keyset.editor.KeyboardDiagramLayout;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.KeyboardPreviewViewport;
import dev.keyset.editor.MouseDiagramLayout;
import dev.keyset.editor.MouseDiagramPainter;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.UiAppearance;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A read-only miniature of the current inputs, using the same geometry and colors as the editor. */
public final class AppearancePreviewRenderer implements AutoCloseable {
    private final MouseTextureCache mouseArtwork = new MouseTextureCache();
    private long revision = Long.MIN_VALUE;
    private BindingIndex<KeyMapping> allInputs;
    private BindingIndex<KeyMapping> visibleInputs;
    private UiAppearance cachedAppearance;
    private AppearancePalette cachedPalette;
    private KeyboardDiagramLayout clickableKeyboard;
    public void clearHitTest() { clickableKeyboard = null; }

    public String keyAt(double x, double y) {
        if (clickableKeyboard == null) return null;
        return clickableKeyboard.keys().stream().filter(cell -> {
            var rect = cell.bounds();
            return x >= rect.x() && x < rect.x() + rect.width() && y >= rect.y() && y < rect.y() + rect.height();
        }).map(KeyboardDiagramLayout.Cell::input).findFirst().orElse(null);
    }

    /** Color editing uses the full pane for one device instead of two unreadable thumbnails. */
    public void drawDevice(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           UiAppearance appearance, KeyboardLayouts.Layout language, boolean mouseMode, double keyboardPan) {
        AppearancePalette palette = palette(appearance);
        graphics.fill(x, y, x + width, y + height, palette.panel());
        if (mouseMode) {
            mouse(graphics, font, x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8),
                    appearance.mouseScale(), palette);
        } else {
            UiAppearance fitted = new UiAppearance(100, appearance.rowHeight(), appearance.inputOnRight(),
                appearance.accentColor(), appearance.panelColor(), appearance.panelOpacity(),
                appearance.boundKeyColor(), appearance.conflictKeyColor(), appearance.unboundKeyColor(),
                appearance.keyboardFormFactor(), appearance.keyboardPresentation(), appearance.mouseBodyColor(),
                appearance.mouseScale());
            var viewport = KeyboardPreviewViewport.forArea(width, height, appearance.keyboardFormFactor(), keyboardPan);
            graphics.enableScissor(x + 4, y + 4, x + 4 + viewport.visibleWidth(), y + 4 + viewport.diagramHeight());
            keyboard(graphics, font, x + 4 - viewport.offset(), y + 4, viewport.contentWidth(), viewport.diagramHeight(),
                fitted, language, palette);
            graphics.disableScissor();
        }
    }

    private AppearancePalette palette(UiAppearance appearance) {
        refreshBindings();
        if (!appearance.equals(cachedAppearance)) {
            cachedAppearance = appearance;
            cachedPalette = AppearancePalette.from(appearance);
        }
        return cachedPalette;
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                     UiAppearance appearance, KeyboardLayouts.Layout language) {
        AppearancePalette palette = palette(appearance);
        graphics.fill(x, y, x + width, y + height, palette.panel());
        if (height <= 80) {
            int keyboardWidth = width * 3 / 5;
            keyboard(graphics, font, x + 2, y + 2, keyboardWidth - 4, height - 4, appearance, language, palette);
            mouse(graphics, font, x + keyboardWidth, y + 2, width - keyboardWidth - 2, height - 4,
                    appearance.mouseScale(), palette);
            return;
        }
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_preview"),
                x + 6, y + 6, width - 12, palette.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.input_keyboard")
                        .append(" · " + appearance.keyboardFormFactor().label()),
                x + 6, y + 24, width - 12, palette.muted());
        int keyboardHeight = Math.min(140, Math.max(42, height / 3));
        keyboard(graphics, font, x + 4, y + 37, width - 8, keyboardHeight, appearance, language, palette);
        int mouseTop = y + 37 + keyboardHeight + 12;
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.input_mouse"),
                x + 6, mouseTop, width - 12, palette.muted());
        mouse(graphics, font, x + 4, mouseTop + 12, width - 8,
                Math.max(1, y + height - mouseTop - 16), appearance.mouseScale(), palette);
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
        if (width < 1 || height < 1) return;
        final int virtualWidth = 280, virtualHeight = 148;
        float scale = Math.min(width / (float) virtualWidth, height / (float) virtualHeight);
        var layout = MouseDiagramLayout.forArea(0, 0, virtualWidth, virtualHeight, mouseScale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (width - virtualWidth * scale) / 2f,
                y + (height - virtualHeight * scale) / 2f);
        graphics.pose().scale(scale, scale);
        MouseDiagramRenderer.shell(graphics, layout, palette, mouseArtwork);
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
            MouseDiagramRenderer.button(graphics, font, Component.literal(label), part.control(), rect, state, palette, mouseArtwork);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public void close() { mouseArtwork.close(); }

}
