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
import lv.tezaurs.tip.tezaurs.entity.TezaursSense;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SenseModelServicesTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleRepository exampleRepository = mock(ExampleRepository.class);
    private final TezaursSenseRepository senses = mock(TezaursSenseRepository.class);
    private final ModelService model = mock(ModelService.class);
    private final SenseService senseService = mock(SenseService.class);
    private final SenseClusteringService clustering = new SenseClusteringService(reviews, exampleRepository, senses, model, senseService);
    private final SenseDefinitionService definitions = new SenseDefinitionService(reviews, exampleRepository, senses, model, senseService);
    private Review review;
    private Example first;
    private Example second;
    private final UUID senseId = UUID.randomUUID();

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
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(first, second));
        TezaursSense existing = mock(TezaursSense.class);
        when(existing.getId()).thenReturn(7);
        when(existing.getGloss()).thenReturn("Esošā nozīme");
        when(senses.findOrderedByEntryId(100)).thenReturn(List.of(existing));
        when(senseService.addGroupedSenses(eq(review.getId()), any(), anyList())).thenAnswer(call -> {
            List<SenseGroup> groups = call.getArgument(2);
            return groups.stream().map(group -> response(UUID.randomUUID(), group.description(), group.existingSenseId(), group.exampleIds())).toList();
        });
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(response(senseId, "Saved meaning", null, List.of(second.getId()))));
    }

    private Example example(String citation) {
        return new Example(review.getId(), new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", citation, "https://example.com", null, null), "editor");
    }

    private ClusterSensesRequest clusterRequest() {
        return new ClusterSensesRequest(null);
    }

    private GenerateDefinitionsRequest definitionRequest() {
        return new GenerateDefinitionsRequest(List.of(
                new GenerateDefinitionsRequest.SenseInput(senseId, "Cilvēka labots apraksts", List.of(second.getId()))), null);
    }

    @Test
    void persistsValidatedGroupsWithoutApprovingExamples() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Jauna nozīme", null, List.of(1)),
                new ClusteredSenses.Group("Esošā nozīme", 7, List.of(2)))));

        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                response(UUID.randomUUID(), "Iespējama nozīme", null, List.of()),
                response(UUID.randomUUID(), "Esošā nozīme", 7, List.of())));
        var groups = clustering.regroupSenses(review.getId(), clusterRequest());

        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst().id()).isNotNull();
        assertThat(groups.getLast().existingSenseId()).isEqualTo(7);
        assertThat(first.getSense()).isNull();
        verify(exampleRepository, never()).save(any());
        verify(reviews, never()).save(any());
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), argThat(input -> {
            var possibleSenses = ((Map<?, ?>) input).get("possibleSenses");
            return possibleSenses.equals(List.of("Iespējama nozīme"))
                    && input.toString().contains("id=1") && input.toString().contains("id=2")
                    && input.toString().contains(first.getCitation()) && input.toString().contains(second.getCitation())
                    && !input.toString().contains(first.getId().toString()) && !input.toString().contains(second.getId().toString());
        }), eq(ClusteredSenses.class));
        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), anyList());
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
            var request = new ClusterSensesRequest("chosen-model");
            when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                    new ClusteredSenses.Group("Private group description", 999, List.of(1)))));
            assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), request)).isInstanceOf(SenseModelException.class);
            when(model.requestStructuredResponse(eq("definitions"), any(), any(), eq(SenseDefinitions.class))).thenReturn(new SenseDefinitions(List.of()));
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
                new ClusteredSenses.Group("Apraksts", null, List.of(999)),
                new ClusteredSenses.Group("Apraksts", 999, List.of(1)),
                new ClusteredSenses.Group(" ", null, List.of(1)))) {
            when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(proposal)));
            assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest())).isInstanceOf(SenseModelException.class);
        }
        verify(senseService, never()).addGroupedSenses(any(), any(), anyList());
    }

    @Test
    void keepsTheFirstAssignmentWhenTheModelRepeatsAnExampleAndSkipsEmptiedGroups() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("First", null, List.of(1, 1)),
                new ClusteredSenses.Group("Duplicate only", null, List.of(1)),
                new ClusteredSenses.Group("Second", null, List.of(1, 2)))));

        clustering.regroupSenses(review.getId(), clusterRequest());

        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), eq(List.of(
                new SenseGroup("First", null, List.of(first.getId())),
                new SenseGroup("Second", null, List.of(second.getId())))));
    }

    @Test
    void rejectsIncompleteGroupResponsesAndAllowsExamplesToBeLeftUngrouped() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of()));
        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest())).isInstanceOf(SenseModelException.class);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(1)))));
        assertThat(clustering.regroupSenses(review.getId(), clusterRequest())).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatesDefinitionsFromHumanSelectedExamplesWithExistingSensesAsContext() {
        when(model.requestStructuredResponse(eq("definitions"), any(), any(), eq(SenseDefinitions.class))).thenReturn(new SenseDefinitions(List.of(
                new GeneratedDefinition(senseId, " Gala skaidrojums "))));
        var generated = definitions.generateSenseDefinitions(review.getId(), definitionRequest());
        assertThat(generated.getFirst().definition()).isEqualTo("Gala skaidrojums");
        verify(model).requestStructuredResponse(eq("definitions"), isNull(), argThat(input -> {
            var groups = (List<Map<String, Object>>) ((Map<String, Object>) input).get("groups");
            return groups.getFirst().get("examples").toString().contains("Otrais piemērs")
                    && !groups.getFirst().get("examples").toString().contains("Pirmais piemērs")
                    && ((Map<String, Object>) input).containsKey("existingSenses");
        }), eq(SenseDefinitions.class));
        verify(exampleRepository, never()).save(any());
    }

    @Test
    void rejectsMissingDuplicateForeignAndBlankDefinitions() {
        for (var result : List.of(
                new SenseDefinitions(List.of()),
                new SenseDefinitions(List.of(new GeneratedDefinition(UUID.randomUUID(), "Skaidrojums"))),
                new SenseDefinitions(List.of(new GeneratedDefinition(senseId, " "))),
                new SenseDefinitions(List.of(new GeneratedDefinition(senseId, "Pirmais"),
                        new GeneratedDefinition(senseId, "Otrais"))))) {
            when(model.requestStructuredResponse(eq("definitions"), any(), any(), eq(SenseDefinitions.class))).thenReturn(result);
            assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), definitionRequest())).isInstanceOf(SenseModelException.class);
        }
    }

    @Test
    void clusteringIncludesExamplesAttachedToApprovedSensesInTheCurrentEntry() {
        Sense approved = new Sense(review.getId(), 100);
        approved.setApproved(true);
        first.reassignTo(approved);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(2)))));
        clustering.regroupSenses(review.getId(), clusterRequest());
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), argThat(input ->
                input.toString().contains(second.getCitation()) && input.toString().contains(first.getCitation())), eq(ClusteredSenses.class));
    }

    @Test
    void rejectsEmptyClusteringAndForeignRepeatedOrMovedDefinitionExamples() {
        var group = definitionRequest().groups().getFirst();
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), new GenerateDefinitionsRequest(List.of(group, group), null)))
                .isInstanceOf(ConflictException.class);
        for (var ids : List.of(List.of(UUID.randomUUID()), List.of(first.getId(), first.getId()))) {
            assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), new GenerateDefinitionsRequest(List.of(
                    new GenerateDefinitionsRequest.SenseInput(senseId, "Apraksts", ids)), null)))
                    .isInstanceOf(ConflictException.class);
        }
        second.reassignTo(new Sense(review.getId(), 99));
        assertThatThrownBy(() -> definitions.generateSenseDefinitions(review.getId(), definitionRequest())).isInstanceOf(ConflictException.class);
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), clusterRequest())).isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
    }

    @Test
    void supportsNewEntriesWithoutExistingSenses() {
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(second));
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(1)))));
        assertThat(clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null))).hasSize(1);
        verifyNoInteractions(senses);
    }

    @Test
    void acceptsMoreThanOneHundredExamplesForGroupingAndDefinitions() {
        List<Example> selectedExamples = java.util.stream.IntStream.range(0, 625)
                .mapToObj(index -> example("Piemērs " + index)).toList();
        List<UUID> selectedIds = selectedExamples.stream().map(Example::getId).toList();
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(selectedExamples);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, java.util.stream.IntStream.rangeClosed(1, 625).boxed().toList()))));
        when(model.requestStructuredResponse(eq("definitions"), any(), any(), eq(SenseDefinitions.class))).thenReturn(new SenseDefinitions(List.of(
                new GeneratedDefinition(senseId, "Skaidrojums"))));

        assertThat(clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null)))
                .singleElement().satisfies(group -> assertThat(group.exampleIds()).hasSize(625));
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(response(senseId, "Saved meaning", null, selectedIds)));
        assertThat(definitions.generateSenseDefinitions(review.getId(), new GenerateDefinitionsRequest(List.of(
                new GenerateDefinitionsRequest.SenseInput(senseId, "Apraksts", selectedIds)), null))).hasSize(1);
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
        return new SenseResponse(id, gloss, false, existingId, null, null, 1, Map.of(), examples);
    }

}
