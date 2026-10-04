package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.dto.AddCorpusExampleRequest;
import lv.tezaurs.suggestions.suggestion.dto.CorpusExampleResponse;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.CorpusExample;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.repository.CorpusExampleRepository;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages review data that belongs to a source suggestion. */
@Service
@RequiredArgsConstructor
public class ReviewService {
    private final IncubatorSuggestionRepository incubatorSuggestionRepository;
    private final SuggestionRepository suggestionRepository;
    private final MeaningRepository meaningRepository;
    private final CorpusExampleRepository corpusExampleRepository;

    @Transactional
    public Optional<ReviewResponse> findBySourceSuggestionId(Integer sourceSuggestionId) {
        requireSourceSuggestion(sourceSuggestionId);
        Suggestion review = suggestionRepository.findBySourceSuggestionId(sourceSuggestionId).orElse(null);
        if (review == null) {
            IncubatorSuggestion source = requireSourceSuggestionForUpdate(sourceSuggestionId);
            review = suggestionRepository.findBySourceSuggestionId(sourceSuggestionId).orElse(null);
            if (review == null && source.getStatus().requiresReviewRecord()) {
                review = create(source);
            }
        }
        return Optional.ofNullable(review).map(ReviewResponse::from);
    }

    @Transactional
    public Suggestion ensureCreated(IncubatorSuggestion source) {
        return suggestionRepository.findBySourceSuggestionId(source.getId())
                .orElseGet(() -> create(source));
    }

    @Transactional
    public ReviewResponse correctTerm(UUID reviewId, CorrectTermRequest request) {
        Suggestion review = requireReview(reviewId);
        review.correctTerm(request.reviewedTerm().trim());
        suggestionRepository.flush();
        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse recordTezaursCheck(UUID reviewId, RecordTezaursCheckRequest request) {
        Suggestion review = requireReview(reviewId);
        review.recordTezaursCheck(request.status(), request.matchedEntryId());
        suggestionRepository.flush();
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<CorpusExampleResponse> listCorpusExamples(UUID reviewId) {
        requireReview(reviewId);
        return corpusExampleRepository.findAllByReviewIdOrderByCreatedAtAscIdAsc(reviewId).stream()
                .map(CorpusExampleResponse::from)
                .toList();
    }

    @Transactional
    public CorpusExampleResponse addCorpusExample(UUID reviewId, AddCorpusExampleRequest request) {
        Suggestion review = requireReview(reviewId);
        String url = request.url();
        if (corpusExampleRepository.existsByReviewIdAndUrl(review.getId(), url)) {
            throw new ConflictException("This corpus example link has already been added");
        }

        review.recordCorpusExample();
        CorpusExample example = new CorpusExample(review.getId(), url);
        return CorpusExampleResponse.from(corpusExampleRepository.saveAndFlush(example));
    }

    private IncubatorSuggestion requireSourceSuggestion(Integer id) {
        return incubatorSuggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }

    private IncubatorSuggestion requireSourceSuggestionForUpdate(Integer id) {
        return incubatorSuggestionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }

    private Suggestion requireReview(UUID id) {
        return suggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }

    private Suggestion create(IncubatorSuggestion source) {
        Suggestion review = suggestionRepository.saveAndFlush(new Suggestion(source.getId()));
        meaningRepository.save(new Meaning(review.getId(), MeaningOrigin.SUBMITTER, source.getGloss()));
        return review;
    }
}
