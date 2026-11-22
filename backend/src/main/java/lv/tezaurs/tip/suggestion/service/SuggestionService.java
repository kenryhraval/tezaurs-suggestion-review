package lv.tezaurs.tip.suggestion.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.review.service.ReviewService;
import lv.tezaurs.tip.suggestion.dto.SuggestionCountsResponse;
import lv.tezaurs.tip.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.tip.suggestion.repository.SuggestionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionService {
    private final SuggestionImportRepository importRepository;
    private final IncubatorSuggestionRepository sourceRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewSuggestionRepository linkRepository;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public SuggestionCountsResponse counts() {
        return new SuggestionCountsResponse(sourceRepository.count(),
                reviewRepository.countByStatusNotIn(List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE)));
    }

    @Transactional
    public int importSuggestions() {
        if (!importRepository.tryAcquireImportLock()) {
            return 0;
        }

        var suggestions = importRepository.findUnlinkedSuggestions();
        for (var source : suggestions) {
            Review importedReview = reviewService.createFromSuggestion(
                    source.lemma(), statusFromLegacySuggestion(source.id(), source.status()));
            linkRepository.save(new ReviewSuggestion(sourceRepository.getReferenceById(source.id()), importedReview));
        }

        return suggestions.size();
    }

    private ReviewStatus statusFromLegacySuggestion(Integer suggestionId, Integer status) {
        if (status == null) {
            return ReviewStatus.NEW;
        }
        if (status < 0 || status > 8) {
            log.warn("Suggestion {} has unknown legacy status {}; importing as NEW", suggestionId, status);
            return ReviewStatus.NEW;
        }

        return switch (status) {
            case 0 -> ReviewStatus.NEW;
            case 1 -> ReviewStatus.READY_FOR_REVIEW;
            case 2 -> ReviewStatus.GARBAGE;
            case 3 -> ReviewStatus.COMPLETED;
            case 4 -> ReviewStatus.IN_PROGRESS;
            case 5 -> ReviewStatus.INVENTED;
            case 6 -> ReviewStatus.ALREADY_EXISTS;
            case 7 -> ReviewStatus.INSUFFICIENT_DATA;
            case 8 -> ReviewStatus.NEEDS_EXPERT;
            default -> ReviewStatus.NEW;
        };
    }
}
