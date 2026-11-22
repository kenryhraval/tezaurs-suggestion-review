package lv.tezaurs.tip.tezaurs.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.tip.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.tip.tezaurs.entity.TezaursEntry;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TezaursEntrySearchService {
    private static final int MAX_ADDITIONAL_RESULTS = 5;

    private final TezaursEntryRepository entryRepository;
    private final TezaursSenseRepository senseRepository;

    @Transactional(readOnly = true)
    public List<TezaursEntryCandidateResponse> findMatches(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm.isBlank()) return List.of();

        var exactMatches = entryRepository.findExactMatches(normalizedTerm);
        var entries = new ArrayList<>(exactMatches);
        var entryIds = new HashSet<Integer>();
        exactMatches.forEach(entry -> entryIds.add(entry.getId()));

        // Allow room for exact matches that may also appear in the substring results.
        int searchLimit = exactMatches.size() + MAX_ADDITIONAL_RESULTS;
        var containingMatches = entryRepository.findContaining(
                normalizedTerm, PageRequest.of(0, searchLimit));
        int additionalCount = 0;
        for (var entry : containingMatches) {
            if (entryIds.add(entry.getId())) {
                entries.add(entry);
                additionalCount++;
                if (additionalCount == MAX_ADDITIONAL_RESULTS) break;
            }
        }
        if (entries.isEmpty()) return List.of();

        var senses = senseRepository.findOrderedByEntryIdIn(
                entries.stream().map(TezaursEntry::getId).toList());

        return entries.stream().map(entry -> new TezaursEntryCandidateResponse(
                entry.getId(), entry.getHeading(), entry.getHumanKey(),
                senses.stream()
                        .filter(sense -> entry.getId().equals(sense.getEntryId()))
                        .map(TezaursSenseResponse::from)
                        .toList())).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
