package lv.tezaurs.suggestions;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "Tēzaurs suggestion review API",
        version = "v1",
        description = "API for submitting and reviewing Tēzaurs.lv suggestions"))
public class TezaursSuggestionReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(TezaursSuggestionReviewApplication.class, args);
    }

}
