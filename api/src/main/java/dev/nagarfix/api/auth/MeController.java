package dev.nagarfix.api.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Who am I?" - needs a valid token. */
@RestController
public class MeController {

    private final UserService users;

    public MeController(UserService users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return UserView.of(users.get(Long.parseLong(jwt.getSubject())));
    }
}
