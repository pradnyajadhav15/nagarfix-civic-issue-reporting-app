package dev.nagarfix.api.auth;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

/** Admin-only (see SecurityConfig): list users, create officers or other admins. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            @Size(max = 20) String wardCode) {
    }

    private final UserService users;

    public AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserView> list() {
        return users.all().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest req) {
        users.actor(jwt).requireNotDemo();
        return UserView.of(users.create(req.fullName(), req.email(), req.password(), req.role(), req.wardCode()));
    }
}
