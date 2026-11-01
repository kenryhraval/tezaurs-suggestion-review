package lv.tezaurs.suggestions.entry.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.entry.dto.EntryDraftResponse;
import lv.tezaurs.suggestions.entry.dto.SaveEntryDraftRequest;
import lv.tezaurs.suggestions.entry.dto.SaveEntryDraftResponse;
import lv.tezaurs.suggestions.entry.dto.SaveLexemeDraftRequest;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.suggestion.service.ReviewCompletionService;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryTypeRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntryDraftService {
    private static final int DEFAULT_ENTRY_TYPE_ID = 1;
    private static final int DEFAULT_LEXEME_ROLE_ID = 1;

    private final ReviewCompletionService completionService;
    private final ReviewEntryRepository entryRepository;
    private final ReviewLexemeRepository lexemeRepository;
    private final ReviewCaseRepository caseRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final TezaursEntryTypeRepository entryTypeRepository;
    private final TezaursFlagService flagService;
    private final LexemeDraftService lexemeDraftService;

    @Transactional(readOnly = true)
    public Optional<EntryDraftResponse> find(UUID caseId) {
        ReviewCase reviewCase = requireCase(caseId);
        if (reviewCase.getMatchedEntryId() != null) return Optional.empty();
        return entryRepository.findByCaseId(caseId)
                .map(entry -> EntryDraftResponse.from(entry,
                        lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId())));
    }

    @Transactional
    public SaveEntryDraftResponse create(UUID caseId) {
        ReviewCase reviewCase = requireEditableCase(caseId);
        Optional<ReviewEntry> existing = entryRepository.findByCaseId(caseId);
        if (existing.isPresent()) {
            ReviewEntry entry = existing.get();
            List<ReviewLexeme> lexemes = lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());
            return response(entry, lexemes, reviewCase);
        }

        SaveEntryDraftRequest request = new SaveEntryDraftRequest(
                reviewCase.getReviewedTerm(), DEFAULT_ENTRY_TYPE_ID,
                null, null, null, null, null, Map.of());
        validateEntry(request);
        ReviewEntry entry = new ReviewEntry(caseId);
        int homonymNo = nextHomonymNumber(request.heading().trim(), entry.getId());
        updateEntry(entry, request, homonymNo);
        ReviewEntry savedEntry = entryRepository.save(entry);
        ReviewLexeme primaryLexeme = lexemeDraftService.createInitial(
                savedEntry, new SaveLexemeDraftRequest(entry.getHeading(), true, DEFAULT_LEXEME_ROLE_ID, null,
                        null, null, null, List.of(), Map.of(), null));
        List<ReviewLexeme> lexemes = List.of(primaryLexeme);

        return response(savedEntry, lexemes, reviewCase);
    }

    @Transactional
    public SaveEntryDraftResponse update(UUID caseId, SaveEntryDraftRequest request) {
        ReviewCase reviewCase = requireEditableCase(caseId);
        ReviewEntry entry = entryRepository.findByCaseId(caseId)
                .orElseThrow(() -> new NotFoundException("Entry draft was not found"));
        if (!entryTypeRepository.existsById(request.typeId())) {
            throw new ConflictException("The selected entry type does not exist");
        }
        flagService.validateChanges("E", request.flags(), entry.getFlags());
        updateEntry(entry, request, entry.getHomonymNo());

        List<ReviewLexeme> lexemes = lexemeRepository
                .findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());
        return response(entry, lexemes, reviewCase);
    }

    private void updateEntry(ReviewEntry entry, SaveEntryDraftRequest request, int homonymNo) {
        String heading = request.heading().trim();
        entry.update(heading, homonymNo, request.typeId(),
                clean(request.etymology()), clean(request.normativeComment()),
                clean(request.scientificName()), clean(request.source()), clean(request.notes()),
                request.flags());
    }

    private void validateEntry(SaveEntryDraftRequest request) {
        if (!entryTypeRepository.existsById(request.typeId())) {
            throw new ConflictException("The selected entry type does not exist");
        }
        flagService.validate("E", request.flags());
    }

    private int nextHomonymNumber(String heading, UUID currentEntryId) {
        int tezaursMaximum = tezaursEntryRepository
                .findFirstByHeadingOrderByHomonymNoDesc(heading)
                .map(TezaursEntry::getHomonymNo)
                .orElse(0);
        int draftMaximum = entryRepository
                .findFirstByHeadingAndIdNotOrderByHomonymNoDesc(heading, currentEntryId)
                .map(ReviewEntry::getHomonymNo)
                .orElse(0);
        return Math.max(tezaursMaximum, draftMaximum) + 1;
    }

    private ReviewCase requireEditableCase(UUID id) {
        ReviewCase reviewCase = requireCase(id);
        if (!reviewCase.canEditEntryDraft()) {
            throw new ConflictException(
                    "Only new entry drafts can be edited");
        }
        return reviewCase;
    }

    private ReviewCase requireCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }

    private SaveEntryDraftResponse response(
            ReviewEntry entry, List<ReviewLexeme> lexemes, ReviewCase reviewCase) {
        return new SaveEntryDraftResponse(
                EntryDraftResponse.from(entry, lexemes), completionService.response(reviewCase));
    }

    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
