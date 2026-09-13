package lv.tezaurs.suggestions.suggestion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CreateSuggestionRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankRequiredFieldsAndInvalidEmail() {
        CreateSuggestionRequest request = new CreateSuggestionRequest(" ", "", null, null, null, "invalid");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("term", "definition", "submitterEmail");
    }

    @Test
    void acceptsACompleteSubmission() {
        CreateSuggestionRequest request = new CreateSuggestionRequest("jaunvārds", "Skaidrojums", "Piemērs",
                "Piezīme", "Iesniedzējs", "submitter@example.test");

        assertThat(validator.validate(request)).isEmpty();
    }
}
