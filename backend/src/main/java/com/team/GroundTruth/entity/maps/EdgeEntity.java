package com.team.GroundTruth.entity.maps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

/**
 * JPA entity representing a directed graph edge backed by a PostGIS linestring.
 */
@Entity
@Table(name = "edges")
public class EdgeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source", nullable = false)
	private Long source;

	@Column(name = "target", nullable = false)
	private Long target;

	@Column(name = "geom", nullable = false, columnDefinition = "geometry(LineString,4326)")
	private LineString geom;

	@Column(name = "length_m", nullable = false)
	private double lengthMeters;

	@Column(name = "cost_s", nullable = false)
	private double costSeconds;

	@Column(name = "mode", nullable = false)
	private String mode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
	private String attrs;

	protected EdgeEntity() {
	}

	/**
	 * Creates an edge entity with the given properties.
	 *
	 * @param source source node id
	 * @param target target node id
	 * @param geom edge geometry
	 * @param lengthMeters edge length in meters
	 * @param costSeconds edge traversal cost in seconds
	 * @param attrs edge attributes as JSON
	 */
	public EdgeEntity(Long source, Long target, LineString geom, double lengthMeters, double costSeconds, String attrs) {
		this.source = source;
		this.target = target;
		this.geom = geom;
		this.lengthMeters = lengthMeters;
		this.costSeconds = costSeconds;
		this.attrs = attrs;
	}

	/**
	 * Returns the edge id.
	 *
	 * @return edge id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Returns the source node id.
	 *
	 * @return source node id
	 */
	public Long getSource() {
		return source;
	}

	/**
	 * Returns the target node id.
	 *
	 * @return target node id
	 */
	public Long getTarget() {
		return target;
	}

	/**
	 * Returns the edge geometry.
	 *
	 * @return edge geometry
	 */
	public LineString getGeom() {
		return geom;
	}

	/**
	 * Returns the edge length in meters.
	 *
	 * @return edge length in meters
	 */
	public double getLengthMeters() {
		return lengthMeters;
	}

	/**
	 * Returns the edge traversal cost in seconds.
	 *
	 * @return traversal cost in seconds
	 */
	public double getCostSeconds() {
		return costSeconds;
	}

	/**
	 * Returns the travel mode identifier for the edge.
	 *
	 * @return travel mode identifier
	 */
	public String getMode() {
		return mode;
	}

	/**
	 * Returns the edge attributes JSON.
	 *
	 * @return edge attributes
	 */
	public String getAttrs() {
		return attrs;
	}
}
