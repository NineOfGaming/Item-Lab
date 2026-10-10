package dev.nineofgaming.itemlab.editor.ui.syntax;

import java.util.Objects;

/**
 * A source span with both Java UTF-16 indices and ImGui-compatible UTF-8 byte offsets.
 * Every boundary is placed between complete Unicode code points.
 */
public record CodeToken(
    int start,
    int end,
    int startUtf8,
    int endUtf8,
    CodeTokenType type
) {
    public CodeToken {
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid character span");
        if (startUtf8 < 0 || endUtf8 < startUtf8) throw new IllegalArgumentException("Invalid UTF-8 span");
        Objects.requireNonNull(type, "type");
    }
}
