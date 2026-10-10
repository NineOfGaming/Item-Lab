package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.config.ItemLabConfig;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.diamondfire.DiamondFireFeatures;
import dev.nineofgaming.itemlab.editor.ui.text.TextFormatUi;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import imgui.ImGui;
import imgui.type.ImBoolean;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.ui.EditorUi.setWrappedTooltip;

/**
 * Global editor preferences and their immediate effects on text editing.
 */
public final class EditorSettingsPanel {
    private static final float SETTINGS_POPUP_WIDTH = 470.0f;
    private final ItemEditor host;
    private final LoreEditorPanel textEditor;
    private final ItemLabConfig preferences;

    public EditorSettingsPanel(ItemEditor host, LoreEditorPanel textEditor, ItemLabConfig preferences) {
        this.host = host;
        this.textEditor = textEditor;
        this.preferences = preferences;
    }

    public void draw() {
        ImGui.setNextWindowSizeConstraints(
            SETTINGS_POPUP_WIDTH,
            0.0f,
            SETTINGS_POPUP_WIDTH,
            Float.MAX_VALUE
        );
        if (!ImGui.beginPopup("editor_settings_popup")) return;
        try {
            ImGui.text(tr("settings.title"));
            ImGui.separator();

            ImGui.separatorText(tr("settings.editor_layout"));
            ImBoolean collapseAbsent = new ImBoolean(preferences.collapseAbsentComponents());
            if (ImGui.checkbox(tr("settings.collapse_absent_components"), collapseAbsent)) {
                preferences.setCollapseAbsentComponents(collapseAbsent.get());
            }
            settingsTooltip(tr("settings.collapse_absent_components.help"));

            ImGui.spacing();
            ImGui.separatorText(tr("settings.text_editing"));
            TextInputFormat textFormat = preferences.textInputFormat();
            TextFormatUi.drawSelector(
                "global_text_input_format",
                textFormat,
                host::switchTextInputFormat,
                false
            );

            ImGui.spacing();
            ImBoolean removeItalics = new ImBoolean(preferences.removeDefaultItalics());
            if (ImGui.checkbox(tr("text.remove_default_italics"), removeItalics)) {
                textEditor.setRemoveDefaultItalics(removeItalics.get());
            }
            settingsTooltip(tr(
                "text.remove_default_italics.help",
                textFormat == TextInputFormat.MINI_MESSAGE
                    ? "<italic> tags"
                    : textFormat.codeMarker() + "o segments"
            ));

            ImGui.spacing();
            ImBoolean whiteLore = new ImBoolean(preferences.useWhiteDefaultLoreColor());
            if (ImGui.checkbox(tr("text.white_default_lore"), whiteLore)) {
                textEditor.setUseWhiteDefaultLoreColor(whiteLore.get());
            }
            settingsTooltip(tr(
                "text.white_default_lore.help",
                textFormat == TextInputFormat.MINI_MESSAGE ? "color tags" : "formatting codes"
            ));

            ImGui.spacing();
            ImBoolean hidePrefixes = new ImBoolean(preferences.hideLeadingFormattingPrefixes());
            if (ImGui.checkbox(tr("text.hide_leading_prefixes"), hidePrefixes)) {
                textEditor.setHideLeadingFormattingPrefixes(hidePrefixes.get());
            }
            settingsTooltip(tr("text.hide_leading_prefixes.help"));

            ImGui.spacing();
            ImGui.separatorText(tr("settings.inventory_tooltips"));
            ImBoolean showEditHint = new ImBoolean(preferences.showInventoryEditHint());
            if (ImGui.checkbox(tr("settings.show_inventory_edit_hint"), showEditHint)) {
                preferences.setShowInventoryEditHint(showEditHint.get());
            }
            settingsTooltip(tr("settings.show_inventory_edit_hint.help"));

            if (DiamondFireFeatures.available()) {
                ImGui.spacing();
                ImGui.separatorText(tr("settings.diamondfire"));
                ImBoolean forceDiamondFire = new ImBoolean(preferences.forceDiamondFireCompatibility());
                if (ImGui.checkbox(tr("settings.force_diamondfire_compatibility"), forceDiamondFire)) {
                    preferences.setForceDiamondFireCompatibility(forceDiamondFire.get());
                }
                settingsTooltip(tr("settings.force_diamondfire_compatibility.help"));
            }
        } finally {
            ImGui.endPopup();
        }
    }

    private static void settingsTooltip(String text) {
        if (ImGui.isItemHovered()) setWrappedTooltip(text);
    }

}
