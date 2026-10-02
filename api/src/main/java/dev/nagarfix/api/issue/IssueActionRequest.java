package dev.nagarfix.api.issue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One status change. note: reason or comment (needed to reject or reopen);
 * photoUrl: the "after" photo (needed to resolve); duplicateOf: the original report's number.
 */
public record IssueActionRequest(
        @NotNull IssueAction action,
        @Size(max = 1000) String note,
        @Size(max = 500) String photoUrl,
        Long duplicateOf) {
}
