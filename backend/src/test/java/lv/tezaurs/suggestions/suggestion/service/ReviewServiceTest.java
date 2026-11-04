package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import lv.tezaurs.suggestions.auth.service.CurrentUserService;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.ReviewCaseListItem;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSourceResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.repository.CaseSuggestionRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursEntrySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewServiceTest {
    private ReviewCaseRepository reviewRepository;
    private CaseSuggestionRepository reviewSourceRepository;
    private TezaursEntryRepository tezaursEntryRepository;
    private TezaursEntrySearchService tezaursEntrySearchService;
    private ReviewCompletionService completionService;
    private ReviewSenseRepository senseRepository;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewCaseRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewSourceRepository = mock(CaseSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        tezaursEntryRepository = mock(TezaursEntryRepository.class);
        tezaursEntrySearchService = mock(TezaursEntrySearchService.class);
        senseRepository = mock(ReviewSenseRepository.class);
        completionService = new ReviewCompletionService(mock(ReviewEntryRepository.class),
                mock(ReviewLexemeRepository.class), senseRepository);
        service = new ReviewService(completionService, reviewRepository, reviewSourceRepository,
                tezaursEntryRepository, tezaursEntrySearchService, new CurrentUserService());
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

        ReviewCase review = service.startReview(source, ReviewStatus.READY_FOR_REVIEW, authorization("mikus"));

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        ArgumentCaptor<CaseSuggestion> link = ArgumentCaptor.forClass(CaseSuggestion.class);
        verify(reviewSourceRepository).save(link.capture());
        assertThat(link.getValue().getSourceSuggestion()).isSameAs(source);
        assertThat(link.getValue().getReviewCase()).isSameAs(review);
        assertThat(ReviewResponse.from(review, null).sources()).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(source.getId());
                    assertThat(item.submittedDefinition()).isEqualTo(source.getGloss());
                });
    }

    @Test
    void anExistingReviewCannotBeChangedThroughItsSourceSuggestionId() {
        IncubatorSuggestion source = source();
        when(reviewSourceRepository.existsById(source.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.startReview(source, ReviewStatus.GARBAGE, authorization("mikus")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findsExistingReviewsAndLinksDuplicateSources() {
        IncubatorSuggestion source = source();
        ReviewCase target = new ReviewCase("term");
        target.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        new CaseSuggestion(new IncubatorSuggestion(43, "term", "other meaning"), target);
        when(reviewRepository.findDuplicateCandidates(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(ReviewStatus.GARBAGE),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(List.of(target));
        when(reviewSourceRepository.countByReviewCaseId(target.getId())).thenReturn(2L);
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));

        var candidates = service.findDuplicateCandidates("  term  ", null);
        ReviewResponse linked = service.linkDuplicate(source, target.getId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().sourceCount()).isEqualTo(2);
        assertThat(linked.id()).isEqualTo(target.getId());
        assertThat(linked.sources()).extracting(ReviewSourceResponse::id).containsExactly(42, 43);
        assertThat(target.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        ArgumentCaptor<CaseSuggestion> link = ArgumentCaptor.forClass(CaseSuggestion.class);
        verify(reviewSourceRepository).save(link.capture());
        assertThat(link.getValue().getSourceSuggestion()).isSameAs(source);
        assertThat(link.getValue().getReviewCase()).isSameAs(target);
        verify(reviewRepository, never()).delete(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsMergingACaseIntoItself() {
        IncubatorSuggestion source = source();
        ReviewCase ongoing = inProgressReview();
        CaseSuggestion link = new CaseSuggestion(source, ongoing);
        when(reviewSourceRepository.findById(source.getId())).thenReturn(Optional.of(link));
        when(reviewRepository.findById(ongoing.getId())).thenReturn(Optional.of(ongoing));
        assertThatThrownBy(() -> service.linkDuplicate(source, ongoing.getId()))
                .isInstanceOf(ConflictException.class);
        verify(reviewRepository, never()).delete(any());
        verify(reviewSourceRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void mergingPreservesTheDestinationStatus(ReviewStatus status) {
        IncubatorSuggestion source = source();
        ReviewCase sourceCase = inProgressReview();
        CaseSuggestion link = new CaseSuggestion(source, sourceCase);
        ReviewCase target = inProgressReview();
        target.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        ReflectionTestUtils.setField(target, "status", status);
        when(reviewSourceRepository.findById(source.getId())).thenReturn(Optional.of(link));
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));

        var result = service.linkDuplicate(source, target.getId());

        assertThat(result.status()).isEqualTo(status);
        assertThat(target.getMatchedEntryId()).isEqualTo(42);
        assertThat(target.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
        verify(reviewRepository).delete(sourceCase);
    }

    @Test
    void reassignsOnlyTheSourceOfAnEmptyPreliminaryCase() {
        IncubatorSuggestion source = source();
        ReviewCase preliminary = new ReviewCase("term");
        preliminary.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        CaseSuggestion link = new CaseSuggestion(source, preliminary);
        ReviewCase target = inProgressReview();
        new CaseSuggestion(new IncubatorSuggestion(43, "term", "definition"), target);
        when(reviewSourceRepository.findById(source.getId())).thenReturn(Optional.of(link));
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));

        var result = service.linkDuplicate(source, target.getId());

        assertThat(result.id()).isEqualTo(target.getId());
        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.sources()).extracting(ReviewSourceResponse::id).containsExactly(42, 43);
        assertThat(preliminary.getSources()).isEmpty();
        assertThat(link.getReviewCase()).isSameAs(target);
        verify(reviewRepository).delete(preliminary);
        verify(reviewSourceRepository, never()).save(any());
    }

    @Test
    void mergesAllSourcesOfAWorkedCaseAndKeepsTheDestinationData() {
        IncubatorSuggestion source = source();
        ReviewCase preliminary = new ReviewCase("term");
        preliminary.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        CaseSuggestion link = new CaseSuggestion(source, preliminary);
        CaseSuggestion other = new CaseSuggestion(new IncubatorSuggestion(43, "term", "definition"), preliminary);
        ReviewCase target = inProgressReview();
        new CaseSuggestion(new IncubatorSuggestion(44, "target", "definition"), target);
        preliminary.changeStatus(ReviewStatus.IN_PROGRESS);
        preliminary.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        preliminary.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, null);
        when(reviewSourceRepository.findById(source.getId())).thenReturn(Optional.of(link));
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));

        var result = service.linkDuplicate(source, target.getId());

        assertThat(result.sources()).extracting(ReviewSourceResponse::id).containsExactly(42, 43, 44);
        assertThat(link.getReviewCase()).isSameAs(target);
        assertThat(other.getReviewCase()).isSameAs(target);
        assertThat(preliminary.getSources()).isEmpty();
        assertThat(target.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(target.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_CHECKED);
        var order = org.mockito.Mockito.inOrder(reviewSourceRepository, reviewRepository);
        order.verify(reviewSourceRepository).flush();
        order.verify(reviewRepository).delete(preliminary);
    }

    @Test
    void listsAndChangesReviewsByReviewId() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        ReviewCaseListItem item = mock(ReviewCaseListItem.class);
        when(item.getId()).thenReturn(review.getId());
        when(item.getReviewedTerm()).thenReturn("term");
        when(item.getStatus()).thenReturn(ReviewStatus.READY_FOR_REVIEW);
        when(item.getSourceCount()).thenReturn(2L);
        when(reviewRepository.findForReviewQueue(
                org.mockito.ArgumentMatchers.eq(ReviewStatus.READY_FOR_REVIEW),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var page = service.list(ReviewStatus.READY_FOR_REVIEW,
                " term ", 0, 20, ReviewSort.MOST_SUGGESTIONS);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        var changed = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), "Basic bWlrdXM6cHc=");

        assertThat(page.items()).singleElement()
                .satisfies(result -> assertThat(result.id()).isEqualTo(review.getId()));
        assertThat(changed.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(changed.assignedTo()).isEqualTo("mikus");
    }

    @Test
    void detachesOneSuggestionWithoutChangingTheCaseDecision() {
        ReviewCase review = inProgressReview();
        CaseSuggestion detached = new CaseSuggestion(source(), review);
        CaseSuggestion retained = new CaseSuggestion(new IncubatorSuggestion(43, "other", "definition"), review);
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var response = service.detachSuggestion(review.getId(), 42);

        assertThat(response.sources()).extracting(ReviewSourceResponse::id).containsExactly(43);
        assertThat(review.getSources()).containsExactly(retained);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
        verify(reviewSourceRepository).delete(detached);
        verify(reviewRepository, never()).delete(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsDetachingTheLastSuggestionOrASuggestionFromAnotherCase() {
        ReviewCase review = inProgressReview();
        new CaseSuggestion(source(), review);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.detachSuggestion(review.getId(), 42))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.detachSuggestion(review.getId(), 99))
                .isInstanceOf(NotFoundException.class);
        assertThat(review.getSources()).hasSize(1);
        verify(reviewSourceRepository, never()).delete(any());
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
                new RecordTezaursCheckRequest(TezaursStatus.FOUND, 99), authorization("mikus")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsTheEntryDraftWhenTheReviewerConfirmsAnAbsentEntry() {
        ReviewCase review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.NOT_FOUND, null), authorization("mikus"));

        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
    }

    @Test
    void confirmingAnExistingMeaningAlsoClosesTheCase() {
        ReviewCase review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);

        var result = service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.MEANING_ALREADY_EXISTS, 42), authorization("mikus"));

        assertThat(result.tezaursStatus()).isEqualTo(TezaursStatus.MEANING_ALREADY_EXISTS);
        assertThat(result.status()).isEqualTo(ReviewStatus.ALREADY_EXISTS);

        var repeated = service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.MEANING_ALREADY_EXISTS, 42), authorization("mikus"));
        assertThat(repeated.status()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
    }

    @Test
    void correctingAnAlreadyExistingMeaningReopensTheCase() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);

        var result = service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.MEANING_NOT_FOUND, 42), authorization("mikus"));

        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.tezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
        assertThat(result.matchedEntryId()).isEqualTo(42);
    }

    @Test
    void correctingAClosedCaseToAnAbsentEntryReopensItAndCreatesADraft() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var result = service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.NOT_FOUND, null), authorization("mikus"));

        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.tezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
        assertThat(result.matchedEntryId()).isNull();
    }

    @Test
    void directCompletionRequestsUseTheSameDraftChecksAsTheResponse() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        assertThat(service.find(review.getId()).canComplete()).isFalse();
        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.COMPLETED), authorization("mikus")))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(new lv.tezaurs.suggestions.sense.entity.ReviewSense(review.getId(), 42)));
        assertThat(service.find(review.getId()).canComplete()).isTrue();
        assertThat(service.changeStatus(review.getId(), new UpdateStatusRequest(ReviewStatus.COMPLETED), authorization("mikus")).status())
                .isEqualTo(ReviewStatus.COMPLETED);
    }

    private static IncubatorSuggestion source() {
        return new IncubatorSuggestion(42, "term", "definition");
    }

    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void everyStatusChangeRequiresAUsername(ReviewStatus status) {
        ReviewCase review = inProgressReview();

        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(status), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");
        assertThatThrownBy(() -> service.startReview(source(), status, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(reviewRepository, never()).findLockedById(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aDictionaryDecisionRequiresAUsernameBeforeItCanCloseTheCase() {
        ReviewCase review = inProgressReview();

        assertThatThrownBy(() -> service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.MEANING_ALREADY_EXISTS, 42), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_CHECKED);
        verify(tezaursEntryRepository, never()).existsById(any());
    }

    @Test
    void takingAReviewRecordsTheAuthenticatedUsernameAndCanBeRetried() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        var request = new UpdateStatusRequest(ReviewStatus.IN_PROGRESS);

        var claimed = service.changeStatus(review.getId(), request, authorization("mikus"));
        var retried = service.changeStatus(review.getId(), request, authorization("mikus"));

        assertThat(claimed.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(claimed.assignedTo()).isEqualTo("mikus");
        assertThat(retried.assignedTo()).isEqualTo("mikus");
        verify(reviewRepository, org.mockito.Mockito.times(2)).findLockedById(review.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic invalid", "Bearer token"})
    void takingAReviewRequiresAnAuthenticatedUsername(String authorization) {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization))
                .isInstanceOf(ConflictException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        assertThat(review.getAssignedTo()).isNull();
    }

    @Test
    void anotherUserCannotTakeOrGiveUpAnAssignedReview() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        review.takeForReview("mikus");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW), authorization("laura")))
                .isInstanceOf(ConflictException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getAssignedTo()).isEqualTo("mikus");
    }

    @Test
    void givingUpAReviewKeepsItsChecksAndAllowsAnotherUserToTakeIt() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        review.takeForReview("mikus");
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var released = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW), authorization("mikus"));

        assertThat(released.status()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        assertThat(released.assignedTo()).isNull();
        assertThat(released.tezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
        assertThat(released.matchedEntryId()).isEqualTo(42);

        var reassigned = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura"));
        assertThat(reassigned.assignedTo()).isEqualTo("laura");
    }

    @Test
    void startingThroughASourceIdAlsoRecordsTheReviewer() {
        var review = service.startReview(source(), ReviewStatus.IN_PROGRESS, authorization("mikus"));

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getAssignedTo()).isEqualTo("mikus");
    }

    private static String authorization(String username) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":test").getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void keepsTheCaseOpenWhenAnExistingMeaningNeedsEditing() {
        ReviewCase review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);
        var response = service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.MEANING_NEEDS_EDITING, 42), authorization("mikus"));
        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.tezaursStatus()).isEqualTo(TezaursStatus.MEANING_NEEDS_EDITING);
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("term");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        review.changeStatus(ReviewStatus.IN_PROGRESS);
        return review;
    }
}
