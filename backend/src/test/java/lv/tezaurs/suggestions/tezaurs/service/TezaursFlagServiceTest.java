package lv.tezaurs.suggestions.tezaurs.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlag;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class TezaursFlagServiceTest {
    private TezaursGrammarFlagRepository repository;
    private TezaursFlagService service;

    @BeforeEach
    void setUp() {
        repository = mock(TezaursGrammarFlagRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        service = new TezaursFlagService(repository);
    }

    @Test
    void acceptsSupportedSingleAndMultipleEnumValues() {
        var definitions = List.of(
                flag("Deminutīvs", false, "E", new String[]{"L"}, value("Jā", false)),
                flag("Lietojums", true, "E", new String[]{"L"},
                        value("Sarunvaloda", false), value("Novecojis", true)));
        when(repository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(definitions);

        assertThatCode(() -> service.validate("L", Map.of(
                "Deminutīvs", "Jā",
                "Lietojums", List.of("Sarunvaloda"))))
                .doesNotThrowAnyException();
    }

    @Test
    void preservesUnchangedLegacyFlagsButValidatesAnyChanges() {
        var legacy = Map.<String, Object>of("Veca pazīme", "vērtība");
        assertThatCode(() -> service.validateChanges("L", legacy, legacy)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validateChanges("L", Map.of("Veca pazīme", "jauna vērtība"), legacy))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptsNullAndFreeTextValuesWithoutAnEnumerationLookup() {
        var definitions = List.of(flag("Piezīmes", true, "F", new String[]{"L"}));
        when(repository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(definitions);

        assertThatCode(() -> service.validate("L", Map.of("Piezīmes", List.of("Piezīme"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validate("L",
                java.util.Collections.singletonMap("Piezīmes", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownFlagsAndFlagsFromAnotherScope() {
        var definitions = List.of(
                flag("Deminutīvs", false, "E", new String[]{"L"}, value("Jā", false)));
        when(repository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(definitions);

        assertThatThrownBy(() -> service.validate("L", Map.of("Nezināms", "Jā")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Flag Nezināms is not available for scope L");
        assertThatThrownBy(() -> service.validate("E", Map.of("Deminutīvs", "Jā")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Flag Deminutīvs is not available for scope E");
    }

    @Test
    void rejectsSeveralValuesForASingleValueFlag() {
        var definitions = List.of(
                flag("Deminutīvs", false, "E", new String[]{"L"}, value("Jā", false)));
        when(repository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(definitions);

        assertThatThrownBy(() -> service.validate("L", Map.of("Deminutīvs", List.of("Jā"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Flag Deminutīvs only accepts one value");
    }

    @Test
    void rejectsUnsupportedAndDeprecatedEnumValues() {
        var definitions = List.of(
                flag("Lietojums", true, "E", new String[]{"L"},
                        value("Sarunvaloda", false), value("Novecojis", true)));
        when(repository.findAllByDeprecatedFalseOrderByOrderNoAscNameAsc())
                .thenReturn(definitions);

        assertThatThrownBy(() -> service.validate("L", Map.of("Lietojums", List.of("Cits"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Flag Lietojums contains an unsupported value");
        assertThatThrownBy(() -> service.validate("L", Map.of("Lietojums", List.of("Novecojis"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Flag Lietojums contains an unsupported value");
        assertThatThrownBy(() -> service.validate("L", Map.of("Lietojums", List.of(42))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Flag Lietojums contains an unsupported value");
    }

    private static TezaursGrammarFlag flag(
            String name, boolean multiple, String permittedValues, String[] scopes,
            TezaursGrammarFlagValue... values) {
        TezaursGrammarFlag flag = mock(TezaursGrammarFlag.class);
        when(flag.getName()).thenReturn(name);
        when(flag.isMultiple()).thenReturn(multiple);
        when(flag.getPermittedValues()).thenReturn(permittedValues);
        when(flag.getScope()).thenReturn(scopes);
        when(flag.getValues()).thenReturn(List.of(values));
        return flag;
    }

    private static TezaursGrammarFlagValue value(String value, boolean deprecated) {
        TezaursGrammarFlagValue flagValue = mock(TezaursGrammarFlagValue.class);
        when(flagValue.getValue()).thenReturn(value);
        when(flagValue.isDeprecated()).thenReturn(deprecated);
        return flagValue;
    }
}
