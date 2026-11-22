package lv.tezaurs.tip.entry.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.entry.dto.EntryResponse;
import lv.tezaurs.tip.entry.dto.SaveLexemeRequest;
import lv.tezaurs.tip.entry.entity.Entry;
import lv.tezaurs.tip.entry.entity.Lexeme;
import lv.tezaurs.tip.entry.repository.EntryRepository;
import lv.tezaurs.tip.entry.repository.LexemeRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.tip.tezaurs.service.TezaursFlagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LexemeService {
    private final EntryRepository entryRepository;
    private final LexemeRepository lexemeRepository;
    private final ReviewRepository reviewRepository;
    private final TezaursLexemeRoleRepository lexemeRoleRepository;
    private final TezaursParadigmRepository paradigmRepository;
    private final TezaursFlagService flagService;

    @Transactional
    public EntryResponse.LexemeResponse create(UUID reviewId, SaveLexemeRequest request) {
        Review review = requireEditableReview(reviewId);
        Entry entry = requireEntry(review);

        validate(request);

        List<Lexeme> existingLexemes = findLexemes(entry);
        if (request.primary()) {
            existingLexemes.forEach(lexeme -> lexeme.setPrimary(false));
        }

        int orderNo = existingLexemes.stream()
                .mapToInt(Lexeme::getOrderNo)
                .max()
                .orElse(0) + 1;

        Lexeme lexeme = new Lexeme(entry.getId(), orderNo);
        updateLexeme(lexeme, request);
        Lexeme savedLexeme = lexemeRepository.save(lexeme);

        return EntryResponse.LexemeResponse.from(savedLexeme);
    }

    @Transactional
    public EntryResponse.LexemeResponse update(
            UUID reviewId, UUID lexemeId, SaveLexemeRequest request) {
        Review review = requireEditableReview(reviewId);
        Entry entry = requireEntry(review);
        Lexeme lexeme = requireLexeme(entry.getId(), lexemeId);

        validate(request);

        List<Lexeme> lexemes = findLexemes(entry);
        if (!request.primary() && lexeme.isPrimary()) {
            throw new ConflictException("Vispirms izvēlieties citu galveno leksēmu.");
        }
        if (request.primary()) {
            lexemes.stream()
                    .filter(existing -> !existing.getId().equals(lexemeId))
                    .forEach(existing -> existing.setPrimary(false));
        }

        updateLexeme(lexeme, request);

        return EntryResponse.LexemeResponse.from(lexeme);
    }

    @Transactional
    public void delete(UUID reviewId, UUID lexemeId) {
        Review review = requireEditableReview(reviewId);
        Entry entry = requireEntry(review);
        Lexeme lexeme = requireLexeme(entry.getId(), lexemeId);

        if (lexeme.isPrimary()) {
            throw new ConflictException("Pirms šīs leksēmas dzēšanas izvēlieties citu galveno leksēmu.");
        }

        lexemeRepository.delete(lexeme);
    }

    private void updateLexeme(Lexeme lexeme, SaveLexemeRequest request) {
        lexeme.update(request.lemma().trim(), request.primary(),
                request.roleId(), request.paradigmId(),
                EntryService.clean(request.stem1()),
                EntryService.clean(request.stem2()),
                EntryService.clean(request.stem3()),
                request.pronunciations(), request.flags(),
                lexeme.getOrderNo());
    }

    private void validate(SaveLexemeRequest request) {
        validateReferences(request);
        flagService.validate("L", request.flags());
    }

    private void validateReferences(SaveLexemeRequest request) {
        if (!lexemeRoleRepository.existsById(request.roleId())) {
            throw new ConflictException("Izvēlētā leksēmas loma šķirklī nav pieejama.");
        }
        if (request.paradigmId() != null && !paradigmRepository.existsById(request.paradigmId())) {
            throw new ConflictException("Izvēlētā locīšanas paradigma nav pieejama.");
        }
    }

    private Review requireEditableReview(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + id + "” netika atrasts."));

        if (review.getStatus() != ReviewStatus.IN_PROGRESS
                || review.getType() != ReviewType.ENTRY
                || review.getUsageDecision() != UsageDecision.USED) {
            throw new ConflictException(
                    "Jauna šķirkļa melnrakstu var labot tikai stāvoklī “Apstrādē”, kad vārda lietojums ir apstiprināts.");
        }

        return review;
    }

    private Entry requireEntry(Review review) {
        return entryRepository.findByReviewId(review.getId())
                .orElseThrow(() -> new NotFoundException("Šķirkļa melnraksts netika atrasts."));
    }

    private Lexeme requireLexeme(UUID entryId, UUID lexemeId) {
        return lexemeRepository.findByIdAndEntryId(lexemeId, entryId)
                .orElseThrow(() -> new NotFoundException("Leksēmas melnraksts netika atrasts."));
    }

    private List<Lexeme> findLexemes(Entry entry) {
        return new ArrayList<>(
                lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()));
    }

}
