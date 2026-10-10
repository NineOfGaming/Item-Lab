package dev.nineofgaming.itemlab.editor.ui.control;

/**
 * Separates an empty control's semantic purpose from its optional value-shaped syntax help.
 */
public record FieldHints(String purposeHint, String syntaxExample) {
    public FieldHints {
        purposeHint = purposeHint == null ? "" : purposeHint;
        syntaxExample = syntaxExample == null ? "" : syntaxExample;
    }

    public static FieldHints purpose(String purposeHint) {
        return new FieldHints(purposeHint, "");
    }
}
