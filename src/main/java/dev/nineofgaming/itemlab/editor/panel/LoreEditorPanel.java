package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.list.EditorListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.text.RichTextLine;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import dev.nineofgaming.itemlab.editor.ui.text.TextFormatUi;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputText;

/**
 * State and rendering for the display-name/lore editor.
 */
public final class LoreEditorPanel {
    private static final float TEXT_OPTIONS_POPUP_WIDTH = 360.0f;
    private static final AtomicLong NEXT_LORE_ROW_ID = new AtomicLong();

    private final ItemEditor host;
    private final ItemLabConfig preferences;
    private final List<RichTextLine> rows = new ArrayList<>();
    private final Map<RichTextLine, Long> loreRowIds = new IdentityHashMap<>();
    private TextInputFormat textFormat;
    private RichTextLine displayName;
    private boolean disableDefaultItalics;
    private boolean useWhiteDefaultLoreColor;
    private boolean hideLeadingFormattingPrefixes;
    private RichTextLine baselineDisplayName;
    private final List<RichTextLine> baselineRows = new ArrayList<>();
    private boolean baselineNameDisableDefaultItalics;
    private boolean baselineLoreDisableDefaultItalics;
    private boolean baselineUseWhiteDefaultLoreColor;
    private long focusLoreRowId;
    private boolean focusAddLoreButton;
    private boolean focusDisplayName;
    private OrderedListDragDrop.Scope loreDragScope = OrderedListDragDrop.newScope("lore");

    public LoreEditorPanel(ItemEditor host) {
        this.host = host;
        preferences = ItemLabConfig.get();
        textFormat = preferences.textInputFormat();
        displayName = RichTextLine.createEmpty(textFormat);
        disableDefaultItalics = preferences.removeDefaultItalics();
        useWhiteDefaultLoreColor = preferences.useWhiteDefaultLoreColor();
        hideLeadingFormattingPrefixes = preferences.hideLeadingFormattingPrefixes();
        baselineDisplayName = displayName.copy();
        baselineNameDisableDefaultItalics = disableDefaultItalics;
        baselineLoreDisableDefaultItalics = disableDefaultItalics;
        baselineUseWhiteDefaultLoreColor = useWhiteDefaultLoreColor;
    }

    public void requestPendingFocus() {
        focusLoreRowId = 0L;
        focusAddLoreButton = false;
        focusDisplayName = !displayName.validationError().isBlank();
        if (focusDisplayName) return;
        for (RichTextLine row : rows) {
            if (!row.validationError().isBlank()) {
                focusLoreRowId = loreRowId(row);
                return;
            }
        }
        if (isNameDirty() || rows.isEmpty()) focusDisplayName = true;
        else focusLoreRowId = loreRowId(rows.getFirst());
    }

    public boolean isDirty() {
        return isNameDirty() || isLoreDirty();
    }

    public boolean isNameDirty() {
        return disableDefaultItalics != baselineNameDisableDefaultItalics
            || !displayName.sameEditorState(baselineDisplayName);
    }

    public boolean isLoreDirty() {
        return disableDefaultItalics != baselineLoreDisableDefaultItalics
            || useWhiteDefaultLoreColor != baselineUseWhiteDefaultLoreColor
            || !sameLines(rows, baselineRows);
    }

    public HistoryState historyState() {
        return new HistoryState(captureState());
    }

    public void restoreHistoryState(HistoryState state) {
        restoreState(state.state);
    }

    public void draw(Minecraft client) {
        ImGui.spacing();
        boolean openTextOptions = false;
        if (ImGui.beginTable("name_lore_toolbar", 2, ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX)) {
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Formatting", ImGuiTableColumnFlags.WidthFixed, textOptionsButtonWidth());
            ImGui.tableNextColumn();
            EditorUi.drawSaveDiscardActions(isDirty(), () -> save(client, true), () -> {
                sync(host.draft());
                host.setStatus(true, tr("message.discarded"));
            });
            EditorUi.sameLineIfFits(EditorIcons.buttonSize());
            drawAddLoreButton("top", false);
            ImGui.tableNextColumn();
            openTextOptions = drawTextOptionsButton("name_and_lore");
            ImGui.endTable();
        }
        if (openTextOptions) {
            ImGui.openPopup(textOptionsPopupId("name_and_lore"));
        }
        EditorUi.beginScrollableChild("lore_rows", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            host.drawTooltipPreview(client, livePreviewStack(), "name_and_lore");
            ImGui.spacing();
            drawNameEditor();
            ImGui.spacing();
            drawLoreSectionHeading();
            int actionIndex = -1;
            EditorUi.ListAction action = EditorUi.ListAction.NONE;
            OrderedListDragDrop.ReorderRequest reorderRequest = null;
            for (int index = 0; index < rows.size(); index++) {
                RichTextLine row = rows.get(index);
                long rowId = loreRowId(row);
                ImGui.pushID("lore_row_" + rowId);
                EditorUi.ListAction rowAction = drawLoreRow(index, row, rowId);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.popID();
                OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                    loreDragScope,
                    rowId,
                    index
                );
                if (reorderRequest == null) reorderRequest = rowRequest;
                ImGui.spacing();
            }
            if (reorderRequest != null) applyLoreReorder(reorderRequest);
            else applyLoreAction(actionIndex, action);
            if (rows.isEmpty()) EditorUi.drawEmptyState(tr("lore.empty"));
            drawAddLoreButton("bottom", true);
            OrderedListDragDrop.autoScrollCurrentWindow(loreDragScope);
        } finally {
            ImGui.endChild();
        }
        drawTextOptionsPopup(true, true, "name_and_lore");
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (type == DataComponents.CUSTOM_NAME) {
            drawNameComponent(client);
        } else if (type == DataComponents.LORE) {
            drawLoreComponent(client);
        }
    }

    private void drawNameComponent(Minecraft client) {
        ImGui.spacing();
        boolean openTextOptions = false;
        if (ImGui.beginTable("custom_name_toolbar", 2, ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX)) {
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Formatting", ImGuiTableColumnFlags.WidthFixed, textOptionsButtonWidth());
            ImGui.tableNextColumn();
            EditorUi.drawSaveDiscardActions(isNameDirty(), () -> saveName(client, true), () -> {
                syncName(host.draft());
                host.setStatus(true, tr("message.discarded"));
            });
            ImGui.tableNextColumn();
            openTextOptions = drawTextOptionsButton("custom_name");
            ImGui.endTable();
        }
        if (openTextOptions) {
            ImGui.openPopup(textOptionsPopupId("custom_name"));
        }
        host.drawTooltipPreview(client, livePreviewStack(), "custom_name_component");
        ImGui.spacing();
        drawNameEditor();
        drawTextOptionsPopup(true, false, "custom_name");
    }

    private void drawLoreComponent(Minecraft client) {
        ImGui.spacing();
        boolean openTextOptions = false;
        if (ImGui.beginTable("lore_component_toolbar", 2, ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX)) {
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Formatting", ImGuiTableColumnFlags.WidthFixed, textOptionsButtonWidth());
            ImGui.tableNextColumn();
            EditorUi.drawSaveDiscardActions(isLoreDirty(), () -> saveLore(client, true), () -> {
                syncLore(host.draft());
                host.setStatus(true, tr("message.discarded"));
            });
            EditorUi.sameLineIfFits(EditorIcons.buttonSize());
            drawAddLoreButton("top", false);
            ImGui.tableNextColumn();
            openTextOptions = drawTextOptionsButton("lore_component");
            ImGui.endTable();
        }
        if (openTextOptions) {
            ImGui.openPopup(textOptionsPopupId("lore_component"));
        }
        EditorUi.beginScrollableChild("component_lore_rows", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            host.drawTooltipPreview(client, livePreviewStack(), "lore_component");
            ImGui.spacing();
            drawLoreSectionHeading();
            int actionIndex = -1;
            EditorUi.ListAction action = EditorUi.ListAction.NONE;
            OrderedListDragDrop.ReorderRequest reorderRequest = null;
            for (int index = 0; index < rows.size(); index++) {
                RichTextLine row = rows.get(index);
                long rowId = loreRowId(row);
                ImGui.pushID("lore_row_" + rowId);
                EditorUi.ListAction rowAction = drawLoreRow(index, row, rowId);
                if (rowAction != EditorUi.ListAction.NONE) {
                    actionIndex = index;
                    action = rowAction;
                }
                ImGui.popID();
                OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                    loreDragScope,
                    rowId,
                    index
                );
                if (reorderRequest == null) reorderRequest = rowRequest;
                ImGui.spacing();
            }
            if (reorderRequest != null) applyLoreReorder(reorderRequest);
            else applyLoreAction(actionIndex, action);
            if (rows.isEmpty()) EditorUi.drawEmptyState(tr("lore.empty"));
            drawAddLoreButton("bottom", true);
            OrderedListDragDrop.autoScrollCurrentWindow(loreDragScope);
        } finally {
            ImGui.endChild();
        }
        drawTextOptionsPopup(false, true, "lore_component");
    }

    public Component liveNamePreview() {
        ItemStack preview = livePreviewStack();
        return preview.isEmpty() ? Component.literal(tr("empty.item")) : preview.getStyledHoverName();
    }

    public void sync(ItemStack draft) {
        syncName(draft);
        syncLore(draft);
    }

    public void syncName(ItemStack draft) {
        focusDisplayName = false;
        displayName = RichTextLine.from(draft.get(DataComponents.CUSTOM_NAME), textFormat);
        baselineDisplayName = displayName.copy();
        baselineNameDisableDefaultItalics = disableDefaultItalics;
    }

    public void syncLore(ItemStack draft) {
        rows.clear();
        ItemLore lore = draft.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                rows.add(RichTextLine.from(line, textFormat));
            }
        }
        resetLoreRowUi();
        replaceLines(baselineRows, rows);
        baselineLoreDisableDefaultItalics = disableDefaultItalics;
        baselineUseWhiteDefaultLoreColor = useWhiteDefaultLoreColor;
    }

    public void applyName(ItemStack edited) {
        if (!isNameDirty()) {
            return;
        }
        if (displayName.isBlank()) {
            edited.remove(DataComponents.CUSTOM_NAME);
        } else {
            edited.set(DataComponents.CUSTOM_NAME, displayName.toComponent(disableDefaultItalics));
        }
    }

    public ItemStack pendingStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        applyName(edited);
        if (isLoreDirty()) {
            if (rows.isEmpty()) edited.remove(DataComponents.LORE);
            else edited.set(DataComponents.LORE, new ItemLore(savedLoreComponents()));
        }
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    public void restorePending(ItemStack state, ItemStack baseline, TextOptions options) {
        disableDefaultItalics = options.disableDefaultItalics();
        useWhiteDefaultLoreColor = options.useWhiteDefaultLoreColor();
        preferences.setRemoveDefaultItalics(disableDefaultItalics);
        preferences.setUseWhiteDefaultLoreColor(useWhiteDefaultLoreColor);

        displayName = RichTextLine.from(state.get(DataComponents.CUSTOM_NAME), textFormat);
        rows.clear();
        ItemLore stateLore = state.get(DataComponents.LORE);
        if (stateLore != null) {
            stateLore.lines().forEach(line -> rows.add(RichTextLine.from(line, textFormat)));
        }

        baselineDisplayName = RichTextLine.from(baseline.get(DataComponents.CUSTOM_NAME), textFormat);
        baselineRows.clear();
        ItemLore baselineLore = baseline.get(DataComponents.LORE);
        if (baselineLore != null) {
            baselineLore.lines().forEach(line -> baselineRows.add(RichTextLine.from(line, textFormat)));
        }
        baselineNameDisableDefaultItalics = disableDefaultItalics;
        baselineLoreDisableDefaultItalics = disableDefaultItalics;
        baselineUseWhiteDefaultLoreColor = useWhiteDefaultLoreColor;
        resetLoreRowUi();
    }

    public boolean save(Minecraft client, boolean announce) {
        if (rows.size() > ItemLore.MAX_LINES) {
            host.setStatus(false, tr("error.lore_limit", ItemLore.MAX_LINES));
            return false;
        }
        try {
            boolean saveLore = isLoreDirty();
            ItemStack edited = host.draft().copy();
            applyName(edited);
            if (saveLore) {
                if (rows.isEmpty()) {
                    edited.remove(DataComponents.LORE);
                } else {
                    List<Component> lines = savedLoreComponents();
                    edited.set(DataComponents.LORE, new ItemLore(lines));
                }
            }
            host.acceptSectionDraft(client, edited);
            sync(edited);
            if (announce) {
                host.setStatus(true, tr("message.saved"));
            }
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private boolean saveName(Minecraft client, boolean announce) {
        try {
            ItemStack edited = host.draft().copy();
            applyName(edited);
            host.acceptSectionDraft(client, edited);
            syncName(edited);
            if (announce) {
                host.setStatus(true, tr("message.saved"));
            }
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private boolean saveLore(Minecraft client, boolean announce) {
        if (rows.size() > ItemLore.MAX_LINES) {
            host.setStatus(false, tr("error.lore_limit", ItemLore.MAX_LINES));
            return false;
        }
        try {
            ItemStack edited = host.draft().copy();
            if (rows.isEmpty()) {
                edited.remove(DataComponents.LORE);
            } else {
                List<Component> lines = savedLoreComponents();
                edited.set(DataComponents.LORE, new ItemLore(lines));
            }
            host.acceptSectionDraft(client, edited);
            syncLore(edited);
            if (announce) {
                host.setStatus(true, tr("message.saved"));
            }
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    private EditorUi.ListAction drawLoreRow(int index, RichTextLine row, long rowId) {
        boolean requestFocus = focusLoreRowId == rowId;
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "lore_card",
            EditorUi.identifiedTitle(
                tr("lore.line", index + 1),
                row.toPreviewComponent(
                    disableDefaultItalics,
                    useWhiteDefaultLoreColor
                ).getString()
            ),
            ImGuiTableFlags.SizingStretchProp,
            index,
            rows.size(),
            true,
            rows.size() < ItemLore.MAX_LINES,
            rows.size() < ItemLore.MAX_LINES,
            requestFocus,
            loreDragScope,
            rowId
        );
        if (card.focusApplied()) focusLoreRowId = 0L;
        if (card.expanded()) {
            if (requestFocus) ImGui.setKeyboardFocusHere();
            drawFormattedTextField("##lore_text", row, true);
            EditorUi.endCard();
        }
        return card.action();
    }

    private void applyLoreAction(int index, EditorUi.ListAction action) {
        if (index < 0 || action == EditorUi.ListAction.NONE) return;
        RichTextLine removed = action == EditorUi.ListAction.REMOVE && index < rows.size()
            ? rows.get(index)
            : null;
        EditorListMutations.Mutation<RichTextLine> mutation = EditorListMutations.apply(
            rows,
            index,
            action,
            RichTextLine::copy,
            () -> RichTextLine.createEmpty(textFormat)
        );
        if (!mutation.changed()) return;
        if (removed != null) loreRowIds.remove(removed);
        if (mutation.focusRow() == null) {
            focusLoreRowId = 0L;
            focusAddLoreButton = true;
        } else {
            focusLoreRowId = loreRowId(mutation.focusRow());
        }
    }

    private void applyLoreReorder(OrderedListDragDrop.ReorderRequest request) {
        if (request == null || !loreDragScope.equals(request.scope())) return;
        EditorListMutations.Mutation<RichTextLine> mutation = EditorListMutations.reorder(
            rows,
            request,
            this::loreRowId
        );
        if (!mutation.changed()) return;
        focusLoreRowId = loreRowId(mutation.focusRow());
    }

    private void drawAddLoreButton(String placement, boolean acceptFocusRequest) {
        ImGui.beginDisabled(rows.size() >= ItemLore.MAX_LINES);
        if (acceptFocusRequest && focusAddLoreButton) {
            ImGui.setKeyboardFocusHere();
            focusAddLoreButton = false;
        }
        if (EditorIcons.button("add_lore_line_" + placement, EditorIcons.Icon.ADD, tr("lore.add_line"))) {
            RichTextLine added = RichTextLine.createEmpty(textFormat);
            rows.add(added);
            focusLoreRowId = loreRowId(added);
        }
        ImGui.endDisabled();
    }

    private long loreRowId(RichTextLine row) {
        return loreRowIds.computeIfAbsent(row, ignored -> NEXT_LORE_ROW_ID.incrementAndGet());
    }

    private void resetLoreRowUi() {
        loreRowIds.clear();
        focusLoreRowId = 0L;
        focusAddLoreButton = false;
        loreDragScope = OrderedListDragDrop.newScope("lore");
    }

    private ItemStack livePreviewStack() {
        ItemStack preview = host.draft().copy();
        applyLiveName(preview);
        if (isLoreDirty()) {
            if (rows.isEmpty()) {
                preview.remove(DataComponents.LORE);
            } else {
                preview.set(DataComponents.LORE, new ItemLore(previewLoreComponents()));
            }
        }
        return preview;
    }

    private void drawNameEditor() {
        if (EditorUi.beginCard("name_format_card", 1, ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableNextColumn();
            ImGui.text(tr("lore.display_name"));
            host.drawDiamondFireComponentHint(DataComponents.CUSTOM_NAME);
            if (focusDisplayName) {
                ImGui.setScrollHereY(0.25f);
                ImGui.setKeyboardFocusHere();
                focusDisplayName = false;
            }
            drawFormattedTextField("##display_name", displayName, false);
            EditorUi.endCard();
        }
    }

    private void drawFormattedTextField(String id, RichTextLine line, boolean lore) {
        String validationError = line.validationError();
        if (!validationError.isBlank()) ItemEditorTheme.pushInvalidField();
        ImGui.setNextItemWidth(-1.0f);
        boolean changed = inputText(
            id,
            line.editorSource(
                hideLeadingFormattingPrefixes,
                lore && useWhiteDefaultLoreColor
            )
        );
        if (!validationError.isBlank()) ItemEditorTheme.popInvalidField();
        if (changed) line.commitEditorSource();
        validationError = line.validationError();
        if (!validationError.isBlank()) {
            ItemEditorTheme.drawFieldProblem(tr("lore.validation.text", line.format().label(), validationError));
        }
    }

    private void drawLoreSectionHeading() {
        ImGui.text(tr("lore.lines_heading", rows.size()));
        host.drawDiamondFireComponentHint(DataComponents.LORE);
        ImGui.separator();
    }

    private float textOptionsButtonWidth() {
        return TextFormatUi.buttonWidth(textFormat);
    }

    private boolean drawTextOptionsButton(String id) {
        String options = tr(
            "text.options_tooltip",
            disableDefaultItalics
                ? tr("text.italics.removed")
                : tr("text.italics.unchanged"),
            useWhiteDefaultLoreColor
                ? tr("text.lore_color.white")
                : tr("text.lore_color.unchanged"),
            hideLeadingFormattingPrefixes
                ? tr("text.prefixes.hidden")
                : tr("text.prefixes.shown")
        );
        return TextFormatUi.button("text_options_" + id, textFormat, options);
    }

    private String textOptionsPopupId(String id) {
        return "text_options_popup##" + id;
    }

    private void drawTextOptionsPopup(boolean editsName, boolean editsLore, String id) {
        String popupId = textOptionsPopupId(id);
        // Wrapped help text cannot establish a useful auto-fit width on the first
        // frame. A stable width prevents ImGui from turning the popup tall on the
        // next frame and clamping it to the top of the viewport.
        ImGui.setNextWindowSizeConstraints(
            TEXT_OPTIONS_POPUP_WIDTH,
            0.0f,
            TEXT_OPTIONS_POPUP_WIDTH,
            Float.MAX_VALUE
        );
        if (!ImGui.beginPopup(popupId)) {
            return;
        }
        ImGui.text(tr("text.options_title"));
        ImGui.separator();
        TextFormatUi.drawSelector("text_input_format_" + id, textFormat, host::switchTextInputFormat);
        ImGui.spacing();

        ImBoolean nonItalic = new ImBoolean(disableDefaultItalics);
        if (ImGui.checkbox(tr("text.remove_default_italics"), nonItalic)) {
            setRemoveDefaultItalics(nonItalic.get(), editsName, editsLore);
        }
        EditorUi.textDisabledWrapped(
            tr(
                "text.remove_default_italics.help",
                textFormat == TextInputFormat.MINI_MESSAGE
                    ? tr("text.markers.italic_tags")
                    : tr("text.markers.italic_codes", textFormat.codeMarker())
            )
        );
        if (editsLore) {
            ImGui.spacing();
            ImBoolean whiteLore = new ImBoolean(useWhiteDefaultLoreColor);
            if (ImGui.checkbox(tr("text.white_default_lore"), whiteLore)) {
                setUseWhiteDefaultLoreColor(whiteLore.get());
            }
            EditorUi.textDisabledWrapped(
                tr(
                    "text.white_default_lore.help",
                    textFormat == TextInputFormat.MINI_MESSAGE
                        ? tr("text.markers.color_tags")
                        : tr("text.markers.formatting_codes")
                )
            );
        }
        ImGui.spacing();
        ImBoolean hidePrefixes = new ImBoolean(hideLeadingFormattingPrefixes);
        if (ImGui.checkbox(tr("text.hide_leading_prefixes"), hidePrefixes)) {
            setHideLeadingFormattingPrefixes(hidePrefixes.get());
        }
        EditorUi.textDisabledWrapped(tr("text.hide_leading_prefixes.help"));
        ImGui.endPopup();
    }

    public void setRemoveDefaultItalics(boolean remove) {
        setRemoveDefaultItalics(remove, true, true);
    }

    private void setRemoveDefaultItalics(boolean remove, boolean editsName, boolean editsLore) {
        if (disableDefaultItalics == remove) return;
        disableDefaultItalics = remove;
        preferences.setRemoveDefaultItalics(remove);
        if (!editsName) baselineNameDisableDefaultItalics = remove;
        if (!editsLore) baselineLoreDisableDefaultItalics = remove;
    }

    public void setUseWhiteDefaultLoreColor(boolean useWhite) {
        if (useWhiteDefaultLoreColor == useWhite) return;
        useWhiteDefaultLoreColor = useWhite;
        preferences.setUseWhiteDefaultLoreColor(useWhite);
    }

    public void setHideLeadingFormattingPrefixes(boolean hide) {
        if (hideLeadingFormattingPrefixes == hide) return;
        hideLeadingFormattingPrefixes = hide;
        preferences.setHideLeadingFormattingPrefixes(hide);
    }

    public Runnable prepareTextFormatChange(TextInputFormat targetFormat) {
        List<RichTextLine> allLines = new ArrayList<>(rows.size() + 1);
        allLines.add(displayName);
        allLines.addAll(rows);
        List<String> converted = new ArrayList<>(allLines.size());
        for (RichTextLine line : allLines) {
            converted.add(line.convertedSource(targetFormat));
        }
        return () -> {
            for (int index = 0; index < allLines.size(); index++) {
                allLines.get(index).switchFormat(targetFormat, converted.get(index));
            }
            baselineDisplayName = baselineDisplayName.convertedCopy(targetFormat);
            List<RichTextLine> convertedBaselineRows = baselineRows.stream()
                .map(line -> line.convertedCopy(targetFormat))
                .toList();
            replaceLines(baselineRows, convertedBaselineRows);
            textFormat = targetFormat;
        };
    }

    private void applyLiveName(ItemStack preview) {
        if (!isNameDirty()) {
            return;
        }
        if (displayName.isBlank()) {
            preview.remove(DataComponents.CUSTOM_NAME);
        } else {
            preview.set(
                DataComponents.CUSTOM_NAME,
                displayName.toPreviewComponent(disableDefaultItalics)
            );
        }
    }

    private List<Component> savedLoreComponents() {
        return rows.stream()
            .map(row -> row.toComponent(disableDefaultItalics, useWhiteDefaultLoreColor))
            .toList();
    }

    private List<Component> previewLoreComponents() {
        return rows.stream()
            .map(row -> row.toPreviewComponent(disableDefaultItalics, useWhiteDefaultLoreColor))
            .toList();
    }

    private EditorState captureState() {
        return new EditorState(
            displayName.copy(),
            copyLines(rows),
            disableDefaultItalics,
            useWhiteDefaultLoreColor,
            baselineDisplayName.copy(),
            copyLines(baselineRows),
            baselineNameDisableDefaultItalics,
            baselineLoreDisableDefaultItalics,
            baselineUseWhiteDefaultLoreColor
        );
    }

    private void restoreState(EditorState state) {
        displayName = state.displayName().copy();
        replaceLines(rows, state.rows());
        resetLoreRowUi();
        disableDefaultItalics = state.disableDefaultItalics();
        useWhiteDefaultLoreColor = state.useWhiteDefaultLoreColor();
        baselineDisplayName = state.baselineDisplayName().copy();
        replaceLines(baselineRows, state.baselineRows());
        baselineNameDisableDefaultItalics = state.baselineNameDisableDefaultItalics();
        baselineLoreDisableDefaultItalics = state.baselineLoreDisableDefaultItalics();
        baselineUseWhiteDefaultLoreColor = state.baselineUseWhiteDefaultLoreColor();
        preferences.setRemoveDefaultItalics(disableDefaultItalics);
        preferences.setUseWhiteDefaultLoreColor(useWhiteDefaultLoreColor);
    }

    private static List<RichTextLine> copyLines(List<RichTextLine> source) {
        return source.stream().map(RichTextLine::copy).toList();
    }

    private static void replaceLines(List<RichTextLine> destination, List<RichTextLine> source) {
        destination.clear();
        destination.addAll(copyLines(source));
    }

    private static boolean sameLines(List<RichTextLine> first, List<RichTextLine> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).sameEditorState(second.get(index))) return false;
        }
        return true;
    }

    private record EditorState(
        RichTextLine displayName,
        List<RichTextLine> rows,
        boolean disableDefaultItalics,
        boolean useWhiteDefaultLoreColor,
        RichTextLine baselineDisplayName,
        List<RichTextLine> baselineRows,
        boolean baselineNameDisableDefaultItalics,
        boolean baselineLoreDisableDefaultItalics,
        boolean baselineUseWhiteDefaultLoreColor
    ) {
        private boolean sameState(EditorState other) {
            return displayName.sameEditorState(other.displayName)
                && sameLines(rows, other.rows)
                && disableDefaultItalics == other.disableDefaultItalics
                && useWhiteDefaultLoreColor == other.useWhiteDefaultLoreColor
                && baselineDisplayName.sameEditorState(other.baselineDisplayName)
                && sameLines(baselineRows, other.baselineRows)
                && baselineNameDisableDefaultItalics == other.baselineNameDisableDefaultItalics
                && baselineLoreDisableDefaultItalics == other.baselineLoreDisableDefaultItalics
                && baselineUseWhiteDefaultLoreColor == other.baselineUseWhiteDefaultLoreColor;
        }
    }

    public record TextOptions(boolean disableDefaultItalics, boolean useWhiteDefaultLoreColor) {
    }

    public static final class HistoryState {
        private final EditorState state;

        private HistoryState(EditorState state) {
            this.state = state;
        }

        public boolean sameState(HistoryState other) {
            return other != null && state.sameState(other.state);
        }
    }
}
