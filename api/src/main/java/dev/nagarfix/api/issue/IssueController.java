package dev.nagarfix.api.issue;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issues;

    public IssueController(IssueService issues) {
        this.issues = issues;
    }

    /** Report a new issue (needs login). The ward is assigned automatically. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateIssueRequest req) {
        return issues.create(Long.parseLong(jwt.getSubject()), req);
    }

    /** My own reports, newest first (needs login). */
    @GetMapping("/mine")
    public List<IssueView> mine(@AuthenticationPrincipal Jwt jwt) {
        return issues.mine(Long.parseLong(jwt.getSubject()));
    }

    /** One report - public. */
    @GetMapping("/{id}")
    public IssueView one(@PathVariable long id) {
        return issues.get(id);
    }
}
