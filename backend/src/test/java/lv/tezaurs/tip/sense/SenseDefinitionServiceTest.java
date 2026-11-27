package lv.tezaurs.tip.sense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.common.error.SenseModelException;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.sense.dto.GenerateDefinitionsRequest;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.model.service.ModelService;
import lv.tezaurs.tip.sense.dto.SenseDefinitions;
import lv.tezaurs.tip.sense.dto.GeneratedDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenseDefinitionServiceTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleRepository exampleRepository = mock(ExampleRepository.class);
    private final ModelService model = mock(ModelService.class);
    private final SenseService senseService = mock(SenseService.class);
    private final SenseDefinitionService definitions = new SenseDefinitionService(reviews, exampleRepository, model, senseService);
    private Review review;
    private Example first;
    private Example second;
    private final UUID senseId = UUID.randomUUID();
    private final UUID dictionaryReferenceId = UUID.randomUUID();

    @BeforeEach
    void setUpDefinitionData() {
        review = new Review("vārds", "editor", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("editor");
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(100);
        first = createExternalExample("Pirmais piemērs");
        second = createExternalExample("Otrais piemērs");
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(first, second));
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                createSenseResponse(senseId, "Saved meaning", null, List.of(second.getId())),
                createSenseResponse(dictionaryReferenceId, "Esošā nozīme", 7, List.of())));
        when(senseService.saveGeneratedDefinitions(eq(review.getId()), any(), anyList())).thenAnswer(call -> {
            List<GeneratedDefinition> generated = call.getArgument(2);
            return generated.stream().map(definition -> createSenseResponse(definition.senseId(), definition.definition(), null, List.of())).toList();
        });
    }

    @Test
    void definesSensesUsingApprovedExamples() {
        UUID otherSenseId = UUID.randomUUID();
        Example otherExample = createExternalExample("Otra jaunā nozīme");
        Example dictionaryExample = createExternalExample("Konteksta piemērs");
        second.setApproved(true);
        otherExample.setApproved(true);
        when(exampleRepository.findForReview(eq(review.getId()), any()))
                .thenReturn(List.of(first, second, otherExample, dictionaryExample));
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                createSenseResponse(senseId, "First meaning", null, List.of(first.getId(), second.getId())),
                createSenseResponse(otherSenseId, "Other meaning", null, List.of(otherExample.getId())),
                createSenseResponse(UUID.randomUUID(), "No approved examples", null, List.of()),
                createSenseResponse(dictionaryReferenceId, "Dictionary meaning", 7, List.of(dictionaryExample.getId()))));
        when(model.requestStructuredResponse(eq("definitions"), any(), any(), any(), eq(SenseDefinitions.class)))
                .thenReturn(new SenseDefinitions(List.of(new SenseDefinitions.Definition(2, "Other definition"),
                        new SenseDefinitions.Definition(1, " First definition "))));

        var generated = definitions.generateSenseDefinitions(review.getId(), createDefinitionsRequest());

        assertThat(generated).extracting(SenseResponse::gloss).containsExactly("Other definition", "First definition");
        verify(model, times(1)).requestStructuredResponse(eq("definitions"), isNull(), any(), argThat(input -> {
            Map<?, ?> payload = (Map<?, ?>) input;
            String groups = payload.get("groups").toString();
            String context = payload.get("existingSenses").toString();
            assertThat(payload.toString()).doesNotContain(senseId.toString(), otherSenseId.toString(),
                    dictionaryReferenceId.toString(), second.getId().toString(), otherExample.getId().toString(), dictionaryExample.getId().toString());
            assertThat(groups).contains("id=1", "id=2", "id=3");
            assertThat(context).contains("id=4");
            return ((List<?>) payload.get("groups")).size() == 2
                    && groups.contains(second.getCitation()) && groups.contains(otherExample.getCitation())
                    && !groups.contains(first.getCitation()) && !groups.contains("No approved examples")
                    && context.contains("Dictionary meaning") && context.contains(dictionaryExample.getCitation());
        }), eq(SenseDefinitions.class));
        verify(senseService).saveGeneratedDefinitions(eq(review.getId()), eq(100), eq(List.of(
                new GeneratedDefinition(otherSenseId, "Other definition"), new GeneratedDefinition(senseId, "First definition"))));
    }

    @Test
    void rejectsInvalidDefinitions() {
        second.setApproved(true);
        for (var result : List.of(
                new SenseDefinitions(List.of()),
                new SenseDefinitions(List.of(new SenseDefinitions.Definition(999, "Skaidrojums"))),
                new SenseDefinitions(List.of(new SenseDefinitions.Definition(0, "Skaidrojums"))),
                new SenseDefinitions(List.of(new SenseDefinitions.Definition(null, "Skaidrojums"))),
                new SenseDefinitions(List.of(new SenseDefinitions.Definition(2, "Esošā nozīme nav jāmaina"))),
                new SenseDefinitions(List.of(new SenseDefinitions.Definition(1, " "))),
                new SenseDefinitions(List.of(new SenseDefinitions.Definition(1, "Pirmais"),
                        new SenseDefinitions.Definition(1, "Otrais"))))) {
            when(model.requestStructuredResponse(eq("definitions"), any(), any(), any(), eq(SenseDefinitions.class))).thenReturn(result);
            assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), createDefinitionsRequest())).isInstanceOf(SenseModelException.class);
        }
        verify(senseService, never()).saveGeneratedDefinitions(any(), any(), anyList());
    }

    @Test
    void requiresApprovedExamples() {
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), createDefinitionsRequest()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
        verify(senseService, never()).saveGeneratedDefinitions(any(), any(), anyList());
    }

    @Test
    void definesLargeExampleSets() {
        List<Example> examples = java.util.stream.IntStream.range(0, 625)
                .mapToObj(index -> createExternalExample("Piemērs " + index)).toList();
        examples.forEach(example -> example.setApproved(true));
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(examples);
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                createSenseResponse(senseId, "Saved meaning", null, examples.stream().map(Example::getId).toList())));
        when(model.requestStructuredResponse(eq("definitions"), any(), any(), any(), eq(SenseDefinitions.class)))
                .thenReturn(new SenseDefinitions(List.of(new SenseDefinitions.Definition(1, "Skaidrojums"))));

        assertThat(definitions.generateSenseDefinitions(review.getId(), createDefinitionsRequest())).hasSize(1);
    }

    @Test
    void requiresAnEditableReview() {
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(UUID.randomUUID(), createDefinitionsRequest()))
                .isInstanceOf(NotFoundException.class);
        review.setType(ReviewType.UNKNOWN);
        review.setMatchedEntryId(null);
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), createDefinitionsRequest()))
                .isInstanceOf(ConflictException.class);
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(100);
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), createDefinitionsRequest()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
    }

    private Example createExternalExample(String citation) {
        return new Example(review.getId(), new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", citation, "https://example.com", null, null, null, null), "editor");
    }

    private GenerateDefinitionsRequest createDefinitionsRequest() {
        return new GenerateDefinitionsRequest(null, null);
    }

    private static SenseResponse createSenseResponse(UUID id, String gloss, Integer existingId, List<UUID> examples) {
        return new SenseResponse(id, gloss, false, existingId, null, 1, Map.of(), examples);
    }
}
