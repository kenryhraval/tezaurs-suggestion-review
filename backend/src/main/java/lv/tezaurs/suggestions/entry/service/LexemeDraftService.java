package lv.tezaurs.suggestions.entry.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.entry.dto.EntryDraftResponse;
import lv.tezaurs.suggestions.entry.dto.SaveLexemeDraftRequest;
import lv.tezaurs.suggestions.entry.dto.SaveLexemeDraftResponse;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursFlagService;
import lv.tezaurs.suggestions.tezaurs.service.TezaursRestrictionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LexemeDraftService {
    private final ReviewEntryRepository entryRepository;
    private final ReviewLexemeRepository lexemeRepository;
    private final ReviewCaseRepository caseRepository;
    private final TezaursLexemeRoleRepository lexemeRoleRepository;
    private final TezaursParadigmRepository paradigmRepository;
    private final TezaursFlagService flagService;
    private final TezaursRestrictionService restrictionService;

    @Transactional
    public SaveLexemeDraftResponse create(UUID caseId, SaveLexemeDraftRequest request) {
        ReviewCase reviewCase = requireCaseWithoutTezaursEntry(caseId);
        ReviewEntry entry = requireEntry(caseId);
        validate(request);

        List<ReviewLexeme> existingLexemes = findLexemes(entry);
        if (request.primary()) {
            existingLexemes.forEach(lexeme -> lexeme.setPrimary(false));
        }
        int orderNo = existingLexemes.stream()
                .mapToInt(ReviewLexeme::getOrderNo)
                .max()
                .orElse(0) + 1;
        ReviewLexeme lexeme = new ReviewLexeme(entry.getId(), orderNo);
        updateLexeme(lexeme, request);
        ReviewLexeme savedLexeme = lexemeRepository.save(lexeme);

        existingLexemes.add(savedLexeme);
        reviewCase.recordEntryDraftReadiness(entry.isReady(existingLexemes));
        return response(savedLexeme, reviewCase);
    }

    @Transactional
    public SaveLexemeDraftResponse update(
            UUID caseId, UUID lexemeId, SaveLexemeDraftRequest request) {
        ReviewCase reviewCase = requireCaseWithoutTezaursEntry(caseId);
        ReviewEntry entry = requireEntry(caseId);
        ReviewLexeme lexeme = requireLexeme(entry.getId(), lexemeId);
        validate(request);

        List<ReviewLexeme> lexemes = findLexemes(entry);
        if (!request.primary() && lexeme.isPrimary()) {
            throw new ConflictException("Select another primary lexeme before changing this one");
        }
        if (request.primary()) {
            lexemes.stream()
                    .filter(existing -> !existing.getId().equals(lexemeId))
                    .forEach(existing -> existing.setPrimary(false));
        }
        updateLexeme(lexeme, request);

        reviewCase.recordEntryDraftReadiness(entry.isReady(lexemes));
        return response(lexeme, reviewCase);
    }

    @Transactional
    public ReviewResponse delete(UUID caseId, UUID lexemeId) {
        ReviewCase reviewCase = requireCaseWithoutTezaursEntry(caseId);
        ReviewEntry entry = requireEntry(caseId);
        ReviewLexeme lexeme = requireLexeme(entry.getId(), lexemeId);
        if (lexeme.isPrimary()) {
            throw new ConflictException("Select another primary lexeme before deleting this one");
        }

        lexemeRepository.delete(lexeme);
        List<ReviewLexeme> remainingLexemes = findLexemes(entry).stream()
                .filter(existing -> !existing.getId().equals(lexemeId))
                .toList();
        reviewCase.recordEntryDraftReadiness(entry.isReady(remainingLexemes));
        return ReviewResponse.from(reviewCase);
    }

    ReviewLexeme createInitial(ReviewEntry entry, SaveLexemeDraftRequest request) {
        validate(request);
        ReviewLexeme lexeme = new ReviewLexeme(entry.getId(), 1);
        updateLexeme(lexeme, request);
        return lexemeRepository.save(lexeme);
    }

    private void updateLexeme(ReviewLexeme lexeme, SaveLexemeDraftRequest request) {
        lexeme.update(request.lemma().trim(), request.primary(),
                request.roleId(), request.paradigmId(),
                EntryDraftService.clean(request.stem1()),
                EntryDraftService.clean(request.stem2()),
                EntryDraftService.clean(request.stem3()),
                request.pronunciations(), request.flags(), request.structuralRestrictions(),
                lexeme.getOrderNo());
    }

    private void validate(SaveLexemeDraftRequest request) {
        if (!lexemeRoleRepository.existsById(request.roleId())) {
            throw new ConflictException("The selected lexeme role does not exist");
        }
        if (request.paradigmId() != null && !paradigmRepository.existsById(request.paradigmId())) {
            throw new ConflictException("The selected paradigm does not exist");
        }
        flagService.validate("L", request.flags());
        restrictionService.validate(request.structuralRestrictions());
    }

    private ReviewCase requireCaseWithoutTezaursEntry(UUID id) {
        ReviewCase reviewCase = caseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
        if (reviewCase.getTezaursStatus() != TezaursStatus.NOT_FOUND) {
            throw new ConflictException(
                    "An entry draft can only be saved when the entry is absent from Tēzaurs");
        }
        return reviewCase;
    }

    private ReviewEntry requireEntry(UUID caseId) {
        return entryRepository.findByCaseId(caseId)
                .orElseThrow(() -> new NotFoundException("Entry draft was not found"));
    }

    private ReviewLexeme requireLexeme(UUID entryId, UUID lexemeId) {
        return lexemeRepository.findByIdAndEntryId(lexemeId, entryId)
                .orElseThrow(() -> new NotFoundException("Lexeme draft was not found"));
    }

    private List<ReviewLexeme> findLexemes(ReviewEntry entry) {
        return new ArrayList<>(
                lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()));
    }

    private static SaveLexemeDraftResponse response(
            ReviewLexeme lexeme, ReviewCase reviewCase) {
        return new SaveLexemeDraftResponse(
                EntryDraftResponse.LexemeDraftResponse.from(lexeme),
                ReviewResponse.from(reviewCase));
    }
}
