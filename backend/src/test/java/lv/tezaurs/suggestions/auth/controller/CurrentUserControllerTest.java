package lv.tezaurs.suggestions.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import lv.tezaurs.suggestions.auth.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CurrentUserControllerTest {
    private CurrentUserService service;
    private CurrentUserController controller;

    @BeforeEach
    void setUp() {
        service = mock(CurrentUserService.class);
        controller = new CurrentUserController(service);
    }

    @Test
    void returnsTheResolvedUsername() {
        when(service.usernameFrom("authorization")).thenReturn(Optional.of("henrijs"));

        var response = controller.currentUser("authorization");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("henrijs");
    }

    @Test
    void returnsUnauthorizedWhenNoUsernameCanBeResolved() {
        when(service.usernameFrom(null)).thenReturn(Optional.empty());

        assertThat(controller.currentUser(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
