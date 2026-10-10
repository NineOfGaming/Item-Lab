package dev.nineofgaming.itemlab.editor.panel;

import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.concurrent.atomic.AtomicLong;

final class EnchantmentRow {
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong();

    final long rowId;
    final ImString id;
    final ImInt level;

    EnchantmentRow(String id, int level) {
        this.rowId = NEXT_ROW_ID.incrementAndGet();
        this.id = new ImString(id, 512);
        this.level = new ImInt(level);
    }
}
