package dev.nagarfix.api.issue;

/** One entry in a report's public history. Shows the role (citizen, officer...), never the person. */
public record IssueEventView(
        String fromStatus,
        String toStatus,
        String action,
        String actorRole,
        String note,
        String photoUrl,
        String createdAt) {
}
