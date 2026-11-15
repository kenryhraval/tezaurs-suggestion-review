package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SuggestionImportRepository {
    private final JdbcTemplate jdbc;

    public boolean tryAcquireImportLock() {
        // Transaction-scoped lock shared by all backend instances.
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(714, 1)", Boolean.class));
    }

    public List<SourceSuggestion> findUnlinkedSuggestions() {
        // Reading status through JSON also works if the legacy column is removed.
        return jdbc.query("""
                SELECT source.id, source.lemma, (to_jsonb(source.*)->>'status')::integer AS status
                FROM incubator.suggestions source
                WHERE NOT EXISTS (
                    SELECT 1 FROM review.case_suggestions link
                    WHERE link.source_suggestion_id = source.id
                )
                ORDER BY source.id
                """, (row, rowNumber) -> new SourceSuggestion(
                row.getInt("id"), row.getString("lemma"), row.getObject("status", Integer.class)));
    }

    public record SourceSuggestion(Integer id, String lemma, Integer status) {
    }
}
