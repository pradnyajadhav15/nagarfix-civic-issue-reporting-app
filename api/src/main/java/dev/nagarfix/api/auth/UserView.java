package dev.nagarfix.api.auth;

/** What the API shows about a user (never the password hash). */
public record UserView(Long id, String fullName, String email, Role role, String wardCode, boolean demo) {

    public static UserView of(AppUser u) {
        return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getWardCode(), u.isDemo());
    }
}
