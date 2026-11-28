package lv.tezaurs.tip.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lv.tezaurs.tip.suggestion.service.SuggestionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuggestionImportJob {
    private final SuggestionService suggestionService;

    @Scheduled(initialDelay = 0, fixedDelayString = "${suggestions.import-delay-ms}")
    public void importSuggestions() {
        int imported = suggestionService.importSuggestions();
        if (imported > 0) {
            log.info("Created review cases for {} suggestions", imported);
        }
    }
}
