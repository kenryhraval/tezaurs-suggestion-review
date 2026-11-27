package lv.tezaurs.tip.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.tezaurs.entity.TezaursEntry;
import lv.tezaurs.tip.tezaurs.entity.TezaursGrammarFlag;
import lv.tezaurs.tip.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.tip.tezaurs.entity.TezaursLexemeRole;
import lv.tezaurs.tip.tezaurs.entity.TezaursParadigm;
import lv.tezaurs.tip.tezaurs.entity.TezaursSense;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursGrammarFlagRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursSenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.data.domain.Sort;

class TezaursServiceTest {
    private TezaursEntryRepository entryRepository;
    private TezaursSenseRepository senseRepository;
    private TezaursGrammarFlagRepository grammarFlagRepository;
    private TezaursLexemeRoleRepository lexemeRoleRepository;
    private TezaursParadigmRepository paradigmRepository;
    private TezaursService service;

    @BeforeEach
    void setUp() {
        entryRepository = mockRepository(TezaursEntryRepository.class);
        senseRepository = mockRepository(TezaursSenseRepository.class);
        grammarFlagRepository = mockRepository(TezaursGrammarFlagRepository.class);
        lexemeRoleRepository = mockRepository(TezaursLexemeRoleRepository.class);
        paradigmRepository = mockRepository(TezaursParadigmRepository.class);
        service = new TezaursService(entryRepository, senseRepository, grammarFlagRepository,
                lexemeRoleRepository, paradigmRepository);
    }

    @Test
    void returnsOptionsFromTezaursMetadata() {
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

        TezaursGrammarFlagValue activeValue = createFlagValue("Jā", false);
        TezaursGrammarFlagValue deprecatedValue = createFlagValue("Vecs", true);
        TezaursGrammarFlag flag = mock(TezaursGrammarFlag.class);
        when(flag.getName()).thenReturn("Deminutīvs");
        when(flag.getCaption()).thenReturn(null);
        when(flag.getDescription()).thenReturn("Deminutīva pazīme");
        when(flag.isMultiple()).thenReturn(false);
        when(flag.getPermittedValues()).thenReturn("E");
        when(flag.getScope()).thenReturn(new String[]{"L", "R"});
        when(flag.getValues()).thenReturn(List.of(activeValue, deprecatedValue));

        when(lexemeRoleRepository.findAll(any(Sort.class))).thenReturn(List.of(role));
        when(paradigmRepository.findAll(any(Sort.class))).thenReturn(List.of(paradigm));
        when(grammarFlagRepository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(List.of(flag));

        var result = service.formOptions();

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
    }

    @Test
    void returnsAnEntryWithItsSenses() {
        TezaursEntry entry = mock(TezaursEntry.class);
        when(entry.getId()).thenReturn(10);
        when(entry.getHeading()).thenReturn("māja");
        when(entry.getHumanKey()).thenReturn("māja:1");
        when(entryRepository.findById(10)).thenReturn(Optional.of(entry));
        TezaursSense sense = mock(TezaursSense.class);
        when(sense.getId()).thenReturn(30);
        when(sense.getGloss()).thenReturn("Dzīvojamā ēka.");
        when(senseRepository.findOrderedByEntryId(10)).thenReturn(List.of(sense));

        var result = service.findEntry(10);

        assertThat(result.id()).isEqualTo(10);
        assertThat(result.heading()).isEqualTo("māja");
        assertThat(result.humanKey()).isEqualTo("māja:1");
        assertThat(result.senses()).singleElement().satisfies(senseResponse -> {
            assertThat(senseResponse.id()).isEqualTo(30);
            assertThat(senseResponse.gloss()).isEqualTo("Dzīvojamā ēka.");
        });
        org.mockito.Mockito.verifyNoInteractions(lexemeRoleRepository, paradigmRepository);
    }

    @Test
    void returnsSensesOnlyForParentChoicesAndNumbering() {
        when(entryRepository.existsById(10)).thenReturn(true);
        TezaursSense sense = mock(TezaursSense.class);
        when(sense.getId()).thenReturn(30);
        when(sense.getParentSenseId()).thenReturn(20);
        when(sense.getOrderNo()).thenReturn(2);
        when(sense.getGloss()).thenReturn("Dzīvojamā ēka.");
        when(senseRepository.findOrderedByEntryId(10)).thenReturn(List.of(sense));

        assertThat(service.findSenses(10)).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(30);
            assertThat(result.parentSenseId()).isEqualTo(20);
            assertThat(result.orderNo()).isEqualTo(2);
            assertThat(result.gloss()).isEqualTo("Dzīvojamā ēka.");
        });
    }

    @Test
    void rejectsAnUnknownEntry() {
        when(entryRepository.findById(404)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findEntry(404))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tēzaura šķirklis “404” netika atrasts.");
        assertThatThrownBy(() -> service.findSenses(404))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tēzaura šķirklis “404” netika atrasts.");
        org.mockito.Mockito.verifyNoInteractions(senseRepository);
    }

    private static TezaursGrammarFlagValue createFlagValue(String value, boolean deprecated) {
        TezaursGrammarFlagValue flagValue = mock(TezaursGrammarFlagValue.class);
        when(flagValue.getValue()).thenReturn(value);
        when(flagValue.isDeprecated()).thenReturn(deprecated);
        return flagValue;
    }

    private static <T> T mockRepository(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.PROXY));
    }
}
