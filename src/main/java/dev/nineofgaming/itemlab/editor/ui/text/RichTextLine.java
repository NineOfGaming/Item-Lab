package dev.nineofgaming.itemlab.editor.ui.text;

import imgui.type.ImString;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;

/**
 * Editable Minecraft text represented in one of the supported input syntaxes.
 */
public final class RichTextLine {
    private static final String LEGACY_COLOR_CODES = "0123456789abcdef";
    private static final String[] LEADING_MINI_MESSAGE_DEFAULTS = {
        "<!italic>",
        "<!underlined>",
        "<!strikethrough>",
        "<!bold>",
        "<!obfuscated>"
    };
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final TextColor[] TEXT_COLOR_VALUES = {
        null,
        TextColor.fromRgb(0x000000), TextColor.fromRgb(0x0000AA),
        TextColor.fromRgb(0x00AA00), TextColor.fromRgb(0x00AAAA),
        TextColor.fromRgb(0xAA0000), TextColor.fromRgb(0xAA00AA),
        TextColor.fromRgb(0xFFAA00), TextColor.fromRgb(0xAAAAAA),
        TextColor.fromRgb(0x555555), TextColor.fromRgb(0x5555FF),
        TextColor.fromRgb(0x55FF55), TextColor.fromRgb(0x55FFFF),
        TextColor.fromRgb(0xFF5555), TextColor.fromRgb(0xFF55FF),
        TextColor.fromRgb(0xFFFF55), TextColor.fromRgb(0xFFFFFF)
    };

    private final ImString source;
    private final ImString editorSource;
    private final Component original;
    private TextInputFormat format;
    private String concealedEditorPrefix = "";
    public boolean changed;

    private RichTextLine(Component component, boolean changed, TextInputFormat format) {
        original = component;
        this.changed = changed;
        this.format = format;
        String encoded = encode(component, format);
        source = new ImString(encoded, Math.max(4_096, encoded.length() * 2 + 64));
        editorSource = new ImString(encoded, Math.max(4_096, encoded.length() * 2 + 64));
    }

    public static RichTextLine from(Component component, TextInputFormat format) {
        return new RichTextLine(component, false, format);
    }

    /**
     * Reads a legacy-formatted string field such as a written-book title or author.
     */
    public static RichTextLine fromLegacyString(String value, TextInputFormat format) {
        return new RichTextLine(parseColorCodes(value == null ? "" : value, '§'), false, format);
    }

    public static RichTextLine createEmpty(TextInputFormat format) {
        return new RichTextLine(null, true, format);
    }

    /**
     * Copies both the saved source and any currently invalid in-progress source exactly.
     */
    public RichTextLine copy() {
        RichTextLine copy = new RichTextLine(original, changed, format);
        copy.source.set(source.get());
        copy.editorSource.set(editorSource.get());
        copy.concealedEditorPrefix = concealedEditorPrefix;
        return copy;
    }

    /**
     * Returns whether two lines contain the exact same editable value.
     */
    public boolean sameEditorState(RichTextLine other) {
        return other != null
            && format == other.format
            && source.get().equals(other.source.get());
    }

    /**
     * Copies this line while converting its editable value to another syntax.
     */
    public RichTextLine convertedCopy(TextInputFormat targetFormat) {
        if (targetFormat == format) {
            return copy();
        }
        RichTextLine copy = copy();
        copy.switchFormat(targetFormat, convertedSource(targetFormat));
        return copy;
    }

    public boolean isBlank() {
        return source.get().isEmpty();
    }

    public TextInputFormat format() {
        return format;
    }

    /**
     * Returns an editable view which may conceal generated formatting at the start.
     */
    public ImString editorSource(boolean hideLeadingDefaults, boolean hideWhiteDefault) {
        String completeSource = source.get();
        String concealedPrefix = hideLeadingDefaults
            ? leadingFormattingPrefix(completeSource, hideWhiteDefault)
            : "";
        String visibleSource = completeSource.substring(concealedPrefix.length());
        if (!concealedEditorPrefix.equals(concealedPrefix) || !editorSource.get().equals(visibleSource)) {
            concealedEditorPrefix = concealedPrefix;
            editorSource.set(visibleSource);
        }
        return editorSource;
    }

    /**
     * Commits the visible editor buffer while retaining its concealed prefix exactly.
     */
    public void commitEditorSource() {
        source.set(concealedEditorPrefix + editorSource.get());
        changed = true;
    }

    /**
     * Converts the editor text to a native Minecraft component for saving.
     */
    public Component toComponent(boolean disableDefaultItalics) {
        return toComponent(disableDefaultItalics, false);
    }

    /**
     * Converts the editor text while applying optional item-text defaults at its root.
     */
    public Component toComponent(boolean disableDefaultItalics, boolean useWhiteDefaultColor) {
        Component component = rawComponent();
        return disableDefaultItalics || useWhiteDefaultColor
            ? withBaseDefaults(component, disableDefaultItalics, useWhiteDefaultColor)
            : component;
    }

    /**
     * Converts text for the live preview without allowing an unfinished tag to break a frame.
     */
    public Component toPreviewComponent(boolean disableDefaultItalics) {
        return toPreviewComponent(disableDefaultItalics, false);
    }

    public Component toPreviewComponent(boolean disableDefaultItalics, boolean useWhiteDefaultColor) {
        try {
            return toComponent(disableDefaultItalics, useWhiteDefaultColor);
        } catch (RuntimeException ignored) {
            return original != null ? original : Component.literal(source.get());
        }
    }

    /**
     * Compiles rich input into the legacy formatting supported by Minecraft string fields.
     */
    public String toLegacyString() {
        return encodeLegacyCodes(rawComponent());
    }

    public String toLegacyPreviewString() {
        try {
            return toLegacyString();
        } catch (RuntimeException ignored) {
            return original != null ? encodeLegacyCodes(original) : source.get();
        }
    }

    public String validationError() {
        if (!changed) {
            return "";
        }
        try {
            rawComponent();
            return "";
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        }
    }

    /**
     * Produces the equivalent source in another syntax without mutating this line.
     */
    public String convertedSource(TextInputFormat targetFormat) {
        return targetFormat == format ? source.get() : encode(rawComponent(), targetFormat);
    }

    /**
     * Completes a previously validated format conversion.
     */
    public void switchFormat(TextInputFormat targetFormat, String convertedSource) {
        source.set(convertedSource);
        format = targetFormat;
    }

    private String leadingFormattingPrefix(String completeSource, boolean hideWhiteDefault) {
        if (format.usesFormattingCodes()) {
            String whitePrefix = String.valueOf(format.codeMarker()) + 'f';
            return hideWhiteDefault && completeSource.startsWith(whitePrefix) ? whitePrefix : "";
        }

        int length = 0;
        boolean matched;
        do {
            matched = false;
            if (hideWhiteDefault && completeSource.startsWith("<white>", length)) {
                length += "<white>".length();
                matched = true;
                continue;
            }
            for (String prefix : LEADING_MINI_MESSAGE_DEFAULTS) {
                if (completeSource.startsWith(prefix, length)) {
                    length += prefix.length();
                    matched = true;
                    break;
                }
            }
        } while (matched);
        return completeSource.substring(0, length);
    }

    private Component rawComponent() {
        return !changed && original != null ? original : parse(source.get(), format);
    }

    private static Component parse(String source, TextInputFormat format) {
        return switch (format) {
            case COLOR_CODES, SECTION_CODES -> parseColorCodes(source, format.codeMarker());
            case MINI_MESSAGE -> MinecraftClientAudiences.of().nonWrappingSerializer().serialize(
                MINI_MESSAGE.deserialize(source)
            );
        };
    }

    private static Component parseColorCodes(String source, char codeMarker) {
        MutableComponent result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;

        for (int index = 0; index < source.length(); index++) {
            char marker = source.charAt(index);
            if (marker != codeMarker || index + 1 >= source.length()) {
                buffer.append(marker);
                continue;
            }

            char rawCode = source.charAt(index + 1);
            if (rawCode == marker) {
                buffer.append(marker);
                index++;
                continue;
            }

            char code = Character.toLowerCase(rawCode);
            if (code == '#' && index + 7 < source.length()) {
                String hex = source.substring(index + 2, index + 8);
                if (isHexColor(hex)) {
                    appendStyledText(result, buffer, style);
                    style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hex, 16)));
                    index += 7;
                    continue;
                }
            }

            if (code == 'x') {
                int cursor = index + 2;
                StringBuilder hex = new StringBuilder(6);
                while (hex.length() < 6
                    && cursor + 1 < source.length()
                    && source.charAt(cursor) == codeMarker
                    && Character.digit(source.charAt(cursor + 1), 16) >= 0) {
                    hex.append(source.charAt(cursor + 1));
                    cursor += 2;
                }
                if (hex.length() == 6) {
                    appendStyledText(result, buffer, style);
                    style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hex.toString(), 16)));
                    index = cursor - 1;
                    continue;
                }
            }

            int colorIndex = LEGACY_COLOR_CODES.indexOf(code);
            if (colorIndex >= 0) {
                appendStyledText(result, buffer, style);
                style = Style.EMPTY.withColor(TEXT_COLOR_VALUES[colorIndex + 1]);
                index++;
                continue;
            }

            Style updatedStyle = switch (code) {
                case 'k' -> style.withObfuscated(true);
                case 'l' -> style.withBold(true);
                case 'm' -> style.withStrikethrough(true);
                case 'n' -> style.withUnderlined(true);
                case 'o' -> style.withItalic(true);
                case 'r' -> Style.EMPTY;
                default -> null;
            };
            if (updatedStyle != null) {
                appendStyledText(result, buffer, style);
                style = updatedStyle;
                index++;
            } else {
                buffer.append(marker);
            }
        }

        appendStyledText(result, buffer, style);
        return result;
    }

    private static void appendStyledText(MutableComponent target, StringBuilder buffer, Style style) {
        if (!buffer.isEmpty()) {
            target.append(Component.literal(buffer.toString()).withStyle(style));
            buffer.setLength(0);
        }
    }

    private static boolean isHexColor(String value) {
        if (value.length() != 6) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String encode(Component component, TextInputFormat format) {
        if (component == null) {
            return "";
        }
        if (format == TextInputFormat.MINI_MESSAGE) {
            return MINI_MESSAGE.serialize(MinecraftClientAudiences.of().asAdventure(component));
        }
        return encodeColorCodes(component, format.codeMarker());
    }

    private static String encodeColorCodes(Component component, char codeMarker) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Component part : component.toFlatList(Style.EMPTY)) {
            String text = part.getString();
            if (text.isEmpty()) {
                continue;
            }
            if (!first) {
                result.append(codeMarker).append('r');
            }
            appendStyleCodes(result, part.getStyle(), codeMarker);
            String marker = String.valueOf(codeMarker);
            result.append(text.replace(marker, marker + marker));
            first = false;
        }
        return result.toString();
    }

    private static void appendStyleCodes(StringBuilder target, Style style, char codeMarker) {
        TextColor color = style.getColor();
        if (color != null) {
            int knownColor = -1;
            for (int index = 1; index < TEXT_COLOR_VALUES.length; index++) {
                if (color.equals(TEXT_COLOR_VALUES[index])) {
                    knownColor = index - 1;
                    break;
                }
            }
            if (knownColor >= 0) {
                target.append(codeMarker).append(LEGACY_COLOR_CODES.charAt(knownColor));
            } else {
                target.append(codeMarker)
                    .append('#')
                    .append(String.format(Locale.ROOT, "%06X", color.getValue() & 0xFFFFFF));
            }
        }
        if (style.isObfuscated()) target.append(codeMarker).append('k');
        if (style.isBold()) target.append(codeMarker).append('l');
        if (style.isStrikethrough()) target.append(codeMarker).append('m');
        if (style.isUnderlined()) target.append(codeMarker).append('n');
        if (style.isItalic()) target.append(codeMarker).append('o');
    }

    private static String encodeLegacyCodes(Component component) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Component part : component.toFlatList(Style.EMPTY)) {
            String text = part.getString();
            if (text.isEmpty()) continue;
            if (!first) result.append('§').append('r');
            appendLegacyStyleCodes(result, part.getStyle());
            result.append(text);
            first = false;
        }
        return result.toString();
    }

    private static void appendLegacyStyleCodes(StringBuilder target, Style style) {
        TextColor color = style.getColor();
        if (color != null) {
            int nearest = nearestLegacyColor(color.getValue());
            target.append('§').append(LEGACY_COLOR_CODES.charAt(nearest));
        }
        if (style.isObfuscated()) target.append('§').append('k');
        if (style.isBold()) target.append('§').append('l');
        if (style.isStrikethrough()) target.append('§').append('m');
        if (style.isUnderlined()) target.append('§').append('n');
        if (style.isItalic()) target.append('§').append('o');
    }

    private static int nearestLegacyColor(int rgb) {
        int red = rgb >>> 16 & 0xFF;
        int green = rgb >>> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int nearest = 0;
        long nearestDistance = Long.MAX_VALUE;
        for (int index = 1; index < TEXT_COLOR_VALUES.length; index++) {
            int candidate = TEXT_COLOR_VALUES[index].getValue();
            int redDifference = red - (candidate >>> 16 & 0xFF);
            int greenDifference = green - (candidate >>> 8 & 0xFF);
            int blueDifference = blue - (candidate & 0xFF);
            long distance = (long) redDifference * redDifference
                + (long) greenDifference * greenDifference
                + (long) blueDifference * blueDifference;
            if (distance < nearestDistance) {
                nearest = index - 1;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static Component withBaseDefaults(
        Component component,
        boolean disableDefaultItalics,
        boolean useWhiteDefaultColor
    ) {
        MinecraftClientAudiences audiences = MinecraftClientAudiences.of();
        net.kyori.adventure.text.Component adventure = audiences.asAdventure(component);
        if (disableDefaultItalics
            && adventure.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            adventure = adventure.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }
        if (useWhiteDefaultColor && adventure.color() == null) {
            adventure = adventure.color(NamedTextColor.WHITE);
        }
        return audiences.nonWrappingSerializer().serialize(adventure);
    }
}
