package dev.nineofgaming.itemlab.editor.ui.syntax;

import java.util.Objects;

/**
 * One newline-free token fragment positioned on a zero-based source line.
 */
public record CodeRenderSpan(
    int start,
    int end,
    int line,
    int lineStart,
    CodeTokenType type
) {
    public CodeRenderSpan {
        if (start < 0 || end <= start) throw new IllegalArgumentException("Invalid render span");
        if (line < 0 || lineStart < 0 || lineStart > start) {
            throw new IllegalArgumentException("Invalid line position");
        }
        Objects.requireNonNull(type, "type");
    }
}
