package lv.tezaurs.suggestions.meaning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.entity.MeaningStatus;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class MeaningServiceTest {
    private MeaningRepository meaningRepository;
    private SuggestionRepository suggestionRepository;
    private MeaningService service;

    @BeforeEach
    void setUp() {
        meaningRepository = mock(MeaningRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        suggestionRepository = mock(SuggestionRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        service = new MeaningService(meaningRepository, suggestionRepository);
        when(meaningRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Meaning.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void revisesWithoutOverwritingTheSource() {
        UUID suggestionId = UUID.randomUUID();
        Meaning source = meaning(suggestionId, MeaningOrigin.GENERATED, "original text");
        when(meaningRepository.findByIdAndSuggestionId(source.getId(), suggestionId)).thenReturn(Optional.of(source));

        var revision = service.revise(suggestionId, source.getId(),
                new CreateMeaningRevisionRequest("corrected text"));

        assertThat(source.getGloss()).isEqualTo("original text");
        assertThat(source.getStatus()).isEqualTo(MeaningStatus.SUPERSEDED);
        assertThat(revision.origin()).isEqualTo(MeaningOrigin.REVIEWER);
        assertThat(revision.status()).isEqualTo(MeaningStatus.CURRENT);
        assertThat(revision.supersedesMeaningId()).isEqualTo(source.getId());
        assertThat(revision.gloss()).isEqualTo("corrected text");
        assertThatThrownBy(() -> source.revise("another revision"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A superseded meaning cannot be changed");
    }

    @Test
    void rejectsUnknownSuggestionsAndMeanings() {
        UUID suggestionId = UUID.randomUUID();
        UUID meaningId = UUID.randomUUID();

        assertThatThrownBy(() -> service.list(suggestionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Suggestion " + suggestionId + " was not found");
        assertThatThrownBy(() -> service.revise(suggestionId, meaningId,
                new CreateMeaningRevisionRequest("revision")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Meaning " + meaningId + " was not found");
    }

    private static Meaning meaning(UUID suggestionId, MeaningOrigin origin, String gloss) {
        return new Meaning(suggestionId, origin, gloss);
    }
}
