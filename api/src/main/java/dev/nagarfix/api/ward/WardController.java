package dev.nagarfix.api.ward;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Zones (wards) of Solapur: map data and "which zone is this point in?". */
@RestController
@RequestMapping("/api/wards")
public class WardController {

    private final JdbcTemplate jdbc;

    public WardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All zones as a GeoJSON FeatureCollection (slightly simplified for the map). */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String all() {
        return jdbc.queryForObject("""
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(json_agg(json_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_SimplifyPreserveTopology(boundary, 0.0002), 6)::json,
                        'properties', json_build_object(
                            'code', code, 'name', name, 'nameMr', name_mr, 'official', is_official)
                    ) ORDER BY code), '[]'::json)
                )::text
                FROM ward
                """, String.class);
    }

    /** Which zone contains this point? 404 when the point is outside Solapur. */
    @GetMapping("/locate")
    public ResponseEntity<Map<String, Object>> locate(@RequestParam double lat, @RequestParam double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("message", "Invalid coordinates"));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code, name, name_mr AS "nameMr", is_official AS "official"
                FROM ward
                WHERE ST_Contains(boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                LIMIT 1
                """, lng, lat);
        if (rows.isEmpty()) {
            return ResponseEntity.status(404).body(Map.<String, Object>of("message", "Location is outside Solapur"));
        }
        return ResponseEntity.ok(rows.get(0));
    }
}
