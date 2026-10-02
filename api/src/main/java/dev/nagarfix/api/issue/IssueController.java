package dev.nagarfix.api.issue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.nagarfix.api.common.ApiException;
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

    /**
     * Public list for the map. Optional filters:
     * status=SUBMITTED,ASSIGNED  category=POTHOLE  ward=Z05  bbox=minLng,minLat,maxLng,maxLat  limit=500
     */
    @GetMapping
    public List<IssueView> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) String ward,
                                @RequestParam(required = false) String bbox,
                                @RequestParam(defaultValue = "500") int limit) {
        return issues.search(codes(status), codes(category), ward, parseBbox(bbox), Math.max(1, Math.min(limit, 1000)));
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

    /** "submitted, in_progress" -> [SUBMITTED, IN_PROGRESS]; anything odd is ignored. */
    private static List<String> codes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> s.matches("[A-Z_]{2,20}"))
                .distinct()
                .toList();
    }

    private static double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bbox must be minLng,minLat,maxLng,maxLat");
        }
        try {
            double[] values = new double[4];
            for (int i = 0; i < 4; i++) {
                values[i] = Double.parseDouble(parts[i].trim());
            }
            return values;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bbox must contain 4 numbers");
        }
    }
}
