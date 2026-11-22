package lv.tezaurs.tip.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.review.dto.ReviewSort;
import lv.tezaurs.tip.review.dto.ReviewSuggestionResponse;
import lv.tezaurs.tip.review.dto.UpdateReviewTypeRequest;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.sense.service.SenseService;
import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
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
    private ReviewRepository reviewRepository;
    private ReviewSuggestionRepository reviewSourceRepository;
    private TezaursEntryRepository tezaursEntryRepository;
    private SenseRepository senseRepository;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewSourceRepository = mock(ReviewSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        tezaursEntryRepository = mock(TezaursEntryRepository.class);
        senseRepository = mock(SenseRepository.class);
        service = new ReviewService(reviewRepository, reviewSourceRepository,
                tezaursEntryRepository, new CurrentUserService(),
                mock(EntryService.class), senseRepository, mock(SenseService.class));
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewSourceRepository.save(any(ReviewSuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reviewLookupUsesTheReviewId() {
        UUID missingId = UUID.randomUUID();
        when(reviewRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(missingId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void publicLinkFindsTheReviewAfterItsTitleChanges() {
        Review review = inProgressReview();
        ReflectionTestUtils.setField(review, "publicId", "k7m2pa");
        when(reviewRepository.findByPublicId("k7m2pa")).thenReturn(Optional.of(review));

        assertThat(service.findByPublicId("k7m2pa").id()).isEqualTo(review.getId());
        review.setReviewedTerm("corrected title");

        ReviewResponse response = service.findByPublicId("k7m2pa");
        assertThat(response.publicId()).isEqualTo("k7m2pa");
        assertThat(response.reviewedTerm()).isEqualTo("corrected title");
        assertThatThrownBy(() -> service.findByPublicId("absent"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findsExistingReviewsAndLinksDuplicateSources() {
        IncubatorSuggestion source = source();
        Review sourceReview = new Review(source.getLemma(), ReviewStatus.NEW, "case01");
        ReviewSuggestion sourceLink = new ReviewSuggestion(source, sourceReview);
        when(reviewRepository.findLockedById(sourceReview.getId())).thenReturn(Optional.of(sourceReview));
        Review target = new Review("term", "reviewer", "case01");
        target.setStatus(ReviewStatus.READY_FOR_REVIEW);
        target.setLastStatusChangedBy("reviewer");
        new ReviewSuggestion(new IncubatorSuggestion(43, "term", "other meaning"), target);
        when(reviewRepository.findDuplicateCandidates(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE)),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(List.of(target));
        when(reviewSourceRepository.countByReviewId(target.getId())).thenReturn(2L);
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var candidates = service.findDuplicateCandidates("  term  ", null);
        ReviewResponse linked = service.mergeReviews(sourceReview.getId(), target.getId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().sourceCount()).isEqualTo(2);
        assertThat(linked.id()).isEqualTo(target.getId());
        assertThat(linked.sources()).extracting(ReviewSuggestionResponse::id).containsExactly(42, 43);
        assertThat(target.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        assertThat(sourceLink.getReview()).isSameAs(target);
        verify(reviewRepository).delete(sourceReview);
        verify(reviewRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMergingWhenEitherReviewIsCompleted(boolean sourceIsCompleted) {
        Review source = inProgressReview();
        Review target = inProgressReview();
        (sourceIsCompleted ? source : target).setStatus(ReviewStatus.COMPLETED);
        ReviewSuggestion link = new ReviewSuggestion(source(), source);
        when(reviewRepository.findLockedById(source.getId())).thenReturn(Optional.of(source));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.mergeReviews(source.getId(), target.getId()))
                .isInstanceOf(ConflictException.class);

        assertThat(link.getReview()).isSameAs(source);
        verify(reviewSourceRepository, never()).flush();
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void locksBothReviewsBeforeMovingSuggestions() {
        Review source = inProgressReview();
        Review target = inProgressReview();
        ReviewSuggestion link = new ReviewSuggestion(source(), source);
        when(reviewRepository.findLockedById(source.getId())).thenReturn(Optional.of(source));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        ReviewResponse result = service.mergeReviews(source.getId(), target.getId());

        var operations = inOrder(reviewRepository, reviewSourceRepository);
        operations.verify(reviewRepository).findLockedById(source.getId());
        operations.verify(reviewRepository).findLockedById(target.getId());
        operations.verify(reviewSourceRepository).flush();
        operations.verify(reviewRepository).delete(source);
        verify(reviewRepository, never()).findById(any());
        assertThat(link.getReview()).isSameAs(target);
        assertThat(result.id()).isEqualTo(target.getId());
    }

    @Test
    void doesNotMoveSuggestionsIfTheOtherReviewIsMissingAfterWaitingForItsLock() {
        Review source = inProgressReview();
        ReflectionTestUtils.setField(source, "id", new UUID(0, 1));
        UUID missingTargetId = new UUID(0, 2);
        ReviewSuggestion link = new ReviewSuggestion(source(), source);
        when(reviewRepository.findLockedById(source.getId())).thenReturn(Optional.of(source));
        when(reviewRepository.findLockedById(missingTargetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mergeReviews(source.getId(), missingTargetId))
                .isInstanceOf(NotFoundException.class);

        assertThat(source.getSuggestions()).containsExactly(link);
        assertThat(link.getReview()).isSameAs(source);
        verify(reviewSourceRepository, never()).flush();
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void ordinaryStatusChangesUseTheVersionedReviewWithoutAnExplicitReadLock() {
        Review review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW), authorization("mikus"));

        assertThat(response.status()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        verify(reviewRepository, never()).findLockedById(any());
    }

    @Test
    void rejectsMergingAReviewIntoItself() {
        IncubatorSuggestion source = source();
        Review ongoing = inProgressReview();
        ReviewSuggestion link = new ReviewSuggestion(source, ongoing);
        when(reviewRepository.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
        when(reviewRepository.findById(ongoing.getId())).thenReturn(Optional.of(ongoing));
        when(reviewRepository.findLockedById(ongoing.getId())).thenReturn(Optional.of(ongoing));
        assertThatThrownBy(() -> service.mergeReviews(ongoing.getId(), ongoing.getId()))
                .isInstanceOf(ConflictException.class);
        verify(reviewRepository, never()).delete(any());
        verify(reviewSourceRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void mergingPreservesTheDestinationStatus(ReviewStatus status) {
        IncubatorSuggestion source = source();
        Review sourceReview = inProgressReview();
        ReviewSuggestion link = new ReviewSuggestion(source, sourceReview);
        Review target = inProgressReview();
        target.setType(ReviewType.SENSE);
        target.setMatchedEntryId(42);
        ReflectionTestUtils.setField(target, "status", status);
        when(reviewRepository.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var result = service.mergeReviews(link.getReview().getId(), target.getId());

        assertThat(result.status()).isEqualTo(status);
        assertThat(target.getMatchedEntryId()).isEqualTo(42);
        assertThat(target.getType()).isEqualTo(ReviewType.SENSE);
        verify(reviewRepository).delete(sourceReview);
    }

    @Test
    void reassignsOnlyTheSourceOfAnEmptyPreliminaryReview() {
        IncubatorSuggestion source = source();
        Review preliminary = new Review("term", "reviewer", "case01");
        preliminary.setStatus(ReviewStatus.READY_FOR_REVIEW);
        preliminary.setLastStatusChangedBy("reviewer");
        ReviewSuggestion link = new ReviewSuggestion(source, preliminary);
        Review target = inProgressReview();
        new ReviewSuggestion(new IncubatorSuggestion(43, "term", "definition"), target);
        when(reviewRepository.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var result = service.mergeReviews(link.getReview().getId(), target.getId());

        assertThat(result.id()).isEqualTo(target.getId());
        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.sources()).extracting(ReviewSuggestionResponse::id).containsExactly(42, 43);
        assertThat(preliminary.getSuggestions()).isEmpty();
        assertThat(link.getReview()).isSameAs(target);
        verify(reviewRepository).delete(preliminary);
        verify(reviewSourceRepository, never()).save(any());
    }

    @Test
    void mergesAllSourcesOfAWorkedReviewAndKeepsTheDestinationData() {
        IncubatorSuggestion source = source();
        Review preliminary = new Review("term", "reviewer", "case01");
        preliminary.setStatus(ReviewStatus.READY_FOR_REVIEW);
        preliminary.setLastStatusChangedBy("reviewer");
        ReviewSuggestion link = new ReviewSuggestion(source, preliminary);
        ReviewSuggestion other = new ReviewSuggestion(new IncubatorSuggestion(43, "term", "definition"), preliminary);
        Review target = inProgressReview();
        new ReviewSuggestion(new IncubatorSuggestion(44, "target", "definition"), target);
        preliminary.setStatus(ReviewStatus.IN_PROGRESS);
        preliminary.setLastStatusChangedBy("reviewer");
        preliminary.setType(ReviewType.ENTRY);
        preliminary.setMatchedEntryId(null);
        preliminary.setUsageDecision(UsageDecision.USED);
        preliminary.setUsageNote(null);
        preliminary.setUsageDecidedBy("reviewer");
        preliminary.setUsageDecidedAt(java.time.Instant.now());
        when(reviewRepository.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
        when(reviewRepository.findLockedById(target.getId())).thenReturn(Optional.of(target));

        var result = service.mergeReviews(link.getReview().getId(), target.getId());

        assertThat(result.sources()).extracting(ReviewSuggestionResponse::id).containsExactly(42, 43, 44);
        assertThat(link.getReview()).isSameAs(target);
        assertThat(other.getReview()).isSameAs(target);
        assertThat(preliminary.getSuggestions()).isEmpty();
        assertThat(target.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(target.getType()).isEqualTo(ReviewType.UNKNOWN);
        var order = org.mockito.Mockito.inOrder(reviewSourceRepository, reviewRepository);
        order.verify(reviewSourceRepository).flush();
        order.verify(reviewRepository).delete(preliminary);
    }

    @Test
    void listsAndChangesReviewsByReviewId() {
        Review review = new Review("term", "reviewer", "case01");
        review.setStatus(ReviewStatus.READY_FOR_REVIEW);
        review.setLastStatusChangedBy("reviewer");
        ReflectionTestUtils.setField(review, "publicId", "k7m2pa");
        new ReviewSuggestion(new IncubatorSuggestion(1, "term", "one meaning"), review);
        new ReviewSuggestion(new IncubatorSuggestion(2, "term", "another meaning"), review);
        when(reviewRepository.findForReviewQueue(
                org.mockito.ArgumentMatchers.eq(ReviewStatus.READY_FOR_REVIEW),
                org.mockito.ArgumentMatchers.eq(List.of(ReviewType.values())),
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var page = service.list(ReviewStatus.READY_FOR_REVIEW, null,
                " term ", 0, 20, ReviewSort.MOST_SUGGESTIONS);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
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
    void detachesOneSuggestionWithoutChangingTheReviewDecision() {
        Review review = inProgressReview();
        ReviewSuggestion detached = new ReviewSuggestion(source(), review);
        ReviewSuggestion retained = new ReviewSuggestion(new IncubatorSuggestion(43, "other", "definition"), review);
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        var response = service.moveSuggestionToNewReview(review.getId(), 42);

        assertThat(response.sources()).extracting(ReviewSuggestionResponse::id).containsExactly(43);
        assertThat(review.getSuggestions()).containsExactly(retained);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
        Review newReview = detached.getReview();
        assertThat(newReview.getPublicId()).matches("[a-z0-9]{6}");
        assertThat(newReview).isNotSameAs(review);
        assertThat(newReview.getStatus()).isEqualTo(ReviewStatus.NEW);
        assertThat(newReview.getLastStatusChangedBy()).isNull();
        assertThat(newReview.getSuggestions()).containsExactly(detached);
        verify(reviewRepository).save(newReview);
        verify(reviewSourceRepository, never()).delete(any());
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void rejectsDetachingTheLastSuggestionOrASuggestionFromAnotherReview() {
        Review review = inProgressReview();
        new ReviewSuggestion(source(), review);
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.moveSuggestionToNewReview(review.getId(), 42))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.moveSuggestionToNewReview(review.getId(), 99))
                .isInstanceOf(NotFoundException.class);
        assertThat(review.getSuggestions()).hasSize(1);
        verify(reviewSourceRepository, never()).delete(any());
    }

    @Test
    void rejectsReviewTypeChangesToAnUnknownTezaursEntry() {
        Review review = inProgressReview();
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        when(tezaursEntryRepository.existsById(99)).thenReturn(false);
        assertThatThrownBy(() -> service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.SENSE, 99, false), authorization("mikus")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsTheEntryDraftWhenTheReviewerConfirmsAnAbsentEntry() {
        Review review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.ENTRY, null, false), authorization("mikus"));

        assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
    }

    @Test
    void confirmingAnExistingMeaningAlsoClosesTheReview() {
        Review review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);

        var result = service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.SENSE, 42, true), authorization("mikus"));

        assertThat(result.type()).isEqualTo(ReviewType.SENSE);
        assertThat(result.status()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(result.lastStatusChangedBy()).isEqualTo("mikus");

        assertThatThrownBy(() -> service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.SENSE, 42, true), authorization("mikus")))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void resumingAnAlreadyExistingReviewAllowsCorrectingItsMeaningChoice() {
        Review review = inProgressReview();
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        review.setStatus(ReviewStatus.ALREADY_EXISTS);
        review.setLastStatusChangedBy("reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);

        service.changeStatus(review.getId(), new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("mikus"));

        var result = service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.SENSE, 42, false), authorization("mikus"));

        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.type()).isEqualTo(ReviewType.SENSE);
        assertThat(result.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(result.matchedEntryId()).isEqualTo(42);
    }

    @Test
    void resumingAClosedReviewAllowsChangingItToANewEntry() {
        Review review = inProgressReview();
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        review.setStatus(ReviewStatus.ALREADY_EXISTS);
        review.setLastStatusChangedBy("reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));

        service.changeStatus(review.getId(), new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("mikus"));

        var result = service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.ENTRY, null, false), authorization("mikus"));

        assertThat(result.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(result.type()).isEqualTo(ReviewType.ENTRY);
        assertThat(result.matchedEntryId()).isNull();
    }

    @Test
    void completionRequiresAnApprovedSenseDraft() {
        Review review = inProgressReview();
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.COMPLETED), authorization("mikus")))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        var operations = inOrder(reviewRepository, senseRepository);
        operations.verify(reviewRepository).findById(review.getId());
        operations.verify(senseRepository).findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42);
        var approvedSense = new lv.tezaurs.tip.sense.entity.Sense(review.getId(), 42);
        approvedSense.setApproved(true);
        when(senseRepository.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(approvedSense));
        assertThat(service.changeStatus(review.getId(), new UpdateStatusRequest(ReviewStatus.COMPLETED), authorization("mikus")).status())
                .isEqualTo(ReviewStatus.COMPLETED);
    }

    private static IncubatorSuggestion source() {
        return new IncubatorSuggestion(42, "term", "definition");
    }

    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void everyStatusChangeRequiresAUsername(ReviewStatus status) {
        Review review = inProgressReview();

        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(status), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(reviewRepository, never()).findById(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aDictionaryDecisionRequiresAUsernameBeforeItCanCloseTheReview() {
        Review review = inProgressReview();

        assertThatThrownBy(() -> service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.SENSE, 42, true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getType()).isEqualTo(ReviewType.UNKNOWN);
        verify(tezaursEntryRepository, never()).existsById(any());
    }

    @Test
    void takingAnAlreadyExistingReviewReopensItAndKeepsTheSelectedEntry() {
        Review review = inProgressReview();
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        review.setStatus(ReviewStatus.ALREADY_EXISTS);
        review.setLastStatusChangedBy("closer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        ReviewResponse response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("mikus"));

        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(response.type()).isEqualTo(ReviewType.SENSE);
        assertThat(response.matchedEntryId()).isEqualTo(42);
    }

    @Test
    void takingAnImportedAlreadyExistingReviewStartsWithTheDictionaryCheck() {
        Review review = new Review("term", ReviewStatus.ALREADY_EXISTS, "case01");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        ReviewResponse response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("mikus"));

        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.lastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void takingAReviewRecordsTheAuthenticatedUsernameAndCanBeRetried() {
        Review review = new Review("term", "reviewer", "case01");
        review.setStatus(ReviewStatus.READY_FOR_REVIEW);
        review.setLastStatusChangedBy("reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        var request = new UpdateStatusRequest(ReviewStatus.IN_PROGRESS);

        var claimed = service.changeStatus(review.getId(), request, authorization("mikus"));
        var retried = service.changeStatus(review.getId(), request, authorization("mikus"));

        assertThat(claimed.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(claimed.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(retried.lastStatusChangedBy()).isEqualTo("mikus");
        verify(reviewRepository, org.mockito.Mockito.times(2)).findById(review.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic invalid", "Bearer token"})
    void takingAReviewRequiresAnAuthenticatedUsername(String authorization) {
        Review review = new Review("term", "reviewer", "case01");
        review.setStatus(ReviewStatus.READY_FOR_REVIEW);
        review.setLastStatusChangedBy("reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization))
                .isInstanceOf(ConflictException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
    }

    @Test
    void takingAnActiveReviewDoesNotReplaceItsLastStatusAuthor() {
        Review review = new Review("term", "reviewer", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("mikus");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura"));

        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.lastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void resumingAnExpertReviewReturnsItsConfirmedUsageForTheDraftView() {
        Review review = inProgressReview();
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        review.setUsageDecision(UsageDecision.USED);
        review.setUsageNote(null);
        review.setUsageDecidedBy("reviewer");
        review.setUsageDecidedAt(java.time.Instant.now());
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        review.setLastStatusChangedBy("reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        ReviewResponse response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("mikus"));

        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.usageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(response.usageDecidedBy()).isEqualTo("reviewer");
    }

    @Test
    void anotherReviewerReceivesTheSavedUsageCheckAfterTheReviewIsReleased() {
        Review review = inProgressReview();
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("mikus");
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        review.setUsageDecision(UsageDecision.USED);
        review.setUsageNote("Confirmed usage");
        review.setUsageDecidedBy("mikus");
        review.setUsageDecidedAt(java.time.Instant.now());
        var decisionTime = review.getUsageDecidedAt();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        service.changeStatus(review.getId(), new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW),
                authorization("mikus"));
        ReviewResponse response = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.IN_PROGRESS), authorization("laura"));

        assertThat(response.lastStatusChangedBy()).isEqualTo("laura");
        assertThat(response.usageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(response.usageNote()).isEqualTo("Confirmed usage");
        assertThat(response.usageDecidedBy()).isEqualTo("mikus");
        assertThat(response.usageDecidedAt()).isEqualTo(decisionTime);
    }

    @Test
    void givingUpAReviewKeepsItsChecksAndAllowsAnotherUserToTakeIt() {
        Review review = new Review("term", "reviewer", "case01");
        review.setStatus(ReviewStatus.READY_FOR_REVIEW);
        review.setLastStatusChangedBy("reviewer");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("mikus");
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var released = service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.READY_FOR_REVIEW), authorization("mikus"));

        assertThat(released.status()).isEqualTo(ReviewStatus.READY_FOR_REVIEW);
        assertThat(released.lastStatusChangedBy()).isEqualTo("mikus");
        assertThat(released.type()).isEqualTo(ReviewType.SENSE);
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
    void keepsTheReviewOpenWhenAMeaningIsMissing() {
        Review review = inProgressReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursEntryRepository.existsById(42)).thenReturn(true);
        var response = service.changeType(review.getId(),
                new UpdateReviewTypeRequest(ReviewType.SENSE, 42, false), authorization("mikus"));
        assertThat(response.status()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(response.type()).isEqualTo(ReviewType.SENSE);
    }

    private static Review inProgressReview() {
        Review review = new Review("term", "reviewer", "case01");
        review.setStatus(ReviewStatus.READY_FOR_REVIEW);
        review.setLastStatusChangedBy("reviewer");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("reviewer");
        return review;
    }
}
