-- Materialized edge costs for walk accessibility hazards (Cracks, Blocked Sidewalk)
-- These are sidewalk-specific hazards that affect pedestrian accessibility
CREATE TABLE IF NOT EXISTS walk_accessibility_edge_costs (
    edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
    cost_seconds DOUBLE PRECISION NOT NULL,
    contributing_hazard_ids UUID[] NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for finding edges affected by a specific accessibility hazard
CREATE INDEX IF NOT EXISTS idx_walk_accessibility_edge_costs_hazards
    ON walk_accessibility_edge_costs USING GIN (contributing_hazard_ids);

-- Comment documenting the allowed hazard types for this table
COMMENT ON TABLE walk_accessibility_edge_costs IS
    'Edge costs based on walk accessibility hazards: Cracks, Blocked Sidewalk';