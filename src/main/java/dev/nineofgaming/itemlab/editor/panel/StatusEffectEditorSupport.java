package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import java.util.Locale;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Display text shared by potion, food, and death-protection effect editors.
 */
final class StatusEffectEditorSupport {
    private StatusEffectEditorSupport() {
    }

    static String effectName(JsonObject effect) {
        String idText = string(effect, "id", "minecraft:speed");
        Identifier id = Identifier.tryParse(idText);
        if (id == null) return idText;
        MobEffect installed = BuiltInRegistries.MOB_EFFECT.getValue(id);
        return installed == null ? idText : installed.getDisplayName().getString();
    }

    static String durationSummary(int ticks) {
        if (ticks < 0) return tr("potion.effect.duration.infinite");
        int totalSeconds = ticks / 20;
        int hours = totalSeconds / 3600;
        int minutes = totalSeconds % 3600 / 60;
        int seconds = totalSeconds % 60;
        String clock = hours > 0
            ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
        return tr("potion.effect.duration.summary", clock, ticks);
    }
}
