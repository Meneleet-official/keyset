package dev.keyset.client;

import dev.keyset.KeysetClient;
import dev.keyset.profile.Binding;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Resolves an existing conflict without interrupting normal immediate key assignment. */
public final class BindingConflictScreen extends Screen {
    private final Screen parent;
    private final String actionId;
    private List<KeyMapping> peers = List.of();
    private KeyMapping action;
    private long observedRevision;
    private int scroll, listTop, listBottom, panelX, panelY, panelWidth, panelHeight, capacity;
    private Component error;
    private final List<StringWidget> peerNames = new ArrayList<>();

    public BindingConflictScreen(Screen parent, KeyMapping mapping) {
        super(Component.translatable("keyset.conflict_title"));
        this.parent = parent;
        this.actionId = mapping.getName();
    }

    @Override
    protected void init() {
        clearWidgets();
        peerNames.clear();
        observedRevision = KeysetClient.revision();
        action = KeysetClient.mappings().stream().filter(mapping -> mapping.getName().equals(actionId)).findFirst().orElse(null);
        peers = action == null ? List.of() : KeysetClient.conflictsFor(action, KeysetClient.binding(action));
        panelWidth = Math.min(620, width - 32);
        panelHeight = Math.min(360, height - 32);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listTop = panelY + 88;
        listBottom = panelY + panelHeight - 78;
        capacity = Math.max(1, (listBottom - listTop) / 32);
        scroll = Math.max(0, Math.min(scroll, peers.size() - capacity));
        for (int slot = 0; slot < capacity; slot++) {
            StringWidget label = new StringWidget(panelX + 14, listTop + slot * 32, 1, 15,
                    Component.empty(), font).setMaxWidth(panelWidth - 28);
            peerNames.add(addRenderableWidget(label));
        }
        int buttonWidth = Math.max(1, (panelWidth - 36) / 3);
        int buttonY = panelY + panelHeight - 59;
        var replace = addRenderableWidget(KeysetWidgets.flatButton(panelX + 12, buttonY, buttonWidth, 24,
                Component.translatable("keyset.conflict_replace"), ignored -> resolve(KeysetClient.ConflictResolution.REPLACE),
                KeysetWidgets.ButtonStyle.PRIMARY));
        replace.active = !peers.isEmpty();
        replace.setTooltip(Tooltip.create(Component.translatable("keyset.conflict_replace_tip")));
        var swap = addRenderableWidget(KeysetWidgets.flatButton(panelX + 18 + buttonWidth, buttonY, buttonWidth, 24,
                Component.translatable("keyset.conflict_swap"), ignored -> resolve(KeysetClient.ConflictResolution.SWAP),
                KeysetWidgets.ButtonStyle.ACTION));
        Binding previous = KeysetClient.previousBindingForSwap(actionId);
        swap.active = peers.size() == 1 && previous != null;
        swap.setTooltip(Tooltip.create(previous == null || peers.size() != 1
                ? Component.translatable("keyset.conflict_swap_unavailable")
                : Component.translatable("keyset.conflict_swap_tip", KeysetClient.displayBinding(previous))));
        addRenderableWidget(KeysetWidgets.flatButton(panelX + 24 + buttonWidth * 2, buttonY, buttonWidth, 24,
                Component.translatable("keyset.conflict_keep"), ignored -> onClose(), KeysetWidgets.ButtonStyle.QUIET));
        refreshRows();
    }

    private void refreshRows() {
        for (int slot = 0; slot < peerNames.size(); slot++) {
            int index = scroll + slot;
            StringWidget widget = peerNames.get(slot);
            widget.visible = index < peers.size();
            if (!widget.visible) continue;
            KeyMapping peer = peers.get(index);
            widget.setMessage(KeysetClient.name(peer));
            widget.setTooltip(Tooltip.create(KeysetClient.name(peer).copy().append("\n")
                    .append(KeysetClient.categoryLabel(peer.getCategory())).append(" · ").append(KeysetClient.owner(peer).name())));
        }
    }

    private void resolve(KeysetClient.ConflictResolution resolution) {
        if (observedRevision != KeysetClient.revision()) {
            error = Component.translatable("keyset.conflict_changed");
            init();
            return;
        }
        try {
            KeysetClient.resolveBindingConflict(actionId, resolution,
                    peers.size() == 1 ? peers.getFirst().getName() : null, observedRevision);
            onClose();
        } catch (IOException | IllegalArgumentException failure) {
            error = KeysetClient.operationError(failure);
            init();
        }
    }

    @Override
    public void tick() {
        if (observedRevision != KeysetClient.revision()) {
            error = Component.translatable("keyset.conflict_changed");
            init();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < listTop || mouseY > listBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), Math.max(0, peers.size() - capacity)));
        refreshRows();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        KeysetWidgets.drawCard(graphics, panelX, panelY, panelWidth, panelHeight);
        KeysetWidgets.drawText(graphics, font, title, panelX + 12, panelY + 12, panelWidth - 24, UiTheme.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.conflict_action", KeysetClient.actionName(actionId)),
                panelX + 12, panelY + 34, panelWidth - 24, UiTheme.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.conflict_binding",
                action == null ? Component.empty() : KeysetClient.displayBinding(KeysetClient.binding(action))),
                panelX + 12, panelY + 50, panelWidth - 24, UiTheme.text());
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.conflict_peers", peers.size()),
                panelX + 12, panelY + 70, panelWidth - 24, UiTheme.muted());
        for (int slot = 0; slot < capacity && scroll + slot < peers.size(); slot++) {
            int y = listTop + slot * 32;
            KeyMapping peer = peers.get(scroll + slot);
            graphics.fill(panelX + 8, y, panelX + panelWidth - 8, y + 29, UiTheme.row());
            KeysetWidgets.drawText(graphics, font, KeysetClient.categoryLabel(peer.getCategory()).copy()
                    .append(" · ").append(KeysetClient.owner(peer).name()), panelX + 14, y + 17, panelWidth - 28, UiTheme.muted());
        }
        if (peers.isEmpty()) KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.conflict_gone"),
                panelX + 12, listTop + 8, panelWidth - 24, UiTheme.muted());
        if (error != null) KeysetWidgets.drawText(graphics, font, error, panelX + 12,
                panelY + panelHeight - 23, panelWidth - 24, UiTheme.red());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
}
