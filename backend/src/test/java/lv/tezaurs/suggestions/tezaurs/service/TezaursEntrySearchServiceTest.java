package lv.tezaurs.suggestions.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSense;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TezaursEntrySearchServiceTest {
    private TezaursEntryRepository entryRepository;
    private TezaursSenseRepository senseRepository;
    private TezaursEntrySearchService service;

    @BeforeEach
    void setUp() {
        entryRepository = mock(TezaursEntryRepository.class);
        senseRepository = mock(TezaursSenseRepository.class);
        service = new TezaursEntrySearchService(entryRepository, senseRepository);
    }

    @Test
    void preservesSearchOrderAndBatchesSenses() {
        TezaursEntry firstEntry = entry(1, "mezglot", "mezglot:1");
        TezaursEntry secondEntry = entry(2, "mezglots", "mezglots:1");
        when(entryRepository.findExactMatches("mezglot")).thenReturn(List.of(firstEntry));
        when(entryRepository.findContaining("mezglot", PageRequest.of(0, 6)))
                .thenReturn(List.of(secondEntry, firstEntry));
        TezaursSense sense = mock(TezaursSense.class);
        when(sense.getEntryId()).thenReturn(2);
        when(sense.getGloss()).thenReturn("definition");
        when(senseRepository.findOrderedByEntryIdIn(List.of(1, 2))).thenReturn(List.of(sense));

        var results = service.findMatches("Mezglot");

        assertThat(results).extracting(candidate -> candidate.id()).containsExactly(1, 2);
        assertThat(results.getFirst().senses()).isEmpty();
        assertThat(results.get(1).senses()).singleElement()
                .satisfies(result -> assertThat(result.gloss()).isEqualTo("definition"));
        verify(senseRepository).findOrderedByEntryIdIn(List.of(1, 2));
    }

    @Test
    void normalizesTheReviewedTerm() {
        TezaursEntry entry = entry(3, "kvilings", "kvilings:1");
        when(entryRepository.findExactMatches("kvilings")).thenReturn(List.of(entry));
        when(entryRepository.findContaining("kvilings", PageRequest.of(0, 6)))
                .thenReturn(List.of(entry));
        when(senseRepository.findOrderedByEntryIdIn(List.of(3))).thenReturn(List.of());

        assertThat(service.findMatches("  KVILINGS  "))
                .extracting(candidate -> candidate.id())
                .containsExactly(3);
    }

    private static TezaursEntry entry(Integer id, String heading, String humanKey) {
        TezaursEntry entry = mock(TezaursEntry.class);
        when(entry.getId()).thenReturn(id);
        when(entry.getHeading()).thenReturn(heading);
        when(entry.getHumanKey()).thenReturn(humanKey);
        return entry;
    }

    @Test
    void skipsDatabaseQueriesForBlankSearch() {
        assertThat(service.findMatches("  ")).isEmpty();
        verifyNoInteractions(entryRepository, senseRepository);
    }

    @Test
    void skipsSenseQueryWhenNoEntriesMatch() {
        when(entryRepository.findContaining("missing", PageRequest.of(0, 5))).thenReturn(List.of());
        assertThat(service.findMatches("missing")).isEmpty();
        verifyNoInteractions(senseRepository);
    }

    @Test
    void retainsAllExactMatchesAndFiveAdditionalEntriesDespiteOverlap() {
        var exact = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(id -> entry(id, "term", "term:" + id)).toList();
        var additional = java.util.stream.IntStream.rangeClosed(7, 11)
                .mapToObj(id -> entry(id, "term-extra-" + id, "term-extra-" + id + ":1")).toList();
        var containing = new java.util.ArrayList<>(exact);
        containing.addAll(additional);
        when(entryRepository.findExactMatches("term")).thenReturn(exact);
        when(entryRepository.findContaining("term", PageRequest.of(0, 11)))
                .thenReturn(containing);

        var results = service.findMatches("term");

        assertThat(results).extracting(candidate -> candidate.id())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        verify(senseRepository).findOrderedByEntryIdIn(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
    }
}
