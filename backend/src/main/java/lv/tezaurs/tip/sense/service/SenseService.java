package lv.tezaurs.tip.sense.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
import lv.tezaurs.tip.sense.dto.GeneratedDefinition;
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

    @Transactional(readOnly = true)
    public List<SenseResponse> findAllForReview(UUID reviewId) {
        Review review = getReviewOrThrow(reviewId);
        List<Sense> senses = findSenses(review);

        return senses.stream().map(SenseResponse::from).toList();
    }

    @Transactional
    public void initializeForSelection(UUID reviewId, boolean meaningAlreadyExists) {
        Review review = requireEditableReview(reviewId);
        List<Sense> senses = findSenses(review);
        ensureExistingSenseReferences(review, senses);
        if (meaningAlreadyExists || senses.stream().anyMatch(sense -> sense.getExistingSenseId() == null)) {
            return;
        }

        Set<String> submittedDefinitions = new LinkedHashSet<>();
        review.getSuggestions().stream()
                .sorted(Comparator.comparing(link -> link.getSourceSuggestion().getId()))
                .forEach(link -> {
                    String gloss = link.getSourceSuggestion().getGloss();
                    if (gloss != null && !gloss.isBlank()) {
                        submittedDefinitions.add(gloss.trim());
                    }
                });

        int orderNo = nextOrderNo(senses);
        for (String gloss : submittedDefinitions) {
            Sense sense = new Sense(reviewId, review.getMatchedEntryId());
            sense.setGloss(gloss);
            sense.setOrderNo(orderNo++);
            senseRepository.save(sense);
        }
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
        if (senses.stream().anyMatch(child -> senseId.equals(child.getParentSenseId()))) {
            throw new ConflictException("Pirms pamatnozīmes dzēšanas pārvietojiet vai dzēsiet tās apakšnozīmes.");
        }

        List.copyOf(sense.getExamples()).forEach(example -> example.reassignTo(null));
        senseRepository.delete(sense);
    }

    @Transactional
    public SenseResponse merge(UUID reviewId, UUID sourceSenseId, UUID targetSenseId) {
        Review review = requireEditableReview(reviewId);
        List<Sense> senses = findSenses(review);
        Sense source = requireSense(sourceSenseId, senses);
        Sense target = requireSense(targetSenseId, senses);
        if (source.getId().equals(target.getId())) {
            throw new ConflictException("Nozīmi nevar apvienot pašu ar sevi.");
        }
        if (source.getExistingSenseId() != null) {
            throw new ConflictException("Esošu Tēzaura nozīmi šeit nevar apvienot ar citu nozīmi.");
        }

        List<Sense> children = senses.stream()
                .filter(sense -> sourceSenseId.equals(sense.getParentSenseId())).toList();
        if (!children.isEmpty()) {
            if (target.getParentSenseId() != null) {
                throw new ConflictException("Nozīmi ar apakšnozīmēm var apvienot tikai ar pamatnozīmi.");
            }
            if (target.getExistingSenseId() == null && !target.isApproved()
                    && children.stream().anyMatch(Sense::isApproved)) {
                throw new ConflictException("Vispirms apstipriniet mērķa pamatnozīmi, lai saglabātu apakšnozīmju apstiprinājumus.");
            }
        }

        for (Sense child : children) {
            child.setParentSenseId(targetSenseId);
        }
        List.copyOf(source.getExamples()).forEach(example -> example.reassignTo(target));
        senseRepository.delete(source);

        return SenseResponse.from(target);
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
                && sense.getId().equals(child.getParentSenseId()))) {
            throw new ConflictException("Vispirms atceliet šīs pamatnozīmes apakšnozīmju apstiprinājumu.");
        }

        sense.setGloss(request.gloss().trim());
        sense.setParentSenseId(request.parentSenseId());
        sense.setApproved(approved);
        if (request.flags() != null) {
            sense.setFlags(new HashMap<>(request.flags()));
        }

        return SenseResponse.from(senseRepository.save(sense));
    }

    private void validateParent(Review review, UUID senseId, SaveSenseRequest request, List<Sense> senses, boolean approved) {
        if (senseId.equals(request.parentSenseId())) {
            throw new ConflictException("Nozīmi nevar pakārtot pašai sev.");
        }
        if (request.parentSenseId() == null) return;

        Sense parent = requireSense(request.parentSenseId(), senses);
        if (parent.getParentSenseId() != null) {
            throw new ConflictException("Apakšnozīmi var pakārtot tikai pamatnozīmei. Atļauti divi nozīmju līmeņi.");
        }
        if (approved && !parent.isApprovedDraft() && parent.getExistingSenseId() == null) {
            throw new ConflictException("Vispirms apstipriniet virsnozīmi.");
        }
        if (senses.stream().anyMatch(child -> senseId.equals(child.getParentSenseId()))) {
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
        Map<UUID, Sense> savedSenses = new HashMap<>();
        senses.forEach(sense -> savedSenses.put(sense.getId(), sense));
        Map<UUID, Example> examples = new HashMap<>();
        exampleRepository.findForReview(reviewId, review.getMatchedEntryId())
                .forEach(example -> examples.put(example.getId(), example));
        Set<UUID> groupedExampleIds = new HashSet<>();
        for (var group : groups) {
            Sense target = null;
            if (group.senseId() != null) {
                target = requireSense(group.senseId(), senses);
                if (!group.subsenses().isEmpty() && target.getParentSenseId() != null) {
                    throw new ConflictException("Apakšnozīmi var pakārtot tikai pamatnozīmei.");
                }
            }
            for (var subsense : group.subsenses()) {
                if (subsense.senseId() != null) {
                    Sense child = requireSense(subsense.senseId(), senses);
                    boolean sameParent = target != null && target.getId().equals(child.getParentSenseId());
                    if (!sameParent) {
                        throw new ConflictException("Esošas nozīmes pakārtojums ir mainījies. Atkārtojiet grupēšanu.");
                    }
                }
            }
            List<UUID> groupExampleIds = new ArrayList<>(group.exampleIds());
            group.subsenses().forEach(subsense -> groupExampleIds.addAll(subsense.exampleIds()));
            for (UUID exampleId : groupExampleIds) {
                Example example = examples.get(exampleId);
                if (example == null || example.isApproved() || !groupedExampleIds.add(exampleId)
                        || (example.getSense() != null && !Objects.equals(
                                example.getSense().getTargetEntryId(), expectedEntryId))) {
                    throw new ConflictException("Piemēri mainījušies vai nepieder šim perēklim. Atkārtojiet grupēšanu.");
                }
            }
        }

        int orderNo = nextOrderNo(senses);
        for (var group : groups) {
            Sense target = savedSenses.get(group.senseId());
            if (target == null) {
                target = senseRepository.save(new Sense(UUID.randomUUID(), reviewId, expectedEntryId,
                        group.description(), null, orderNo++));
                senses.add(target);
            }
            for (UUID exampleId : group.exampleIds()) {
                examples.get(exampleId).reassignTo(target);
            }
            for (var subsense : group.subsenses()) {
                Sense child = savedSenses.get(subsense.senseId());
                if (child == null) {
                    child = new Sense(UUID.randomUUID(), reviewId, expectedEntryId, subsense.description(), null, orderNo++);
                    child.setParentSenseId(target.getId());
                    senseRepository.save(child);
                    senses.add(child);
                }
                for (UUID exampleId : subsense.exampleIds()) {
                    examples.get(exampleId).reassignTo(child);
                }
            }
        }

        return senses.stream().map(SenseResponse::from).toList();
    }

    @Transactional
    public List<SenseResponse> saveGeneratedDefinitions(UUID reviewId, Integer expectedEntryId, List<GeneratedDefinition> definitions) {
        Review review = requireEditableReview(reviewId);
        if (!Objects.equals(review.getMatchedEntryId(), expectedEntryId)) {
            throw new ConflictException("Pārbaudes Tēzaurā izvēle ir mainījusies. Atkārtojiet skaidrojumu ģenerēšanu.");
        }
        List<Sense> senses = findSenses(review);
        for (GeneratedDefinition definition : definitions) {
            Sense sense = requireSense(definition.senseId(), senses);
            if (sense.getExistingSenseId() != null) {
                throw new ConflictException("Esošas Tēzaura nozīmes datus labo Tēzaura redaktorā.");
            }
        }
        for (GeneratedDefinition definition : definitions) {
            requireSense(definition.senseId(), senses).setGloss(definition.definition());
        }
        return senses.stream().map(SenseResponse::from).toList();
    }

    private void ensureExistingSenseReferences(Review review, List<Sense> senses) {
        if (review.getMatchedEntryId() == null) return;
        Map<Integer, Sense> references = new HashMap<>();
        senses.stream().filter(sense -> sense.getExistingSenseId() != null)
                .forEach(sense -> references.put(sense.getExistingSenseId(), sense));
        int orderNo = nextOrderNo(senses);
        var dictionarySenses = tezaursSenseRepository.findOrderedByEntryId(review.getMatchedEntryId());
        for (var existing : dictionarySenses) {
            Sense reference = references.get(existing.getId());
            if (reference == null) {
                String gloss = existing.getGloss();
                if (gloss == null || gloss.isBlank()) gloss = "Skaidrojums nav norādīts.";
                reference = senseRepository.save(new Sense(UUID.randomUUID(), review.getId(),
                        review.getMatchedEntryId(), gloss, existing.getId(), orderNo++));
                senses.add(reference);
                references.put(existing.getId(), reference);
            }
        }
        for (var existing : dictionarySenses) {
            Sense parent = references.get(existing.getParentSenseId());
            references.get(existing.getId()).setParentSenseId(parent == null ? null : parent.getId());
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
                .orElseThrow(() -> new ConflictException("Nozīme nepieder perēkļa pašreizējai šķirkļa izvēlei."));
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

    private Review getReviewOrThrow(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
    }

    private Review lockReview(UUID reviewId) {
        return reviewRepository.findLockedById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
    }
}
