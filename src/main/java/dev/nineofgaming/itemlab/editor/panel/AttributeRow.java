package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.ui.text.RichTextLine;
import dev.nineofgaming.itemlab.editor.ui.text.TextInputFormat;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicLong;

final class AttributeRow {
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();

    final long rowId;
    final ImString attributeId;
    final ImString modifierId;
    final ImDouble amount;
    final ImInt operation;
    final ImInt slot;
    final ImInt display;
    final RichTextLine displayText;

    AttributeRow(
        String attributeId,
        String modifierId,
        double amount,
        int operation,
        int slot,
        int display,
        Component displayText,
        TextInputFormat textFormat
    ) {
        this.rowId = NEXT_ROW_ID.incrementAndGet();
        this.attributeId = new ImString(attributeId, 512);
        this.modifierId = new ImString(modifierId, 512);
        this.amount = new ImDouble(amount);
        this.operation = new ImInt(operation);
        this.slot = new ImInt(slot);
        this.display = new ImInt(display);
        this.displayText = RichTextLine.from(displayText, textFormat);
    }

    private AttributeRow(
        String attributeId,
        String modifierId,
        double amount,
        int operation,
        int slot,
        int display,
        RichTextLine displayText
    ) {
        this.rowId = NEXT_ROW_ID.incrementAndGet();
        this.attributeId = new ImString(attributeId, 512);
        this.modifierId = new ImString(modifierId, 512);
        this.amount = new ImDouble(amount);
        this.operation = new ImInt(operation);
        this.slot = new ImInt(slot);
        this.display = new ImInt(display);
        this.displayText = displayText;
    }

    static AttributeRow createDefault(int index, TextInputFormat textFormat) {
        return new AttributeRow(
            "minecraft:attack_damage",
            "itemlab:modifier_" + (index + 1),
            1.0,
            0,
            1,
            0,
            Component.empty(),
            textFormat
        );
    }

    AttributeRow copy(String copiedModifierId) {
        return new AttributeRow(
            attributeId.get(),
            copiedModifierId,
            amount.get(),
            operation.get(),
            slot.get(),
            display.get(),
            displayText.copy()
        );
    }
}
