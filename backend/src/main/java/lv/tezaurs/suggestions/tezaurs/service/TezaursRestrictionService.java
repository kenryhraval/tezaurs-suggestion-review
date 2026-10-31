package lv.tezaurs.suggestions.tezaurs.service;

import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionFrequencyRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarRestrictionTypeRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TezaursRestrictionService {
    private static final String OVERALL_FREQUENCY = "Vispārīgais lietojuma biežums";
    private static final String WORD_FORMATION_PART = "Vārddarināšana (sastāvdaļa)";
    private static final String WORD_FORMATION_RESULT = "Vārddarināšana (rezultāts)";

    private final TezaursGrammarRestrictionTypeRepository typeRepository;
    private final TezaursGrammarRestrictionFrequencyRepository frequencyRepository;
    private final TezaursFlagService flagService;

    public void validate(Map<String, Object> restriction) {
        if (restriction == null) {
            return;
        }
        if (restriction.containsKey("AND")) {
            validateGroup("AND", restriction.get("AND"));
            return;
        }
        if (restriction.containsKey("OR")) {
            validateGroup("OR", restriction.get("OR"));
            return;
        }
        if (restriction.containsKey("NOT")) {
            validateNested(restriction.get("NOT"));
            return;
        }
        validateSimple(restriction);
    }

    private void validateGroup(String operator, Object value) {
        if (!(value instanceof Collection<?> items) || items.size() < 2) {
            throw new ConflictException(operator + " requires at least two restrictions");
        }
        items.forEach(this::validateNested);
    }

    private void validateNested(Object value) {
        if (!(value instanceof Map<?, ?> nested)) {
            throw new ConflictException("A nested restriction must be an object");
        }
        validate(stringKeyMap(nested));
    }

    private void validateSimple(Map<String, Object> restriction) {
        String type = requiredText(restriction.get("Restriction"), "Restriction type is required");
        if (!typeRepository.existsByCaption(type)) {
            throw new ConflictException("The selected restriction type does not exist");
        }

        String frequency = optionalText(restriction.get("Frequency"));
        if (frequency != null && !frequencyRepository.existsByCaption(frequency)) {
            throw new ConflictException("The selected restriction frequency does not exist");
        }
        if (OVERALL_FREQUENCY.equals(type) && frequency == null) {
            throw new ConflictException("Overall usage frequency requires a frequency");
        }

        Map<String, Object> value = optionalMap(restriction.get("Value"));
        Map<String, Object> flags = value == null ? null : optionalMap(value.get("Flags"));
        Collection<?> languageMaterial = value == null
                ? null : optionalCollection(value.get("LanguageMaterial"));

        if (flags != null && !flags.isEmpty()) {
            flagService.validate("R", flags);
        }
        if (languageMaterial != null) {
            validateLanguageMaterial(languageMaterial);
        }
        if (requiresValue(type) && isEmpty(flags) && isEmpty(languageMaterial)) {
            throw new ConflictException(
                    "A restriction requires a grammatical flag or language material");
        }
    }

    private static boolean requiresValue(String type) {
        return !OVERALL_FREQUENCY.equals(type)
                && !WORD_FORMATION_PART.equals(type)
                && !WORD_FORMATION_RESULT.equals(type);
    }

    private static void validateLanguageMaterial(Collection<?> values) {
        if (values.stream().anyMatch(value -> !(value instanceof String text)
                || text.isBlank())) {
            throw new ConflictException("Language material must contain non-empty text values");
        }
    }

    private static String requiredText(Object value, String message) {
        String text = optionalText(value);
        if (text == null) {
            throw new ConflictException(message);
        }
        return text;
    }

    private static String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ConflictException("Restriction text values must be strings");
        }
        return text.isBlank() ? null : text;
    }

    private static Map<String, Object> optionalMap(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConflictException("Restriction values must be objects");
        }
        return stringKeyMap(map);
    }

    private static Collection<?> optionalCollection(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new ConflictException("Language material must be a list");
        }
        return collection;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> map) {
        if (map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new ConflictException("Restriction object keys must be strings");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typedMap = (Map<String, Object>) map;
        return typedMap;
    }

    private static boolean isEmpty(Map<?, ?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }
}
