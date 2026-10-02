package dev.nagarfix.api.issue;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIssueRequest(
        @NotNull IssueCategory category,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 300) String address,
        @NotBlank @Size(max = 500) String photoUrl) {
}
