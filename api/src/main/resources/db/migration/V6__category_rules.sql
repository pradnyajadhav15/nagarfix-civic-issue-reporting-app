-- V6: which department handles each category, and its target time to fix (deadline) in days.
CREATE TABLE category_rule (
    category   VARCHAR(30)  PRIMARY KEY
               CHECK (category IN ('POTHOLE', 'GARBAGE', 'STREETLIGHT', 'DRAINAGE', 'WATER_LEAK', 'OTHER')),
    department VARCHAR(80)  NOT NULL,
    sla_days   INT          NOT NULL CHECK (sla_days BETWEEN 1 AND 90),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO category_rule (category, department, sla_days) VALUES
    ('POTHOLE',     'Roads',                   7),
    ('GARBAGE',     'Solid Waste Management',  2),
    ('STREETLIGHT', 'Electrical',              3),
    ('DRAINAGE',    'Drainage',                5),
    ('WATER_LEAK',  'Water Supply',            2),
    ('OTHER',       'General Administration', 10);
