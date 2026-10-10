package dev.nineofgaming.itemlab.editor.ui.control;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeOverlayLayout;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeRenderSpan;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeSyntaxLexer;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Multiline input with a synchronized line gutter and editor-style cursor feedback.
 */
public final class CodeEditorInput {
    private static final float MIN_HEIGHT = 96.0f;
    private static final float GUTTER_PADDING = 7.0f;
    private static final double CARET_BLINK_PERIOD_SECONDS = 1.20;
    private static final double CARET_VISIBLE_SECONDS = 0.80;
    private static final Pattern MESSAGE_LINE = Pattern.compile("(?i)\\bline\\s+(\\d+)\\b");
    private static final Pattern MESSAGE_POSITION = Pattern.compile("(?i)\\b(?:position|pos)\\s+(\\d+)\\b");

    private int cursorByteOffset;
    private int cursorLine = 1;
    private int cursorColumn = 1;
    private int previousCursorLine = 1;
    private int previousCursorByteOffset;
    private boolean active;
    private boolean focusRequested;
    private double caretBlinkResetTime;
    private String highlightedSource;
    private CodeLanguage highlightedLanguage = CodeLanguage.PLAIN_TEXT;
    private List<CodeRenderSpan> highlightedSpans = List.of();

    private final ImGuiInputTextCallback cursorCallback = new ImGuiInputTextCallback() {
        @Override
        public void accept(ImGuiInputTextCallbackData data) {
            int nextCursorByteOffset = Math.max(0, data.getCursorPos());
            if (nextCursorByteOffset != cursorByteOffset) {
                caretBlinkResetTime = ImGui.getTime();
            }
            cursorByteOffset = nextCursorByteOffset;
            CodeOverlayLayout.CaretPosition position = CodeOverlayLayout.caretPosition(
                data.getBuf(),
                cursorByteOffset
            );
            cursorLine = position.line() + 1;
            cursorColumn = position.column();
        }
    };

    public void resetCursor() {
        focusRequested = false;
        cursorByteOffset = 0;
        cursorLine = 1;
        cursorColumn = 1;
        previousCursorLine = 1;
        previousCursorByteOffset = 0;
        active = false;
        caretBlinkResetTime = 0.0;
        highlightedSource = null;
        highlightedLanguage = CodeLanguage.PLAIN_TEXT;
        highlightedSpans = List.of();
    }

    /**
     * Deferred until the actual input is drawn, inside both editor child windows.
     */
    public void requestFocus() {
        focusRequested = true;
    }

    public void cancelFocusRequest() {
        focusRequested = false;
    }

    /**
     * Draws an editor whose requested height includes its one-line status footer.
     */
    public boolean draw(
        String id,
        ImString value,
        float width,
        float height,
        int flags,
        int errorLine,
        CodeLanguage language
    ) {
        String source = value.get();
        int lineCount = lineCount(source);
        CodeOverlayLayout.CaretPosition clampedCursor = CodeOverlayLayout.caretPosition(
            source,
            cursorByteOffset
        );
        cursorLine = Math.clamp(clampedCursor.line() + 1, 1, lineCount);
        cursorColumn = clampedCursor.column();

        float statusHeight = ImGui.getTextLineHeightWithSpacing();
        float viewportHeight = Math.max(MIN_HEIGHT, height - statusHeight);
        float requestedWidth = width < 0.0f ? ImGui.getContentRegionAvailX() : width;
        boolean[] changed = {false};

        boolean visible = EditorUi.beginScrollableChild(
            id + "_editor",
            requestedWidth,
            viewportHeight,
            ImGuiChildFlags.Borders,
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
        );
        try {
            if (visible) {
                changed[0] = drawEditorContents(
                    id,
                    value,
                    flags,
                    errorLine,
                    lineCount,
                    language
                );
            }
        } finally {
            ImGui.endChild();
        }

        String lines = lineCount == 1
            ? tr("code_editor.line_count.one")
            : tr("code_editor.line_count.many", lineCount);
        ImGui.textDisabled(tr("code_editor.status", cursorLine, cursorColumn, lines));
        return changed[0];
    }

    private boolean drawEditorContents(
        String id,
        ImString value,
        int flags,
        int errorLine,
        int lineCount,
        CodeLanguage language
    ) {
        float lineHeight = ImGui.getTextLineHeight();
        int digits = Integer.toString(Math.max(1, lineCount)).length();
        float gutterWidth = ImGui.calcTextSizeX("9".repeat(digits)) + GUTTER_PADDING * 2.0f + 4.0f;

        float localStartX = ImGui.getCursorPosX();
        float screenStartX = ImGui.getCursorScreenPosX();
        float viewportTop = ImGui.getWindowPosY();
        float viewportBottom = viewportTop + ImGui.getWindowSizeY();
        float availableWidth = ImGui.getContentRegionAvailX();
        float availableHeight = Math.max(1.0f, ImGui.getContentRegionAvailY());
        float editorWidth = Math.max(40.0f, availableWidth - gutterWidth);
        float textStartX = screenStartX + gutterWidth;
        float right = textStartX + editorWidth;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(screenStartX, viewportTop, right, viewportBottom, true);
        drawList.addRectFilled(
            screenStartX,
            viewportTop,
            textStartX,
            viewportBottom,
            ItemEditorTheme.codeEditorGutterColor()
        );
        drawList.addLine(
            textStartX - 1.0f,
            viewportTop,
            textStartX - 1.0f,
            viewportBottom,
            ImGui.getColorU32(ImGuiCol.Separator)
        );
        drawList.popClipRect();

        ImGui.setCursorPosX(localStartX + gutterWidth);
        TextViewportResult result = new TextViewportResult(false, viewportTop, viewportTop, viewportBottom);
        boolean visible = EditorUi.beginScrollableChild(
            id + "_text_viewport",
            editorWidth,
            availableHeight,
            ImGuiChildFlags.None,
            ImGuiWindowFlags.HorizontalScrollbar
        );
        try {
            if (visible) {
                result = drawTextViewport(id, value, flags, errorLine, lineCount, language);
            }
        } finally {
            ImGui.endChild();
        }

        drawGutter(
            drawList,
            screenStartX,
            textStartX,
            result.textOriginY(),
            lineHeight,
            lineCount,
            errorLine,
            result.viewportTop(),
            result.viewportBottom()
        );
        return result.changed();
    }

    private TextViewportResult drawTextViewport(
        String id,
        ImString value,
        int flags,
        int errorLine,
        int lineCount,
        CodeLanguage language
    ) {
        float lineHeight = ImGui.getTextLineHeight();
        float framePaddingY = ImGui.getStyle().getFramePaddingY();
        float framePaddingX = ImGui.getStyle().getFramePaddingX();
        float scrollbarSize = ImGui.getStyle().getScrollbarSize();
        float baseContentHeight = framePaddingY * 2.0f + lineCount * lineHeight + 1.0f;
        float baseContentWidth = longestLineWidth(value.get()) + framePaddingX * 2.0f + 1.0f;
        float windowWidth = Math.max(1.0f, ImGui.getWindowSizeX());
        float windowHeight = Math.max(1.0f, ImGui.getWindowSizeY());

        boolean needsVerticalScrollbar = baseContentHeight > windowHeight + 0.5f;
        float visibleWidth = Math.max(1.0f, windowWidth - (needsVerticalScrollbar ? scrollbarSize : 0.0f));
        boolean needsHorizontalScrollbar = baseContentWidth > visibleWidth + 0.5f;
        float visibleHeight = Math.max(1.0f, windowHeight - (needsHorizontalScrollbar ? scrollbarSize : 0.0f));
        if (!needsVerticalScrollbar && baseContentHeight > visibleHeight + 0.5f) {
            visibleWidth = Math.max(1.0f, windowWidth - scrollbarSize);
            needsHorizontalScrollbar = baseContentWidth > visibleWidth + 0.5f;
            visibleHeight = Math.max(1.0f, windowHeight - (needsHorizontalScrollbar ? scrollbarSize : 0.0f));
        }

        float contentWidth = Math.max(visibleWidth, baseContentWidth);
        float contentHeight = Math.max(visibleHeight, baseContentHeight);
        float scrollX = Math.max(0.0f, ImGui.getScrollX());
        float scrollY = Math.max(0.0f, ImGui.getScrollY());
        float contentStartX = ImGui.getCursorScreenPosX();
        float contentStartY = ImGui.getCursorScreenPosY();
        float viewportLeft = contentStartX + scrollX;
        float viewportTop = contentStartY + scrollY;
        float viewportRight = viewportLeft + visibleWidth;
        float viewportBottom = viewportTop + visibleHeight;
        float textOriginX = contentStartX + framePaddingX;
        float textOriginY = contentStartY + framePaddingY;
        String inputLabel = "##" + id + "_text";
        boolean activeBeforeDraw = imgui.internal.ImGui.getActiveID() == ImGui.getID(inputLabel);
        if (activeBeforeDraw && ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            // InputText normally treats Escape as "revert to the value from focus entry".
            // Deactivate first so Escape only leaves the editor and preserves the draft.
            imgui.internal.ImGui.clearActiveID();
            activeBeforeDraw = false;
        }
        boolean showSyntaxOverlay = language != CodeLanguage.PLAIN_TEXT;
        boolean activeLastFrame = active;
        active = activeBeforeDraw;

        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(viewportLeft, viewportTop, viewportRight, viewportBottom, true);
        drawLineBackgrounds(drawList, viewportLeft, viewportRight, textOriginY, lineHeight, errorLine);
        drawList.popClipRect();

        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0.0f, 0.0f, 0.0f, 0.0f);
        if (showSyntaxOverlay) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0.0f);
        boolean changed;
        try {
            if (focusRequested) {
                ImGui.setKeyboardFocusHere();
                focusRequested = false;
            }
            changed = EditorInput.inputTextMultiline(
                inputLabel,
                value,
                contentWidth,
                contentHeight,
                flags | ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.NoHorizontalScroll,
                cursorCallback
            );
            active = ImGui.isItemActive();
            if (active && (!activeLastFrame || changed)) {
                caretBlinkResetTime = ImGui.getTime();
            }
        } finally {
            ImGui.popStyleVar();
            ImGui.popStyleColor(showSyntaxOverlay ? 4 : 3);
        }

        if (showSyntaxOverlay) {
            drawSyntaxOverlay(
                drawList,
                value.get(),
                language,
                textOriginX,
                viewportLeft,
                viewportRight,
                textOriginY,
                lineHeight,
                viewportTop,
                viewportBottom
            );
            if (active) {
                drawSyntaxCaret(
                    drawList,
                    value.get(),
                    textOriginX,
                    viewportLeft,
                    viewportRight,
                    textOriginY,
                    lineHeight,
                    viewportTop,
                    viewportBottom
                );
            }
        }

        keepCursorVisible(
            value.get(),
            scrollX,
            scrollY,
            visibleWidth,
            visibleHeight,
            framePaddingX,
            framePaddingY,
            lineHeight,
            contentWidth,
            contentHeight
        );
        previousCursorLine = cursorLine;
        previousCursorByteOffset = cursorByteOffset;
        return new TextViewportResult(changed, textOriginY, viewportTop, viewportBottom);
    }

    private record TextViewportResult(
        boolean changed,
        float textOriginY,
        float viewportTop,
        float viewportBottom
    ) {
    }

    private void drawSyntaxOverlay(
        ImDrawList drawList,
        String source,
        CodeLanguage language,
        float textOriginX,
        float clipLeft,
        float clipRight,
        float textOriginY,
        float lineHeight,
        float viewportTop,
        float viewportBottom
    ) {
        updateHighlightCache(language, source);
        if (highlightedSpans.isEmpty()) return;

        int firstVisibleLine = Math.max(0, (int) Math.floor((viewportTop - textOriginY) / lineHeight));
        int lastVisibleLine = Math.max(firstVisibleLine, (int) Math.ceil((viewportBottom - textOriginY) / lineHeight));
        int firstSpan = CodeOverlayLayout.firstSpanAtOrAfterLine(highlightedSpans, firstVisibleLine);
        drawList.pushClipRect(clipLeft, viewportTop, clipRight, viewportBottom, true);
        for (int index = firstSpan; index < highlightedSpans.size(); index++) {
            CodeRenderSpan span = highlightedSpans.get(index);
            if (span.line() > lastVisibleLine) break;
            float x = textOriginX + unroundedTextWidth(source, span.lineStart(), span.start());
            float y = textOriginY + span.line() * lineHeight;
            drawList.addText(
                x,
                y,
                ItemEditorTheme.codeTokenColor(span.type()),
                source.substring(span.start(), span.end())
            );
        }
        drawList.popClipRect();
    }

    private void updateHighlightCache(CodeLanguage language, String source) {
        if (language == highlightedLanguage && source.equals(highlightedSource)) return;
        highlightedSource = source;
        highlightedLanguage = language;
        highlightedSpans = CodeOverlayLayout.layout(source, CodeSyntaxLexer.tokenize(language, source));
    }

    private void drawSyntaxCaret(
        ImDrawList drawList,
        String source,
        float textOriginX,
        float clipLeft,
        float clipRight,
        float textOriginY,
        float lineHeight,
        float viewportTop,
        float viewportBottom
    ) {
        if (!syntaxCaretVisible()) return;
        CodeOverlayLayout.CaretPosition caret = CodeOverlayLayout.caretPosition(source, cursorByteOffset);
        float x = textOriginX + unroundedTextWidth(source, caret.lineStart(), caret.offset());
        float y = textOriginY + caret.line() * lineHeight;
        float caretHeight = Math.min(lineHeight, ImGui.getFontSize() + 1.0f);
        drawList.pushClipRect(clipLeft, viewportTop, clipRight, viewportBottom, true);
        drawList.addRectFilled(
            x,
            y,
            x + 1.0f,
            y + caretHeight,
            ImGui.getColorU32(ImGuiCol.Text)
        );
        drawList.popClipRect();
    }

    private boolean syntaxCaretVisible() {
        if (!ImGui.getIO().getConfigInputTextCursorBlink()) return true;
        double elapsed = Math.max(0.0, ImGui.getTime() - caretBlinkResetTime);
        return elapsed % CARET_BLINK_PERIOD_SECONDS <= CARET_VISIBLE_SECONDS;
    }

    private void drawLineBackgrounds(
        ImDrawList drawList,
        float left,
        float right,
        float textStartY,
        float lineHeight,
        int errorLine
    ) {
        if (active) {
            float activeTop = textStartY + (cursorLine - 1) * lineHeight;
            drawList.addRectFilled(
                left,
                activeTop,
                right,
                activeTop + lineHeight,
                ItemEditorTheme.codeEditorActiveLineColor()
            );
        }
        if (errorLine > 0) {
            float errorTop = textStartY + (errorLine - 1) * lineHeight;
            drawList.addRectFilled(
                left,
                errorTop,
                right,
                errorTop + lineHeight,
                ItemEditorTheme.codeEditorErrorLineColor()
            );
        }
    }

    private static void drawGutter(
        ImDrawList drawList,
        float left,
        float right,
        float textStartY,
        float lineHeight,
        int lineCount,
        int errorLine,
        float viewportTop,
        float viewportBottom
    ) {
        int firstLine = Math.max(1, (int) Math.floor((viewportTop - textStartY) / lineHeight) + 1);
        int lastLine = Math.min(lineCount, (int) Math.ceil((viewportBottom - textStartY) / lineHeight) + 1);
        int normalColor = ImGui.getColorU32(ImGuiCol.TextDisabled);
        int errorColor = ImGui.getColorU32(1.0f, 0.38f, 0.40f, 1.0f);

        drawList.pushClipRect(left, viewportTop, right, viewportBottom, true);
        for (int line = firstLine; line <= lastLine; line++) {
            String label = Integer.toString(line);
            float x = right - GUTTER_PADDING - ImGui.calcTextSizeX(label);
            float y = textStartY + (line - 1) * lineHeight;
            int color = line == errorLine ? errorColor : normalColor;
            drawList.addText(x, y, color, label);
            if (line == errorLine) {
                drawList.addRectFilled(right - 3.0f, y, right, y + lineHeight, errorColor);
            }
        }
        drawList.popClipRect();
    }

    private void keepCursorVisible(
        String source,
        float scrollX,
        float scrollY,
        float visibleWidth,
        float visibleHeight,
        float framePaddingX,
        float framePaddingY,
        float lineHeight,
        float contentWidth,
        float contentHeight
    ) {
        if (!active) return;

        if (cursorByteOffset != previousCursorByteOffset) {
            CodeOverlayLayout.CaretPosition caret = CodeOverlayLayout.caretPosition(source, cursorByteOffset);
            float caretX = framePaddingX + unroundedTextWidth(source, caret.lineStart(), caret.offset());
            float horizontalMargin = Math.min(lineHeight, visibleWidth * 0.25f);
            if (caretX < scrollX + horizontalMargin) {
                ImGui.setScrollX(Math.max(0.0f, caretX - horizontalMargin));
            } else if (caretX + 1.0f > scrollX + visibleWidth - horizontalMargin) {
                float next = caretX + horizontalMargin - visibleWidth + 1.0f;
                ImGui.setScrollX(Math.clamp(contentWidth - visibleWidth, 0.0f, next));
            }
        }

        if (cursorLine == previousCursorLine) return;
        float lineTop = framePaddingY + (cursorLine - 1) * lineHeight;
        float lineBottom = lineTop + lineHeight;
        if (lineTop < scrollY) {
            ImGui.setScrollY(Math.max(0.0f, lineTop - lineHeight));
        } else if (lineBottom > scrollY + visibleHeight) {
            float next = lineBottom - visibleHeight + lineHeight;
            ImGui.setScrollY(Math.clamp(contentHeight - visibleHeight, 0.0f, next));
        }
    }

    public static int errorLine(Throwable problem, String source) {
        Throwable cursor = problem;
        for (int depth = 0; cursor != null && depth < 12; depth++) {
            if (cursor instanceof CommandSyntaxException syntax && syntax.getCursor() >= 0) {
                return lineAtCharacterOffset(source, syntax.getCursor());
            }
            String message = cursor.getMessage();
            if (message != null) {
                Matcher line = MESSAGE_LINE.matcher(message);
                if (line.find()) {
                    try {
                        return Math.max(1, Integer.parseInt(line.group(1)));
                    } catch (NumberFormatException ignored) {
                        // Continue through the cause chain.
                    }
                }
                Matcher position = MESSAGE_POSITION.matcher(message.toLowerCase(Locale.ROOT));
                if (position.find()) {
                    try {
                        return lineAtCharacterOffset(source, Integer.parseInt(position.group(1)));
                    } catch (NumberFormatException ignored) {
                        // Continue through the cause chain.
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return 0;
    }

    private static int lineAtCharacterOffset(String source, int offset) {
        int line = 1;
        int end = Math.clamp(offset, 0, source.length());
        for (int index = 0; index < end; index++) {
            if (source.charAt(index) == '\n') line++;
        }
        return line;
    }

    private static int lineCount(String source) {
        int lines = 1;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') lines++;
        }
        return lines;
    }

    private static float longestLineWidth(String source) {
        int start = 0;
        int widestStart = 0;
        int widestEnd = 0;
        for (int index = 0; index <= source.length(); index++) {
            if (index == source.length() || source.charAt(index) == '\n') {
                int end = index > start && source.charAt(index - 1) == '\r' ? index - 1 : index;
                if (end - start > widestEnd - widestStart) {
                    widestStart = start;
                    widestEnd = end;
                }
                start = index + 1;
            }
        }
        return unroundedTextWidth(source, widestStart, widestEnd);
    }

    /**
     * InputText advances glyphs with the font's raw widths. ImGui.calcTextSizeX rounds a complete
     * measurement up to a pixel, which accumulates a visible offset when each token starts a new
     * colored draw run.
     */
    private static float unroundedTextWidth(String source, int start, int end) {
        if (start >= end) return 0.0f;
        return ImGui.getFont().calcTextSizeAX(
            ImGui.getFontSize(),
            Float.MAX_VALUE,
            0.0f,
            source.substring(start, end)
        );
    }
}
