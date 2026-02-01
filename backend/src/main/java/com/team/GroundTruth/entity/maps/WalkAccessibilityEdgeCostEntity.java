package com.team.GroundTruth.entity.maps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity representing pre-computed walk accessibility edge costs.
 * Based on sidewalk-specific hazards: Cracks, Blocked Sidewalk.
 */
@Entity
@Table(name = "walk_accessibility_edge_costs")
public class WalkAccessibilityEdgeCostEntity {

	@Id
	@Column(name = "edge_id")
	private Long edgeId;

	@Column(name = "cost_seconds", nullable = false)
	private double costSeconds;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "contributing_hazard_ids", nullable = false, columnDefinition = "uuid[]")
	private UUID[] contributingHazardIds;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected WalkAccessibilityEdgeCostEntity() {
	}

	public WalkAccessibilityEdgeCostEntity(Long edgeId, double costSeconds, UUID[] contributingHazardIds) {
		this.edgeId = edgeId;
		this.costSeconds = costSeconds;
		this.contributingHazardIds = contributingHazardIds != null ? contributingHazardIds : new UUID[0];
		this.updatedAt = OffsetDateTime.now();
	}

	public Long getEdgeId() {
		return edgeId;
	}

	public double getCostSeconds() {
		return costSeconds;
	}

	public void setCostSeconds(double costSeconds) {
		this.costSeconds = costSeconds;
		this.updatedAt = OffsetDateTime.now();
	}

	public UUID[] getContributingHazardIds() {
		return contributingHazardIds;
	}

	public void setContributingHazardIds(UUID[] contributingHazardIds) {
		this.contributingHazardIds = contributingHazardIds != null ? contributingHazardIds : new UUID[0];
		this.updatedAt = OffsetDateTime.now();
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}