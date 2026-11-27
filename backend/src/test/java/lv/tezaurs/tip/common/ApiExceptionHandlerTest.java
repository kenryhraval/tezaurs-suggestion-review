package lv.tezaurs.tip.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reviews/missing");

    @Test
    void notFoundIncludesTheMessageAndRequestPath() {
        var response = handler.notFound(new NotFoundException("missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("missing");
        assertThat(response.getBody().path()).isEqualTo("/api/reviews/missing");
    }

    @Test
    void concurrentUpdatesExplainHowToRecover() {
        var response = handler.concurrentUpdate(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo(
                "Dati pa šo laiku ir mainījušies. Pārlādējiet lapu un mēģiniet vēlreiz.");
    }
}
