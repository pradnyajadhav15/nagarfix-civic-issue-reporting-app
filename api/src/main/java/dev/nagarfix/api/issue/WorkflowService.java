package dev.nagarfix.api.issue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.nagarfix.api.auth.Actor;
import dev.nagarfix.api.auth.Role;
import dev.nagarfix.api.common.ApiException;
import dev.nagarfix.api.notify.NotificationService;
import dev.nagarfix.api.upload.CloudinaryProps;

/**
 * Moves reports through the workflow (see {@link Workflow}), checking who may do what,
 * recording every change in the audit trail and notifying the people involved.
 */
@Service
public class WorkflowService {

    /** The few columns needed to decide whether an action is allowed. */
    private record Current(long id, String status, String wardCode, long reporterId, Long assignedOfficerId,
                           boolean demo, String category) {
    }

    private final JdbcTemplate jdbc;
    private final IssueService issues;
    private final CloudinaryProps cloud;
    private final NotificationService notifications;

    public WorkflowService(JdbcTemplate jdbc, IssueService issues, CloudinaryProps cloud,
                           NotificationService notifications) {
        this.jdbc = jdbc;
        this.issues = issues;
        this.cloud = cloud;
        this.notifications = notifications;
    }

    /** The buttons to show this person for this report. */
    public List<IssueAction> allowedActions(Actor actor, long issueId) {
        Current issue = load(issueId);
        return Arrays.stream(IssueAction.values())
                .filter(action -> Workflow.next(action, issue.status()).isPresent())
                .filter(action -> mayAct(actor, issue, action))
                .toList();
    }

    @Transactional
    public IssueView apply(Actor actor, long issueId, IssueActionRequest req) {
        Current issue = load(issueId);
        IssueAction action = req.action();
        String to = Workflow.next(action, issue.status()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "You can't " + action.verb() + " a report that is " + Workflow.label(issue.status()) + "."));
        if (actor.demo() && !issue.demo()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Demo accounts can only change demo reports.");
        }
        if (!mayAct(actor, issue, action)) {
            throw new ApiException(HttpStatus.FORBIDDEN, action.byReporter()
                    ? "Only the person who reported this can do that."
                    : "Only an officer of this zone (or an admin) can do that.");
        }

        String note = trimToNull(req.note());
        String photo = trimToNull(req.photoUrl());
        String eventNote = note;
        String eventPhoto = null;
        StringBuilder set = new StringBuilder("status = ?, updated_at = now()");
        List<Object> args = new ArrayList<>(List.of(to));

        switch (action) {
            case ASSIGN -> {
                set.append(", assigned_officer_id = ?");
                args.add(actor.id());
            }
            case START -> {
                set.append(", assigned_officer_id = COALESCE(assigned_officer_id, ?)");
                args.add(actor.id());
            }
            case RESOLVE -> {
                if (photo == null) {
                    throw badRequest("Add an 'after' photo that shows the fix.");
                }
                if (!cloud.isOwnPhotoUrl(photo)) {
                    throw badRequest("The photo must be uploaded through NagarFix");
                }
                set.append(", resolution_photo_url = ?, resolution_note = ?, resolved_at = now()");
                args.add(photo);
                args.add(note);
                eventPhoto = photo;
            }
            case REJECT -> {
                requireReason(note);
                set.append(", status_reason = ?, closed_at = now()");
                args.add(note);
            }
            case DUPLICATE -> {
                long original = checkOriginal(issueId, req.duplicateOf());
                eventNote = "Duplicate of #" + original + (note == null ? "" : " - " + note);
                set.append(", duplicate_of_id = ?, status_reason = ?, closed_at = now()");
                args.add(original);
                args.add(eventNote);
            }
            case CONFIRM -> set.append(", closed_at = now()");
            case REOPEN -> {
                requireReason(note);
                set.append(", status_reason = ?, resolved_at = NULL");
                args.add(note);
            }
        }

        // Compare-and-set: only succeeds if nobody changed the status since we read it
        args.add(issueId);
        args.add(issue.status());
        int updated = jdbc.update("UPDATE issue SET " + set + " WHERE id = ? AND status = ?", args.toArray());
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Someone else just updated this report. Refresh the page and try again.");
        }
        issues.recordEvent(issueId, issue.status(), to, action.name(), actor.id(), actor.role().name(),
                eventNote, eventPhoto);
        notifyPeople(actor, issue, action, to, note);
        return issues.get(issueId);
    }

    private Current load(long issueId) {
        return jdbc.query("""
                SELECT id, status, ward_code, reporter_id, assigned_officer_id, is_demo, category
                FROM issue WHERE id = ?
                """, (rs, n) -> new Current(rs.getLong("id"), rs.getString("status"), rs.getString("ward_code"),
                        rs.getLong("reporter_id"), rs.getObject("assigned_officer_id", Long.class),
                        rs.getBoolean("is_demo"), rs.getString("category")), issueId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    /** Reporter actions need the reporter; officer actions need an officer of that zone, or an admin. */
    private static boolean mayAct(Actor actor, Current issue, IssueAction action) {
        if (actor.demo() && !issue.demo()) {
            return false;
        }
        if (action.byReporter()) {
            return actor.id() == issue.reporterId();
        }
        return actor.isAdmin()
                || (actor.role() == Role.OFFICER && Objects.equals(actor.wardCode(), issue.wardCode()));
    }

    private long checkOriginal(long issueId, Long original) {
        if (original == null) {
            throw badRequest("Give the number of the original report.");
        }
        if (original == issueId) {
            throw badRequest("A report can't be a duplicate of itself.");
        }
        String status = jdbc.query("SELECT status FROM issue WHERE id = ?", (rs, n) -> rs.getString(1), original)
                .stream()
                .findFirst()
                .orElseThrow(() -> badRequest("Report #" + original + " does not exist."));
        if (status.equals("DUPLICATE") || status.equals("REJECTED")) {
            throw badRequest("Report #" + original + " is itself " + Workflow.label(status)
                    + ". Link to the original report instead.");
        }
        return original;
    }

    private void notifyPeople(Actor actor, Current issue, IssueAction action, String to, String note) {
        String what = "#" + issue.id() + " (" + IssueService.categoryName(issue.category()) + ")";
        if (action.byReporter()) {
            String message = action == IssueAction.CONFIRM
                    ? "The citizen confirmed that report " + what + " is fixed."
                    : "Report " + what + " was reopened by the citizen: " + note;
            if (issue.assignedOfficerId() != null) {
                notifications.toUser(issue.assignedOfficerId(), issue.id(), message, issue.demo());
            } else {
                notifications.toZoneOfficers(issue.wardCode(), issue.id(), message, issue.demo());
            }
            return;
        }
        if (actor.id() == issue.reporterId()) {
            return; // no need to tell people about their own change
        }
        String message = switch (to) {
            case "ASSIGNED" -> "Your report " + what + " was assigned to an officer.";
            case "IN_PROGRESS" -> "Work has started on your report " + what + ".";
            case "RESOLVED" -> "Your report " + what + " was marked fixed. Please check and confirm it.";
            case "REJECTED" -> "Your report " + what + " was rejected: " + note;
            case "DUPLICATE" -> "Your report " + what + " was marked as a duplicate of another report.";
            default -> "Your report " + what + " is now " + Workflow.label(to) + ".";
        };
        notifications.toUser(issue.reporterId(), issue.id(), message, issue.demo());
    }

    private static void requireReason(String note) {
        if (note == null || note.length() < 5) {
            throw badRequest("Please give a reason (at least 5 characters).");
        }
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
