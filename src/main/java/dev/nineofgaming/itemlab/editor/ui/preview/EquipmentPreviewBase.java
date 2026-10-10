package dev.nineofgaming.itemlab.editor.ui.preview;

import dev.nineofgaming.itemlab.editor.ui.EditorText;

/**
 * Base model used by the native worn-equipment preview.
 */
public enum EquipmentPreviewBase {
    PLAYER("player"),
    ARMOR_STAND("armor_stand"),
    EQUIPMENT_ONLY("equipment_only");

    private final String translationId;

    EquipmentPreviewBase(String translationId) {
        this.translationId = translationId;
    }

    public String label() {
        return EditorText.tr("equipment.preview.base." + translationId);
    }
}
