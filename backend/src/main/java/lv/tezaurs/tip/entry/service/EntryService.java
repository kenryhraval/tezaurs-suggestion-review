package lv.tezaurs.tip.entry.service;

import java.util.List;
import java.util.Optional;
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
import lv.tezaurs.tip.entry.dto.SaveEntryRequest;
import lv.tezaurs.tip.entry.entity.Entry;
import lv.tezaurs.tip.entry.entity.Lexeme;
import lv.tezaurs.tip.entry.repository.EntryRepository;
import lv.tezaurs.tip.entry.repository.LexemeRepository;
import lv.tezaurs.tip.tezaurs.entity.TezaursEntry;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.tip.tezaurs.service.TezaursFlagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntryService {
    private final EntryRepository entryRepository;
    private final LexemeRepository lexemeRepository;
    private final ReviewRepository reviewRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final TezaursFlagService flagService;

    @Transactional(readOnly = true)
    public Optional<EntryResponse> find(UUID reviewId) {
        Review review = getReviewOrThrow(reviewId);

        if (review.getMatchedEntryId() != null) return Optional.empty();

        return entryRepository.findByReviewId(reviewId)
                .map(entry -> EntryResponse.from(entry,
                        lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId())));
    }

    @Transactional(readOnly = true)
    public boolean isReady(UUID reviewId) {
        Optional<Entry> savedEntry = entryRepository.findByReviewId(reviewId);

        if (savedEntry.isEmpty()) {
            return false;
        }

        Entry entry = savedEntry.get();
        List<Lexeme> lexemes = lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());
        long primaryLexemeCount = lexemes.stream()
                .filter(Lexeme::isPrimary)
                .count();

        return primaryLexemeCount == 1;
    }

    @Transactional
    public EntryResponse create(UUID reviewId) {
        Review review = requireEditableReview(reviewId);
        Optional<Entry> existing = entryRepository.findByReviewId(reviewId);

        if (existing.isPresent()) {
            Entry entry = existing.get();
            List<Lexeme> lexemes = lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());
            return EntryResponse.from(entry, lexemes);
        }

        String heading = review.getReviewedTerm().trim();
        int homonymNo = nextHomonymNumber(heading);
        Entry entry = new Entry(reviewId, heading, homonymNo);
        Entry savedEntry = entryRepository.save(entry);

        Lexeme primaryLexeme = new Lexeme(savedEntry.getId(), heading);
        Lexeme savedLexeme = lexemeRepository.save(primaryLexeme);

        return EntryResponse.from(savedEntry, List.of(savedLexeme));
    }

    @Transactional
    public EntryResponse update(UUID reviewId, SaveEntryRequest request) {
        requireEditableReview(reviewId);
        Entry entry = entryRepository.findByReviewId(reviewId)
                .orElseThrow(() -> new NotFoundException("Šķirkļa melnraksts netika atrasts."));

        flagService.validate("E", request.flags());

        updateEntry(entry, request, entry.getHomonymNo());

        List<Lexeme> lexemes = lexemeRepository
                .findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId());

        return EntryResponse.from(entry, lexemes);
    }

    private void updateEntry(Entry entry, SaveEntryRequest request, int homonymNo) {
        String heading = request.heading().trim();

        entry.update(heading, homonymNo, request.type(),
                clean(request.etymology()), clean(request.normativeComment()),
                clean(request.scientificName()), clean(request.source()), clean(request.notes()),
                request.flags());
    }

    private int nextHomonymNumber(String heading) {
        int tezaursMaximum = tezaursEntryRepository
                .findFirstByHeadingOrderByHomonymNoDesc(heading)
                .map(TezaursEntry::getHomonymNo)
                .orElse(0);
        int draftMaximum = entryRepository
                .findFirstByHeadingOrderByHomonymNoDesc(heading)
                .map(Entry::getHomonymNo)
                .orElse(0);

        return Math.max(tezaursMaximum, draftMaximum) + 1;
    }

    private Review requireEditableReview(UUID id) {
        Review review = getReviewOrThrow(id);

        if (review.getStatus() != ReviewStatus.IN_PROGRESS
                || review.getType() != ReviewType.ENTRY
                || review.getUsageDecision() != UsageDecision.USED) {
            throw new ConflictException(
                    "Jauna šķirkļa melnrakstu var labot tikai stāvoklī “Apstrādē”, kad vārda lietojums ir apstiprināts.");
        }

        return review;
    }

    private Review getReviewOrThrow(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
    }

    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
