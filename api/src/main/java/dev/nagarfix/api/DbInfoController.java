package dev.nagarfix.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quick check that the database and PostGIS are reachable. */
@RestController
public class DbInfoController {

    private final JdbcTemplate jdbc;

    public DbInfoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/db")
    public Map<String, Object> db() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "ok");
        info.put("postgres", jdbc.queryForObject("SHOW server_version", String.class));
        info.put("postgis", jdbc.queryForObject("SELECT PostGIS_Lib_Version()", String.class));
        info.put("wards", jdbc.queryForObject("SELECT count(*) FROM ward", Long.class));
        return info;
    }
}
