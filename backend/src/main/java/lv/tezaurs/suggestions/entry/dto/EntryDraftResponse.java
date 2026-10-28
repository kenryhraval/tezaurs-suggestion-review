package lv.tezaurs.suggestions.entry.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;

public record EntryDraftResponse(
        UUID id,
        String heading,
        String humanKey,
        Integer typeId,
        String etymology,
        String normativeComment,
        String scientificName,
        String source,
        String notes,
        Map<String, Object> flags,
        List<LexemeDraftResponse> lexemes) {

    public static EntryDraftResponse from(ReviewEntry entry, List<ReviewLexeme> lexemes) {
        return new EntryDraftResponse(entry.getId(), entry.getHeading(), entry.humanKey(),
                entry.getTypeId(),
                entry.getEtymology(), entry.getNormativeComment(),
                entry.getScientificName(), entry.getSource(), entry.getNotes(),
                entry.getFlags(),
                lexemes.stream().map(LexemeDraftResponse::from).toList());
    }

    public record LexemeDraftResponse(
            UUID id,
            String lemma,
            boolean primary,
            Integer roleId,
            Integer paradigmId,
            String stem1,
            String stem2,
            String stem3,
            List<String> pronunciations,
            Map<String, Object> flags,
            Map<String, Object> structuralRestrictions) {

        public static LexemeDraftResponse from(ReviewLexeme lexeme) {
            return new LexemeDraftResponse(lexeme.getId(), lexeme.getLemma(), lexeme.isPrimary(),
                    lexeme.getRoleId(), lexeme.getParadigmId(), lexeme.getStem1(),
                    lexeme.getStem2(), lexeme.getStem3(), lexeme.getPronunciations(),
                    lexeme.getFlags(), lexeme.getStructuralRestrictions());
        }
    }
}
