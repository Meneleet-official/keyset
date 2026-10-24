package dev.keyset.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.keyset.KeysetClient;
import dev.keyset.profile.Binding;
import dev.keyset.profile.Profile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Compares a saved source profile with the active target and selectively copies assignments. */
public final class ProfileComparisonScreen extends Screen {
    private static final int ROW_HEIGHT = 31;

    private final Screen parent;
    private final String requestedSourceProfileId;
    private final Set<String> selectedActionIds = new LinkedHashSet<>();
    private final List<KeysetWidgets.FlatButton> rowSelectButtons = new ArrayList<>();
    private final List<KeysetWidgets.FlatButton> rowNameButtons = new ArrayList<>();
    private final List<KeysetWidgets.FlatButton> rowSourceButtons = new ArrayList<>();
    private final List<KeysetWidgets.FlatButton> rowTargetButtons = new ArrayList<>();

    private String sourceProfileId;
    private String targetProfileId;
    private String lastActiveProfileId;
    private long observedRevision = Long.MIN_VALUE;
    private List<Profile> profiles = List.of();
    private List<DiffRow> differences = List.of();
    private Profile sourceProfile;
    private Profile targetProfile;
    private KeysetWidgets.FlatButton sourceButton;
    private KeysetWidgets.FlatButton selectAllButton;
    private KeysetWidgets.FlatButton applyButton;
    private int listTop, listBottom, visibleRows, scroll;
    private int panelX, panelWidth, rowLeft, rowWidth, nameX, nameWidth, sourceX, bindingWidth, targetX, targetWidth;
    private Component statusMessage;
    private boolean statusIsError;

    public ProfileComparisonScreen(Screen parent, String sourceProfileId) {
        super(Component.translatable("keyset.compare_title"));
        this.parent = parent;
        this.requestedSourceProfileId = sourceProfileId;
        this.sourceProfileId = sourceProfileId;
    }

    @Override
    protected void init() {
        KeysetClient.ensureLoaded();
        refreshModelIfChanged();
        clearWidgets();
        rowSelectButtons.clear();
        rowNameButtons.clear();
        rowSourceButtons.clear();
        rowTargetButtons.clear();

        panelX = 16;
        panelWidth = Math.max(0, width - 32);
        int innerWidth = Math.max(1, panelWidth - 24);
        rowLeft = panelX + 12;
        rowWidth = innerWidth;
        nameX = rowLeft + 26;
        nameWidth = Math.max(48, innerWidth * 40 / 100);
        sourceX = nameX + nameWidth + 4;
        bindingWidth = Math.max(28, (rowLeft + innerWidth - sourceX - 4) / 2);
        targetX = sourceX + bindingWidth + 4;
        targetWidth = Math.max(24, rowLeft + innerWidth - targetX);

        int panelBottom = height - 47;
        listTop = 128;
        listBottom = Math.max(listTop + 1, panelBottom - 9);
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = clampScroll(scroll, differences.size(), visibleRows);

        int half = Math.max(1, (panelWidth - 42) / 2);
        int sourceWidth = Math.max(70, half);
        sourceButton = addRenderableWidget(KeysetWidgets.flatButton(panelX + 12, 74, sourceWidth, 24,
            Component.empty(), button -> cycleSource(), KeysetWidgets.ButtonStyle.ACTION));
        sourceButton.setTooltip(Tooltip.create(Component.translatable("keyset.compare_source_tip")));

        int backWidth = Math.min(78, Math.max(56, (width - 48) / 5));
        int applyWidth = Math.min(134, Math.max(82, (width - 48) / 3));
        int footerY = height - 34;
        KeysetWidgets.FlatButton backButton = addRenderableWidget(KeysetWidgets.flatButton(width - 16 - backWidth,
            footerY, backWidth, 24, Component.translatable("keyset.compare_back"), button -> onClose(),
            KeysetWidgets.ButtonStyle.QUIET));
        int applyX = backButton.getX() - applyWidth - 6;
        applyButton = addRenderableWidget(KeysetWidgets.flatButton(applyX, footerY, applyWidth, 24,
            Component.translatable("keyset.compare_apply"), button -> applySelection(), KeysetWidgets.ButtonStyle.PRIMARY));

        int selectAllWidth = Math.min(112, Math.max(74, panelWidth / 4));
        selectAllButton = addRenderableWidget(KeysetWidgets.flatButton(panelX + panelWidth - 12 - selectAllWidth,
            106, selectAllWidth, 20, Component.translatable("keyset.compare_select_all"),
            button -> toggleAll(), KeysetWidgets.ButtonStyle.TAB));

        for (int slot = 0; slot < visibleRows; slot++) {
            final int rowSlot = slot;
            int y = listTop + slot * ROW_HEIGHT;
            rowSelectButtons.add(addRenderableWidget(KeysetWidgets.flatButton(rowLeft, y, 22, 26,
                Component.empty(), button -> toggleAt(rowSlot), KeysetWidgets.ButtonStyle.TAB)));
            rowNameButtons.add(addRenderableWidget(KeysetWidgets.flatButton(nameX, y, nameWidth, 26,
                Component.empty(), button -> toggleAt(rowSlot), KeysetWidgets.ButtonStyle.ROW)));
            rowSourceButtons.add(addRenderableWidget(KeysetWidgets.flatButton(sourceX, y, bindingWidth, 26,
                Component.empty(), button -> toggleAt(rowSlot), KeysetWidgets.ButtonStyle.BINDING)));
            rowTargetButtons.add(addRenderableWidget(KeysetWidgets.flatButton(targetX, y, targetWidth, 26,
                Component.empty(), button -> toggleAt(rowSlot), KeysetWidgets.ButtonStyle.BINDING)));
        }
        refreshRows();
    }

    private boolean refreshModelIfChanged() {
        long revision = KeysetClient.revision();
        if (revision == observedRevision) return false;

        boolean hadModel = observedRevision != Long.MIN_VALUE;
        observedRevision = revision;
        profiles = KeysetClient.profiles().profiles();
        Profile active = KeysetClient.profiles().active();
        String activeId = active == null ? null : active.id();

        if (!hadModel || !Objects.equals(activeId, lastActiveProfileId)
                || findProfile(targetProfileId) == null) {
            targetProfileId = activeId;
        }
        lastActiveProfileId = activeId;

        if (findProfile(sourceProfileId) == null || Objects.equals(sourceProfileId, targetProfileId)) {
            sourceProfileId = findProfile(requestedSourceProfileId) != null
                    && !Objects.equals(requestedSourceProfileId, targetProfileId)
                ? requestedSourceProfileId : firstOtherProfileId(targetProfileId);
        }
        sourceProfile = findProfile(sourceProfileId);
        targetProfile = findProfile(targetProfileId);
        rebuildDifferences();
        if (hadModel) {
            selectedActionIds.clear();
            statusMessage = Component.translatable("keyset.compare_changed");
            statusIsError = false;
        }
        return true;
    }

    private void rebuildDifferences() {
        if (sourceProfile == null || targetProfile == null) {
            differences = List.of();
            selectedActionIds.clear();
            return;
        }
        Set<String> actionIds = new LinkedHashSet<>(sourceProfile.bindings().keySet());
        actionIds.addAll(targetProfile.bindings().keySet());
        List<DiffRow> rows = new ArrayList<>();
        for (String actionId : actionIds) {
            Binding source = sourceProfile.bindings().get(actionId);
            Binding target = targetProfile.bindings().get(actionId);
            if (!Objects.equals(source, target)) rows.add(new DiffRow(actionId, source, target));
        }
        rows.sort(Comparator.comparing((DiffRow row) -> KeysetClient.actionName(row.actionId()).getString(),
            String.CASE_INSENSITIVE_ORDER).thenComparing(DiffRow::actionId));
        differences = List.copyOf(rows);
        selectedActionIds.removeIf(id -> differences.stream().noneMatch(row -> row.actionId().equals(id) && row.copyable()));
    }

    private Profile findProfile(String profileId) {
        if (profileId == null) return null;
        return profiles.stream().filter(profile -> profile.id().equals(profileId)).findFirst().orElse(null);
    }

    private String firstOtherProfileId(String excludedId) {
        return profiles.stream().filter(profile -> !profile.id().equals(excludedId))
            .map(Profile::id).findFirst().orElse(null);
    }

    private void cycleSource() {
        if (refreshModelIfChanged()) {
            init();
            return;
        }
        List<Profile> otherProfiles = profiles.stream()
            .filter(profile -> !profile.id().equals(targetProfileId)).toList();
        if (otherProfiles.size() < 2) return;
        int current = -1;
        for (int i = 0; i < otherProfiles.size(); i++) {
            if (otherProfiles.get(i).id().equals(sourceProfileId)) current = i;
        }
        sourceProfileId = otherProfiles.get((current + 1) % otherProfiles.size()).id();
        sourceProfile = findProfile(sourceProfileId);
        selectedActionIds.clear();
        statusMessage = null;
        statusIsError = false;
        rebuildDifferences();
        init();
    }

    private void toggleAt(int slot) {
        if (refreshModelIfChanged()) {
            init();
            return;
        }
        int index = scroll + slot;
        if (index < 0 || index >= differences.size()) return;
        DiffRow row = differences.get(index);
        if (!row.copyable()) return;
        if (!selectedActionIds.add(row.actionId())) selectedActionIds.remove(row.actionId());
        refreshRows();
    }

    private void toggleAll() {
        if (refreshModelIfChanged()) {
            init();
            return;
        }
        List<DiffRow> copyable = differences.stream().filter(DiffRow::copyable).toList();
        boolean allSelected = !copyable.isEmpty() && copyable.stream().allMatch(row -> selectedActionIds.contains(row.actionId()));
        if (allSelected) selectedActionIds.clear();
        else copyable.forEach(row -> selectedActionIds.add(row.actionId()));
        refreshRows();
    }

    private void refreshRows() {
        for (int slot = 0; slot < rowSelectButtons.size(); slot++) {
            int index = scroll + slot;
            boolean visible = index < differences.size() && listTop + slot * ROW_HEIGHT + 26 <= listBottom;
            KeysetWidgets.FlatButton select = rowSelectButtons.get(slot);
            KeysetWidgets.FlatButton name = rowNameButtons.get(slot);
            KeysetWidgets.FlatButton source = rowSourceButtons.get(slot);
            KeysetWidgets.FlatButton target = rowTargetButtons.get(slot);
            select.visible = name.visible = source.visible = target.visible = visible;
            if (!visible) continue;

            DiffRow row = differences.get(index);
            boolean selected = selectedActionIds.contains(row.actionId());
            Component actionName = KeysetClient.actionName(row.actionId());
            Component sourceBinding = bindingLabel(row.sourceBinding());
            Component targetBinding = bindingLabel(row.targetBinding());

            select.active = name.active = source.active = target.active = row.copyable();
            select.setMessage(Component.literal(selected ? "✓" : ""));
            name.setMessage(actionName);
            source.setMessage(sourceBinding);
            target.setMessage(targetBinding);
            select.setTooltip(Tooltip.create(Component.translatable("keyset.compare_toggle_tip", actionName)));
            name.setTooltip(Tooltip.create(actionName));
            source.setTooltip(Tooltip.create(sourceBinding));
            target.setTooltip(Tooltip.create(targetBinding));
            select.setSelected(selected);
            name.setSelected(selected);
            source.setSelected(selected);
            target.setSelected(selected);
        }

        if (sourceButton != null) {
            sourceButton.active = sourceProfile != null && profiles.stream()
                .filter(profile -> !profile.id().equals(targetProfileId)).count() > 1;
            sourceButton.setMessage(sourceProfile == null ? Component.translatable("keyset.compare_no_source")
                : Component.literal(sourceProfile.name() + "  ▾"));
        }
        if (selectAllButton != null) {
            boolean allSelected = differences.stream().anyMatch(DiffRow::copyable) && differences.stream()
                .filter(DiffRow::copyable).allMatch(row -> selectedActionIds.contains(row.actionId()));
            selectAllButton.setMessage(Component.translatable(allSelected
                ? "keyset.compare_select_none" : "keyset.compare_select_all"));
            selectAllButton.active = differences.stream().anyMatch(DiffRow::copyable);
            selectAllButton.setSelected(allSelected);
        }
        if (applyButton != null) {
            applyButton.active = sourceProfile != null && targetProfile != null && !selectedActionIds.isEmpty();
        }
    }

    private Component bindingLabel(Binding binding) {
        return binding == null ? Component.translatable("keyset.compare_missing") : KeysetClient.displayBinding(binding);
    }

    private void applySelection() {
        if (refreshModelIfChanged()) {
            init();
            return;
        }
        if (sourceProfile == null || targetProfile == null || selectedActionIds.isEmpty()) return;
        try {
            KeysetClient.copyProfileBindings(sourceProfileId, Set.copyOf(selectedActionIds), observedRevision);
            minecraft.setScreenAndShow(parent);
        } catch (IOException | IllegalArgumentException exception) {
            statusMessage = KeysetClient.operationError(exception);
            statusIsError = true;
        }
    }

    private int clampScroll(int value, int itemCount, int visibleCount) {
        return Math.max(0, Math.min(Math.max(0, itemCount - visibleCount), value));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (refreshModelIfChanged()) init();

        KeysetWidgets.drawCard(graphics, panelX, 48, panelWidth, Math.max(1, height - 96));
        graphics.text(font, Component.translatable("keyset.compare_title"), 18, 17, KeysetWidgets.TEXT);
        int summaryX = Math.max(150, width - 225);
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_summary",
            differences.size(), selectedActionIds.size()), summaryX, 20,
            Math.max(1, width - 18 - summaryX), KeysetWidgets.MUTED);
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_hint"), 18, 34,
            Math.max(1, width - 36), KeysetWidgets.MUTED);

        int targetLabelX = panelX + 18 + Math.max(70, (panelWidth - 42) / 2) + 10;
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_source_label"),
            panelX + 12, 58, Math.max(1, (panelWidth - 42) / 2), KeysetWidgets.MUTED);
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_target_label"),
            targetLabelX, 58, Math.max(1, panelWidth - (targetLabelX - panelX) - 12), KeysetWidgets.MUTED);
        Component targetName = targetProfile == null ? Component.translatable("keyset.compare_no_target")
            : Component.literal(targetProfile.name());
        KeysetWidgets.drawText(graphics, font, targetName, targetLabelX, 80,
            Math.max(1, panelX + panelWidth - targetLabelX - 14), KeysetWidgets.TEXT);

        graphics.fill(panelX + 12, 101, panelX + panelWidth - 12, 102, KeysetWidgets.BORDER);
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_action_column"),
            nameX + 4, 111, nameWidth - 8, KeysetWidgets.MUTED);
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_source_column"),
            sourceX + 4, 111, bindingWidth - 8, KeysetWidgets.MUTED);
        KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_target_column"),
            targetX + 4, 111, targetWidth - 8, KeysetWidgets.MUTED);

        for (int slot = 0; slot < rowSelectButtons.size(); slot++) {
            int index = scroll + slot;
            if (index >= differences.size() || listTop + slot * ROW_HEIGHT + 26 > listBottom) continue;
            int y = listTop + slot * ROW_HEIGHT - 2;
            graphics.fill(rowLeft - 3, y, rowLeft + rowWidth, y + ROW_HEIGHT - 2,
                (index & 1) == 0 ? KeysetWidgets.ROW : KeysetWidgets.ROW_ALTERNATE);
        }
        if (differences.isEmpty()) {
            Component empty = sourceProfile == null ? Component.translatable("keyset.compare_no_source")
                : Component.translatable("keyset.compare_no_differences");
            KeysetWidgets.drawText(graphics, font, empty, panelX + 18, listTop + 5,
                Math.max(1, panelWidth - 36), KeysetWidgets.MUTED);
        }
        int copyableCount = (int) differences.stream().filter(DiffRow::copyable).count();
        if (copyableCount < differences.size()) {
            KeysetWidgets.drawText(graphics, font, Component.translatable("keyset.compare_missing_source_hint"),
                panelX + 12, listBottom + 1, Math.max(1, panelWidth - 24), KeysetWidgets.MUTED);
        }
        if (statusMessage != null) {
            KeysetWidgets.drawText(graphics, font, statusMessage, 18, height - 27,
                Math.max(1, applyButton.getX() - 24), statusIsError ? KeysetWidgets.RED : KeysetWidgets.MUTED);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= listTop && mouseY <= listBottom) {
            int next = clampScroll(scroll - (int) Math.signum(scrollY) * 2, differences.size(), visibleRows);
            if (next != scroll) {
                scroll = next;
                refreshRows();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private record DiffRow(String actionId, Binding sourceBinding, Binding targetBinding) {
        private boolean copyable() { return sourceBinding != null; }
    }
}
