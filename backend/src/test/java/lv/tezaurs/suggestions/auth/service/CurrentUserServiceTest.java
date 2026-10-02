package lv.tezaurs.suggestions.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CurrentUserServiceTest {
    private final CurrentUserService service = new CurrentUserService();

    @Test
    void extractsTheBasicAuthUsername() {
        assertThat(service.usernameFrom(basicAuthorization("henrijs", "secret")))
                .contains("henrijs");
    }

    @Test
    void allowsColonsInThePassword() {
        assertThat(service.usernameFrom(basicAuthorization("henrijs", "secret:with:colons")))
                .contains("henrijs");
    }

    @Test
    void rejectsMissingOrMalformedCredentials() {
        assertThat(service.usernameFrom(null)).isEmpty();
        assertThat(service.usernameFrom("Bearer token")).isEmpty();
        assertThat(service.usernameFrom("Basic not-base64")).isEmpty();
        assertThat(service.usernameFrom("Basic " + Base64.getEncoder().encodeToString(
                "missing-separator".getBytes(StandardCharsets.ISO_8859_1)))).isEmpty();
    }

    private static String basicAuthorization(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.ISO_8859_1));
    }
}
