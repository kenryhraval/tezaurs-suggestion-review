package lv.tezaurs.suggestions.entry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryTypeRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.ArgumentCaptor;

class EntryDraftServiceTest {
    private ReviewEntryRepository entryRepository;
    private ReviewLexemeRepository lexemeRepository;
    private ReviewCaseRepository caseRepository;
    private TezaursEntryRepository tezaursEntryRepository;
    private TezaursEntryTypeRepository entryTypeRepository;
    private LexemeDraftService lexemeDraftService;
    private EntryDraftService service;

    @BeforeEach
    void setUp() {
        entryRepository = mock(ReviewEntryRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        lexemeRepository = mock(ReviewLexemeRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        caseRepository = mock(ReviewCaseRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        tezaursEntryRepository = mock(TezaursEntryRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        entryTypeRepository = mock(TezaursEntryTypeRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursFlagService flagService = mock(TezaursFlagService.class);
        lexemeDraftService = mock(LexemeDraftService.class);
        service = new EntryDraftService(entryRepository, lexemeRepository, caseRepository,
                tezaursEntryRepository, entryTypeRepository, flagService, lexemeDraftService);

        when(entryRepository.save(any(ReviewEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAnEntryWithItsFirstPrimaryLexeme() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByCaseId(review.getId())).thenReturn(Optional.empty());
        when(entryTypeRepository.existsById(1)).thenReturn(true);

        TezaursEntry existingEntry = mock(TezaursEntry.class);
        when(existingEntry.getHomonymNo()).thenReturn(2);
        when(tezaursEntryRepository.findFirstByHeadingOrderByHomonymNoDesc("jauns vārds"))
                .thenReturn(Optional.of(existingEntry));
        ReviewEntry otherDraft = mock(ReviewEntry.class);
        when(otherDraft.getHomonymNo()).thenReturn(4);
        when(entryRepository.findFirstByHeadingAndIdNotOrderByHomonymNoDesc(
                org.mockito.ArgumentMatchers.eq("jauns vārds"), any(UUID.class)))
                .thenReturn(Optional.of(otherDraft));

        ReviewLexeme primaryLexeme = primaryLexeme("jauns vārds");
        when(lexemeDraftService.createInitial(any(ReviewEntry.class), any(SaveLexemeDraftRequest.class)))
                .thenReturn(primaryLexeme);

        var response = service.create(review.getId());

        assertThat(response.draft().heading()).isEqualTo("jauns vārds");
        assertThat(response.draft().humanKey()).isEqualTo("jauns vārds:5");
        assertThat(response.draft().lexemes()).singleElement()
                .extracting(lexeme -> lexeme.lemma())
                .isEqualTo("jauns vārds");
        var request = ArgumentCaptor.forClass(SaveLexemeDraftRequest.class);
        verify(lexemeDraftService).createInitial(any(ReviewEntry.class), request.capture());
        assertThat(request.getValue().lemma()).isEqualTo("jauns vārds");
        assertThat(request.getValue().primary()).isTrue();
        assertThat(request.getValue().paradigmId()).isNull();
    }

    @Test
    void rejectsEntryDraftsForExistingEntries() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.create(review.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reusesAnExistingDraftWithoutOverwritingItsEdits() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        ReviewEntry entry = new ReviewEntry(review.getId());
        entry.update("edited heading", 2, 1, null, null, null, null, "saved note", Map.of());
        when(caseRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByCaseId(review.getId())).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()))
                .thenReturn(List.of(primaryLexeme("saved lemma")));

        var response = service.create(review.getId());

        assertThat(response.draft().heading()).isEqualTo("edited heading");
        assertThat(response.draft().notes()).isEqualTo("saved note");
        verify(entryRepository, never()).save(any());
        verify(lexemeDraftService, never()).createInitial(any(), any());
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("jauns vārds");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        return review;
    }

    private static ReviewLexeme primaryLexeme(String lemma) {
        ReviewLexeme lexeme = new ReviewLexeme(UUID.randomUUID(), 1);
        lexeme.update(lemma, true, 1, 10, "jaun", null, null,
                List.of(), Map.of(), null, 1);
        return lexeme;
    }
}
