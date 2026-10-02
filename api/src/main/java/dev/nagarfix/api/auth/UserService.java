package dev.nagarfix.api.auth;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.nagarfix.api.common.ApiException;

@Service
public class UserService {

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

    public AppUser authenticate(String email, String password) {
        return users.findByEmail(normalizeEmail(email))
                .filter(u -> passwords.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    public AppUser get(long id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found - please log in again"));
    }

    public boolean exists(String email) {
        return users.existsByEmail(normalizeEmail(email));
    }

    public List<AppUser> all() {
        return users.findAll(Sort.by("id"));
    }
}
