package dev.nineofgaming.itemlab.editor.ui.list;

/**
 * Shared insertion-boundary math for ordered-list moves.
 */
final class ListReorder {
    private ListReorder() {
    }

    /**
     * Resolves an insertion boundary against the list after the source row is removed.
     * Returns {@code -1} for invalid requests and for the boundaries immediately before
     * and after the source row, both of which leave the list unchanged.
     */
    static int destinationIndex(int size, int sourceIndex, int destinationBoundary) {
        if (sourceIndex < 0
            || sourceIndex >= size
            || destinationBoundary < 0
            || destinationBoundary > size
            || destinationBoundary == sourceIndex
            || destinationBoundary == sourceIndex + 1) {
            return -1;
        }
        return destinationBoundary > sourceIndex
            ? destinationBoundary - 1
            : destinationBoundary;
    }
}
