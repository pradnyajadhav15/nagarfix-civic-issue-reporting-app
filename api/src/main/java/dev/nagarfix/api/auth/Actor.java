package dev.nagarfix.api.auth;

import org.springframework.http.HttpStatus;

import dev.nagarfix.api.common.ApiException;

/** The logged-in person making a request, loaded fresh from the database (role and zone can change). */
public record Actor(long id, Role role, String wardCode, boolean demo) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Demo accounts can look around, but they cannot change users or settings. */
    public void requireNotDemo() {
        if (demo) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "This is a demo account: it can look around but can't change this.");
        }
    }
}
