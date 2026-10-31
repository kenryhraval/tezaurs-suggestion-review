package lv.tezaurs.suggestions.sense.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.sense.dto.SaveSenseDraftsRequest;
import lv.tezaurs.suggestions.sense.dto.SaveSenseDraftsResponse;
import lv.tezaurs.suggestions.sense.dto.SenseDraftResponse;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.service.ReviewCompletionService;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSense;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import lv.tezaurs.suggestions.tezaurs.service.TezaursRestrictionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SenseDraftService {
    private final ReviewCompletionService completionService;
    private final ReviewSenseRepository senseRepository;
    private final ReviewCaseRepository caseRepository;
    private final TezaursSenseRepository tezaursSenseRepository;
    private final TezaursFlagService flagService;
    private final TezaursRestrictionService restrictionService;

    @Transactional(readOnly = true)
    public List<SenseDraftResponse> findAll(UUID caseId) {
        ReviewCase review = requireCase(caseId);
        return senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(caseId, review.getMatchedEntryId()).stream()
                .map(SenseDraftResponse::from).toList();
    }

    @Transactional
    public SaveSenseDraftsResponse save(UUID caseId, SaveSenseDraftsRequest request) {
        ReviewCase reviewCase = requireCase(caseId);
        if (reviewCase.getStatus() != SuggestionStatus.IN_PROGRESS) {
            throw new ConflictException("Sense drafts can only be edited while the case is in progress");
        }
        if (reviewCase.getTezaursStatus() != TezaursStatus.NOT_FOUND
                && reviewCase.getTezaursStatus() != TezaursStatus.MEANING_NOT_FOUND
                && reviewCase.getTezaursStatus() != TezaursStatus.MEANING_NEEDS_EDITING) {
            throw new ConflictException("Sense drafts require an absent entry or meaning");
        }

        Map<UUID, ReviewSense> existing = new HashMap<>();
        senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(caseId, reviewCase.getMatchedEntryId())
                .forEach(sense -> existing.put(sense.getId(), sense));
        Map<Integer, TezaursSense> originals = new HashMap<>();
        if (reviewCase.getTezaursStatus() == TezaursStatus.MEANING_NEEDS_EDITING) {
            tezaursSenseRepository.findOrderedByEntryId(reviewCase.getMatchedEntryId())
                    .forEach(sense -> originals.put(sense.getId(), sense));
        }
        validate(request, reviewCase, existing, originals);
        List<ReviewSense> senses = new ArrayList<>();
        for (int index = 0; index < request.senses().size(); index++) {
            var item = request.senses().get(index);
            ReviewSense sense = item.id() == null
                    ? new ReviewSense(caseId, reviewCase.getMatchedEntryId()) : existing.remove(item.id());
            if (sense == null) {
                throw new ConflictException("A sense does not belong to this review");
            }
            if (item.id() == null && item.sourceSenseId() != null) {
                sense.recordSource(item.sourceSenseId());
            }
            sense.update(item.gloss().trim(), item.parentDraftSenseId(), item.parentTezaursSenseId(),
                    index + 1, item.flags(), item.structuralRestrictions());
            senses.add(sense);
        }
        senseRepository.deleteAll(existing.values().stream()
                .filter(sense -> reviewCase.getTezaursStatus() == TezaursStatus.MEANING_NEEDS_EDITING || sense.getSourceSenseId() == null)
                .toList());
        senses = senseRepository.saveAll(senses);
        return new SaveSenseDraftsResponse(
                senses.stream().map(SenseDraftResponse::from).toList(), completionService.response(reviewCase));
    }

    private void validate(SaveSenseDraftsRequest request, ReviewCase reviewCase, Map<UUID, ReviewSense> existing,
                          Map<Integer, TezaursSense> originals) {
        Set<UUID> submittedIds = new HashSet<>();
        Set<Integer> submittedSources = new HashSet<>();
        Map<UUID, UUID> parents = new HashMap<>();
        for (var sense : request.senses()) {
            ReviewSense previous = existing.get(sense.id());
            Integer sourceId = previous == null ? sense.sourceSenseId() : previous.getSourceSenseId();
            TezaursSense original = originals.get(sourceId);
            if (sourceId != null && !submittedSources.add(sourceId)) {
                throw new ConflictException("The same original sense cannot be submitted twice");
            }
            if (previous != null && sense.sourceSenseId() != null && !sense.sourceSenseId().equals(sourceId)) {
                throw new ConflictException("The source of an existing sense draft cannot be changed");
            }
            if (previous == null && sourceId != null && original == null) {
                throw new ConflictException("The original sense does not belong to the selected entry");
            }
            if (sense.id() == null && sourceId != null && existing.values().stream()
                    .anyMatch(draft -> sourceId.equals(draft.getSourceSenseId()))) {
                throw new ConflictException("An edit for this original sense already exists; reload the review");
            }
            if (reviewCase.getTezaursStatus() != TezaursStatus.MEANING_NEEDS_EDITING
                    && sourceId != null) {
                throw new ConflictException("Editing existing senses has not been selected");
            }
            if (sense.gloss().isBlank() && sourceId == null) {
                throw new ConflictException("A new sense must have a definition");
            }
            if (sense.id() != null && !submittedIds.add(sense.id())) {
                throw new ConflictException("The same sense cannot be submitted twice");
            }
            if (sense.parentDraftSenseId() != null && sense.parentTezaursSenseId() != null) {
                throw new ConflictException("A sense can only have one parent");
            }
            if (sense.id() != null && sense.id().equals(sense.parentDraftSenseId())) {
                throw new ConflictException("A sense cannot be its own parent");
            }
            if (sense.id() != null && sense.parentDraftSenseId() != null) {
                parents.put(sense.id(), sense.parentDraftSenseId());
            }
            if (sense.parentTezaursSenseId() != null
                    && (reviewCase.getMatchedEntryId() == null
                    || !tezaursSenseRepository.existsByIdAndEntryId(
                            sense.parentTezaursSenseId(), reviewCase.getMatchedEntryId()))) {
                throw new ConflictException("The selected parent sense does not belong to the matched entry");
            }
            var gram = original == null ? Map.<String, Object>of() : object(object(original.getData()).get("Gram"));
            var oldFlags = previous == null ? object(gram.get("Flags")) : previous.getFlags();
            var oldRestrictions = previous == null ? gram.get("StructuralRestrictions") : previous.getStructuralRestrictions();
            if (oldRestrictions instanceof List<?> values) oldRestrictions = Map.of("AND", values);
            flagService.validateChanges("S", sense.flags(), oldFlags);
            if (!java.util.Objects.equals(oldRestrictions, sense.structuralRestrictions())) {
                restrictionService.validate(sense.structuralRestrictions());
            }
        }
        for (var sense : request.senses()) {
            if (sense.parentDraftSenseId() != null
                    && !submittedIds.contains(sense.parentDraftSenseId())) {
                throw new ConflictException("A parent draft sense must be included in the same draft");
            }
        }
        for (UUID senseId : parents.keySet()) {
            Set<UUID> ancestors = new HashSet<>();
            UUID current = senseId;
            while (current != null) {
                if (!ancestors.add(current)) {
                    throw new ConflictException("Sense hierarchy cannot contain a cycle");
                }
                current = parents.get(current);
            }
        }
        validateSourceHierarchy(request, existing, originals);
    }

    private void validateSourceHierarchy(SaveSenseDraftsRequest request, Map<UUID, ReviewSense> existing,
                                         Map<Integer, TezaursSense> originals) {
        Map<String, String> parents = new HashMap<>();
        originals.values().forEach(sense -> parents.put("source:" + sense.getId(),
                sense.getParentSenseId() == null ? null : "source:" + sense.getParentSenseId()));
        Map<UUID, String> draftKeys = new HashMap<>();
        for (var item : request.senses()) {
            var previous = existing.get(item.id());
            Integer sourceId = previous == null ? item.sourceSenseId() : previous.getSourceSenseId();
            if (item.id() != null) draftKeys.put(item.id(), sourceId == null ? "draft:" + item.id() : "source:" + sourceId);
        }
        for (var item : request.senses()) {
            String key = draftKeys.get(item.id());
            if (item.id() == null && item.sourceSenseId() != null) key = "source:" + item.sourceSenseId();
            String parent = null;
            if (item.parentDraftSenseId() != null) parent = draftKeys.get(item.parentDraftSenseId());
            else if (item.parentTezaursSenseId() != null) parent = "source:" + item.parentTezaursSenseId();
            if (key != null) parents.put(key, parent);
        }
        for (String key : parents.keySet()) {
            Set<String> ancestors = new HashSet<>();
            while (key != null) {
                if (!ancestors.add(key)) throw new ConflictException("Sense hierarchy cannot contain a cycle");
                key = parents.get(key);
            }
        }
    }

    private static Map<String, Object> object(Object value) {
        Map<String, Object> result = new HashMap<>();
        if (value instanceof Map<?, ?> fields) fields.forEach((key, item) -> {
            if (key instanceof String name) result.put(name, item);
        });
        return result;
    }

    private ReviewCase requireCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }
}
