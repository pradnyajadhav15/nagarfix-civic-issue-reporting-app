package dev.nagarfix.api.notify;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Emails notifications marked PENDING, a few at a time, every minute while the server is awake.
 * A failed email is retried up to 3 times. Does nothing while email is switched off.
 */
@Component
public class EmailOutbox {

    private final JdbcTemplate jdbc;
    private final EmailSender email;
    private final String webUrl;

    public EmailOutbox(JdbcTemplate jdbc, EmailSender email,
                       @Value("${app.web-url:https://nagarfix-civic-issue-reporting-app.vercel.app}") String webUrl) {
        this.jdbc = jdbc;
        this.email = email;
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }

    @Scheduled(initialDelayString = "${app.email.initial-delay-ms:45000}", fixedDelayString = "${app.email.interval-ms:60000}")
    public void sendPending() {
        if (!email.enabled()) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT n.id, n.issue_id, n.message, u.email, u.full_name
                FROM notification n
                JOIN app_user u ON u.id = n.user_id
                WHERE n.email_status = 'PENDING'
                ORDER BY n.id
                LIMIT 20
                """);
        for (Map<String, Object> row : rows) {
            String message = (String) row.get("message");
            Object issueId = row.get("issue_id");
            String link = issueId == null ? webUrl : webUrl + "/issues/" + issueId;
            boolean sent = email.send((String) row.get("email"), (String) row.get("full_name"),
                    "NagarFix: " + (message.length() > 70 ? message.substring(0, 67) + "..." : message),
                    message, link);
            jdbc.update("""
                    UPDATE notification
                    SET email_attempts = email_attempts + 1,
                        email_status = CASE WHEN ? THEN 'SENT'
                                            WHEN email_attempts + 1 >= 3 THEN 'FAILED'
                                            ELSE 'PENDING' END
                    WHERE id = ?
                    """, sent, row.get("id"));
        }
    }
}
