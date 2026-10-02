package dev.nagarfix.api.common;

import org.springframework.http.HttpStatus;

/** An error with an HTTP status and a message that is safe to show to the user. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
