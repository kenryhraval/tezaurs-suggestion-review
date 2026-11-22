package lv.tezaurs.tip.sense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import lv.tezaurs.tip.sense.dto.ClusterSensesRequest;
import lv.tezaurs.tip.sense.dto.GenerateDefinitionsRequest;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.model.service.ModelService;
import lv.tezaurs.tip.sense.dto.ClusteredSenses;
import lv.tezaurs.tip.sense.dto.SenseGroup;
import lv.tezaurs.tip.sense.dto.SenseDefinitions;
import lv.tezaurs.tip.sense.dto.GeneratedDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SenseModelServicesTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleRepository exampleRepository = mock(ExampleRepository.class);
    private final ModelService model = mock(ModelService.class);
    private final SenseService senseService = mock(SenseService.class);
    private final SenseClusteringService clustering = new SenseClusteringService(reviews, exampleRepository, model, senseService);
    private final SenseDefinitionService definitions = new SenseDefinitionService(reviews, exampleRepository, model, senseService);
    private Review review;
    private Example first;
    private Example second;
    private final UUID senseId = UUID.randomUUID();
    private final UUID dictionaryReferenceId = UUID.randomUUID();

    @BeforeEach
    void prepare() {
        review = new Review("vārds", "editor", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("editor");
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(100);
        first = example("Pirmais piemērs");
        second = example("Otrais piemērs");
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(first, second));
        when(senseService.addGroupedSenses(eq(review.getId()), any(), anyList())).thenAnswer(call -> {
            List<SenseGroup> groups = call.getArgument(2);
            return groups.stream().map(group -> {
                if (group.senseId() != null) {
                    return senseService.findAllForReview(review.getId()).stream()
                            .filter(sense -> sense.id().equals(group.senseId())).findFirst().orElseThrow();
                }
                return response(UUID.randomUUID(), group.description(), null, group.exampleIds());
            }).toList();
        });
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                response(senseId, "Saved meaning", null, List.of(second.getId())),
                response(dictionaryReferenceId, "Esošā nozīme", 7, List.of())));
        when(senseService.saveGeneratedDefinitions(eq(review.getId()), any(), anyList())).thenAnswer(call -> {
            List<GeneratedDefinition> generated = call.getArgument(2);
            return generated.stream().map(definition -> response(definition.senseId(), definition.definition(), null, List.of())).toList();
        });
    }

    private Example example(String citation) {
        return new Example(review.getId(), new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", citation, "https://example.com", null, null, null, null), "editor");
    }

    private ClusterSensesRequest clusterRequest() {
        return new ClusterSensesRequest(null, null);
    }

    private GenerateDefinitionsRequest definitionRequest() {
        return new GenerateDefinitionsRequest(null, null);
    }

    @Test
    void persistsValidatedGroupsWithoutApprovingExamples() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Jauna nozīme", null, List.of(1), List.of()),
                new ClusteredSenses.Group("Esošā nozīme", 2, List.of(2), List.of()))));

        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                response(UUID.randomUUID(), "Iespējama nozīme", null, List.of()),
                response(dictionaryReferenceId, "Esošā nozīme", 7, List.of())));
        var groups = clustering.regroupSenses(review.getId(), clusterRequest());

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().id()).isNotNull();
        assertThat(groups.getLast().existingSenseId()).isEqualTo(7);
        assertThat(first.getSense()).isNull();
        verify(exampleRepository, never()).save(any());
        verify(reviews, never()).save(any());
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), any(), argThat(input -> {
            var suppliedSenses = ((Map<?, ?>) input).get("senses");
            return suppliedSenses.toString().contains("Iespējama nozīme")
                    && input.toString().contains("id=1") && input.toString().contains("id=2")
                    && input.toString().contains(first.getCitation()) && input.toString().contains(second.getCitation())
                    && !input.toString().contains(first.getId().toString()) && !input.toString().contains(second.getId().toString());
        }), eq(ClusteredSenses.class));
        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), anyList());
    }

    @Test
    void mapsNumberedLocalSensesBackToSavedIdsAndPassesReviewerInstructions() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(new ClusteredSenses.Group(null, 1, List.of(1), List.of()))));

        clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null, "Atšķir pārnesto nozīmi."));

        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), eq(List.of(
                new SenseGroup("Saved meaning", senseId, List.of(first.getId()), List.of()))));
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), eq("Atšķir pārnesto nozīmi."),
                any(), eq(ClusteredSenses.class));
    }

    @Test
    void logsTheReviewModelOperationAndSpecificValidationFailure() {
        var logger = (Logger) LoggerFactory.getLogger(SenseClusteringService.class);
        var definitionLogger = (Logger) LoggerFactory.getLogger(SenseDefinitionService.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        definitionLogger.addAppender(captured);
        try {
            var request = new ClusterSensesRequest("chosen-model", null);
            when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                    new ClusteredSenses.Group("Private group description", 999, List.of(1), List.of()))));
            assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), request)).isInstanceOf(SenseModelException.class);
            when(model.requestStructuredResponse(eq("definitions"), any(), any(), any(), eq(SenseDefinitions.class))).thenReturn(new SenseDefinitions(List.of()));
            second.setApproved(true);
            assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), definitionRequest())).isInstanceOf(SenseModelException.class);

            var messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0)).contains("reviewId=" + review.getId(), "modelId=chosen-model", "operation=clustering",
                    "reason=Group refers to an unknown existing sense");
            assertThat(messages.get(1)).contains("modelId=(default)", "operation=definitions", "expected 1, received 0");
            assertThat(String.join("\n", messages)).doesNotContain("Private group description", first.getCitation(), second.getCitation());
        } finally {
            logger.detachAppender(captured);
            definitionLogger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void rejectsInventedIdsAndMalformedGroups() {
        for (var proposal : List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(999), List.of()),
                new ClusteredSenses.Group("Apraksts", 999, List.of(1), List.of()),
                new ClusteredSenses.Group(" ", null, List.of(1), List.of()))) {
            when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(proposal)));
            assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest())).isInstanceOf(SenseModelException.class);
        }
        verify(senseService, never()).addGroupedSenses(any(), any(), anyList());
    }

    @Test
    void keepsTheFirstExampleAssignmentWithoutDiscardingEmptyMeanings() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("First", null, List.of(1, 1), List.of()),
                new ClusteredSenses.Group("Duplicate only", null, List.of(1), List.of()),
                new ClusteredSenses.Group("Second", null, List.of(1, 2), List.of()))));

        clustering.regroupSenses(review.getId(), clusterRequest());

        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), eq(List.of(
                new SenseGroup("First", null, List.of(first.getId()), List.of()),
                new SenseGroup("Duplicate only", null, List.of(), List.of()),
                new SenseGroup("Second", null, List.of(second.getId()), List.of()))));
    }

    @Test
    void allowsNoGroupsAndExamplesToBeLeftUngrouped() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of()));
        assertThat(clustering.regroupSenses(review.getId(), clusterRequest())).isEmpty();
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(1), List.of()))));
        assertThat(clustering.regroupSenses(review.getId(), clusterRequest())).hasSize(1);
    }

    @Test
    void acceptsParentsWithoutDirectExamplesAndResolvesSubsenseExampleNumbers() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(
                        new ClusteredSenses.Group("Parent", null, List.of(), List.of(
                                new ClusteredSenses.Subsense("Child", null, List.of(2)))),
                        new ClusteredSenses.Group("Empty meaning", null, null, null))));

        clustering.regroupSenses(review.getId(), clusterRequest());

        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), eq(List.of(
                new SenseGroup("Parent", null, List.of(), List.of(
                        new SenseGroup.Subsense("Child", null, List.of(second.getId())))),
                new SenseGroup("Empty meaning", null, List.of(), List.of()))));
    }

    @Test
    void rejectsUnknownExampleNumbersInsideSubsensesBeforeSaving() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(new ClusteredSenses.Group("Parent", null, List.of(),
                        List.of(new ClusteredSenses.Subsense("Child", null, List.of(999)))))));

        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest()))
                .isInstanceOf(SenseModelException.class);
        verify(senseService, never()).addGroupedSenses(any(), any(), anyList());
    }

    @Test
    void generatesAllEligibleDefinitionsInOneCallUsingOnlyApprovedExamplesAndExistingSenseContext() {
        UUID otherSenseId = UUID.randomUUID();
        Example otherExample = example("Otra jaunā nozīme");
        Example dictionaryExample = example("Konteksta piemērs");
        second.setApproved(true);
        otherExample.setApproved(true);
        when(exampleRepository.findForReview(eq(review.getId()), any()))
                .thenReturn(List.of(first, second, otherExample, dictionaryExample));
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                response(senseId, "First meaning", null, List.of(first.getId(), second.getId())),
                response(otherSenseId, "Other meaning", null, List.of(otherExample.getId())),
                response(UUID.randomUUID(), "No approved examples", null, List.of()),
                response(dictionaryReferenceId, "Dictionary meaning", 7, List.of(dictionaryExample.getId()))));
        when(model.requestStructuredResponse(eq("definitions"), any(), any(), any(), eq(SenseDefinitions.class)))
                .thenReturn(new SenseDefinitions(List.of(new SenseDefinitions.Definition(2, "Other definition"),
                        new SenseDefinitions.Definition(1, " First definition "))));

        var generated = definitions.generateSenseDefinitions(review.getId(), definitionRequest());

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
    void rejectsMissingDuplicateForeignAndBlankDefinitions() {
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
            assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), definitionRequest())).isInstanceOf(SenseModelException.class);
        }
        verify(senseService, never()).saveGeneratedDefinitions(any(), any(), anyList());
    }

    @Test
    void clusteringIncludesExamplesAttachedToApprovedSensesInTheCurrentEntry() {
        Sense approved = new Sense(review.getId(), 100);
        approved.setApproved(true);
        first.reassignTo(approved);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(2), List.of()))));
        clustering.regroupSenses(review.getId(), clusterRequest());
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), any(), argThat(input ->
                input.toString().contains(second.getCitation()) && input.toString().contains(first.getCitation())), eq(ClusteredSenses.class));
    }

    @Test
    void clusteringLeavesApprovedExamplesOutOfTheModelRequest() {
        Sense sense = new Sense(review.getId(), 100);
        first.reassignTo(sense);
        first.setApproved(true);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(new ClusteredSenses.Group("Meaning", null, List.of(1), List.of()))));

        clustering.regroupSenses(review.getId(), clusterRequest());

        verify(model).requestStructuredResponse(eq("clustering"), isNull(), any(), argThat(input ->
                input.toString().contains(second.getCitation()) && !input.toString().contains(first.getCitation())), eq(ClusteredSenses.class));
    }

    @Test
    void requiresApprovedExamplesForDefinitionsAndSelectedExamplesForClustering() {
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), definitionRequest()))
                .isInstanceOf(ConflictException.class);
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of());
        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest())).isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
        verify(senseService, never()).saveGeneratedDefinitions(any(), any(), anyList());
    }

    @Test
    void supportsNewEntriesWithoutExistingSenses() {
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(second));
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(1), List.of()))));
        assertThat(clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null, null))).hasSize(1);
    }

    @Test
    void acceptsMoreThanOneHundredExamplesForGroupingAndDefinitions() {
        List<Example> selectedExamples = java.util.stream.IntStream.range(0, 625)
                .mapToObj(index -> example("Piemērs " + index)).toList();
        List<UUID> selectedIds = selectedExamples.stream().map(Example::getId).toList();
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(selectedExamples);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, java.util.stream.IntStream.rangeClosed(1, 625).boxed().toList(), List.of()))));
        when(model.requestStructuredResponse(eq("definitions"), any(), any(), any(), eq(SenseDefinitions.class))).thenReturn(new SenseDefinitions(List.of(
                new SenseDefinitions.Definition(1, "Skaidrojums"))));

        assertThat(clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null, null)))
                .singleElement().satisfies(group -> assertThat(group.exampleIds()).hasSize(625));
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(response(senseId, "Saved meaning", null, selectedIds)));
        selectedExamples.forEach(example -> example.setApproved(true));
        assertThat(definitions.generateSenseDefinitions(review.getId(), definitionRequest())).hasSize(1);
    }

    @Test
    void requiresReviewInProgressWithKnownType() {
        assertThatThrownBy(() -> clustering.regroupSenses(UUID.randomUUID(), clusterRequest())).isInstanceOf(NotFoundException.class);
        review.setType(ReviewType.UNKNOWN);
        review.setMatchedEntryId(null);
        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest())).isInstanceOf(ConflictException.class);
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(100);
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        review.setLastStatusChangedBy("editor");
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), definitionRequest())).isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
    }
    private static SenseResponse response(UUID id, String gloss, Integer existingId, List<UUID> examples) {
        return new SenseResponse(id, gloss, false, existingId, null, 1, Map.of(), examples);
    }

}
