package dev.nineofgaming.itemlab.editor.ui.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure source-to-line layout used by the ImGui syntax overlay.
 */
public final class CodeOverlayLayout {
    private CodeOverlayLayout() {
    }

    public static List<CodeRenderSpan> layout(String source, List<CodeToken> tokens) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(tokens, "tokens");
        if (tokens.isEmpty()) return List.of();

        int[] lineStarts = lineStarts(source);
        List<CodeRenderSpan> spans = new ArrayList<>(tokens.size());
        for (CodeToken token : tokens) {
            if (token.end() > source.length()) {
                throw new IllegalArgumentException("Token extends beyond source: " + token);
            }
            int segmentStart = token.start();
            int line = lineAtOffset(lineStarts, segmentStart);
            while (segmentStart < token.end()) {
                int newline = source.indexOf('\n', segmentStart);
                int rawEnd = newline >= 0 && newline < token.end() ? newline : token.end();
                int segmentEnd = rawEnd > segmentStart && source.charAt(rawEnd - 1) == '\r'
                    ? rawEnd - 1
                    : rawEnd;
                if (segmentStart < segmentEnd) {
                    spans.add(new CodeRenderSpan(
                        segmentStart,
                        segmentEnd,
                        line,
                        lineStarts[line],
                        token.type()
                    ));
                }
                if (newline < 0 || newline >= token.end()) break;
                segmentStart = newline + 1;
                line++;
            }
        }
        return List.copyOf(spans);
    }

    /**
     * Returns the first span whose line is greater than or equal to {@code line}.
     */
    public static int firstSpanAtOrAfterLine(List<CodeRenderSpan> spans, int line) {
        Objects.requireNonNull(spans, "spans");
        int low = 0;
        int high = spans.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (spans.get(middle).line() < line) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    /**
     * Resolves an ImGui UTF-8 cursor offset to the source line and Java-string boundaries.
     */
    public static CaretPosition caretPosition(String source, int utf8ByteOffset) {
        Objects.requireNonNull(source, "source");
        int offset = CodeSyntaxLexer.utf16OffsetAtUtf8Byte(source, utf8ByteOffset);
        int line = 0;
        int lineStart = 0;
        int column = 1;
        for (int index = 0; index < offset; ) {
            int codePoint = source.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '\n') {
                line++;
                lineStart = index;
                column = 1;
            } else if (codePoint != '\r') {
                column++;
            }
        }
        return new CaretPosition(offset, line, lineStart, column);
    }

    private static int[] lineStarts(String source) {
        int lines = 1;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') lines++;
        }
        int[] starts = new int[lines];
        int line = 1;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') starts[line++] = index + 1;
        }
        return starts;
    }

    private static int lineAtOffset(int[] lineStarts, int offset) {
        int low = 0;
        int high = lineStarts.length;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (lineStarts[middle] <= offset) low = middle;
            else high = middle;
        }
        return low;
    }

    public record CaretPosition(int offset, int line, int lineStart, int column) {
    }
}
