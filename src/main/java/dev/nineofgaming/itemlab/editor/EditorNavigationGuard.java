package dev.nineofgaming.itemlab.editor;

import java.util.Objects;

/**
 * Pure policy for deciding whether an editor route may change.
 *
 * <p>The guard deliberately does not run the transition. Callers must inspect the
 * returned decision before changing a selected tab, target, mode, or item.</p>
 */
public final class EditorNavigationGuard {
    private EditorNavigationGuard() {
    }

    public static Decision decide(
        Source source,
        Transition transition,
        boolean sameTarget,
        Blocker blocker
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transition, "transition");

        boolean allowed = sameTarget
            || blocker == null
            || transition == Transition.VIEW_ONLY
            || transition == Transition.COMPATIBLE_RETARGET
            && blocker.pendingState() == PendingState.VALID;
        return new Decision(source, transition, allowed, allowed ? null : blocker);
    }

    /**
     * A conditional route stays reachable for as long as it owns pending state.
     */
    public static boolean shouldKeepRouteVisible(boolean normallyVisible, boolean pending) {
        return normallyVisible || pending;
    }

    public enum Source {
        USER,
        PROGRAMMATIC
    }

    public enum Transition {
        VIEW_ONLY,
        COMPATIBLE_RETARGET,
        DESTRUCTIVE_RETARGET,
        EXTERNAL_REPLACEMENT
    }

    public enum PendingState {
        VALID,
        INVALID
    }

    public record FocusRequest(String target) {
        public FocusRequest {
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("focus target must not be blank");
            }
        }
    }

    public record Blocker(
        String editor,
        String tab,
        PendingState pendingState,
        String explanation,
        FocusRequest focusRequest
    ) {
        public Blocker {
            if (editor == null || editor.isBlank()) {
                throw new IllegalArgumentException("blocking editor must not be blank");
            }
            if (tab == null || tab.isBlank()) {
                throw new IllegalArgumentException("blocking tab must not be blank");
            }
            Objects.requireNonNull(pendingState, "pendingState");
            if (explanation == null || explanation.isBlank()) {
                throw new IllegalArgumentException("blocking explanation must not be blank");
            }
        }
    }

    public record Decision(
        Source source,
        Transition transition,
        boolean allowed,
        Blocker blocker
    ) {
        public Decision {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(transition, "transition");
            if (allowed && blocker != null) {
                throw new IllegalArgumentException("an allowed decision cannot retain a blocker");
            }
            if (!allowed && blocker == null) {
                throw new IllegalArgumentException("a rejected decision must identify its blocker");
            }
        }
    }
}
