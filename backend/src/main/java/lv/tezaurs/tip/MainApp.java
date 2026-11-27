package lv.tezaurs.tip;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import lv.tezaurs.tip.common.config.DatabaseUrlInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(
        title = "Tēzaura ieteikumu perēklis API",
        version = "v1",
        description = "API for reviewing suggestions and preparing dictionary drafts"))
public class MainApp {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MainApp.class);
        application.addInitializers(new DatabaseUrlInitializer());
        application.run(args);
    }

}
