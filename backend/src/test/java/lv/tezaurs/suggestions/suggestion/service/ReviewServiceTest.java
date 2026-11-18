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
import lv.tezaurs.suggestions.suggestion.dto.UpdateCaseTypeRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSourceResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;
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
    void publicLinkFindsTheCaseAfterItsTitleChanges() {
        ReviewCase review = inProgressReview();
        ReflectionTestUtils.setField(review, "publicId", "k7m2pa");
        when(reviewRepository.findByPublicId("k7m2pa")).thenReturn(Optional.of(review));

        assertThat(service.findByPublicId("k7m2pa").id()).isEqualTo(review.getId());
        review.correctTerm("corrected title");

        ReviewResponse response = service.findByPublicId("k7m2pa");
        assertThat(response.publicId()).isEqualTo("k7m2pa");
        assertThat(response.reviewedTerm()).isEqualTo("corrected title");
        assertThatThrownBy(() -> service.findByPublicId("absent"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findsExistingReviewsAndLinksDuplicateSources() {
        IncubatorSuggestion source = source();
        ReviewCase sourceCase = ReviewCase.fromSuggestion(source.getLemma(), ReviewStatus.NEW);
        CaseSuggestion sourceLink = new CaseSuggestion(source, sourceCase);
        when(reviewRepository.findById(sourceCase.getId())).thenReturn(Optional.of(sourceCase));
        when(reviewRepository.findLockedById(sourceCase.getId())).thenReturn(Optional.of(sourceCase));
        ReviewCase target = new ReviewCase("term", "reviewer");
        target.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        new CaseSuggestion(new IncubatorSuggestion(43, "term", "other meaning"), target);
        when(reviewRepository.findDuplicateCandidates(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE)),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(List.of(target));
        when(reviewSourceRepository.countByReviewCaseId(target.getId())).thenReturn(2L);
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var candidates = service.findDuplicateCandidates("  term  ", null);
        ReviewResponse linked = service.linkDuplicate(sourceCase.getId(), target.getId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().sourceCount()).isEqualTo(2);
        assertThat(linked.id()).isEqualTo(target.getId());
        assertThat(linked.sources()).extracting(ReviewSourceResponse::id).containsExactly(42, 43);
        assertThat(target.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        assertThat(sourceLink.getReviewCase()).isSameAs(target);
        verify(reviewRepository).delete(sourceCase);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsMergingACaseIntoItself() {
        IncubatorSuggestion source = source();
        ReviewCase ongoing = inProgressReview();
        CaseSuggestion link = new CaseSuggestion(source, ongoing);
        when(reviewRepository.findById(link.getReviewCase().getId())).thenReturn(Optional.of(link.getReviewCase()));
        when(reviewRepository.findById(ongoing.getId())).thenReturn(Optional.of(ongoing));
        when(reviewRepository.findLockedById(ongoing.getId())).thenReturn(Optional.of(ongoing));
        assertThatThrownBy(() -> service.linkDuplicate(ongoing.getId(), ongoing.getId()))
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
        target.changeType(CaseType.SENSE, 42, false, "reviewer");
        ReflectionTestUtils.setField(target, "status", status);
        when(reviewRepository.findById(link.getReviewCase().getId())).thenReturn(Optional.of(link.getReviewCase()));
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var result = service.linkDuplicate(link.getReviewCase().getId(), target.getId());

        assertThat(result.status()).isEqualTo(status);
        assertThat(target.getMatchedEntryId()).isEqualTo(42);
        assertThat(target.getType()).isEqualTo(CaseType.SENSE);
        verify(reviewRepository).delete(sourceCase);
    }

    @Test
    void reassignsOnlyTheSourceOfAnEmptyPreliminaryCase() {
        IncubatorSuggestion source = source();
        ReviewCase preliminary = new ReviewCase("term", "reviewer");
        preliminary.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        CaseSuggestion link = new CaseSuggestion(source, preliminary);
        ReviewCase target = inProgressReview();
        new CaseSuggestion(new IncubatorSuggestion(43, "term", "definition"), target);
        when(reviewRepository.findById(link.getReviewCase().getId())).thenReturn(Optional.of(link.getReviewCase()));
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var result = service.linkDuplicate(link.getReviewCase().getId(), target.getId());

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
        ReviewCase preliminary = new ReviewCase("term", "reviewer");
        preliminary.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        CaseSuggestion link = new CaseSuggestion(source, preliminary);
        CaseSuggestion other = new CaseSuggestion(new IncubatorSuggestion(43, "term", "definition"), preliminary);
        ReviewCase target = inProgressReview();
        new CaseSuggestion(new IncubatorSuggestion(44, "target", "definition"), target);
        preliminary.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        preliminary.changeType(CaseType.ENTRY, null, false, "reviewer");
        preliminary.recordUsageDecision(UsageDecision.USED, null, "reviewer");
        when(reviewRepository.findById(link.getReviewCase().getId())).thenReturn(Optional.of(link.getReviewCase()));
        when(reviewRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var result = service.linkDuplicate(link.getReviewCase().getId(), target.getId());

        assertThat(result.sources()).extracting(ReviewSourceResponse::id).containsExactly(42, 43, 44);
        assertThat(link.getReviewCase()).isSameAs(target);
        assertThat(other.getReviewCase()).isSameAs(target);
        assertThat(preliminary.getSources()).isEmpty();
        assertThat(target.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(target.getType()).isEqualTo(CaseType.UNKNOWN);
        var order = org.mockito.Mockito.inOrder(reviewSourceRepository, reviewRepository);
        order.verify(reviewSourceRepository).flush();
        order.verify(reviewRepository).delete(preliminary);
    }

    @Test
    void listsAndChangesReviewsByReviewId() {
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        ReflectionTestUtils.setField(review, "publicId", "k7m2pa");
        new CaseSuggestion(new IncubatorSuggestion(1, "term", "one meaning"), review);
        new CaseSuggestion(new IncubatorSuggestion(2, "term", "another meaning"), review);
        when(reviewRepository.findForReviewQueue(
                org.mockito.ArgumentMatchers.eq(ReviewStatus.READY_FOR_REVIEW),
                org.mockito.ArgumentMatchers.eq(List.of(CaseType.values())),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var page = service.list(ReviewStatus.READY_FOR_REVIEW, null,
                " term ", 0, 20, ReviewSort.MOST_SUGGESTIONS);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        var changed = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), "Basic bWlrdXM6cHc=");

        assertThat(page.items()).singleElement()
                .satisfies(result -> {
                    assertThat(result.id()).isEqualTo(review.getId());
                    assertThat(result.publicId()).isEqualTo("k7m2pa");
                    assertThat(result.sourceCount()).isEqualTo(2);
                });
        assertThat(changed.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(changed.lastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void detachesOneSuggestionWithoutChangingTheCaseDecision() {
        ReviewCase review = inProgressReview();
        CaseSuggestion detached = new CaseSuggestion(source(), review);
        CaseSuggestion retained = new CaseSuggestion(new IncubatorSuggestion(43, "other", "definition"), review);
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var response = service.detachSuggestion(review.getId(), 42);

        assertThat(response.sources()).extracting(ReviewSourceResponse::id).containsExactly(43);
        assertThat(review.getSources()).containsExactly(retained);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
        ReviewCase newCase = detached.getReviewCase();
        assertThat(newCase).isNotSameAs(review);
        assertThat(newCase.getStatus()).isEqualTo(ReviewStatus.NEW);
        assertThat(newCase.getLastStatusChangedBy()).isNull();
        assertThat(newCase.getSources()).containsExactly(detached);
        verify(reviewRepository).save(newCase);
        verify(reviewSourceRepository, never()).delete(any());
        verify(reviewRepository, never()).delete(any());
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
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        service.findTezaursCandidates(review.getId(), null);
        verify(tezaursEntrySearchService).findMatches("corrected");

        service.findTezaursCandidates(review.getId(), "  another term  ");
        verify(tezaursEntrySearchService).findMatches("another term");
        when(tezaursEntryRepository.existsById(99)).thenReturn(false);
        assertThatThrownBy(() -> service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.SENSE, 99, false), authorization("mikus")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsTheEntryDraftWhenTheReviewerConfirmsAnAbsentEntry() {
        ReviewCase review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.ENTRY, null, false), authorization("mikus"));

        assertThat(review.getType()).isEqualTo(CaseType.ENTRY);
    }

    @Test
    void confirmingAnExistingMeaningAlsoClosesTheCase() {
        ReviewCase review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);

        var result = service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.SENSE, 42, true), authorization("mikus"));

        assertThat(result.type()).isEqualTo(CaseType.SENSE);
        assertThat(result.status()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(result.lastStatusChangedBy()).isEqualTo("mikus");

        var repeated = service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.SENSE, 42, true), authorization("mikus"));
        assertThat(repeated.status()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(repeated.lastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void correctingAnAlreadyExistingMeaningReopensTheCase() {
        ReviewCase review = inProgressReview();
        review.changeType(CaseType.SENSE, 42, true, "reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);

        var result = service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.SENSE, 42, false), authorization("mikus"));

        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.type()).isEqualTo(CaseType.SENSE);
        assertThat(result.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(result.matchedEntryId()).isEqualTo(42);
    }

    @Test
    void correctingAClosedCaseToAnAbsentEntryReopensItAndCreatesADraft() {
        ReviewCase review = inProgressReview();
        review.changeType(CaseType.SENSE, 42, true, "reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var result = service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.ENTRY, null, false), authorization("mikus"));

        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.type()).isEqualTo(CaseType.ENTRY);
        assertThat(result.matchedEntryId()).isNull();
    }

    @Test
    void directCompletionRequestsUseTheSameDraftChecksAsTheResponse() {
        ReviewCase review = inProgressReview();
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
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

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(reviewRepository, never()).findLockedById(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aDictionaryDecisionRequiresAUsernameBeforeItCanCloseTheCase() {
        ReviewCase review = inProgressReview();

        assertThatThrownBy(() -> service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.SENSE, 42, true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getType()).isEqualTo(CaseType.UNKNOWN);
        verify(tezaursEntryRepository, never()).existsById(any());
    }

    @Test
    void takingAReviewRecordsTheAuthenticatedUsernameAndCanBeRetried() {
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        var request = new UpdateStatusRequest(ReviewStatus.IN_PROGRESS);

        var claimed = service.changeStatus(review.getId(), request, authorization("mikus"));
        var retried = service.changeStatus(review.getId(), request, authorization("mikus"));

        assertThat(claimed.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(claimed.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(retried.lastStatusChangedBy()).isEqualTo("mikus");
        verify(reviewRepository, org.mockito.Mockito.times(2)).findLockedById(review.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic invalid", "Bearer token"})
    void takingAReviewRequiresAnAuthenticatedUsername(String authorization) {
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization))
                .isInstanceOf(ConflictException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
    }

    @Test
    void takingAnActiveReviewDoesNotReplaceItsLastStatusAuthor() {
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.takeForReview("mikus");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura"));

        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.lastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void resumingAnExpertCaseReturnsItsConfirmedUsageForTheDraftView() {
        ReviewCase review = inProgressReview();
        review.changeType(CaseType.ENTRY, null, false, "reviewer");
        review.recordUsageDecision(UsageDecision.USED, null, "reviewer");
        review.changeStatus(ReviewStatus.NEEDS_EXPERT, "reviewer");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        ReviewResponse response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("mikus"));

        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.usageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(response.usageDecidedBy()).isEqualTo("reviewer");
        assertThat(response.completionBlockReason())
                .isEqualTo(CompletionBlockReason.ENTRY_DRAFT_REQUIRED);
    }

    @Test
    void anotherReviewerReceivesTheSavedUsageCheckAfterTheCaseIsReleased() {
        ReviewCase review = inProgressReview();
        review.takeForReview("mikus");
        review.changeType(CaseType.ENTRY, null, false, "mikus");
        review.recordUsageDecision(UsageDecision.USED, "Confirmed usage", "mikus");
        var decisionTime = review.getUsageDecidedAt();
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        service.changeStatus(review.getId(), new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW),
                authorization("mikus"));
        ReviewResponse response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura"));

        assertThat(response.lastStatusChangedBy()).isEqualTo("laura");
        assertThat(response.usageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(response.usageNote()).isEqualTo("Confirmed usage");
        assertThat(response.usageDecidedBy()).isEqualTo("mikus");
        assertThat(response.usageDecidedAt()).isEqualTo(decisionTime);
        assertThat(response.completionBlockReason()).isEqualTo(CompletionBlockReason.ENTRY_DRAFT_REQUIRED);
    }

    @Test
    void givingUpAReviewKeepsItsChecksAndAllowsAnotherUserToTakeIt() {
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.takeForReview("mikus");
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var released = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW), authorization("mikus"));

        assertThat(released.status()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        assertThat(released.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(released.type()).isEqualTo(CaseType.SENSE);
        assertThat(released.matchedEntryId()).isEqualTo(42);

        var reassigned = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura"));
        assertThat(reassigned.lastStatusChangedBy()).isEqualTo("laura");
    }

    private static String authorization(String username) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":test").getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void keepsTheCaseOpenWhenAMeaningIsMissing() {
        ReviewCase review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);
        var response = service.changeType(review.getId(),
                new UpdateCaseTypeRequest(CaseType.SENSE, 42, false), authorization("mikus"));
        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.type()).isEqualTo(CaseType.SENSE);
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        return review;
    }
}
