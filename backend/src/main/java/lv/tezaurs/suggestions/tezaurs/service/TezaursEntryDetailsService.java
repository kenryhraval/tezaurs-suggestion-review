package lv.tezaurs.suggestions.tezaurs.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryDetailsResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursFormOptionsResponse;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursExample;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexemeRole;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursParadigm;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryTypeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursExampleRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarFlagRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionFrequencyRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionTypeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSenseRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursSourceLinkRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TezaursEntryDetailsService {
    private final TezaursEntryRepository entryRepository;
    private final TezaursLexemeRepository lexemeRepository;
    private final TezaursSenseRepository senseRepository;
    private final TezaursExampleRepository exampleRepository;
    private final TezaursSourceLinkRepository sourceLinkRepository;
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
                        paradigm.getId(), paradigm.getHumanKey(), paradigm.getCaption(), paradigm.getStems()))
                .toList();
        var flags = grammarFlagRepository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc().stream()
                .map(flag -> new TezaursFormOptionsResponse.FlagOption(
                        flag.getName(), flag.getCaption() == null ? flag.getName() : flag.getCaption(),
                        flag.getDescription(), flag.isMultiple(), flag.getPermittedValues(),
                        List.of(flag.getScope()),
                        flag.getValues().stream().filter(value -> !value.isDeprecated())
                                .map(value -> value.getValue()).toList()))
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
    public TezaursEntryDetailsResponse findEntry(Integer entryId) {
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Tēzaurs entry " + entryId + " was not found"));
        var lexemes = lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entryId);
        var senses = senseRepository.findOrderedByEntryId(entryId);
        var examples = exampleRepository.findAllByEntryIdOrderBySenseIdAscOrderNoAscIdAsc(entryId);
        var roles = lexemeRoleRepository.findAllById(
                        lexemes.stream().map(lexeme -> lexeme.getRoleId()).distinct().toList())
                .stream().collect(Collectors.toMap(TezaursLexemeRole::getId, Function.identity()));
        var paradigms = paradigmRepository.findAllById(
                        lexemes.stream().map(lexeme -> lexeme.getParadigmId()).filter(id -> id != null)
                                .distinct().toList())
                .stream().collect(Collectors.toMap(TezaursParadigm::getId, Function.identity()));
        Map<Integer, List<TezaursExample>> examplesBySense = examples.stream()
                .filter(example -> example.getSenseId() != null)
                .collect(Collectors.groupingBy(TezaursExample::getSenseId));

        var lexemeResponses = lexemes.stream().map(lexeme -> {
            var role = roles.get(lexeme.getRoleId());
            var paradigm = paradigms.get(lexeme.getParadigmId());
            return new TezaursEntryDetailsResponse.Lexeme(
                    lexeme.getId(), lexeme.getLemma(), lexeme.getRoleId(),
                    role == null ? null : role.getDescription(), lexeme.getParadigmId(),
                    paradigm == null ? null : paradigm.getLegacyNo(),
                    paradigm == null ? null : paradigm.getHumanKey(),
                    paradigm == null ? null : paradigm.getCaption(),
                    paradigm == null ? null : paradigm.getData(),
                    lexeme.getStem1(), lexeme.getStem2(), lexeme.getStem3(), lexeme.isHidden(),
                    lexeme.getData(), lexeme.getExtras());
        }).toList();
        var senseResponses = senses.stream().map(sense -> new TezaursEntryDetailsResponse.Sense(
                sense.getId(), sense.getParentSenseId(), sense.getOrderNo(), sense.getSenseTag(),
                sense.getGloss(), sense.isHidden(), sense.getData(),
                mapExamples(examplesBySense.getOrDefault(sense.getId(), List.of())))).toList();
        var entryExamples = mapExamples(examples.stream()
                .filter(example -> example.getSenseId() == null).toList());
        var sources = sourceLinkRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entryId).stream()
                .map(link -> new TezaursEntryDetailsResponse.SourceLink(
                        link.getId(), link.getSenseId(), link.getLexemeId(),
                        link.getSource().getAbbr(), link.getSource().getTitle(), link.getSource().getUrl()))
                .toList();
        String type = entryTypeRepository.findById(entry.getTypeId())
                .map(value -> value.getDescription()).orElse(null);

        return new TezaursEntryDetailsResponse(entry.getId(), entry.getHeading(), entry.getHumanKey(),
                entry.getTypeId(), type, entry.isHidden(), entry.getNotes(), entry.getData(),
                lexemeResponses, senseResponses, entryExamples, sources);
    }

    private static List<TezaursEntryDetailsResponse.Example> mapExamples(List<TezaursExample> examples) {
        return examples.stream().map(example -> new TezaursEntryDetailsResponse.Example(
                example.getId(), example.getContent(), example.isHidden(), example.getData())).toList();
    }
}
