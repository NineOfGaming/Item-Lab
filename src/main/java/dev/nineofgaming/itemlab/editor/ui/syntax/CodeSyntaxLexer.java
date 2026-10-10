package dev.nineofgaming.itemlab.editor.ui.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lightweight, error-tolerant presentation lexers for raw JSON and SNBT input.
 */
public final class CodeSyntaxLexer {
    private static final Pattern SNBT_INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern SNBT_BYTE = Pattern.compile("[-+]?\\d+[bB]");
    private static final Pattern SNBT_SHORT = Pattern.compile("[-+]?\\d+[sS]");
    private static final Pattern SNBT_LONG = Pattern.compile("[-+]?\\d+[lL]");
    private static final Pattern SNBT_FLOAT = Pattern.compile(
            "[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?[fF]"
    );
    private static final Pattern SNBT_DOUBLE_SUFFIX = Pattern.compile(
            "[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?[dD]"
    );
    private static final Pattern SNBT_DOUBLE = Pattern.compile(
        "[-+]?(?:(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?|\\d+[eE][-+]?\\d+)"
    );

    private CodeSyntaxLexer() {
    }

    public static List<CodeToken> tokenize(CodeLanguage language, String source) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(source, "source");
        TokenBuilder tokens = new TokenBuilder(source);
        switch (language) {
            case PLAIN_TEXT -> {
                return List.of();
            }
            case JSON -> lexJson(source, tokens);
            case SNBT -> lexSnbt(source, tokens);
        }
        return tokens.finish();
    }

    /**
     * Converts a Java string boundary to its UTF-8 byte offset.
     */
    public static int utf8ByteOffset(String source, int utf16Offset) {
        Objects.requireNonNull(source, "source");
        int requested = Math.clamp(utf16Offset, 0, source.length());
        int utf8 = 0;
        for (int index = 0; index < requested; ) {
            int codePoint = source.codePointAt(index);
            int chars = Character.charCount(codePoint);
            if (index + chars > requested) break;
            utf8 += utf8Length(codePoint);
            index += chars;
        }
        return utf8;
    }

    /**
     * Converts a UTF-8 byte offset to the preceding complete Java string boundary.
     */
    public static int utf16OffsetAtUtf8Byte(String source, int utf8ByteOffset) {
        Objects.requireNonNull(source, "source");
        int requested = Math.max(0, utf8ByteOffset);
        int consumed = 0;
        int index = 0;
        while (index < source.length()) {
            int codePoint = source.codePointAt(index);
            int bytes = utf8Length(codePoint);
            if (consumed + bytes > requested) break;
            consumed += bytes;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static void lexJson(String source, TokenBuilder tokens) {
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '"') {
                int end = quotedEnd(source, index, '"');
                CodeTokenType type = followedByColon(source, end) ? CodeTokenType.KEY : CodeTokenType.STRING;
                addQuotedTokens(source, index, end, type, tokens);
                index = end;
                continue;
            }
            if (isJsonNumberStart(source, index)) {
                int end = jsonNumberEnd(source, index);
                tokens.add(index, end, CodeTokenType.NUMBER);
                index = end;
                continue;
            }
            if (isAsciiLetter(current)) {
                int end = index + 1;
                while (end < source.length() && isAsciiLetter(source.charAt(end))) end++;
                String word = source.substring(index, end);
                CodeTokenType type = word.equals("true") || word.equals("false") || word.equals("null")
                    ? CodeTokenType.LITERAL
                    : CodeTokenType.UNCLASSIFIED;
                tokens.add(index, end, type);
                index = end;
                continue;
            }
            if (isJsonPunctuation(current)) {
                tokens.add(index, index + 1, CodeTokenType.PUNCTUATION);
                index++;
                continue;
            }
            int end = index + Character.charCount(source.codePointAt(index));
            tokens.add(index, end, CodeTokenType.UNCLASSIFIED);
            index = end;
        }
    }

    private static void lexSnbt(String source, TokenBuilder tokens) {
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '"' || current == '\'') {
                int end = quotedEnd(source, index, current);
                CodeTokenType type = followedByColon(source, end) ? CodeTokenType.KEY : CodeTokenType.STRING;
                addQuotedTokens(source, index, end, type, tokens);
                index = end;
                continue;
            }
            if (isTypedArrayPrefix(source, index)) {
                tokens.add(index, index + 3, CodeTokenType.LITERAL);
                index += 3;
                continue;
            }
            if (isSnbtUnquoted(current)) {
                int end = index + 1;
                while (end < source.length() && isSnbtUnquoted(source.charAt(end))) end++;
                String word = source.substring(index, end);
                CodeTokenType type;
                if (followedByColon(source, end)) {
                    type = CodeTokenType.KEY;
                } else if (word.equalsIgnoreCase("true") || word.equalsIgnoreCase("false")) {
                    type = CodeTokenType.LITERAL;
                } else if (isSnbtNumber(word)) {
                    type = CodeTokenType.NUMBER;
                } else {
                    type = CodeTokenType.STRING;
                }
                tokens.add(index, end, type);
                index = end;
                continue;
            }
            if (isSnbtPunctuation(current)) {
                tokens.add(index, index + 1, CodeTokenType.PUNCTUATION);
                index++;
                continue;
            }
            int end = index + Character.charCount(source.codePointAt(index));
            tokens.add(index, end, CodeTokenType.UNCLASSIFIED);
            index = end;
        }
    }

    private static void addQuotedTokens(
        String source,
        int start,
        int end,
        CodeTokenType stringType,
        TokenBuilder tokens
    ) {
        int segmentStart = start;
        int index = start + 1;
        while (index < end) {
            char current = source.charAt(index);
            if (current == '\\') {
                if (segmentStart < index) tokens.add(segmentStart, index, stringType);
                int escapeEnd = index + 1;
                if (escapeEnd < end && source.charAt(escapeEnd) == 'u') {
                    escapeEnd++;
                    int digits = 0;
                    while (escapeEnd < end && digits < 4 && isHexDigit(source.charAt(escapeEnd))) {
                        escapeEnd++;
                        digits++;
                    }
                } else if (escapeEnd < end) {
                    escapeEnd += Character.charCount(source.codePointAt(escapeEnd));
                }
                tokens.add(index, escapeEnd, CodeTokenType.ESCAPE);
                index = escapeEnd;
                segmentStart = index;
                continue;
            }
            index += Character.charCount(source.codePointAt(index));
        }
        if (segmentStart < end) tokens.add(segmentStart, end, stringType);
    }

    private static int quotedEnd(String source, int start, char quote) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\\') {
                index = Math.min(source.length(), index + 2);
                continue;
            }
            if (current == quote) return index + 1;
            if (current == '\n' || current == '\r') return index;
            index += Character.charCount(source.codePointAt(index));
        }
        return source.length();
    }

    private static boolean followedByColon(String source, int index) {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        return index < source.length() && source.charAt(index) == ':';
    }

    private static boolean isJsonNumberStart(String source, int index) {
        char current = source.charAt(index);
        return Character.isDigit(current)
            || current == '-' && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1));
    }

    private static int jsonNumberEnd(String source, int start) {
        int index = start;
        if (source.charAt(index) == '-') index++;
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        if (index < source.length() && source.charAt(index) == '.') {
            do index++;
            while (index < source.length() && Character.isDigit(source.charAt(index)));
        }
        if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            index++;
            if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        }
        return index;
    }

    private static boolean isTypedArrayPrefix(String source, int index) {
        if (index + 2 >= source.length() || source.charAt(index) != '[' || source.charAt(index + 2) != ';') {
            return false;
        }
        char type = source.charAt(index + 1);
        return type == 'B' || type == 'I' || type == 'L';
    }

    private static boolean isSnbtNumber(String value) {
        return SNBT_INTEGER.matcher(value).matches()
            || SNBT_BYTE.matcher(value).matches()
            || SNBT_SHORT.matcher(value).matches()
            || SNBT_LONG.matcher(value).matches()
            || SNBT_FLOAT.matcher(value).matches()
            || SNBT_DOUBLE_SUFFIX.matcher(value).matches()
            || SNBT_DOUBLE.matcher(value).matches();
    }

    private static boolean isAsciiLetter(char value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z';
    }

    private static boolean isHexDigit(char value) {
        return value >= '0' && value <= '9'
            || value >= 'a' && value <= 'f'
            || value >= 'A' && value <= 'F';
    }

    private static boolean isJsonPunctuation(char value) {
        return value == '{' || value == '}' || value == '[' || value == ']'
            || value == ',' || value == ':';
    }

    private static boolean isSnbtPunctuation(char value) {
        return isJsonPunctuation(value) || value == ';';
    }

    private static boolean isSnbtUnquoted(char value) {
        return value >= '0' && value <= '9'
            || value >= 'A' && value <= 'Z'
            || value >= 'a' && value <= 'z'
            || value == '_' || value == '-' || value == '.' || value == '+';
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }

    private static final class TokenBuilder {
        private final int[] utf8Offsets;
        private final List<CodeToken> tokens = new ArrayList<>();

        private TokenBuilder(String source) {
            utf8Offsets = new int[source.length() + 1];
            int utf8 = 0;
            for (int index = 0; index < source.length(); ) {
                int codePoint = source.codePointAt(index);
                int chars = Character.charCount(codePoint);
                utf8Offsets[index] = utf8;
                for (int interior = 1; interior < chars; interior++) {
                    utf8Offsets[index + interior] = utf8;
                }
                utf8 += utf8Length(codePoint);
                index += chars;
                utf8Offsets[index] = utf8;
            }
        }

        private void add(int start, int end, CodeTokenType type) {
            if (start >= end) return;
            int size = tokens.size();
            if (size > 0) {
                CodeToken previous = tokens.get(size - 1);
                if (previous.end() == start && previous.type() == type) {
                    tokens.set(size - 1, new CodeToken(
                        previous.start(),
                        end,
                        previous.startUtf8(),
                        utf8Offsets[end],
                        type
                    ));
                    return;
                }
            }
            tokens.add(new CodeToken(start, end, utf8Offsets[start], utf8Offsets[end], type));
        }

        private List<CodeToken> finish() {
            return List.copyOf(tokens);
        }
    }
}
