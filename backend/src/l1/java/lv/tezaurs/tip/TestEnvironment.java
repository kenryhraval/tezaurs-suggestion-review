package lv.tezaurs.tip.l1;

import io.cucumber.spring.CucumberContextConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import lv.tezaurs.tip.MainApp;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@CucumberContextConfiguration
@SpringBootTest(classes = {MainApp.class, TestEnvironment.DatabaseConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"suggestions.import-delay-ms=200", "model.models.groq-gpt-oss.api-key=",
                "model.models.gemini-flash-lite.api-key="})
public class TestEnvironment {
    private static final List<String> TEZAURS_TABLES = List.of(
            "dict.entries", "dict.lexemes", "dict.senses", "dict.examples", "dict.entry_types",
            "dict.lexeme_roles", "dict.paradigms", "dict.grammar_flags", "dict.grammar_flag_values",
            "dict.sources", "dict.source_links", "incubator.suggestions");
    private static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:18-alpine")
            .withInitScript("fixtures/legacy-schema.sql");

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return database;
        }
    }

    public static String snapshotTezaursData() throws SQLException {
        StringBuilder snapshot = new StringBuilder();
        try (Connection connection = openTestDatabaseConnection(); Statement statement = connection.createStatement()) {
            for (String table : TEZAURS_TABLES) {
                snapshot.append(table).append('\n');
                try (ResultSet rows = statement.executeQuery(
                        "SELECT row_to_json(record)::text FROM " + table + " record ORDER BY id")) {
                    while (rows.next()) {
                        snapshot.append(rows.getString(1)).append('\n');
                    }
                }
            }
        }
        return snapshot.toString();
    }

    public static Connection openTestDatabaseConnection() throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

}
