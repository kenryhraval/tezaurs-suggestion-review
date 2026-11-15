package lv.tezaurs.suggestions.tezaurs.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursFormOptionsResponse;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryTypeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarFlagRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionFrequencyRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionTypeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TezaursService {
    private final TezaursEntryRepository entryRepository;
    private final TezaursSenseRepository senseRepository;
    private final TezaursGrammarFlagRepository grammarFlagRepository;
    private final TezaursEntryTypeRepository entryTypeRepository;
    private final TezaursLexemeRoleRepository lexemeRoleRepository;
    private final TezaursParadigmRepository paradigmRepository;
    private final TezaursGrammarRestrictionTypeRepository restrictionTypeRepository;
    private final TezaursGrammarRestrictionFrequencyRepository restrictionFrequencyRepository;

    @Transactional(readOnly = true)
    public TezaursFormOptionsResponse formOptions() {
        var entryTypes = entryTypeRepository.findAll(Sort.by("id")).stream()
                .map(type -> new TezaursFormOptionsResponse.Option(
                        type.getId(), type.getName(), type.getDescription()))
                .toList();
        var roles = lexemeRoleRepository.findAll(Sort.by("id")).stream()
                .map(role -> new TezaursFormOptionsResponse.Option(
                        role.getId(), role.getName(), role.getDescription()))
                .toList();
        var paradigms = paradigmRepository.findAll(Sort.by("id")).stream()
                .map(paradigm -> new TezaursFormOptionsResponse.ParadigmOption(
                        paradigm.getId(), paradigm.getHumanKey(), paradigm.getCaption(), paradigm.getStems(),
                        paradigm.getLegacyNo(), paradigm.getData()))
                .toList();
        var flags = grammarFlagRepository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc().stream()
                .map(flag -> new TezaursFormOptionsResponse.FlagOption(
                        flag.getName(), flag.getCaption() == null ? flag.getName() : flag.getCaption(),
                        flag.getDescription(), flag.isMultiple(), flag.getPermittedValues(),
                        List.of(flag.getScope()),
                        flag.getValues().stream().filter(value -> !value.isDeprecated())
                                .map(TezaursGrammarFlagValue::getValue).toList()))
                .toList();
        var restrictionTypes = restrictionTypeRepository.findAll(Sort.by("id")).stream()
                .map(type -> new TezaursFormOptionsResponse.Option(
                        type.getId(), type.getName(), type.getCaption()))
                .toList();
        var restrictionFrequencies = restrictionFrequencyRepository.findAll(Sort.by("id")).stream()
                .map(frequency -> new TezaursFormOptionsResponse.Option(
                        frequency.getId(), frequency.getName(), frequency.getCaption()))
                .toList();
        return new TezaursFormOptionsResponse(entryTypes, roles, paradigms, flags,
                restrictionTypes, restrictionFrequencies);
    }

    @Transactional(readOnly = true)
    public TezaursEntryCandidateResponse findEntry(Integer entryId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Tēzaurs entry " + entryId + " was not found"));
        var senses = senseRepository.findOrderedByEntryId(entryId).stream()
                .map(TezaursSenseResponse::from).toList();
        return new TezaursEntryCandidateResponse(entry.getId(), entry.getHeading(), entry.getHumanKey(), senses);
    }

    @Transactional(readOnly = true)
    public List<TezaursSenseResponse> findSenses(Integer entryId) {
        if (!entryRepository.existsById(entryId)) {
            throw new NotFoundException("Tēzaurs entry " + entryId + " was not found");
        }
        return senseRepository.findOrderedByEntryId(entryId).stream()
                .map(TezaursSenseResponse::from).toList();
    }
}
