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
import lv.tezaurs.tip.sense.dto.GeneratedDefinition;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;
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
    void setUpSenseRepositories() {
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42)).thenReturn(saved);
        when(senses.save(any(Sense.class))).thenAnswer(call -> {
            Sense sense = call.getArgument(0);
            if (!saved.contains(sense)) saved.add(sense);
            return sense;
        });
    }

    @Test
    void generatedDefinitionsUpdateOnlyTextAndPreserveApprovalFlagsHierarchyAndExamples() {
        Sense parent = addSavedSense("Original", true, null);
        parent.setFlags(Map.of("Note", "Human flag"));
        Sense child = addSavedSense("Child", false, null);
        child.setParentSenseId(parent.getId());
        Example example = createExternalExample();
        example.reassignTo(child);
        example.setApproved(true);

        service.saveGeneratedDefinitions(review.getId(), 42, List.of(
                new GeneratedDefinition(parent.getId(), "New parent definition"),
                new GeneratedDefinition(child.getId(), "New child definition")));

        assertThat(parent.getGloss()).isEqualTo("New parent definition");
        assertThat(parent.isApproved()).isTrue();
        assertThat(parent.getFlags()).containsEntry("Note", "Human flag");
        assertThat(child.getParentSenseId()).isEqualTo(parent.getId());
        assertThat(child.getGloss()).isEqualTo("New child definition");
        assertThat(example.getSense()).isSameAs(child);
        assertThat(example.isApproved()).isTrue();
    }

    @Test
    void validatesEveryGeneratedTargetBeforeChangingAnyDefinitions() {
        Sense local = addSavedSense("Original", false, null);
        Sense existing = addSavedSense("Dictionary", false, 300);

        assertThatThrownBy(() -> service.saveGeneratedDefinitions(review.getId(), 42, List.of(
                new GeneratedDefinition(local.getId(), "New"), new GeneratedDefinition(existing.getId(), "Forbidden"))))
                .isInstanceOf(ConflictException.class);
        assertThat(local.getGloss()).isEqualTo("Original");
        assertThat(existing.getGloss()).isEqualTo("Dictionary");
        assertThatThrownBy(() -> service.saveGeneratedDefinitions(review.getId(), 99, List.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsDistinctSubmittedDefinitionsOnceWithoutOverwritingEdits() {
        new ReviewSuggestion(new IncubatorSuggestion(2, "vārds", "Otra nozīme"), review);
        new ReviewSuggestion(new IncubatorSuggestion(1, "vārds", " Pirmā nozīme "), review);
        new ReviewSuggestion(new IncubatorSuggestion(3, "vārds", "Pirmā nozīme"), review);
        new ReviewSuggestion(new IncubatorSuggestion(4, "vārds", " "), review);

        service.initializeForSelection(review.getId(), false);

        assertThat(saved).extracting(Sense::getGloss).containsExactly("Pirmā nozīme", "Otra nozīme");
        assertThat(saved).allSatisfy(sense -> {
            assertThat(sense.isApproved()).isFalse();
            assertThat(sense.getExistingSenseId()).isNull();
            assertThat(sense.getTargetEntryId()).isEqualTo(42);
        });
        saved.getFirst().setGloss("Redaktora labojums");
        service.initializeForSelection(review.getId(), false);
        assertThat(saved).extracting(Sense::getGloss).containsExactly("Redaktora labojums", "Otra nozīme");
    }

    @Test
    void doesNotInventAnInitialDefinitionWhenNoneWasSubmitted() {
        new ReviewSuggestion(new IncubatorSuggestion(1, "vārds", " "), review);
        service.initializeForSelection(review.getId(), false);
        assertThat(saved).isEmpty();
    }

    @Test
    void createsAnUnapprovedSenseAndKeepsItVisibleAfterApproval() {
        var created = service.create(review.getId(), createSenseRequest(" Meaning ", null, null));
        assertThat(created.gloss()).isEqualTo("Meaning");
        assertThat(created.approved()).isFalse();
        assertThat(created.flags()).isEmpty();

        var approved = service.update(review.getId(), created.id(), createSenseRequest("Meaning", null, true));
        assertThat(approved.approved()).isTrue();
        assertThat(service.findAllForReview(review.getId())).singleElement()
                .satisfies(sense -> assertThat(sense.id()).isEqualTo(created.id()));
    }

    @Test
    void descriptionAndApprovalChangesPreserveFlagsAndExampleAssignments() {
        Sense sense = addSavedSense("Meaning", false, null);
        sense.setFlags(Map.of("Lietojums", List.of("Sarunvaloda")));
        Example example = createExternalExample();
        example.reassignTo(sense);

        var edited = service.update(review.getId(), sense.getId(), createSenseRequest("Edited", null, true));

        assertThat(edited.flags()).containsEntry("Lietojums", List.of("Sarunvaloda"));
        assertThat(edited.exampleIds()).containsExactly(example.getId());
        assertThat(service.findAllForReview(review.getId())).hasSize(1);
        verifyNoInteractions(flags);
    }

    @Test
    void readingSensesDoesNotInitializeReferencesOrLockTheReview() {
        assertThat(service.findAllForReview(review.getId())).isEmpty();

        verify(reviews, never()).findLockedById(any());
        verify(senses, never()).save(any());
        verifyNoInteractions(tezaursSenses);
    }

    @Test
    void createsExistingReferenceCardsOnlyOnceAndProtectsTheirDictionaryContent() {
        TezaursSense existing = mock(TezaursSense.class);
        when(existing.getId()).thenReturn(300);
        when(existing.getParentSenseId()).thenReturn(null);
        when(existing.getGloss()).thenReturn("Dictionary definition");
        when(tezaursSenses.findOrderedByEntryId(42)).thenReturn(List.of(existing));

        service.initializeForSelection(review.getId(), true);
        var first = service.findAllForReview(review.getId());
        service.initializeForSelection(review.getId(), true);
        var second = service.findAllForReview(review.getId());

        assertThat(first).singleElement().satisfies(sense -> {
            assertThat(sense.existingSenseId()).isEqualTo(300);
            assertThat(sense.gloss()).isEqualTo("Dictionary definition");
        });
        assertThat(second.getFirst().id()).isEqualTo(first.getFirst().id());
        assertThat(saved).hasSize(1);
        assertThatThrownBy(() -> service.update(review.getId(), first.getFirst().id(), createSenseRequest("Changed", null, true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.delete(review.getId(), first.getFirst().id())).isInstanceOf(ConflictException.class);
        verify(tezaursSenses, never()).save(any());
    }

    @Test
    void regroupingAddsNewSensesAndMovesOnlyReturnedExamplesEvenFromApprovedSenses() {
        Sense approved = addSavedSense("Reviewed definition", true, null);
        Sense old = addSavedSense("Old unapproved meaning", false, null);
        Sense existing = addSavedSense("Dictionary meaning", false, 300);
        Example moved = createExternalExample();
        Example ungrouped = createExternalExample();
        Example existingMatch = createExternalExample();
        moved.reassignTo(approved);
        ungrouped.reassignTo(old);
        when(examples.findForReview(eq(review.getId()), any()))
                .thenReturn(List.of(moved, ungrouped, existingMatch));

        var result = service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("New AI meaning", null, List.of(moved.getId()), List.of()),
                new SenseGroup("Model wording must not replace dictionary text", existing.getId(), List.of(existingMatch.getId()), List.of())));

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
    void regroupingReusesLocalSenseIdsWithoutDuplicatingOrOverwritingHumanWork() {
        Sense parent = addSavedSense("Human wording", true, null);
        parent.setFlags(Map.of("Note", "Human flag"));
        Sense child = addSavedSense("Human subsense", false, null);
        child.setParentSenseId(parent.getId());
        Example example = createExternalExample();
        when(examples.findForReview(eq(review.getId()), any())).thenReturn(List.of(example));

        var result = service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("Model wording", parent.getId(), List.of(), List.of(
                        new SenseGroup.Subsense("Model child wording", child.getId(), List.of(example.getId()))))));

        assertThat(result).hasSize(2);
        assertThat(parent.getGloss()).isEqualTo("Human wording");
        assertThat(parent.isApproved()).isTrue();
        assertThat(parent.getFlags()).containsEntry("Note", "Human flag");
        assertThat(child.getGloss()).isEqualTo("Human subsense");
        assertThat(child.getParentSenseId()).isEqualTo(parent.getId());
        assertThat(example.getSense()).isSameAs(child);
        verify(senses, never()).save(any());
    }

    @Test
    void savesUnapprovedSubsensesUnderNewParentsWithoutRequiringParentExamples() {
        Example example = createExternalExample();
        when(examples.findForReview(eq(review.getId()), any())).thenReturn(List.of(example));

        var result = service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("Parent", null, List.of(), List.of(
                        new SenseGroup.Subsense("Child", null, List.of(example.getId()))))));

        assertThat(result).hasSize(2);
        var parent = result.getFirst();
        var child = result.getLast();
        assertThat(parent.exampleIds()).isEmpty();
        assertThat(child.parentSenseId()).isEqualTo(parent.id());
        assertThat(child.exampleIds()).containsExactly(example.getId());
        assertThat(result).allSatisfy(sense -> assertThat(sense.approved()).isFalse());
    }

    @Test
    void savesNewSubsensesUnderExistingDictionaryRoots() {
        Sense existing = addSavedSense("Dictionary root", false, 300);
        when(examples.findForReview(eq(review.getId()), any())).thenReturn(List.of());

        var result = service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("Model title", existing.getId(), List.of(), List.of(
                        new SenseGroup.Subsense("New child", null, List.of())))));

        assertThat(result).hasSize(2);
        assertThat(existing.getGloss()).isEqualTo("Dictionary root");
        assertThat(result.getLast().parentSenseId()).isEqualTo(existing.getId());
    }

    @Test
    void includesExistingDictionaryParentsInTheResearchHierarchy() {
        TezaursSense root = mock(TezaursSense.class);
        when(root.getId()).thenReturn(300);
        when(root.getParentSenseId()).thenReturn(null);
        when(root.getGloss()).thenReturn("Parent");
        TezaursSense child = mock(TezaursSense.class);
        when(child.getId()).thenReturn(301);
        when(child.getParentSenseId()).thenReturn(300);
        when(child.getGloss()).thenReturn("Child");
        when(tezaursSenses.findOrderedByEntryId(42)).thenReturn(List.of(root, child));

        service.initializeForSelection(review.getId(), true);
        var result = service.findAllForReview(review.getId());

        assertThat(result.getLast().parentSenseId()).isEqualTo(result.getFirst().id());
        assertThat(result.getFirst().parentSenseId()).isNull();
    }

    @Test
    void regroupingCannotMoveAnExampleApprovedWhileTheModelWasRunning() {
        Sense sense = addSavedSense("Human meaning", true, null);
        Example example = createExternalExample();
        example.reassignTo(sense);
        example.setApproved(true);
        when(examples.findForReview(eq(review.getId()), any())).thenReturn(List.of(example));

        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("AI meaning", null, List.of(example.getId()), List.of()))))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isSameAs(sense);
        assertThat(example.isApproved()).isTrue();
    }

    @Test
    void regroupingValidatesAllExamplesBeforeMovingAnyAndRejectsOtherEntryBranches() {
        Sense original = addSavedSense("Original", true, null);
        Example example = createExternalExample();
        example.reassignTo(original);
        when(examples.findForReview(eq(review.getId()), any())).thenReturn(List.of(example));

        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("First", null, List.of(example.getId()), List.of()),
                new SenseGroup("Invalid", null, List.of(UUID.randomUUID()), List.of()))))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isSameAs(original);
        assertThat(saved).containsExactly(original);

        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 99, List.of()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of(
                new SenseGroup("First", null, List.of(example.getId(), example.getId()), List.of()))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void enforcesTwoLevelsAndPreventsSelfParentingOrMovingParentsUnderOtherSenses() {
        Sense root = addSavedSense("Root", true, null);
        Sense child = addSavedSense("Child", true, null);
        child.setParentSenseId(root.getId());
        Sense anotherRoot = addSavedSense("Other root", true, null);

        assertThatThrownBy(() -> service.create(review.getId(), createSenseRequest("Grandchild", child.getId(), false)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), root.getId(), createSenseRequest("Root", anotherRoot.getId(), true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), root.getId(), createSenseRequest("Root", root.getId(), true)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), root.getId(), createSenseRequest("Root", null, false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void existingParentsMustBeRootsAndApprovedChildrenNeedAnApprovedParent() {
        Sense dictionaryRoot = addSavedSense("Dictionary root", false, 7);
        Sense dictionaryChild = addSavedSense("Dictionary child", false, 8);
        dictionaryChild.setParentSenseId(dictionaryRoot.getId());
        assertThat(service.create(review.getId(), createSenseRequest("Child", dictionaryRoot.getId(), true)).parentSenseId()).isEqualTo(dictionaryRoot.getId());
        assertThatThrownBy(() -> service.create(review.getId(), createSenseRequest("Grandchild", dictionaryChild.getId(), true)))
                .isInstanceOf(ConflictException.class);
        Sense unapproved = addSavedSense("Unapproved", false, null);
        assertThatThrownBy(() -> service.create(review.getId(), createSenseRequest("Child", unapproved.getId(), true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void mergingKeepsTargetDataAndExamplesAndMovesSourceExamplesAndSubsenses() {
        Sense source = addSavedSense("Discarded wording", true, null);
        Sense target = addSavedSense("Target wording", true, null);
        target.setFlags(Map.of("Piezīmes", "Target flags"));
        Sense child = addSavedSense("Child wording", true, null);
        child.setParentSenseId(source.getId());
        Example moved = createExternalExample();
        moved.reassignTo(source);
        moved.setApproved(true);
        Example retained = createExternalExample();
        retained.reassignTo(target);
        retained.setApproved(true);

        var merged = service.merge(review.getId(), source.getId(), target.getId());

        assertThat(merged.id()).isEqualTo(target.getId());
        assertThat(merged.gloss()).isEqualTo("Target wording");
        assertThat(merged.flags()).containsEntry("Piezīmes", "Target flags");
        assertThat(merged.approved()).isTrue();
        assertThat(merged.exampleIds()).containsExactlyInAnyOrder(moved.getId(), retained.getId());
        assertThat(moved.isApproved()).isFalse();
        assertThat(retained.isApproved()).isTrue();
        assertThat(child.getParentSenseId()).isEqualTo(target.getId());
        assertThat(child.isApproved()).isTrue();
        verify(senses).delete(source);
    }

    @Test
    void mergingProtectsExistingSensesReviewBoundariesAndTwoLevelStructure() {
        Sense source = addSavedSense("Source", false, null);
        Sense child = addSavedSense("Child", true, null);
        child.setParentSenseId(source.getId());
        Sense target = addSavedSense("Target", false, null);
        Sense existing = addSavedSense("Dictionary wording", false, 300);

        assertThatThrownBy(() -> service.merge(review.getId(), source.getId(), source.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.merge(review.getId(), source.getId(), UUID.randomUUID())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.merge(review.getId(), existing.getId(), target.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.merge(review.getId(), source.getId(), child.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.merge(review.getId(), source.getId(), target.getId())).isInstanceOf(ConflictException.class);
        assertThat(child.getParentSenseId()).isEqualTo(source.getId());
        verify(senses, never()).delete(any());
    }

    @Test
    void mergingIntoAnExistingRootPreservesDictionaryTextAndReparentsDraftChildren() {
        Sense source = addSavedSense("Source", false, null);
        Sense child = addSavedSense("Child", false, null);
        child.setParentSenseId(source.getId());
        Sense target = addSavedSense("Dictionary wording", false, 300);

        service.merge(review.getId(), source.getId(), target.getId());

        assertThat(child.getParentSenseId()).isEqualTo(target.getId());
        assertThat(target.getGloss()).isEqualTo("Dictionary wording");
        verify(tezaursSenses, never()).save(any());
    }

    @Test
    void deletingASenseReleasesExamplesButDoesNotDeleteThem() {
        Sense sense = addSavedSense("Meaning", false, null);
        Example example = createExternalExample();
        example.reassignTo(sense);
        example.setApproved(true);
        service.delete(review.getId(), sense.getId());
        assertThat(example.getSense()).isNull();
        assertThat(example.isApproved()).isFalse();
        verify(senses).delete(sense);
        verify(examples, never()).delete(any());
    }

    @Test
    void newEntrySensesCanBeCreatedBeforeUsageConfirmationButApprovalCannot() {
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        var created = service.create(review.getId(), createSenseRequest("Meaning", null, null));
        assertThat(created.approved()).isFalse();
        assertThatThrownBy(() -> service.create(review.getId(), createSenseRequest("Meaning", null, true)))
                .isInstanceOf(ConflictException.class);
        review.setUsageDecision(UsageDecision.USED);
        assertThat(service.create(review.getId(), createSenseRequest("Meaning", null, true)).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void writesRequireInProgressAndReadsDoNotCreateReferencesInClosedReviews(ReviewStatus status) {
        review.setStatus(status);
        assertThatThrownBy(() -> service.create(review.getId(), createSenseRequest("Meaning", null, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(review.getId(), UUID.randomUUID(), createSenseRequest("Changed", null, true))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.delete(review.getId(), UUID.randomUUID())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.merge(review.getId(), UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addGroupedSenses(review.getId(), 42, List.of())).isInstanceOf(ConflictException.class);
        service.findAllForReview(review.getId());
        verify(senses, never()).save(any());
        verifyNoInteractions(tezaursSenses);
    }

    private Sense addSavedSense(String gloss, boolean approved, Integer existingId) {
        Sense sense = new Sense(UUID.randomUUID(), review.getId(), 42, gloss, existingId, saved.size() + 1);
        sense.setApproved(approved);
        saved.add(sense);
        return sense;
    }

    private Example createExternalExample() {
        return new Example(review.getId(), new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", "Example", "https://example.com", null, null, null, null), "reviewer");
    }

    private SaveSenseRequest createSenseRequest(String gloss, UUID parentSenseId, Boolean approved) {
        return new SaveSenseRequest(gloss, parentSenseId, null, approved);
    }
}
