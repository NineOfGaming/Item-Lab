package dev.nineofgaming.itemlab.editor.ui;

import net.minecraft.client.resources.language.I18n;

/**
 * Central access to Item Lab's client language entries for ImGui labels.
 */
public final class EditorText {
    private static final String PREFIX = "itemlab.";

    private EditorText() {
    }

    public static String tr(String key, Object... arguments) {
        return I18n.get(PREFIX + key, arguments);
    }
}
