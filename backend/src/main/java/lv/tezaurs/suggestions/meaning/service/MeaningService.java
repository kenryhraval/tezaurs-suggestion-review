package lv.tezaurs.suggestions.meaning.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.dto.MeaningResponse;
import lv.tezaurs.suggestions.meaning.entity.ReviewMeaning;
import lv.tezaurs.suggestions.meaning.repository.ReviewMeaningRepository;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeaningService {
    private final ReviewMeaningRepository meaningRepository;
    private final ReviewSuggestionRepository reviewRepository;

    @Transactional(readOnly = true)
    public List<MeaningResponse> list(UUID reviewId) {
        requireReview(reviewId);
        return meaningRepository.findAllByReviewIdOrderByCreatedAtAscIdAsc(reviewId).stream()
                .map(MeaningResponse::from)
                .toList();
    }

    @Transactional
    public MeaningResponse revise(UUID reviewId, UUID meaningId, CreateMeaningRevisionRequest request) {
        ReviewMeaning currentMeaning = requireMeaningInReview(reviewId, meaningId);
        ReviewMeaning revision = currentMeaning.revise(request.gloss().trim());

        // Release the unique CURRENT slot before inserting its replacement.
        meaningRepository.flush();
        ReviewMeaning savedRevision = meaningRepository.saveAndFlush(revision);
        return MeaningResponse.from(savedRevision);
    }

    private ReviewSuggestion requireReview(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review " + reviewId + " was not found"));
    }

    private ReviewMeaning requireMeaningInReview(UUID reviewId, UUID meaningId) {
        return meaningRepository.findByIdAndReviewId(meaningId, reviewId)
                .orElseThrow(() -> new NotFoundException("Meaning " + meaningId + " was not found"));
    }
}
