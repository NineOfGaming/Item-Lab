package dev.nineofgaming.itemlab.editor.component;

import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

/**
 * Complete rendering metadata for one generated component field.
 */
record ComponentFieldContext(
    String componentId,
    DataComponentType<?> componentType,
    String jsonPath,
    String uiPath,
    OrderedListDragDrop.Scope uiScope,
    String fieldKey,
    String displayLabel,
    String description,
    ComponentSchemas.Field schemaMetadata,
    List<IdSuggestion> registrySuggestions,
    String purposeHint,
    String syntaxExample,
    boolean required,
    boolean removable,
    boolean custom
) {
    ComponentFieldContext {
        registrySuggestions = registrySuggestions == null ? List.of() : List.copyOf(registrySuggestions);
        purposeHint = purposeHint == null ? "" : purposeHint;
        syntaxExample = syntaxExample == null ? "" : syntaxExample;
    }

    String stableId() {
        return componentId + "@" + uiPath;
    }

    String stateLabel() {
        if (required) return "required";
        if (custom) return "custom";
        if (schemaMetadata == null) return "codec";
        return "optional";
    }
}
