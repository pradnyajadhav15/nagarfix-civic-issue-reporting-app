package dev.nagarfix.api.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads CLOUDINARY_URL in the form cloudinary://API_KEY:API_SECRET@CLOUD_NAME */
@Component
public class CloudinaryProps {

    private static final String PREFIX = "cloudinary://";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public CloudinaryProps(@Value("${app.cloudinary.url:}") String url) {
        String name = "";
        String key = "";
        String secret = "";
        String value = url == null ? "" : url.trim();
        int at = value.lastIndexOf('@');
        if (value.startsWith(PREFIX) && at > PREFIX.length()) {
            String[] credentials = value.substring(PREFIX.length(), at).split(":", 2);
            if (credentials.length == 2) {
                key = credentials[0];
                secret = credentials[1];
                name = value.substring(at + 1);
            }
        }
        this.cloudName = name;
        this.apiKey = key;
        this.apiSecret = secret;
    }

    public boolean configured() {
        return !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    public String cloudName() {
        return cloudName;
    }

    public String apiKey() {
        return apiKey;
    }

    String apiSecret() {
        return apiSecret;
    }

    /** Only photos stored in our own Cloudinary account are accepted in reports. */
    public boolean isOwnPhotoUrl(String url) {
        return configured() && url != null
                && url.startsWith("https://res.cloudinary.com/" + cloudName + "/image/upload/");
    }
}
