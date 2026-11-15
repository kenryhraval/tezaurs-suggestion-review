package lv.tezaurs.suggestions.usage.service;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.auth.service.CurrentUserService;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.entry.service.EntryDraftService;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.service.ReviewCompletionService;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import lv.tezaurs.suggestions.usage.dto.CorpusSearchResponse;
import lv.tezaurs.suggestions.usage.dto.EvidenceResponse;
import lv.tezaurs.suggestions.usage.dto.SaveEvidenceRequest;
import lv.tezaurs.suggestions.usage.dto.UsageDecisionRequest;
import lv.tezaurs.suggestions.usage.entity.EvidenceSource;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;
import lv.tezaurs.suggestions.usage.entity.UsageEvidence;
import lv.tezaurs.suggestions.usage.repository.UsageEvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsageService {
    private final ReviewCaseRepository caseRepository;
    private final UsageEvidenceRepository evidenceRepository;
    private final EntryDraftService entryDraftService;
    private final ReviewCompletionService completionService;
    private final CurrentUserService currentUserService;
    private final CorpusSearchService corpusSearchService;

    @Transactional(readOnly = true)
    public List<EvidenceResponse> evidence(UUID caseId) {
        requireUsageCase(caseId);
        return evidenceRepository.findByCaseIdOrderByCreatedAtAscIdAsc(caseId).stream()
                .map(EvidenceResponse::from).toList();
    }

    public CorpusSearchResponse search(UUID caseId, String query, Corpus corpus, int page) {
        requireUsageCase(caseId);
        return corpusSearchService.search(query, corpus, page);
    }

    @Transactional
    public ReviewResponse decide(UUID caseId, UsageDecisionRequest request, String authorization) {
        String username = currentUserService.requireUsername(authorization);
        ReviewCase review = requireEditableEntryCase(caseId);
        review.recordUsageDecision(request.decision(), request.note(), username);
        if (request.decision() == UsageDecision.USED) {
            entryDraftService.create(caseId);
        }
        return completionService.response(review);
    }

    @Transactional
    public EvidenceResponse addEvidence(UUID caseId, SaveEvidenceRequest request, String authorization) {
        requireEditableEntryCase(caseId);
        if (request.source() == EvidenceSource.CORPUS && (request.corpus() == null || request.tokenNumber() == null)) {
            throw new ConflictException("Corpus evidence requires a corpus and token number");
        }
        if (request.source() != EvidenceSource.CORPUS && (request.corpus() != null || request.tokenNumber() != null)) {
            throw new ConflictException("Only corpus evidence can specify a corpus and token number");
        }
        validateUrl(request.url().trim());
        if (request.source() == EvidenceSource.CORPUS) {
            var existing = evidenceRepository.findByCaseIdAndCorpusAndTokenNumber(
                    caseId, request.corpus(), request.tokenNumber());
            if (existing.isPresent()) {
                return EvidenceResponse.from(existing.get());
            }
        }
        String username = currentUserService.usernameFrom(authorization).orElse(null);
        UsageEvidence evidence = new UsageEvidence(caseId, request, username);
        return EvidenceResponse.from(evidenceRepository.save(evidence));
    }

    @Transactional
    public void deleteEvidence(UUID caseId, UUID evidenceId) {
        requireEditableEntryCase(caseId);
        UsageEvidence evidence = evidenceRepository.findByIdAndCaseId(evidenceId, caseId)
                .orElseThrow(() -> new NotFoundException("Evidence was not found in this case"));
        evidenceRepository.delete(evidence);
    }

    private void requireUsageCase(UUID caseId) {
        ReviewCase review = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Review " + caseId + " was not found"));
        if (review.getType() != CaseType.ENTRY) {
            throw new ConflictException("Usage research is currently available only for new entries");
        }
    }

    private ReviewCase requireEditableEntryCase(UUID caseId) {
        ReviewCase review = caseRepository.findLockedById(caseId)
                .orElseThrow(() -> new NotFoundException("Review " + caseId + " was not found"));
        if (review.getType() != CaseType.ENTRY) {
            throw new ConflictException("Usage research is currently available only for new entries");
        }
        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Atsāciet izskatīšanu statusā “Apstrādē”, lai mainītu lietojuma pārbaudi.");
        }
        return review;
    }

    private static void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) return;
        } catch (IllegalArgumentException ignored) {
        }
        throw new ConflictException("Evidence must have an HTTP or HTTPS source URL");
    }
}
