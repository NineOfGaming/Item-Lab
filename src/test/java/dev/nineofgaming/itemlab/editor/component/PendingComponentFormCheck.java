package dev.nineofgaming.itemlab.editor.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nineofgaming.itemlab.editor.PendingEditTransaction;
import dev.nineofgaming.itemlab.editor.ui.control.CodeEditorInput;
import imgui.type.ImString;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.invoke;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.read;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.require;
import static dev.nineofgaming.itemlab.editor.EditorCheckSupport.write;

/**
 * Exercises production pending buffers without an ImGui window or running client.
 */
public final class PendingComponentFormCheck {
    private PendingComponentFormCheck() {
    }

    public static void main(String[] arguments) throws ReflectiveOperationException {
        verifyRootTextBlocksPreflight();
        verifyNestedTextAndFocusAreRetained();
        verifyValidPreviewIsIsolated();
        verifyCodeFocusDoesNotResetTheCursor();
    }

    private static void verifyRootTextBlocksPreflight() throws ReflectiveOperationException {
        ComponentFormPanel panel = new ComponentFormPanel(null);
        panel.load(JsonParser.parseString("5"));
        ((ImString) read(panel, "scalar")).set("not a number");
        write(panel, "problem", "Enter a number.");
        write(panel, "dirty", true);
        BufferState before = buffers(panel);
        AtomicInteger laterPreviews = new AtomicInteger();
        JsonElement source = panel.pendingValue();
        var result = PendingEditTransaction.prepare(source, JsonElement::deepCopy, ignored -> {
        }, List.of(
            new PendingEditTransaction.Participant<JsonElement>(
                "component_form", "Generated component fields", "COMPONENTS", "selected_component_editor",
                panel::isDirty, Set.of("minecraft:damage"), ignored -> panel.validatedPendingValue()),
            new PendingEditTransaction.Participant<JsonElement>(
                "later", "Later", "NAME", null, () -> true, Set.of("minecraft:custom_name"), input -> {
                laterPreviews.incrementAndGet();
                return input;
            })
        ));
        require(!result.succeeded(), "invalid root text saved the stale numeric value");
        require(result.failure().kind() == PendingEditTransaction.FailureKind.INVALID
            && result.failure().participantId().equals("component_form")
            && result.failure().route().equals("COMPONENTS"), "the generated owner was not reported");
        require(laterPreviews.get() == 0 && source.getAsInt() == 5, "failed preflight changed its source or continued");
        panel.requestPendingFocus();
        require(before.equals(buffers(panel)) && panel.isDirty(), "root recovery lost pending text or its baseline");
        require("$".equals(read(panel, "focusFieldPath")), "root recovery did not target the value input");
    }

    @SuppressWarnings("unchecked")
    private static void verifyNestedTextAndFocusAreRetained() throws ReflectiveOperationException {
        ComponentFormPanel panel = new ComponentFormPanel(null);
        panel.load(JsonParser.parseString("{\"amount\":1,\"other\":2}"));
        Map<String, String> problems = (Map<String, String>) read(panel, "fieldProblems");
        Map<String, ImString> inputs = (Map<String, ImString>) read(panel, "fieldBuffers");
        problems.put("$/rows/42/amount", "Bad amount.");
        problems.put("$/rows/99/other", "Bad other value.");
        inputs.put("$/rows/42/amount", new ImString("broken", 128));
        inputs.put("$/rows/99/other", new ImString("also broken", 128));
        write(panel, "dirty", true);
        BufferState before = buffers(panel);
        try {
            panel.validatedPendingValue();
            throw new AssertionError("invalid nested text was silently replaced by the last valid value");
        } catch (IllegalArgumentException expected) {
            require("Bad amount.".equals(expected.getMessage()), "error and focus chose different fields");
        }
        panel.requestPendingFocus();
        require("$/rows/42/amount".equals(read(panel, "focusFieldPath")), "the first invalid nested input was not targeted");
        require(before.equals(buffers(panel)) && panel.isDirty(), "nested recovery changed pending buffers");
    }

    private static void verifyValidPreviewIsIsolated() throws ReflectiveOperationException {
        ComponentFormPanel panel = new ComponentFormPanel(null);
        panel.restorePending(JsonParser.parseString("{\"amount\":1}"), JsonParser.parseString("{\"amount\":2}"));
        BufferState before = buffers(panel);
        JsonElement preview = panel.validatedPendingValue();
        preview.getAsJsonObject().addProperty("amount", 99);
        require(before.equals(buffers(panel)), "mutating a valid preview changed the editor");
        require(panel.isDirty(), "validation advanced the panel baseline before commit");
    }

    private static void verifyCodeFocusDoesNotResetTheCursor() throws ReflectiveOperationException {
        CodeEditorInput editor = new CodeEditorInput();
        write(editor, "cursorByteOffset", 17);
        write(editor, "cursorLine", 3);
        editor.requestFocus();
        require(Boolean.TRUE.equals(read(editor, "focusRequested")), "source focus was not deferred");
        require(Integer.valueOf(17).equals(read(editor, "cursorByteOffset"))
            && Integer.valueOf(3).equals(read(editor, "cursorLine")), "source focus reset the cursor");
        editor.cancelFocusRequest();
        require(Boolean.FALSE.equals(read(editor, "focusRequested")), "source focus survived cancellation");
    }

    @SuppressWarnings("unchecked")
    private static BufferState buffers(ComponentFormPanel panel) throws ReflectiveOperationException {
        Map<String, ImString> inputs = (Map<String, ImString>) read(panel, "fieldBuffers");
        return new BufferState(panel.pendingValue().toString(), read(panel, "loadedValue").toString(),
            ((ImString) read(panel, "scalar")).get(),
            inputs.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get())),
            Map.copyOf((Map<String, String>) read(panel, "fieldProblems")),
            (String) read(panel, "problem"), (boolean) read(panel, "dirty"));
    }




    private record BufferState(String value, String baseline, String scalar, Map<String, String> inputs,
                               Map<String, String> problems, String rootProblem, boolean dirty) {
    }
}
