package dev.nagarfix.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Zones, accounts, reporting, the public list, uploads and CORS - over real HTTP (see IntegrationTestBase). */
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class ApiIntegrationTests extends IntegrationTestBase {

    @Test
    void healthDatabaseAndZones() {
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
    void locateFindsAZoneOnlyInsideSolapur() {
        Reply inside = get("/api/wards/locate?lat=" + SOLAPUR_LAT + "&lng=" + SOLAPUR_LNG);
        assertThat(inside.status()).as("body: %s", inside.body()).isEqualTo(200);
        assertThat(inside.<String>json("$.code")).matches("Z\\d{2}");

        Reply outside = get("/api/wards/locate?lat=" + PUNE_LAT + "&lng=" + PUNE_LNG);
        assertThat(outside.status()).isEqualTo(404);
        assertThat(outside.<String>json("$.message")).isEqualTo("Location is outside Solapur");

        assertThat(get("/api/wards/locate?lat=95&lng=75").status()).isEqualTo(400);
    }

    @Test
    void signUpLogInAndWhoAmI() {
        String email = "New.Citizen-" + UUID.randomUUID() + "@Example.com";
        String signUp = """
                {"fullName": "New Citizen", "email": "%s", "password": "test-password-1"}
                """.formatted(email);

        Reply created = post("/api/auth/register", signUp, null);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
        assertThat(created.<String>json("$.token")).isNotBlank();
        assertThat(created.<String>json("$.user.role")).isEqualTo("CITIZEN");
        assertThat(created.<String>json("$.user.email")).isEqualTo(email.toLowerCase(Locale.ROOT));
        assertThat(created.<Boolean>json("$.user.demo")).isFalse();

        assertThat(post("/api/auth/register", signUp, null).status()).isEqualTo(409);

        Reply me = get("/api/me", login(email, "test-password-1"));
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
    void privatePagesNeedALogin() {
        assertThat(get("/api/me").status()).isEqualTo(401);
        assertThat(get("/api/me", "not-a-real-token").status()).isEqualTo(401);
        assertThat(get("/api/issues/mine").status()).isEqualTo(401);
        assertThat(post("/api/issues", issueJson("POTHOLE", SOLAPUR_LAT, SOLAPUR_LNG, PHOTO), null).status())
                .isEqualTo(401);
        assertThat(post("/api/uploads/signature", null, null).status()).isEqualTo(401);
        assertThat(get("/api/notifications").status()).isEqualTo(401);
        assertThat(get("/api/issues/1/actions").status()).isEqualTo(401);
    }

    @Test
    void onlyAdminsManageUsers() {
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
    void aReportIsRoutedToItsZoneAndShowsUpEverywhere() {
        String token = newCitizen();
        Reply created = post("/api/issues", issueJson("POTHOLE", SOLAPUR_LAT, SOLAPUR_LNG, PHOTO), token);
        assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);

        long id = created.<Number>json("$.id").longValue();
        String zone = created.json("$.wardCode");
        assertThat(zone).matches("Z\\d{2}");
        assertThat(created.<String>json("$.status")).isEqualTo("SUBMITTED");
        assertThat(created.<Boolean>json("$.demo")).isFalse();
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
        assertThat(ids(get("/api/issues?demo=false"))).contains(id);
        assertThat(ids(get("/api/issues?demo=true"))).doesNotContain(id);

        assertThat(get("/api/issues/999999999").status()).isEqualTo(404);
    }

    @Test
    void badReportsAreRejected() {
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
    void photoUploadsAreSignedForLoggedInUsers() {
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
    void theWebsiteMayCallTheApiButOtherSitesMayNot() {
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
}
