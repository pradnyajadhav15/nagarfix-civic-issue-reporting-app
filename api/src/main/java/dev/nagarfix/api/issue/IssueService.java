package dev.nagarfix.api.issue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.nagarfix.api.common.ApiException;
import dev.nagarfix.api.upload.CloudinaryProps;

/** Saves reports and finds their ward with PostGIS. Uses plain SQL because of the geometry column. */
@Service
public class IssueService {

    private static final String SELECT = """
            SELECT i.id, i.category, i.description,
                   ST_Y(i.location) AS lat, ST_X(i.location) AS lng, i.address,
                   i.ward_code, w.name AS ward_name, w.name_mr AS ward_name_mr,
                   i.photo_url, i.status, i.created_at
            FROM issue i
            LEFT JOIN ward w ON w.code = i.ward_code
            """;

    private static final RowMapper<IssueView> MAPPER = (rs, rowNum) -> new IssueView(
            rs.getLong("id"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getDouble("lat"),
            rs.getDouble("lng"),
            rs.getString("address"),
            rs.getString("ward_code"),
            rs.getString("ward_name"),
            rs.getString("ward_name_mr"),
            rs.getString("photo_url"),
            rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant().toString());

    private final JdbcTemplate jdbc;
    private final CloudinaryProps cloud;

    public IssueService(JdbcTemplate jdbc, CloudinaryProps cloud) {
        this.jdbc = jdbc;
        this.cloud = cloud;
    }

    @Transactional
    public IssueView create(long reporterId, CreateIssueRequest req) {
        if (!cloud.isOwnPhotoUrl(req.photoUrl())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The photo must be uploaded through NagarFix");
        }
        List<String> wards = jdbc.queryForList("""
                SELECT code FROM ward
                WHERE ST_Contains(boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                LIMIT 1
                """, String.class, req.lng(), req.lat());
        if (wards.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This location is outside Solapur. NagarFix currently covers Solapur only.");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO issue (category, description, location, address, ward_code, photo_url, reporter_id)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                req.category().name(), blankToNull(req.description()), req.lng(), req.lat(),
                blankToNull(req.address()), wards.get(0), req.photoUrl(), reporterId);
        return get(id);
    }

    public IssueView get(long id) {
        return jdbc.query(SELECT + " WHERE i.id = ?", MAPPER, id).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    public List<IssueView> mine(long reporterId) {
        return jdbc.query(SELECT + " WHERE i.reporter_id = ? ORDER BY i.created_at DESC", MAPPER, reporterId);
    }

    /**
     * Public list for the map, newest first. All filters are optional.
     * bbox = {minLng, minLat, maxLng, maxLat}; "&&" uses the spatial (GIST) index.
     */
    public List<IssueView> search(List<String> statuses, List<String> categories, String wardCode,
                                  double[] bbox, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (!statuses.isEmpty()) {
            where.append(" AND i.status IN (").append(placeholders(statuses.size())).append(')');
            args.addAll(statuses);
        }
        if (!categories.isEmpty()) {
            where.append(" AND i.category IN (").append(placeholders(categories.size())).append(')');
            args.addAll(categories);
        }
        if (wardCode != null && !wardCode.isBlank()) {
            where.append(" AND i.ward_code = ?");
            args.add(wardCode.trim());
        }
        if (bbox != null) {
            where.append(" AND i.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)");
            args.add(bbox[0]);
            args.add(bbox[1]);
            args.add(bbox[2]);
            args.add(bbox[3]);
        }
        args.add(limit);
        return jdbc.query(SELECT + where + " ORDER BY i.created_at DESC LIMIT ?", MAPPER, args.toArray());
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
