package lv.tezaurs.suggestions.tezaurs.dto;

import java.util.List;
import java.util.Map;

public record TezaursEntryDetailsResponse(
        Integer id,
        String heading,
        String humanKey,
        Integer typeId,
        String type,
        boolean hidden,
        String notes,
        Map<String, Object> data,
        List<Lexeme> lexemes,
        List<Sense> senses,
        List<Example> examples,
        List<SourceLink> sources) {

    public record Lexeme(
            Integer id,
            String lemma,
            Integer roleId,
            String role,
            Integer paradigmId,
            Integer paradigmLegacyNo,
            String paradigmCode,
            String paradigm,
            Map<String, Object> paradigmData,
            String stem1,
            String stem2,
            String stem3,
            boolean hidden,
            Map<String, Object> data,
            Map<String, Object> extras) {
    }

    public record Sense(
            Integer id,
            Integer parentSenseId,
            Integer orderNo,
            String senseTag,
            String gloss,
            boolean hidden,
            Map<String, Object> data,
            List<Example> examples) {
    }

    public record Example(
            Integer id,
            String content,
            boolean hidden,
            Map<String, Object> data) {
    }

    public record SourceLink(
            Integer id,
            Integer senseId,
            Integer lexemeId,
            String abbreviation,
            String title,
            String url) {
    }
}
