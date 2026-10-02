-- V5: status workflow. Every status change is also written to issue_event (the audit trail).
ALTER TABLE issue
    ADD COLUMN assigned_officer_id  BIGINT        REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN resolution_photo_url VARCHAR(500),
    ADD COLUMN resolution_note      VARCHAR(1000),
    ADD COLUMN status_reason        VARCHAR(1000),
    ADD COLUMN duplicate_of_id      BIGINT        REFERENCES issue (id) ON DELETE SET NULL,
    ADD COLUMN resolved_at          TIMESTAMPTZ,
    ADD COLUMN closed_at            TIMESTAMPTZ;

-- Finds reports waiting for the citizen's answer (auto-close after N days)
CREATE INDEX idx_issue_resolved ON issue (resolved_at) WHERE status = 'RESOLVED';

CREATE TABLE issue_event (
    id          BIGSERIAL     PRIMARY KEY,
    issue_id    BIGINT        NOT NULL REFERENCES issue (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)   NOT NULL,
    action      VARCHAR(20)   NOT NULL,
    actor_id    BIGINT        REFERENCES app_user (id) ON DELETE SET NULL,
    actor_role  VARCHAR(20)   NOT NULL CHECK (actor_role IN ('CITIZEN', 'OFFICER', 'ADMIN', 'SYSTEM')),
    note        VARCHAR(1000),
    photo_url   VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_issue_event_issue ON issue_event (issue_id, created_at, id);

-- Reports that already exist get their first history entry
INSERT INTO issue_event (issue_id, from_status, to_status, action, actor_id, actor_role, created_at)
SELECT i.id, NULL, 'SUBMITTED', 'SUBMIT', i.reporter_id, u.role, i.created_at
FROM issue i
JOIN app_user u ON u.id = i.reporter_id;
