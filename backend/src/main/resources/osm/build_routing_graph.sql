BEGIN;

DROP EXTENSION IF EXISTS postgis_tiger_geocoder CASCADE;

DROP TABLE IF EXISTS edge_cost_overlays;
DROP TABLE IF EXISTS edges;
DROP TABLE IF EXISTS nodes;

CREATE TABLE nodes (
	id BIGSERIAL PRIMARY KEY,
	geom GEOMETRY(Point, 4326) NOT NULL
);

CREATE INDEX idx_nodes_geom ON nodes USING GIST (geom);

CREATE TABLE edges (
	id BIGSERIAL PRIMARY KEY,
	source BIGINT NOT NULL REFERENCES nodes(id),
	target BIGINT NOT NULL REFERENCES nodes(id),
	geom GEOMETRY(LineString, 4326) NOT NULL,
	length_m DOUBLE PRECISION NOT NULL,
	cost_s DOUBLE PRECISION NOT NULL,
	mode TEXT NOT NULL,
	attrs JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_edges_source ON edges (source);
CREATE INDEX idx_edges_target ON edges (target);
CREATE INDEX idx_edges_geom ON edges USING GIST (geom);
CREATE INDEX idx_edges_mode ON edges (mode);

CREATE TABLE edge_cost_overlays (
	id BIGSERIAL PRIMARY KEY,
	edge_id BIGINT NOT NULL REFERENCES edges(id) ON DELETE CASCADE,
	mode TEXT NOT NULL,
	cost_delta_s DOUBLE PRECISION NOT NULL DEFAULT 0,
	cost_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1,
	reason TEXT,
	attrs JSONB NOT NULL DEFAULT '{}'::jsonb,
	valid_from TIMESTAMPTZ,
	valid_to TIMESTAMPTZ
);

CREATE INDEX idx_edge_cost_overlays_edge ON edge_cost_overlays (edge_id);
CREATE INDEX idx_edge_cost_overlays_mode ON edge_cost_overlays (mode);
CREATE INDEX idx_edge_cost_overlays_validity ON edge_cost_overlays (valid_from, valid_to);

CREATE TEMP TABLE osm_routing_segments AS
SELECT
	s.highway,
	s.oneway,
	ST_Transform(s.geom, 4326) AS geom,
	CASE
		WHEN s.oneway IN ('-1', 'reverse') THEN -1
		WHEN s.oneway IN ('yes', 'true', '1') THEN 1
		ELSE 0
	END AS oneway_dir,
	ST_Length(ST_Transform(s.geom, 4326)::geography) AS length_m
FROM (
	SELECT
		highway,
		oneway,
		(ST_DumpSegments(way)).geom AS geom
	FROM planet_osm_line
	WHERE highway IS NOT NULL
	  AND way IS NOT NULL
) s;

DELETE FROM osm_routing_segments WHERE length_m <= 0;

INSERT INTO nodes(geom)
SELECT DISTINCT ST_StartPoint(geom) FROM osm_routing_segments
UNION
SELECT DISTINCT ST_EndPoint(geom) FROM osm_routing_segments;

CREATE TEMP TABLE osm_node_index AS
SELECT id, ST_AsEWKB(geom) AS geom_key FROM nodes;

INSERT INTO edges (source, target, geom, length_m, cost_s, mode, attrs)
SELECT
	sn.id,
	tn.id,
	s.geom,
	s.length_m,
	s.length_m / CASE WHEN s.highway = 'steps' THEN 1.0 ELSE 1.4 END,
	'walk',
	jsonb_build_object('highway', s.highway, 'oneway', s.oneway)
FROM osm_routing_segments s
JOIN osm_node_index sn ON sn.geom_key = ST_AsEWKB(ST_StartPoint(s.geom))
JOIN osm_node_index tn ON tn.geom_key = ST_AsEWKB(ST_EndPoint(s.geom))
WHERE s.highway IN (
	'footway','pedestrian','path','living_street','residential','service','track',
	'unclassified','tertiary','secondary','primary','cycleway','steps','corridor','road'
);

INSERT INTO edges (source, target, geom, length_m, cost_s, mode, attrs)
SELECT
	tn.id,
	sn.id,
	s.geom,
	s.length_m,
	s.length_m / CASE WHEN s.highway = 'steps' THEN 1.0 ELSE 1.4 END,
	'walk',
	jsonb_build_object('highway', s.highway, 'oneway', s.oneway)
FROM osm_routing_segments s
JOIN osm_node_index sn ON sn.geom_key = ST_AsEWKB(ST_StartPoint(s.geom))
JOIN osm_node_index tn ON tn.geom_key = ST_AsEWKB(ST_EndPoint(s.geom))
WHERE s.highway IN (
	'footway','pedestrian','path','living_street','residential','service','track',
	'unclassified','tertiary','secondary','primary','cycleway','steps','corridor','road'
);

INSERT INTO edges (source, target, geom, length_m, cost_s, mode, attrs)
SELECT
	sn.id,
	tn.id,
	s.geom,
	s.length_m,
	s.length_m / CASE
		WHEN s.highway IN ('motorway','motorway_link') THEN 27.8
		WHEN s.highway IN ('trunk','trunk_link') THEN 24.6
		WHEN s.highway IN ('primary','primary_link') THEN 20.1
		WHEN s.highway IN ('secondary','secondary_link') THEN 17.9
		WHEN s.highway IN ('tertiary','tertiary_link') THEN 15.6
		WHEN s.highway IN ('residential','living_street') THEN 11.1
		WHEN s.highway IN ('service','unclassified','road') THEN 11.1
		ELSE 13.9
	END,
	'drive',
	jsonb_build_object('highway', s.highway, 'oneway', s.oneway)
FROM osm_routing_segments s
JOIN osm_node_index sn ON sn.geom_key = ST_AsEWKB(ST_StartPoint(s.geom))
JOIN osm_node_index tn ON tn.geom_key = ST_AsEWKB(ST_EndPoint(s.geom))
WHERE s.highway IN (
	'motorway','motorway_link','trunk','trunk_link','primary','primary_link',
	'secondary','secondary_link','tertiary','tertiary_link','residential',
	'unclassified','service','living_street','road'
)
  AND s.oneway_dir >= 0;

INSERT INTO edges (source, target, geom, length_m, cost_s, mode, attrs)
SELECT
	tn.id,
	sn.id,
	s.geom,
	s.length_m,
	s.length_m / CASE
		WHEN s.highway IN ('motorway','motorway_link') THEN 27.8
		WHEN s.highway IN ('trunk','trunk_link') THEN 24.6
		WHEN s.highway IN ('primary','primary_link') THEN 20.1
		WHEN s.highway IN ('secondary','secondary_link') THEN 17.9
		WHEN s.highway IN ('tertiary','tertiary_link') THEN 15.6
		WHEN s.highway IN ('residential','living_street') THEN 11.1
		WHEN s.highway IN ('service','unclassified','road') THEN 11.1
		ELSE 13.9
	END,
	'drive',
	jsonb_build_object('highway', s.highway, 'oneway', s.oneway)
FROM osm_routing_segments s
JOIN osm_node_index sn ON sn.geom_key = ST_AsEWKB(ST_StartPoint(s.geom))
JOIN osm_node_index tn ON tn.geom_key = ST_AsEWKB(ST_EndPoint(s.geom))
WHERE s.highway IN (
	'motorway','motorway_link','trunk','trunk_link','primary','primary_link',
	'secondary','secondary_link','tertiary','tertiary_link','residential',
	'unclassified','service','living_street','road'
)
  AND s.oneway_dir <= 0;

COMMIT;
