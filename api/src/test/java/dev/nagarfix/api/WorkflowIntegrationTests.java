package dev.nagarfix.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The Phase 2 workflow: status changes, permissions, history, deadlines, notifications, auto-close and demo mode. */
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class WorkflowIntegrationTests extends IntegrationTestBase {

    @Test
    void aReportGoesFromSubmittedToClosedWithAFullHistory() {
        String citizen = newCitizen();
        long id = report(citizen);
        String officer = officerFor(zoneOf(id));

        assertThat(actions(id, citizen)).isEmpty();
        assertThat(actions(id, officer)).containsExactly("ASSIGN", "REJECT", "DUPLICATE");

        assertThat(act(id, officer, "ASSIGN").<String>json("$.status")).isEqualTo("ASSIGNED");
        assertThat(act(id, officer, "START").<String>json("$.status")).isEqualTo("IN_PROGRESS");
        Reply resolved = resolve(id, officer);
        assertThat(resolved.<String>json("$.status")).isEqualTo("RESOLVED");
        assertThat(resolved.<String>json("$.resolutionPhotoUrl")).isEqualTo(AFTER_PHOTO);
        assertThat(resolved.<String>json("$.resolutionNote")).isEqualTo("Filled and levelled");
        assertThat(resolved.<String>json("$.resolvedAt")).isNotBlank();

        assertThat(actions(id, citizen)).containsExactly("CONFIRM", "REOPEN");
        assertThat(actions(id, officer)).isEmpty();

        Reply closed = act(id, citizen, "CONFIRM");
        assertThat(closed.status()).as("body: %s", closed.body()).isEqualTo(200);
        assertThat(closed.<String>json("$.status")).isEqualTo("CLOSED");
        assertThat(closed.<String>json("$.closedAt")).isNotBlank();

        Reply history = get("/api/issues/" + id + "/history");
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.<List<String>>json("$[*].toStatus"))
                .containsExactly("SUBMITTED", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED");
        assertThat(history.<List<String>>json("$[*].actorRole"))
                .containsExactly("CITIZEN", "OFFICER", "OFFICER", "OFFICER", "CITIZEN");
        // the public history names roles, never people
        assertThat(history.body()).doesNotContain("@example.com");
        assertThat(get("/api/issues/999999999/history").status()).isEqualTo(404);
    }

    @Test
    void theCitizenCanReopenWithAReason() {
        String citizen = newCitizen();
        long id = report(citizen);
        String officer = officerFor(zoneOf(id));
        act(id, officer, "ASSIGN");
        act(id, officer, "START");
        resolve(id, officer);

        assertThat(act(id, citizen, "REOPEN").status()).isEqualTo(400);

        Reply reopened = actWith(id, citizen, """
                {"action": "REOPEN", "note": "Still a big hole"}
                """);
        assertThat(reopened.status()).as("body: %s", reopened.body()).isEqualTo(200);
        assertThat(reopened.<String>json("$.status")).isEqualTo("REOPENED");
        assertThat(reopened.<String>json("$.statusReason")).isEqualTo("Still a big hole");

        assertThat(actions(id, officer)).containsExactly("START", "REJECT", "DUPLICATE");
        assertThat(act(id, officer, "START").<String>json("$.status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void onlyTheRightPeopleCanChangeAReport() {
        String citizen = newCitizen();
        long id = report(citizen);
        String zone = zoneOf(id);
        String officer = officerFor(zone);
        String otherZoneOfficer = officerFor(zone.equals("Z01") ? "Z02" : "Z01");

        assertThat(act(id, citizen, "ASSIGN").status()).isEqualTo(403);
        assertThat(act(id, otherZoneOfficer, "ASSIGN").status()).isEqualTo(403);
        assertThat(act(id, adminToken(), "ASSIGN").status()).isEqualTo(200);   // admins can act in any zone
        assertThat(act(id, officer, "ASSIGN").status()).isEqualTo(409);        // already assigned
        assertThat(act(id, officer, "CONFIRM").status()).isEqualTo(409);       // not resolved yet

        act(id, officer, "START");
        assertThat(act(id, officer, "RESOLVE").status()).isEqualTo(400);       // no "after" photo
        assertThat(actWith(id, officer, """
                {"action": "RESOLVE", "photoUrl": "https://example.com/fixed.jpg"}
                """).status()).isEqualTo(400);                                 // photo from another site
        resolve(id, officer);

        assertThat(act(id, newCitizen(), "CONFIRM").status()).isEqualTo(403);  // not the reporter
        assertThat(act(id, citizen, "CONFIRM").status()).isEqualTo(200);

        assertThat(act(999_999_999L, officer, "ASSIGN").status()).isEqualTo(404);
        assertThat(actWith(id, officer, "{\"action\": \"FLY\"}").status()).isEqualTo(400);
    }

    @Test
    void rejectAndDuplicateNeedDetails() {
        String citizen = newCitizen();
        long original = report(citizen);
        long copy = report(citizen);
        String officer = officerFor(zoneOf(copy));

        assertThat(act(copy, officer, "REJECT").status()).isEqualTo(400);      // no reason
        assertThat(act(copy, officer, "DUPLICATE").status()).isEqualTo(400);   // no original
        assertThat(actWith(copy, officer, duplicateOf(copy)).status()).isEqualTo(400);
        assertThat(actWith(copy, officer, duplicateOf(999_999_999L)).status()).isEqualTo(400);

        Reply duplicate = actWith(copy, officer, duplicateOf(original));
        assertThat(duplicate.status()).as("body: %s", duplicate.body()).isEqualTo(200);
        assertThat(duplicate.<String>json("$.status")).isEqualTo("DUPLICATE");
        assertThat(duplicate.<Number>json("$.duplicateOfId").longValue()).isEqualTo(original);

        long third = report(citizen);
        assertThat(actWith(third, officer, duplicateOf(copy)).status()).isEqualTo(400); // copy is itself a duplicate
        Reply rejected = actWith(third, officer, """
                {"action": "REJECT", "note": "Private property"}
                """);
        assertThat(rejected.<String>json("$.status")).isEqualTo("REJECTED");
        assertThat(rejected.<String>json("$.statusReason")).isEqualTo("Private property");
        assertThat(act(third, officer, "ASSIGN").status()).isEqualTo(409);    // closed for good
    }

    @Test
    void onlyOneOfManySimultaneousClicksWins() {
        long id = report(newCitizen());
        String officer = officerFor(zoneOf(id));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Integer>> clicks = IntStream.range(0, 8)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> act(id, officer, "ASSIGN").status(), pool))
                    .toList();
            List<Integer> statuses = clicks.stream().map(CompletableFuture::join).toList();
            assertThat(statuses).containsOnly(200, 409);
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(get("/api/issues/" + id + "/history").<List<Object>>json("$")).hasSize(2);
    }

    @Test
    void departmentsAndDeadlines() {
        assertThat(get("/api/rules").<List<Object>>json("$")).hasSize(6);

        long id = report(newCitizen());
        Reply issue = get("/api/issues/" + id);
        assertThat(issue.<String>json("$.department")).isEqualTo("Roads");
        assertThat(issue.<Number>json("$.slaDays").intValue()).isEqualTo(7);
        assertThat(issue.<Boolean>json("$.overdue")).isFalse();
        assertThat(Instant.parse(issue.<String>json("$.dueAt")))
                .isAfter(Instant.now().plus(Duration.ofDays(6)));

        // pretend the report is 8 days old: a pothole has 7 days, so it is now overdue
        jdbc.update("UPDATE issue SET created_at = now() - interval '8 days' WHERE id = ?", id);
        assertThat(get("/api/issues/" + id).<Boolean>json("$.overdue")).isTrue();

        assertThat(put("/api/admin/rules/POTHOLE", ruleJson("Roads", 10), newCitizen()).status()).isEqualTo(403);
        assertThat(put("/api/admin/rules/POTHOLE", ruleJson("Roads", 0), adminToken()).status()).isEqualTo(400);
        assertThat(put("/api/admin/rules/UFO", ruleJson("Space", 5), adminToken()).status()).isEqualTo(404);
        try {
            Reply changed = put("/api/admin/rules/pothole", ruleJson("Roads and Bridges", 10), adminToken());
            assertThat(changed.status()).as("body: %s", changed.body()).isEqualTo(200);
            assertThat(changed.<Number>json("$.slaDays").intValue()).isEqualTo(10);
            Reply after = get("/api/issues/" + id);
            assertThat(after.<String>json("$.department")).isEqualTo("Roads and Bridges");
            assertThat(after.<Boolean>json("$.overdue")).isFalse();
        } finally {
            put("/api/admin/rules/POTHOLE", ruleJson("Roads", 7), adminToken());
        }

        Reply coverage = get("/api/admin/coverage", adminToken());
        assertThat(coverage.status()).as("body: %s", coverage.body()).isEqualTo(200);
        assertThat(coverage.<List<Object>>json("$")).hasSize(26);
        assertThat(get("/api/admin/coverage", newCitizen()).status()).isEqualTo(403);
    }

    @Test
    void peopleAreNotifiedAboutChanges() {
        String officer = officerFor(zoneAt(SOLAPUR_LAT, SOLAPUR_LNG));
        String citizen = newCitizen();
        long id = report(citizen);
        assertThat(get("/api/notifications", officer).body()).contains("New report #" + id);

        act(id, officer, "ASSIGN");
        act(id, officer, "START");
        Reply inbox = get("/api/notifications", citizen);
        assertThat(inbox.status()).isEqualTo(200);
        assertThat(inbox.<Number>json("$.unread").intValue()).isEqualTo(2);
        assertThat(inbox.<String>json("$.items[0].message")).contains("#" + id).contains("Work has started");

        assertThat(post("/api/notifications/read", null, citizen).status()).isEqualTo(204);
        assertThat(get("/api/notifications", citizen).<Number>json("$.unread").intValue()).isZero();
    }

    @Test
    void resolvedReportsCloseAutomaticallyAfterAWeek() {
        String citizen = newCitizen();
        long id = report(citizen);
        String officer = officerFor(zoneOf(id));
        act(id, officer, "ASSIGN");
        act(id, officer, "START");
        resolve(id, officer);
        jdbc.update("UPDATE issue SET resolved_at = now() - interval '8 days' WHERE id = ?", id);

        assertThat(post("/api/admin/jobs/run", null, newCitizen()).status()).isEqualTo(403);
        Reply run = post("/api/admin/jobs/run", null, adminToken());
        assertThat(run.status()).as("body: %s", run.body()).isEqualTo(200);
        assertThat(run.<Number>json("$.autoClosed").intValue()).isGreaterThanOrEqualTo(1);

        assertThat(get("/api/issues/" + id).<String>json("$.status")).isEqualTo("CLOSED");
        Reply history = get("/api/issues/" + id + "/history");
        assertThat(last(history.<List<String>>json("$[*].action"))).isEqualTo("AUTO_CLOSE");
        assertThat(last(history.<List<String>>json("$[*].actorRole"))).isEqualTo("SYSTEM");
        assertThat(get("/api/notifications", citizen).body()).contains("closed automatically");
    }

    @Test
    void demoAccountsAreSafe() {
        Reply run = post("/api/admin/jobs/run", null, adminToken());
        assertThat(run.status()).as("body: %s", run.body()).isEqualTo(200);
        assertThat(run.<Number>json("$.demoReports").intValue()).isGreaterThanOrEqualTo(300);

        List<Long> demoIds = ids(get("/api/issues?demo=true&limit=1000"));
        assertThat(demoIds).hasSizeGreaterThanOrEqualTo(300);
        Reply sample = get("/api/issues/" + demoIds.get(0));
        assertThat(sample.<Boolean>json("$.demo")).isTrue();
        assertThat(sample.<String>json("$.photoUrl")).startsWith("/demo/");
        assertThat(get("/api/issues/" + demoIds.get(0) + "/history").<List<Object>>json("$")).isNotEmpty();

        // one-click demo logins; demo accounts cannot log in with a password
        String demoOfficer = demoLogin("OFFICER");
        Reply me = get("/api/me", demoOfficer);
        assertThat(me.<Boolean>json("$.demo")).isTrue();
        assertThat(me.<String>json("$.wardCode")).isEqualTo("Z13");
        assertThat(post("/api/auth/login", """
                {"email": "demo-officer@example.com", "password": "anything-at-all"}
                """, null).status()).isEqualTo(401);
        assertThat(get("/api/notifications", demoOfficer).<Number>json("$.unread").intValue()).isPositive();

        // the demo officer can work on demo reports in zone Z13, but not on real ones there
        long demoReport = ids(get("/api/issues?demo=true&ward=Z13&status=SUBMITTED")).get(0);
        assertThat(act(demoReport, demoOfficer, "ASSIGN").status()).isEqualTo(200);
        double[] z13 = pointIn("Z13");
        long realReport = report(newCitizen(), "GARBAGE", z13[0], z13[1]);
        assertThat(zoneOf(realReport)).isEqualTo("Z13");
        Reply blocked = act(realReport, demoOfficer, "ASSIGN");
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.<String>json("$.message")).contains("Demo accounts");
        assertThat(actions(realReport, demoOfficer)).isEmpty();

        // the demo admin can look around but not change anything
        String demoAdmin = demoLogin("ADMIN");
        assertThat(get("/api/admin/users", demoAdmin).status()).isEqualTo(200);
        assertThat(post("/api/admin/users", userJson("OFFICER", "Z05"), demoAdmin).status()).isEqualTo(403);
        assertThat(put("/api/admin/rules/POTHOLE", ruleJson("Roads", 7), demoAdmin).status()).isEqualTo(403);
        assertThat(post("/api/admin/jobs/run", null, demoAdmin).status()).isEqualTo(403);

        // a demo citizen's report becomes a demo report
        long fromDemo = report(demoLogin("CITIZEN"));
        assertThat(get("/api/issues/" + fromDemo).<Boolean>json("$.demo")).isTrue();
    }

    private static String duplicateOf(long original) {
        return "{\"action\": \"DUPLICATE\", \"duplicateOf\": " + original + "}";
    }
}
