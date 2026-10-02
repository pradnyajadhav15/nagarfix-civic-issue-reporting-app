-- V7: in-app notifications. email_status also makes this table the email outbox:
-- rows marked PENDING are emailed by a background job, then marked SENT or FAILED.
CREATE TABLE notification (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    issue_id       BIGINT        REFERENCES issue (id) ON DELETE CASCADE,
    message        VARCHAR(300)  NOT NULL,
    read_at        TIMESTAMPTZ,
    email_status   VARCHAR(10)   NOT NULL DEFAULT 'SKIPPED'
                   CHECK (email_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    email_attempts INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_user   ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_outbox ON notification (id) WHERE email_status = 'PENDING';

-- When each background job last ran, so a server that was asleep catches up when it wakes
CREATE TABLE job_run (
    name        VARCHAR(40)  PRIMARY KEY,
    last_run_at TIMESTAMPTZ  NOT NULL
);
