package lv.tezaurs.tip.entry.dto;

import java.util.List;
import java.util.Map;
import lv.tezaurs.tip.entry.entity.EntryType;
import java.util.UUID;
import lv.tezaurs.tip.entry.entity.Entry;
import lv.tezaurs.tip.entry.entity.Lexeme;

public record EntryResponse(
        UUID id,
        String heading,
        String humanKey,
        EntryType type,
        String etymology,
        String normativeComment,
        String scientificName,
        String source,
        String notes,
        Map<String, Object> flags,
        List<LexemeResponse> lexemes) {

    public static EntryResponse from(Entry entry, List<Lexeme> lexemes) {
        return new EntryResponse(entry.getId(), entry.getHeading(), entry.humanKey(),
                entry.getType(),
                entry.getEtymology(), entry.getNormativeComment(),
                entry.getScientificName(), entry.getSource(), entry.getNotes(),
                entry.getFlags(),
                lexemes.stream().map(LexemeResponse::from).toList());
    }

    public record LexemeResponse(
            UUID id,
            String lemma,
            boolean primary,
            Integer roleId,
            Integer paradigmId,
            String stem1,
            String stem2,
            String stem3,
            List<String> pronunciations,
            Map<String, Object> flags) {

        public static LexemeResponse from(Lexeme lexeme) {
            return new LexemeResponse(lexeme.getId(), lexeme.getLemma(), lexeme.isPrimary(),
                    lexeme.getRoleId(), lexeme.getParadigmId(), lexeme.getStem1(),
                    lexeme.getStem2(), lexeme.getStem3(), lexeme.getPronunciations(),
                    lexeme.getFlags());
        }
    }
}
