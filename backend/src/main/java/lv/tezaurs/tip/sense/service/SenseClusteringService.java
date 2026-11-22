package lv.tezaurs.tip.sense.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import lv.tezaurs.tip.sense.dto.ClusterSensesRequest;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.model.service.ModelService;
import lv.tezaurs.tip.sense.dto.ClusteredSenses;
import lv.tezaurs.tip.sense.dto.SenseGroup;
import lv.tezaurs.tip.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SenseClusteringService {
    private final ReviewRepository reviewRepository;
    private final ExampleRepository exampleRepository;
    private final TezaursSenseRepository senseRepository;
    private final ModelService modelService;
    private final SenseService senseService;

    private record ModelExample(UUID id, String citation) {}
    private record NumberedExample(int id, String citation) {}

    public List<SenseResponse> regroupSenses(UUID reviewId, ClusterSensesRequest request) {
        Review review = requireEditableReview(reviewId);
        List<ModelExample> examples = loadExamplesForClustering(review);
        if (examples.isEmpty()) {
            throw new ConflictException("Vispirms atzīmējiet piemērus lietojuma pārbaudē.");
        }

        List<NumberedExample> numberedExamples = new ArrayList<>();
        for (int index = 0; index < examples.size(); index++) {
            numberedExamples.add(new NumberedExample(index + 1, examples.get(index).citation()));
        }

        List<String> possibleSenses = senseService.findAllForReview(reviewId).stream()
                .filter(sense -> sense.existingSenseId() == null)
                .map(SenseResponse::gloss).toList();
        List<TezaursSenseResponse> existingSenses = loadExistingSenses(review);

        var modelResponse = modelService.requestStructuredResponse("clustering", request.modelId(), Map.of("term", review.getReviewedTerm(),
                "examples", numberedExamples, "existingSenses", existingSenses,
                "possibleSenses", possibleSenses), ClusteredSenses.class);

        List<SenseGroup> validatedGroups = validateGroups(reviewId, request, modelResponse, examples, existingSenses);

        return senseService.addGroupedSenses(reviewId, review.getMatchedEntryId(), validatedGroups);
    }

    private List<SenseGroup> validateGroups(UUID reviewId, ClusterSensesRequest request,
            ClusteredSenses modelResponse, List<ModelExample> examples, List<TezaursSenseResponse> existingSenses) {
        if (modelResponse == null || modelResponse.groups() == null) {
            throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Missing groups list");
        }
        if (modelResponse.groups().isEmpty() || modelResponse.groups().size() > 50) {
            throw rejectedModelResponse(reviewId, request.modelId(), "clustering",
                    "Expected 1–50 groups, received " + modelResponse.groups().size());
        }

        Set<Integer> existingSenseIds = new HashSet<>();
        existingSenses.forEach(sense -> existingSenseIds.add(sense.id()));
        Set<Integer> groupedExampleNumbers = new HashSet<>();
        List<SenseGroup> groups = new ArrayList<>();
        int duplicateExampleCount = 0;

        for (var proposedGroup : modelResponse.groups()) {
            if (proposedGroup == null) {
                throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Null group");
            }
            if (!hasNonBlankTextWithinLimit(proposedGroup.description(), 2000)) {
                throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Group description is blank or exceeds 2000 characters");
            }
            if (proposedGroup.exampleIds() == null || proposedGroup.exampleIds().isEmpty()) {
                throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Group has no example IDs");
            }
            if (proposedGroup.existingSenseId() != null && !existingSenseIds.contains(proposedGroup.existingSenseId())) {
                throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Group refers to an unknown existing sense");
            }
            List<UUID> uniqueExampleIds = new ArrayList<>();
            for (Integer exampleNumber : proposedGroup.exampleIds()) {
                if (exampleNumber == null || exampleNumber < 1 || exampleNumber > examples.size()) {
                    throw rejectedModelResponse(reviewId, request.modelId(), "clustering",
                            "Group contains example numbers not supplied in the request");
                }
                if (groupedExampleNumbers.add(exampleNumber)) {
                    uniqueExampleIds.add(examples.get(exampleNumber - 1).id());
                } else {
                    duplicateExampleCount++;
                }
            }
            if (!uniqueExampleIds.isEmpty()) {
                groups.add(new SenseGroup(proposedGroup.description().trim(),
                        proposedGroup.existingSenseId(), List.copyOf(uniqueExampleIds)));
            }
        }

        if (duplicateExampleCount > 0) {
            log.warn("AI clustering contained duplicate example assignments: reviewId={}, modelId={}, duplicates={}; kept first assignment",
                    reviewId, request.modelId(), duplicateExampleCount);
        }

        return groups;
    }

    private List<ModelExample> loadExamplesForClustering(Review review) {
        return exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId()).stream()
                .filter(example -> example.getSense() == null || Objects.equals(
                        example.getSense().getTargetEntryId(), review.getMatchedEntryId()))
                .map(example -> new ModelExample(example.getId(), CorpusTextFormatter.format(example.getCitation()))).toList();
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
