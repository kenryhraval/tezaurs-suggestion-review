package lv.tezaurs.suggestions.meaning.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.dto.MeaningResponse;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the single meaning revision chain for each review. */
@Service
@RequiredArgsConstructor
public class MeaningService {
    private final MeaningRepository meaningRepository;
    private final SuggestionRepository suggestionRepository;

    @Transactional(readOnly = true)
    public List<MeaningResponse> list(UUID reviewId) {
        requireReview(reviewId);
        return meaningRepository.findAllByReviewIdOrderByCreatedAtAscIdAsc(reviewId).stream()
                .map(MeaningResponse::from)
                .toList();
    }

    @Transactional
    public MeaningResponse revise(UUID reviewId, UUID meaningId, CreateMeaningRevisionRequest request) {
        Meaning currentMeaning = requireMeaningInReview(reviewId, meaningId);
        Meaning revision = currentMeaning.revise(request.gloss().trim());

        // Release the unique CURRENT slot before inserting its replacement.
        meaningRepository.flush();
        Meaning savedRevision = meaningRepository.saveAndFlush(revision);
        return MeaningResponse.from(savedRevision);
    }

    private Suggestion requireReview(UUID reviewId) {
        return suggestionRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review " + reviewId + " was not found"));
    }

    private Meaning requireMeaningInReview(UUID reviewId, UUID meaningId) {
        return meaningRepository.findByIdAndReviewId(meaningId, reviewId)
                .orElseThrow(() -> new NotFoundException("Meaning " + meaningId + " was not found"));
    }
}
