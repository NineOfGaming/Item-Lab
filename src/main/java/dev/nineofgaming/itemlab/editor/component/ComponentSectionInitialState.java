package dev.nineofgaming.itemlab.editor.component;

import dev.nineofgaming.itemlab.editor.catalog.ComponentState;

/**
 * Resolves the one-time expansion state for component-backed editor sections.
 */
public final class ComponentSectionInitialState {
    private ComponentSectionInitialState() {
    }

    public static boolean resolve(
        boolean collapseAbsentComponents,
        boolean focusedComponentEditor,
        ComponentState... states
    ) {
        if (!collapseAbsentComponents || focusedComponentEditor || states.length == 0) {
            return true;
        }
        for (ComponentState state : states) {
            if (state != ComponentState.ABSENT) return true;
        }
        return false;
    }
}
