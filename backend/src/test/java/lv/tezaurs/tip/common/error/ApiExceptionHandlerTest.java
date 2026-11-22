package lv.tezaurs.tip.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    @Test
    void returnsANotFoundError() {
        var request = new MockHttpServletRequest("GET", "/api/suggestions/test");

        var response = new ApiExceptionHandler().notFound(new NotFoundException("missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("missing");
        assertThat(response.getBody().path()).isEqualTo("/api/suggestions/test");
    }

    @Test
    void returnsAConflictWithLatvianGuidanceForAnOptimisticLockFailure() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ConcurrentUpdateController())
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(post("/test/concurrent-update"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Dati pa šo laiku ir mainījušies. Pārlādējiet lapu un mēģiniet vēlreiz."));
    }

    @RestController
    static class ConcurrentUpdateController {
        @PostMapping("/test/concurrent-update")
        void update() {
            throw new ObjectOptimisticLockingFailureException(Review.class, UUID.randomUUID());
        }
    }
}
