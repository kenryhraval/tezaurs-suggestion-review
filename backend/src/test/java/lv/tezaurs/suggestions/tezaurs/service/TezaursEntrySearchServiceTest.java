package lv.tezaurs.suggestions.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexeme;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TezaursEntrySearchServiceTest {
    private TezaursLexemeRepository lexemeRepository;
    private TezaursSenseRepository senseRepository;
    private TezaursEntrySearchService service;

    @BeforeEach
    void setUp() {
        lexemeRepository = mock(TezaursLexemeRepository.class);
        senseRepository = mock(TezaursSenseRepository.class);
        service = new TezaursEntrySearchService(lexemeRepository, senseRepository);
    }

    @Test
    void returnsTheExactMatchFirst() {
        TezaursEntry firstEntry = entry(1, "mezglot", "mezglot:1");
        TezaursEntry secondEntry = entry(2, "mezglots", "mezglots:1");
        TezaursLexeme firstLexeme = lexeme(firstEntry, "mezglot");
        TezaursLexeme secondLexeme = lexeme(secondEntry, "mezglots");
        when(lexemeRepository.findContaining("mezglot", PageRequest.of(0, 5)))
                .thenReturn(List.of(firstLexeme, secondLexeme));
        when(senseRepository.findOrderedByEntryId(1)).thenReturn(List.of());
        when(senseRepository.findOrderedByEntryId(2)).thenReturn(List.of());

        var results = service.findMatches("Mezglot", null);

        assertThat(results).extracting(candidate -> candidate.id()).containsExactly(1, 2);
    }

    @Test
    void normalizesTheReviewedTerm() {
        TezaursEntry entry = entry(3, "kvilings", "kvilings:1");
        TezaursLexeme lexeme = lexeme(entry, "kvilings");
        when(lexemeRepository.findContaining("kvilings", PageRequest.of(0, 5)))
                .thenReturn(List.of(lexeme));
        when(senseRepository.findOrderedByEntryId(3)).thenReturn(List.of());

        assertThat(service.findMatches("  KVILINGS  ", null))
                .extracting(candidate -> candidate.id())
                .containsExactly(3);
    }

    @Test
    void keepsThePreviouslySelectedEntryVisible() {
        TezaursEntry entry = entry(42, "iepriekšējais", "iepriekšējais:1");
        TezaursLexeme lexeme = lexeme(entry, "iepriekšējais");
        when(lexemeRepository.findContaining("labots", PageRequest.of(0, 5))).thenReturn(List.of());
        when(lexemeRepository.findFirstByEntryIdOrderByOrderNoAscIdAsc(42))
                .thenReturn(Optional.of(lexeme));
        when(senseRepository.findOrderedByEntryId(42)).thenReturn(List.of());

        assertThat(service.findMatches("labots", 42))
                .extracting(candidate -> candidate.id())
                .containsExactly(42);
    }

    private static TezaursEntry entry(Integer id, String heading, String humanKey) {
        TezaursEntry entry = mock(TezaursEntry.class);
        when(entry.getId()).thenReturn(id);
        when(entry.getHeading()).thenReturn(heading);
        when(entry.getHumanKey()).thenReturn(humanKey);
        return entry;
    }

    private static TezaursLexeme lexeme(TezaursEntry entry, String lemma) {
        Integer entryId = entry.getId();
        TezaursLexeme lexeme = mock(TezaursLexeme.class);
        when(lexeme.getEntry()).thenReturn(entry);
        when(lexeme.getEntryId()).thenReturn(entryId);
        when(lexeme.getLemma()).thenReturn(lemma);
        return lexeme;
    }
}
