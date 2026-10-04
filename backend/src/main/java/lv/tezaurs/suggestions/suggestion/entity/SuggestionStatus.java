package lv.tezaurs.suggestions.suggestion.entity;

import java.util.Arrays;
import java.util.Set;
import lv.tezaurs.suggestions.common.error.ConflictException;

/** Workflow status stored in {@code incubator.suggestions.status}. */
public enum SuggestionStatus {
    NEW(0),
    READY_FOR_REVIEW(1),
    GARBAGE(2),
    COMPLETED(3),
    IN_PROGRESS(4),
    INVENTED(5),
    ALREADY_EXISTS(6),
    INSUFFICIENT_DATA(7),
    NEEDS_EXPERT(8);

    private final int databaseValue;

    SuggestionStatus(int databaseValue) {
        this.databaseValue = databaseValue;
    }

    public int databaseValue() {
        return databaseValue;
    }

    public boolean requiresReviewRecord() {
        return this != NEW && this != GARBAGE;
    }

    public boolean canTransitionTo(SuggestionStatus target) {
        return transitions().contains(target);
    }

    public static SuggestionStatus fromDatabaseValue(Integer value) {
        if (Integer.valueOf(83).equals(value)) {
            return NEEDS_EXPERT;
        }
        return Arrays.stream(values())
                .filter(status -> status.databaseValue == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown suggestion status: " + value));
    }

    public void requireTransitionTo(SuggestionStatus target) {
        if (!canTransitionTo(target)) {
            throw new ConflictException("Suggestion status cannot change from " + this + " to " + target);
        }
    }

    private Set<SuggestionStatus> transitions() {
        return switch (this) {
            case NEW -> Set.of(READY_FOR_REVIEW, IN_PROGRESS, GARBAGE);
            case READY_FOR_REVIEW -> Set.of(IN_PROGRESS, COMPLETED, NEW);
            case GARBAGE -> Set.of(NEW);
            case IN_PROGRESS -> Set.of(INVENTED, ALREADY_EXISTS, INSUFFICIENT_DATA,
                    NEEDS_EXPERT, COMPLETED, READY_FOR_REVIEW, GARBAGE);
            case INVENTED, INSUFFICIENT_DATA -> Set.of(IN_PROGRESS, GARBAGE);
            case NEEDS_EXPERT -> Set.of(IN_PROGRESS);
            case ALREADY_EXISTS, COMPLETED -> Set.of();
        };
    }
}
