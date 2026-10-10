package dev.nineofgaming.itemlab.editor.panel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.component.CodecComponentState;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Shared layout and transient state for grouped, codec-backed component editors.
 */
abstract class ComponentEditorPanel implements PendingStackEditor {
    protected final ItemEditor host;
    protected final Map<String, ImString> textBuffers = new HashMap<>();
    protected boolean dirty;

    private final String scope;
    private boolean focusPendingToolbar;
    private boolean revealPendingSection;
    private String pendingFocusSection;
    private String pendingFocusField;

    /**
     * Default recovery target for panels that do not yet expose a field-specific request.
     */
    @Override
    public void requestPendingFocus() {
        pendingFocusSection = null;
        pendingFocusField = null;
        revealPendingSection = false;
        focusPendingToolbar = true;
    }

    protected final void requestPendingSection() {
        revealPendingSection = true;
    }

    /**
     * Reveals only the requested section; earlier sections must not consume recovery focus.
     */
    protected final void requestPendingField(String section, String field) {
        focusPendingToolbar = false;
        revealPendingSection = false;
        pendingFocusSection = section;
        pendingFocusField = field;
    }

    protected final boolean hasPendingFieldFocus(String section, String field) {
        return section.equals(pendingFocusSection) && field.equals(pendingFocusField);
    }

    protected final boolean hasPendingSectionFocus(String section) {
        return section.equals(pendingFocusSection);
    }

    /**
     * Reads an existing input buffer without creating one or normalizing the model.
     */
    protected final String pendingBufferText(String key, String fallback) {
        ImString pending = textBuffers.get(key);
        return pending == null ? fallback : pending.get();
    }

    /**
     * Uses the native value codec only to choose a recovery control, without rewriting input.
     */
    protected static boolean invalidCodecValue(Codec<?> codec, JsonElement value) {
        try {
            return codec.parse(JsonOps.INSTANCE, value).error().isPresent();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    protected static boolean invalidCodecField(JsonObject value, String field, Codec<?> codec) {
        return value.has(field) && invalidCodecValue(codec, value.get(field));
    }

    protected final boolean consumePendingFieldFocus(String section, String field) {
        if (!hasPendingFieldFocus(section, field)) return false;
        pendingFocusSection = null;
        pendingFocusField = null;
        return true;
    }

    /**
     * Cancels a view request without clearing any pending input.
     */
    protected final void clearPendingFocus() {
        focusPendingToolbar = false;
        revealPendingSection = false;
        pendingFocusSection = null;
        pendingFocusField = null;
    }

    protected final void applyPendingFieldFocus(String section, String field) {
        if (!consumePendingFieldFocus(section, field)) return;
        ImGui.setScrollHereY(0.35f);
        ImGui.setKeyboardFocusHere();
    }

    protected ComponentEditorPanel(ItemEditor host, String scope) {
        this.host = host;
        this.scope = scope;
    }

    @Override
    public final boolean isDirty() {
        if (dirty) {
            try {
                dirty = hasPendingChanges();
            } catch (RuntimeException ignored) {
                // Invalid in-progress input is still an unsaved change.
                dirty = true;
            }
        }
        return dirty;
    }

    /**
     * Compares the current editor model with the loaded draft when a change may exist.
     */
    protected boolean hasPendingChanges() {
        return dirty;
    }

    /**
     * Commits a validated preview and resets the panel only after the host accepts it.
     */
    protected final boolean savePendingStack(
        Minecraft client,
        boolean announce,
        Consumer<ItemStack> synchronize
    ) {
        try {
            ItemStack edited = pendingStack(client);
            ItemStackCodecs.validateStack(edited);
            host.acceptSectionDraft(client, edited);
            synchronize.accept(edited);
            if (announce) host.setStatus(true, tr("message.saved"));
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("error.save", EditorUi.readableMessage(exception)));
            return false;
        }
    }

    /**
     * Formats the current buffer defensively, even if a caller's validation became stale.
     */
    protected final boolean formatPendingSnbt(ImString source, String problemKey) {
        try {
            String formatted = ItemStackCodecs.formatSnbt(source.get());
            source.set(formatted);
            dirty = true;
            return true;
        } catch (RuntimeException exception) {
            host.setStatus(false, tr(problemKey, EditorUi.readableMessage(exception)));
            return false;
        }
    }

    protected final boolean beginSection(
        String id,
        CodecComponentState state
    ) {
        String translationBase = "panel." + scope + ".section." + id;
        String title = tr(translationBase + ".title");
        boolean reveal = revealPendingSection || id.equals(pendingFocusSection);
        if (!EditorUi.beginCard(scope + "_" + id, 1, ImGuiTableFlags.SizingStretchProp, reveal)) {
            return false;
        }
        ImGui.tableNextColumn();
        ImBoolean enabled = new ImBoolean(state.present());
        applyPendingFieldFocus(id, "$enabled");
        if (ImGui.checkbox("##enabled_" + id, enabled)) {
            state.setPresent(enabled.get());
            dirty = true;
        }
        ImGui.sameLine();
        boolean expanded = EditorUi.collapsibleHeader(
            scope + "_section_" + id,
            title,
            host.componentSectionStartsExpanded(state.type()),
            reveal
        );
        revealPendingSection = false;
        host.drawDiamondFireComponentHint(state.type());
        if (!expanded) {
            EditorUi.endCard();
            return false;
        }
        ImGui.beginDisabled(!state.present());
        ImGui.spacing();
        return true;
    }

    protected static void endSection() {
        ImGui.endDisabled();
        EditorUi.endCard();
    }

    protected static boolean beginFields(String id) {
        float labelWidth = Math.clamp(ImGui.getContentRegionAvailX() * 0.34f, 120.0f, 175.0f);
        if (!ImGui.beginTable(
            id,
            2,
            ImGuiTableFlags.SizingStretchProp
                | ImGuiTableFlags.BordersInnerV
                | ImGuiTableFlags.NoPadOuterX
        )) {
            return false;
        }
        ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthFixed, labelWidth);
        ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
        return true;
    }

    protected static void fieldLabel(String label, String description) {
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textWrapped(label);
        if (ImGui.isItemHovered() && !description.isBlank()) {
            EditorUi.setTooltip(description);
        }
        ImGui.tableNextColumn();
    }

    protected final ImString buffer(String key, String value) {
        return textBuffers.computeIfAbsent(
            key,
            ignored -> new ImString(value, Math.max(512, value.length() * 2 + 64))
        );
    }

    protected final void resetEditorUi() {
        clearPendingFocus();
        textBuffers.clear();
        host.resetAutocomplete();
    }

    protected final boolean drawPanelToolbar(
        BooleanSupplier saveAction,
        Runnable discardAction
    ) {
        ImGui.spacing();
        if (focusPendingToolbar) {
            ImGui.setScrollY(0.0f);
            ImGui.setKeyboardFocusHere();
            focusPendingToolbar = false;
        }
        return EditorUi.drawSaveDiscardActions(
            isDirty(),
            saveAction::getAsBoolean,
            () -> {
                discardAction.run();
                host.setStatus(true, tr("message.discarded"));
            }
        );
    }

}
