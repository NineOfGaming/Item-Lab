package dev.nineofgaming.itemlab.editor.panel;

import dev.nineofgaming.itemlab.editor.EditorNavigationGuard;
import dev.nineofgaming.itemlab.editor.ItemEditor;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import dev.nineofgaming.itemlab.editor.ui.syntax.CodeLanguage;
import dev.nineofgaming.itemlab.editor.ui.EditorIcons;
import dev.nineofgaming.itemlab.editor.ui.EditorUi;
import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.ui.ItemEditorTheme;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

import static dev.nineofgaming.itemlab.editor.ui.EditorText.tr;

/**
 * Advanced import/export editor for the complete codec-backed item stack.
 */
public final class RawStackEditorPanel {
    private final ItemEditor host;
    private final ImString source = new ImString(262_144);
    private final ImBoolean includeDefaultComponents = new ImBoolean(false);
    private final CodeEditorInput codeEditor = new CodeEditorInput();
    private boolean dirty;
    private boolean focusPendingPanel;
    private String codecProblem = "";
    private String validatedSource;
    private StackValidation validation = StackValidation.invalid("", 0);
    private Item expandedDefaultsItem;
    private Set<DataComponentType<?>> expandedDefaultComponents = Set.of();

    public RawStackEditorPanel(ItemEditor host) {
        this.host = host;
    }

    public void resetCodeEditorView() {
        codeEditor.resetCursor();
    }

    public void requestPendingFocus() {
        focusPendingPanel = true;
        codeEditor.requestFocus();
    }

    public boolean isDirty() {
        if (dirty) {
            StackValidation current = validate(Minecraft.getInstance());
            dirty = !current.valid() || !ItemStack.matches(host.draft(), current.stack());
        }
        return dirty;
    }

    public void sync(Minecraft client, ItemStack draft) {
        focusPendingPanel = false;
        codeEditor.cancelFocusRequest();
        codecProblem = "";
        clearValidation();
        dirty = false;
        try {
            boolean includeDefaults = includeDefaultComponents.get();
            source.set(ItemStackCodecs.encodeStackSnbt(client, draft, includeDefaults));
            expandedDefaultsItem = includeDefaults ? draft.getItem() : null;
            expandedDefaultComponents = includeDefaults
                ? ItemStackCodecs.inheritedPersistentComponents(draft)
                : Set.of();
        } catch (RuntimeException exception) {
            source.clear();
            expandedDefaultsItem = null;
            expandedDefaultComponents = Set.of();
            codecProblem = tr("raw.encode_error", EditorUi.readableMessage(exception));
        }
    }

    public void draw(Minecraft client) {
        ImGui.spacing();
        EditorUi.beginScrollableChild(
            "stack_snbt_panel",
            0.0f,
            Math.max(1.0f, ImGui.getContentRegionAvailY() - 2.0f),
            ImGuiChildFlags.Borders
        );
        try {
            if (focusPendingPanel) {
                ImGui.setScrollY(0.0f);
                focusPendingPanel = false;
            }
            ImGui.separatorText(tr("raw.title"));

            boolean pending = isDirty();
            StackValidation current = validate(client);
            EditorNavigationGuard.Decision representationDecision = includeDefaultsDecision(current, pending);
            ImGui.beginDisabled(!representationDecision.allowed());
            if (ImGui.checkbox(tr("raw.include_defaults"), includeDefaultComponents)) {
                sync(client, host.draft());
                current = validate(client);
            }
            ImGui.endDisabled();
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                String help = tr("raw.include_defaults.help");
                EditorUi.setTooltip(!representationDecision.allowed()
                    ? help + "\n\n" + representationDecision.blocker().explanation()
                    : help);
            }

            String problem = current.problem();
            boolean otherEditorPending = host.hasPendingNonRawEditorChanges();
            float editorHeight = Math.max(
                120.0f,
                ImGui.getContentRegionAvailY()
                    - 104.0f
                    - (problem.isBlank() ? 0.0f : 44.0f)
                    - (otherEditorPending ? 44.0f : 0.0f)
            );
            boolean invalidBeforeEdit = !current.valid();
            if (invalidBeforeEdit) ItemEditorTheme.pushInvalidField();
            if (codeEditor.draw(
                "raw_item_snbt",
                source,
                -1.0f,
                editorHeight,
                ImGuiInputTextFlags.AllowTabInput,
                current.line(),
                CodeLanguage.SNBT
            )) {
                dirty = true;
                codecProblem = "";
                current = validate(client);
                problem = current.problem();
            }
            if (invalidBeforeEdit) ItemEditorTheme.popInvalidField();
            ItemEditorTheme.drawFieldProblem(problem.isBlank() ? "" : tr("raw.invalid_stack", problem));
            if (otherEditorPending) {
                ItemEditorTheme.drawFieldWarning(tr("raw.other_editor_pending"));
            }

            if (EditorIcons.button("reload_raw", EditorIcons.Icon.RELOAD, tr("raw.reload"))) {
                sync(client, host.draft());
                host.setStatus(true, tr("raw.status.reloaded"));
            }
            EditorUi.sameLineIfFits(tr("raw.format"));
            if (ImGui.button(tr("raw.format"))) {
                format();
            }
            EditorUi.sameLineIfFits(EditorIcons.buttonSize());
            if (EditorIcons.button("copy_raw", EditorIcons.Icon.COPY, tr("raw.copy"))) {
                client.keyboardHandler.setClipboard(source.get());
                host.setStatus(true, tr("raw.status.copied"));
            }
            EditorUi.sameLineIfFits(tr("raw.replace"));
            ImGui.beginDisabled(!current.valid() || otherEditorPending);
            if (ImGui.button(tr("raw.replace"))) {
                replaceDraft(client, current);
            }
            ImGui.endDisabled();
            if (otherEditorPending && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                EditorUi.setTooltip(tr("raw.other_editor_pending"));
            }

            if (isDirty()) {
                ImGui.textWrapped(tr("raw.pending"));
            }
        } finally {
            ImGui.endChild();
        }
    }

    private EditorNavigationGuard.Decision includeDefaultsDecision(
        StackValidation current,
        boolean pending
    ) {
        EditorNavigationGuard.Blocker blocker = null;
        if (pending) {
            boolean invalid = !current.valid();
            blocker = new EditorNavigationGuard.Blocker(
                tr("tab.raw"),
                "raw",
                invalid
                    ? EditorNavigationGuard.PendingState.INVALID
                    : EditorNavigationGuard.PendingState.VALID,
                invalid
                    ? tr("navigation.raw_invalid", tr("tab.raw"), current.problem())
                    : tr("navigation.raw_pending", tr("tab.raw")),
                new EditorNavigationGuard.FocusRequest("raw_source")
            );
        }
        return EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            blocker
        );
    }

    private StackValidation validate(Minecraft client) {
        String rawSource = source.get();
        String validationKey = codecProblem + '\0' + rawSource;
        if (validationKey.equals(validatedSource)) {
            return validation;
        }
        validatedSource = validationKey;
        if (!codecProblem.isBlank()) {
            validation = StackValidation.invalid(codecProblem, 0);
            return validation;
        }
        try {
            ItemStackCodecs.DecodedStackSnbt decoded = ItemStackCodecs.decodeStackSnbtDocument(client, rawSource);
            ItemStack parsed = decoded.stack();
            requireNonEmpty(parsed);
            if (parsed.getItem() == expandedDefaultsItem) {
                ItemStackCodecs.applyExpandedDefaultDeletions(
                    parsed,
                    expandedDefaultComponents,
                    decoded.componentKeys()
                );
            }
            ItemStackCodecs.validateStack(parsed);
            validation = StackValidation.valid(parsed);
        } catch (RuntimeException exception) {
            validation = StackValidation.invalid(
                EditorUi.readableMessage(exception),
                CodeEditorInput.errorLine(exception, rawSource)
            );
        }
        return validation;
    }

    private void replaceDraft(Minecraft client, StackValidation current) {
        if (host.hasPendingNonRawEditorChanges()) {
            host.setStatus(false, tr("raw.other_editor_pending"));
            return;
        }
        try {
            if (!current.valid()) {
                host.setStatus(false, tr("raw.invalid_stack", current.problem()));
                return;
            }
            host.acceptRawDraft(client, current.stack().copy());
            host.setStatus(true, tr("raw.status.replaced"));
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("raw.invalid_stack", EditorUi.readableMessage(exception)));
        }
    }

    private void format() {
        try {
            source.set(ItemStackCodecs.formatSnbt(source.get()));
            dirty = true;
            host.setStatus(true, tr("raw.status.formatted"));
        } catch (RuntimeException exception) {
            host.setStatus(false, tr("raw.invalid_snbt", EditorUi.readableMessage(exception)));
        }
    }

    public String pendingSource() {
        return source.get();
    }

    /**
     * Decodes the pending whole-stack source without changing the live draft.
     */
    public ItemStack pendingStack(Minecraft client) {
        StackValidation current = validate(client);
        if (!current.valid()) throw new IllegalArgumentException(current.problem());
        return current.stack().copy();
    }

    public void restorePendingSource(String state) {
        source.set(state);
        codecProblem = "";
        clearValidation();
        dirty = true;
    }

    private void clearValidation() {
        validatedSource = null;
        validation = StackValidation.invalid("", 0);
    }

    private static void requireNonEmpty(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(tr("raw.empty_stack"));
        }
    }

    private record StackValidation(ItemStack stack, String problem, int line) {
        private static StackValidation valid(ItemStack stack) {
            return new StackValidation(stack, "", 0);
        }

        private static StackValidation invalid(String problem, int line) {
            return new StackValidation(null, problem, line);
        }

        private boolean valid() {
            return stack != null;
        }
    }
}
