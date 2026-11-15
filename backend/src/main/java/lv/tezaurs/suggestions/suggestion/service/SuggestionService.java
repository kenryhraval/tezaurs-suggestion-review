package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionCountsResponse;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {
    private final IncubatorSuggestionRepository sourceSuggestionRepository;
    private final ReviewCaseRepository caseRepository;

    @Transactional(readOnly = true)
    public SuggestionCountsResponse counts() {
        return new SuggestionCountsResponse(sourceSuggestionRepository.count(),
                caseRepository.countByStatusNotIn(List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE)));
    }
}
