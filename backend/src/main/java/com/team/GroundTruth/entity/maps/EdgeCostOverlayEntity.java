package com.team.GroundTruth.entity.maps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity representing a temporary overlay that adjusts edge traversal cost.
 */
@Entity
@Table(name = "edge_cost_overlays")
public class EdgeCostOverlayEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "edge_id", nullable = false)
	private Long edgeId;

	@Column(name = "mode", nullable = false)
	private String mode;

	@Column(name = "cost_delta_s", nullable = false)
	private double costDeltaSeconds;

	@Column(name = "cost_multiplier", nullable = false)
	private double costMultiplier;

	@Column(name = "reason")
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
	private String attrs;

	@Column(name = "valid_from")
	private OffsetDateTime validFrom;

	@Column(name = "valid_to")
	private OffsetDateTime validTo;

	protected EdgeCostOverlayEntity() {
	}

	/**
	 * Returns the overlay id.
	 *
	 * @return overlay id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Returns the target edge id.
	 *
	 * @return edge id
	 */
	public Long getEdgeId() {
		return edgeId;
	}

	/**
	 * Returns the travel mode identifier.
	 *
	 * @return travel mode identifier
	 */
	public String getMode() {
		return mode;
	}

	/**
	 * Returns the cost delta in seconds.
	 *
	 * @return cost delta in seconds
	 */
	public double getCostDeltaSeconds() {
		return costDeltaSeconds;
	}

	/**
	 * Returns the cost multiplier.
	 *
	 * @return cost multiplier
	 */
	public double getCostMultiplier() {
		return costMultiplier;
	}

	/**
	 * Returns the overlay reason.
	 *
	 * @return overlay reason
	 */
	public String getReason() {
		return reason;
	}

	/**
	 * Returns the overlay attributes.
	 *
	 * @return overlay attributes
	 */
	public String getAttrs() {
		return attrs;
	}

	/**
	 * Returns the start time when the overlay is active.
	 *
	 * @return valid from timestamp
	 */
	public OffsetDateTime getValidFrom() {
		return validFrom;
	}

	/**
	 * Returns the end time when the overlay is active.
	 *
	 * @return valid to timestamp
	 */
	public OffsetDateTime getValidTo() {
		return validTo;
	}
}
