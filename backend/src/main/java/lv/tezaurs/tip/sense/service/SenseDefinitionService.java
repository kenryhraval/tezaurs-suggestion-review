package lv.tezaurs.tip.sense.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SenseDefinitionService {
    private final ReviewRepository reviewRepository;
    private final ExampleRepository exampleRepository;
    private final ModelService modelService;
    private final SenseService senseService;

    private record NumberedExample(int id, String citation) {}
    private record NumberedSense(int id, String description, List<NumberedExample> examples) {}

    public List<SenseResponse> generateSenseDefinitions(UUID reviewId, GenerateDefinitionsRequest request) {
        Review review = requireEditableReview(reviewId);
        List<SenseResponse> savedSenses = senseService.findAllForReview(reviewId);
        var examples = exampleRepository.findForReview(reviewId, review.getMatchedEntryId());
        Map<UUID, Integer> exampleNumbersById = new HashMap<>();
        for (int index = 0; index < examples.size(); index++) {
            exampleNumbersById.put(examples.get(index).getId(), index + 1);
        }

        Map<Integer, UUID> senseIdsByNumber = new HashMap<>();
        List<NumberedSense> groups = new ArrayList<>();
        List<NumberedSense> existingSenses = new ArrayList<>();

        for (int index = 0; index < savedSenses.size(); index++) {
            SenseResponse sense = savedSenses.get(index);
            int senseNumber = index + 1;
            var senseExamples = examples.stream()
                    .filter(example -> sense.exampleIds().contains(example.getId()))
                    .filter(example -> sense.existingSenseId() != null || example.isApproved())
                    .map(example -> new NumberedExample(exampleNumbersById.get(example.getId()),
                            CorpusTextFormatter.format(example.getCitation())))
                    .toList();
            var input = new NumberedSense(senseNumber, sense.gloss(), senseExamples);
            if (sense.existingSenseId() != null) {
                existingSenses.add(input);
            } else if (!senseExamples.isEmpty()) {
                groups.add(input);
                senseIdsByNumber.put(senseNumber, sense.id());
            }
        }
        if (groups.isEmpty()) {
            throw new ConflictException("Vispirms piesaistiet piemērus jaunajām nozīmēm un apstipriniet tos.");
        }

        var modelResponse = modelService.requestStructuredResponse("definitions", request.modelId(), request.additionalInstructions(),
                Map.of("term", review.getReviewedTerm(), "groups", groups, "existingSenses", existingSenses), SenseDefinitions.class);
        List<GeneratedDefinition> definitions = validateAndResolveDefinitions(reviewId, request.modelId(), modelResponse, senseIdsByNumber);

        return senseService.saveGeneratedDefinitions(reviewId, review.getMatchedEntryId(), definitions);
    }

    private List<GeneratedDefinition> validateAndResolveDefinitions(UUID reviewId, String modelId,
            SenseDefinitions modelResponse, Map<Integer, UUID> senseIdsByNumber) {
        if (modelResponse == null || modelResponse.definitions() == null) {
            throw rejectedModelResponse(reviewId, modelId, "definitions", "Missing definitions list");
        }

        Set<Integer> returnedSenseNumbers = new HashSet<>();
        List<GeneratedDefinition> definitions = new ArrayList<>();
        for (var definition : modelResponse.definitions()) {
            if (definition == null) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Null definition");
            }
            if (!senseIdsByNumber.containsKey(definition.senseId())) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Definition refers to an unknown group");
            }
            if (!returnedSenseNumbers.add(definition.senseId())) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Group has more than one definition");
            }
            if (!hasNonBlankTextWithinLimit(definition.definition(), 10000)) {
                throw rejectedModelResponse(reviewId, modelId, "definitions", "Definition is blank or exceeds 10000 characters");
            }
            UUID senseId = senseIdsByNumber.get(definition.senseId());
            definitions.add(new GeneratedDefinition(senseId, definition.definition().trim()));
        }

        if (!returnedSenseNumbers.equals(senseIdsByNumber.keySet())) {
            throw rejectedModelResponse(reviewId, modelId, "definitions",
                    "Not all requested groups received a definition: expected " + senseIdsByNumber.size() + ", received " + returnedSenseNumbers.size());
        }

        return definitions;
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
