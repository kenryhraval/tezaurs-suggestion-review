package lv.tezaurs.suggestions.auth.controller;

import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.auth.dto.CurrentUserResponse;
import lv.tezaurs.suggestions.auth.service.CurrentUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CurrentUserController {
    private final CurrentUserService service;

    @GetMapping("/api/me")
    ResponseEntity<CurrentUserResponse> currentUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.usernameFrom(authorization)
                .map(username -> ResponseEntity.ok(new CurrentUserResponse(username)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
