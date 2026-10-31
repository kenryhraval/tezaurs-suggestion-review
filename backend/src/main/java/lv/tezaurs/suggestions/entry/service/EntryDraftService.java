package lv.tezaurs.suggestions.entry.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.entry.dto.CreateEntryDraftRequest;
import lv.tezaurs.suggestions.entry.dto.EntryDraftResponse;
import lv.tezaurs.suggestions.entry.dto.SaveEntryDraftRequest;
import lv.tezaurs.suggestions.entry.dto.SaveEntryDraftResponse;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryTypeRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntryDraftService {
    private final ReviewEntryRepository entryRepository;
    private final ReviewLexemeRepository lexemeRepository;
    private final ReviewCaseRepository caseRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final TezaursEntryTypeRepository entryTypeRepository;
    private final TezaursFlagService flagService;
    private final LexemeDraftService lexemeDraftService;

    @Transactional(readOnly = true)
    public Optional<EntryDraftResponse> find(UUID caseId) {
        requireCase(caseId);
        return entryRepository.findByCaseId(caseId)
                .map(entry -> EntryDraftResponse.from(entry,
                        lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId())));
    }

    @Transactional
    public SaveEntryDraftResponse create(UUID caseId, CreateEntryDraftRequest request) {
        ReviewCase reviewCase = requireCaseWithoutTezaursEntry(caseId);
        if (entryRepository.findByCaseId(caseId).isPresent()) {
            throw new ConflictException("An entry draft already exists for this review");
        }

        validateEntry(request.entry());
        if (!request.primaryLexeme().primary()) {
            throw new ConflictException("The first lexeme must be the primary lexeme");
        }
        ReviewEntry entry = new ReviewEntry(caseId);
        updateEntry(entry, request.entry());
        ReviewEntry savedEntry = entryRepository.save(entry);
        ReviewLexeme primaryLexeme = lexemeDraftService.createInitial(
                savedEntry, request.primaryLexeme());
        List<ReviewLexeme> lexemes = List.of(primaryLexeme);
        reviewCase.recordEntryDraftReadiness(savedEntry.isReady(lexemes));

        return response(savedEntry, lexemes, reviewCase);
    }

    @Transactional
    public SaveEntryDraftResponse update(UUID caseId, SaveEntryDraftRequest request) {
        ReviewCase reviewCase = requireCaseWithoutTezaursEntry(caseId);
        ReviewEntry entry = entryRepository.findByCaseId(caseId)
                .orElseThrow(() -> new NotFoundException("Entry draft was not found"));
        validateEntry(request);
        updateEntry(entry, request);

        List<ReviewLexeme> lexemes = lexemeRepository
                .findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());
        reviewCase.recordEntryDraftReadiness(entry.isReady(lexemes));
        return response(entry, lexemes, reviewCase);
    }

    private void updateEntry(ReviewEntry entry, SaveEntryDraftRequest request) {
        String heading = request.heading().trim();
        int homonymNo = nextHomonymNumber(heading, entry.getId());
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
                .map(entry -> entry.getHomonymNo())
                .orElse(0);
        int draftMaximum = entryRepository
                .findFirstByHeadingAndIdNotOrderByHomonymNoDesc(heading, currentEntryId)
                .map(ReviewEntry::getHomonymNo)
                .orElse(0);
        return Math.max(tezaursMaximum, draftMaximum) + 1;
    }

    private ReviewCase requireCaseWithoutTezaursEntry(UUID id) {
        ReviewCase reviewCase = requireCase(id);
        if (reviewCase.getTezaursStatus() != TezaursStatus.NOT_FOUND) {
            throw new ConflictException(
                    "An entry draft can only be saved when the entry is absent from Tēzaurs");
        }
        return reviewCase;
    }

    private ReviewCase requireCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }

    private static SaveEntryDraftResponse response(
            ReviewEntry entry, List<ReviewLexeme> lexemes, ReviewCase reviewCase) {
        return new SaveEntryDraftResponse(
                EntryDraftResponse.from(entry, lexemes), ReviewResponse.from(reviewCase));
    }

    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
