package dev.nagarfix.api.issue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.nagarfix.api.auth.Actor;
import dev.nagarfix.api.common.ApiException;
import dev.nagarfix.api.notify.NotificationService;
import dev.nagarfix.api.upload.CloudinaryProps;

/** Saves reports, finds their ward with PostGIS and keeps their history. Plain SQL because of the geometry column. */
@Service
public class IssueService {

    private static final String SELECT = """
            SELECT i.id, i.category, i.description,
                   ST_Y(i.location) AS lat, ST_X(i.location) AS lng, i.address,
                   i.ward_code, w.name AS ward_name, w.name_mr AS ward_name_mr,
                   i.photo_url, i.status, i.created_at, i.updated_at,
                   r.department, r.sla_days,
                   i.created_at + make_interval(days => r.sla_days) AS due_at,
                   (i.status IN ('SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
                       AND now() > i.created_at + make_interval(days => r.sla_days)) AS overdue,
                   i.resolution_photo_url, i.resolution_note, i.status_reason, i.duplicate_of_id,
                   i.resolved_at, i.closed_at, i.is_demo
            FROM issue i
            LEFT JOIN ward w ON w.code = i.ward_code
            LEFT JOIN category_rule r ON r.category = i.category
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
            iso(rs, "created_at"),
            iso(rs, "updated_at"),
            rs.getString("department"),
            rs.getObject("sla_days", Integer.class),
            iso(rs, "due_at"),
            rs.getBoolean("overdue"),
            rs.getString("resolution_photo_url"),
            rs.getString("resolution_note"),
            rs.getString("status_reason"),
            rs.getObject("duplicate_of_id", Long.class),
            iso(rs, "resolved_at"),
            iso(rs, "closed_at"),
            rs.getBoolean("is_demo"));

    private static final RowMapper<IssueEventView> EVENT_MAPPER = (rs, rowNum) -> new IssueEventView(
            rs.getString("from_status"),
            rs.getString("to_status"),
            rs.getString("action"),
            rs.getString("actor_role"),
            rs.getString("note"),
            rs.getString("photo_url"),
            iso(rs, "created_at"));

    private final JdbcTemplate jdbc;
    private final CloudinaryProps cloud;
    private final NotificationService notifications;

    public IssueService(JdbcTemplate jdbc, CloudinaryProps cloud, NotificationService notifications) {
        this.jdbc = jdbc;
        this.cloud = cloud;
        this.notifications = notifications;
    }

    @Transactional
    public IssueView create(Actor reporter, CreateIssueRequest req) {
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
        String ward = wards.get(0);
        Long id = jdbc.queryForObject("""
                INSERT INTO issue (category, description, location, address, ward_code, photo_url, reporter_id, is_demo)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                req.category().name(), blankToNull(req.description()), req.lng(), req.lat(),
                blankToNull(req.address()), ward, req.photoUrl(), reporter.id(), reporter.demo());
        recordEvent(id, null, "SUBMITTED", "SUBMIT", reporter.id(), reporter.role().name(), null, null);
        notifications.toZoneOfficers(ward, id,
                "New report #" + id + " (" + categoryName(req.category().name()) + ") in your zone.", reporter.demo());
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

    /** The public history of one report, oldest first. */
    public List<IssueEventView> history(long issueId) {
        get(issueId); // 404 if the report does not exist
        return jdbc.query("""
                SELECT from_status, to_status, action, actor_role, note, photo_url, created_at
                FROM issue_event WHERE issue_id = ? ORDER BY created_at, id
                """, EVENT_MAPPER, issueId);
    }

    /** Writes one line of the audit trail. actorId is null for the system (auto-close). */
    public void recordEvent(long issueId, String fromStatus, String toStatus, String action,
                            Long actorId, String actorRole, String note, String photoUrl) {
        jdbc.update("""
                INSERT INTO issue_event (issue_id, from_status, to_status, action, actor_id, actor_role, note, photo_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, issueId, fromStatus, toStatus, action, actorId, actorRole, note, photoUrl);
    }

    /**
     * Public list for the map, newest first. All filters are optional.
     * bbox = {minLng, minLat, maxLng, maxLat}; "&&" uses the spatial (GIST) index.
     * demo = true/false shows only demo or only real reports; null shows both.
     */
    public List<IssueView> search(List<String> statuses, List<String> categories, String wardCode, Boolean demo,
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
        if (demo != null) {
            where.append(" AND i.is_demo = ?");
            args.add(demo);
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

    /** POTHOLE -> "Pothole", WATER_LEAK -> "Water leak". */
    public static String categoryName(String category) {
        String words = category.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String iso(ResultSet rs, String column) throws SQLException {
        OffsetDateTime time = rs.getObject(column, OffsetDateTime.class);
        return time == null ? null : time.toInstant().toString();
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
