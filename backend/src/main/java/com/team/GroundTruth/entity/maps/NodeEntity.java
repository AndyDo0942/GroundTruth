package com.team.GroundTruth.entity.maps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

/**
 * JPA entity representing a graph node backed by a PostGIS point.
 */
@Entity
@Table(name = "nodes")
public class NodeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "geom", nullable = false, columnDefinition = "geometry(Point,4326)")
	private Point geom;

	protected NodeEntity() {
	}

	/**
	 * Creates a node entity with the given geometry.
	 *
	 * @param geom node geometry
	 */
	public NodeEntity(Point geom) {
		this.geom = geom;
	}

	/**
	 * Returns the node id.
	 *
	 * @return node id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Returns the node geometry.
	 *
	 * @return node geometry
	 */
	public Point getGeom() {
		return geom;
	}
}
