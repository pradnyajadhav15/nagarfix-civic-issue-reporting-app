-- V8: one-click demo accounts and clearly marked demo reports.
-- nagarfix_reset_demo() rebuilds all demo data; a background job runs it once a day,
-- so visitors can click around freely without touching real reports.
ALTER TABLE app_user ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE issue    ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_issue_demo ON issue (is_demo) WHERE is_demo;

-- Demo accounts have no usable password: they sign in only through the "Try a demo" buttons.
INSERT INTO app_user (full_name, email, password_hash, role, ward_code, is_demo)
SELECT v.full_name, v.email, '{noop}' || md5(random()::text || clock_timestamp()::text), v.role, v.ward, TRUE
FROM (VALUES
    ('Demo Citizen',   'demo-citizen@example.com',   'CITIZEN', NULL),
    ('Demo Officer',   'demo-officer@example.com',   'OFFICER', 'Z13'),
    ('Demo Admin',     'demo-admin@example.com',     'ADMIN',   NULL),
    ('Demo Residents', 'demo-residents@example.com', 'CITIZEN', NULL)
) AS v (full_name, email, role, ward)
WHERE NOT EXISTS (SELECT 1 FROM app_user u WHERE lower(u.email) = v.email);

CREATE OR REPLACE FUNCTION nagarfix_reset_demo() RETURNS INTEGER
LANGUAGE plpgsql AS $fn$
DECLARE
    v_citizen     BIGINT;
    v_officer     BIGINT;
    v_residents   BIGINT;
    v_count       INTEGER := 0;
    p             RECORD;
    v_cat         TEXT;
    v_dept        TEXT;
    v_sla         INTEGER;
    v_profile     TEXT;
    v_reporter    BIGINT;
    v_created     TIMESTAMPTZ;
    v_outcome     DOUBLE PRECISION;
    v_reply       DOUBLE PRECISION;
    v_dup         BIGINT;
    v_id          BIGINT;
    t             TIMESTAMPTZ;
    k             INTEGER;
    n_applied     INTEGER;
    ev_time       TIMESTAMPTZ[];
    ev_from       TEXT[];
    ev_to         TEXT[];
    ev_action     TEXT[];
    ev_role       TEXT[];
    ev_note       TEXT[];
    ev_photo      TEXT[];
    v_status      TEXT;
    v_updated     TIMESTAMPTZ;
    v_assigned    BIGINT;
    v_resolved_at TIMESTAMPTZ;
    v_closed_at   TIMESTAMPTZ;
    v_res_note    TEXT;
    v_res_photo   TEXT;
    v_reason      TEXT;
    v_dup_of      BIGINT;
BEGIN
    SELECT id INTO v_citizen   FROM app_user WHERE email = 'demo-citizen@example.com';
    SELECT id INTO v_officer   FROM app_user WHERE email = 'demo-officer@example.com';
    SELECT id INTO v_residents FROM app_user WHERE email = 'demo-residents@example.com';
    IF v_citizen IS NULL OR v_officer IS NULL OR v_residents IS NULL THEN
        RETURN 0;
    END IF;

    -- Remove yesterday's demo data (history and notifications go with it)
    DELETE FROM notification WHERE user_id IN (SELECT id FROM app_user WHERE is_demo);
    DELETE FROM issue WHERE is_demo;

    PERFORM setseed(0.42);

    FOR p IN
        SELECT pts.n, pts.geom, pts.extra, z.code AS ward
        FROM (
            SELECT row_number() OVER () AS n, d.geom, FALSE AS extra
            FROM (SELECT (ST_Dump(ST_GeneratePoints(boundary, 300, 7))).geom AS geom
                  FROM city_boundary WHERE id = 1) d
            UNION ALL
            SELECT 1000 + row_number() OVER (), d.geom, TRUE
            FROM (SELECT (ST_Dump(ST_GeneratePoints(boundary, 30, 11))).geom AS geom
                  FROM ward WHERE code = 'Z13') d
        ) pts
        CROSS JOIN LATERAL (
            SELECT code FROM ward WHERE ST_Intersects(boundary, pts.geom) ORDER BY code LIMIT 1
        ) z
        ORDER BY pts.n
    LOOP
        -- Category, with potholes and garbage the most common
        v_outcome := random();
        v_cat := CASE
            WHEN v_outcome < 0.30 THEN 'POTHOLE'
            WHEN v_outcome < 0.58 THEN 'GARBAGE'
            WHEN v_outcome < 0.73 THEN 'STREETLIGHT'
            WHEN v_outcome < 0.85 THEN 'DRAINAGE'
            WHEN v_outcome < 0.95 THEN 'WATER_LEAK'
            ELSE 'OTHER' END;
        SELECT department, sla_days INTO v_dept, v_sla FROM category_rule WHERE category = v_cat;
        v_dept := COALESCE(v_dept, 'Municipal');
        v_sla := COALESCE(v_sla, 7);

        -- Who reported it, and which story it follows
        v_reporter := CASE WHEN p.n % 9 = 0 THEN v_citizen ELSE v_residents END;
        v_profile := 'random';
        IF p.extra AND p.n <= 1008 THEN
            v_profile := 'fresh';                      -- brand new, waiting for the demo officer
        ELSIF p.extra AND p.n <= 1014 THEN
            v_profile := 'working';                    -- in progress with the demo officer
        ELSIF p.extra AND p.n <= 1016 THEN
            v_profile := 'reopened';                   -- the citizen said it is not fixed yet
        ELSIF v_reporter = v_citizen AND p.n % 27 = 0 THEN
            v_profile := 'awaiting';                   -- fixed recently, waiting for the citizen's answer
        END IF;

        v_created := CASE v_profile
            WHEN 'fresh'    THEN now() - (p.n - 1000) * interval '3 hours'
            WHEN 'working'  THEN now() - (3 + random() * 3) * interval '1 day'
            WHEN 'reopened' THEN now() - (10 + random() * 4) * interval '1 day'
            WHEN 'awaiting' THEN now() - (8 + random() * 12) * interval '1 day'
            ELSE now() - power(random(), 1.6) * 180 * interval '1 day' END;

        ev_time   := ARRAY[v_created];
        ev_from   := ARRAY[NULL]::TEXT[];
        ev_to     := ARRAY['SUBMITTED'];
        ev_action := ARRAY['SUBMIT'];
        ev_role   := ARRAY['CITIZEN'];
        ev_note   := ARRAY[NULL]::TEXT[];
        ev_photo  := ARRAY[NULL]::TEXT[];
        v_outcome := random();
        t := v_created + (0.1 + random() * 1.5) * interval '1 day';
        v_dup := NULL;
        IF v_profile = 'random' AND v_outcome >= 0.04 AND v_outcome < 0.08 THEN
            SELECT id INTO v_dup FROM issue
            WHERE is_demo AND category = v_cat AND status NOT IN ('DUPLICATE', 'REJECTED')
            ORDER BY id DESC LIMIT 1;
        END IF;

        IF v_profile = 'fresh' THEN
            NULL;  -- only "Submitted"
        ELSIF v_profile = 'random' AND v_outcome < 0.04 THEN
            ev_time := ev_time || t; ev_from := ev_from || 'SUBMITTED'::TEXT; ev_to := ev_to || 'REJECTED'::TEXT;
            ev_action := ev_action || 'REJECT'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
            ev_note := ev_note || 'Private property - not handled by the municipality'::TEXT; ev_photo := ev_photo || NULL::TEXT;
        ELSIF v_dup IS NOT NULL THEN
            ev_time := ev_time || t; ev_from := ev_from || 'SUBMITTED'::TEXT; ev_to := ev_to || 'DUPLICATE'::TEXT;
            ev_action := ev_action || 'DUPLICATE'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
            ev_note := ev_note || ('Duplicate of #' || v_dup)::TEXT; ev_photo := ev_photo || NULL::TEXT;
        ELSE
            ev_time := ev_time || t; ev_from := ev_from || 'SUBMITTED'::TEXT; ev_to := ev_to || 'ASSIGNED'::TEXT;
            ev_action := ev_action || 'ASSIGN'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
            ev_note := ev_note || NULL::TEXT; ev_photo := ev_photo || NULL::TEXT;

            -- About 5% stall after being assigned (they show up as overdue)
            IF NOT (v_profile = 'random' AND v_outcome >= 0.08 AND v_outcome < 0.13) THEN
                t := t + (0.2 + random() * 2) * interval '1 day';
                ev_time := ev_time || t; ev_from := ev_from || 'ASSIGNED'::TEXT; ev_to := ev_to || 'IN_PROGRESS'::TEXT;
                ev_action := ev_action || 'START'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
                ev_note := ev_note || NULL::TEXT; ev_photo := ev_photo || NULL::TEXT;

                IF v_profile = 'working' THEN
                    NULL;  -- stays "In progress"
                ELSE
                    t := CASE WHEN v_profile = 'awaiting' THEN now() - (0.5 + random() * 2.5) * interval '1 day'
                              WHEN v_profile = 'reopened' THEN now() - (3 + random()) * interval '1 day'
                              ELSE t + (0.3 + random() * 1.4) * v_sla * interval '1 day' END;
                    ev_time := ev_time || t; ev_from := ev_from || 'IN_PROGRESS'::TEXT; ev_to := ev_to || 'RESOLVED'::TEXT;
                    ev_action := ev_action || 'RESOLVE'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
                    ev_note := ev_note || ('Fixed by the ' || v_dept || ' team')::TEXT;
                    ev_photo := ev_photo || '/demo/fixed.svg'::TEXT;

                    IF v_profile = 'reopened' THEN
                        ev_time := ev_time || (now() - (0.5 + random()) * interval '1 day');
                        ev_from := ev_from || 'RESOLVED'::TEXT; ev_to := ev_to || 'REOPENED'::TEXT;
                        ev_action := ev_action || 'REOPEN'::TEXT; ev_role := ev_role || 'CITIZEN'::TEXT;
                        ev_note := ev_note || 'The light still goes off at night'::TEXT; ev_photo := ev_photo || NULL::TEXT;
                    -- About 8% are reopened by the citizen, then fixed again
                    ELSIF v_profile = 'random' AND v_outcome >= 0.13 AND v_outcome < 0.21 THEN
                        t := t + (0.3 + random() * 2) * interval '1 day';
                        ev_time := ev_time || t; ev_from := ev_from || 'RESOLVED'::TEXT; ev_to := ev_to || 'REOPENED'::TEXT;
                        ev_action := ev_action || 'REOPEN'::TEXT; ev_role := ev_role || 'CITIZEN'::TEXT;
                        ev_note := ev_note || 'Still not fixed properly'::TEXT; ev_photo := ev_photo || NULL::TEXT;

                        t := t + (0.2 + random() * 1.5) * interval '1 day';
                        ev_time := ev_time || t; ev_from := ev_from || 'REOPENED'::TEXT; ev_to := ev_to || 'IN_PROGRESS'::TEXT;
                        ev_action := ev_action || 'START'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
                        ev_note := ev_note || NULL::TEXT; ev_photo := ev_photo || NULL::TEXT;

                        t := t + (0.3 + random() * 0.8) * v_sla * interval '1 day';
                        ev_time := ev_time || t; ev_from := ev_from || 'IN_PROGRESS'::TEXT; ev_to := ev_to || 'RESOLVED'::TEXT;
                        ev_action := ev_action || 'RESOLVE'::TEXT; ev_role := ev_role || 'OFFICER'::TEXT;
                        ev_note := ev_note || 'Fixed again and checked on site'::TEXT;
                        ev_photo := ev_photo || '/demo/fixed.svg'::TEXT;
                    END IF;

                    -- The citizen confirms within a few days, or the system closes it after 7 days
                    IF v_profile NOT IN ('awaiting', 'reopened') THEN
                        v_reply := 0.3 + random() * 10;
                        IF v_reply <= 7 THEN
                            ev_time := ev_time || (t + v_reply * interval '1 day');
                            ev_action := ev_action || 'CONFIRM'::TEXT; ev_role := ev_role || 'CITIZEN'::TEXT;
                            ev_note := ev_note || NULL::TEXT;
                        ELSE
                            ev_time := ev_time || (t + interval '7 days');
                            ev_action := ev_action || 'AUTO_CLOSE'::TEXT; ev_role := ev_role || 'SYSTEM'::TEXT;
                            ev_note := ev_note || 'Closed automatically: no reply from the reporter within 7 days'::TEXT;
                        END IF;
                        ev_from := ev_from || 'RESOLVED'::TEXT; ev_to := ev_to || 'CLOSED'::TEXT;
                        ev_photo := ev_photo || NULL::TEXT;
                    END IF;
                END IF;
            END IF;
        END IF;

        -- Only events that already happened count; the latest one decides the status
        n_applied := 0;
        v_assigned := NULL; v_resolved_at := NULL; v_closed_at := NULL;
        v_res_note := NULL; v_res_photo := NULL; v_reason := NULL; v_dup_of := NULL;
        FOR k IN 1 .. array_length(ev_time, 1) LOOP
            EXIT WHEN ev_time[k] > now();
            n_applied := k;
            IF ev_action[k] IN ('ASSIGN', 'START') AND p.ward = 'Z13' THEN v_assigned := v_officer; END IF;
            IF ev_action[k] = 'RESOLVE' THEN
                v_resolved_at := ev_time[k]; v_res_note := ev_note[k]; v_res_photo := ev_photo[k];
            END IF;
            IF ev_action[k] = 'REOPEN' THEN v_resolved_at := NULL; v_reason := ev_note[k]; END IF;
            IF ev_action[k] IN ('REJECT', 'DUPLICATE') THEN v_reason := ev_note[k]; v_closed_at := ev_time[k]; END IF;
            IF ev_action[k] = 'DUPLICATE' THEN v_dup_of := v_dup; END IF;
            IF ev_action[k] IN ('CONFIRM', 'AUTO_CLOSE') THEN v_closed_at := ev_time[k]; END IF;
        END LOOP;
        v_status := ev_to[n_applied];
        v_updated := ev_time[n_applied];

        INSERT INTO issue (category, description, location, address, ward_code, photo_url, status, reporter_id,
                           created_at, updated_at, assigned_officer_id, resolution_photo_url, resolution_note,
                           status_reason, duplicate_of_id, resolved_at, closed_at, is_demo)
        VALUES (
            v_cat,
            CASE v_cat
                WHEN 'POTHOLE' THEN (ARRAY['Deep pothole near the bus stop', 'Several potholes after the rain',
                    'Pothole in the middle of the road, bikes swerving', 'Broken road surface near the school gate'])[1 + floor(random() * 4)::INT]
                WHEN 'GARBAGE' THEN (ARRAY['Garbage not collected for three days', 'Overflowing bin near the market',
                    'Waste dumped on the roadside', 'Garbage being burnt near houses'])[1 + floor(random() * 4)::INT]
                WHEN 'STREETLIGHT' THEN (ARRAY['Streetlight not working for a week', 'Lamp flickers all night',
                    'Whole lane is dark after 7 pm', 'Broken light on the pole'])[1 + floor(random() * 4)::INT]
                WHEN 'DRAINAGE' THEN (ARRAY['Blocked drain, water on the road', 'Open drain cover, risky at night',
                    'Sewage overflowing at the corner', 'Drain clogged with plastic'])[1 + floor(random() * 4)::INT]
                WHEN 'WATER_LEAK' THEN (ARRAY['Pipeline leaking for two days', 'Water leaking from a valve',
                    'Burst pipe flooding the lane', 'Leak at the public tap'])[1 + floor(random() * 4)::INT]
                ELSE (ARRAY['Fallen branch blocking the footpath', 'Broken footpath tiles',
                    'Stray cattle on the main road', 'Damaged bus stop shelter'])[1 + floor(random() * 4)::INT]
            END,
            p.geom,
            (ARRAY['Near the bus stop', 'Main road', 'Market lane', 'Near the school', 'Temple road',
                   'Behind the hospital', 'Station road', 'Near the garden'])[1 + floor(random() * 8)::INT],
            p.ward,
            '/demo/' || lower(v_cat) || '.svg',
            v_status,
            v_reporter,
            v_created,
            v_updated,
            v_assigned,
            v_res_photo,
            v_res_note,
            v_reason,
            v_dup_of,
            CASE WHEN v_status IN ('RESOLVED', 'CLOSED') THEN v_resolved_at END,
            CASE WHEN v_status IN ('CLOSED', 'REJECTED', 'DUPLICATE') THEN v_closed_at END,
            TRUE)
        RETURNING id INTO v_id;

        FOR k IN 1 .. n_applied LOOP
            INSERT INTO issue_event (issue_id, from_status, to_status, action, actor_id, actor_role, note, photo_url, created_at)
            VALUES (v_id, ev_from[k], ev_to[k], ev_action[k],
                    CASE ev_role[k] WHEN 'CITIZEN' THEN v_reporter
                                    WHEN 'OFFICER' THEN CASE WHEN p.ward = 'Z13' THEN v_officer END END,
                    ev_role[k], ev_note[k], ev_photo[k], ev_time[k]);
        END LOOP;
        v_count := v_count + 1;
    END LOOP;

    -- A few unread notifications, so the bell has something to show
    INSERT INTO notification (user_id, issue_id, message, created_at)
    SELECT i.reporter_id, i.id,
           'Your report #' || i.id || ' (' || initcap(replace(lower(i.category), '_', ' ')) || ') ' ||
           CASE i.status
               WHEN 'RESOLVED'    THEN 'was marked fixed. Please confirm it.'
               WHEN 'IN_PROGRESS' THEN 'is now in progress.'
               WHEN 'ASSIGNED'    THEN 'was assigned to an officer.'
               WHEN 'CLOSED'      THEN 'is closed.'
               WHEN 'REJECTED'    THEN 'was rejected.'
               WHEN 'DUPLICATE'   THEN 'was marked as a duplicate.'
               WHEN 'REOPENED'    THEN 'was reopened.'
               ELSE 'was updated.' END,
           i.updated_at
    FROM issue i
    WHERE i.is_demo AND i.reporter_id = v_citizen AND i.status <> 'SUBMITTED'
      AND i.updated_at > now() - interval '14 days';

    INSERT INTO notification (user_id, issue_id, message, created_at)
    SELECT v_officer, i.id,
           'New report #' || i.id || ' (' || initcap(replace(lower(i.category), '_', ' ')) || ') in your zone.',
           i.created_at
    FROM issue i
    WHERE i.is_demo AND i.ward_code = 'Z13' AND i.created_at > now() - interval '2 days';

    RETURN v_count;
END;
$fn$;
