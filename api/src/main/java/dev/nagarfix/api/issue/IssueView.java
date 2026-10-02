package dev.nagarfix.api.issue;

/** What the API shows about an issue (the reporter stays private). */
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
        String createdAt) {
}
