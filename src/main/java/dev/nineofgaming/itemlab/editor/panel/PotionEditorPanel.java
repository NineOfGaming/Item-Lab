package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.catalog.EditorCatalog;
import dev.nineofgaming.itemlab.editor.catalog.IdSuggestion;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.control.ColorSelector;
import dev.nineofgaming.itemlab.editor.ui.control.FieldHints;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.ui.list.JsonListMutations;
import dev.nineofgaming.itemlab.editor.ui.list.OrderedListDragDrop;
import imgui.ImGui;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.array;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.bool;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.integer;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.primitiveDouble;
import static dev.nineofgaming.itemlab.editor.component.JsonComponentValues.string;
import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;
import static dev.nineofgaming.itemlab.editor.panel.StatusEffectEditorSupport.durationSummary;
import static dev.nineofgaming.itemlab.editor.panel.StatusEffectEditorSupport.effectName;
import static dev.nineofgaming.itemlab.editor.ui.control.EditorInput.inputTextWithHint;

/**
 * Purpose-built editor for potion components with native item and tooltip previews.
 */
public final class PotionEditorPanel extends ComponentEditorPanel {
    private static final int MAX_HIDDEN_EFFECT_DEPTH = 8;
    private static final AtomicLong NEXT_EFFECT_ROW_ID = new AtomicLong();

    private final CodecComponentState contents = new CodecComponentState(
        DataComponents.POTION_CONTENTS,
        "{\"potion\":\"minecraft:water\",\"custom_effects\":[]}"
    );
    private final CodecComponentState durationScale = new CodecComponentState(
        DataComponents.POTION_DURATION_SCALE,
        "1.0"
    );
    private final List<Long> effectRowIds = new ArrayList<>();
    private List<IdSuggestion> potionIds = List.of();
    private List<IdSuggestion> effectIds = List.of();
    private long focusEffectRowId;
    private boolean focusAddEffectButton;
    private OrderedListDragDrop.Scope effectDragScope = OrderedListDragDrop.newScope("potion_effects");

    public PotionEditorPanel(ItemEditor host) {
        super(host, "potion");
    }

    @Override
    public void requestPendingFocus() {
        focusEffectRowId = 0L;
        focusAddEffectButton = false;
        if (contents.present() && contents.value().isJsonObject()) {
            JsonObject value = contents.value().getAsJsonObject();
            if (value.has("potion") && !potionProblem(pendingBufferText("base_potion", string(value, "potion", ""))).isBlank()) {
                requestPendingField("contents", "base_potion");
                return;
            }
            if (invalidCodecField(value, "custom_color", Codec.INT)) {
                requestPendingField("contents", "custom_color");
                return;
            }
            if (invalidCodecField(value, "custom_name", Codec.STRING)) {
                requestPendingField("contents", "custom_name");
                return;
            }
            JsonElement effectsValue = value.get("custom_effects");
            if (effectsValue != null && effectsValue.isJsonArray()) {
                JsonArray effects = effectsValue.getAsJsonArray();
                ensureEffectRowIds(effects.size());
                for (int index = 0; index < effects.size(); index++) {
                    if (!effects.get(index).isJsonObject()) continue;
                    long rowId = effectRowIds.get(index);
                    String field = invalidEffectField(effects.get(index).getAsJsonObject(), "effect_" + rowId, 0);
                    if (field != null) {
                        focusEffectRowId = rowId;
                        requestPendingField("contents", field);
                        return;
                    }
                }
            }
        }
        if (contents.present()) {
            Minecraft client = Minecraft.getInstance();
            try {
                ItemStack probe = host.draft().copy();
                contents.apply(client, probe);
                int droppedEffect = droppedHiddenEffectIndex(client, probe);
                if (droppedEffect >= 0) {
                    long rowId = effectRowIds.get(droppedEffect);
                    focusEffectRowId = rowId;
                    requestPendingField("contents", "effect_" + rowId + "/hidden_effect");
                    return;
                }
            } catch (RuntimeException ignored) {
                requestPendingField("contents", "$enabled");
                return;
            }
        }
        if (durationScale.present() && invalidCodecValue(ExtraCodecs.NON_NEGATIVE_FLOAT, durationScale.value())) {
            requestPendingField("duration_scale", "value");
            return;
        }
        requestPendingField(contents.present() ? "contents" : "duration_scale", "$enabled");
    }

    private String invalidEffectField(JsonObject value, String path, int depth) {
        if (depth == 0 && !effectProblem(pendingBufferText(path + "/id", string(value, "id", ""))).isBlank())
            return path + "/id";
        if (invalidCodecField(value, "amplifier", ExtraCodecs.UNSIGNED_BYTE)) return path + "/strength";
        if (invalidCodecField(value, "duration", Codec.INT)) return path + "/duration";
        for (String field : List.of("ambient", "show_particles", "show_icon")) {
            if (invalidCodecField(value, field, Codec.BOOL)) return path + "/" + field;
        }
        JsonElement hidden = value.get("hidden_effect");
        if (hidden != null) {
            if (!hidden.isJsonObject()) return path + "/hidden_effect";
            if (depth < MAX_HIDDEN_EFFECT_DEPTH)
                return invalidEffectField(hidden.getAsJsonObject(), path + "_hidden", depth + 1);
        }
        return null;
    }

    /**
     * Vanilla's copying effect accessor can discard fallbacks when the component is serialized.
     */
    private int droppedHiddenEffectIndex(Minecraft client, ItemStack edited) {
        if (!contents.present() || !contents.value().isJsonObject()) return -1;
        JsonElement sourceEffects = contents.value().getAsJsonObject().get("custom_effects");
        if (sourceEffects == null || !sourceEffects.isJsonArray()) return -1;
        JsonElement encoded = null;
        for (int index = 0; index < sourceEffects.getAsJsonArray().size(); index++) {
            JsonElement source = sourceEffects.getAsJsonArray().get(index);
            if (!source.isJsonObject() || !source.getAsJsonObject().has("hidden_effect")) continue;
            if (encoded == null) encoded = ItemStackCodecs.encodeComponent(client, DataComponents.POTION_CONTENTS,
                edited.get(DataComponents.POTION_CONTENTS));
            JsonElement savedEffects = encoded.isJsonObject() ? encoded.getAsJsonObject().get("custom_effects") : null;
            if (savedEffects == null || !savedEffects.isJsonArray() || index >= savedEffects.getAsJsonArray().size())
                return index;
            JsonElement saved = savedEffects.getAsJsonArray().get(index);
            if (!saved.isJsonObject() || !saved.getAsJsonObject().has("hidden_effect")) return index;
        }
        return -1;
    }

    public static boolean supports(DataComponentType<?> type) {
        return type == DataComponents.POTION_CONTENTS
            || type == DataComponents.POTION_DURATION_SCALE;
    }

    public boolean isAvailable(ItemStack stack) {
        return stack.has(DataComponents.POTION_CONTENTS)
            || stack.has(DataComponents.POTION_DURATION_SCALE);
    }

    public void setCatalog(EditorCatalog catalog) {
        potionIds = catalog.registryIds("potion");
        effectIds = catalog.mobEffectIds();
    }

    public void sync(Minecraft client, ItemStack stack) {
        contents.sync(client, stack);
        durationScale.sync(client, stack);
        resetEffectRowIds();
        resetEditorUi();
        dirty = false;
    }

    public void syncComponent(Minecraft client, DataComponentType<?> type, ItemStack stack) {
        if (type == DataComponents.POTION_CONTENTS) {
            contents.sync(client, stack);
            resetEffectRowIds();
        } else if (type == DataComponents.POTION_DURATION_SCALE) {
            durationScale.sync(client, stack);
        }
        resetEditorUi();
        dirty = hasPendingChanges();
    }

    public void draw(Minecraft client) {
        if (drawToolbar(client, null)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        EditorUi.beginScrollableChild("potion_editor_sections", 0.0f, Math.max(1.0f, ImGui.getContentRegionAvailY()), false);
        try {
            host.drawTooltipPreview(client, preview, "potion");
            ImGui.spacing();
            drawPotionContents(client);
            ImGui.spacing();
            drawDurationScale();
        } finally {
            ImGui.endChild();
        }
    }

    public void drawComponent(Minecraft client, DataComponentType<?> type) {
        if (drawToolbar(client, type)) return;
        ItemStack preview = previewStack(client);
        host.setItemIconPreview(preview);
        host.drawTooltipPreview(client, preview, "potion_component");
        ImGui.spacing();
        if (type == DataComponents.POTION_CONTENTS) drawPotionContents(client);
        else if (type == DataComponents.POTION_DURATION_SCALE) drawDurationScale();
    }

    private void drawDurationScale() {
        if (!beginSection("duration_scale", durationScale)) return;
        if (beginFields("potion_duration_scale_fields")) {
            fieldLabel(tr("potion.duration_scale"), tr("potion.duration_scale.help"));
            ImDouble scale = new ImDouble(primitiveDouble(durationScale, 1.0));
            ImGui.setNextItemWidth(-1.0f);
            applyPendingFieldFocus("duration_scale", "value");
            if (ImGui.inputDouble("##potion_duration_scale", scale, 0.1, 0.5, "%.2fx")) {
                durationScale.setValue(new JsonPrimitive(Math.max(0.0, scale.get())));
                dirty = true;
            }
            ImGui.endTable();
        }
        endSection();
    }

    private void drawPotionContents(Minecraft client) {
        if (!beginSection("contents", contents)) return;
        JsonObject value = contents.object();
        if (beginFields("potion_contents_fields")) {
            if (contents.supportsField("potion")) {
                fieldLabel(tr("potion.base"), tr("potion.base.help"));
                drawBasePotion(value);
            }
            if (contents.supportsField("custom_color")) {
                fieldLabel(tr("potion.color"), tr("potion.color.help"));
                drawCustomColor(client, value);
            }
            if (contents.supportsField("custom_name")) {
                fieldLabel(tr("potion.name"), tr("potion.name.help"));
                drawCustomName(value);
            }
            ImGui.endTable();
        }

        if (contents.supportsField("custom_effects")) {
            ImGui.spacing();
            drawCustomEffects(value);
        }
        endSection();
        OrderedListDragDrop.autoScrollCurrentWindow(effectDragScope);
    }

    private void drawBasePotion(JsonObject value) {
        boolean present = value.has("potion") && value.get("potion").isJsonPrimitive();
        ImBoolean enabled = new ImBoolean(present);
        if (ImGui.checkbox(tr("potion.base.enabled"), enabled)) {
            if (enabled.get()) value.addProperty("potion", "minecraft:water");
            else value.remove("potion");
            dirty = true;
        }
        ImGui.beginDisabled(!enabled.get());
        String current = string(value, "potion", "minecraft:water");
        ImString potion = buffer("base_potion", current);
        applyPendingFieldFocus("contents", "base_potion");
        if (host.inputIdentifierWithSuggestions(
            "base_potion",
            new FieldHints(tr("field_purpose.potion_identifier"), "minecraft:water"),
            potion,
            potionIds,
            potionProblem(potion.get())
        )) {
            value.addProperty("potion", potion.get().trim());
            dirty = true;
        }
        ImGui.endDisabled();
    }

    private void drawCustomColor(Minecraft client, JsonObject value) {
        ImBoolean customColor = new ImBoolean(value.has("custom_color"));
        if (ImGui.checkbox(tr("potion.color.override"), customColor)) {
            if (customColor.get()) value.addProperty("custom_color", naturalPotionColor(client));
            else value.remove("custom_color");
            dirty = true;
        }
        ImGui.beginDisabled(!customColor.get());
        int color = integer(value, "custom_color", naturalPotionColor(client)) & 0xFFFFFF;
        boolean requestFocus = consumePendingFieldFocus("contents", "custom_color");
        if (requestFocus) ImGui.setScrollHereY(0.35f);
        int changed = ColorSelector.drawRgb("potion_color", color, false, requestFocus);
        if (changed != color) {
            value.addProperty("custom_color", changed);
            dirty = true;
        }
        ImGui.endDisabled();
    }

    private void drawCustomName(JsonObject value) {
        ImBoolean customName = new ImBoolean(value.has("custom_name"));
        if (ImGui.checkbox(tr("potion.name.override"), customName)) {
            if (customName.get()) value.addProperty("custom_name", "custom");
            else value.remove("custom_name");
            dirty = true;
        }
        ImGui.beginDisabled(!customName.get());
        ImString name = buffer("custom_name", string(value, "custom_name", "custom"));
        ImGui.setNextItemWidth(-1.0f);
        applyPendingFieldFocus("contents", "custom_name");
        if (inputTextWithHint(
            "##potion_custom_name",
            new FieldHints(tr("field_purpose.translation_key_suffix"), "custom"),
            name
        )) {
            value.addProperty("custom_name", name.get().trim());
            dirty = true;
        }
        ImGui.endDisabled();
    }

    private void drawCustomEffects(JsonObject value) {
        ImGui.separatorText(tr("potion.effects"));
        JsonArray effects = array(value, "custom_effects");
        ensureEffectRowIds(effects.size());
        drawAddEffectAction(effects, "top", false);

        int actionIndex = -1;
        EditorUi.ListAction action = EditorUi.ListAction.NONE;
        OrderedListDragDrop.ReorderRequest reorderRequest = null;
        for (int index = 0; index < effects.size(); index++) {
            JsonElement element = effects.get(index);
            if (!element.isJsonObject()) continue;
            long rowId = effectRowIds.get(index);
            ImGui.pushID("potion_effect_" + rowId);
            ImGui.spacing();
            EditorUi.ListAction rowAction = drawEffect(
                index,
                effects.size(),
                element.getAsJsonObject(),
                rowId,
                focusEffectRowId == rowId
            );
            if (rowAction != EditorUi.ListAction.NONE) {
                actionIndex = index;
                action = rowAction;
            }
            OrderedListDragDrop.ReorderRequest rowRequest = OrderedListDragDrop.drawRowTarget(
                effectDragScope,
                rowId,
                index
            );
            if (reorderRequest == null) reorderRequest = rowRequest;
            ImGui.popID();
        }
        JsonListMutations.Mutation mutation = reorderRequest == null
            ? JsonListMutations.apply(
            effects,
            effectRowIds,
            actionIndex,
            action,
            NEXT_EFFECT_ROW_ID::incrementAndGet,
            PotionEditorPanel::defaultEffect
        )
            : JsonListMutations.reorder(effects, effectRowIds, reorderRequest);
        finishEffectMutation(mutation);
        if (effects.isEmpty()) EditorUi.drawEmptyState(tr("potion.effects.empty"));
        drawAddEffectAction(effects, "bottom", true);
    }

    private void drawAddEffectAction(JsonArray effects, String placement, boolean acceptFocus) {
        if (acceptFocus && focusAddEffectButton) {
            ImGui.setKeyboardFocusHere();
            focusAddEffectButton = false;
        }
        if (EditorIcons.button(
            "add_potion_effect_" + placement,
            EditorIcons.Icon.ADD,
            tr("potion.effects.add")
        )) {
            effects.add(defaultEffect());
            long rowId = NEXT_EFFECT_ROW_ID.incrementAndGet();
            effectRowIds.add(rowId);
            focusEffectRowId = rowId;
            focusAddEffectButton = false;
            resetEditorUi();
            dirty = true;
        }
    }

    private EditorUi.ListAction drawEffect(
        int index,
        int size,
        JsonObject effect,
        long rowId,
        boolean requestFocus
    ) {
        EditorUi.ListCardResult card = EditorUi.beginCollapsibleListCard(
            "potion_effect_row",
            effectTitle(index, effect),
            ImGuiTableFlags.SizingStretchProp,
            index,
            size,
            true,
            true,
            true,
            requestFocus,
            effectDragScope,
            rowId
        );
        if (card.focusApplied()) focusEffectRowId = 0L;
        if (card.expanded()) {
            drawEffectDetails(effect, "effect_" + rowId, 0, true);
            EditorUi.endCard();
        }
        return card.action();
    }

    private void resetEffectRowIds() {
        effectRowIds.clear();
        focusEffectRowId = 0L;
        focusAddEffectButton = false;
        effectDragScope = OrderedListDragDrop.newScope("potion_effects");
        ensureEffectRowIds(array(contents.object(), "custom_effects").size());
    }

    private void finishEffectMutation(JsonListMutations.Mutation mutation) {
        if (!mutation.changed()) return;
        if (mutation.focusRowId() == 0L) {
            focusEffectRowId = 0L;
            focusAddEffectButton = true;
        } else {
            focusEffectRowId = mutation.focusRowId();
            focusAddEffectButton = false;
        }
        resetEditorUi();
        dirty = true;
    }

    private void ensureEffectRowIds(int size) {
        while (effectRowIds.size() < size) {
            effectRowIds.add(NEXT_EFFECT_ROW_ID.incrementAndGet());
        }
        while (effectRowIds.size() > size) {
            effectRowIds.removeLast();
        }
    }

    private void drawEffectDetails(JsonObject details, String path, int depth, boolean showId) {
        ImGui.pushID(path);
        try {
            if (beginFields("potion_effect_fields")) {
                if (showId) {
                    fieldLabel(tr("potion.effect.id"), tr("potion.effect.id.help"));
                    ImString id = buffer(path + "/id", string(details, "id", "minecraft:speed"));
                    applyPendingFieldFocus("contents", path + "/id");
                    if (host.inputIdentifierWithSuggestions(
                        "effect_id",
                        new FieldHints(tr("field_purpose.effect_identifier"), "minecraft:speed"),
                        id,
                        effectIds,
                        effectProblem(id.get())
                    )) {
                        details.addProperty("id", id.get().trim());
                        dirty = true;
                    }
                }

                fieldLabel(tr("potion.effect.duration"), tr("potion.effect.duration.help"));
                ImInt duration = new ImInt(integer(details, "duration", 200));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("contents", path + "/duration");
                if (ImGui.inputInt("##duration", duration, 20, 200)) {
                    details.addProperty("duration", Math.max(-1, duration.get()));
                    dirty = true;
                }
                EditorUi.textDisabledWrapped(durationSummary(Math.max(-1, duration.get())));

                fieldLabel(tr("potion.effect.strength"), tr("potion.effect.strength.help"));
                ImInt strength = new ImInt(Math.clamp(integer(details, "amplifier", 0) + 1, 1, 256));
                ImGui.setNextItemWidth(-1.0f);
                applyPendingFieldFocus("contents", path + "/strength");
                if (ImGui.inputInt("##strength", strength, 1, 5)) {
                    details.addProperty("amplifier", Math.clamp(strength.get(), 1, 256) - 1);
                    dirty = true;
                }

                fieldLabel(tr("potion.effect.ambient"), tr("potion.effect.ambient.help"));
                applyPendingFieldFocus("contents", path + "/ambient");
                dirty |= booleanField(details, "ambient", false);

                fieldLabel(tr("potion.effect.particles"), tr("potion.effect.particles.help"));
                applyPendingFieldFocus("contents", path + "/show_particles");
                dirty |= booleanField(details, "show_particles", true);

                fieldLabel(tr("potion.effect.icon"), tr("potion.effect.icon.help"));
                applyPendingFieldFocus("contents", path + "/show_icon");
                dirty |= booleanField(details, "show_icon", bool(details, "show_particles", true));

                fieldLabel(tr("potion.effect.fallback"), tr("potion.effect.fallback.help"));
                JsonElement hiddenValue = details.get("hidden_effect");
                boolean hiddenPresent = hiddenValue != null && hiddenValue.isJsonObject();
                ImBoolean hidden = new ImBoolean(hiddenPresent);
                applyPendingFieldFocus("contents", path + "/hidden_effect");
                if (ImGui.checkbox("##hidden_effect", hidden)) {
                    if (hidden.get()) details.add("hidden_effect", defaultEffectDetails());
                    else details.remove("hidden_effect");
                    dirty = true;
                }
                ImGui.endTable();
            }

            JsonElement currentHidden = details.get("hidden_effect");
            boolean hiddenPresent = currentHidden != null && currentHidden.isJsonObject();
            if (hiddenPresent) {
                if (depth >= MAX_HIDDEN_EFFECT_DEPTH) {
                    EditorUi.textDisabledWrapped(tr("potion.effect.fallback.depth"));
                } else {
                    ImGui.spacing();
                    ImGui.separatorText(tr("potion.effect.fallback.details"));
                    drawEffectDetails(
                        details.getAsJsonObject("hidden_effect"),
                        path + "_hidden",
                        depth + 1,
                        false
                    );
                }
            }
        } finally {
            ImGui.popID();
        }
    }

    private boolean booleanField(JsonObject object, String key, boolean fallback) {
        ImBoolean value = new ImBoolean(bool(object, key, fallback));
        if (!ImGui.checkbox("##" + key, value)) return false;
        object.addProperty(key, value.get());
        return true;
    }

    private boolean drawToolbar(Minecraft client, DataComponentType<?> componentType) {
        return drawPanelToolbar(
            () -> save(client, true),
            () -> {
                if (componentType == null) sync(client, host.draft());
                else syncComponent(client, componentType, host.draft());
            }
        );
    }

    private int naturalPotionColor(Minecraft client) {
        try {
            ItemStack stack = buildEditedStack(client);
            PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
            return potion == null ? PotionContents.BASE_POTION_COLOR : potion.getColor();
        } catch (RuntimeException ignored) {
            return PotionContents.BASE_POTION_COLOR;
        }
    }

    private String effectTitle(int index, JsonObject effect) {
        String name = effectName(effect);
        int strength = Math.clamp(integer(effect, "amplifier", 0) + 1, 1, 256);
        return tr("potion.effect.entry", index + 1, name, strength);
    }


    private static String potionProblem(String value) {
        Identifier id = Identifier.tryParse(value == null ? "" : value.trim());
        if (id == null) return tr("validation.identifier_format");
        return BuiltInRegistries.POTION.containsKey(id) ? "" : tr("validation.identifier_unknown");
    }

    private static String effectProblem(String value) {
        Identifier id = Identifier.tryParse(value == null ? "" : value.trim());
        if (id == null) return tr("validation.identifier_format");
        return BuiltInRegistries.MOB_EFFECT.containsKey(id) ? "" : tr("validation.identifier_unknown");
    }

    public boolean save(Minecraft client, boolean announce) {
        return savePendingStack(client, announce, edited -> sync(client, edited));
    }

    private ItemStack previewStack(Minecraft client) {
        try {
            return buildEditedStack(client);
        } catch (RuntimeException ignored) {
            return host.draft();
        }
    }

    private ItemStack buildEditedStack(Minecraft client) {
        ItemStack edited = host.draft().copy();
        contents.apply(client, edited);
        if (droppedHiddenEffectIndex(client, edited) >= 0) {
            throw new IllegalArgumentException(tr("potion.effect.fallback.unsupported"));
        }
        durationScale.apply(client, edited);
        ItemStackCodecs.validateStack(edited);
        return edited;
    }

    @Override
    public ItemStack pendingStack(Minecraft client) {
        return buildEditedStack(client);
    }

    @Override
    public void restorePending(Minecraft client, ItemStack state) {
        sync(client, state);
        dirty = true;
    }

    @Override
    protected boolean hasPendingChanges() {
        Minecraft client = Minecraft.getInstance();
        ItemStack draft = host.draft();
        return contents.differsFrom(client, draft)
            || durationScale.differsFrom(client, draft);
    }

    private static JsonObject defaultEffect() {
        JsonObject effect = defaultEffectDetails();
        effect.addProperty("id", "minecraft:speed");
        return effect;
    }

    private static JsonObject defaultEffectDetails() {
        JsonObject effect = new JsonObject();
        effect.addProperty("duration", 200);
        effect.addProperty("amplifier", 0);
        effect.addProperty("ambient", false);
        effect.addProperty("show_particles", true);
        effect.addProperty("show_icon", true);
        return effect;
    }

}
