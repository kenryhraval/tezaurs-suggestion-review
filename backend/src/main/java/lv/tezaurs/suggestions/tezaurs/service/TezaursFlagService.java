package lv.tezaurs.suggestions.tezaurs.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlag;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarFlagValue;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursGrammarFlagRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TezaursFlagService {
    private final TezaursGrammarFlagRepository repository;

    public void validateChanges(String scope, Map<String, Object> flags, Map<String, Object> previous) {
        Map<String, Object> changed = new java.util.HashMap<>();
        flags.forEach((name, value) -> {
            if (!previous.containsKey(name) || !java.util.Objects.equals(previous.get(name), value)) {
                changed.put(name, value);
            }
        });
        if (!changed.isEmpty()) validate(scope, changed);
    }

    public void validate(String scope, Map<String, Object> flags) {
        Map<String, TezaursGrammarFlag> definitions = repository
                .findAllByDeprecatedFalseOrderByOrderNoAscNameAsc().stream()
                .collect(Collectors.toMap(TezaursGrammarFlag::getName, Function.identity()));

        flags.forEach((name, value) -> validateFlag(scope, name, value, definitions.get(name)));
    }

    private static void validateFlag(
            String scope, String name, Object value, TezaursGrammarFlag definition) {
        if (definition == null || !Arrays.asList(definition.getScope()).contains(scope)) {
            throw new ConflictException("Flag " + name + " is not available for scope " + scope);
        }
        if (!definition.isMultiple() && value instanceof Collection<?>) {
            throw new ConflictException("Flag " + name + " only accepts one value");
        }
        if (value == null || !"E".equals(definition.getPermittedValues())) {
            return;
        }

        Set<String> allowedValues = definition.getValues().stream()
                .filter(item -> !item.isDeprecated())
                .map(TezaursGrammarFlagValue::getValue)
                .collect(Collectors.toSet());
        Collection<?> submittedValues = value instanceof Collection<?> collection
                ? collection : java.util.List.of(value);
        if (submittedValues.stream().anyMatch(item -> !(item instanceof String text)
                || !allowedValues.contains(text))) {
            throw new ConflictException("Flag " + name + " contains an unsupported value");
        }
    }
}
