package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
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
        TezaursStatus checkStatus = review.getTezaursStatus();
        if (checkStatus == TezaursStatus.NOT_CHECKED) {
            return CompletionBlockReason.TEZAURS_CHECK_REQUIRED;
        }
        if (checkStatus == TezaursStatus.FOUND) {
            return CompletionBlockReason.MEANING_CHECK_REQUIRED;
        }
        if (checkStatus == TezaursStatus.MEANING_ALREADY_EXISTS) {
            return null;
        }
        if (checkStatus == TezaursStatus.NOT_FOUND && !hasValidEntryDraft(review)) {
            return CompletionBlockReason.ENTRY_DRAFT_REQUIRED;
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
        if (review.getTezaursStatus() == TezaursStatus.MEANING_NEEDS_EDITING) {
            return !senses.isEmpty();
        }
        for (ReviewSense sense : senses) {
            if (sense.getSourceSenseId() == null) {
                return true;
            }
        }
        return false;
    }
}
