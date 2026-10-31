package dev.keyset.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.editor.ColorSelection;
import dev.keyset.editor.ColorPickerLayout;
import dev.keyset.editor.KeyboardLayouts;
import dev.keyset.editor.KeyboardPreviewViewport;
import dev.keyset.profile.AppearancePalette;
import dev.keyset.profile.UiAppearance;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A local color draft: dragging previews the color, confirmation saves it through the parent. */
public final class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final int originalColor;
    private final IntConsumer onConfirm;
    private final IntFunction<UiAppearance> previewAppearance;
    private final AppearancePreviewRenderer preview = new AppearancePreviewRenderer();
    private final List<ChannelSlider> channels = new ArrayList<>();
    private ColorSelection selection;
    private HueSlider hueSlider;
    private SaturationValueArea colorArea;
    private EditBox hexBox;
    private KeysetWidgets.FlatButton confirmButton;
    private KeysetWidgets.FlatButton colorsTab, previewTab, keyboardTab, mouseTab;
    private ColorPickerLayout pickerLayout;
    private boolean previewPage, previewMouse;
    private double keyboardPan;
    private PreviewScrollbar previewScrollbar;
    private String hexDraft;
    private boolean syncingHex;
    private boolean invalidHex;
    private int panelX, panelY, panelWidth, panelHeight, innerX, innerWidth, footerY;
    private int bodyTop, leftWidth, rightX, rightWidth, previewHeight, hexY;

    public ColorPickerScreen(Screen parent, Component title, int initialColor, IntConsumer onConfirm) {
        this(parent, title, initialColor, onConfirm, null);
    }

    public ColorPickerScreen withMousePreview(boolean enabled) {
        previewMouse = enabled;
        return this;
    }

    public ColorPickerScreen(Screen parent, Component title, int initialColor, IntConsumer onConfirm,
                             IntFunction<UiAppearance> previewAppearance) {
        super(title);
        this.parent = parent;
        this.originalColor = initialColor & 0xFFFFFF;
        this.selection = ColorSelection.fromRgb(initialColor);
        this.onConfirm = onConfirm;
        this.previewAppearance = previewAppearance;
        this.hexDraft = hex(initialColor);
    }

    @Override
    protected void init() {
        KeycapRenderer.resetFontMetrics();
        clearWidgets();
        channels.clear();
        pickerLayout = ColorPickerLayout.forScreen(width, height, previewAppearance != null);
        panelX = pickerLayout.x(); panelY = pickerLayout.y();
        panelWidth = pickerLayout.width(); panelHeight = pickerLayout.height();
        innerX = pickerLayout.innerX(); innerWidth = pickerLayout.innerWidth();
        footerY = pickerLayout.footerY(); bodyTop = pickerLayout.contentTop();
        leftWidth = pickerLayout.color().width();
        rightX = pickerLayout.samples().x(); rightWidth = pickerLayout.samples().width();
        int areaHeight = pickerLayout.color().height();
        colorArea = addRenderableWidget(new SaturationValueArea(innerX, bodyTop, leftWidth, areaHeight));
        hueSlider = addRenderableWidget(new HueSlider(innerX, bodyTop + areaHeight + 6, leftWidth, 14));
        hexY = pickerLayout.hex().y();
        hexBox = new EditBox(font, innerX + 15, hexY + 5, Math.max(1, leftWidth - 21), 12,
                Component.literal("HEX"));
        hexBox.setBordered(false);
        hexBox.setMaxLength(7);
        hexBox.setTextColor(UiTheme.text());
        hexBox.setValue(hexDraft);
        hexBox.setResponder(this::changeHex);
        addRenderableWidget(hexBox);

        previewHeight = pickerLayout.samples().height();
        for (int i = 0; i < 3; i++) {
            int shift = 16 - i * 8;
            channels.add(addRenderableWidget(new ChannelSlider(rightX, pickerLayout.channels().get(i).y(),
                    rightWidth, shift)));
        }
        initPreviewTabs();

        int buttonGap = 4;
        int buttonWidth = Math.max(1, (innerWidth - 2 * buttonGap) / 3);
        addRenderableWidget(KeysetWidgets.flatButton(innerX, footerY, buttonWidth, 20,
                Component.translatable("keyset.reset"), ignored -> changeSelection(ColorSelection.fromRgb(originalColor), true),
                KeysetWidgets.ButtonStyle.ACTION));
        addRenderableWidget(KeysetWidgets.flatButton(innerX + buttonWidth + buttonGap, footerY, buttonWidth, 20,
                Component.translatable("keyset.cancel"), ignored -> onClose(), KeysetWidgets.ButtonStyle.QUIET));
        int confirmX = innerX + 2 * (buttonWidth + buttonGap);
        confirmButton = addRenderableWidget(KeysetWidgets.flatButton(confirmX, footerY,
                innerX + innerWidth - confirmX, 20, Component.translatable("keyset.confirm"), ignored -> confirm(),
                KeysetWidgets.ButtonStyle.PRIMARY));
        confirmButton.active = !invalidHex;
        updateControlValues();
        refreshPreviewVisibility();
    }

    private void initPreviewTabs() {
        colorsTab = previewTab = keyboardTab = mouseTab = null;
        if (previewAppearance == null) return;
        if (pickerLayout.pagedPreview()) {
            var row = pickerLayout.pageTabs();
            int tabWidth = (row.width() - 4) / 2;
            colorsTab = addRenderableWidget(KeysetWidgets.flatButton(row.x(), row.y(), tabWidth, row.height(),
                Component.translatable("keyset.appearance_colors_tab"), ignored -> {
                    previewPage = false; refreshPreviewVisibility();
                }, KeysetWidgets.ButtonStyle.TAB));
            previewTab = addRenderableWidget(KeysetWidgets.flatButton(row.x() + tabWidth + 4, row.y(),
                row.width() - tabWidth - 4, row.height(), Component.translatable("keyset.appearance_preview"), ignored -> {
                    previewPage = true; refreshPreviewVisibility();
                }, KeysetWidgets.ButtonStyle.TAB));
        }
        var row = pickerLayout.deviceTabs();
        int tabWidth = (row.width() - 4) / 2;
        keyboardTab = addRenderableWidget(KeysetWidgets.flatButton(row.x(), row.y(), tabWidth, row.height(),
            Component.translatable("keyset.input_keyboard"), ignored -> {
                previewMouse = false; refreshPreviewVisibility();
            }, KeysetWidgets.ButtonStyle.TAB));
        mouseTab = addRenderableWidget(KeysetWidgets.flatButton(row.x() + tabWidth + 4, row.y(),
            row.width() - tabWidth - 4, row.height(), Component.translatable("keyset.input_mouse"), ignored -> {
                previewMouse = true; refreshPreviewVisibility();
            }, KeysetWidgets.ButtonStyle.TAB));
        previewScrollbar = addRenderableWidget(new PreviewScrollbar());
    }

    private void refreshPreviewVisibility() {
        boolean colors = !pickerLayout.pagedPreview() || !previewPage;
        colorArea.visible = hueSlider.visible = hexBox.visible = colors;
        channels.forEach(channel -> channel.visible = colors);
        if (colorsTab != null) {
            colorsTab.setSelected(!previewPage);
            previewTab.setSelected(previewPage);
        }
        if (keyboardTab != null) {
            keyboardTab.visible = mouseTab.visible = !pickerLayout.pagedPreview() || previewPage;
            keyboardTab.setSelected(!previewMouse);
            mouseTab.setSelected(previewMouse);
            var area = pickerLayout.preview();
            var viewport = KeyboardPreviewViewport.forArea(area.width(), area.height() - 24,
                previewAppearance.apply(selection.rgb()).keyboardFormFactor(), keyboardPan);
            previewScrollbar.visible = keyboardTab.visible && !previewMouse && viewport.scrollbarHeight() > 0;
        }
        setFocused(null);
    }

    private void changeHex(String value) {
        if (syncingHex) return;
        hexDraft = value;
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        invalidHex = !normalized.matches("[0-9a-fA-F]{6}");
        if (!invalidHex) {
            selection = selection.withRgb(Integer.parseInt(normalized, 16));
            if (value.startsWith("#")) {
                hexDraft = normalized;
                syncingHex = true;
                try { hexBox.setValue(normalized); }
                finally { syncingHex = false; }
            }
        }
        updateControlValues();
        confirmButton.active = !invalidHex;
    }

    private void changeSelection(ColorSelection next, boolean updateHex) {
        selection = next;
        if (updateHex) {
            invalidHex = false;
            hexDraft = hex(next.rgb());
            syncingHex = true;
            try { hexBox.setValue(hexDraft); }
            finally { syncingHex = false; }
        }
        updateControlValues();
        confirmButton.active = !invalidHex;
    }

    private void updateControlValues() {
        hueSlider.syncValue();
        channels.forEach(ChannelSlider::syncValue);
        colorArea.setMessage(Component.literal("HSV: " + Math.round(selection.hue() * 360) + ", "
                + Math.round(selection.saturation() * 100) + "%, " + Math.round(selection.brightness() * 100) + "%"));
    }

    private void confirm() {
        if (invalidHex) return;
        onConfirm.accept(selection.rgb());
        minecraft.setScreenAndShow(parent);
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, innerX, panelY + 11, innerWidth, UiTheme.text());
        boolean colorsVisible = !pickerLayout.pagedPreview() || !previewPage;
        if (previewAppearance != null && (!pickerLayout.pagedPreview() || previewPage)) {
            var area = pickerLayout.preview();
            preview.drawDevice(graphics, font, area.x(), area.y() + 24, area.width(), Math.max(1, area.height() - 24),
                previewAppearance.apply(selection.rgb()), KeyboardLayouts.forLanguage(minecraft.options.languageCode), previewMouse, keyboardPan);
        }
        if (colorsVisible) {
            int previewGap = 12;
            int previewWidth = Math.max(1, (rightWidth - previewGap) / 2);
            int chipsY = bodyTop;
            int chipsHeight = previewHeight;
            drawPreview(graphics, rightX, chipsY, previewWidth, chipsHeight, originalColor);
            int nextX = rightX + previewWidth + previewGap;
            drawPreview(graphics, nextX, chipsY, rightX + rightWidth - nextX, chipsHeight, selection.rgb());
            graphics.text(font, ">", rightX + previewWidth + 3, chipsY + (chipsHeight - font.lineHeight) / 2,
                    UiTheme.muted(), false);
            graphics.fill(innerX, hexY, innerX + leftWidth, hexY + 20, UiTheme.widgetSurface(UiTheme.control()));
            graphics.outline(innerX, hexY, leftWidth, 20,
                    invalidHex ? UiTheme.red() : hexBox.isFocused() ? UiTheme.accent() : UiTheme.border());
            graphics.text(font, "#", innerX + 5, hexY + 5, UiTheme.muted(), false);
        }
        if (invalidHex) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.appearance_invalid_hex"),
                    innerX, footerY - 12, innerWidth, UiTheme.red());
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPreview(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int rgb) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000 | rgb);
        graphics.outline(x, y, width, height, UiTheme.border());
        int foreground = AppearancePalette.contrastRatio(rgb, 0xFFFFFF)
                >= AppearancePalette.contrastRatio(rgb, 0) ? 0xFFFFFFFF : 0xFF000000;
        String value = hex(rgb);
        graphics.text(font, value, x + (width - font.width(value)) / 2,
                y + (height - font.lineHeight) / 2, foreground, false);
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    private final class PreviewScrollbar extends AbstractSliderButton {
        private double dragOffset;
        PreviewScrollbar() {
            super(pickerLayout.preview().x() + 4, pickerLayout.preview().bottom() - 12,
                pickerLayout.preview().width() - 8, 8, Component.translatable("keyset.preview_scroll_hint"), keyboardPan);
            setTooltip(Tooltip.create(Component.translatable("keyset.preview_scroll_hint")));
        }

        @Override protected void updateMessage() {}
        @Override protected void applyValue() { keyboardPan = value; }

        private int thumbWidth() {
            var area = pickerLayout.preview();
            var viewport = KeyboardPreviewViewport.forArea(area.width(), area.height() - 24,
                previewAppearance.apply(selection.rgb()).keyboardFormFactor(), value);
            return Math.max(20, getWidth() * viewport.visibleWidth() / viewport.contentWidth());
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            int thumb = thumbWidth();
            double start = getX() + (getWidth() - thumb) * value;
            dragOffset = event.x() >= start && event.x() < start + thumb ? event.x() - start : thumb / 2.0;
            setValue((event.x() - getX() - dragOffset) / Math.max(1, getWidth() - thumb));
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
            setValue((event.x() - getX() - dragOffset) / Math.max(1, getWidth() - thumbWidth()));
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int thumb = thumbWidth();
            int start = getX() + (int) Math.round((getWidth() - thumb) * value);
            graphics.fill(getX(), getY() + 3, getRight(), getY() + 5, UiTheme.border());
            graphics.fill(start, getY() + 1, start + thumb, getBottom() - 1,
                isHoveredOrFocused() ? UiTheme.accent() : UiTheme.muted());
            handleCursor(graphics);
        }
    }

    private final class SaturationValueArea extends AbstractWidget {
        private SaturationValueArea(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("HSV"));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int steps = Math.min(48, getWidth());
            for (int i = 0; i < steps; i++) {
                double saturation = i / (double) Math.max(1, steps - 1);
                int top = new ColorSelection(selection.hue(), saturation, 1).rgb();
                graphics.fillGradient(getX() + i * getWidth() / steps, getY(),
                        getX() + (i + 1) * getWidth() / steps, getBottom(), 0xFF000000 | top, 0xFF000000);
            }
            int markerX = getX() + (int) Math.round(selection.saturation() * (getWidth() - 1));
            int markerY = getY() + (int) Math.round((1 - selection.brightness()) * (getHeight() - 1));
            graphics.outline(markerX - 3, markerY - 3, 7, 7, 0xFF000000);
            graphics.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
            graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2,
                    isFocused() ? UiTheme.accent() : UiTheme.border());
            handleCursor(graphics);
        }

        private void selectAt(MouseButtonEvent event) {
            changeSelection(selection.withSaturationAndBrightness(
                    (event.x() - getX()) / Math.max(1, getWidth() - 1),
                    1 - (event.y() - getY()) / Math.max(1, getHeight() - 1)), true);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) { selectAt(event); }

        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) { selectAt(event); }

        @Override
        public boolean keyPressed(KeyEvent event) {
            double saturation = selection.saturation();
            double brightness = selection.brightness();
            if (event.key() == InputConstants.KEY_LEFT) saturation -= 0.01;
            else if (event.key() == InputConstants.KEY_RIGHT) saturation += 0.01;
            else if (event.key() == InputConstants.KEY_DOWN) brightness -= 0.01;
            else if (event.key() == InputConstants.KEY_UP) brightness += 0.01;
            else return super.keyPressed(event);
            changeSelection(selection.withSaturationAndBrightness(saturation, brightness), true);
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
        }
    }

    private final class HueSlider extends AbstractSliderButton {
        private HueSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), selection.hue());
            updateMessage();
        }

        private void syncValue() { value = selection.hue(); updateMessage(); }

        @Override
        protected void updateMessage() { setMessage(Component.literal("H: " + Math.round(value * 360) + "°")); }

        @Override
        protected void applyValue() { changeSelection(selection.withHue(Math.min(Math.nextDown(1.0), value)), true); }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int trackWidth = Math.max(1, getWidth() - 8);
            int steps = Math.min(60, trackWidth);
            for (int i = 0; i < steps; i++) {
                int rgb = new ColorSelection(i / (double) Math.max(1, steps - 1), 1, 1).rgb();
                graphics.fill(getX() + 4 + i * trackWidth / steps, getY() + 2,
                        getX() + 4 + (i + 1) * trackWidth / steps, getBottom() - 2, 0xFF000000 | rgb);
            }
            drawHandle(graphics, getX() + 4 + (int) Math.round(value * trackWidth), getY() + 1, getHeight() - 2);
            if (isFocused()) graphics.outline(getX(), getY(), getWidth(), getHeight(), UiTheme.accent());
            handleCursor(graphics);
        }
    }

    private final class ChannelSlider extends AbstractSliderButton {
        private final int shift;
        private final String label;

        private ChannelSlider(int x, int y, int width, int shift) {
            super(x, y, width, 24, Component.empty(), selection.channel(shift) / 255.0);
            this.shift = shift;
            label = shift == 16 ? "R" : shift == 8 ? "G" : "B";
            updateMessage();
        }

        private void syncValue() { value = selection.channel(shift) / 255.0; updateMessage(); }

        @Override
        protected void updateMessage() { setMessage(Component.literal(label + ": " + Math.round(value * 255))); }

        @Override
        protected void applyValue() { changeSelection(selection.withChannel(shift, (int) Math.round(value * 255)), true); }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), UiTheme.widgetSurface(UiTheme.control()));
            graphics.text(font, label, getX() + 4, getY() + 2, UiTheme.text(), false);
            String number = Integer.toString(selection.channel(shift));
            graphics.text(font, number, getRight() - 4 - font.width(number), getY() + 2, UiTheme.text(), false);
            int trackWidth = Math.max(1, getWidth() - 8);
            int steps = Math.min(32, trackWidth);
            for (int i = 0; i < steps; i++) {
                int channel = (int) Math.round(i * 255.0 / Math.max(1, steps - 1));
                int rgb = (selection.rgb() & ~(0xFF << shift)) | (channel << shift);
                graphics.fill(getX() + 4 + i * trackWidth / steps, getBottom() - 9,
                        getX() + 4 + (i + 1) * trackWidth / steps, getBottom() - 2, 0xFF000000 | rgb);
            }
            drawHandle(graphics, getX() + 4 + (int) Math.round(value * trackWidth), getBottom() - 10, 9);
            if (isFocused()) graphics.outline(getX(), getY(), getWidth(), getHeight(), UiTheme.accent());
            handleCursor(graphics);
        }
    }

    private static void drawHandle(GuiGraphicsExtractor graphics, int centerX, int y, int height) {
        graphics.fill(centerX - 3, y, centerX + 3, y + height, 0xFF000000);
        graphics.outline(centerX - 2, y + 1, 4, Math.max(1, height - 2), 0xFFFFFFFF);
    }
}
