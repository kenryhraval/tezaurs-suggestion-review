package lv.tezaurs.suggestions.common.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/** Converts a PostgreSQL connection URI into Spring JDBC datasource properties. */
public final class DatabaseUrlInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String databaseUrl = applicationContext.getEnvironment().getProperty("DB_URL");
        if (!StringUtils.hasText(databaseUrl)) {
            return;
        }

        DatabaseConnection connection = parse(databaseUrl);
        applicationContext.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "databaseUrl",
                Map.of(
                        "spring.datasource.url", connection.jdbcUrl(),
                        "spring.datasource.username", connection.username(),
                        "spring.datasource.password", connection.password())));
    }

    static DatabaseConnection parse(String databaseUrl) {
        URI uri;
        try {
            uri = new URI(databaseUrl);
        } catch (URISyntaxException exception) {
            throw invalidDatabaseUrl("is not a valid URI", exception);
        }

        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw invalidDatabaseUrl("must use the postgres:// or postgresql:// scheme", null);
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw invalidDatabaseUrl("must contain a database host", null);
        }
        if (uri.getRawPath() == null || uri.getRawPath().length() <= 1) {
            throw invalidDatabaseUrl("must contain a database name", null);
        }

        String rawUserInfo = uri.getRawUserInfo();
        int passwordSeparator = rawUserInfo == null ? -1 : rawUserInfo.indexOf(':');
        if (passwordSeparator <= 0 || passwordSeparator == rawUserInfo.length() - 1) {
            throw invalidDatabaseUrl("must contain a username and password", null);
        }

        String username = decode(rawUserInfo.substring(0, passwordSeparator));
        String password = decode(rawUserInfo.substring(passwordSeparator + 1));
        String host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
        if (uri.getPort() != -1) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        return new DatabaseConnection(jdbcUrl.toString(), username, password);
    }

    private static String decode(String value) {
        try {
            // In URI user-info, '+' is a literal plus rather than a space.
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidDatabaseUrl("contains invalid percent-encoding in its credentials", exception);
        }
    }

    private static IllegalArgumentException invalidDatabaseUrl(String detail, Exception cause) {
        return new IllegalArgumentException("DB_URL " + detail, cause);
    }

    record DatabaseConnection(String jdbcUrl, String username, String password) {
    }
}
