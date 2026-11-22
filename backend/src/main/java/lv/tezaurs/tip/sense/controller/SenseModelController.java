package lv.tezaurs.tip.sense.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.sense.dto.ClusterSensesRequest;
import lv.tezaurs.tip.sense.dto.GenerateDefinitionsRequest;
import lv.tezaurs.tip.sense.service.SenseClusteringService;
import lv.tezaurs.tip.sense.service.SenseDefinitionService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/sense-research")
@RequiredArgsConstructor
public class SenseModelController {
    private final SenseClusteringService clusteringService;
    private final SenseDefinitionService definitionService;
    private final CurrentUserService currentUserService;

    @PostMapping("/regroup")
    List<SenseResponse> regroupSenses(@PathVariable UUID reviewId, @Valid @RequestBody ClusterSensesRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        currentUserService.requireUsername(authorization);
        return clusteringService.regroupSenses(reviewId, request);
    }

    @PostMapping("/definitions")
    List<SenseResponse> generateSenseDefinitions(@PathVariable UUID reviewId, @Valid @RequestBody GenerateDefinitionsRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        currentUserService.requireUsername(authorization);
        return definitionService.generateSenseDefinitions(reviewId, request);
    }
}
