package lv.tezaurs.tip.sense.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.common.error.SenseModelException;
import lv.tezaurs.tip.common.util.CorpusTextFormatter;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.sense.dto.GenerateDefinitionsRequest;
import lv.tezaurs.tip.sense.dto.GeneratedDefinition;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.model.service.ModelService;
import lv.tezaurs.tip.sense.dto.SenseDefinitions;
import lv.tezaurs.tip.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SenseDefinitionService {
    private final ReviewRepository reviewRepository;
    private final ExampleRepository exampleRepository;
    private final TezaursSenseRepository senseRepository;
    private final ModelService modelService;
    private final SenseService senseService;

    private record ModelExample(UUID id, String citation) {}

    public List<GeneratedDefinition> generateSenseDefinitions(UUID reviewId, GenerateDefinitionsRequest request) {
        Review review = requireEditableReview(reviewId);
        Map<UUID, SenseResponse> savedSenses = senseService.findAllForReview(reviewId).stream()
                .collect(Collectors.toMap(SenseResponse::id, Function.identity()));
        for (var selected : request.groups()) {
            SenseResponse sense = savedSenses.get(selected.id());
            if (sense == null || sense.existingSenseId() != null || !sense.exampleIds().containsAll(selected.exampleIds())) {
                throw new ConflictException("Atlasītie piemēri nepieder šai saglabātajai nozīmei.");
            }
        }

        List<UUID> exampleIds = request.groups().stream().flatMap(group -> group.exampleIds().stream()).toList();
        List<ModelExample> examples = loadSelectedExamplesForDefinitions(review, exampleIds);

        Set<UUID> requestedGroupIds = new HashSet<>();
        List<Map<String, Object>> definitionGroupInputs = new ArrayList<>();
        for (var group : request.groups()) {
            if (!requestedGroupIds.add(group.id())) {
                throw new ConflictException("Nozīmes grupa pieprasījumā atkārtojas.");
            }
            definitionGroupInputs.add(Map.of("id", group.id(), "description", group.description(), "examples",
                    examples.stream().filter(example -> group.exampleIds().contains(example.id())).toList()));
        }

        var modelResponse = modelService.requestStructuredResponse("definitions", request.modelId(), Map.of("term", review.getReviewedTerm(),
                "groups", definitionGroupInputs, "existingSenses", loadExistingSenses(review)), SenseDefinitions.class);

        return validateAndNormalizeDefinitions(reviewId, request.modelId(), modelResponse, requestedGroupIds);
    }

    private List<GeneratedDefinition> validateAndNormalizeDefinitions(UUID reviewId, String modelId,
            SenseDefinitions modelResponse, Set<UUID> requestedGroupIds) {
        if (modelResponse == null || modelResponse.definitions() == null) {
            throw rejectedModelResponse(reviewId, modelId, "definitions", "Missing definitions list");
        }

        Set<UUID> returnedGroupIds = new HashSet<>();
        for (var definition : modelResponse.definitions()) {
            if (definition == null) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Null definition");
            }
            if (!requestedGroupIds.contains(definition.senseId())) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Definition refers to an unknown group");
            }
            if (!returnedGroupIds.add(definition.senseId())) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Group has more than one definition");
            }
            if (!hasNonBlankTextWithinLimit(definition.definition(), 10000)) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Definition is blank or exceeds 10000 characters");
            }
        }

        if (!returnedGroupIds.equals(requestedGroupIds)) {
            throw rejectedModelResponse(reviewId, modelId, "definitions",
                    "Not all requested groups received a definition: expected " + requestedGroupIds.size() + ", received " + returnedGroupIds.size());
        }

        return modelResponse.definitions().stream()
                .map(definition -> new GeneratedDefinition(definition.senseId(), definition.definition().trim())).toList();
    }

    private List<ModelExample> loadAvailableExamples(Review review) {
        return exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId()).stream()
                .filter(example -> example.getSense() == null || Objects.equals(
                        example.getSense().getTargetEntryId(), review.getMatchedEntryId()))
                .map(example -> new ModelExample(example.getId(), CorpusTextFormatter.format(example.getCitation()))).toList();
    }

    private List<ModelExample> loadSelectedExamplesForDefinitions(Review review, List<UUID> requestedExampleIds) {
        Set<UUID> uniqueExampleIds = new HashSet<>(requestedExampleIds);
        if (requestedExampleIds.isEmpty() || uniqueExampleIds.size() != requestedExampleIds.size()) {
            throw new ConflictException("Atlasiet vismaz vienu piemēru; piemēri nedrīkst atkārtoties.");
        }
        List<ModelExample> examples = loadAvailableExamples(review).stream()
                .filter(example -> uniqueExampleIds.contains(example.id())).toList();
        if (examples.size() != uniqueExampleIds.size()) {
            throw new ConflictException("Piemērs nepieder pašreizējai izskatīšanai.");
        }

        return examples;
    }

    private List<TezaursSenseResponse> loadExistingSenses(Review review) {
        if (review.getMatchedEntryId() == null) return List.of();

        return senseRepository.findOrderedByEntryId(review.getMatchedEntryId()).stream()
                .map(TezaursSenseResponse::from).toList();
    }

    private Review requireEditableReview(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }
        if (review.getType() == ReviewType.UNKNOWN) {
            throw new ConflictException("Vispirms veiciet pārbaudi Tēzaurā.");
        }

        return review;
    }

    private static boolean hasNonBlankTextWithinLimit(String text, int maxLength) {
        return text != null && !text.isBlank() && text.length() <= maxLength;
    }

    private static SenseModelException rejectedModelResponse(UUID reviewId, String modelId, String operation, String reason) {
        log.warn("AI response rejected: reviewId={}, modelId={}, operation={}, reason={}",
                reviewId, modelId == null || modelId.isBlank() ? "(default)" : modelId, operation, reason);

        return new SenseModelException(new IllegalArgumentException(reason));
    }
}
