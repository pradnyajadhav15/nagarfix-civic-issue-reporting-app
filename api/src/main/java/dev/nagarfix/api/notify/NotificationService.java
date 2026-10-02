package dev.nagarfix.api.notify;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * In-app notifications. When email is switched on, each new notification is also queued for email
 * (email_status = PENDING) and {@link EmailOutbox} sends it in the background.
 * Demo reports only ever notify demo accounts, and real reports only real accounts.
 */
@Service
public class NotificationService {

    public record NotificationView(long id, Long issueId, String message, boolean read, String createdAt) {
    }

    public record Inbox(long unread, List<NotificationView> items) {
    }

    private final JdbcTemplate jdbc;
    private final EmailSender email;

    public NotificationService(JdbcTemplate jdbc, EmailSender email) {
        this.jdbc = jdbc;
        this.email = email;
    }

    public void toUser(long userId, long issueId, String message, boolean demoIssue) {
        jdbc.update("""
                INSERT INTO notification (user_id, issue_id, message, email_status)
                SELECT u.id, ?, ?, CASE WHEN ? AND NOT u.is_demo THEN 'PENDING' ELSE 'SKIPPED' END
                FROM app_user u
                WHERE u.id = ? AND u.is_demo = ?
                """, issueId, clip(message), email.enabled(), userId, demoIssue);
    }

    public void toZoneOfficers(String wardCode, long issueId, String message, boolean demoIssue) {
        if (wardCode == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO notification (user_id, issue_id, message, email_status)
                SELECT u.id, ?, ?, CASE WHEN ? AND NOT u.is_demo THEN 'PENDING' ELSE 'SKIPPED' END
                FROM app_user u
                WHERE u.role = 'OFFICER' AND u.ward_code = ? AND u.is_demo = ?
                """, issueId, clip(message), email.enabled(), wardCode, demoIssue);
    }

    /** The latest 30 notifications and how many are unread. */
    public Inbox inbox(long userId) {
        Long unread = jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE user_id = ? AND read_at IS NULL", Long.class, userId);
        List<NotificationView> items = jdbc.query("""
                SELECT id, issue_id, message, read_at, created_at
                FROM notification
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 30
                """, (rs, n) -> new NotificationView(
                        rs.getLong("id"),
                        rs.getObject("issue_id", Long.class),
                        rs.getString("message"),
                        rs.getObject("read_at") != null,
                        rs.getObject("created_at", OffsetDateTime.class).toInstant().toString()),
                userId);
        return new Inbox(unread == null ? 0 : unread, items);
    }

    public void markAllRead(long userId) {
        jdbc.update("UPDATE notification SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId);
    }

    private static String clip(String message) {
        return message.length() <= 300 ? message : message.substring(0, 297) + "...";
    }
}
