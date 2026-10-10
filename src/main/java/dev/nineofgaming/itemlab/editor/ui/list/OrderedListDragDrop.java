package dev.nineofgaming.itemlab.editor.ui.list;

import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDragDropFlags;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Shared drag presentation and list-scoped payload contract for ordered editor rows.
 */
public final class OrderedListDragDrop {
    private static final String PAYLOAD_TYPE = "ITEMLAB_ORDERED_LIST_ROW";
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();
    private static final float MARKER_BAND_HEIGHT = 14.0f;
    private static final float MARKER_THICKNESS = 4.0f;
    private static final float AUTO_SCROLL_MARGIN = 42.0f;
    private static final float AUTO_SCROLL_SPEED = 360.0f;

    private OrderedListDragDrop() {
    }

    /**
     * Creates a fresh editor-session scope for one semantic list instance.
     */
    public static Scope newScope(String listId) {
        return new Scope(NEXT_SESSION_ID.incrementAndGet(), listId);
    }

    /**
     * Draws the only surface from which a structured row drag may begin.
     */
    public static void drawHandle(String id, Scope scope, long sourceRowId, String sourceLabel) {
        Objects.requireNonNull(scope, "scope");
        EditorIcons.dragHandle(id, tr("action.drag_to_reorder"));
        if (!ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoDisableHover)) return;
        try {
            ImGui.setDragDropPayload(PAYLOAD_TYPE, new Payload(scope, sourceRowId));
            ImGui.textUnformatted(tr("action.dragging_entry"));
            ImGui.separator();
            ImGui.textWrapped(sourceLabel == null || sourceLabel.isBlank()
                ? tr("action.drag_to_reorder")
                : sourceLabel);
            ImGui.textDisabled(tr("action.drop_at_insertion_marker"));
        } finally {
            ImGui.endDragDropSource();
        }
    }

    /**
     * Turns the complete row card into two generous insertion targets. Dropping on
     * its upper half inserts before the row; dropping on its lower half inserts after.
     */
    public static ReorderRequest drawRowTarget(Scope scope, long rowId, int rowIndex) {
        Objects.requireNonNull(scope, "scope");
        Payload active = ImGui.getDragDropPayload(PAYLOAD_TYPE);
        if (!accepts(scope, active)) return null;

        float left = ImGui.getItemRectMinX();
        float top = ImGui.getItemRectMinY();
        float right = ImGui.getItemRectMaxX();
        float bottom = ImGui.getItemRectMaxY();
        int guideColor = ImGui.getColorU32(ImGuiCol.DragDropTarget, 0.24f);
        ImGui.getWindowDrawList().addLine(left, top, right, top, guideColor, 1.5f);
        ImGui.getWindowDrawList().addLine(left, bottom, right, bottom, guideColor, 1.5f);
        if (active.sourceRowId() == rowId) {
            ImGui.getWindowDrawList().addRectFilled(
                left,
                top,
                right,
                bottom,
                ImGui.getColorU32(ImGuiCol.HeaderActive, 0.12f),
                6.0f
            );
            ImGui.getWindowDrawList().addRect(
                left,
                top,
                right,
                bottom,
                ImGui.getColorU32(ImGuiCol.DragDropTarget, 0.85f),
                6.0f,
                0,
                2.5f
            );
        }

        if (!ImGui.beginDragDropTarget()) return null;
        try {
            int destinationBoundary = ImGui.getMousePosY() < (top + bottom) * 0.5f
                ? rowIndex
                : rowIndex + 1;
            float markerY = destinationBoundary == rowIndex ? top : bottom;
            drawActiveMarker(left, right, markerY);
            Payload delivered = ImGui.acceptDragDropPayload(
                PAYLOAD_TYPE,
                ImGuiDragDropFlags.AcceptNoDrawDefaultRect,
                Payload.class
            );
            if (!ImGui.isMouseReleased(0) || !accepts(scope, delivered)) {
                return null;
            }
            return new ReorderRequest(scope, delivered.sourceRowId(), destinationBoundary);
        } finally {
            ImGui.endDragDropTarget();
        }
    }

    private static void drawActiveMarker(float left, float right, float y) {
        ImGui.getWindowDrawList().addRectFilled(
            left,
            y - MARKER_BAND_HEIGHT * 0.5f,
            right,
            y + MARKER_BAND_HEIGHT * 0.5f,
            ImGui.getColorU32(ImGuiCol.DragDropTargetBg, 0.45f),
            3.0f
        );
        int markerColor = ImGui.getColorU32(ImGuiCol.DragDropTarget);
        ImGui.getWindowDrawList().addLine(left, y, right, y, markerColor, MARKER_THICKNESS);
        ImGui.getWindowDrawList().addCircleFilled(left + 3.0f, y, 3.5f, markerColor);
        ImGui.getWindowDrawList().addCircleFilled(right - 3.0f, y, 3.5f, markerColor);
    }

    /**
     * Scrolls the current owning child window while a matching row drag nears an edge.
     */
    public static void autoScrollCurrentWindow(Scope scope) {
        Payload active = ImGui.getDragDropPayload(PAYLOAD_TYPE);
        if (!accepts(scope, active) || ImGui.getScrollMaxY() <= 0.0f) return;

        float mouseY = ImGui.getMousePosY();
        float top = ImGui.getWindowPosY();
        float bottom = top + ImGui.getWindowHeight();
        float direction = 0.0f;
        float strength = 0.0f;
        if (mouseY < top + AUTO_SCROLL_MARGIN) {
            direction = -1.0f;
            strength = 1.0f - Math.max(0.0f, mouseY - top) / AUTO_SCROLL_MARGIN;
        } else if (mouseY > bottom - AUTO_SCROLL_MARGIN) {
            direction = 1.0f;
            strength = 1.0f - Math.max(0.0f, bottom - mouseY) / AUTO_SCROLL_MARGIN;
        }
        if (direction == 0.0f) return;

        float next = ImGui.getScrollY()
            + direction * AUTO_SCROLL_SPEED * Math.max(0.0f, ImGui.getIO().getDeltaTime()) * strength;
        ImGui.setScrollY(Math.clamp(next, 0.0f, ImGui.getScrollMaxY()));
    }

    /**
     * Pure scope check used by targets and by compatibility verification.
     */
    public static boolean accepts(Scope target, Payload payload) {
        return payload != null && payload.scope().equals(target);
    }

    /**
     * A token combines a unique editor session with a stable semantic list path.
     */
    public record Scope(long sessionId, String listId) {
        public Scope {
            Objects.requireNonNull(listId, "listId");
        }

        /**
         * Derives a named descendant scope without introducing an index identity.
         */
        public Scope child(String childListId) {
            Objects.requireNonNull(childListId, "childListId");
            return new Scope(sessionId, listId + "/" + childListId);
        }

        /**
         * Derives a nested list scope from the stable identity of its owning row.
         */
        public Scope nested(long ownerRowId, String childListId) {
            return new Scope(sessionId, listId + "/" + ownerRowId + "/" + childListId);
        }
    }

    /**
     * Drag payloads contain stable identity only; indices are resolved when applying a request.
     */
    public record Payload(Scope scope, long sourceRowId) {
        public Payload {
            Objects.requireNonNull(scope, "scope");
        }
    }

    /**
     * A completed drag expressed as a stable source identity and insertion boundary.
     */
    public record ReorderRequest(Scope scope, long sourceRowId, int destinationBoundary) {
        public ReorderRequest {
            Objects.requireNonNull(scope, "scope");
        }
    }
}
