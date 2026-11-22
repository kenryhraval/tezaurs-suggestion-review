package lv.tezaurs.tip.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import lv.tezaurs.tip.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.tip.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.tip.tezaurs.entity.TezaursEntry;
import lv.tezaurs.tip.tezaurs.entity.TezaursSense;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

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
    void preservesSearchOrderAndGroupsBatchedSensesByEntry() {
        TezaursEntry firstEntry = entry(1, "mezglot", "mezglot:1");
        TezaursEntry secondEntry = entry(2, "mezglots", "mezglots:1");
        when(entryRepository.findExactMatches("mezglot")).thenReturn(List.of(firstEntry));
        when(entryRepository.findContaining("mezglot", PageRequest.of(0, 6)))
                .thenReturn(List.of(secondEntry, firstEntry));
        TezaursSense root = sense(10, 1, null, "Pirmā nozīme.");
        TezaursSense child = sense(11, 1, 10, "Apakšnozīme.");
        TezaursSense other = sense(20, 2, null, "Cita šķirkļa nozīme.");
        when(senseRepository.findOrderedByEntryIdIn(List.of(1, 2)))
                .thenReturn(List.of(other, root, child));
        var results = service.findMatches("Mezglot");

        assertThat(results).extracting(TezaursEntryCandidateResponse::id).containsExactly(1, 2);
        assertThat(results.get(1).heading()).isEqualTo("mezglots");
        assertThat(results.get(1).humanKey()).isEqualTo("mezglots:1");
        assertThat(results.get(0).senses()).extracting(TezaursSenseResponse::id).containsExactly(10, 11);
        assertThat(results.get(0).senses().get(1).parentSenseId()).isEqualTo(10);
        assertThat(results.get(1).senses()).singleElement()
                .satisfies(sense -> assertThat(sense.gloss()).isEqualTo("Cita šķirkļa nozīme."));
        verify(senseRepository).findOrderedByEntryIdIn(List.of(1, 2));
        verify(senseRepository, never()).findOrderedByEntryId(anyInt());
    }

    @Test
    void normalizesTheReviewedTerm() {
        TezaursEntry entry = entry(3, "kvilings", "kvilings:1");
        when(entryRepository.findExactMatches("kvilings")).thenReturn(List.of(entry));
        when(entryRepository.findContaining("kvilings", PageRequest.of(0, 6)))
                .thenReturn(List.of(entry));

        assertThat(service.findMatches("  KVILINGS  "))
                .extracting(TezaursEntryCandidateResponse::id)
                .containsExactly(3);
    }

    private static TezaursSense sense(int id, int entryId, Integer parentId, String gloss) {
        TezaursSense sense = mock(TezaursSense.class);
        when(sense.getId()).thenReturn(id);
        when(sense.getEntryId()).thenReturn(entryId);
        when(sense.getParentSenseId()).thenReturn(parentId);
        when(sense.getGloss()).thenReturn(gloss);
        return sense;
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
    void returnsNoCandidatesWhenNoEntriesMatch() {
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

        assertThat(results).extracting(TezaursEntryCandidateResponse::id)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

    }
}
