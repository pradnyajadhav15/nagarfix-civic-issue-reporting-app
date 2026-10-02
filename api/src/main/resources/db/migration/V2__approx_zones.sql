-- V2: APPROXIMATE demo zones for Solapur - NOT official SMC ward boundaries.
-- City boundary source: Fallback: 7.5 km circle around Solapur centre
-- Method: 3000 random points inside the city -> k-means into 26 groups ->
--         Voronoi cells around the group centres -> clipped to the city boundary.

ALTER TABLE ward ADD COLUMN is_official BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ward ADD COLUMN source VARCHAR(200);

CREATE TABLE city_boundary (
    id       SMALLINT     PRIMARY KEY,
    name     VARCHAR(80)  NOT NULL,
    source   VARCHAR(200) NOT NULL,
    boundary geometry(MultiPolygon, 4326) NOT NULL
);

INSERT INTO city_boundary (id, name, source, boundary)
VALUES (1, 'Solapur', 'Fallback: 7.5 km circle around Solapur centre', ST_Multi(ST_Buffer(ST_SetSRID(ST_MakePoint(75.9064, 17.6599), 4326)::geography, 7500)::geometry));

WITH city AS (
    SELECT boundary AS g FROM city_boundary WHERE id = 1
),
pts AS (
    SELECT (ST_Dump(ST_GeneratePoints(g, 3000, 42))).geom AS p FROM city
),
clustered AS (
    SELECT p, ST_ClusterKMeans(p, 26) OVER () AS cid FROM pts
),
centres AS (
    SELECT ST_Centroid(ST_Collect(p)) AS c FROM clustered GROUP BY cid
),
cells AS (
    SELECT (ST_Dump(ST_VoronoiPolygons(ST_Collect(c), 0.0,
             (SELECT ST_Expand(ST_Envelope(g), 0.05) FROM city)))).geom AS cell
    FROM centres
),
zones AS (
    SELECT ST_Multi(ST_CollectionExtract(ST_Intersection(cell, (SELECT g FROM city)), 3)) AS g
    FROM cells
),
numbered AS (
    SELECT g,
           row_number() OVER (ORDER BY ST_Y(ST_PointOnSurface(g)) DESC,
                                       ST_X(ST_PointOnSurface(g))) AS n
    FROM zones
    WHERE NOT ST_IsEmpty(g)
)
INSERT INTO ward (code, name, name_mr, boundary, is_official, source)
SELECT 'Z' || lpad(n::text, 2, '0'),
       'Zone ' || n || ' (approx.)',
       U&'\0915\094D\0937\0947\0924\094D\0930 ' || n || U&' (\0905\0902\0926\093E\091C\0947)',
       g,
       FALSE,
       'Approximate demo zone (generated), not an official ward'
FROM numbered;
