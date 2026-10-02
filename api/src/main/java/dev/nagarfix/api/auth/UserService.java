package dev.nagarfix.api.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.nagarfix.api.common.ApiException;

@Service
public class UserService {

    /** The demo accounts created by migration V8, one per role. */
    private static final Map<Role, String> DEMO_EMAILS = Map.of(
            Role.CITIZEN, "demo-citizen@example.com",
            Role.OFFICER, "demo-officer@example.com",
            Role.ADMIN, "demo-admin@example.com");

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;

    public UserService(UserRepository users, PasswordEncoder passwords, JdbcTemplate jdbc) {
        this.users = users;
        this.passwords = passwords;
        this.jdbc = jdbc;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AppUser create(String fullName, String email, String password, Role role, String wardCode) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        String ward = null;
        if (role == Role.OFFICER) {
            if (wardCode == null || wardCode.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Officers must be assigned a ward");
            }
            Integer found = jdbc.queryForObject("SELECT count(*) FROM ward WHERE code = ?", Integer.class, wardCode.trim());
            if (found == null || found == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown ward: " + wardCode);
            }
            ward = wardCode.trim();
        }
        return users.save(new AppUser(fullName.trim(), normalized, passwords.encode(password), role, ward));
    }

    /** Password login. Demo accounts cannot log in this way. */
    public AppUser authenticate(String email, String password) {
        return users.findByEmail(normalizeEmail(email))
                .filter(u -> !u.isDemo())
                .filter(u -> passwords.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    public AppUser get(long id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found - please log in again"));
    }

    /** The person behind a login token, with their current role and zone. */
    public Actor actor(Jwt jwt) {
        AppUser u = get(Long.parseLong(jwt.getSubject()));
        return new Actor(u.getId(), u.getRole(), u.getWardCode(), u.isDemo());
    }

    public AppUser demoUser(Role role) {
        return users.findByEmail(DEMO_EMAILS.get(role))
                .filter(AppUser::isDemo)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Demo accounts are not available right now"));
    }

    public boolean exists(String email) {
        return users.existsByEmail(normalizeEmail(email));
    }

    public List<AppUser> all() {
        return users.findAll(Sort.by("id"));
    }
}
