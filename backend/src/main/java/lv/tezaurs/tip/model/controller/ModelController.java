package lv.tezaurs.tip.model.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.model.dto.ModelOption;
import lv.tezaurs.tip.model.service.ModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModelController {
    private final ModelService modelService;

    @GetMapping("/api/sense-models")
    List<ModelOption> getModelOptions() {
        return modelService.getAvailableModelOptions();
    }
}
