-- V1: turn on PostGIS and create the ward table (boundaries are loaded later)
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE ward (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    name_mr    VARCHAR(120),
    boundary   geometry(MultiPolygon, 4326),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ward_boundary ON ward USING GIST (boundary);
