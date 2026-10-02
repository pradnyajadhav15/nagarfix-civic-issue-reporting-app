package dev.nagarfix.api.jobs;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.nagarfix.api.issue.IssueService;
import dev.nagarfix.api.notify.NotificationService;

/**
 * Background housekeeping, run 30 seconds after the server starts and then every hour while it is awake:
 * - closes reports that stayed "Resolved" for N days without the citizen's answer
 * - rebuilds the demo data once a day
 * Render's free server sleeps when idle, so a daily GitHub Actions job wakes it up (.github/workflows/wake.yml).
 */
@Component
public class DailyJobs {

    private static final Logger log = LoggerFactory.getLogger(DailyJobs.class);
    /** Postgres advisory lock id, so two runs never overlap. */
    private static final long LOCK_ID = 731_001L;

    public record Result(boolean ran, int autoClosed, Integer demoReports) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final IssueService issues;
    private final NotificationService notifications;
    private final boolean enabled;
    private final int autoCloseDays;

    public DailyJobs(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, IssueService issues,
                     NotificationService notifications,
                     @Value("${app.jobs.enabled:true}") boolean enabled,
                     @Value("${app.workflow.auto-close-days:7}") int autoCloseDays) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.issues = issues;
        this.notifications = notifications;
        this.enabled = enabled;
        this.autoCloseDays = autoCloseDays;
    }

    @Scheduled(initialDelayString = "${app.jobs.initial-delay-ms:30000}", fixedDelayString = "${app.jobs.interval-ms:3600000}")
    public void onSchedule() {
        if (!enabled) {
            return;
        }
        try {
            Result result = run(false);
            if (result.autoClosed() > 0 || result.demoReports() != null) {
                log.info("Background jobs: {}", result);
            }
        } catch (RuntimeException e) {
            log.warn("Background jobs failed", e);
        }
    }

    /** force = rebuild the demo data even if it was rebuilt recently (admin "run now" button). */
    public Result run(boolean force) {
        return tx.execute(status -> {
            Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_ID);
            if (!Boolean.TRUE.equals(locked)) {
                return new Result(false, 0, null);
            }
            int closed = autoClose();
            Integer demo = null;
            if (force || demoIsDue()) {
                demo = jdbc.queryForObject("SELECT nagarfix_reset_demo()", Integer.class);
                jdbc.update("""
                        INSERT INTO job_run (name, last_run_at) VALUES ('demo-reset', now())
                        ON CONFLICT (name) DO UPDATE SET last_run_at = excluded.last_run_at
                        """);
            }
            return new Result(true, closed, demo);
        });
    }

    private boolean demoIsDue() {
        Boolean recent = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM job_run
                               WHERE name = 'demo-reset' AND last_run_at > now() - interval '20 hours')
                """, Boolean.class);
        return !Boolean.TRUE.equals(recent);
    }

    private int autoClose() {
        List<Map<String, Object>> closed = jdbc.queryForList("""
                UPDATE issue SET status = 'CLOSED', closed_at = now(), updated_at = now()
                WHERE status = 'RESOLVED' AND resolved_at < now() - make_interval(days => ?)
                RETURNING id, reporter_id, category, is_demo
                """, autoCloseDays);
        String note = "Closed automatically: no reply from the reporter within " + autoCloseDays + " days";
        for (Map<String, Object> row : closed) {
            long id = ((Number) row.get("id")).longValue();
            long reporter = ((Number) row.get("reporter_id")).longValue();
            boolean demo = Boolean.TRUE.equals(row.get("is_demo"));
            issues.recordEvent(id, "RESOLVED", "CLOSED", "AUTO_CLOSE", null, "SYSTEM", note, null);
            notifications.toUser(reporter, id, "Your report #" + id + " ("
                    + IssueService.categoryName((String) row.get("category"))
                    + ") was closed automatically, as there was no reply for " + autoCloseDays + " days.", demo);
        }
        return closed.size();
    }
}
