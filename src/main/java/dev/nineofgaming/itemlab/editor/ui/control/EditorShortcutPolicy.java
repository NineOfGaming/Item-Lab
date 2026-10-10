package dev.nineofgaming.itemlab.editor.ui.control;

/**
 * Shared policy for editor-level shortcuts while a control owns keyboard input.
 */
public final class EditorShortcutPolicy {
    private EditorShortcutPolicy() {
    }

    public static boolean suppresses(boolean anyItemActive, boolean wantsTextInput) {
        return anyItemActive || wantsTextInput;
    }
}
