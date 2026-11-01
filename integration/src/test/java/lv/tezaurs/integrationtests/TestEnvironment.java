package lv.tezaurs.integrationtests;

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.testcontainers.postgresql.PostgreSQLContainer;

public class TestEnvironment {
    private static final List<String> LEGACY_TABLES = List.of(
            "dict.entries", "dict.lexemes", "dict.senses", "dict.examples", "dict.entry_types",
            "dict.lexeme_roles", "dict.paradigms", "dict.grammar_flags", "dict.grammar_flag_values",
            "dict.grammar_restriction_types", "dict.grammar_restriction_frequencies",
            "dict.sources", "dict.source_links", "incubator.suggestions");
    private static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("integration_tests")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("fixtures/legacy-schema.sql");
    private static final Path BACKEND_LOG = Path.of("target/backend.log");
    private static Process backend;
    private static String baseUrl;

    @BeforeAll
    public static void start() throws Exception {
        Path backendJar = Path.of(System.getProperty("backend.jar"));
        if (!Files.isRegularFile(backendJar)) {
            throw new IllegalStateException("Build the backend JAR first; see integration/README.md");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(TestEnvironment::stop));
        try {
            database.start();
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            baseUrl = "http://127.0.0.1:" + port;
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            ProcessBuilder process = new ProcessBuilder(java.toString(), "-jar", backendJar.toString(),
                    "--server.address=127.0.0.1", "--server.port=" + port,
                    "--spring.datasource.url=" + database.getJdbcUrl(),
                    "--spring.datasource.username=" + database.getUsername(),
                    "--spring.datasource.password=" + database.getPassword());
            process.environment().clear();
            process.environment().put("DATABASE_PASSWORD", database.getPassword());
            process.redirectErrorStream(true);
            process.redirectOutput(BACKEND_LOG.toFile());
            backend = process.start();
            waitForBackend();
        } catch (Exception exception) {
            stop();
            throw exception;
        }
    }

    public static String baseUrl() {
        return baseUrl;
    }

    public static void resetData() throws IOException, SQLException {
        try (var resource = TestEnvironment.class.getResourceAsStream("/fixtures/scenario-data.sql")) {
            if (resource == null) {
                throw new IllegalStateException("Missing scenario-data.sql");
            }
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    public static String legacyDataSnapshot() throws SQLException {
        StringBuilder snapshot = new StringBuilder();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String table : LEGACY_TABLES) {
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

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private static void waitForBackend() throws IOException, InterruptedException {
        Instant deadline = Instant.now().plusSeconds(90);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            HttpRequest health = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(1)).GET().build();
            while (Instant.now().isBefore(deadline)) {
                if (!backend.isAlive()) {
                    throw new IllegalStateException("Test backend exited. See " + BACKEND_LOG.toAbsolutePath());
                }
                try {
                    if (client.send(health, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                        return;
                    }
                } catch (IOException notReadyYet) {
                    // Connection failures are expected while Spring starts.
                }
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("Test backend did not become healthy. See " + BACKEND_LOG.toAbsolutePath());
    }

    @AfterAll
    public static synchronized void stop() {
        if (backend != null) {
            backend.destroy();
            try {
                if (!backend.waitFor(10, TimeUnit.SECONDS)) {
                    backend.destroyForcibly();
                    backend.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException exception) {
                backend.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            backend = null;
        }
        database.stop();
    }
}
