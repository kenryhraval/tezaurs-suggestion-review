package lv.tezaurs.tip.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.review.dto.ReviewSort;
import lv.tezaurs.tip.review.dto.ReviewSuggestionResponse;
import lv.tezaurs.tip.review.dto.UpdateReviewTitleRequest;
import lv.tezaurs.tip.review.dto.UpdateReviewTypeRequest;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.dto.UsageDecisionRequest;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.sense.service.SenseService;
import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ReviewServiceTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewSuggestionRepository reviewSuggestions = mock(ReviewSuggestionRepository.class);
    private final TezaursEntryRepository tezaursEntries = mock(TezaursEntryRepository.class);
    private final EntryService entries = mock(EntryService.class);
    private final SenseRepository senses = mock(SenseRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final ReviewService service = new ReviewService(reviews, reviewSuggestions, tezaursEntries,
            users, entries, senses, mock(SenseService.class));

    @BeforeEach
    void setUp() {
        when(reviews.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
        when(users.requireUsername(anyString())).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    class DuplicatesAndQueue {
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void rejectsMergingWhenEitherReviewIsCompleted(boolean sourceReviewIsCompleted) {
            Review sourceReview = createInProgressReview();
            Review target = createInProgressReview();
            (sourceReviewIsCompleted ? sourceReview : target).setStatus(ReviewStatus.COMPLETED);
            ReviewSuggestion link = new ReviewSuggestion(createSuggestion(), sourceReview);
            when(reviews.findLockedById(sourceReview.getId())).thenReturn(Optional.of(sourceReview));
            when(reviews.findLockedById(target.getId())).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> service.mergeReviews(sourceReview.getId(), target.getId()))
                    .isInstanceOf(ConflictException.class);

            assertThat(link.getReview()).isSameAs(sourceReview);
            verify(reviewSuggestions, never()).flush();
            verify(reviews, never()).delete(any());
        }

        @Test
        void doesNotMoveSuggestionsIfTheOtherReviewIsMissingAfterWaitingForItsLock() {
            Review sourceReview = createInProgressReview();
            UUID missingTargetId = new UUID(0, 2);
            ReviewSuggestion link = new ReviewSuggestion(createSuggestion(), sourceReview);
            when(reviews.findLockedById(sourceReview.getId())).thenReturn(Optional.of(sourceReview));
            when(reviews.findLockedById(missingTargetId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.mergeReviews(sourceReview.getId(), missingTargetId))
                    .isInstanceOf(NotFoundException.class);

            assertThat(sourceReview.getSuggestions()).containsExactly(link);
            assertThat(link.getReview()).isSameAs(sourceReview);
            verify(reviewSuggestions, never()).flush();
            verify(reviews, never()).delete(any());
        }

        @Test
        void rejectsMergingAReviewIntoItself() {
            IncubatorSuggestion suggestion = createSuggestion();
            Review ongoing = createInProgressReview();
            ReviewSuggestion link = new ReviewSuggestion(suggestion, ongoing);
            when(reviews.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
            assertThatThrownBy(() -> service.mergeReviews(ongoing.getId(), ongoing.getId()))
                    .isInstanceOf(ConflictException.class);
            verify(reviews, never()).delete(any());
            verify(reviewSuggestions, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = ReviewStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
        void mergingPreservesTheDestinationStatus(ReviewStatus status) {
            IncubatorSuggestion suggestion = createSuggestion();
            Review sourceReview = createInProgressReview();
            ReviewSuggestion link = new ReviewSuggestion(suggestion, sourceReview);
            Review target = createInProgressReview();
            target.setType(ReviewType.SENSE);
            target.setMatchedEntryId(42);
            target.setStatus(status);
            when(reviews.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
            when(reviews.findLockedById(target.getId())).thenReturn(Optional.of(target));

            var result = service.mergeReviews(link.getReview().getId(), target.getId());

            assertThat(result.status()).isEqualTo(status);
            assertThat(target.getMatchedEntryId()).isEqualTo(42);
            assertThat(target.getType()).isEqualTo(ReviewType.SENSE);
            verify(reviews).delete(sourceReview);
        }

        @Test
        void mergesAllSuggestionsOfAWorkedReviewAndKeepsTheDestinationData() {
            IncubatorSuggestion suggestion = createSuggestion();
            Review preliminary = createInProgressReview();
            ReviewSuggestion link = new ReviewSuggestion(suggestion, preliminary);
            ReviewSuggestion other = new ReviewSuggestion(new IncubatorSuggestion(43, "term", "definition"), preliminary);
            Review target = createInProgressReview();
            new ReviewSuggestion(new IncubatorSuggestion(44, "target", "definition"), target);
            preliminary.setType(ReviewType.ENTRY);
            preliminary.setUsageDecision(UsageDecision.USED);
            when(reviews.findLockedById(link.getReview().getId())).thenReturn(Optional.of(link.getReview()));
            when(reviews.findLockedById(target.getId())).thenReturn(Optional.of(target));

            var result = service.mergeReviews(link.getReview().getId(), target.getId());

            assertThat(result.sources()).extracting(ReviewSuggestionResponse::id).containsExactly(42, 43, 44);
            assertThat(link.getReview()).isSameAs(target);
            assertThat(other.getReview()).isSameAs(target);
            assertThat(preliminary.getSuggestions()).isEmpty();
            assertThat(target.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(target.getType()).isEqualTo(ReviewType.UNKNOWN);
            var order = org.mockito.Mockito.inOrder(reviewSuggestions, reviews);
            order.verify(reviewSuggestions).flush();
            order.verify(reviews).delete(preliminary);
        }

        @Test
        void listsReviewsWithTheirSuggestionCounts() {
            Review review = new Review("term", "reviewer", "case01");
            review.setStatus(ReviewStatus.READY_FOR_REVIEW);
            review.setLastStatusChangedBy("reviewer");
            new ReviewSuggestion(new IncubatorSuggestion(1, "term", "one meaning"), review);
            new ReviewSuggestion(new IncubatorSuggestion(2, "term", "another meaning"), review);
            when(reviews.findForReviewQueue(
                    org.mockito.ArgumentMatchers.eq(ReviewStatus.READY_FOR_REVIEW),
                    org.mockito.ArgumentMatchers.eq(List.of(ReviewType.values())),
                    org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(review)));
            var page = service.list(ReviewStatus.READY_FOR_REVIEW, null,
                    " term ", 0, 20, ReviewSort.MOST_SUGGESTIONS);

            assertThat(page.items()).singleElement()
                    .satisfies(result -> {
                        assertThat(result.id()).isEqualTo(review.getId());
                        assertThat(result.publicId()).isEqualTo("case01");
                        assertThat(result.sourceCount()).isEqualTo(2);
                    });
        }

        @Test
        void detachesOneSuggestionWithoutChangingTheReviewDecision() {
            Review review = createInProgressReview();
            ReviewSuggestion detached = new ReviewSuggestion(createSuggestion(), review);
            ReviewSuggestion retained = new ReviewSuggestion(new IncubatorSuggestion(43, "other", "definition"), review);
            review.setType(ReviewType.SENSE);
            review.setMatchedEntryId(42);
            when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));

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
            verify(reviews).save(newReview);
            verify(reviewSuggestions, never()).delete(any());
            verify(reviews, never()).delete(any());
        }

        @Test
        void rejectsDetachingTheLastSuggestionOrASuggestionFromAnotherReview() {
            Review review = createInProgressReview();
            new ReviewSuggestion(createSuggestion(), review);
            when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));

            assertThatThrownBy(() -> service.moveSuggestionToNewReview(review.getId(), 42))
                    .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> service.moveSuggestionToNewReview(review.getId(), 99))
                    .isInstanceOf(NotFoundException.class);
            assertThat(review.getSuggestions()).hasSize(1);
            verify(reviewSuggestions, never()).delete(any());
        }

        private static IncubatorSuggestion createSuggestion() {
            return new IncubatorSuggestion(42, "term", "definition");
        }

        private static Review createInProgressReview() {
            Review review = new Review("term", "reviewer", "case01");
            review.setStatus(ReviewStatus.IN_PROGRESS);
            return review;
        }
    }

    @Nested
    class Workflow {
        private void stubReviewLookup(Review review) {
            when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
            when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
        }

        private void changeReviewStatus(Review review, ReviewStatus status, String username) {
            stubReviewLookup(review);
            service.changeStatus(review.getId(), new UpdateStatusRequest(status), username);
        }

        private void changeReviewType(Review review, ReviewType type, Integer entryId, boolean alreadyExists, String username) {
            stubReviewLookup(review);
            if (entryId != null) {
                when(tezaursEntries.existsById(entryId)).thenReturn(true);
            }
            service.changeType(review.getId(),
                    new UpdateReviewTypeRequest(type, entryId, alreadyExists), username);
        }

        private void saveUsageDecision(Review review, UsageDecision decision, String note, String username) {
            stubReviewLookup(review);
            service.recordUsageDecision(review.getId(),
                    new UsageDecisionRequest(decision, note), username);
        }

        private void updateReviewTitle(Review review, String title) {
            stubReviewLookup(review);
            service.updateTitle(review.getId(), new UpdateReviewTitleRequest(title));
        }

        @Test
        void importedInProgressReviewsCanBeClaimedWithoutReplacingAnExistingReviewer() {
            Review review = new Review("vārds", ReviewStatus.IN_PROGRESS, "case01");

            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "mikus");
            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "laura");

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("mikus");
        }

        @Test
        void recordsReviewData() {
            Review review = createInProgressReview();
            updateReviewTitle(review, "corrected");
            assertThat(review.getPublicId()).isEqualTo("case01");
            changeReviewType(review, ReviewType.SENSE, 42, false, "reviewer");

            assertThat(review.getReviewedTerm()).isEqualTo("corrected");
            assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
        }

        @Test
        void closingASenseReviewAsAlreadyExistingKeepsItsTargetEntry() {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.SENSE, 42, false, "reviewer");

            changeReviewStatus(review, ReviewStatus.ALREADY_EXISTS, "reviewer");

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
            assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
            assertThat(review.getMatchedEntryId()).isEqualTo(42);
        }

        @Test
        void cannotCloseAsAlreadyExistingWhenTheSelectedEntryNoLongerExists() {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.SENSE, 42, false, "reviewer");
            when(tezaursEntries.existsById(42)).thenReturn(false);

            assertThatThrownBy(() -> changeReviewStatus(review, ReviewStatus.ALREADY_EXISTS, "closer"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Izvēlētais Tēzaura šķirklis netika atrasts.");

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
            assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
            assertThat(review.getMatchedEntryId()).isEqualTo(42);
        }

        @Test
        void closingAsAlreadyExistingStillRequiresAMatchingEntry() {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.ENTRY, null, false, "reviewer");
            saveUsageDecision(review, lv.tezaurs.tip.review.entity.UsageDecision.USED, null, "reviewer");

            assertThatThrownBy(() -> changeReviewStatus(review, ReviewStatus.ALREADY_EXISTS, "reviewer"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Izvēlieties atbilstošo Tēzaura šķirkli.");
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
        }

        @Test
        void rejectsReviewDataThatDoesNotMatchTheTezaursCheck() {
            Review review = createInProgressReview();

            assertThatThrownBy(() -> changeReviewType(review, ReviewType.ENTRY, 42, false, "reviewer"))
                    .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> changeReviewType(review, ReviewType.SENSE, null, false, "reviewer"))
                    .isInstanceOf(ConflictException.class);

        }

        @Test
        void rejectsContradictoryTypeChoicesWithoutChangingTheReview() {
            Review review = createInProgressReview();

            assertThatThrownBy(() -> changeReviewType(review, ReviewType.ENTRY, null, true, "reviewer"))
                    .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> changeReviewType(review, ReviewType.UNKNOWN, 42, false, "reviewer"))
                    .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> changeReviewType(review, null, null, false, "reviewer"))
                    .isInstanceOf(ConflictException.class);

            assertThat(review.getType()).isEqualTo(ReviewType.UNKNOWN);
            assertThat(review.getMatchedEntryId()).isNull();
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        }

        @Test
        void selectingAnExistingMeaningClosesTheReviewAndFurtherEditsRequireResuming() {
            Review review = createInProgressReview();

            changeReviewType(review, ReviewType.SENSE, 42, true, "closer");

            assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
            assertThat(review.getMatchedEntryId()).isEqualTo(42);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");

            assertThatThrownBy(() -> changeReviewType(review, ReviewType.SENSE, 99, false, "editor"))
                    .isInstanceOf(ConflictException.class);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
            assertThat(review.getMatchedEntryId()).isEqualTo(42);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");

            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "editor");
            changeReviewType(review, ReviewType.SENSE, 99, false, "editor");

            assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
            assertThat(review.getMatchedEntryId()).isEqualTo(99);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
        }

        @ParameterizedTest
        @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
        void requiresResumingBeforeChangingTheTezaursCheck(ReviewStatus status) {
            Review review = createInProgressReview();
            review.setType(ReviewType.ENTRY);
            review.setStatus(status);
            Review paused = review;
            ReviewType previousCheck = paused.getType();

            assertThatThrownBy(() -> changeReviewType(paused, ReviewType.SENSE, 42, false, "reviewer"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Apstrādē");
            assertThat(paused.getStatus()).isEqualTo(status);
            assertThat(paused.getType()).isEqualTo(previousCheck);
            assertThat(paused.getMatchedEntryId()).isNull();
        }

        @ParameterizedTest
        @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
        void requiresResumingBeforeChangingTheTitle(ReviewStatus status) {
            Review review = createInProgressReview();
            review.setType(ReviewType.SENSE);
            review.setMatchedEntryId(42);
            review.setStatus(status);
            Review paused = review;

            assertThatThrownBy(() -> updateReviewTitle(paused, "changed"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Apstrādē");
            assertThat(paused.getReviewedTerm()).isEqualTo("submitted");
        }

        private Review createInProgressReview() {
            Review review = new Review("submitted", "reviewer", "case01");
            review.setStatus(ReviewStatus.IN_PROGRESS);
            stubReviewLookup(review);
            return review;
        }

        @ParameterizedTest
        @EnumSource(value = ReviewStatus.class, names = {
                "READY_FOR_REVIEW", "GARBAGE", "COMPLETED", "INVENTED",
                "ALREADY_EXISTS", "INSUFFICIENT_DATA", "NEEDS_EXPERT"})
        void recordsWhoActuallyChangedTheStatus(ReviewStatus nextStatus) {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.SENSE, 42, false, "researcher");

            when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                    .thenReturn(List.of(createApprovedSense(review.getId(), 42)));
            changeReviewStatus(review, nextStatus, "editor");

            assertThat(review.getStatus()).isEqualTo(nextStatus);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
        }

        @Test
        void recordsUsageStatusChangesButNotRepeatedDecisionsOrTitleEdits() {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.ENTRY, null, false, "researcher");
            saveUsageDecision(review, UsageDecision.USED, null, "visitor");
            assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
            updateReviewTitle(review, "corrected");
            assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");

            saveUsageDecision(review, UsageDecision.NOT_CONFIRMED, null, "researcher");
            assertThat(review.getLastStatusChangedBy()).isEqualTo("researcher");
            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "linguist");
            saveUsageDecision(review, UsageDecision.INVENTED, null, "linguist");
            assertThat(review.getLastStatusChangedBy()).isEqualTo("linguist");
            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "expert");
            saveUsageDecision(review, UsageDecision.USED, null, "expert");
            assertThat(review.getLastStatusChangedBy()).isEqualTo("expert");
        }

        @Test
        void recordsThePersonWhoReleasedTheReview() {
            Review review = new Review("submitted", "reviewer", "case01");
            changeReviewStatus(review, ReviewStatus.READY_FOR_REVIEW, "sorter");
            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "owner");
            assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");

            changeReviewStatus(review, ReviewStatus.READY_FOR_REVIEW, "owner");

            assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");

            assertThatThrownBy(() -> changeReviewStatus(review, ReviewStatus.NEW, "visitor"))
                    .isInstanceOf(ConflictException.class);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");
        }

        @Test
        void takingAnAlreadyActiveReviewKeepsTheUsageAnswer() {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.ENTRY, null, false, "reviewer");
            saveUsageDecision(review, UsageDecision.USED, null, "reviewer");

            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");
            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");

            assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
            assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
            assertThat(review.getUsageDecidedAt()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(value = UsageDecision.class, names = {"NOT_CONFIRMED", "INVENTED"})
        void usageDecisionCanChangeOnlyAfterResumingAPausedReview(UsageDecision previousDecision) {
            Review review = createInProgressReview();
            changeReviewType(review, ReviewType.ENTRY, null, false, "reviewer");
            saveUsageDecision(review, previousDecision, null, "reviewer");

            assertThatThrownBy(() -> saveUsageDecision(review, UsageDecision.USED, null, "editor"))
                    .isInstanceOf(ConflictException.class);
            assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "editor");
            assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
            saveUsageDecision(review, UsageDecision.USED, null, "editor");

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
            assertThat(review.getUsageDecidedBy()).isEqualTo("editor");
            assertThat(review.getUsageDecidedAt()).isNotNull();
        }

        @Test
        void authenticationFailureLeavesTheReviewUnchanged() {
            Review review = createInProgressReview();
            var denied = new ConflictException("Authentication required");
            when(users.requireUsername("missing")).thenThrow(denied);

            assertThatThrownBy(() -> changeReviewStatus(review, ReviewStatus.NEEDS_EXPERT, "missing"))
                    .isSameAs(denied);
            assertThatThrownBy(() -> changeReviewType(review, ReviewType.SENSE, 42, false, "missing"))
                    .isSameAs(denied);
            assertThatThrownBy(() -> saveUsageDecision(review, UsageDecision.USED, null, "missing"))
                    .isSameAs(denied);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        }

        @ParameterizedTest
        @CsvSource({
                "READY_FOR_REVIEW, USED", "NEEDS_EXPERT, USED", "ALREADY_EXISTS, USED",
                "INSUFFICIENT_DATA, NOT_CONFIRMED", "INVENTED, INVENTED"
        })
        void resumingPreservesTheUsageAssessment(ReviewStatus status, UsageDecision decision) {
            Review review = createInProgressReview();
            review.setType(ReviewType.ENTRY);
            saveUsageDecision(review, decision, "Saved assessment", "reviewer");
            var decisionTime = review.getUsageDecidedAt();
            review.setStatus(status);

            changeReviewStatus(review, ReviewStatus.IN_PROGRESS, "editor");

            assertThat(review.getUsageDecision()).isEqualTo(decision);
            assertThat(review.getUsageNote()).isEqualTo("Saved assessment");
            assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
            assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
        }

        private static Sense createApprovedSense(UUID reviewId, Integer entryId) {
            Sense sense = new Sense(reviewId, entryId);
            sense.setApproved(true);
            return sense;
        }
    }

    @Nested
    class Completion {
        @BeforeEach
        void setUpUser() {
            when(users.requireUsername("authorization")).thenReturn("reviewer");
        }

        @Test
        void requiresDictionaryCheckBeforeCompletion() {
            Review review = stubReviewForCompletion(ReviewType.UNKNOWN, null);

            assertCompletionRejected(review, "Vispirms veiciet pārbaudi Tēzaurā.");
        }

        @Test
        void requiresConfirmedUsageForNewEntries() {
            Review review = stubReviewForCompletion(ReviewType.ENTRY, null);

            assertCompletionRejected(review, "Vispirms apstipriniet vārda lietojumu.");
        }

        @Test
        void requiresAReadyEntryDraft() {
            Review review = stubReviewForCompletion(ReviewType.ENTRY, null);
            review.setUsageDecision(UsageDecision.USED);

            assertCompletionRejected(review, "Saglabājiet šķirkļa melnrakstu ar vienu galveno leksēmu.");
        }

        @Test
        void requiresANewSenseForTheSelectedEntry() {
            Review review = stubReviewForCompletion(ReviewType.SENSE, 99);
            when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                    .thenReturn(List.of(createApprovedSense(review.getId(), 42)));

            assertCompletionRejected(review, "Pievienojiet melnrakstam vismaz vienu jaunu nozīmi.");
        }

        @Test
        void unapprovedCandidatesAndExistingSensesDoNotAllowCompletion() {
            Review review = stubReviewForCompletion(ReviewType.SENSE, 42);
            Sense candidate = new Sense(UUID.randomUUID(), review.getId(), 42, "Priekšlikums", null, 1);
            Sense existing = new Sense(UUID.randomUUID(), review.getId(), 42, "Esoša nozīme", 7, 2);
            when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                    .thenReturn(List.of(candidate, existing));

            assertCompletionRejected(review, "Pievienojiet melnrakstam vismaz vienu jaunu nozīmi.");
        }

        @Test
        void completesNewEntriesWithConfirmedUsageAndRequiredDrafts() {
            Review review = stubReviewForCompletion(ReviewType.ENTRY, null);
            review.setUsageDecision(UsageDecision.USED);
            when(entries.isReady(review.getId())).thenReturn(true);
            when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), null))
                    .thenReturn(List.of(createApprovedSense(review.getId(), null)));

            assertThat(service.changeStatus(review.getId(),
                    new UpdateStatusRequest(ReviewStatus.COMPLETED), "authorization").status())
                    .isEqualTo(ReviewStatus.COMPLETED);
        }

        @Test
        void completesNewSenseReviewsWithoutANewEntryDraftOrUsageDecision() {
            Review review = stubReviewForCompletion(ReviewType.SENSE, 42);
            when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                    .thenReturn(List.of(createApprovedSense(review.getId(), 42)));

            assertThat(service.changeStatus(review.getId(),
                    new UpdateStatusRequest(ReviewStatus.COMPLETED), "authorization").status())
                    .isEqualTo(ReviewStatus.COMPLETED);
            verifyNoInteractions(entries);
        }

        @Test
        void readsAndOtherStatusChangesDoNotCheckDraftReadiness() {
            Review review = stubReviewForCompletion(ReviewType.ENTRY, null);
            when(reviews.findById(review.getId())).thenReturn(Optional.of(review));

            assertThat(service.findById(review.getId()).id()).isEqualTo(review.getId());
            assertThat(service.changeStatus(review.getId(),
                    new UpdateStatusRequest(ReviewStatus.NEEDS_EXPERT), "authorization").status())
                    .isEqualTo(ReviewStatus.NEEDS_EXPERT);
            verifyNoInteractions(entries, senses);
        }

        private void assertCompletionRejected(Review review, String message) {
            assertThatThrownBy(() -> service.changeStatus(review.getId(),
                    new UpdateStatusRequest(ReviewStatus.COMPLETED), "authorization"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage(message);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
            assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
        }

        private Review stubReviewForCompletion(ReviewType type, Integer entryId) {
            Review review = new Review("vārds", "reviewer", "case01");
            review.setStatus(ReviewStatus.IN_PROGRESS);
            review.setType(type);
            review.setMatchedEntryId(entryId);
            when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
            return review;
        }
        private static Sense createApprovedSense(UUID reviewId, Integer entryId) {
            Sense sense = new Sense(reviewId, entryId);
            sense.setApproved(true);
            return sense;
        }
    }

    @Nested
    class Creation {
        @Test
        void createsAReviewWithASixCharacterLowercaseAlphanumericId() {
            when(reviews.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));

            Review created = service.createFromSuggestion("vārds", ReviewStatus.NEW);

            assertThat(created.getPublicId()).matches("[a-z0-9]{6}");
            assertThat(created.getReviewedTerm()).isEqualTo("vārds");
            assertThat(created.getStatus()).isEqualTo(ReviewStatus.NEW);
            assertThat(created.getLastStatusChangedBy()).isNull();
            verify(reviews).save(created);
        }

    }

    @Nested
    class UsageAssessment {
        private final Review review = new Review("vārds", "editor", "case01");

        @BeforeEach
        void setUp() {
            review.setStatus(ReviewStatus.IN_PROGRESS);
            review.setType(ReviewType.ENTRY);
            when(users.requireUsername("authorization")).thenReturn("editor");
            when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
        }

        @ParameterizedTest
        @CsvSource({"USED, IN_PROGRESS", "NOT_CONFIRMED, INSUFFICIENT_DATA", "INVENTED, INVENTED"})
        void recordsTheUsageAssessment(UsageDecision decision, ReviewStatus status) {
            service.recordUsageDecision(review.getId(), new UsageDecisionRequest(decision, "Editor note"), "authorization");

            assertThat(review.getStatus()).isEqualTo(status);
            assertThat(review.getUsageDecision()).isEqualTo(decision);
            assertThat(review.getUsageNote()).isEqualTo("Editor note");
            assertThat(review.getUsageDecidedBy()).isEqualTo("editor");
            assertThat(review.getUsageDecidedAt()).isNotNull();
            if (decision == UsageDecision.USED) {
                verify(entries).create(review.getId());
            } else {
                verifyNoInteractions(entries);
            }
        }

        @ParameterizedTest
        @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
        void pausedAndClosedReviewsRejectNewDecisions(ReviewStatus status) {
            review.setStatus(status);

            assertThatThrownBy(() -> service.recordUsageDecision(review.getId(),
                    new UsageDecisionRequest(UsageDecision.USED, null), "authorization"))
                    .isInstanceOf(ConflictException.class);
            assertThat(review.getStatus()).isEqualTo(status);
            assertThat(review.getUsageDecision()).isNull();
            verifyNoInteractions(entries);
        }

        @Test
        void existingEntriesDoNotNeedANewWordUsageDecision() {
            review.setType(ReviewType.SENSE);
            review.setMatchedEntryId(100);

            assertThatThrownBy(() -> service.recordUsageDecision(review.getId(),
                    new UsageDecisionRequest(UsageDecision.USED, null), "authorization"))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(entries);
        }
    }
}
