package dev.nineofgaming.itemlab.editor.ui.preview;

import com.google.gson.annotations.SerializedName;
import dev.nineofgaming.itemlab.editor.ui.EditorText;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Vanilla display context represented by the appearance editor's live preview.
 */
public enum ItemModelPreviewMode {
    @SerializedName(value = "FIXED", alternate = "NONE")
    FIXED("fixed", ItemDisplayContext.FIXED),
    GUI("gui", null),
    THIRD_PERSON_RIGHT_HAND("third_person_right", null),
    THIRD_PERSON_LEFT_HAND("third_person_left", null),
    FIRST_PERSON_RIGHT_HAND("first_person_right", ItemDisplayContext.FIRST_PERSON_RIGHT_HAND),
    FIRST_PERSON_LEFT_HAND("first_person_left", ItemDisplayContext.FIRST_PERSON_LEFT_HAND),
    HEAD("head", null),
    GROUND("ground", null),
    FRAME("frame", null),
    ON_SHELF("shelf", ItemDisplayContext.ON_SHELF);

    private final String translationId;
    private final ItemDisplayContext directContext;

    ItemModelPreviewMode(String translationId, ItemDisplayContext directContext) {
        this.translationId = translationId;
        this.directContext = directContext;
    }

    public String label() {
        return EditorText.tr("appearance.model_preview.mode." + translationId);
    }

    public boolean usesDirectModel() {
        return directContext != null;
    }

    public ItemDisplayContext directContext() {
        return directContext;
    }

    public boolean isFirstPerson() {
        return this == FIRST_PERSON_RIGHT_HAND || this == FIRST_PERSON_LEFT_HAND;
    }

    public boolean isRotatable() {
        return this != GUI && !isFirstPerson();
    }
}
