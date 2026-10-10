package dev.nineofgaming.itemlab.editor.ui.preview;

import dev.nineofgaming.itemlab.editor.ui.EditorText;

/**
 * Portion of a player skin shown by the profile preview.
 */
public enum PlayerSkinPreviewMode {
    HEAD_ONLY("head"),
    FULL_SKIN("full");

    private final String translationId;

    PlayerSkinPreviewMode(String translationId) {
        this.translationId = translationId;
    }

    public String label() {
        return EditorText.tr("player_head.preview.mode." + translationId);
    }
}
