package dev.nineofgaming.itemlab.editor.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.network.chat.FormattedText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Stateless layout and presentation helpers shared by editor panels.
 * Usage patterns and reference panels are documented in
 * {@code docs/editor-ui-reference-patterns.md}.
 */
public final class EditorUi {
    private static final float PREVIEW_FORM_SPLIT_MIN_WIDTH = 690.0f;
    private static final List<String> SUMMARY_KEYS = List.of(
        "id", "type", "name", "attribute", "pattern", "effect",
        "item", "sound", "blocks", "items", "value"
    );
    private static final Set<Integer> MEASURED_CARDS = new HashSet<>();
    private static final Set<Integer> INITIALIZED_COLLAPSIBLES = new HashSet<>();
    private static final Set<Integer> INITIALIZED_SCROLLS = new HashSet<>();
    private static long collapsibleSessionId;

    private EditorUi() {
    }

    public static void setTooltip(String text) {
        ImGui.setTooltip(text);
    }

    /**
     * Shows value-shaped syntax help for the immediately preceding control.
     */
    public static void showSyntaxExampleTooltip(String example) {
        if (example != null && !example.isBlank() && ImGui.isItemHovered()) {
            setTooltip(tr("field.syntax_example", example));
        }
    }

    /**
     * Draws a tooltip that preserves newlines and wraps long translated descriptions.
     */
    public static void setWrappedTooltip(String text) {
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 34.0f);
        try {
            ImGui.textUnformatted(text.replaceAll("(?<=[.!?])\\h+", "\n"));
        } finally {
            ImGui.popTextWrapPos();
            ImGui.endTooltip();
        }
    }

    /**
     * Selects the item session used to isolate collapsible state from sibling editor sessions.
     */
    public static void setCollapsibleSessionId(long sessionId) {
        collapsibleSessionId = sessionId;
    }

    public static boolean beginCard(String id, int columns, int flags) {
        return beginCard(id, columns, flags, false);
    }

    /**
     * Submit and reveal a focus target even when its previously measured card is off screen.
     */
    public static boolean beginCard(String id, int columns, int flags, boolean revealForFocus) {
        if (revealForFocus) ImGui.setScrollFromPosY(ImGui.getCursorPosY(), 0.25f);
        String surfaceId = id + "_surface";
        boolean firstMeasurement = MEASURED_CARDS.add(ImGui.getID(surfaceId));
        ItemEditorTheme.pushCard();
        boolean childVisible = ImGui.beginChild(
            surfaceId,
            0.0f,
            0.0f,
            ImGuiChildFlags.Borders
                | ImGuiChildFlags.AlwaysUseWindowPadding
                | ImGuiChildFlags.AutoResizeY
                | (firstMeasurement || revealForFocus ? ImGuiChildFlags.AlwaysAutoResize : 0),
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
        );
        if (!childVisible) {
            ImGui.endChild();
            ItemEditorTheme.popCard();
            return false;
        }
        boolean opened = ImGui.beginTable(id + "_layout", columns, flags | ImGuiTableFlags.RowBg);
        if (!opened) {
            ImGui.endChild();
            ItemEditorTheme.popCard();
        }
        return opened;
    }

    public static void endCard() {
        ImGui.endTable();
        ImGui.endChild();
        ItemEditorTheme.popCard();
    }

    /**
     * Begins a one-column card whose contents can be collapsed from its title row.
     */
    public static boolean beginCollapsibleCard(String id, String title, int flags) {
        return beginCollapsibleCard(id, title, flags, true);
    }

    /**
     * Begins a one-column card with an explicit expansion state for its first visit.
     */
    public static boolean beginCollapsibleCard(
        String id,
        String title,
        int flags,
        boolean initiallyExpanded
    ) {
        return beginCollapsibleCard(id, title, flags, initiallyExpanded, false);
    }

    public static boolean beginCollapsibleCard(
        String id,
        String title,
        int flags,
        boolean initiallyExpanded,
        boolean revealForFocus
    ) {
        if (!beginCard(id, 1, flags, revealForFocus)) {
            return false;
        }
        ImGui.tableNextColumn();
        boolean expanded = collapsibleHeader(id, title, initiallyExpanded, revealForFocus);
        if (!expanded) {
            endCard();
            return false;
        }
        ImGui.spacing();
        return true;
    }

    /**
     * Begins a collapsible entry card with list controls kept in its always-visible header.
     */
    public static ListCardResult beginCollapsibleListCard(
        String id,
        String title,
        int flags,
        int index,
        int size,
        boolean ordered,
        boolean canDuplicate
    ) {
        return beginCollapsibleListCard(
            id,
            title,
            flags,
            index,
            size,
            ordered,
            canDuplicate,
            false,
            false
        );
    }

    /**
     * Begins a list card and optionally transfers keyboard focus to its header.
     */
    public static ListCardResult beginCollapsibleListCard(
        String id,
        String title,
        int flags,
        int index,
        int size,
        boolean ordered,
        boolean canDuplicate,
        boolean requestFocus
    ) {
        return beginCollapsibleListCard(
            id,
            title,
            flags,
            index,
            size,
            ordered,
            canDuplicate,
            false,
            requestFocus
        );
    }

    /**
     * Begins a list card with optional insertion and focus-aware row actions.
     */
    public static ListCardResult beginCollapsibleListCard(
        String id,
        String title,
        int flags,
        int index,
        int size,
        boolean ordered,
        boolean canDuplicate,
        boolean canInsertAfter,
        boolean requestFocus
    ) {
        return beginCollapsibleListCard(
            id,
            title,
            flags,
            index,
            size,
            ordered,
            canDuplicate,
            canInsertAfter,
            requestFocus,
            null,
            0L
        );
    }

    /**
     * Begins an ordered list card with a stable-identity drag handle in its header.
     */
    public static ListCardResult beginCollapsibleListCard(
        String id,
        String title,
        int flags,
        int index,
        int size,
        boolean ordered,
        boolean canDuplicate,
        boolean canInsertAfter,
        boolean requestFocus,
        OrderedListDragDrop.Scope dragScope,
        long stableRowId
    ) {
        boolean draggable = ordered && dragScope != null;
        if (!beginCard(id, 1, flags, requestFocus)) {
            return new ListCardResult(false, ListAction.NONE, false);
        }
        ImGui.tableNextColumn();

        boolean expanded;
        ListAction action;
        float expandedActionsWidth = listActionsWidth(ordered, canDuplicate, canInsertAfter);
        float handleWidth = draggable
            ? EditorIcons.smallButtonSize() + ImGui.getStyle().getItemSpacingX()
            : 0.0f;
        boolean compactActions = ImGui.getContentRegionAvailX()
            < expandedActionsWidth + handleWidth + 140.0f;
        float actionsWidth = compactActions
            ? EditorIcons.smallButtonSize()
            : expandedActionsWidth;
        if (ImGui.beginTable(
            id + "_header",
            draggable ? 3 : 2,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
        )) {
            if (draggable) {
                ImGui.tableSetupColumn(
                    "Reorder",
                    ImGuiTableColumnFlags.WidthFixed,
                    EditorIcons.smallButtonSize()
                );
            }
            ImGui.tableSetupColumn("Entry", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn(
                "Actions",
                ImGuiTableColumnFlags.WidthFixed,
                actionsWidth
            );
            if (draggable) {
                ImGui.tableNextColumn();
                OrderedListDragDrop.drawHandle("reorder", dragScope, stableRowId, title);
            }
            ImGui.tableNextColumn();
            if (requestFocus) ImGui.setKeyboardFocusHere();
            expanded = collapsibleHeader(id, title, true, requestFocus);
            if (requestFocus) ImGui.setScrollHereY(0.25f);
            ImGui.tableNextColumn();
            action = compactActions
                ? drawCompactListActions(id, index, size, ordered, canDuplicate, canInsertAfter)
                : drawListActions(index, size, ordered, canDuplicate, canInsertAfter);
            ImGui.endTable();
        } else {
            if (draggable) {
                OrderedListDragDrop.drawHandle("reorder", dragScope, stableRowId, title);
                ImGui.sameLine();
            }
            if (requestFocus) ImGui.setKeyboardFocusHere();
            expanded = collapsibleHeader(id, title, true, requestFocus);
            if (requestFocus) ImGui.setScrollHereY(0.25f);
            action = drawListActions(index, size, ordered, canDuplicate, canInsertAfter);
        }

        if (!expanded) {
            endCard();
            return new ListCardResult(false, action, requestFocus);
        }
        ImGui.spacing();
        return new ListCardResult(true, action, requestFocus);
    }

    /**
     * Recovery requests reveal controls even when their section was collapsed.
     */
    public static boolean collapsibleHeader(String id, String title, boolean initiallyExpanded, boolean forceExpanded) {
        ImGui.pushID("collapsible_session_" + collapsibleSessionId);
        try {
            String visibleTitle = ellipsize(
                title,
                Math.max(1.0f, ImGui.getContentRegionAvailX() - ImGui.getFrameHeight())
            );
            String stableId = id + "_expanded";
            if (INITIALIZED_COLLAPSIBLES.add(ImGui.getID(stableId))) {
                ImGui.setNextItemOpen(initiallyExpanded, ImGuiCond.Always);
            }
            if (forceExpanded) ImGui.setNextItemOpen(true, ImGuiCond.Always);
            int flags = ImGuiTreeNodeFlags.NoTreePushOnOpen
                | ImGuiTreeNodeFlags.FramePadding
                | ImGuiTreeNodeFlags.SpanAvailWidth;
            if (initiallyExpanded) flags |= ImGuiTreeNodeFlags.DefaultOpen;
            boolean expanded = ImGui.treeNodeEx(
                visibleTitle + "###" + stableId,
                flags
            );
            if (!visibleTitle.equals(title) && ImGui.isItemHovered()) {
                ImGui.setTooltip(title);
            }
            return expanded;
        } finally {
            ImGui.popID();
        }
    }

    /**
     * Makes every card perform one complete size measurement the next time it is submitted.
     */
    public static void resetCardMeasurements() {
        MEASURED_CARDS.clear();
    }

    /**
     * Forgets one-time collapsible initialization for the next newly opened root item.
     */
    public static void resetCollapsibleStates() {
        INITIALIZED_COLLAPSIBLES.clear();
        resetCardMeasurements();
    }

    /**
     * Makes each item editor scroll area start at the origin when it is next visited.
     */
    public static void resetScrollStates() {
        INITIALIZED_SCROLLS.clear();
    }

    /**
     * Resets the current window once after {@link #resetScrollStates()}, then preserves it.
     */
    public static void resetCurrentScrollOnFirstVisit(String id) {
        if (!INITIALIZED_SCROLLS.add(ImGui.getID("##item_scroll_" + id))) return;
        ImGui.setScrollX(0.0f);
        ImGui.setScrollY(0.0f);
    }

    public static boolean beginScrollableChild(String id, float width, float height, boolean border) {
        boolean visible = ImGui.beginChild(id, width, height, border);
        resetCurrentScrollOnFirstVisit(id);
        return visible;
    }

    public static boolean beginScrollableChild(String id, float width, float height, int childFlags) {
        boolean visible = ImGui.beginChild(id, width, height, childFlags);
        resetCurrentScrollOnFirstVisit(id);
        return visible;
    }

    public static boolean beginScrollableChild(
        String id,
        float width,
        float height,
        int childFlags,
        int windowFlags
    ) {
        boolean visible = ImGui.beginChild(id, width, height, childFlags, windowFlags);
        resetCurrentScrollOnFirstVisit(id);
        return visible;
    }

    public static String identifiedTitle(String base, String detail) {
        if (detail == null || detail.isBlank()) return base;
        return base + " - " + detail.strip();
    }

    /**
     * Produces a short best-effort identity for arbitrary component list entries.
     */
    public static String jsonSummary(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return compactSummary(value.getAsString());
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.isEmpty()) return "";
            String first = jsonSummary(array.get(0));
            return array.size() <= 1 || first.isBlank() ? first : first + " +" + (array.size() - 1);
        }
        JsonObject object = value.getAsJsonObject();
        for (String key : SUMMARY_KEYS) {
            if (!object.has(key)) continue;
            String summary = jsonSummary(object.get(key));
            if (!summary.isBlank()) return summary;
        }
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            String summary = jsonSummary(entry.getValue());
            if (!summary.isBlank()) return summary;
        }
        return "";
    }

    private static String compactSummary(String value) {
        if (value == null) return "";
        String compact = value.strip().replaceAll("\\s+", " ");
        return compact.length() <= 72 ? compact : compact.substring(0, 69) + "...";
    }

    public static void sameLineIfFits(String nextButtonLabel) {
        float nextWidth = ImGui.calcTextSizeX(nextButtonLabel) + ImGui.getStyle().getFramePaddingX() * 2.0f;
        sameLineIfFits(nextWidth);
    }

    public static void sameLineIfFits(float nextWidth) {
        // getContentRegionMaxX() follows the current table column's work area. The window-wide
        // equivalent can incorrectly keep controls inline until they overlap the next column.
        float rightEdge = ImGui.getWindowPosX() + ImGui.getContentRegionMaxX();
        if (ImGui.getItemRectMaxX() + ImGui.getStyle().getItemSpacingX() + nextWidth <= rightEdge) {
            ImGui.sameLine();
        }
    }

    /**
     * Draws a fixed-width preview beside a stretching form when space permits, and
     * stacks the same regions in preview-first order at narrower widths.
     */
    public static void drawPreviewFormSplit(
        String id,
        float previewWidth,
        Runnable preview,
        Runnable form
    ) {
        if (ImGui.getContentRegionAvailX() >= PREVIEW_FORM_SPLIT_MIN_WIDTH
            && ImGui.beginTable(id, 2, ImGuiTableFlags.SizingStretchProp)) {
            try {
                ImGui.tableSetupColumn("Preview", ImGuiTableColumnFlags.WidthFixed, previewWidth);
                ImGui.tableSetupColumn("Editor", ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableNextColumn();
                preview.run();
                ImGui.tableNextColumn();
                form.run();
            } finally {
                ImGui.endTable();
            }
            return;
        }
        preview.run();
        ImGui.spacing();
        form.run();
    }

    /**
     * Draws parallel fields as one shared label row followed by one shared control row.
     * Keeping those rows explicit prevents content in one cell from moving the control in
     * a neighboring cell to a different vertical position.
     */
    public static void alignedTableFields(String[] labels, Runnable... controls) {
        if (labels.length != controls.length) {
            throw new IllegalArgumentException("Every table field must have one label and one control");
        }

        ImGui.tableNextRow();
        for (String label : labels) {
            ImGui.tableNextColumn();
            ImGui.textWrapped(label);
        }

        ImGui.tableNextRow();
        for (Runnable control : controls) {
            ImGui.tableNextColumn();
            control.run();
        }
    }

    /**
     * Begins a row with flexible content and a compact icon action beside it.
     */
    public static boolean beginInlineIconActionRow(String id) {
        return beginInlineIconActionRow(id, 1);
    }

    /**
     * Begins a row with flexible content and the requested number of compact icon actions.
     */
    public static boolean beginInlineIconActionRow(String id, int actionCount) {
        int count = Math.max(1, actionCount);
        float width = EditorIcons.smallButtonSize() * count
            + ImGui.getStyle().getItemSpacingX() * (count - 1);
        return beginInlineActionRow(id, width);
    }

    /**
     * Draws the ordered move/insert/remove controls used by compact non-card rows.
     */
    public static ListAction drawInlineOrderedListActions(
        int index,
        int size,
        boolean canInsertAfter
    ) {
        return drawListActions(index, size, true, false, canInsertAfter);
    }

    private static boolean beginInlineActionRow(String id, float actionWidth) {
        if (!ImGui.beginTable(
            id,
            2,
            ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoPadOuterX
        )) {
            return false;
        }
        ImGui.tableSetupColumn("Content", ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, actionWidth);
        ImGui.tableNextColumn();
        return true;
    }

    private static ListAction drawListActions(
        int index,
        int size,
        boolean ordered,
        boolean canDuplicate,
        boolean canInsertAfter
    ) {
        ListAction action = ListAction.NONE;
        boolean hasPreviousAction = false;
        if (ordered) {
            ImGui.beginDisabled(index <= 0);
            if (EditorIcons.smallButton("move_up", EditorIcons.Icon.MOVE_UP, tr("action.move_up"))) {
                action = ListAction.MOVE_UP;
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            ImGui.beginDisabled(index + 1 >= size);
            if (EditorIcons.smallButton("move_down", EditorIcons.Icon.MOVE_DOWN, tr("action.move_down"))) {
                action = ListAction.MOVE_DOWN;
            }
            ImGui.endDisabled();
            hasPreviousAction = true;
        }

        if (canInsertAfter) {
            if (hasPreviousAction) ImGui.sameLine();
            if (EditorIcons.smallButton("insert_after", EditorIcons.Icon.ADD, tr("action.insert_below"))) {
                action = ListAction.INSERT_AFTER;
            }
            hasPreviousAction = true;
        }

        if (canDuplicate) {
            if (hasPreviousAction) ImGui.sameLine();
            if (EditorIcons.smallButton("duplicate", EditorIcons.Icon.DUPLICATE, tr("action.duplicate"))) {
                action = ListAction.DUPLICATE;
            }
            hasPreviousAction = true;
        }

        if (hasPreviousAction) ImGui.sameLine();
        if (EditorIcons.smallButton("remove", EditorIcons.Icon.REMOVE, tr("action.remove_entry"))) {
            action = ListAction.REMOVE;
        }
        return action;
    }

    /**
     * Draws the shared overflow menu for structural row actions.
     */
    public static ListAction drawCompactListActions(
        String id,
        int index,
        int size,
        boolean ordered,
        boolean canDuplicate,
        boolean canInsertAfter
    ) {
        String popupId = "##" + id + "_list_actions_popup";
        if (EditorIcons.smallButton(
            id + "_list_actions",
            EditorIcons.Icon.MORE,
            tr("action.list_actions")
        )) {
            ImGui.openPopup(popupId);
        }
        if (!ImGui.beginPopup(popupId)) {
            return ListAction.NONE;
        }

        ListAction action = ListAction.NONE;
        try {
            if (ordered) {
                ImGui.beginDisabled(index <= 0);
                if (ImGui.selectable(tr("action.move_up") + "##move_up")) action = ListAction.MOVE_UP;
                ImGui.endDisabled();

                ImGui.beginDisabled(index + 1 >= size);
                if (ImGui.selectable(tr("action.move_down") + "##move_down")) action = ListAction.MOVE_DOWN;
                ImGui.endDisabled();
            }
            if (canInsertAfter && ImGui.selectable(tr("action.insert_below") + "##insert_after")) {
                action = ListAction.INSERT_AFTER;
            }
            if (canDuplicate && ImGui.selectable(tr("action.duplicate") + "##duplicate")) {
                action = ListAction.DUPLICATE;
            }
            if (ImGui.selectable(tr("action.remove_entry") + "##remove")) {
                action = ListAction.REMOVE;
            }
        } finally {
            ImGui.endPopup();
        }
        return action;
    }

    /**
     * Returns a combo width based on every option, so virtualized rows cannot resize the column.
     */
    public static float stableComboWidth(String[] labels, float minimumWidth) {
        float widestLabel = 0.0f;
        for (String label : labels) {
            widestLabel = Math.max(widestLabel, ImGui.calcTextSizeX(label));
        }
        float chromeWidth = ImGui.getFrameHeight() + ImGui.getStyle().getFramePaddingX() * 2.0f;
        return Math.max(minimumWidth, widestLabel + chromeWidth);
    }

    private static float listActionsWidth(boolean ordered, boolean canDuplicate, boolean canInsertAfter) {
        float width = EditorIcons.smallButtonSize();
        int count = 1;
        if (ordered) {
            width += EditorIcons.smallButtonSize() * 2.0f;
            count += 2;
        }
        if (canDuplicate) {
            width += EditorIcons.smallButtonSize();
            count++;
        }
        if (canInsertAfter) {
            width += EditorIcons.smallButtonSize();
            count++;
        }
        return width + ImGui.getStyle().getItemSpacingX() * (count - 1);
    }

    private static String ellipsize(String text, float maxWidth) {
        if (ImGui.calcTextSizeX(text) <= maxWidth) return text;
        String suffix = "...";
        float suffixWidth = ImGui.calcTextSizeX(suffix);
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (ImGui.calcTextSizeX(text.substring(0, middle)) + suffixWidth <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, low) + suffix;
    }

    /**
     * Draws the standard editor actions and reports whether edits were discarded.
     */
    public static boolean drawSaveDiscardActions(boolean dirty, Runnable saveAction, Runnable discardAction) {
        ImGui.beginDisabled(!dirty);
        if (EditorIcons.button("save_changes", EditorIcons.Icon.SAVE, tr("action.save_changes"))) {
            saveAction.run();
        }
        ImGui.endDisabled();
        sameLineIfFits(EditorIcons.buttonSize());
        ImGui.beginDisabled(!dirty);
        boolean discarded = false;
        if (EditorIcons.button("discard_edits", EditorIcons.Icon.DISCARD, tr("action.discard_edits"))) {
            discardAction.run();
            discarded = true;
        }
        ImGui.endDisabled();
        return discarded;
    }

    /**
     * Draws a primary editor action followed by the standard discard action.
     */
    public static boolean drawPrimaryDiscardActions(
        String primaryLabel,
        boolean primaryEnabled,
        Runnable primaryAction,
        boolean discardEnabled,
        Runnable discardAction
    ) {
        ImGui.beginDisabled(!primaryEnabled);
        if (ImGui.button(primaryLabel)) {
            primaryAction.run();
        }
        ImGui.endDisabled();
        sameLineIfFits(tr("action.discard_edits"));
        ImGui.beginDisabled(!discardEnabled);
        boolean discarded = false;
        if (ImGui.button(tr("action.discard_edits"))) {
            discardAction.run();
            discarded = true;
        }
        ImGui.endDisabled();
        return discarded;
    }

    public static void textDisabledWrapped(String text) {
        ImGui.pushStyleColor(
            ImGuiCol.Text,
            ImGui.getStyleColorVec4X(ImGuiCol.TextDisabled),
            ImGui.getStyleColorVec4Y(ImGuiCol.TextDisabled),
            ImGui.getStyleColorVec4Z(ImGuiCol.TextDisabled),
            ImGui.getStyleColorVec4W(ImGuiCol.TextDisabled)
        );
        ImGui.textWrapped(text);
        ImGui.popStyleColor();
    }

    /**
     * Draws explanatory copy for an empty editor collection.
     */
    public static void drawEmptyState(String text) {
        textDisabledWrapped(text);
    }

    /**
     * Draws styled Minecraft text without ImGuiMC inserting normal item spacing between
     * adjacent style runs. ImGuiMC temporarily sets spacing to zero for each run, but its
     * trailing same-line call happens after that temporary style has been restored.
     */
    public static void minecraftText(FormattedText text, float wrapWidth) {
        ImGui.pushStyleVar(
            ImGuiStyleVar.ItemSpacing,
            0.0f,
            ImGui.getStyle().getItemSpacingY()
        );
        try {
            EditorRichTextRenderer.render(text, wrapWidth);
        } finally {
            ImGui.popStyleVar();
        }
    }

    public static String readableMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 320 ? message.substring(0, 317) + "..." : message;
    }

    public enum ListAction {
        NONE,
        MOVE_UP,
        MOVE_DOWN,
        INSERT_AFTER,
        DUPLICATE,
        REMOVE
    }

    public record ListCardResult(boolean expanded, ListAction action, boolean focusApplied) {
    }
}
