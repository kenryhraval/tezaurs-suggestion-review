package lv.tezaurs.suggestions.suggestion.entity;

import java.util.Set;
import lv.tezaurs.suggestions.common.error.ConflictException;

public enum SuggestionStatus {
    NEW,
    READY_FOR_REVIEW,
    GARBAGE,
    COMPLETED,
    IN_PROGRESS,
    INVENTED,
    ALREADY_EXISTS,
    INSUFFICIENT_DATA,
    NEEDS_EXPERT;

    public boolean canTransitionTo(SuggestionStatus target) {
        return transitions().contains(target);
    }

    public void requireTransitionTo(SuggestionStatus target) {
        if (!canTransitionTo(target)) {
            throw new ConflictException("Suggestion status cannot change from " + this + " to " + target);
        }
    }

    private Set<SuggestionStatus> transitions() {
        return switch (this) {
            case NEW -> Set.of(READY_FOR_REVIEW, IN_PROGRESS, GARBAGE);
            case READY_FOR_REVIEW -> Set.of(IN_PROGRESS, COMPLETED, GARBAGE);
            case GARBAGE -> Set.of(READY_FOR_REVIEW);
            case IN_PROGRESS -> Set.of(INVENTED, ALREADY_EXISTS, INSUFFICIENT_DATA,
                    NEEDS_EXPERT, COMPLETED, READY_FOR_REVIEW, GARBAGE);
            case INVENTED, INSUFFICIENT_DATA -> Set.of(IN_PROGRESS, GARBAGE);
            case NEEDS_EXPERT -> Set.of(IN_PROGRESS);
            case ALREADY_EXISTS, COMPLETED -> Set.of();
        };
    }
}
