package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.ReviewCaseListItem;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.repository.CaseSuggestionRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursEntrySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ReviewServiceTest {
    private ReviewCaseRepository reviewRepository;
    private CaseSuggestionRepository reviewSourceRepository;
    private TezaursEntryRepository tezaursEntryRepository;
    private TezaursEntrySearchService tezaursEntrySearchService;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewCaseRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewSourceRepository = mock(CaseSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        tezaursEntryRepository = mock(TezaursEntryRepository.class);
        tezaursEntrySearchService = mock(TezaursEntrySearchService.class);
        service = new ReviewService(reviewRepository, reviewSourceRepository,
                tezaursEntryRepository, tezaursEntrySearchService);
        when(reviewRepository.save(any(ReviewCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewSourceRepository.save(any(CaseSuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reviewLookupUsesTheReviewId() {
        UUID missingId = UUID.randomUUID();
        when(reviewRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(missingId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void theFirstStatusChangeCreatesTheLinkedReview() {
        IncubatorSuggestion source = source();

        ReviewCase review = service.startReview(source, SuggestionStatus.READY_FOR_REVIEW);

        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.READY_FOR_REVIEW);
        ArgumentCaptor<CaseSuggestion> link = ArgumentCaptor.forClass(CaseSuggestion.class);
        verify(reviewSourceRepository).save(link.capture());
        assertThat(link.getValue().getSourceSuggestion()).isSameAs(source);
        assertThat(link.getValue().getReviewCase()).isSameAs(review);
        assertThat(ReviewResponse.from(review).sources()).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(source.getId());
                    assertThat(item.submittedDefinition()).isEqualTo(source.getGloss());
                });
    }

    @Test
    void anExistingReviewCannotBeChangedThroughItsSourceSuggestionId() {
        IncubatorSuggestion source = source();
        when(reviewSourceRepository.existsById(source.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.startReview(source, SuggestionStatus.GARBAGE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findsExistingReviewsAndLinksDuplicateSources() {
        IncubatorSuggestion source = source();
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);
        when(reviewRepository.findByReviewedTermContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(List.of(review));
        when(reviewSourceRepository.countByReviewCaseId(review.getId())).thenReturn(2L);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var candidates = service.findDuplicateCandidates("  term  ");
        service.linkDuplicate(source, review.getId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().sourceCount()).isEqualTo(2);
        ArgumentCaptor<CaseSuggestion> link = ArgumentCaptor.forClass(CaseSuggestion.class);
        verify(reviewSourceRepository).save(link.capture());
        assertThat(link.getValue().getSourceSuggestion()).isSameAs(source);
        assertThat(link.getValue().getReviewCase()).isSameAs(review);
    }

    @Test
    void listsAndChangesReviewsByReviewId() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);
        ReviewCaseListItem item = mock(ReviewCaseListItem.class);
        when(item.getId()).thenReturn(review.getId());
        when(item.getReviewedTerm()).thenReturn("term");
        when(item.getStatus()).thenReturn(SuggestionStatus.READY_FOR_REVIEW);
        when(item.getSourceCount()).thenReturn(2L);
        when(reviewRepository.findForReviewQueue(
                org.mockito.ArgumentMatchers.eq(SuggestionStatus.READY_FOR_REVIEW),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var page = service.list(SuggestionStatus.READY_FOR_REVIEW,
                " term ", 0, 20, ReviewSort.MOST_SUGGESTIONS);
        var changed = service.changeStatus(review.getId(),
                new UpdateStatusRequest(SuggestionStatus.IN_PROGRESS));

        assertThat(page.items()).singleElement()
                .satisfies(result -> assertThat(result.id()).isEqualTo(review.getId()));
        assertThat(changed.status()).isEqualTo(SuggestionStatus.IN_PROGRESS);
    }

    @Test
    void validatesTezaursEntriesAndSearchesByTheReviewTerm() {
        ReviewCase review = inProgressReview();
        review.correctTerm("corrected");
        review.recordTezaursCheck(TezaursStatus.FOUND, 42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        service.findTezaursCandidates(review.getId(), null);
        verify(tezaursEntrySearchService).findMatches("corrected");

        service.findTezaursCandidates(review.getId(), "  another term  ");
        verify(tezaursEntrySearchService).findMatches("another term");
        when(tezaursEntryRepository.existsById(99)).thenReturn(false);
        assertThatThrownBy(() -> service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.FOUND, 99)))
                .isInstanceOf(ConflictException.class);
    }

    private static IncubatorSuggestion source() {
        return new IncubatorSuggestion(42, "term", "definition");
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        return review;
    }
}
