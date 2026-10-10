package dev.nineofgaming.itemlab.editor.ui.list;

import dev.nineofgaming.itemlab.editor.ui.EditorUi;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * Shared structural mutations for editor lists whose rows carry their own stable identity.
 */
public final class EditorListMutations {
    private EditorListMutations() {
    }

    public static <T> Mutation<T> apply(
        List<T> rows,
        int index,
        EditorUi.ListAction action,
        UnaryOperator<T> duplicator
    ) {
        return apply(rows, index, action, duplicator, null);
    }

    public static <T> Mutation<T> apply(
        List<T> rows,
        int index,
        EditorUi.ListAction action,
        UnaryOperator<T> duplicator,
        Supplier<T> inserter
    ) {
        if (index < 0 || index >= rows.size() || action == EditorUi.ListAction.NONE) {
            return Mutation.unchanged();
        }

        return switch (action) {
            case MOVE_UP -> {
                if (index == 0) yield Mutation.unchanged();
                yield move(rows, index, index - 1);
            }
            case MOVE_DOWN -> {
                if (index + 1 >= rows.size()) yield Mutation.unchanged();
                yield move(rows, index, index + 2);
            }
            case INSERT_AFTER -> {
                if (inserter == null) yield Mutation.unchanged();
                T inserted = inserter.get();
                if (inserted == null) yield Mutation.unchanged();
                rows.add(index + 1, inserted);
                yield Mutation.changed(inserted);
            }
            case DUPLICATE -> {
                if (duplicator == null) yield Mutation.unchanged();
                T duplicate = duplicator.apply(rows.get(index));
                rows.add(index + 1, duplicate);
                yield Mutation.changed(duplicate);
            }
            case REMOVE -> {
                rows.remove(index);
                T focused = rows.isEmpty() ? null : rows.get(Math.min(index, rows.size() - 1));
                yield Mutation.changed(focused);
            }
            default -> Mutation.unchanged();
        };
    }

    /**
     * Applies a stable-identity move to an insertion boundary in the current list.
     */
    public static <T> Mutation<T> reorder(
        List<T> rows,
        OrderedListDragDrop.ReorderRequest request,
        ToLongFunction<? super T> stableId
    ) {
        if (request == null || stableId == null) return Mutation.unchanged();
        int sourceIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (stableId.applyAsLong(rows.get(index)) == request.sourceRowId()) {
                sourceIndex = index;
                break;
            }
        }
        return move(rows, sourceIndex, request.destinationBoundary());
    }

    private static <T> Mutation<T> move(List<T> rows, int sourceIndex, int destinationBoundary) {
        int destinationIndex = ListReorder.destinationIndex(
            rows.size(),
            sourceIndex,
            destinationBoundary
        );
        if (destinationIndex < 0) return Mutation.unchanged();
        T focused = rows.remove(sourceIndex);
        rows.add(destinationIndex, focused);
        return Mutation.changed(focused);
    }

    public record Mutation<T>(boolean changed, T focusRow) {
        private static <T> Mutation<T> unchanged() {
            return new Mutation<>(false, null);
        }

        private static <T> Mutation<T> changed(T focusRow) {
            return new Mutation<>(true, focusRow);
        }
    }
}
