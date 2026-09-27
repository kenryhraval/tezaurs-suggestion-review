package lv.tezaurs.suggestions.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class DatabaseUrlInitializerTest {

    @Test
    void addsDatasourcePropertiesToTheApplicationEnvironment() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test", Map.of("DB_URL", "postgres://user:pwd@127.0.0.1:5432/beta_dv")));

            new DatabaseUrlInitializer().initialize(context);

            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://127.0.0.1:5432/beta_dv");
            assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                    .isEqualTo("user");
            assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                    .isEqualTo("pwd");
        }
    }

    @Test
    void preservesQueryAndDecodesCredentials() {
        DatabaseUrlInitializer.DatabaseConnection connection = DatabaseUrlInitializer.parse(
                "postgresql://review%40user:p%3Ass+word@db.example.lv/review?sslmode=require");

        assertThat(connection.jdbcUrl())
                .isEqualTo("jdbc:postgresql://db.example.lv/review?sslmode=require");
        assertThat(connection.username()).isEqualTo("review@user");
        assertThat(connection.password()).isEqualTo("p:ss+word");
    }

    @Test
    void rejectsUnsupportedOrIncompleteUrls() {
        assertThatThrownBy(() -> DatabaseUrlInitializer.parse("mysql://user:pwd@localhost/review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DB_URL must use the postgres:// or postgresql:// scheme");
        assertThatThrownBy(() -> DatabaseUrlInitializer.parse("postgres://localhost/review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DB_URL must contain a username and password");
        assertThatThrownBy(() -> DatabaseUrlInitializer.parse("postgres://user:pwd@localhost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DB_URL must contain a database name");
    }
}
