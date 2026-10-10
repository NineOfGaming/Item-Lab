package dev.nineofgaming.itemlab.editor.state;

import dev.nineofgaming.itemlab.editor.component.ItemStackCodecs;
import dev.nineofgaming.itemlab.editor.panel.PendingStackEditor;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Uses the same panel roster to capture and restore pending item-stack snapshots.
 */
public final class PendingPanelHistory {
    private final List<Panel> panels;

    public PendingPanelHistory(List<Panel> panels) {
        this.panels = List.copyOf(panels);
        if (panels.stream().map(Panel::id).distinct().count() != panels.size()) {
            throw new IllegalArgumentException("Duplicate pending history panel");
        }
    }

    public Map<String, String> capture(Minecraft client) {
        Map<String, String> snapshots = new LinkedHashMap<>();
        for (Panel panel : panels) {
            if (panel.editor().isDirty()) {
                snapshots.put(panel.id(), ItemStackCodecs.encodeStackSnbt(client, panel.editor().pendingStack(client)));
            }
        }
        return Map.copyOf(snapshots);
    }

    public void restore(Minecraft client, Map<String, String> snapshots) {
        for (Panel panel : panels) {
            String source = snapshots.get(panel.id());
            if (source != null) {
                panel.editor().restorePending(client, ItemStackCodecs.decodeStackSnbt(client, source));
            }
        }
    }

    public record Panel(String id, PendingStackEditor editor) {
        public Panel {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(editor, "editor");
        }
    }
}
