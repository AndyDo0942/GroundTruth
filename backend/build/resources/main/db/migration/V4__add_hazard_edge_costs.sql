-- Materialized edge costs for walk mode, updated when hazards change
CREATE TABLE IF NOT EXISTS walk_edge_costs (
    edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
    cost_seconds DOUBLE PRECISION NOT NULL,
    contributing_hazard_ids BIGINT[] NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Materialized edge costs for drive mode, updated when hazards change
CREATE TABLE IF NOT EXISTS drive_edge_costs (
    edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
    cost_seconds DOUBLE PRECISION NOT NULL,
    contributing_hazard_ids BIGINT[] NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for finding edges affected by a specific hazard (for recalculation on hazard removal)
CREATE INDEX IF NOT EXISTS idx_walk_edge_costs_hazards ON walk_edge_costs USING GIN (contributing_hazard_ids);
CREATE INDEX IF NOT EXISTS idx_drive_edge_costs_hazards ON drive_edge_costs USING GIN (contributing_hazard_ids);

-- Materialized edge costs for walk mode based on statistical safety data (not hazards)
CREATE TABLE IF NOT EXISTS walk_safe_edge_costs (
    edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
    cost_seconds DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Individual safety modifiers per edge (used to compute walk_safe_edge_costs)
CREATE TABLE IF NOT EXISTS walk_safe_modifiers (
    edge_id BIGINT PRIMARY KEY REFERENCES edges(id) ON DELETE CASCADE,
    pop_density DOUBLE PRECISION,
    streetlight DOUBLE PRECISION,
    crime_in_area DOUBLE PRECISION,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);