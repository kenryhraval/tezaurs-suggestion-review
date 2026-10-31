package lv.tezaurs.suggestions.tezaurs.dto;

import java.util.List;
import java.util.Map;

public record TezaursFormOptionsResponse(
        List<Option> entryTypes,
        List<Option> lexemeRoles,
        List<ParadigmOption> paradigms,
        List<FlagOption> flags,
        List<Option> restrictionTypes,
        List<Option> restrictionFrequencies) {

    public record Option(Integer id, String code, String label) {
    }

    public record ParadigmOption(Integer id, String code, String label, Integer stems,
                                 Integer legacyNo, Map<String, Object> data) {
    }

    public record FlagOption(
            String name,
            String label,
            String description,
            boolean multiple,
            String permittedValues,
            List<String> scopes,
            List<String> values) {
    }
}
