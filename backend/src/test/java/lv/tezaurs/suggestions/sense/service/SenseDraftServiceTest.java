package lv.tezaurs.suggestions.sense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.sense.dto.SaveSenseDraftsRequest;
import lv.tezaurs.suggestions.sense.dto.SenseDraftResponse;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import lv.tezaurs.suggestions.tezaurs.service.TezaursRestrictionService;
import lv.tezaurs.suggestions.suggestion.service.ReviewCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class SenseDraftServiceTest {
    private ReviewSenseRepository senseRepository;
    private ReviewCaseRepository caseRepository;
    private TezaursSenseRepository tezaursSenseRepository;
    private SenseDraftService service;

    @BeforeEach
    void setUp() {
        senseRepository = mock(ReviewSenseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        caseRepository = mock(
                ReviewCaseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        tezaursSenseRepository = mock(
                TezaursSenseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursFlagService flagService = mock(TezaursFlagService.class);
        TezaursRestrictionService restrictionService = mock(TezaursRestrictionService.class);
        service = new SenseDraftService(
                new ReviewCompletionService(
                        mock(lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository.class),
                        mock(lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository.class), senseRepository),
                senseRepository, caseRepository, tezaursSenseRepository,
                flagService, restrictionService);
        when(senseRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ReviewSense> saved = invocation.getArgument(0);
            if (!saved.isEmpty()) {
                ReviewSense first = saved.getFirst();
                when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(
                        first.getCaseId(), first.getTargetEntryId())).thenReturn(saved);
            }
            return saved;
        });
    }

    @Test
    void savesAFullSenseDraftAndAllowsCompletion() {
        ReviewCase review = new ReviewCase("vārds", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), review.getMatchedEntryId()))
                .thenReturn(List.of());

        var response = service.save(review.getId(), request(null, null));

        assertThat(response.senses()).singleElement()
                .extracting(SenseDraftResponse::gloss)
                .isEqualTo("Jauna nozīme");
        assertThat(response.review().canComplete()).isTrue();
        review.changeStatus(ReviewStatus.COMPLETED, "reviewer");
        assertThatThrownBy(() -> service.save(review.getId(), request(null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsASenseThatIsItsOwnParent() {
        ReviewCase review = new ReviewCase("vārds", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        UUID senseId = UUID.randomUUID();
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.save(review.getId(), request(senseId, senseId)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void returnsExistingDraftSenses() {
        ReviewCase review = reviewWithMissingMeaning();
        ReviewSense sense = new ReviewSense(review.getId(), review.getMatchedEntryId());
        sense.update("Nozīme", null, null, 1, Map.of(), null);
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), review.getMatchedEntryId()))
                .thenReturn(List.of(sense));

        assertThat(service.findAll(review.getId())).singleElement()
                .extracting(SenseDraftResponse::gloss)
                .isEqualTo("Nozīme");
    }

    @Test
    void rejectsMissingReviewsAndReviewsWithoutAMissingSense() {
        UUID missingId = UUID.randomUUID();
        when(caseRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAll(missingId))
                .isInstanceOf(NotFoundException.class);

        ReviewCase unchecked = new ReviewCase("vārds", "reviewer");
        unchecked.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        unchecked.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        when(caseRepository.findById(unchecked.getId())).thenReturn(Optional.of(unchecked));

        assertThatThrownBy(() -> service.save(unchecked.getId(), request(null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validatesDraftAndTezaursParentReferences() {
        ReviewCase review = reviewWithMissingMeaning();
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursSenseRepository.existsByIdAndEntryId(7, 42)).thenReturn(true);
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), review.getMatchedEntryId()))
                .thenReturn(List.of());

        var validParent = item(null, null, 7);
        assertThat(service.save(review.getId(), new SaveSenseDraftsRequest(List.of(validParent)))
                .senses()).hasSize(1);

        UUID senseId = UUID.randomUUID();
        var bothParents = item(senseId, UUID.randomUUID(), 7);
        assertThatThrownBy(() -> service.save(review.getId(),
                new SaveSenseDraftsRequest(List.of(bothParents))))
                .isInstanceOf(ConflictException.class);

        var foreignParent = item(null, null, 8);
        assertThatThrownBy(() -> service.save(review.getId(),
                new SaveSenseDraftsRequest(List.of(foreignParent))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsDuplicateMissingAndCyclicDraftParents() {
        ReviewCase review = reviewWithMissingMeaning();
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        var first = item(firstId, secondId, null);
        var second = item(secondId, firstId, null);
        assertThatThrownBy(() -> service.save(review.getId(),
                new SaveSenseDraftsRequest(List.of(first, second))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sense hierarchy cannot contain a cycle");

        var duplicate = item(firstId, null, null);
        assertThatThrownBy(() -> service.save(review.getId(),
                new SaveSenseDraftsRequest(List.of(duplicate, duplicate))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The same sense cannot be submitted twice");

        var missingParent = item(firstId, UUID.randomUUID(), null);
        assertThatThrownBy(() -> service.save(review.getId(),
                new SaveSenseDraftsRequest(List.of(missingParent))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A parent draft sense must be included in the same draft");
    }

    @Test
    void rejectsAnExistingSenseFromAnotherReview() {
        ReviewCase review = reviewWithMissingMeaning();
        UUID foreignSenseId = UUID.randomUUID();
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), review.getMatchedEntryId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.save(review.getId(), request(foreignSenseId, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A sense does not belong to this review");
    }

    private static SaveSenseDraftsRequest request(UUID id, UUID parentId) {
        return new SaveSenseDraftsRequest(List.of(item(id, parentId, null)));
    }

    @Test
    void savingANewSenseOnlyWritesToTheReviewSchema() {
        ReviewCase review = reviewWithMissingMeaning();
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var response = service.save(review.getId(), request(null, null));

        assertThat(response.senses()).singleElement()
                .extracting(SenseDraftResponse::gloss).isEqualTo("Jauna nozīme");
        var saved = senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42);
        assertThat(saved).singleElement().satisfies(sense -> {
            assertThat(sense.getCaseId()).isEqualTo(review.getId());
            assertThat(sense.getTargetEntryId()).isEqualTo(42);
        });
        org.mockito.Mockito.verifyNoInteractions(tezaursSenseRepository);
    }

    @Test
    void rejectsBlankDefinitionsWithoutSavingThem() {
        ReviewCase review = reviewWithMissingMeaning();
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        var blank = new SaveSenseDraftsRequest.SenseDraftRequest(null, "  ", null, null, Map.of(), null);
        assertThatThrownBy(() -> service.save(review.getId(), new SaveSenseDraftsRequest(List.of(blank))))
                .isInstanceOf(ConflictException.class).hasMessage("A new sense must have a definition");
        verify(senseRepository, never()).saveAll(any());
    }

    @Test
    void updatingADraftDeletesOnlyOmittedDraftsForTheSameTarget() {
        ReviewCase review = reviewWithMissingMeaning();
        ReviewSense retained = new ReviewSense(review.getId(), 42);
        ReviewSense omitted = new ReviewSense(review.getId(), 42);
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(retained, omitted));

        var result = service.save(review.getId(), request(retained.getId(), null));

        assertThat(result.senses()).singleElement().extracting(SenseDraftResponse::id).isEqualTo(retained.getId());
        verify(senseRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(
                deleted -> {
                    var rows = new java.util.ArrayList<ReviewSense>();
                    deleted.forEach(rows::add);
                    return rows.equals(List.of(omitted));
                }));
        org.mockito.Mockito.verifyNoInteractions(tezaursSenseRepository);
    }

    private static SaveSenseDraftsRequest.SenseDraftRequest item(
            UUID id, UUID parentDraftSenseId, Integer parentTezaursSenseId) {
        return new SaveSenseDraftsRequest.SenseDraftRequest(
                id, " Jauna nozīme ", parentDraftSenseId,
                parentTezaursSenseId, Map.of(), null);
    }

    private static ReviewCase reviewWithMissingMeaning() {
        ReviewCase review = new ReviewCase("vārds", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        return review;
    }

    @Test
    void loadsOnlySensesForTheSelectedEntry() {
        ReviewCase review = reviewWithMissingMeaning();
        ReviewSense newEntrySense = new ReviewSense(review.getId(), null);
        newEntrySense.update("new entry meaning", null, null, 1, Map.of(), null);
        ReviewSense existingEntrySense = new ReviewSense(review.getId(), 42);
        existingEntrySense.update("existingEntrySense meaning", null, null, 1, Map.of(), null);
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(existingEntrySense));
        when(senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), null))
                .thenReturn(List.of(newEntrySense));

        assertThat(service.findAll(review.getId())).singleElement().extracting(SenseDraftResponse::id).isEqualTo(existingEntrySense.getId());
        assertThatThrownBy(() -> service.save(review.getId(), request(newEntrySense.getId(), null)))
                .isInstanceOf(ConflictException.class);

        review.changeType(CaseType.ENTRY, null, false, "reviewer");
        review.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, "reviewer");
        assertThat(service.findAll(review.getId())).singleElement().extracting(SenseDraftResponse::id).isEqualTo(newEntrySense.getId());
    }
}
