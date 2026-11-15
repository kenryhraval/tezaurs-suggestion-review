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
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import lv.tezaurs.suggestions.tezaurs.service.TezaursRestrictionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SenseDraftService {
    private static final String SENSE_SCOPE = "S";

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
        if (reviewCase.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Sense drafts can only be edited while the case is in progress");
        }
        if (reviewCase.getType() == CaseType.ENTRY && !reviewCase.canEditEntryDraft()) {
            throw new ConflictException("Confirm word usage before editing a new entry's senses");
        }
        if (reviewCase.getType() != CaseType.ENTRY
                && reviewCase.getType() != CaseType.SENSE) {
            throw new ConflictException("Sense drafts require an absent entry or meaning");
        }

        Map<UUID, ReviewSense> savedDrafts = new HashMap<>();
        senseRepository.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(caseId, reviewCase.getMatchedEntryId())
                .forEach(sense -> savedDrafts.put(sense.getId(), sense));
        validate(request, reviewCase, savedDrafts);
        List<ReviewSense> senses = new ArrayList<>();
        for (int index = 0; index < request.senses().size(); index++) {
            var senseRequest = request.senses().get(index);
            ReviewSense sense = senseRequest.id() == null
                    ? new ReviewSense(caseId, reviewCase.getMatchedEntryId()) : savedDrafts.remove(senseRequest.id());
            if (sense == null) {
                throw new ConflictException("A sense does not belong to this review");
            }
            sense.update(senseRequest.gloss().trim(), senseRequest.parentDraftSenseId(), senseRequest.parentTezaursSenseId(),
                    index + 1, senseRequest.flags(), senseRequest.structuralRestrictions());
            senses.add(sense);
        }
        senseRepository.deleteAll(savedDrafts.values());
        senses = senseRepository.saveAll(senses);
        return new SaveSenseDraftsResponse(
                senses.stream().map(SenseDraftResponse::from).toList(), completionService.response(reviewCase));
    }

    private void validate(SaveSenseDraftsRequest request, ReviewCase reviewCase, Map<UUID, ReviewSense> savedDrafts) {
        Set<UUID> submittedIds = new HashSet<>();
        Map<UUID, UUID> draftParents = new HashMap<>();
        for (var sense : request.senses()) {
            ReviewSense previous = savedDrafts.get(sense.id());
            if (sense.gloss().isBlank()) {
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
                draftParents.put(sense.id(), sense.parentDraftSenseId());
            }
            if (sense.parentTezaursSenseId() != null
                    && (reviewCase.getMatchedEntryId() == null
                    || !tezaursSenseRepository.existsByIdAndEntryId(
                            sense.parentTezaursSenseId(), reviewCase.getMatchedEntryId()))) {
                throw new ConflictException("The selected parent sense does not belong to the matched entry");
            }
            Map<String, Object> previousFlags = previous == null ? Map.of() : previous.getFlags();
            Map<String, Object> previousRestrictions = previous == null ? null : previous.getStructuralRestrictions();
            flagService.validateChanges(SENSE_SCOPE, sense.flags(), previousFlags);
            if (!java.util.Objects.equals(previousRestrictions, sense.structuralRestrictions())) {
                restrictionService.validate(sense.structuralRestrictions());
            }
        }
        for (var sense : request.senses()) {
            if (sense.parentDraftSenseId() != null
                    && !submittedIds.contains(sense.parentDraftSenseId())) {
                throw new ConflictException("A parent draft sense must be included in the same draft");
            }
        }
        for (UUID senseId : draftParents.keySet()) {
            Set<UUID> ancestors = new HashSet<>();
            UUID ancestorId = senseId;
            while (ancestorId != null) {
                if (!ancestors.add(ancestorId)) {
                    throw new ConflictException("Sense hierarchy cannot contain a cycle");
                }
                ancestorId = draftParents.get(ancestorId);
            }
        }
    }

    private ReviewCase requireCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }
}
