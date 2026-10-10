package dev.nineofgaming.itemlab.editor.ui.list;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Keeps editable JSON-array values and their stable row identities in lockstep.
 */
public final class JsonListMutations {
    private JsonListMutations() {
    }

    public static Mutation apply(
        JsonArray values,
        List<Long> rowIds,
        int index,
        EditorUi.ListAction action,
        LongSupplier nextId
    ) {
        return apply(values, rowIds, index, action, nextId, null);
    }

    public static Mutation apply(
        JsonArray values,
        List<Long> rowIds,
        int index,
        EditorUi.ListAction action,
        LongSupplier nextId,
        Supplier<JsonElement> inserter
    ) {
        if (index < 0 || index >= values.size() || rowIds.size() != values.size()) {
            return Mutation.NONE;
        }
        return switch (action) {
            case MOVE_UP -> move(values, rowIds, index, index - 1);
            case MOVE_DOWN -> move(values, rowIds, index, index + 2);
            case INSERT_AFTER -> {
                if (inserter == null) yield Mutation.NONE;
                JsonElement inserted = inserter.get();
                if (inserted == null) yield Mutation.NONE;
                long id = nextId.getAsLong();
                insert(values, index + 1, inserted);
                rowIds.add(index + 1, id);
                yield new Mutation(true, id);
            }
            case DUPLICATE -> {
                long id = nextId.getAsLong();
                insert(values, index + 1, values.get(index).deepCopy());
                rowIds.add(index + 1, id);
                yield new Mutation(true, id);
            }
            case REMOVE -> {
                values.remove(index);
                rowIds.remove(index);
                long focus = rowIds.isEmpty() ? 0L : rowIds.get(Math.min(index, rowIds.size() - 1));
                yield new Mutation(true, focus);
            }
            default -> Mutation.NONE;
        };
    }

    /**
     * Applies a stable-identity move to an insertion boundary in the current array.
     */
    public static Mutation reorder(
        JsonArray values,
        List<Long> rowIds,
        OrderedListDragDrop.ReorderRequest request
    ) {
        if (request == null || rowIds.size() != values.size()) return Mutation.NONE;
        return move(
            values,
            rowIds,
            rowIds.indexOf(request.sourceRowId()),
            request.destinationBoundary()
        );
    }

    private static Mutation move(
        JsonArray values,
        List<Long> ids,
        int sourceIndex,
        int destinationBoundary
    ) {
        int destinationIndex = ListReorder.destinationIndex(
            values.size(),
            sourceIndex,
            destinationBoundary
        );
        if (destinationIndex < 0 || ids.size() != values.size()) return Mutation.NONE;

        JsonElement focusedValue = values.remove(sourceIndex);
        long focusedId = ids.remove(sourceIndex);
        insert(values, destinationIndex, focusedValue);
        ids.add(destinationIndex, focusedId);
        return new Mutation(true, focusedId);
    }

    private static void insert(JsonArray values, int index, JsonElement value) {
        values.add(value);
        for (int cursor = values.size() - 1; cursor > index; cursor--) {
            swap(values, cursor, cursor - 1);
        }
    }

    private static void swap(JsonArray values, int first, int second) {
        var value = values.get(first);
        values.set(first, values.get(second));
        values.set(second, value);
    }

    public record Mutation(boolean changed, long focusRowId) {
        private static final Mutation NONE = new Mutation(false, 0L);
    }
}
