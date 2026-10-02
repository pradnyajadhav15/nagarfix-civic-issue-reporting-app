package dev.nagarfix.api.admin;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.nagarfix.api.auth.UserService;
import dev.nagarfix.api.jobs.DailyJobs;

/** Admin-only (see SecurityConfig): zone coverage and running the background jobs on demand. */
@RestController
@RequestMapping("/api/admin")
public class AdminOpsController {

    /** One zone: how many officers it has, and how many open and overdue reports. */
    public record ZoneCoverage(String code, String name, long officers, long open, long overdue) {
    }

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final DailyJobs jobs;

    public AdminOpsController(JdbcTemplate jdbc, UserService users, DailyJobs jobs) {
        this.jdbc = jdbc;
        this.users = users;
        this.jobs = jobs;
    }

    /** demo=false (default) counts real officers and reports; demo=true counts the demo ones. */
    @GetMapping("/coverage")
    public List<ZoneCoverage> coverage(@RequestParam(defaultValue = "false") boolean demo) {
        return jdbc.query("""
                SELECT w.code, w.name,
                       (SELECT count(*) FROM app_user u
                        WHERE u.role = 'OFFICER' AND u.ward_code = w.code AND u.is_demo = ?) AS officers,
                       count(i.id) FILTER (WHERE i.status IN ('SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')) AS open,
                       count(i.id) FILTER (WHERE i.status IN ('SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
                                             AND now() > i.created_at + make_interval(days => r.sla_days)) AS overdue
                FROM ward w
                LEFT JOIN issue i ON i.ward_code = w.code AND i.is_demo = ?
                LEFT JOIN category_rule r ON r.category = i.category
                GROUP BY w.code, w.name
                ORDER BY w.code
                """, (rs, n) -> new ZoneCoverage(rs.getString("code"), rs.getString("name"),
                        rs.getLong("officers"), rs.getLong("open"), rs.getLong("overdue")), demo, demo);
    }

    /** Runs auto-close now and rebuilds the demo data. Not allowed for the demo admin. */
    @PostMapping("/jobs/run")
    public DailyJobs.Result runJobs(@AuthenticationPrincipal Jwt jwt) {
        users.actor(jwt).requireNotDemo();
        return jobs.run(true);
    }
}
