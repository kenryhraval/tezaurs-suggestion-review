package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.entity.ReviewMeaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.repository.ReviewMeaningRepository;
import lv.tezaurs.suggestions.suggestion.dto.AddCorpusExampleRequest;
import lv.tezaurs.suggestions.suggestion.dto.CorpusExampleResponse;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.DuplicateReviewCandidateResponse;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCorpusExample;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestionSource;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCorpusExampleRepository;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionSourceRepository;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursEntrySearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final IncubatorSuggestionRepository sourceSuggestionRepository;
    private final ReviewSuggestionRepository reviewRepository;
    private final ReviewSuggestionSourceRepository reviewSourceRepository;
    private final ReviewMeaningRepository meaningRepository;
    private final ReviewCorpusExampleRepository corpusExampleRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final TezaursEntrySearchService tezaursEntrySearchService;

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findBySourceSuggestionId(Integer sourceSuggestionId) {
        requireSourceSuggestion(sourceSuggestionId);
        return reviewSourceRepository.findById(sourceSuggestionId)
                .map(ReviewSuggestionSource::getReview)
                .map(ReviewResponse::from);
    }

    @Transactional
    public ReviewSuggestion changeStatus(IncubatorSuggestion source, SuggestionStatus status) {
        ReviewSuggestion review = reviewSourceRepository.findById(source.getId())
                .map(ReviewSuggestionSource::getReview)
                .orElseGet(() -> create(source));
        review.changeStatus(status);
        return review;
    }

    @Transactional(readOnly = true)
    public List<DuplicateReviewCandidateResponse> findDuplicateCandidates(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return reviewRepository.findByReviewedTermContainingIgnoreCase(
                        query.trim(), PageRequest.of(0, 5, Sort.by("reviewedTerm")))
                .stream()
                .map(review -> DuplicateReviewCandidateResponse.from(
                        review, reviewSourceRepository.countByReviewId(review.getId())))
                .toList();
    }

    @Transactional
    public void linkDuplicate(IncubatorSuggestion source, UUID reviewId) {
        if (reviewSourceRepository.existsById(source.getId())) {
            throw new ConflictException("The source suggestion already belongs to a review");
        }
        ReviewSuggestion review = requireReview(reviewId);
        reviewSourceRepository.save(
                new ReviewSuggestionSource(source, review, false));
    }

    @Transactional
    public ReviewResponse correctTerm(UUID reviewId, CorrectTermRequest request) {
        ReviewSuggestion review = requireReview(reviewId);
        review.correctTerm(request.reviewedTerm().trim());
        reviewRepository.flush();
        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse recordTezaursCheck(UUID reviewId, RecordTezaursCheckRequest request) {
        ReviewSuggestion review = requireReview(reviewId);
        if (request.status().entryExists()
                && (request.matchedEntryId() == null
                || !tezaursEntryRepository.existsById(request.matchedEntryId()))) {
            throw new ConflictException("The selected Tēzaurs entry does not exist");
        }
        review.recordTezaursCheck(request.status(), request.matchedEntryId());
        reviewRepository.flush();
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<TezaursEntryCandidateResponse> findTezaursCandidates(UUID reviewId, String query) {
        ReviewSuggestion review = requireReview(reviewId);
        boolean customSearch = query != null && !query.isBlank();
        String searchTerm = customSearch ? query.trim() : review.getReviewedTerm();
        Integer selectedEntryId = customSearch ? null : review.getMatchedEntryId();
        return tezaursEntrySearchService.findMatches(searchTerm, selectedEntryId);
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
        ReviewSuggestion review = requireReview(reviewId);
        String url = request.url();
        if (corpusExampleRepository.existsByReviewIdAndUrl(review.getId(), url)) {
            throw new ConflictException("This corpus example link has already been added");
        }

        review.recordCorpusExample();
        ReviewCorpusExample example = new ReviewCorpusExample(review.getId(), url);
        return CorpusExampleResponse.from(corpusExampleRepository.saveAndFlush(example));
    }

    private IncubatorSuggestion requireSourceSuggestion(Integer id) {
        return sourceSuggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }

    private ReviewSuggestion requireReview(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }

    private ReviewSuggestion create(IncubatorSuggestion source) {
        ReviewSuggestion review = reviewRepository.save(
                new ReviewSuggestion(source.getLemma()));
        reviewSourceRepository.save(
                new ReviewSuggestionSource(source, review, true));
        meaningRepository.save(new ReviewMeaning(review.getId(), MeaningOrigin.SUBMITTER, source.getGloss()));
        return review;
    }
}
