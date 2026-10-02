-- V3: user accounts with roles. Officers belong to one ward (zone).
CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CITIZEN', 'OFFICER', 'ADMIN')),
    ward_code     VARCHAR(20)  REFERENCES ward (code),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT officer_needs_ward CHECK (role <> 'OFFICER' OR ward_code IS NOT NULL)
);

CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));
