package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.CreateSuggestionRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordCorpusCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;

class SuggestionServiceTest {
    private SuggestionRepository repository;
    private SuggestionService service;

    @BeforeEach
    void setUp() {
        repository = mock(SuggestionRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        service = new SuggestionService(repository);
        when(repository.saveAndFlush(any(Suggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsASuggestionWithTrimmedInput() {
        CreateSuggestionRequest request = new CreateSuggestionRequest(
                " term ", " definition ", " example ", " notes ", " name ", " email@example.test ");

        SuggestionResponse response = service.create(request);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(repository).saveAndFlush(captor.capture());
        Suggestion saved = captor.getValue();
        assertThat(response.id()).isEqualTo(saved.getId()).isNotNull();
        assertThat(response.submittedTerm()).isEqualTo("term");
        assertThat(response.submittedDefinition()).isEqualTo("definition");
        assertThat(response.usageExample()).isEqualTo("example");
        assertThat(response.notes()).isEqualTo("notes");
        assertThat(response.submitterName()).isEqualTo("name");
        assertThat(response.submitterEmail()).isEqualTo("email@example.test");
    }

    @Test
    void convertsMissingOrBlankOptionalInputToNull() {
        CreateSuggestionRequest request = new CreateSuggestionRequest("term", "definition", null, " ", null, "");

        SuggestionResponse response = service.create(request);

        assertThat(response.usageExample()).isNull();
        assertThat(response.notes()).isNull();
        assertThat(response.submitterName()).isNull();
        assertThat(response.submitterEmail()).isNull();
    }

    @Test
    void listsAndRetrievesSuggestions() {
        Suggestion first = suggestion();
        Suggestion second = suggestion();
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(first, second));
        when(repository.findById(first.getId())).thenReturn(Optional.of(first));

        assertThat(service.list()).extracting(SuggestionResponse::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(service.get(first.getId()).id()).isEqualTo(first.getId());
    }

    @Test
    void rejectsAnUnknownSuggestion() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Suggestion " + id + " was not found");
    }

    @Test
    void appliesReviewUpdatesAndFlushesEachChange() {
        Suggestion suggestion = suggestion();
        when(repository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));

        service.changeStatus(suggestion.getId(), new UpdateStatusRequest(SuggestionStatus.IN_PROGRESS));
        service.correctTerm(suggestion.getId(), new CorrectTermRequest(" corrected "));
        service.recordTezaursCheck(suggestion.getId(), new RecordTezaursCheckRequest(CheckStatus.FOUND, 42L));
        service.recordCorpusCheck(suggestion.getId(), new RecordCorpusCheckRequest(CheckStatus.NOT_FOUND));

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
        assertThat(suggestion.getReviewedTerm()).isEqualTo("corrected");
        assertThat(suggestion.getTezaursStatus()).isEqualTo(CheckStatus.FOUND);
        assertThat(suggestion.getMatchedEntryId()).isEqualTo(42L);
        assertThat(suggestion.getCorpusStatus()).isEqualTo(CheckStatus.NOT_FOUND);
        verify(repository, times(4)).flush();
    }

    private static Suggestion suggestion() {
        return new Suggestion(UUID.randomUUID(), "term", "definition", null, null, null, null);
    }
}
