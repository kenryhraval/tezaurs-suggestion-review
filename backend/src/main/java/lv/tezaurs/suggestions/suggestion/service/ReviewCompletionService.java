package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewCompletionService {
    private final ReviewEntryRepository entryRepository;
    private final ReviewLexemeRepository lexemeRepository;
    private final ReviewSenseRepository senseRepository;

    public ReviewResponse response(ReviewCase review) {
        return ReviewResponse.from(review, completionBlockReason(review));
    }

    public void requireCompletionAllowed(ReviewCase review) {
        if (completionBlockReason(review) != null) {
            throw new ConflictException("The required review checks must be completed first");
        }
    }

    public CompletionBlockReason completionBlockReason(ReviewCase review) {
        CaseType type = review.getType();
        if (type == CaseType.UNKNOWN) {
            return CompletionBlockReason.TEZAURS_CHECK_REQUIRED;
        }
        if (review.getStatus() == ReviewStatus.ALREADY_EXISTS) {
            return null;
        }
        if (type == CaseType.ENTRY) {
            if (review.getUsageDecision() != UsageDecision.USED) {
                return CompletionBlockReason.USAGE_CHECK_REQUIRED;
            }
            if (!hasValidEntryDraft(review)) {
                return CompletionBlockReason.ENTRY_DRAFT_REQUIRED;
            }
        }
        if (!hasRequiredSenseDraft(review)) {
            return CompletionBlockReason.SENSE_DRAFT_REQUIRED;
        }
        return null;
    }

    private boolean hasValidEntryDraft(ReviewCase review) {
        Optional<ReviewEntry> savedEntry = entryRepository.findByCaseId(review.getId());
        if (savedEntry.isEmpty()) {
            return false;
        }
        ReviewEntry entry = savedEntry.get();
        List<ReviewLexeme> lexemes = lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());
        return entry.isReady(lexemes);
    }

    private boolean hasRequiredSenseDraft(ReviewCase review) {
        List<ReviewSense> senses = senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(
                review.getId(), review.getMatchedEntryId());
        return !senses.isEmpty();
    }
}
