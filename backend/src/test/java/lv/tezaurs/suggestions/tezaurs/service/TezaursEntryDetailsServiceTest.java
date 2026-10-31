package lv.tezaurs.suggestions.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntryType;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursExample;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlag;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarRestrictionFrequency;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarRestrictionType;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexeme;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexemeRole;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursParadigm;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSense;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSource;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSourceLink;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.data.domain.Sort;

class TezaursEntryDetailsServiceTest {
    private TezaursEntryRepository entryRepository;
    private TezaursLexemeRepository lexemeRepository;
    private TezaursSenseRepository senseRepository;
    private TezaursExampleRepository exampleRepository;
    private TezaursSourceLinkRepository sourceLinkRepository;
    private TezaursGrammarFlagRepository grammarFlagRepository;
    private TezaursEntryTypeRepository entryTypeRepository;
    private TezaursLexemeRoleRepository lexemeRoleRepository;
    private TezaursParadigmRepository paradigmRepository;
    private TezaursGrammarRestrictionTypeRepository restrictionTypeRepository;
    private TezaursGrammarRestrictionFrequencyRepository restrictionFrequencyRepository;
    private TezaursEntryDetailsService service;

    @BeforeEach
    void setUp() {
        entryRepository = repository(TezaursEntryRepository.class);
        lexemeRepository = repository(TezaursLexemeRepository.class);
        senseRepository = repository(TezaursSenseRepository.class);
        exampleRepository = repository(TezaursExampleRepository.class);
        sourceLinkRepository = repository(TezaursSourceLinkRepository.class);
        grammarFlagRepository = repository(TezaursGrammarFlagRepository.class);
        entryTypeRepository = repository(TezaursEntryTypeRepository.class);
        lexemeRoleRepository = repository(TezaursLexemeRoleRepository.class);
        paradigmRepository = repository(TezaursParadigmRepository.class);
        restrictionTypeRepository = repository(TezaursGrammarRestrictionTypeRepository.class);
        restrictionFrequencyRepository = repository(TezaursGrammarRestrictionFrequencyRepository.class);
        service = new TezaursEntryDetailsService(entryRepository, lexemeRepository,
                senseRepository, exampleRepository, sourceLinkRepository, grammarFlagRepository,
                entryTypeRepository, lexemeRoleRepository, paradigmRepository,
                restrictionTypeRepository, restrictionFrequencyRepository);
    }

    @Test
    void returnsOptionsFromTezaursMetadata() {
        TezaursEntryType entryType = mock(TezaursEntryType.class);
        when(entryType.getId()).thenReturn(1);
        when(entryType.getName()).thenReturn("word");
        when(entryType.getDescription()).thenReturn("Vārds");
        TezaursLexemeRole role = mock(TezaursLexemeRole.class);
        when(role.getId()).thenReturn(2);
        when(role.getName()).thenReturn("variant");
        when(role.getDescription()).thenReturn("Variants");
        TezaursParadigm paradigm = mock(TezaursParadigm.class);
        when(paradigm.getId()).thenReturn(3);
        when(paradigm.getHumanKey()).thenReturn("noun-1");
        when(paradigm.getLegacyNo()).thenReturn(15);
        when(paradigm.getCaption()).thenReturn("Lietvārds");
        when(paradigm.getData()).thenReturn(Map.of("Vārdšķira", "Lietvārds"));
        when(paradigm.getStems()).thenReturn(1);

        TezaursGrammarFlagValue activeValue = flagValue("Jā", false);
        TezaursGrammarFlagValue deprecatedValue = flagValue("Vecs", true);
        TezaursGrammarFlag flag = mock(TezaursGrammarFlag.class);
        when(flag.getName()).thenReturn("Deminutīvs");
        when(flag.getCaption()).thenReturn(null);
        when(flag.getDescription()).thenReturn("Deminutīva pazīme");
        when(flag.isMultiple()).thenReturn(false);
        when(flag.getPermittedValues()).thenReturn("E");
        when(flag.getScope()).thenReturn(new String[]{"L", "R"});
        when(flag.getValues()).thenReturn(List.of(activeValue, deprecatedValue));
        TezaursGrammarRestrictionType restrictionType = mock(TezaursGrammarRestrictionType.class);
        when(restrictionType.getId()).thenReturn(1);
        when(restrictionType.getName()).thenReturn("inForm");
        when(restrictionType.getCaption()).thenReturn("Formā/atvasinājumā");
        TezaursGrammarRestrictionFrequency frequency = mock(TezaursGrammarRestrictionFrequency.class);
        when(frequency.getId()).thenReturn(1);
        when(frequency.getName()).thenReturn("usually");
        when(frequency.getCaption()).thenReturn("Parasti");

        when(entryTypeRepository.findAll(any(Sort.class))).thenReturn(List.of(entryType));
        when(lexemeRoleRepository.findAll(any(Sort.class))).thenReturn(List.of(role));
        when(paradigmRepository.findAll(any(Sort.class))).thenReturn(List.of(paradigm));
        when(grammarFlagRepository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(List.of(flag));
        when(restrictionTypeRepository.findAll(any(Sort.class))).thenReturn(List.of(restrictionType));
        when(restrictionFrequencyRepository.findAll(any(Sort.class))).thenReturn(List.of(frequency));

        var result = service.formOptions();

        assertThat(result.entryTypes()).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(1);
            assertThat(option.code()).isEqualTo("word");
            assertThat(option.label()).isEqualTo("Vārds");
        });
        assertThat(result.lexemeRoles()).singleElement()
                .extracting("id", "code", "label")
                .containsExactly(2, "variant", "Variants");
        assertThat(result.paradigms()).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(3);
            assertThat(option.code()).isEqualTo("noun-1");
            assertThat(option.label()).isEqualTo("Lietvārds");
            assertThat(option.stems()).isEqualTo(1);
        });
        assertThat(result.flags()).singleElement().satisfies(option -> {
            assertThat(option.label()).isEqualTo("Deminutīvs");
            assertThat(option.scopes()).containsExactly("L", "R");
            assertThat(option.values()).containsExactly("Jā");
        });
        assertThat(result.restrictionTypes()).singleElement()
                .extracting("code", "label")
                .containsExactly("inForm", "Formā/atvasinājumā");
        assertThat(result.restrictionFrequencies()).singleElement()
                .extracting("code", "label")
                .containsExactly("usually", "Parasti");
    }

    @Test
    void returnsCompleteEntryDetails() {
        int entryId = 10;
        TezaursEntry entry = mock(TezaursEntry.class);
        when(entry.getId()).thenReturn(entryId);
        when(entry.getHeading()).thenReturn("māja");
        when(entry.getHumanKey()).thenReturn("māja:1");
        when(entry.getTypeId()).thenReturn(1);
        when(entry.getNotes()).thenReturn("Piezīme");
        when(entry.getData()).thenReturn(Map.of("Gram", Map.of()));

        TezaursLexeme mappedLexeme = lexeme(20, "māja", 2, 3);
        TezaursLexeme unmappedLexeme = lexeme(21, "mājas", 99, null);
        TezaursLexemeRole role = mock(TezaursLexemeRole.class);
        when(role.getId()).thenReturn(2);
        when(role.getDescription()).thenReturn("Pamatvārds");
        TezaursParadigm paradigm = mock(TezaursParadigm.class);
        when(paradigm.getId()).thenReturn(3);
        when(paradigm.getHumanKey()).thenReturn("noun-1");
        when(paradigm.getLegacyNo()).thenReturn(15);
        when(paradigm.getCaption()).thenReturn("Lietvārds");
        when(paradigm.getData()).thenReturn(Map.of("Vārdšķira", "Lietvārds"));

        TezaursSense sense = mock(TezaursSense.class);
        when(sense.getId()).thenReturn(30);
        when(sense.getOrderNo()).thenReturn(1);
        when(sense.getSenseTag()).thenReturn("tag");
        when(sense.getGloss()).thenReturn("Dzīvojamā ēka.");
        when(sense.getData()).thenReturn(Map.of("Gram", Map.of()));
        TezaursExample senseExample = example(40, 30, "Tā ir māja.");
        TezaursExample entryExample = example(41, null, "Māja stāv kalnā.");

        TezaursSource source = mock(TezaursSource.class);
        when(source.getAbbr()).thenReturn("LLVV");
        when(source.getTitle()).thenReturn("Latviešu literārās valodas vārdnīca");
        when(source.getUrl()).thenReturn("https://example.test/source");
        TezaursSourceLink sourceLink = mock(TezaursSourceLink.class);
        when(sourceLink.getId()).thenReturn(50);
        when(sourceLink.getSenseId()).thenReturn(30);
        when(sourceLink.getLexemeId()).thenReturn(20);
        when(sourceLink.getSource()).thenReturn(source);
        TezaursEntryType type = mock(TezaursEntryType.class);
        when(type.getDescription()).thenReturn("Vārds");

        when(entryRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entryId))
                .thenReturn(List.of(mappedLexeme, unmappedLexeme));
        when(senseRepository.findOrderedByEntryId(entryId)).thenReturn(List.of(sense));
        when(exampleRepository.findAllByEntryIdOrderBySenseIdAscOrderNoAscIdAsc(entryId))
                .thenReturn(List.of(entryExample, senseExample));
        when(lexemeRoleRepository.findAllById(List.of(2, 99))).thenReturn(List.of(role));
        when(paradigmRepository.findAllById(List.of(3))).thenReturn(List.of(paradigm));
        when(sourceLinkRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entryId))
                .thenReturn(List.of(sourceLink));
        when(entryTypeRepository.findById(1)).thenReturn(Optional.of(type));

        var result = service.findEntry(entryId);

        assertThat(result.heading()).isEqualTo("māja");
        assertThat(result.humanKey()).isEqualTo("māja:1");
        assertThat(result.type()).isEqualTo("Vārds");
        assertThat(result.lexemes()).hasSize(2);
        assertThat(result.lexemes().getFirst().role()).isEqualTo("Pamatvārds");
        assertThat(result.lexemes().getFirst().paradigmCode()).isEqualTo("noun-1");
        assertThat(result.lexemes().getFirst().paradigmLegacyNo()).isEqualTo(15);
        assertThat(result.lexemes().getFirst().paradigm()).isEqualTo("Lietvārds");
        assertThat(result.lexemes().getFirst().paradigmData())
                .containsEntry("Vārdšķira", "Lietvārds");
        assertThat(result.lexemes().getFirst().extras())
                .containsEntry("ratioPerMille", 0.1);
        assertThat(result.lexemes().get(1).role()).isNull();
        assertThat(result.lexemes().get(1).paradigm()).isNull();
        assertThat(result.senses()).singleElement().satisfies(mappedSense -> {
            assertThat(mappedSense.gloss()).isEqualTo("Dzīvojamā ēka.");
            assertThat(mappedSense.examples()).singleElement()
                    .extracting("content").isEqualTo("Tā ir māja.");
        });
        assertThat(result.examples()).singleElement()
                .extracting("content").isEqualTo("Māja stāv kalnā.");
        assertThat(result.sources()).singleElement().satisfies(mappedSource -> {
            assertThat(mappedSource.abbreviation()).isEqualTo("LLVV");
            assertThat(mappedSource.url()).isEqualTo("https://example.test/source");
        });
    }

    @Test
    void rejectsAnUnknownEntry() {
        when(entryRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findEntry(404))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tēzaurs entry 404 was not found");
    }

    private static TezaursLexeme lexeme(
            int id, String lemma, int roleId, Integer paradigmId) {
        TezaursLexeme lexeme = mock(TezaursLexeme.class);
        when(lexeme.getId()).thenReturn(id);
        when(lexeme.getLemma()).thenReturn(lemma);
        when(lexeme.getRoleId()).thenReturn(roleId);
        when(lexeme.getParadigmId()).thenReturn(paradigmId);
        when(lexeme.getStem1()).thenReturn("celms");
        when(lexeme.getData()).thenReturn(Map.of("Gram", Map.of()));
        when(lexeme.getExtras()).thenReturn(Map.of("ratioPerMille", 0.1));
        return lexeme;
    }

    private static TezaursExample example(int id, Integer senseId, String content) {
        TezaursExample example = mock(TezaursExample.class);
        when(example.getId()).thenReturn(id);
        when(example.getSenseId()).thenReturn(senseId);
        when(example.getContent()).thenReturn(content);
        when(example.getData()).thenReturn(Map.of());
        return example;
    }

    private static TezaursGrammarFlagValue flagValue(String value, boolean deprecated) {
        TezaursGrammarFlagValue flagValue = mock(TezaursGrammarFlagValue.class);
        when(flagValue.getValue()).thenReturn(value);
        when(flagValue.isDeprecated()).thenReturn(deprecated);
        return flagValue;
    }

    private static <T> T repository(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }
}
