package lv.tezaurs.tip.l1;

import static org.junit.jupiter.api.Assertions.fail;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataSteps {
    private record FixtureRow(String table, Map<String, String> values) {
    }

    private static final List<FixtureRow> pendingRows = new ArrayList<>();

    @Before
    public void resetScenarioData() throws SQLException {
        pendingRows.clear();
        try (Connection connection = TestEnvironment.openTestDatabaseConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE review.cases, incubator.suggestions, dict.entries, dict.lexemes, dict.senses,
                        dict.examples, dict.entry_types, dict.lexeme_roles, dict.paradigms,
                        dict.grammar_flags, dict.grammar_flag_values, dict.sources, dict.source_links CASCADE;
                    INSERT INTO dict.entry_types (id, name) VALUES (1, 'word');
                    """);
        }
    }

    @Given("the table {string} contains:")
    public void queueFixtureRows(String table, List<Map<String, String>> rows) {
        rows.forEach(row -> {
            Map<String, String> values = new LinkedHashMap<>(row);
            if (table.startsWith("review.")) {
                values.putIfAbsent("created_at", "2026-01-01T00:00:00Z");
                if (!table.equals("review.case_suggestions") && !table.equals("review.examples")) {
                    values.putIfAbsent("updated_at", "2026-01-01T00:00:00Z");
                }
            }
            pendingRows.add(new FixtureRow(table, values));
        });
    }

    @When("the scheduled import has run")
    public void waitForScheduledImport() throws Exception {
        insertPendingFixtureRows();
        Instant deadline = Instant.now().plusSeconds(10);
        try (Connection connection = TestEnvironment.openTestDatabaseConnection(); Statement statement = connection.createStatement()) {
            while (statement.executeQuery("""
                    SELECT 1 FROM incubator.suggestions suggestion
                    WHERE NOT EXISTS (SELECT 1 FROM review.case_suggestions link
                                      WHERE link.source_suggestion_id = suggestion.id)
                    """).next()) {
                if (Instant.now().isAfter(deadline)) {
                    fail("The importer did not import all suggestions");
                }
                Thread.sleep(50);
            }
        }
    }

    static void insertPendingFixtureRows() throws SQLException {
        if (pendingRows.isEmpty()) {
            return;
        }
        try (Connection connection = TestEnvironment.openTestDatabaseConnection()) {
            connection.setAutoCommit(false);
            for (FixtureRow row : pendingRows) {
                String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(row.table(),
                        String.join(", ", row.values().keySet()),
                        String.join(", ", Collections.nCopies(row.values().size(), "?")));
                try (PreparedStatement insert = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (String value : row.values().values()) {
                        insert.setObject(index++, value, Types.OTHER);
                    }
                    insert.executeUpdate();
                }
            }
            connection.commit();
        }
        pendingRows.clear();
    }
}
