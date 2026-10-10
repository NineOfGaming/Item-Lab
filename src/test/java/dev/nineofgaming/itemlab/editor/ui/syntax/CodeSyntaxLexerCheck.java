package dev.nineofgaming.itemlab.editor.ui.syntax;

import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;

import java.util.List;
import java.util.Random;

/**
 * Headless lexer and UTF-8 span checks for raw code syntax highlighting.
 */
public final class CodeSyntaxLexerCheck {
    private CodeSyntaxLexerCheck() {
    }

    public static void main(String[] arguments) {
        verifyJsonTokens();
        verifyMalformedJson();
        verifySnbtTokens();
        verifyLineEndings();
        verifyUnicodeOffsets();
        verifyOverlayLayout();
        verifyCaretLayout();
        verifyPaletteContrast();
        verifyArbitraryInputProgress();
        measureLargeSource();
    }

    private static void verifyJsonTokens() {
        String source = "{\"name\":\"A\\n😀\",\"amount\":-1.25e+3,\"enabled\":true,\"missing\":null}";
        List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.JSON, source);
        assertToken(source, tokens, "\"name\"", CodeTokenType.KEY);
        assertToken(source, tokens, "\\n", CodeTokenType.ESCAPE);
        assertToken(source, tokens, "-1.25e+3", CodeTokenType.NUMBER);
        assertToken(source, tokens, "true", CodeTokenType.LITERAL);
        assertToken(source, tokens, "null", CodeTokenType.LITERAL);
        assertToken(source, tokens, ":", CodeTokenType.PUNCTUATION);
        assertValidSpans(source, tokens);
    }

    private static void verifyMalformedJson() {
        String source = "{\"open\":\"value\n\"next\": tru @";
        List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.JSON, source);
        assertToken(source, tokens, "\"open\"", CodeTokenType.KEY);
        assertToken(source, tokens, "\"value", CodeTokenType.STRING);
        assertToken(source, tokens, "\"next\"", CodeTokenType.KEY);
        assertToken(source, tokens, "tru", CodeTokenType.UNCLASSIFIED);
        assertToken(source, tokens, "@", CodeTokenType.UNCLASSIFIED);
        assertValidSpans(source, tokens);
    }

    private static void verifySnbtTokens() {
        String source = "{name:'Bee\\'s',health:20.5f,byte:1b,short:2s,long:3L,double:4.0d,"
            + "truth:TRUE,data:[B;1b,2b],note:unquoted,nested:{list:[{value:1},{value:2}]}}";
        List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.SNBT, source);
        assertToken(source, tokens, "name", CodeTokenType.KEY);
        assertToken(source, tokens, "\\'", CodeTokenType.ESCAPE);
        assertToken(source, tokens, "20.5f", CodeTokenType.NUMBER);
        assertToken(source, tokens, "1b", CodeTokenType.NUMBER);
        assertToken(source, tokens, "2s", CodeTokenType.NUMBER);
        assertToken(source, tokens, "3L", CodeTokenType.NUMBER);
        assertToken(source, tokens, "4.0d", CodeTokenType.NUMBER);
        assertToken(source, tokens, "TRUE", CodeTokenType.LITERAL);
        assertToken(source, tokens, "[B;", CodeTokenType.LITERAL);
        assertToken(source, tokens, "unquoted", CodeTokenType.STRING);
        assertToken(source, tokens, "nested", CodeTokenType.KEY);
        assertToken(source, tokens, "list", CodeTokenType.KEY);
        assertToken(source, tokens, "value", CodeTokenType.KEY);
        assertMissingToken("{data:[b;1b]}", "[b;", CodeTokenType.LITERAL);
        assertValidSpans(source, tokens);
    }

    private static void verifyLineEndings() {
        for (String source : List.of("{\n\"key\": 1\n}", "{\r\n\"key\": 1\r\n}")) {
            List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.JSON, source);
            assertToken(source, tokens, "\"key\"", CodeTokenType.KEY);
            assertToken(source, tokens, "1", CodeTokenType.NUMBER);
            assertValidSpans(source, tokens);
        }
    }

    private static void verifyUnicodeOffsets() {
        String source = "a😀é";
        assertEquals("ASCII byte boundary", 1, CodeSyntaxLexer.utf8ByteOffset(source, 1));
        assertEquals("surrogate pair byte boundary", 5, CodeSyntaxLexer.utf8ByteOffset(source, 3));
        assertEquals("accented byte boundary", 7, CodeSyntaxLexer.utf8ByteOffset(source, 4));
        assertEquals("byte within surrogate pair", 1, CodeSyntaxLexer.utf16OffsetAtUtf8Byte(source, 2));
        assertEquals("surrogate pair end", 3, CodeSyntaxLexer.utf16OffsetAtUtf8Byte(source, 5));

        String json = "{\"😀\":\"é\"}";
        List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.JSON, json);
        assertToken(json, tokens, "\"😀\"", CodeTokenType.KEY);
        assertToken(json, tokens, "\"é\"", CodeTokenType.STRING);
        assertValidSpans(json, tokens);
    }

    private static void verifyArbitraryInputProgress() {
        Random random = new Random(0x1A_BC_DE_F0L);
        String alphabet = "{}[],:;\\\"' +-._abcXYZ019eEfFdDbBsSlL\r\n😀é@/";
        for (int sample = 0; sample < 250; sample++) {
            StringBuilder source = new StringBuilder();
            int length = random.nextInt(512);
            for (int index = 0; index < length; index++) {
                int offset = random.nextInt(alphabet.length());
                int codePoint = alphabet.codePointAt(offset);
                if (Character.isLowSurrogate(alphabet.charAt(offset))) codePoint = '?';
                source.appendCodePoint(codePoint);
            }
            for (CodeLanguage language : List.of(CodeLanguage.JSON, CodeLanguage.SNBT)) {
                assertValidSpans(source.toString(), CodeSyntaxLexer.tokenize(language, source.toString()));
            }
        }
    }

    private static void verifyOverlayLayout() {
        String source = "α\n😀x\r\nz";
        CodeToken token = new CodeToken(
            0,
            source.length(),
            0,
            CodeSyntaxLexer.utf8ByteOffset(source, source.length()),
            CodeTokenType.STRING
        );
        List<CodeRenderSpan> spans = CodeOverlayLayout.layout(source, List.of(token));
        assertEquals("multiline span count", 3, spans.size());
        assertRenderSpan(source, spans.get(0), "α", 0, 0);
        assertRenderSpan(source, spans.get(1), "😀x", 1, 2);
        assertRenderSpan(source, spans.get(2), "z", 2, 7);
        assertEquals("first span on line one", 1, CodeOverlayLayout.firstSpanAtOrAfterLine(spans, 1));
        assertEquals("first span after source", 3, CodeOverlayLayout.firstSpanAtOrAfterLine(spans, 3));
    }

    private static void verifyCaretLayout() {
        String source = "a\uD83D\uDE00\r\n\u00E9";
        assertCaret("caret inside surrogate pair", source, 2, 1, 0, 0, 2);
        assertCaret("caret after surrogate pair", source, 5, 3, 0, 0, 3);
        assertCaret("caret after CRLF", source, 7, 5, 1, 5, 1);
        assertCaret("caret after accented glyph", source, 9, 6, 1, 5, 2);
        assertCaret("caret beyond source", source, 999, 6, 1, 5, 2);
    }

    private static void assertCaret(
        String description,
        String source,
        int utf8Offset,
        int expectedOffset,
        int expectedLine,
        int expectedLineStart,
        int expectedColumn
    ) {
        CodeOverlayLayout.CaretPosition caret = CodeOverlayLayout.caretPosition(source, utf8Offset);
        assertEquals(description + " offset", expectedOffset, caret.offset());
        assertEquals(description + " line", expectedLine, caret.line());
        assertEquals(description + " line start", expectedLineStart, caret.lineStart());
        assertEquals(description + " column", expectedColumn, caret.column());
    }

    private static void verifyPaletteContrast() {
        ItemEditorTheme.CodeColor editorBackground = new ItemEditorTheme.CodeColor(
            0.075f,
            0.105f,
            0.150f,
            1.0f
        );
        ItemEditorTheme.CodeColor errorBackground = composite(
            ItemEditorTheme.codeEditorErrorLineColorValue(),
            editorBackground
        );
        for (CodeTokenType type : CodeTokenType.values()) {
            ItemEditorTheme.CodeColor token = ItemEditorTheme.codeTokenColorValue(type);
            assertMinimumContrast(type + " on editor background", token, editorBackground, 4.5);
            assertMinimumContrast(type + " on error background", token, errorBackground, 4.5);
        }
    }

    private static ItemEditorTheme.CodeColor composite(
        ItemEditorTheme.CodeColor foreground,
        ItemEditorTheme.CodeColor background
    ) {
        float alpha = foreground.alpha();
        return new ItemEditorTheme.CodeColor(
            foreground.red() * alpha + background.red() * (1.0f - alpha),
            foreground.green() * alpha + background.green() * (1.0f - alpha),
            foreground.blue() * alpha + background.blue() * (1.0f - alpha),
            1.0f
        );
    }

    private static void assertMinimumContrast(
        String description,
        ItemEditorTheme.CodeColor foreground,
        ItemEditorTheme.CodeColor background,
        double minimum
    ) {
        double foregroundLuminance = relativeLuminance(foreground);
        double backgroundLuminance = relativeLuminance(background);
        double contrast = (Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
            / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
        if (contrast < minimum) {
            throw new AssertionError(description + " contrast was " + contrast + ", expected at least " + minimum);
        }
    }

    private static double relativeLuminance(ItemEditorTheme.CodeColor color) {
        return 0.2126 * linearChannel(color.red())
            + 0.7152 * linearChannel(color.green())
            + 0.0722 * linearChannel(color.blue());
    }

    private static double linearChannel(float channel) {
        return channel <= 0.04045f
            ? channel / 12.92
            : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void measureLargeSource() {
        String row = "{\"key😀\":-1.25e+3,\"enabled\":true,\"value\":\"text\\n\"},\n";
        StringBuilder source = new StringBuilder(270_000);
        source.append('[');
        while (source.length() < 262_144) source.append(row);
        source.append("{}]");
        String largeSource = source.toString();

        long started = System.nanoTime();
        List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.JSON, largeSource);
        List<CodeRenderSpan> spans = CodeOverlayLayout.layout(largeSource, tokens);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        if (tokens.isEmpty() || spans.isEmpty()) throw new AssertionError("Large source produced no highlighting");
        if (elapsedMillis > 5_000L) {
            throw new AssertionError("Large-source highlighting took " + elapsedMillis + " ms");
        }
        System.out.println("Highlighted " + source.length() + " characters into " + spans.size()
            + " render spans in " + elapsedMillis + " ms");
    }

    private static void assertRenderSpan(
        String source,
        CodeRenderSpan span,
        String expectedText,
        int expectedLine,
        int expectedLineStart
    ) {
        if (!source.substring(span.start(), span.end()).equals(expectedText)) {
            throw new AssertionError("Render span text was " + source.substring(span.start(), span.end())
                + ", expected " + expectedText);
        }
        assertEquals("render span line", expectedLine, span.line());
        assertEquals("render span line start", expectedLineStart, span.lineStart());
    }

    private static void assertToken(
        String source,
        List<CodeToken> tokens,
        String expectedText,
        CodeTokenType expectedType
    ) {
        for (CodeToken token : tokens) {
            if (token.type() == expectedType && source.substring(token.start(), token.end()).equals(expectedText)) {
                return;
            }
        }
        throw new AssertionError("Missing " + expectedType + " token: " + expectedText + " in " + tokens);
    }

    private static void assertMissingToken(String source, String text, CodeTokenType type) {
        List<CodeToken> tokens = CodeSyntaxLexer.tokenize(CodeLanguage.SNBT, source);
        for (CodeToken token : tokens) {
            if (token.type() == type && source.substring(token.start(), token.end()).equals(text)) {
                throw new AssertionError("Unexpected " + type + " token: " + text);
            }
        }
    }

    private static void assertValidSpans(String source, List<CodeToken> tokens) {
        int previousEnd = 0;
        for (CodeToken token : tokens) {
            if (token.start() < previousEnd || token.end() > source.length() || token.start() == token.end()) {
                throw new AssertionError("Invalid or overlapping token " + token + " for " + source);
            }
            if (token.start() > 0 && Character.isLowSurrogate(source.charAt(token.start()))) {
                throw new AssertionError("Token starts inside a surrogate pair: " + token);
            }
            if (token.end() < source.length() && Character.isLowSurrogate(source.charAt(token.end()))) {
                throw new AssertionError("Token ends inside a surrogate pair: " + token);
            }
            assertEquals(
                "token start byte offset",
                CodeSyntaxLexer.utf8ByteOffset(source, token.start()),
                token.startUtf8()
            );
            assertEquals(
                "token end byte offset",
                CodeSyntaxLexer.utf8ByteOffset(source, token.end()),
                token.endUtf8()
            );
            previousEnd = token.end();
        }
    }

    private static void assertEquals(String description, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(description + " was " + actual + ", expected " + expected);
        }
    }
}
