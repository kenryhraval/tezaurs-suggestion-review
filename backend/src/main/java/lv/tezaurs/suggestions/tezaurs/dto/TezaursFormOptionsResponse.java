package lv.tezaurs.suggestions.tezaurs.dto;

import java.util.List;

public record TezaursFormOptionsResponse(
        List<Option> entryTypes,
        List<Option> lexemeRoles,
        List<ParadigmOption> paradigms,
        List<FlagOption> flags) {

    public record Option(Integer id, String code, String label) {
    }

    public record ParadigmOption(Integer id, String code, String label, Integer stems) {
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
