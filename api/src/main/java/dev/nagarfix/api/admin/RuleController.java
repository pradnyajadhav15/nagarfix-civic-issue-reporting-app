package dev.nagarfix.api.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.nagarfix.api.auth.UserService;
import dev.nagarfix.api.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Which department handles each category, and its deadline in days. Anyone can read; admins can change. */
@RestController
public class RuleController {

    public record Rule(String category, String department, int slaDays) {
    }

    public record UpdateRule(
            @NotBlank @Size(max = 80) String department,
            @NotNull @Min(1) @Max(90) Integer slaDays) {
    }

    private static final RowMapper<Rule> MAPPER = (rs, n) ->
            new Rule(rs.getString("category"), rs.getString("department"), rs.getInt("sla_days"));

    private final JdbcTemplate jdbc;
    private final UserService users;

    public RuleController(JdbcTemplate jdbc, UserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @GetMapping("/api/rules")
    public List<Rule> rules() {
        return jdbc.query("SELECT category, department, sla_days FROM category_rule ORDER BY category", MAPPER);
    }

    @PutMapping("/api/admin/rules/{category}")
    public Rule update(@AuthenticationPrincipal Jwt jwt, @PathVariable String category,
                       @Valid @RequestBody UpdateRule req) {
        users.actor(jwt).requireNotDemo();
        String code = category.trim().toUpperCase(Locale.ROOT);
        int updated = jdbc.update(
                "UPDATE category_rule SET department = ?, sla_days = ?, updated_at = now() WHERE category = ?",
                req.department().trim(), req.slaDays(), code);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown category: " + category);
        }
        return jdbc.queryForObject(
                "SELECT category, department, sla_days FROM category_rule WHERE category = ?", MAPPER, code);
    }
}
