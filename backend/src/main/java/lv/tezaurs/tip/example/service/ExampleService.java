package lv.tezaurs.tip.example.service;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.example.dto.ExampleResponse;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.dto.UpdateExampleRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExampleService {
    private final ReviewRepository reviewRepository;
    private final ExampleRepository exampleRepository;
    private final CurrentUserService currentUserService;
    private final SenseRepository senseRepository;

    @Transactional(readOnly = true)
    public List<ExampleResponse> getExamplesForReview(UUID reviewId) {
        Review review = requireReviewWithKnownType(reviewId);

        return exampleRepository.findForReview(reviewId, review.getMatchedEntryId()).stream()
                .map(ExampleResponse::from).toList();
    }

    @Transactional
    public ExampleResponse saveExample(UUID reviewId, SaveExampleRequest request, String authorization) {
        Review review = requireEditableReview(reviewId);
        String username = currentUserService.usernameFrom(authorization).orElse(null);

        return validateAndSaveExample(review, request, username);
    }

    @Transactional
    public List<ExampleResponse> saveExamples(UUID reviewId, List<SaveExampleRequest> requests, String authorization) {
        Review review = requireEditableReview(reviewId);
        String username = currentUserService.usernameFrom(authorization).orElse(null);

        return requests.stream().map(request -> validateAndSaveExample(review, request, username)).toList();
    }

    @Transactional
    public void deleteExample(UUID reviewId, UUID exampleId) {
        requireEditableReview(reviewId);
        Example example = exampleRepository.findByIdAndReviewId(exampleId, reviewId)
                .orElseThrow(() -> new NotFoundException("Piemērs šajā perēklī netika atrasts."));

        if (example.isApproved()) {
            throw new ConflictException("Pirms piemēra dzēšanas atceliet tā apstiprinājumu.");
        }
        exampleRepository.delete(example);
    }

    @Transactional
    public List<ExampleResponse> deleteUnapprovedCorpusExamples(UUID reviewId, Corpus corpus) {
        Review review = requireEditableReview(reviewId);

        List<Example> removableExamples = exampleRepository.findForReview(reviewId, review.getMatchedEntryId()).stream()
                .filter(example -> example.getCorpus() == corpus && !example.isApproved()).toList();

        exampleRepository.deleteAll(removableExamples);

        return getExamplesForReview(reviewId);
    }

    @Transactional
    public ExampleResponse updateExample(UUID reviewId, UUID exampleId, UpdateExampleRequest request) {
        Review review = requireEditableReview(reviewId);
        Example example = exampleRepository.findByIdAndReviewId(exampleId, reviewId)
                .orElseThrow(() -> new NotFoundException("Piemērs šajā perēklī netika atrasts."));
        if (example.getSense() != null && !Objects.equals(
                example.getSense().getTargetEntryId(), review.getMatchedEntryId())) {
            throw new ConflictException("Piemērs piesaistīts citai šī perēkļa šķirkļa izvēlei.");
        }
        UUID senseId = request.senseId();
        Sense target = null;
        if (senseId != null) {
            target = senseRepository.findByIdAndReviewId(senseId, reviewId)
                    .orElseThrow(() -> new NotFoundException("Nozīme šajā perēklī netika atrasta."));
            if (!Objects.equals(target.getTargetEntryId(), review.getMatchedEntryId())) {
                throw new ConflictException("Nozīme nepieder pašreizējai šķirkļa izvēlei.");
            }
        }

        if (request.approved()) {
            if (target == null) {
                throw new ConflictException("Pirms piemēra apstiprināšanas piesaistiet to nozīmei.");
            }
            if (target.getExistingSenseId() != null) {
                throw new ConflictException("Esošu Tēzaura nozīmju piemērus šeit nevar apstiprināt.");
            }
        }

        example.reassignTo(target);
        example.setApproved(request.approved());
        return ExampleResponse.from(example);
    }

    private Review requireReviewWithKnownType(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));

        if (review.getType() == ReviewType.UNKNOWN) {
            throw new ConflictException("Vispirms veiciet pārbaudi Tēzaurā.");
        }
        return review;
    }

    private Review requireEditableReview(UUID reviewId) {
        Review review = reviewRepository.findLockedById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));

        if (review.getType() == ReviewType.UNKNOWN) {
            throw new ConflictException("Vispirms veiciet pārbaudi Tēzaurā.");
        }
        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }

        return review;
    }

    @Transactional(readOnly = true)
    public Set<Long> findSelectedTokenNumbers(UUID reviewId, Corpus corpus) {
        return new HashSet<>(exampleRepository.findSelectedTokenNumbers(reviewId, corpus));
    }

    private ExampleResponse validateAndSaveExample(Review review, SaveExampleRequest request, String username) {
        UUID reviewId = review.getId();
        if (request.source() == ExampleSource.CORPUS && (request.corpus() == null || request.tokenNumber() == null)) {
            throw new ConflictException("Korpusa piemēram jānorāda korpuss un piemēra atrašanās vieta tajā.");
        }
        if (request.source() != ExampleSource.CORPUS && (request.corpus() != null || request.tokenNumber() != null)) {
            throw new ConflictException("Korpusu un piemēra atrašanās vietu tajā var norādīt tikai korpusa piemēriem.");
        }

        if (request.matchStart() != null || request.matchEnd() != null) {
            if (request.matchStart() == null || request.matchEnd() == null
                    || request.matchStart() < 0 || request.matchEnd() <= request.matchStart()
                    || request.matchEnd() > request.citation().trim().length()) {
                throw new ConflictException("Piemēra izcēluma robežas nav derīgas.");
            }
        }

        validateUrl(request.url().trim());

        if (request.source() == ExampleSource.CORPUS) {
            var existing = exampleRepository.findByReviewIdAndCorpusAndTokenNumber(
                    reviewId, request.corpus(), request.tokenNumber());
            if (existing.isPresent()) {
                Example example = existing.get();
                if (example.getSense() != null && !Objects.equals(example.getSense().getTargetEntryId(), review.getMatchedEntryId())) {
                    throw new ConflictException("Piemērs jau piesaistīts iepriekš izvēlētā šķirkļa nozīmei.");
                }
                return ExampleResponse.from(example);
            }
        }

        Example example = new Example(reviewId, request, username);

        return ExampleResponse.from(exampleRepository.save(example));
    }

    private static void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) return;
        } catch (IllegalArgumentException ignored) {
        }
        throw new ConflictException("Norādiet piemēra avota saiti, kas sākas ar http:// vai https://.");
    }
}
