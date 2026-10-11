package lv.tezaurs.suggestions.tezaurs.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexeme;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TezaursEntrySearchService {
    private static final int MAX_RESULTS = 5;

    private final TezaursLexemeRepository lexemeRepository;
    private final TezaursSenseRepository senseRepository;

    @Transactional(readOnly = true)
    public List<TezaursEntryCandidateResponse> findMatches(String term, Integer selectedEntryId) {
        String normalizedTerm = normalize(term);
        List<TezaursLexeme> matches = normalizedTerm.isBlank()
                ? List.of()
                : lexemeRepository.findContaining(normalizedTerm, PageRequest.of(0, MAX_RESULTS));

        List<TezaursEntryCandidateResponse> results = new ArrayList<>();
        Set<Integer> addedEntryIds = new HashSet<>();
        for (TezaursLexeme lexeme : matches) {
            if (lexeme.getEntry() != null && addedEntryIds.add(lexeme.getEntryId())) {
                results.add(toResponse(lexeme));
            }
        }

        keepSelectedEntryVisible(results, selectedEntryId);
        return results;
    }

    private void keepSelectedEntryVisible(
            List<TezaursEntryCandidateResponse> results, Integer selectedEntryId) {
        if (selectedEntryId == null || results.stream().anyMatch(result -> result.id().equals(selectedEntryId))) {
            return;
        }

        lexemeRepository.findFirstByEntryIdOrderByOrderNoAscIdAsc(selectedEntryId)
                .filter(lexeme -> lexeme.getEntry() != null)
                .map(this::toResponse)
                .ifPresent(results::add);
    }

    private TezaursEntryCandidateResponse toResponse(TezaursLexeme lexeme) {
        return new TezaursEntryCandidateResponse(
                lexeme.getEntryId(),
                lexeme.getEntry().getHeading(),
                lexeme.getEntry().getHumanKey(),
                lexeme.getLemma(),
                findSenses(lexeme.getEntryId()));
    }

    private List<TezaursSenseResponse> findSenses(Integer entryId) {
        return senseRepository.findOrderedByEntryId(entryId).stream()
                .map(sense -> new TezaursSenseResponse(
                        sense.getId(),
                        sense.getParentSenseId(),
                        sense.getGloss(),
                        sense.getOrderNo()))
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
