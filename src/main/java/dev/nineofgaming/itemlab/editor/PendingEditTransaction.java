package dev.nineofgaming.itemlab.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Composes pending editor previews without changing the live editor state.
 *
 * <p>Participants are evaluated in registration order. Ownership is checked for
 * every dirty participant before the first preview runs, so overlapping editors
 * are never resolved accidentally by that order.</p>
 */
public final class PendingEditTransaction {
    public static final String WHOLE_VALUE = "*";

    private PendingEditTransaction() {
    }

    public static <S> Result<S> prepare(
        S source,
        UnaryOperator<S> copy,
        Consumer<S> validate,
        List<Participant<S>> participants
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(validate, "validate");
        Objects.requireNonNull(participants, "participants");

        List<Participant<S>> dirty = new ArrayList<>();
        for (Participant<S> participant : participants) {
            try {
                if (participant.dirty().get()) dirty.add(participant);
            } catch (RuntimeException exception) {
                return Result.failure(invalid(participant, exception));
            }
        }
        Failure conflict = findConflict(dirty);
        if (conflict != null) return Result.failure(conflict);

        S candidate = copy.apply(source);
        List<String> included = new ArrayList<>(dirty.size());
        for (Participant<S> participant : dirty) {
            try {
                S previewInput = copy.apply(candidate);
                S preview = Objects.requireNonNull(
                    participant.preview().apply(previewInput),
                    participant.id() + " returned no preview"
                );
                validate.accept(preview);
                candidate = copy.apply(preview);
                included.add(participant.id());
            } catch (RuntimeException exception) {
                return Result.failure(invalid(participant, exception));
            }
        }
        return Result.success(copy.apply(candidate), included);
    }

    private static <S> Failure findConflict(List<Participant<S>> participants) {
        Map<String, Participant<S>> claimed = new LinkedHashMap<>();
        Participant<S> wholeValueOwner = null;
        for (Participant<S> participant : participants) {
            Set<String> ownership = participant.ownership();
            if (ownership.contains(WHOLE_VALUE)) {
                Participant<S> existing = wholeValueOwner != null
                    ? wholeValueOwner
                    : claimed.values().stream().findFirst().orElse(null);
                if (existing != null) return conflict(participant, existing, WHOLE_VALUE);
                wholeValueOwner = participant;
                continue;
            }
            if (wholeValueOwner != null && !ownership.isEmpty()) {
                return conflict(participant, wholeValueOwner, WHOLE_VALUE);
            }
            for (String owner : ownership) {
                Participant<S> existing = claimed.putIfAbsent(owner, participant);
                if (existing != null && existing != participant) {
                    return conflict(participant, existing, owner);
                }
            }
        }
        return null;
    }

    private static <S> Failure conflict(
        Participant<S> participant,
        Participant<S> existing,
        String owner
    ) {
        return new Failure(
            FailureKind.CONFLICT,
            participant.id(),
            participant.label(),
            participant.route(),
            participant.focusTarget(),
            existing.id(),
            existing.label(),
            owner
        );
    }

    private static <S> Failure invalid(Participant<S> participant, RuntimeException exception) {
        return new Failure(
            FailureKind.INVALID,
            participant.id(),
            participant.label(),
            participant.route(),
            participant.focusTarget(),
            null,
            null,
            readableMessage(exception)
        );
    }

    private static String readableMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message;
    }

    public record Participant<S>(
        String id,
        String label,
        String route,
        String focusTarget,
        Supplier<Boolean> dirty,
        Set<String> ownership,
        UnaryOperator<S> preview
    ) {
        public Participant {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(dirty, "dirty");
            ownership = Set.copyOf(ownership);
            Objects.requireNonNull(preview, "preview");
        }
    }

    public record Failure(
        FailureKind kind,
        String participantId,
        String participantLabel,
        String route,
        String focusTarget,
        String conflictingParticipantId,
        String conflictingParticipantLabel,
        String detail
    ) {
    }

    public enum FailureKind {
        INVALID,
        CONFLICT
    }

    public record Result<S>(S candidate, List<String> included, Failure failure) {
        private static <S> Result<S> success(S candidate, List<String> included) {
            return new Result<>(candidate, List.copyOf(included), null);
        }

        private static <S> Result<S> failure(Failure failure) {
            return new Result<>(null, List.of(), failure);
        }

        public boolean succeeded() {
            return failure == null;
        }
    }
}
