package lv.tezaurs.suggestions.meaning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRequest;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.entity.MeaningStatus;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        when(meaningRepository.saveAndFlush(any(Meaning.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAndListsReviewerMeanings() {
        UUID suggestionId = UUID.randomUUID();
        Meaning parent = meaning(suggestionId, null, MeaningOrigin.SUBMITTER, "source");
        when(suggestionRepository.existsById(suggestionId)).thenReturn(true);
        when(meaningRepository.findByIdAndSuggestionId(parent.getId(), suggestionId)).thenReturn(Optional.of(parent));

        var created = service.create(suggestionId, new CreateMeaningRequest(" revised gloss ", parent.getId()));
        when(meaningRepository.findAllBySuggestionIdOrderByCreatedAtAscIdAsc(suggestionId))
                .thenReturn(List.of(parent));

        assertThat(created.origin()).isEqualTo(MeaningOrigin.REVIEWER);
        assertThat(created.parentMeaningId()).isEqualTo(parent.getId());
        assertThat(created.gloss()).isEqualTo("revised gloss");
        assertThat(service.list(suggestionId)).hasSize(1);
    }

    @Test
    void revisesWithoutOverwritingTheSource() {
        UUID suggestionId = UUID.randomUUID();
        Meaning source = meaning(suggestionId, null, MeaningOrigin.GENERATED, "original text");
        when(meaningRepository.findByIdAndSuggestionId(source.getId(), suggestionId)).thenReturn(Optional.of(source));

        var revision = service.revise(suggestionId, source.getId(),
                new CreateMeaningRevisionRequest("corrected text"));

        assertThat(source.getGloss()).isEqualTo("original text");
        assertThat(source.getStatus()).isEqualTo(MeaningStatus.SUPERSEDED);
        assertThat(revision.origin()).isEqualTo(MeaningOrigin.REVIEWER);
        assertThat(revision.supersedesMeaningId()).isEqualTo(source.getId());
        assertThat(revision.gloss()).isEqualTo("corrected text");
        assertThatThrownBy(source::approve)
                .isInstanceOf(ConflictException.class)
                .hasMessage("A superseded meaning cannot be changed");

        Meaning existing = new Meaning(suggestionId, null, MeaningOrigin.EXISTING, "Tēzaurs text");
        assertThat(existing.getOrigin()).isEqualTo(MeaningOrigin.EXISTING);
        assertThat(existing.getStatus()).isEqualTo(MeaningStatus.APPROVED);
        when(meaningRepository.findByIdAndSuggestionId(existing.getId(), suggestionId))
                .thenReturn(Optional.of(existing));
        var existingRevision = service.revise(suggestionId, existing.getId(),
                new CreateMeaningRevisionRequest("updated Tēzaurs text"));
        assertThat(existing.getStatus()).isEqualTo(MeaningStatus.SUPERSEDED);
        assertThat(existingRevision.supersedesMeaningId()).isEqualTo(existing.getId());
    }

    @Test
    void approvesAndRejectsMeanings() {
        UUID suggestionId = UUID.randomUUID();
        Meaning meaning = meaning(suggestionId, null, MeaningOrigin.REVIEWER, "gloss");
        when(meaningRepository.findByIdAndSuggestionId(meaning.getId(), suggestionId))
                .thenReturn(Optional.of(meaning));

        assertThat(service.approve(suggestionId, meaning.getId()).status()).isEqualTo(MeaningStatus.APPROVED);
        assertThat(service.reject(suggestionId, meaning.getId()).status()).isEqualTo(MeaningStatus.REJECTED);
        verify(meaningRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void rejectsUnknownSuggestionsAndMeanings() {
        UUID suggestionId = UUID.randomUUID();
        UUID meaningId = UUID.randomUUID();

        assertThatThrownBy(() -> service.list(suggestionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Suggestion " + suggestionId + " was not found");
        assertThatThrownBy(() -> service.approve(suggestionId, meaningId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Meaning " + meaningId + " was not found");
    }

    private static Meaning meaning(UUID suggestionId, UUID parentId, MeaningOrigin origin, String gloss) {
        return new Meaning(suggestionId, parentId, origin, gloss);
    }
}
