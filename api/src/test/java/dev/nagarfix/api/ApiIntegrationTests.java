package dev.nagarfix.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end checks over real HTTP, against a real Postgres + PostGIS database.
 *
 * These tests create users and reports, so they only run when INTEGRATION_TESTS=true:
 * in GitHub Actions, on a throwaway database. They never touch the live Neon database.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.admin.email=" + ApiIntegrationTests.ADMIN_EMAIL,
                "app.admin.password=" + ApiIntegrationTests.ADMIN_PASSWORD,
                "app.cloudinary.url=cloudinary://test-key:" + ApiIntegrationTests.CLOUD_SECRET + "@test-cloud"
        })
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class ApiIntegrationTests {

    static final String ADMIN_EMAIL = "admin@example.com";
    static final String ADMIN_PASSWORD = "test-admin-password";
    static final String CLOUD_SECRET = "test-secret";
    static final String PHOTO = "https://res.cloudinary.com/test-cloud/image/upload/v1/nagarfix/issues/test.jpg";
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

    // ------------------------------------------------------------------ tests

    @Test
    void healthDatabaseAndZones() throws Exception {
        Reply health = get("/actuator/health");
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.<String>json("$.status")).isEqualTo("UP");

        Reply db = get("/api/db");
        assertThat(db.status()).as("body: %s", db.body()).isEqualTo(200);
        assertThat(db.<String>json("$.postgis")).isNotBlank();
        assertThat(db.<Number>json("$.wards").intValue()).isEqualTo(26);

        Reply zones = get("/api/wards");
        assertThat(zones.status()).isEqualTo(200);
        assertThat(zones.<List<Object>>json("$.features")).hasSize(26);
    }

    @Test
    void locateFindsAZoneOnlyInsideSolapur() throws Exception {
        Reply inside = get("/api/wards/locate?lat=" + SOLAPUR_LAT + "&lng=" + SOLAPUR_LNG);
        assertThat(inside.status()).as("body: %s", inside.body()).isEqualTo(200);
        assertThat(inside.<String>json("$.code")).matches("Z\\d{2}");

        Reply outside = get("/api/wards/locate?lat=" + PUNE_LAT + "&lng=" + PUNE_LNG);
        assertThat(outside.status()).isEqualTo(404);
        assertThat(outside.<String>json("$.message")).isEqualTo("Location is outside Solapur");

        assertThat(get("/api/wards/locate?lat=95&lng=75").status()).isEqualTo(400);
    }

    @Test
    void signUpLogInAndWhoAmI() throws Exception {
        String email = "New.Citizen-" + UUID.randomUUID() + "@Example.com";
        String signUp = """
                {"fullName": "New Citizen", "email": "%s", "password": "test-password-1"}
                """.formatted(email);

        Reply created = post("/api/auth/register", signUp, null);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
        assertThat(created.<String>json("$.token")).isNotBlank();
        assertThat(created.<String>json("$.user.role")).isEqualTo("CITIZEN");
        assertThat(created.<String>json("$.user.email")).isEqualTo(email.toLowerCase(Locale.ROOT));

        assertThat(post("/api/auth/register", signUp, null).status()).isEqualTo(409);

        Reply login = post("/api/auth/login", """
                {"email": "%s", "password": "test-password-1"}
                """.formatted(email), null);
        assertThat(login.status()).as("body: %s", login.body()).isEqualTo(200);

        Reply me = get("/api/me", login.json("$.token"));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.<String>json("$.fullName")).isEqualTo("New Citizen");

        Reply wrong = post("/api/auth/login", """
                {"email": "%s", "password": "wrong-password"}
                """.formatted(email), null);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.<String>json("$.message")).isEqualTo("Invalid email or password");

        Reply shortPassword = post("/api/auth/register", """
                {"fullName": "Short", "email": "short-%s@example.com", "password": "123"}
                """.formatted(UUID.randomUUID()), null);
        assertThat(shortPassword.status()).isEqualTo(400);
    }

    @Test
    void privatePagesNeedALogin() throws Exception {
        assertThat(get("/api/me").status()).isEqualTo(401);
        assertThat(get("/api/me", "not-a-real-token").status()).isEqualTo(401);
        assertThat(get("/api/issues/mine").status()).isEqualTo(401);
        assertThat(post("/api/issues", issueJson("POTHOLE", SOLAPUR_LAT, SOLAPUR_LNG, PHOTO), null).status())
                .isEqualTo(401);
        assertThat(post("/api/uploads/signature", null, null).status()).isEqualTo(401);
    }

    @Test
    void onlyAdminsManageUsers() throws Exception {
        assertThat(get("/api/admin/users", newCitizen()).status()).isEqualTo(403);

        String admin = adminToken();
        assertThat(get("/api/admin/users", admin).status()).isEqualTo(200);

        Reply officer = post("/api/admin/users", userJson("OFFICER", "Z05"), admin);
        assertThat(officer.status()).as("body: %s", officer.body()).isEqualTo(201);
        assertThat(officer.<String>json("$.role")).isEqualTo("OFFICER");
        assertThat(officer.<String>json("$.wardCode")).isEqualTo("Z05");

        assertThat(post("/api/admin/users", userJson("OFFICER", null), admin).status()).isEqualTo(400);
        assertThat(post("/api/admin/users", userJson("OFFICER", "Z99"), admin).status()).isEqualTo(400);
    }

    @Test
    void aReportIsRoutedToItsZoneAndShowsUpEverywhere() throws Exception {
        String token = newCitizen();
        Reply created = post("/api/issues", issueJson("POTHOLE", SOLAPUR_LAT, SOLAPUR_LNG, PHOTO), token);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);

        long id = created.<Number>json("$.id").longValue();
        String zone = created.json("$.wardCode");
        assertThat(zone).matches("Z\\d{2}");
        assertThat(created.<String>json("$.status")).isEqualTo("SUBMITTED");
        assertThat(created.<Number>json("$.lat").doubleValue()).isCloseTo(SOLAPUR_LAT, within(1e-6));
        assertThat(created.<Number>json("$.lng").doubleValue()).isCloseTo(SOLAPUR_LNG, within(1e-6));

        Reply one = get("/api/issues/" + id);
        assertThat(one.status()).isEqualTo(200);
        assertThat(one.<String>json("$.wardCode")).isEqualTo(zone);

        assertThat(ids(get("/api/issues/mine", token))).contains(id);
        assertThat(ids(get("/api/issues/mine", newCitizen()))).doesNotContain(id);
        assertThat(ids(get("/api/issues"))).contains(id);
        assertThat(ids(get("/api/issues?status=submitted&category=pothole&ward=" + zone))).contains(id);
        assertThat(ids(get("/api/issues?bbox=" + SOLAPUR_BOX))).contains(id);
        assertThat(ids(get("/api/issues?bbox=" + PUNE_BOX))).doesNotContain(id);
        assertThat(ids(get("/api/issues?category=GARBAGE"))).doesNotContain(id);
        assertThat(ids(get("/api/issues?status=RESOLVED"))).doesNotContain(id);

        assertThat(get("/api/issues/999999999").status()).isEqualTo(404);
    }

    @Test
    void badReportsAreRejected() throws Exception {
        String token = newCitizen();

        Reply otherPhoto = post("/api/issues",
                issueJson("GARBAGE", SOLAPUR_LAT, SOLAPUR_LNG, "https://example.com/photo.jpg"), token);
        assertThat(otherPhoto.status()).isEqualTo(400);
        assertThat(otherPhoto.<String>json("$.message")).isEqualTo("The photo must be uploaded through NagarFix");

        Reply inPune = post("/api/issues", issueJson("GARBAGE", PUNE_LAT, PUNE_LNG, PHOTO), token);
        assertThat(inPune.status()).isEqualTo(400);
        assertThat(inPune.<String>json("$.message")).contains("outside Solapur");

        assertThat(post("/api/issues", issueJson("UFO", SOLAPUR_LAT, SOLAPUR_LNG, PHOTO), token).status())
                .isEqualTo(400);

        Reply noPhoto = post("/api/issues", """
                {"category": "GARBAGE", "lat": 17.6599, "lng": 75.9064}
                """, token);
        assertThat(noPhoto.status()).isEqualTo(400);
        assertThat(noPhoto.<String>json("$.message")).startsWith("photoUrl");

        assertThat(get("/api/issues?bbox=1,2,3").status()).isEqualTo(400);
        assertThat(get("/api/issues?bbox=a,b,c,d").status()).isEqualTo(400);
    }

    @Test
    void photoUploadsAreSignedForLoggedInUsers() throws Exception {
        Reply signed = post("/api/uploads/signature", null, newCitizen());
        assertThat(signed.status()).as("body: %s", signed.body()).isEqualTo(200);
        assertThat(signed.<String>json("$.uploadUrl")).isEqualTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload");
        assertThat(signed.<String>json("$.apiKey")).isEqualTo("test-key");

        // Cloudinary's rule: sha1("folder=...&timestamp=..." + secret)
        String folder = signed.json("$.folder");
        long timestamp = signed.<Number>json("$.timestamp").longValue();
        assertThat(signed.<String>json("$.signature"))
                .isEqualTo(sha1Hex("folder=" + folder + "&timestamp=" + timestamp + CLOUD_SECRET));
    }

    @Test
    void theWebsiteMayCallTheApiButOtherSitesMayNot() throws Exception {
        Reply website = preflight(WEBSITE);
        assertThat(website.status()).isEqualTo(200);
        assertThat(website.header("Access-Control-Allow-Origin")).isEqualTo(WEBSITE);

        Reply local = preflight("http://localhost:3000");
        assertThat(local.status()).isEqualTo(200);
        assertThat(local.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");

        Reply other = preflight("https://other-site.example.com");
        assertThat(other.status()).isEqualTo(403);
        assertThat(other.header("Access-Control-Allow-Origin")).isNull();
    }

    // ------------------------------------------------------------------ helpers

    /** One HTTP response. json("$.path") reads a value from the JSON body. */
    record Reply(int status, String body, HttpHeaders headers) {

        <T> T json(String path) {
            return JsonPath.read(body, path);
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private Reply send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body(), response.headers());
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private Reply get(String path) throws Exception {
        return get(path, null);
    }

    private Reply get(String path, String token) throws Exception {
        return send(request(path, token).GET());
    }

    private Reply post(String path, String json, String token) throws Exception {
        HttpRequest.Builder request = request(path, token);
        if (json == null) {
            return send(request.POST(HttpRequest.BodyPublishers.noBody()));
        }
        return send(request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** What a browser asks before calling the API from another website (CORS preflight). */
    private Reply preflight(String origin) throws Exception {
        return send(request("/api/issues", null)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"));
    }

    /** Signs up a new citizen and returns their login token. */
    private String newCitizen() throws Exception {
        Reply created = post("/api/auth/register", """
                {"fullName": "Test Citizen", "email": "citizen-%s@example.com", "password": "test-password-1"}
                """.formatted(UUID.randomUUID()), null);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
        return created.json("$.token");
    }

    /** Logs in as the admin that AdminBootstrap creates at startup. */
    private String adminToken() throws Exception {
        Reply login = post("/api/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD), null);
        assertThat(login.status()).as("body: %s", login.body()).isEqualTo(200);
        return login.json("$.token");
    }

    private static String userJson(String role, String wardCode) {
        return """
                {"fullName": "Zone Officer", "email": "officer-%s@example.com", "password": "officer-password",
                 "role": "%s", "wardCode": %s}
                """.formatted(UUID.randomUUID(), role, wardCode == null ? "null" : "\"" + wardCode + "\"");
    }

    private static String issueJson(String category, double lat, double lng, String photoUrl) {
        return """
                {"category": "%s", "description": "Test report", "lat": %s, "lng": %s,
                 "address": "Test address", "photoUrl": "%s"}
                """.formatted(category, lat, lng, photoUrl);
    }

    private static List<Long> ids(Reply list) {
        assertThat(list.status()).as("body: %s", list.body()).isEqualTo(200);
        List<Object> raw = list.json("$[*].id");
        return raw.stream().map(id -> ((Number) id).longValue()).toList();
    }

    private static String sha1Hex(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
