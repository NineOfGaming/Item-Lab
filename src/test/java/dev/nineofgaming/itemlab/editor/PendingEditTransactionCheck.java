package dev.nineofgaming.itemlab.editor;

import java.util.List;
import java.util.Set;

/**
 * Headless regression checks for global pending-edit preflight and composition.
 */
public final class PendingEditTransactionCheck {
    private PendingEditTransactionCheck() {
    }

    public static void main(String[] arguments) {
        verifySuccessfulCompositionUsesRegistrationOrder();
        verifyInvalidFirstMiddleAndLastLeaveSourceUntouched();
        verifyOverlappingOwnershipStopsBeforePreview();
        verifyWholeValueOwnershipIsExclusive();
        verifyCleanParticipantsAreIgnored();
        verifyDirtyQueryFailureIsReported();
    }

    private static void verifySuccessfulCompositionUsesRegistrationOrder() {
        MutableValue source = new MutableValue("start");
        PendingEditTransaction.Result<MutableValue> result = prepare(
            source,
            participant("first", "a", value -> value.text += ":one"),
            participant("second", "b", value -> value.text += ":two")
        );
        assertSucceeded(result, "start:one:two", List.of("first", "second"));
        assertText(source, "start", "successful preflight mutated its source");
    }

    private static void verifyInvalidFirstMiddleAndLastLeaveSourceUntouched() {
        for (int failureIndex = 0; failureIndex < 3; failureIndex++) {
            MutableValue source = new MutableValue("start");
            int selectedFailure = failureIndex;
            List<PendingEditTransaction.Participant<MutableValue>> participants = List.of(
                indexedParticipant(0, selectedFailure),
                indexedParticipant(1, selectedFailure),
                indexedParticipant(2, selectedFailure)
            );
            PendingEditTransaction.Result<MutableValue> result = PendingEditTransaction.prepare(
                source,
                MutableValue::copy,
                value -> {
                    if (value.text.contains("invalid")) {
                        throw new IllegalArgumentException("invalid pending value");
                    }
                },
                participants
            );
            if (result.succeeded()
                || result.failure().kind() != PendingEditTransaction.FailureKind.INVALID
                || !result.failure().participantId().equals("participant-" + failureIndex)) {
                throw new AssertionError("wrong invalid participant result: " + result);
            }
            assertText(source, "start", "failed preflight mutated its source");
        }
    }

    private static void verifyOverlappingOwnershipStopsBeforePreview() {
        MutableValue source = new MutableValue("start");
        int[] previews = {0};
        PendingEditTransaction.Result<MutableValue> result = prepare(
            source,
            participant("components", "minecraft:custom_data", value -> {
                previews[0]++;
                value.text += ":components";
            }),
            participant("diamondfire", "minecraft:custom_data", value -> {
                previews[0]++;
                value.text += ":diamondfire";
            })
        );
        if (result.succeeded()
            || result.failure().kind() != PendingEditTransaction.FailureKind.CONFLICT
            || previews[0] != 0) {
            throw new AssertionError("overlap was not rejected before preview: " + result);
        }
        assertText(source, "start", "conflict preflight mutated its source");
    }

    private static void verifyWholeValueOwnershipIsExclusive() {
        PendingEditTransaction.Result<MutableValue> result = prepare(
            new MutableValue("start"),
            participant("raw", PendingEditTransaction.WHOLE_VALUE, value -> value.text = "raw"),
            participant("name", "minecraft:custom_name", value -> value.text += ":name")
        );
        if (result.succeeded()
            || result.failure().kind() != PendingEditTransaction.FailureKind.CONFLICT) {
            throw new AssertionError("whole-value owner was not exclusive: " + result);
        }
    }

    private static void verifyCleanParticipantsAreIgnored() {
        PendingEditTransaction.Participant<MutableValue> clean = new PendingEditTransaction.Participant<>(
            "clean",
            "Clean",
            "clean",
            null,
            () -> false,
            Set.of("same"),
            value -> {
                throw new AssertionError("clean participant was previewed");
            }
        );
        PendingEditTransaction.Result<MutableValue> result = prepare(
            new MutableValue("start"),
            clean,
            participant("dirty", "same", value -> value.text += ":dirty")
        );
        assertSucceeded(result, "start:dirty", List.of("dirty"));
    }

    private static void verifyDirtyQueryFailureIsReported() {
        MutableValue source = new MutableValue("start");
        PendingEditTransaction.Participant<MutableValue> broken = new PendingEditTransaction.Participant<>(
            "broken",
            "Broken",
            "broken",
            "field",
            () -> {
                throw new IllegalStateException("dirty query failed");
            },
            Set.of("owner"),
            value -> value
        );
        PendingEditTransaction.Result<MutableValue> result = prepare(source, broken);
        if (result.succeeded()
            || result.failure().kind() != PendingEditTransaction.FailureKind.INVALID
            || !"broken".equals(result.failure().participantId())
            || !"dirty query failed".equals(result.failure().detail())) {
            throw new AssertionError("dirty query failure was not actionable: " + result);
        }
        assertText(source, "start", "dirty query failure mutated its source");
    }

    @SafeVarargs
    private static PendingEditTransaction.Result<MutableValue> prepare(
        MutableValue source,
        PendingEditTransaction.Participant<MutableValue>... participants
    ) {
        return PendingEditTransaction.prepare(
            source,
            MutableValue::copy,
            value -> {
                if (value.text.contains("invalid")) {
                    throw new IllegalArgumentException("invalid pending value");
                }
            },
            List.of(participants)
        );
    }

    private static PendingEditTransaction.Participant<MutableValue> indexedParticipant(
        int index,
        int failureIndex
    ) {
        return participant(
            "participant-" + index,
            "owner-" + index,
            value -> value.text += index == failureIndex ? ":invalid" : ":valid"
        );
    }

    private static PendingEditTransaction.Participant<MutableValue> participant(
        String id,
        String owner,
        java.util.function.Consumer<MutableValue> preview
    ) {
        return new PendingEditTransaction.Participant<>(
            id,
            id,
            id,
            null,
            () -> true,
            Set.of(owner),
            value -> {
                preview.accept(value);
                return value;
            }
        );
    }

    private static void assertSucceeded(
        PendingEditTransaction.Result<MutableValue> result,
        String text,
        List<String> included
    ) {
        if (!result.succeeded()
            || !result.included().equals(included)
            || !result.candidate().text.equals(text)) {
            throw new AssertionError("unexpected successful result: " + result);
        }
    }

    private static void assertText(MutableValue value, String expected, String message) {
        if (!value.text.equals(expected)) throw new AssertionError(message + ": " + value.text);
    }

    private static final class MutableValue {
        private String text;

        private MutableValue(String text) {
            this.text = text;
        }

        private MutableValue copy() {
            return new MutableValue(text);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
