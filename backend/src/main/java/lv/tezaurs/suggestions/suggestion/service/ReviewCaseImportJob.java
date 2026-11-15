package lv.tezaurs.suggestions.suggestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewCaseImportJob {
    private final ReviewCaseImportService importService;

    @Scheduled(initialDelay = 0, fixedDelay = 10_000)
    public void importSuggestions() {
        int imported = importService.createMissingCases();
        if (imported > 0) {
            log.info("Created review cases for {} suggestions", imported);
        }
    }
}
