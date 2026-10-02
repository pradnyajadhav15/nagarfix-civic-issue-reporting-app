-- V4: civic issues reported by citizens. The ward is found automatically from the GPS point.
CREATE TABLE issue (
    id          BIGSERIAL    PRIMARY KEY,
    category    VARCHAR(30)  NOT NULL
                CHECK (category IN ('POTHOLE', 'GARBAGE', 'STREETLIGHT', 'DRAINAGE', 'WATER_LEAK', 'OTHER')),
    description VARCHAR(1000),
    location    geometry(Point, 4326) NOT NULL,
    address     VARCHAR(300),
    ward_code   VARCHAR(20)  REFERENCES ward (code),
    photo_url   VARCHAR(500) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED'
                CHECK (status IN ('SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED',
                                  'CLOSED', 'REOPENED', 'REJECTED', 'DUPLICATE')),
    reporter_id BIGINT       NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_issue_location    ON issue USING GIST (location);
CREATE INDEX idx_issue_ward_status ON issue (ward_code, status);
CREATE INDEX idx_issue_reporter    ON issue (reporter_id, created_at DESC);
