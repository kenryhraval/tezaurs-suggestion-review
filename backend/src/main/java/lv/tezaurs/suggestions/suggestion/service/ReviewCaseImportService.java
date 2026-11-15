package lv.tezaurs.suggestions.suggestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.repository.CaseSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewCaseImportService {
    private final SuggestionImportRepository importRepository;
    private final IncubatorSuggestionRepository sourceRepository;
    private final ReviewCaseRepository caseRepository;
    private final CaseSuggestionRepository linkRepository;

    @Transactional
    public int createMissingCases() {
        if (!importRepository.tryAcquireImportLock()) {
            return 0;
        }
        var suggestions = importRepository.findUnlinkedSuggestions();
        for (var source : suggestions) {
            ReviewCase review = caseRepository.save(
                    ReviewCase.fromSuggestion(source.lemma(), initialStatus(source.id(), source.status())));
            linkRepository.save(new CaseSuggestion(sourceRepository.getReferenceById(source.id()), review));
        }
        return suggestions.size();
    }

    private ReviewStatus initialStatus(Integer sourceId, Integer status) {
        if (status == null) {
            return ReviewStatus.NEW;
        }
        if (status < 0 || status > 8) {
            log.warn("Suggestion {} has unknown legacy status {}; importing as NEW", sourceId, status);
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
