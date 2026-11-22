package lv.tezaurs.tip.sense.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.sense.dto.SenseGroup;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.sense.dto.SaveSenseRequest;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.tip.tezaurs.service.TezaursFlagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SenseService {
    private final SenseRepository senseRepository;
    private final ReviewRepository reviewRepository;
    private final TezaursSenseRepository tezaursSenseRepository;
    private final TezaursFlagService flagService;
    private final ExampleRepository exampleRepository;

    @Transactional
    public List<SenseResponse> findAllForReview(UUID reviewId) {
        Review review = lockReview(reviewId);
        List<Sense> senses = findSenses(review);
        if (review.getStatus() == ReviewStatus.IN_PROGRESS) {
            ensureExistingSenseReferences(review, senses);
        }

        return senses.stream().map(SenseResponse::from).toList();
    }

    @Transactional
    public SenseResponse create(UUID reviewId, SaveSenseRequest request) {
        Review review = requireEditableReview(reviewId);
        List<Sense> senses = findSenses(review);
        Sense sense = new Sense(reviewId, review.getMatchedEntryId());
        sense.setOrderNo(nextOrderNo(senses));

        return saveSense(review, sense, request, senses);
    }

    @Transactional
    public SenseResponse update(UUID reviewId, UUID senseId, SaveSenseRequest request) {
        Review review = requireEditableReview(reviewId);
        List<Sense> senses = findSenses(review);
        Sense sense = requireSense(senseId, senses);
        if (sense.getExistingSenseId() != null) {
            throw new ConflictException("Esošas Tēzaura nozīmes datus labo Tēzaura redaktorā.");
        }

        return saveSense(review, sense, request, senses);
    }

    @Transactional
    public void delete(UUID reviewId, UUID senseId) {
        Review review = requireEditableReview(reviewId);
        List<Sense> senses = findSenses(review);
        Sense sense = requireSense(senseId, senses);
        if (sense.getExistingSenseId() != null) {
            throw new ConflictException("Esošu Tēzaura nozīmi šeit nevar dzēst.");
        }
        if (senses.stream().anyMatch(child -> senseId.equals(child.getParentDraftSenseId()))) {
            throw new ConflictException("Pirms pamatnozīmes dzēšanas pārvietojiet vai dzēsiet tās apakšnozīmes.");
        }

        List.copyOf(sense.getExamples()).forEach(example -> example.reassignTo(null));
        senseRepository.delete(sense);
    }

    private SenseResponse saveSense(Review review, Sense sense, SaveSenseRequest request, List<Sense> senses) {
        if (request.gloss().isBlank()) {
            throw new ConflictException("Nozīmei jānorāda skaidrojums.");
        }
        boolean approved = request.approved() == null ? sense.isApproved() : request.approved();
        validateParent(review, sense.getId(), request, senses, approved);
        if (request.flags() != null) {
            flagService.validate("S", request.flags());
        }
        if (approved && review.getType() == ReviewType.ENTRY && review.getUsageDecision() != UsageDecision.USED) {
            throw new ConflictException("Pirms nozīmes apstiprināšanas apstipriniet vārda lietojumu.");
        }
        if (!approved && senses.stream().anyMatch(child -> child.isApprovedDraft()
                && sense.getId().equals(child.getParentDraftSenseId()))) {
            throw new ConflictException("Vispirms atceliet šīs pamatnozīmes apakšnozīmju apstiprinājumu.");
        }

        sense.setGloss(request.gloss().trim());
        sense.setParentDraftSenseId(request.parentDraftSenseId());
        sense.setParentTezaursSenseId(request.parentTezaursSenseId());
        sense.setApproved(approved);
        if (request.flags() != null) {
            sense.setFlags(new HashMap<>(request.flags()));
        }

        return SenseResponse.from(senseRepository.save(sense));
    }

    private void validateParent(Review review, UUID senseId, SaveSenseRequest request, List<Sense> senses, boolean approved) {
        if (request.parentDraftSenseId() != null && request.parentTezaursSenseId() != null) {
            throw new ConflictException("Nozīmi var pakārtot tikai vienai citai nozīmei.");
        }
        if (senseId.equals(request.parentDraftSenseId())) {
            throw new ConflictException("Nozīmi nevar pakārtot pašai sev.");
        }
        if (request.parentDraftSenseId() != null) {
            Sense parent = requireSense(request.parentDraftSenseId(), senses);
            if (parent.getParentDraftSenseId() != null || parent.getParentTezaursSenseId() != null
                    || (parent.getExistingSenseId() != null && !tezaursSenseRepository.existsByIdAndEntryIdAndParentSenseIdIsNull(
                            parent.getExistingSenseId(), review.getMatchedEntryId()))) {
                throw new ConflictException("Apakšnozīmi var pakārtot tikai pamatnozīmei. Atļauti divi nozīmju līmeņi.");
            }
            if (approved && !parent.isApprovedDraft() && parent.getExistingSenseId() == null) {
                throw new ConflictException("Vispirms apstipriniet virsnozīmi.");
            }
        }
        if (request.parentTezaursSenseId() != null && (review.getMatchedEntryId() == null
                || !tezaursSenseRepository.existsByIdAndEntryIdAndParentSenseIdIsNull(
                        request.parentTezaursSenseId(), review.getMatchedEntryId()))) {
            throw new ConflictException("Izvēlētajai virsnozīmei jābūt atlasītā Tēzaura šķirkļa pamatnozīmei.");
        }
        if ((request.parentDraftSenseId() != null || request.parentTezaursSenseId() != null)
                && senses.stream().anyMatch(child -> senseId.equals(child.getParentDraftSenseId()))) {
            throw new ConflictException("Pamatnozīmi ar apakšnozīmēm nevar pārvērst par apakšnozīmi.");
        }
    }

    @Transactional
    public List<SenseResponse> addGroupedSenses(UUID reviewId, Integer expectedEntryId,
            List<SenseGroup> groups) {
        Review review = requireEditableReview(reviewId);
        if (!Objects.equals(review.getMatchedEntryId(), expectedEntryId)) {
            throw new ConflictException("Pārbaudes Tēzaurā izvēle ir mainījusies. Atkārtojiet grupēšanu.");
        }
        List<Sense> senses = findSenses(review);
        Map<Integer, Sense> existingReferences = new HashMap<>();
        senses.stream().filter(sense -> sense.getExistingSenseId() != null)
                .forEach(sense -> existingReferences.put(sense.getExistingSenseId(), sense));
        Map<UUID, Example> examples = new HashMap<>();
        exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(reviewId)
                .forEach(example -> examples.put(example.getId(), example));
        Set<UUID> groupedExampleIds = new HashSet<>();
        for (var group : groups) {
            if (group.existingSenseId() != null && !existingReferences.containsKey(group.existingSenseId())) {
                throw new ConflictException("Izvēlētā Tēzaura nozīme vairs nav pieejama. Atkārtojiet grupēšanu.");
            }
            for (UUID exampleId : group.exampleIds()) {
                Example example = examples.get(exampleId);
                if (example == null || !groupedExampleIds.add(exampleId)
                        || (example.getSense() != null && !Objects.equals(
                                example.getSense().getTargetEntryId(), expectedEntryId))) {
                    throw new ConflictException("Piemēri mainījušies vai nepieder šai izskatīšanai. Atkārtojiet grupēšanu.");
                }
            }
        }

        int orderNo = nextOrderNo(senses);
        for (var group : groups) {
            Sense target = existingReferences.get(group.existingSenseId());
            if (target == null) {
                target = senseRepository.save(new Sense(UUID.randomUUID(), reviewId, expectedEntryId,
                        group.description(), null, orderNo++));
                senses.add(target);
            }
            for (UUID exampleId : group.exampleIds()) {
                examples.get(exampleId).reassignTo(target);
            }
        }

        return senses.stream().map(SenseResponse::from).toList();
    }

    private void ensureExistingSenseReferences(Review review, List<Sense> senses) {
        if (review.getMatchedEntryId() == null) return;
        Set<Integer> referencedIds = new HashSet<>();
        senses.forEach(sense -> referencedIds.add(sense.getExistingSenseId()));
        int orderNo = nextOrderNo(senses);
        for (var existing : tezaursSenseRepository.findOrderedByEntryId(review.getMatchedEntryId())) {
            if (referencedIds.add(existing.getId())) {
                String gloss = existing.getGloss();
                if (gloss == null || gloss.isBlank()) gloss = "Skaidrojums nav norādīts.";
                senses.add(senseRepository.save(new Sense(UUID.randomUUID(), review.getId(), review.getMatchedEntryId(),
                        gloss, existing.getId(), orderNo++)));
            }
        }
    }

    private List<Sense> findSenses(Review review) {
        return new ArrayList<>(senseRepository.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(
                review.getId(), review.getMatchedEntryId()));
    }

    private int nextOrderNo(List<Sense> senses) {
        return senses.stream().mapToInt(Sense::getOrderNo).max().orElse(0) + 1;
    }

    private Sense requireSense(UUID senseId, List<Sense> senses) {
        return senses.stream().filter(sense -> sense.getId().equals(senseId)).findFirst()
                .orElseThrow(() -> new ConflictException("Nozīme nepieder šī perēkļa pašreizējai izskatīšanai."));
    }

    private Review requireEditableReview(UUID reviewId) {
        Review review = lockReview(reviewId);
        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }
        if (review.getType() == ReviewType.UNKNOWN) {
            throw new ConflictException("Vispirms veiciet pārbaudi Tēzaurā.");
        }
        return review;
    }

    private Review lockReview(UUID reviewId) {
        return reviewRepository.findLockedById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
    }
}
