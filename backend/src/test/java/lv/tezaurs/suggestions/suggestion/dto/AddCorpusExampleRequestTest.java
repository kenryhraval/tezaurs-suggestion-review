package lv.tezaurs.suggestions.suggestion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AddCorpusExampleRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAndTrimsHttpLinks() {
        AddCorpusExampleRequest request = new AddCorpusExampleRequest(" https://korpuss.lv/id/42 ");

        assertThat(request.url()).isEqualTo("https://korpuss.lv/id/42");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsUnsupportedOrBlankLinks() {
        assertThat(validator.validate(new AddCorpusExampleRequest("javascript:alert(1)")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("url");
        assertThat(validator.validate(new AddCorpusExampleRequest(" ")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("url");
    }
}
