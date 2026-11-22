package lv.tezaurs.tip.tezaurs.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.tip.tezaurs.dto.TezaursFormOptionsResponse;
import lv.tezaurs.tip.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.tip.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursGrammarFlagRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TezaursService {
    private final TezaursEntryRepository entryRepository;
    private final TezaursSenseRepository senseRepository;
    private final TezaursGrammarFlagRepository grammarFlagRepository;
    private final TezaursLexemeRoleRepository lexemeRoleRepository;
    private final TezaursParadigmRepository paradigmRepository;

    @Transactional(readOnly = true)
    public TezaursFormOptionsResponse formOptions() {
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

        return new TezaursFormOptionsResponse(roles, paradigms, flags);
    }

    @Transactional(readOnly = true)
    public TezaursEntryCandidateResponse findEntry(Integer entryId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Tēzaura šķirklis “" + entryId + "” netika atrasts."));

        var senses = senseRepository.findOrderedByEntryId(entryId).stream()
                .map(TezaursSenseResponse::from).toList();

        return new TezaursEntryCandidateResponse(entry.getId(), entry.getHeading(), entry.getHumanKey(), senses);
    }

    @Transactional(readOnly = true)
    public List<TezaursSenseResponse> findSenses(Integer entryId) {
        if (!entryRepository.existsById(entryId)) {
            throw new NotFoundException("Tēzaura šķirklis “" + entryId + "” netika atrasts.");
        }

        return senseRepository.findOrderedByEntryId(entryId).stream()
                .map(TezaursSenseResponse::from).toList();
    }
}
