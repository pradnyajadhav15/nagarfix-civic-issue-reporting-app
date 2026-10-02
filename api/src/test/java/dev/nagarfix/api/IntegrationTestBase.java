package dev.nagarfix.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

/**
 * Shared setup for the end-to-end tests: a real server on a random port, a real Postgres + PostGIS
 * database, and small helpers to call the API over HTTP.
 *
 * These tests create users and reports, so each test class is marked
 * {@code @EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")}:
 * they run in GitHub Actions on a throwaway database and never touch the live Neon database.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.admin.email=" + IntegrationTestBase.ADMIN_EMAIL,
                "app.admin.password=" + IntegrationTestBase.ADMIN_PASSWORD,
                "app.cloudinary.url=cloudinary://test-key:" + IntegrationTestBase.CLOUD_SECRET + "@test-cloud",
                "app.jobs.enabled=false"
        })
abstract class IntegrationTestBase {

    static final String ADMIN_EMAIL = "admin@example.com";
    static final String ADMIN_PASSWORD = "test-admin-password";
    static final String CLOUD_SECRET = "test-secret";
    static final String PHOTO = "https://res.cloudinary.com/test-cloud/image/upload/v1/nagarfix/issues/test.jpg";
    static final String AFTER_PHOTO = "https://res.cloudinary.com/test-cloud/image/upload/v1/nagarfix/issues/fixed.jpg";
    static final String WEBSITE = "https://nagarfix-civic-issue-reporting-app.vercel.app";

    // Solapur city centre (inside the zones) and Pune (outside them)
    static final double SOLAPUR_LAT = 17.6599;
    static final double SOLAPUR_LNG = 75.9064;
    static final double PUNE_LAT = 18.5204;
    static final double PUNE_LNG = 73.8567;
    static final String SOLAPUR_BOX = "75.80,17.55,76.02,17.77";
    static final String PUNE_BOX = "73.70,18.40,74.00,18.65";

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    /** Direct database access, used to "travel in time" (e.g. make a report 8 days old). */
    @Autowired
    protected JdbcTemplate jdbc;

    /** One HTTP response. json("$.path") reads a value from the JSON body. */
    record Reply(int status, String body, HttpHeaders headers) {

        <T> T json(String path) {
            return JsonPath.read(body, path);
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    // ------------------------------------------------------------------ HTTP

    Reply send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    Reply get(String path) {
        return get(path, null);
    }

    Reply get(String path, String token) {
        return send(request(path, token).GET());
    }

    Reply post(String path, String json, String token) {
        HttpRequest.Builder request = request(path, token);
        if (json == null) {
            return send(request.POST(HttpRequest.BodyPublishers.noBody()));
        }
        return send(request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    Reply put(String path, String json, String token) {
        return send(request(path, token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** What a browser asks before calling the API from another website (CORS preflight). */
    Reply preflight(String origin) {
        return send(request("/api/issues", null)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"));
    }

    // ------------------------------------------------------------------ people

    /** Signs up a new citizen and returns their login token. */
    String newCitizen() {
        Reply created = post("/api/auth/register", """
                {"fullName": "Test Citizen", "email": "citizen-%s@example.com", "password": "test-password-1"}
                """.formatted(UUID.randomUUID()), null);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
        return created.json("$.token");
    }

    /** Logs in as the admin that AdminBootstrap creates at startup. */
    String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /** The admin creates a new officer for this zone; returns the officer's login token. */
    String officerFor(String zone) {
        String email = "officer-" + UUID.randomUUID() + "@example.com";
        Reply created = post("/api/admin/users", """
                {"fullName": "Zone Officer", "email": "%s", "password": "officer-password",
                 "role": "OFFICER", "wardCode": "%s"}
                """.formatted(email, zone), adminToken());
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
        return login(email, "officer-password");
    }

    String login(String email, String password) {
        Reply login = post("/api/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password), null);
        assertThat(login.status()).as("body: %s", login.body()).isEqualTo(200);
        return login.json("$.token");
    }

    /** One-click demo login: CITIZEN, OFFICER or ADMIN. */
    String demoLogin(String role) {
        Reply demo = post("/api/auth/demo", "{\"role\": \"" + role + "\"}", null);
        assertThat(demo.status()).as("body: %s", demo.body()).isEqualTo(200);
        return demo.json("$.token");
    }

    // ------------------------------------------------------------------ reports

    /** Reports an issue and returns its number. */
    long report(String token, String category, double lat, double lng) {
        Reply created = post("/api/issues", issueJson(category, lat, lng, PHOTO), token);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
        return created.<Number>json("$.id").longValue();
    }

    long report(String token) {
        return report(token, "POTHOLE", SOLAPUR_LAT, SOLAPUR_LNG);
    }

    String zoneOf(long issueId) {
        return get("/api/issues/" + issueId).json("$.wardCode");
    }

    String zoneAt(double lat, double lng) {
        return get("/api/wards/locate?lat=" + lat + "&lng=" + lng).json("$.code");
    }

    /** A point that is surely inside the given zone. */
    double[] pointIn(String zone) {
        Map<String, Object> p = jdbc.queryForMap("""
                SELECT ST_Y(pt) AS lat, ST_X(pt) AS lng
                FROM (SELECT ST_PointOnSurface(boundary) AS pt FROM ward WHERE code = ?) x
                """, zone);
        return new double[] {((Number) p.get("lat")).doubleValue(), ((Number) p.get("lng")).doubleValue()};
    }

    /** Runs a workflow action that needs no extra details (ASSIGN, START, CONFIRM...). */
    Reply act(long issueId, String token, String action) {
        return actWith(issueId, token, "{\"action\": \"" + action + "\"}");
    }

    Reply actWith(long issueId, String token, String json) {
        return post("/api/issues/" + issueId + "/actions", json, token);
    }

    Reply resolve(long issueId, String token) {
        Reply resolved = actWith(issueId, token, """
                {"action": "RESOLVE", "note": "Filled and levelled", "photoUrl": "%s"}
                """.formatted(AFTER_PHOTO));
        assertThat(resolved.status()).as("body: %s", resolved.body()).isEqualTo(200);
        return resolved;
    }

    /** The actions this person may take on this report right now. */
    List<String> actions(long issueId, String token) {
        Reply allowed = get("/api/issues/" + issueId + "/actions", token);
        assertThat(allowed.status()).as("body: %s", allowed.body()).isEqualTo(200);
        return allowed.json("$");
    }

    // ------------------------------------------------------------------ JSON bodies

    static String issueJson(String category, double lat, double lng, String photoUrl) {
        return """
                {"category": "%s", "description": "Test report", "lat": %s, "lng": %s,
                 "address": "Test address", "photoUrl": "%s"}
                """.formatted(category, lat, lng, photoUrl);
    }

    static String userJson(String role, String wardCode) {
        return """
                {"fullName": "Zone Officer", "email": "officer-%s@example.com", "password": "officer-password",
                 "role": "%s", "wardCode": %s}
                """.formatted(UUID.randomUUID(), role, wardCode == null ? "null" : "\"" + wardCode + "\"");
    }

    static String ruleJson(String department, int slaDays) {
        return "{\"department\": \"" + department + "\", \"slaDays\": " + slaDays + "}";
    }

    static List<Long> ids(Reply list) {
        assertThat(list.status()).as("body: %s", list.body()).isEqualTo(200);
        List<Object> raw = list.json("$[*].id");
        return raw.stream().map(id -> ((Number) id).longValue()).toList();
    }

    static String sha1Hex(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }
}
