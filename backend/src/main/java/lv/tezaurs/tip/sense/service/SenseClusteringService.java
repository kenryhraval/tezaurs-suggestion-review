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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SenseClusteringService {
    private final ReviewRepository reviewRepository;
    private final ExampleRepository exampleRepository;
    private final ModelService modelService;
    private final SenseService senseService;

    private record ModelExample(UUID id, String citation) {}
    private record NumberedExample(int id, String citation) {}
    private record NumberedSense(int id, String description, Integer parentId, boolean inDictionary) {}

    public List<SenseResponse> regroupSenses(UUID reviewId, ClusterSensesRequest request) {
        Review review = requireEditableReview(reviewId);
        List<ModelExample> examples = loadExamplesForClustering(review);
        if (examples.isEmpty()) {
            throw new ConflictException("Atlasiet vismaz vienu neapstiprinātu piemēru, ko grupēt.");
        }

        List<NumberedExample> numberedExamples = new ArrayList<>();
        for (int index = 0; index < examples.size(); index++) {
            numberedExamples.add(new NumberedExample(index + 1, examples.get(index).citation()));
        }

        List<SenseResponse> savedSenses = senseService.findAllForReview(reviewId);
        List<NumberedSense> numberedSenses = new ArrayList<>();
        for (int index = 0; index < savedSenses.size(); index++) {
            SenseResponse sense = savedSenses.get(index);
            Integer parentNumber = null;
            for (int parentIndex = 0; parentIndex < savedSenses.size(); parentIndex++) {
                if (isChildOf(sense, savedSenses.get(parentIndex))) {
                    parentNumber = parentIndex + 1;
                    break;
                }
            }
            numberedSenses.add(new NumberedSense(index + 1, sense.gloss(), parentNumber, sense.existingSenseId() != null));
        }

        var modelResponse = modelService.requestStructuredResponse("clustering", request.modelId(), request.additionalInstructions(),
                Map.of("term", review.getReviewedTerm(), "examples", numberedExamples, "senses", numberedSenses), ClusteredSenses.class);
        List<SenseGroup> validatedGroups = validateGroups(reviewId, request, modelResponse, examples, savedSenses);

        return senseService.addGroupedSenses(reviewId, review.getMatchedEntryId(), validatedGroups);
    }

    private List<SenseGroup> validateGroups(UUID reviewId, ClusterSensesRequest request,
            ClusteredSenses modelResponse, List<ModelExample> examples, List<SenseResponse> savedSenses) {
        if (modelResponse == null || modelResponse.groups() == null) {
            throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Missing groups list");
        }

        Set<Integer> groupedExampleNumbers = new HashSet<>();
        List<SenseGroup> groups = new ArrayList<>();

        for (var proposedGroup : modelResponse.groups()) {
            if (proposedGroup == null) {
                throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Null group");
            }
            SenseResponse target = resolveSenseNumber(reviewId, request.modelId(), proposedGroup.senseId(), savedSenses);
            if (target == null) validateDescription(reviewId, request.modelId(), proposedGroup.description());
            List<UUID> exampleIds = resolveExampleNumbers(reviewId, request.modelId(), proposedGroup.exampleIds(),
                    examples, groupedExampleNumbers);
            List<SenseGroup.Subsense> subsenses = new ArrayList<>();
            if (proposedGroup.subsenses() != null) {
                for (var subsense : proposedGroup.subsenses()) {
                    if (subsense == null) {
                        throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Null subsense");
                    }
                    List<UUID> subsenseExamples = resolveExampleNumbers(reviewId, request.modelId(), subsense.exampleIds(),
                            examples, groupedExampleNumbers);
                    SenseResponse child = resolveSenseNumber(reviewId, request.modelId(), subsense.senseId(), savedSenses);
                    if (child == null) validateDescription(reviewId, request.modelId(), subsense.description());
                    if (child != null && (target == null || !isChildOf(child, target))) {
                        throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "Existing sense hierarchy cannot be changed");
                    }
                    subsenses.add(new SenseGroup.Subsense(child == null ? subsense.description().trim() : child.gloss(),
                            child == null ? null : child.id(), subsenseExamples));
                }
            }
            if (!subsenses.isEmpty() && target != null
                    && target.parentSenseId() != null) {
                throw rejectedModelResponse(reviewId, request.modelId(), "clustering", "A subsense cannot have subsenses");
            }
            groups.add(new SenseGroup(target == null ? proposedGroup.description().trim() : target.gloss(), target == null ? null : target.id(),
                    exampleIds, List.copyOf(subsenses)));
        }

        return groups;
    }

    private SenseResponse resolveSenseNumber(UUID reviewId, String modelId, Integer number, List<SenseResponse> senses) {
        if (number == null) return null;
        if (number < 1 || number > senses.size()) {
            throw rejectedModelResponse(reviewId, modelId, "clustering", "Group refers to an unknown existing sense");
        }
        return senses.get(number - 1);
    }

    private boolean isChildOf(SenseResponse child, SenseResponse parent) {
        return parent.id().equals(child.parentSenseId());
    }

    private List<UUID> resolveExampleNumbers(UUID reviewId, String modelId, List<Integer> numbers,
            List<ModelExample> examples, Set<Integer> groupedNumbers) {
        if (numbers == null) return List.of();

        List<UUID> exampleIds = new ArrayList<>();
        int duplicates = 0;
        for (Integer number : numbers) {
            if (number == null || number < 1 || number > examples.size()) {
                throw rejectedModelResponse(reviewId, modelId, "clustering",
                        "Group contains example numbers not supplied in the request");
            }
            if (groupedNumbers.add(number)) {
                exampleIds.add(examples.get(number - 1).id());
            } else {
                duplicates++;
            }
        }
        if (duplicates > 0) {
            log.warn("AI clustering contained duplicate example assignments: reviewId={}, modelId={}, duplicates={}; kept first assignment",
                    reviewId, modelId, duplicates);
        }
        return List.copyOf(exampleIds);
    }

    private void validateDescription(UUID reviewId, String modelId, String description) {
        if (!hasNonBlankTextWithinLimit(description, 10000)) {
            throw rejectedModelResponse(reviewId, modelId, "clustering", "Group description is blank or exceeds 10000 characters");
        }
    }

    private List<ModelExample> loadExamplesForClustering(Review review) {
        return exampleRepository.findForReview(review.getId(), review.getMatchedEntryId()).stream()
                .filter(example -> !example.isApproved())
                .filter(example -> example.getSense() == null || Objects.equals(
                        example.getSense().getTargetEntryId(), review.getMatchedEntryId()))
                .map(example -> new ModelExample(example.getId(), CorpusTextFormatter.format(example.getCitation()))).toList();
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
