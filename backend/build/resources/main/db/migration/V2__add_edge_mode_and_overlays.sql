ALTER TABLE edges
	ADD COLUMN IF NOT EXISTS mode TEXT NOT NULL DEFAULT 'walk';

CREATE INDEX IF NOT EXISTS idx_edges_mode ON edges (mode);

CREATE TABLE IF NOT EXISTS edge_cost_overlays (
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

CREATE INDEX IF NOT EXISTS idx_edge_cost_overlays_edge ON edge_cost_overlays (edge_id);
CREATE INDEX IF NOT EXISTS idx_edge_cost_overlays_mode ON edge_cost_overlays (mode);
CREATE INDEX IF NOT EXISTS idx_edge_cost_overlays_validity ON edge_cost_overlays (valid_from, valid_to);
