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
import lv.tezaurs.tip.sense.dto.ClusterSensesRequest;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.model.service.ModelService;
import lv.tezaurs.tip.sense.dto.ClusteredSenses;
import lv.tezaurs.tip.sense.dto.SenseGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenseClusteringServiceTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleRepository exampleRepository = mock(ExampleRepository.class);
    private final ModelService model = mock(ModelService.class);
    private final SenseService senseService = mock(SenseService.class);
    private final SenseClusteringService clustering = new SenseClusteringService(reviews, exampleRepository, model, senseService);
    private Review review;
    private Example first;
    private Example second;
    private final UUID senseId = UUID.randomUUID();
    private final UUID dictionaryReferenceId = UUID.randomUUID();

    @BeforeEach
    void setUpClusteringData() {
        review = new Review("vārds", "editor", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("editor");
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(100);
        first = createExternalExample("Pirmais piemērs");
        second = createExternalExample("Otrais piemērs");
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(first, second));
        when(senseService.addGroupedSenses(eq(review.getId()), any(), anyList())).thenAnswer(call -> {
            List<SenseGroup> groups = call.getArgument(2);
            return groups.stream().map(group -> {
                if (group.senseId() != null) {
                    return senseService.findAllForReview(review.getId()).stream()
                            .filter(sense -> sense.id().equals(group.senseId())).findFirst().orElseThrow();
                }
                return createSenseResponse(UUID.randomUUID(), group.description(), null, group.exampleIds());
            }).toList();
        });
        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                createSenseResponse(senseId, "Saved meaning", null, List.of(second.getId())),
                createSenseResponse(dictionaryReferenceId, "Esošā nozīme", 7, List.of())));
    }

    @Test
    void passesValidatedGroupsToSenseService() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Jauna nozīme", null, List.of(1), List.of()),
                new ClusteredSenses.Group("Esošā nozīme", 2, List.of(2), List.of()))));

        when(senseService.findAllForReview(review.getId())).thenReturn(List.of(
                createSenseResponse(UUID.randomUUID(), "Iespējama nozīme", null, List.of()),
                createSenseResponse(dictionaryReferenceId, "Esošā nozīme", 7, List.of())));
        var groups = clustering.regroupSenses(review.getId(), createClusteringRequest());

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
    void mapsModelSenseNumbersToSavedIds() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(new ClusteredSenses.Group(null, 1, List.of(1), List.of()))));

        clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null, "Atšķir pārnesto nozīmi."));

        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), eq(List.of(
                new SenseGroup("Saved meaning", senseId, List.of(first.getId()), List.of()))));
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), eq("Atšķir pārnesto nozīmi."),
                any(), eq(ClusteredSenses.class));
    }

    @Test
    void rejectsUnknownIdsAndInvalidDescriptions() {
        for (var proposal : List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(999), List.of()),
                new ClusteredSenses.Group("Apraksts", 999, List.of(1), List.of()),
                new ClusteredSenses.Group(" ", null, List.of(1), List.of()))) {
            when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(proposal)));
            assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), createClusteringRequest())).isInstanceOf(SenseModelException.class);
        }
        verify(senseService, never()).addGroupedSenses(any(), any(), anyList());
    }

    @Test
    void assignsEachExampleOnlyOnce() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("First", null, List.of(1, 1), List.of()),
                new ClusteredSenses.Group("Duplicate only", null, List.of(1), List.of()),
                new ClusteredSenses.Group("Second", null, List.of(1, 2), List.of()))));

        clustering.regroupSenses(review.getId(), createClusteringRequest());

        verify(senseService).addGroupedSenses(eq(review.getId()), eq(100), eq(List.of(
                new SenseGroup("First", null, List.of(first.getId()), List.of()),
                new SenseGroup("Duplicate only", null, List.of(), List.of()),
                new SenseGroup("Second", null, List.of(second.getId()), List.of()))));
    }

    @Test
    void allowsPartialGrouping() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of()));
        assertThat(clustering.regroupSenses(review.getId(), createClusteringRequest())).isEmpty();
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(1), List.of()))));
        assertThat(clustering.regroupSenses(review.getId(), createClusteringRequest())).hasSize(1);
    }

    @Test
    void groupsExamplesUnderSubsenses() {
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(
                        new ClusteredSenses.Group("Parent", null, List.of(), List.of(
                                new ClusteredSenses.Subsense("Child", null, List.of(2)))),
                        new ClusteredSenses.Group("Empty meaning", null, null, null))));

        clustering.regroupSenses(review.getId(), createClusteringRequest());

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

        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), createClusteringRequest()))
                .isInstanceOf(SenseModelException.class);
        verify(senseService, never()).addGroupedSenses(any(), any(), anyList());
    }

    @Test
    void includesUnapprovedExamplesFromApprovedSenses() {
        Sense approved = new Sense(review.getId(), 100);
        approved.setApproved(true);
        first.reassignTo(approved);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(2), List.of()))));
        clustering.regroupSenses(review.getId(), createClusteringRequest());
        verify(model).requestStructuredResponse(eq("clustering"), isNull(), any(), argThat(input ->
                input.toString().contains(second.getCitation()) && input.toString().contains(first.getCitation())), eq(ClusteredSenses.class));
    }

    @Test
    void excludesApprovedExamples() {
        Sense sense = new Sense(review.getId(), 100);
        first.reassignTo(sense);
        first.setApproved(true);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(new ClusteredSenses.Group("Meaning", null, List.of(1), List.of()))));

        clustering.regroupSenses(review.getId(), createClusteringRequest());

        verify(model).requestStructuredResponse(eq("clustering"), isNull(), any(), argThat(input ->
                input.toString().contains(second.getCitation()) && !input.toString().contains(first.getCitation())), eq(ClusteredSenses.class));
    }

    @Test
    void groupsExamplesForNewEntries() {
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(second));
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class))).thenReturn(new ClusteredSenses(List.of(
                new ClusteredSenses.Group("Apraksts", null, List.of(1), List.of()))));
        assertThat(clustering.regroupSenses(review.getId(), new ClusterSensesRequest(null, null))).hasSize(1);
    }

    @Test
    void requiresSelectedExamples() {
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of());

        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), createClusteringRequest()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
        verify(senseService, never()).addGroupedSenses(any(), any(), anyList());
    }

    @Test
    void groupsLargeExampleSets() {
        List<Example> examples = java.util.stream.IntStream.range(0, 625)
                .mapToObj(index -> createExternalExample("Piemērs " + index)).toList();
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(examples);
        when(model.requestStructuredResponse(eq("clustering"), any(), any(), any(), eq(ClusteredSenses.class)))
                .thenReturn(new ClusteredSenses(List.of(new ClusteredSenses.Group("Apraksts", null,
                        java.util.stream.IntStream.rangeClosed(1, 625).boxed().toList(), List.of()))));

        assertThat(clustering.regroupSenses(review.getId(), createClusteringRequest()))
                .singleElement().satisfies(group -> assertThat(group.exampleIds()).hasSize(625));
    }

    @Test
    void requiresAnEditableReview() {
        assertThatThrownBy(() -> clustering.regroupSenses(UUID.randomUUID(), createClusteringRequest()))
                .isInstanceOf(NotFoundException.class);
        review.setType(ReviewType.UNKNOWN);
        review.setMatchedEntryId(null);
        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), createClusteringRequest()))
                .isInstanceOf(ConflictException.class);
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(100);
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        assertThatThrownBy(() -> clustering.regroupSenses(review.getId(), createClusteringRequest()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(model);
    }

    private Example createExternalExample(String citation) {
        return new Example(review.getId(), new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", citation, "https://example.com", null, null, null, null), "editor");
    }

    private ClusterSensesRequest createClusteringRequest() {
        return new ClusterSensesRequest(null, null);
    }

    private static SenseResponse createSenseResponse(UUID id, String gloss, Integer existingId, List<UUID> examples) {
        return new SenseResponse(id, gloss, false, existingId, null, 1, Map.of(), examples);
    }
}
