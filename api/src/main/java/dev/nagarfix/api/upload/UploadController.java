package dev.nagarfix.api.upload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.nagarfix.api.common.ApiException;

/**
 * Signs a one-time photo upload. The browser sends the photo straight to Cloudinary;
 * the API secret never leaves the server.
 */
@RestController
public class UploadController {

    static final String FOLDER = "nagarfix/issues";

    private final CloudinaryProps cloud;

    public UploadController(CloudinaryProps cloud) {
        this.cloud = cloud;
    }

    @PostMapping("/api/uploads/signature")
    public Map<String, Object> signature() {
        if (!cloud.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Photo uploads are not configured on the server");
        }
        long timestamp = Instant.now().getEpochSecond();
        // Cloudinary signature: parameters sorted alphabetically, then the API secret, hashed with SHA-1
        String signature = sha1Hex("folder=" + FOLDER + "&timestamp=" + timestamp + cloud.apiSecret());
        return Map.<String, Object>of(
                "uploadUrl", "https://api.cloudinary.com/v1_1/" + cloud.cloudName() + "/image/upload",
                "apiKey", cloud.apiKey(),
                "timestamp", timestamp,
                "folder", FOLDER,
                "signature", signature);
    }

    private static String sha1Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
