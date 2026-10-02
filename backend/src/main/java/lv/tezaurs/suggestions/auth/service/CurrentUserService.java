package lv.tezaurs.suggestions.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private static final String BASIC_PREFIX = "Basic ";

    public Optional<String> usernameFrom(String authorization) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return Optional.empty();
        }

        try {
            String credentials = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()).trim()),
                    StandardCharsets.ISO_8859_1);
            int separator = credentials.indexOf(':');
            if (separator <= 0) {
                return Optional.empty();
            }
            return Optional.of(credentials.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
