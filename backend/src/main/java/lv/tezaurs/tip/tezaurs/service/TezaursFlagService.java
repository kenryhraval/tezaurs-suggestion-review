package lv.tezaurs.tip.tezaurs.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.tezaurs.entity.TezaursGrammarFlag;
import lv.tezaurs.tip.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.tip.tezaurs.repository.TezaursGrammarFlagRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TezaursFlagService {
    private final TezaursGrammarFlagRepository repository;

    public void validate(String scope, Map<String, Object> flags) {
        if (flags.isEmpty()) {
            return;
        }

        Map<String, TezaursGrammarFlag> definitions = repository
                .findAllByDeprecatedFalseOrderByOrderNoAscNameAsc().stream()
                .collect(Collectors.toMap(TezaursGrammarFlag::getName, Function.identity()));

        flags.forEach((name, value) -> validateFlag(scope, name, value, definitions.get(name)));
    }

    private static void validateFlag(
            String scope, String name, Object value, TezaursGrammarFlag definition) {
        if (definition == null || !Arrays.asList(definition.getScope()).contains(scope)) {
            throw new ConflictException("Pazīmi “" + name + "” šeit nevar izmantot.");
        }
        if (value == null) {
            return;
        }

        List<?> submittedValues = getSubmittedValues(name, value, definition);

        if (submittedValues.stream().anyMatch(item -> !(item instanceof String))) {
            throw new ConflictException("Pazīmes “" + name + "” vērtībām jābūt tekstam.");
        }
        if (!"E".equals(definition.getPermittedValues())) {
            return;
        }

        Set<String> allowedValues = definition.getValues().stream()
                .filter(item -> !item.isDeprecated())
                .map(TezaursGrammarFlagValue::getValue)
                .collect(Collectors.toSet());
        if (submittedValues.stream().anyMatch(item -> !allowedValues.contains(item))) {
            throw new ConflictException("Pazīmei “" + name + "” norādīta neatļauta vērtība.");
        }
    }

    private static @NonNull List<?> getSubmittedValues(String name, Object value, TezaursGrammarFlag definition) {
        List<?> submittedValues;
        if (definition.isMultiple()) {
            if (!(value instanceof List<?> values)) {
                throw new ConflictException("Pazīmes “" + name + "” vērtības jānorāda sarakstā.");
            }
            submittedValues = values;
        } else {
            if (!(value instanceof String)) {
                throw new ConflictException("Pazīmei “" + name + "” jānorāda viena teksta vērtība.");
            }
            submittedValues = List.of(value);
        }
        return submittedValues;
    }

    // generate json for the AI of allowed flags for given review
}
