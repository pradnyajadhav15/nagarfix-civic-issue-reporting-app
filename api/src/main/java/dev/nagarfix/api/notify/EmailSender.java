package dev.nagarfix.api.notify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends email through Brevo's HTTP API (free plan). Render's free plan blocks SMTP, so a web API is used.
 * Switched off until BREVO_API_KEY and MAIL_FROM (a sender address verified in Brevo) are set.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final URI BREVO = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final String from;
    private final String fromName;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public EmailSender(@Value("${app.email.brevo-api-key:}") String apiKey,
                       @Value("${app.email.from:}") String from,
                       @Value("${app.email.from-name:NagarFix}") String fromName) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
        this.fromName = fromName;
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !from.isEmpty();
    }

    /** Returns true when Brevo accepted the email. */
    public boolean send(String to, String toName, String subject, String text, String link) {
        if (!enabled()) {
            return false;
        }
        String html = "<p>" + html(text) + "</p>"
                + "<p><a href=\"" + html(link) + "\">Open the report</a></p>"
                + "<p style=\"color:#64748b;font-size:12px\">NagarFix is an independent student project. "
                + "Reports are not forwarded to any authority.</p>";
        String body = "{\"sender\":{\"name\":" + json(fromName) + ",\"email\":" + json(from) + "},"
                + "\"to\":[{\"email\":" + json(to) + ",\"name\":" + json(toName) + "}],"
                + "\"subject\":" + json(subject) + ","
                + "\"htmlContent\":" + json(html) + ","
                + "\"textContent\":" + json(text + "\n\nOpen the report: " + link) + "}";
        HttpRequest request = HttpRequest.newBuilder(BREVO)
                .timeout(Duration.ofSeconds(20))
                .header("api-key", apiKey)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            log.warn("Brevo did not accept an email ({}): {}", response.statusCode(), response.body());
        } catch (IOException e) {
            log.warn("Could not reach Brevo: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    static String json(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    static String html(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
