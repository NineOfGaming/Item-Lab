package dev.nineofgaming.itemlab.editor.ui.text;

import dev.nineofgaming.itemlab.editor.ui.EditorText;

/**
 * Text syntaxes shared by names, lore, tooltip copy and book text.
 */
public enum TextInputFormat {
    MINI_MESSAGE("minimessage", '\0', "https://docs.papermc.io/adventure/minimessage/format/"),
    COLOR_CODES("color_codes", '&', "https://minecraft.wiki/w/Formatting_codes"),
    SECTION_CODES("section_codes", '§', "https://minecraft.wiki/w/Formatting_codes");

    private final String translationId;
    private final char codeMarker;
    private final String documentationUrl;

    TextInputFormat(String translationId, char codeMarker, String documentationUrl) {
        this.translationId = translationId;
        this.codeMarker = codeMarker;
        this.documentationUrl = documentationUrl;
    }

    public String label() {
        return EditorText.tr("text.format." + translationId);
    }

    public String colorHelp() {
        return EditorText.tr("text.format." + translationId + ".colors");
    }

    public String styleHelp() {
        return EditorText.tr("text.format." + translationId + ".styles");
    }

    public String documentationLabel() {
        return EditorText.tr("text.format." + translationId + ".documentation");
    }

    public String documentationUrl() {
        return documentationUrl;
    }

    public boolean usesFormattingCodes() {
        return codeMarker != '\0';
    }

    public char codeMarker() {
        if (!usesFormattingCodes()) {
            throw new IllegalStateException(label() + " does not use a formatting-code marker");
        }
        return codeMarker;
    }
}
