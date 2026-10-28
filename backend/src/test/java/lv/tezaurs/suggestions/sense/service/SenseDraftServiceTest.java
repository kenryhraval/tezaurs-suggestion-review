package lv.tezaurs.suggestions.sense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.sense.dto.SaveSenseDraftsRequest;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class SenseDraftServiceTest {
    private ReviewSenseRepository senseRepository;
    private ReviewSuggestionRepository suggestionRepository;
    private TezaursSenseRepository tezaursSenseRepository;
    private SenseDraftService service;

    @BeforeEach
    void setUp() {
        senseRepository = mock(ReviewSenseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        suggestionRepository = mock(
                ReviewSuggestionRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        tezaursSenseRepository = mock(
                TezaursSenseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursFlagService flagService = mock(TezaursFlagService.class);
        service = new SenseDraftService(
                senseRepository, suggestionRepository, tezaursSenseRepository, flagService);
        when(senseRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesAFullSenseDraftAndAllowsCompletion() {
        ReviewSuggestion review = new ReviewSuggestion("vārds");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        when(suggestionRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllBySuggestionIdOrderByOrderNoAscIdAsc(review.getId()))
                .thenReturn(List.of());

        var response = service.save(review.getId(), request(null, null));

        assertThat(response.senses()).singleElement()
                .extracting(sense -> sense.gloss())
                .isEqualTo("Jauna nozīme");
        assertThat(response.review().canComplete()).isTrue();
    }

    @Test
    void rejectsASenseThatIsItsOwnParent() {
        ReviewSuggestion review = new ReviewSuggestion("vārds");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        UUID senseId = UUID.randomUUID();
        when(suggestionRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.save(review.getId(), request(senseId, senseId)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void returnsExistingDraftSenses() {
        ReviewSuggestion review = reviewWithMissingMeaning();
        ReviewSense sense = new ReviewSense(review.getId());
        sense.update("Nozīme", null, null, 1, Map.of(), null);
        when(suggestionRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllBySuggestionIdOrderByOrderNoAscIdAsc(review.getId()))
                .thenReturn(List.of(sense));

        assertThat(service.findAll(review.getId())).singleElement()
                .extracting(item -> item.gloss())
                .isEqualTo("Nozīme");
    }

    @Test
    void rejectsMissingReviewsAndReviewsWithoutAMissingSense() {
        UUID missingId = UUID.randomUUID();
        when(suggestionRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAll(missingId))
                .isInstanceOf(NotFoundException.class);

        ReviewSuggestion unchecked = new ReviewSuggestion("vārds");
        unchecked.changeStatus(SuggestionStatus.IN_PROGRESS);
        when(suggestionRepository.findById(unchecked.getId())).thenReturn(Optional.of(unchecked));

        assertThatThrownBy(() -> service.save(unchecked.getId(), request(null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validatesDraftAndTezaursParentReferences() {
        ReviewSuggestion review = reviewWithMissingMeaning();
        when(suggestionRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(tezaursSenseRepository.existsByIdAndEntryId(7, 42)).thenReturn(true);
        when(senseRepository.findAllBySuggestionIdOrderByOrderNoAscIdAsc(review.getId()))
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
        ReviewSuggestion review = reviewWithMissingMeaning();
        when(suggestionRepository.findById(review.getId())).thenReturn(Optional.of(review));

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
        ReviewSuggestion review = reviewWithMissingMeaning();
        UUID foreignSenseId = UUID.randomUUID();
        when(suggestionRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(senseRepository.findAllBySuggestionIdOrderByOrderNoAscIdAsc(review.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.save(review.getId(), request(foreignSenseId, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A sense does not belong to this review");
    }

    private static SaveSenseDraftsRequest request(UUID id, UUID parentId) {
        return new SaveSenseDraftsRequest(List.of(item(id, parentId, null)));
    }

    private static SaveSenseDraftsRequest.SenseDraftRequest item(
            UUID id, UUID parentDraftSenseId, Integer parentTezaursSenseId) {
        return new SaveSenseDraftsRequest.SenseDraftRequest(
                id, " Jauna nozīme ", parentDraftSenseId,
                parentTezaursSenseId, Map.of(), null);
    }

    private static ReviewSuggestion reviewWithMissingMeaning() {
        ReviewSuggestion review = new ReviewSuggestion("vārds");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        return review;
    }
}
