package lv.tezaurs.suggestions.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionFrequencyRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class TezaursRestrictionServiceTest {
    private TezaursGrammarRestrictionTypeRepository typeRepository;
    private TezaursGrammarRestrictionFrequencyRepository frequencyRepository;
    private TezaursFlagService flagService;
    private TezaursRestrictionService service;

    @BeforeEach
    void setUp() {
        typeRepository = mock(TezaursGrammarRestrictionTypeRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        frequencyRepository = mock(TezaursGrammarRestrictionFrequencyRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        flagService = mock(TezaursFlagService.class);
        service = new TezaursRestrictionService(typeRepository, frequencyRepository, flagService);
    }

    @Test
    void acceptsARestrictionWithAFlag() {
        when(typeRepository.existsByCaption("Formā/atvasinājumā")).thenReturn(true);
        when(frequencyRepository.existsByCaption("Parasti")).thenReturn(true);
        Map<String, Object> flags = Map.of("Laiks", "Saliktie laiki");
        Map<String, Object> restriction = Map.of(
                "Restriction", "Formā/atvasinājumā",
                "Frequency", "Parasti",
                "Value", Map.of("Flags", flags));

        assertThatNoException().isThrownBy(() -> service.validate(restriction));

        verify(flagService).validate("R", flags);
    }

    @Test
    void acceptsLanguageMaterialAndNestedRestrictions() {
        when(typeRepository.existsByCaption("Kopā ar")).thenReturn(true);
        Map<String, Object> item = Map.of(
                "Restriction", "Kopā ar",
                "Value", Map.of("LanguageMaterial", List.of("vārds")));

        assertThatNoException().isThrownBy(() -> service.validate(Map.of("AND", List.of(item, item))));
        assertThatNoException().isThrownBy(() -> service.validate(Map.of("NOT", item)));
    }

    @Test
    void rejectsUnknownOptions() {
        assertThatThrownBy(() -> service.validate(Map.of("Restriction", "Nezināms")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The selected restriction type does not exist");

        when(typeRepository.existsByCaption("Formā/atvasinājumā")).thenReturn(true);
        assertThatThrownBy(() -> service.validate(Map.of(
                "Restriction", "Formā/atvasinājumā",
                "Frequency", "Nezināms")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The selected restriction frequency does not exist");
    }

    @Test
    void validatesRequiredRestrictionContent() {
        when(typeRepository.existsByCaption("Vispārīgais lietojuma biežums")).thenReturn(true);
        assertThatThrownBy(() -> service.validate(Map.of(
                "Restriction", "Vispārīgais lietojuma biežums")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Overall usage frequency requires a frequency");

        when(typeRepository.existsByCaption("Formā/atvasinājumā")).thenReturn(true);
        assertThatThrownBy(() -> service.validate(Map.of(
                "Restriction", "Formā/atvasinājumā")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A restriction requires a grammatical flag or language material");
    }

    @Test
    void rejectsMalformedValuesAndGroups() {
        when(typeRepository.existsByCaption("Kopā ar")).thenReturn(true);

        assertThatThrownBy(() -> service.validate(Map.of("AND", List.of(Map.of()))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.validate(Map.of(
                "Restriction", "Kopā ar", "Value", "vārds")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.validate(Map.of(
                "Restriction", "Kopā ar",
                "Value", Map.of("LanguageMaterial", "vārds"))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.validate(Map.of(
                "Restriction", "Kopā ar",
                "Value", Map.of("LanguageMaterial", List.of("")))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void allowsNoRestrictionAndWordFormationWithoutAValue() {
        when(typeRepository.existsByCaption("Vārddarināšana (rezultāts)")).thenReturn(true);

        assertThatNoException().isThrownBy(() -> service.validate(null));
        assertThatNoException().isThrownBy(() -> service.validate(Map.of(
                "Restriction", "Vārddarināšana (rezultāts)")));
    }
}
