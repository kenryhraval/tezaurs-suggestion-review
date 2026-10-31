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
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import lv.tezaurs.suggestions.tezaurs.service.TezaursRestrictionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SenseDraftService {
    private final ReviewSenseRepository senseRepository;
    private final ReviewCaseRepository caseRepository;
    private final TezaursSenseRepository tezaursSenseRepository;
    private final TezaursFlagService flagService;
    private final TezaursRestrictionService restrictionService;

    @Transactional(readOnly = true)
    public List<SenseDraftResponse> findAll(UUID caseId) {
        requireCase(caseId);
        return senseRepository.findAllByCaseIdOrderByOrderNoAscIdAsc(caseId).stream()
                .map(SenseDraftResponse::from).toList();
    }

    @Transactional
    public SaveSenseDraftsResponse save(UUID caseId, SaveSenseDraftsRequest request) {
        ReviewCase reviewCase = requireCase(caseId);
        if (reviewCase.getTezaursStatus() != TezaursStatus.NOT_FOUND
                && reviewCase.getTezaursStatus() != TezaursStatus.MEANING_NOT_FOUND) {
            throw new ConflictException("Sense drafts require an absent entry or meaning");
        }

        validate(request, reviewCase);
        Map<UUID, ReviewSense> existing = new HashMap<>();
        senseRepository.findAllByCaseIdOrderByOrderNoAscIdAsc(caseId)
                .forEach(sense -> existing.put(sense.getId(), sense));
        List<ReviewSense> senses = new ArrayList<>();
        for (int index = 0; index < request.senses().size(); index++) {
            var item = request.senses().get(index);
            ReviewSense sense = item.id() == null
                    ? new ReviewSense(caseId) : existing.remove(item.id());
            if (sense == null) {
                throw new ConflictException("A sense does not belong to this review");
            }
            sense.update(item.gloss().trim(), item.parentDraftSenseId(), item.parentTezaursSenseId(),
                    index + 1, item.flags(), item.structuralRestrictions());
            senses.add(sense);
        }
        senseRepository.deleteAll(existing.values());
        senses = senseRepository.saveAll(senses);
        reviewCase.markSenseDraftReady();
        return new SaveSenseDraftsResponse(
                senses.stream().map(SenseDraftResponse::from).toList(), ReviewResponse.from(reviewCase));
    }

    private void validate(SaveSenseDraftsRequest request, ReviewCase reviewCase) {
        Set<UUID> submittedIds = new HashSet<>();
        Map<UUID, UUID> parents = new HashMap<>();
        for (var sense : request.senses()) {
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
            flagService.validate("S", sense.flags());
            restrictionService.validate(sense.structuralRestrictions());
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
    }

    private ReviewCase requireCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }
}
