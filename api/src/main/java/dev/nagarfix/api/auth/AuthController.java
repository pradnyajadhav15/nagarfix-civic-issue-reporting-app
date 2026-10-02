package dev.nagarfix.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sign up (always as a citizen), log in, or try a demo account. All return a token. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record DemoRequest(@NotNull Role role) {
    }

    public record AuthResponse(String token, UserView user) {
    }

    private final UserService users;
    private final JwtService jwt;

    public AuthController(UserService users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        AppUser user = users.create(req.fullName(), req.email(), req.password(), Role.CITIZEN, null);
        return new AuthResponse(jwt.issue(user), UserView.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.authenticate(req.email(), req.password());
        return new AuthResponse(jwt.issue(user), UserView.of(user));
    }

    /** One-click demo login: CITIZEN, OFFICER (zone Z13) or ADMIN (read-only). */
    @PostMapping("/demo")
    public AuthResponse demo(@Valid @RequestBody DemoRequest req) {
        AppUser user = users.demoUser(req.role());
        return new AuthResponse(jwt.issue(user), UserView.of(user));
    }
}
