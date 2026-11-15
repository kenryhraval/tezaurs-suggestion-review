package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import org.junit.jupiter.api.Test;

class SuggestionServiceTest {
    @Test
    void countsAllSourcesButExcludesNewAndTrashCases() {
        var sourceRepository = mock(IncubatorSuggestionRepository.class);
        var caseRepository = mock(ReviewCaseRepository.class);
        var service = new SuggestionService(sourceRepository, caseRepository);
        when(sourceRepository.count()).thenReturn(3727L);
        when(caseRepository.countByStatusNotIn(List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE))).thenReturn(15L);

        var counts = service.counts();

        assertThat(counts.suggestions()).isEqualTo(3727L);
        assertThat(counts.cases()).isEqualTo(15L);
    }
}
