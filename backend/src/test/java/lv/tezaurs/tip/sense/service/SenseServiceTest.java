package lv.tezaurs.tip.sense.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.sense.dto.SenseGroup;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.sense.dto.SaveSenseRequest;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.tezaurs.entity.TezaursSense;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.tip.tezaurs.service.TezaursFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SenseServiceTest {
    private final SenseRepository senses = mock(SenseRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final TezaursSenseRepository tezaursSenses = mock(TezaursSenseRepository.class);
    private final TezaursFlagService flags = mock(TezaursFlagService.class);
    private final ExampleRepository examples = mock(ExampleRepository.class);
    private final SenseService service = new SenseService(senses, reviews, tezaursSenses, flags, examples);
    private final List<Sense> saved = new ArrayList<>();
    private final Review review = new Review("vārds", ReviewStatus.IN_PROGRESS, "case01");

    @BeforeEach
    void prepare() {
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42)).thenReturn(saved);
        when(senses.save(any(Sense.class))).thenAnswer(call -> {
            Sense sense = call.getArgument(0);
            if (!saved.contains(sense)) saved.add(sense);
            return sense;
        });
    }

    @Test
    void createsAnUnapprovedSenseAndKeepsItVisibleAfterApproval() {
        var created = service.create(review.getId(), request(" Meaning ", null, null, null));
        assertThat(created.gloss()).isEqualTo("Meaning");
        assertThat(created.approved()).isFalse();
        assertThat(created.flags()).isEmpty();

        var approved = service.update(review.getId(), created.id(), request("Meaning", null, null, true));
        assertThat(approved.approved()).isTrue();
        assertThat(service.findAllForReview(review.getId())).singleElement()
                .satisfies(sense -> assertThat(sense.id()).isEqualTo(created.id()));
    }

    @Test
    void descriptionAndApprovalChangesPreserveFlagsAndExampleAssignments() {
        Sense sense = sense("Meaning", false, null);
        sense.setFlags(Map.of("Lietojums", List.of("Sarunvaloda")));
        Example example = example();
        example.reassignTo(sense);

        var edited = service.update(review.getId(), sense.getId(), request("Edited", null, null, true));

        assertThat(edited.flags()).containsEntry("Lietojums", List.of("Sarunvaloda"));
        assertThat(edited.exampleIds()).containsExactly(example.getId());
        assertThat(service.findAllForReview(review.getId())).hasSize(1);
        verifyNoInteractions(flags);
    }

    @Test
    void createsExistingReferenceCardsOnlyOnceAndProtectsTheirDictionaryContent() {
        TezaursSense existing = mock(TezaursSense.class);
        when(existing.getId()).thenReturn(300);
        when(existing.getGloss()).thenReturn("Dictionary definition");
        when(tezaursSenses.findOrderedByEntryId(42)).thenReturn(List.of(existing));

        var first = service.findAllForReview(review.getId());
        var second = service.findAllForReview(review.getId());

        assertThat(first).singleElement().satisfies(sense -> {
            assertThat(sense.existingSenseId()).isEqualTo(300);
            assertThat(sense.gloss()).isEqualTo("Dictionary definition");
        });
        assertThat(second.getFirst().id()).isEqualTo(first.getFirst().id());
        assertThat(saved).hasSize(1);
        assertThatThrownBy(() -> service.update(review.getId(), first.getFirst().id(), request("Changed", null, null, true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.delete(review.getId(), first.getFirst().id())).isInstanceOf(ConflictException.class);
        verify(tezaursSenses, never()).save(any());
    }

    @Test
    void regroupingAddsNewSensesAndMovesOnlyReturnedExamplesEvenFromApprovedSenses() {
        Sense approved = sense("Reviewed definition", true, null);
        Sense old = sense("Old unapproved meaning", false, null);
        Sense existing = sense("Dictionary meaning", false, 300);
        Example moved = example();
        Example ungrouped = example();
        Example existingMatch = example();
        moved.reassignTo(approved);
        ungrouped.reassignTo(old);
        when(examples.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId()))
                .thenReturn(List.of(moved, ungrouped, existingMatch));

        var result = service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("New AI meaning", null, List.of(moved.getId())),
                new SenseGroup("Model wording must not replace dictionary text", 300, List.of(existingMatch.getId()))));

        assertThat(result).hasSize(4);
        assertThat(approved.isApproved()).isTrue();
        assertThat(approved.getGloss()).isEqualTo("Reviewed definition");
        assertThat(approved.getExamples()).isEmpty();
        assertThat(moved.getSense().getGloss()).isEqualTo("New AI meaning");
        assertThat(moved.getSense().isApproved()).isFalse();
        assertThat(ungrouped.getSense()).isSameAs(old);
        assertThat(existingMatch.getSense()).isSameAs(existing);
        assertThat(existing.getGloss()).isEqualTo("Dictionary meaning");
        verify(senses, never()).delete(any());
        verify(senses, never()).deleteAll(any());
    }

    @Test
    void regroupingValidatesAllExamplesBeforeMovingAnyAndRejectsOtherEntryBranches() {
        Sense original = sense("Original", true, null);
        Example example = example();
        example.reassignTo(original);
        when(examples.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(example));

        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("First", null, List.of(example.getId())),
                new SenseGroup("Invalid", null, List.of(UUID.randomUUID())))))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isSameAs(original);
        assertThat(saved).containsExactly(original);

        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 99, List.of()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("First", null, List.of(example.getId(), example.getId())))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void enforcesTwoLevelsAndPreventsSelfParentingOrMovingParentsUnderOtherSenses() {
        Sense root = sense("Root", true, null);
        Sense child = sense("Child", true, null);
        child.setParentDraftSenseId(root.getId());
        Sense anotherRoot = sense("Other root", true, null);

        assertThatThrownBy(() -> service.create(review.getId(), request("Grandchild", child.getId(), null, false)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), root.getId(), request("Root", anotherRoot.getId(), null, true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), root.getId(), request("Root", root.getId(), null, true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), root.getId(), request("Root", null, null, false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void existingParentsMustBeRootsAndApprovedChildrenNeedAnApprovedParent() {
        when(tezaursSenses.existsByIdAndEntryIdAndParentSenseIdIsNull(7, 42)).thenReturn(true);
        assertThat(service.create(review.getId(), request("Child", null, 7, true)).parentTezaursSenseId()).isEqualTo(7);
        assertThatThrownBy(() -> service.create(review.getId(), request("Grandchild", null, 8, true)))
                .isInstanceOf(ConflictException.class);
        Sense unapproved = sense("Unapproved", false, null);
        assertThatThrownBy(() -> service.create(review.getId(), request("Child", unapproved.getId(), null, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletingASenseReleasesExamplesButDoesNotDeleteThem() {
        Sense sense = sense("Meaning", false, null);
        Example example = example();
        example.reassignTo(sense);
        service.delete(review.getId(), sense.getId());
        assertThat(example.getSense()).isNull();
        verify(senses).delete(sense);
        verify(examples, never()).delete(any());
    }

    @Test
    void newEntrySensesCanBeCreatedBeforeUsageConfirmationButApprovalCannot() {
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        var created = service.create(review.getId(), request("Meaning", null, null, null));
        assertThat(created.approved()).isFalse();
        assertThatThrownBy(() -> service.create(review.getId(), request("Meaning", null, null, true)))
                .isInstanceOf(ConflictException.class);
        review.setUsageDecision(UsageDecision.USED);
        assertThat(service.create(review.getId(), request("Meaning", null, null, true)).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void writesRequireInProgressAndReadsDoNotCreateReferencesInClosedReviews(ReviewStatus status) {
        review.setStatus(status);
        assertThatThrownBy(() -> service.create(review.getId(), request("Meaning", null, null, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), UUID.randomUUID(), request("Changed", null, null, true))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.delete(review.getId(), UUID.randomUUID())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of())).isInstanceOf(ConflictException.class);
        service.findAllForReview(review.getId());
        verify(senses, never()).save(any());
        verifyNoInteractions(tezaursSenses);
    }

    private Sense sense(String gloss, boolean approved, Integer existingId) {
        Sense sense = new Sense(UUID.randomUUID(), review.getId(), 42, gloss, existingId, saved.size() + 1);
        sense.setApproved(approved);
        saved.add(sense);
        return sense;
    }

    private Example example() {
        return new Example(review.getId(), new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", "Example", "https://example.com", null, null), "reviewer");
    }

    private static SaveSenseRequest request(String gloss, UUID parentId, Integer existingParentId, Boolean approved) {
        return new SaveSenseRequest(gloss, parentId, existingParentId, null, approved);
    }
}
