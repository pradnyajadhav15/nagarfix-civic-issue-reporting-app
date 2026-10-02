package dev.nagarfix.api.issue;

/**
 * What the API shows about an issue (the reporter stays private).
 * department / slaDays / dueAt come from the category's rule; overdue = still open after its deadline.
 */
public record IssueView(
        long id,
        String category,
        String description,
        double lat,
        double lng,
        String address,
        String wardCode,
        String wardName,
        String wardNameMr,
        String photoUrl,
        String status,
        String createdAt,
        String updatedAt,
        String department,
        Integer slaDays,
        String dueAt,
        boolean overdue,
        String resolutionPhotoUrl,
        String resolutionNote,
        String statusReason,
        Long duplicateOfId,
        String resolvedAt,
        String closedAt,
        boolean demo) {
}
