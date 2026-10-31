package lv.tezaurs.suggestions.entry.service;

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
import lv.tezaurs.suggestions.entry.dto.SaveLexemeDraftRequest;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import lv.tezaurs.suggestions.tezaurs.service.TezaursRestrictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class LexemeDraftServiceTest {
    private ReviewEntryRepository entryRepository;
    private ReviewLexemeRepository lexemeRepository;
    private ReviewCaseRepository caseRepository;
    private LexemeDraftService service;

    @BeforeEach
    void setUp() {
        entryRepository = mock(ReviewEntryRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        lexemeRepository = mock(ReviewLexemeRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        caseRepository = mock(ReviewCaseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursLexemeRoleRepository roleRepository = mock(
                TezaursLexemeRoleRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursParadigmRepository paradigmRepository = mock(
                TezaursParadigmRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursFlagService flagService = mock(TezaursFlagService.class);
        TezaursRestrictionService restrictionService = mock(TezaursRestrictionService.class);
        service = new LexemeDraftService(entryRepository, lexemeRepository, caseRepository,
                roleRepository, paradigmRepository, flagService, restrictionService);

        when(roleRepository.existsById(1)).thenReturn(true);
        when(paradigmRepository.existsById(10)).thenReturn(true);
        when(lexemeRepository.save(any(ReviewLexeme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsLexemesSeparatelyFromTheEntry() {
        ReviewCase review = reviewWithoutTezaursEntry();
        ReviewEntry entry = entry(review.getId());
        ReviewLexeme primary = lexeme(entry.getId(), "jauns vārds", true, 1);
        prepare(review, entry, List.of(primary));

        var response = service.create(review.getId(), request("jaunvārds", false));

        assertThat(response.lexeme().lemma()).isEqualTo("jaunvārds");
        assertThat(response.lexeme().primary()).isFalse();
        assertThat(response.review().completionBlockReason()).isNotNull();
    }

    @Test
    void selectingAPrimaryLexemeDemotesThePreviousOne() {
        ReviewCase review = reviewWithoutTezaursEntry();
        ReviewEntry entry = entry(review.getId());
        ReviewLexeme oldPrimary = lexeme(entry.getId(), "jauns vārds", true, 1);
        ReviewLexeme newPrimary = lexeme(entry.getId(), "jaunvārds", false, 2);
        prepare(review, entry, List.of(oldPrimary, newPrimary));
        when(lexemeRepository.findByIdAndEntryId(newPrimary.getId(), entry.getId()))
                .thenReturn(Optional.of(newPrimary));

        service.update(review.getId(), newPrimary.getId(), request("jaunvārds", true));

        assertThat(oldPrimary.isPrimary()).isFalse();
        assertThat(newPrimary.isPrimary()).isTrue();
    }

    @Test
    void doesNotDeleteThePrimaryLexeme() {
        ReviewCase review = reviewWithoutTezaursEntry();
        ReviewEntry entry = entry(review.getId());
        ReviewLexeme primary = lexeme(entry.getId(), "jauns vārds", true, 1);
        prepare(review, entry, List.of(primary));
        when(lexemeRepository.findByIdAndEntryId(primary.getId(), entry.getId()))
                .thenReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.delete(review.getId(), primary.getId()))
                .isInstanceOf(ConflictException.class);
    }

    private void prepare(
            ReviewCase review, ReviewEntry entry, List<ReviewLexeme> lexemes) {
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByCaseId(review.getId())).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()))
                .thenReturn(lexemes);
    }

    private static ReviewCase reviewWithoutTezaursEntry() {
        ReviewCase review = new ReviewCase("jauns vārds");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        return review;
    }

    private static ReviewEntry entry(UUID caseId) {
        ReviewEntry entry = new ReviewEntry(caseId);
        entry.update("jauns vārds", 1, 1,
                null, null, null, null, null, Map.of());
        return entry;
    }

    private static ReviewLexeme lexeme(UUID entryId, String lemma, boolean primary, int orderNo) {
        ReviewLexeme lexeme = new ReviewLexeme(entryId, orderNo);
        lexeme.update(lemma, primary, 1, 10, null, null, null,
                List.of(), Map.of(), null, orderNo);
        return lexeme;
    }

    private static SaveLexemeDraftRequest request(String lemma, boolean primary) {
        return new SaveLexemeDraftRequest(lemma, primary, 1, 10,
                null, null, null, List.of(), Map.of(), null);
    }
}
