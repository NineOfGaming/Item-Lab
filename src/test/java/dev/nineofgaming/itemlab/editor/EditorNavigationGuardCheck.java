package dev.nineofgaming.itemlab.editor;

/**
 * Headless checks for pending-edit navigation decisions.
 */
public final class EditorNavigationGuardCheck {
    private EditorNavigationGuardCheck() {
    }

    public static void main(String[] arguments) {
        verifyCleanAndSameTargetTransitions();
        verifyViewOnlyAndCompatibleTransitions();
        verifyDestructiveRetargets();
        verifyExternalReplacement();
        verifyProgrammaticParity();
        verifyConditionalRouteVisibility();
        verifyRejectedTransitionDoesNotRun();
    }

    private static void verifyCleanAndSameTargetTransitions() {
        assertAllowed(EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            null
        ));
        assertAllowed(EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            true,
            validBlocker()
        ));
    }

    private static void verifyViewOnlyAndCompatibleTransitions() {
        assertAllowed(EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.VIEW_ONLY,
            false,
            invalidBlocker()
        ));
        assertAllowed(EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.COMPATIBLE_RETARGET,
            false,
            validBlocker()
        ));
        assertBlocked(
            EditorNavigationGuard.decide(
                EditorNavigationGuard.Source.USER,
                EditorNavigationGuard.Transition.COMPATIBLE_RETARGET,
                false,
                invalidBlocker()
            ),
            EditorNavigationGuard.PendingState.INVALID,
            "components"
        );
    }

    private static void verifyDestructiveRetargets() {
        EditorNavigationGuard.Decision validPending = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            validBlocker()
        );
        assertBlocked(validPending, EditorNavigationGuard.PendingState.VALID, "components");

        EditorNavigationGuard.Decision invalidPending = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            invalidBlocker()
        );
        assertBlocked(invalidPending, EditorNavigationGuard.PendingState.INVALID, "components");
        if (invalidPending.blocker().focusRequest() == null
            || !"component_value".equals(invalidPending.blocker().focusRequest().target())) {
            throw new AssertionError("invalid pending input lost its focus request");
        }
    }

    private static void verifyExternalReplacement() {
        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.EXTERNAL_REPLACEMENT,
            false,
            validBlocker()
        );
        assertBlocked(decision, EditorNavigationGuard.PendingState.VALID, "components");
    }

    private static void verifyProgrammaticParity() {
        EditorNavigationGuard.Decision user = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.USER,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            validBlocker()
        );
        EditorNavigationGuard.Decision programmatic = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.PROGRAMMATIC,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            validBlocker()
        );
        if (user.allowed() != programmatic.allowed()
            || !user.blocker().equals(programmatic.blocker())) {
            throw new AssertionError("user and programmatic navigation decisions diverged");
        }
    }

    private static void verifyConditionalRouteVisibility() {
        if (EditorNavigationGuard.shouldKeepRouteVisible(false, false)) {
            throw new AssertionError("a clean unavailable route stayed visible");
        }
        if (!EditorNavigationGuard.shouldKeepRouteVisible(false, true)) {
            throw new AssertionError("a dirty conditional route became unreachable");
        }
        if (!EditorNavigationGuard.shouldKeepRouteVisible(true, false)) {
            throw new AssertionError("an available clean route was hidden");
        }
    }

    private static void verifyRejectedTransitionDoesNotRun() {
        String[] selectedRoute = {"components"};
        EditorNavigationGuard.Decision decision = EditorNavigationGuard.decide(
            EditorNavigationGuard.Source.PROGRAMMATIC,
            EditorNavigationGuard.Transition.DESTRUCTIVE_RETARGET,
            false,
            invalidBlocker()
        );
        if (decision.allowed()) selectedRoute[0] = "data";
        if (!"components".equals(selectedRoute[0])) {
            throw new AssertionError("a rejected transition changed the selected route");
        }
    }

    private static EditorNavigationGuard.Blocker validBlocker() {
        return new EditorNavigationGuard.Blocker(
            "Components",
            "components",
            EditorNavigationGuard.PendingState.VALID,
            "Apply or discard the component changes first.",
            null
        );
    }

    private static EditorNavigationGuard.Blocker invalidBlocker() {
        return new EditorNavigationGuard.Blocker(
            "Components",
            "components",
            EditorNavigationGuard.PendingState.INVALID,
            "Fix or discard the invalid component value first.",
            new EditorNavigationGuard.FocusRequest("component_value")
        );
    }

    private static void assertAllowed(EditorNavigationGuard.Decision decision) {
        if (!decision.allowed() || decision.blocker() != null) {
            throw new AssertionError("expected navigation to be allowed: " + decision);
        }
    }

    private static void assertBlocked(
        EditorNavigationGuard.Decision decision,
        EditorNavigationGuard.PendingState pendingState,
        String tab
    ) {
        if (decision.allowed()
            || decision.blocker() == null
            || decision.blocker().pendingState() != pendingState
            || !tab.equals(decision.blocker().tab())
            || decision.blocker().explanation().isBlank()) {
            throw new AssertionError("expected an actionable blocked decision: " + decision);
        }
    }
}
