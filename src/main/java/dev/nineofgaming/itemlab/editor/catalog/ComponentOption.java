package dev.nineofgaming.itemlab.editor.catalog;

import net.minecraft.core.component.DataComponentType;

public record ComponentOption(String id, String searchable, DataComponentType<?> type) {
}
