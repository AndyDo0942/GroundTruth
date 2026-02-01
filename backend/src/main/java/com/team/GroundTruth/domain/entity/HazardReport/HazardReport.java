package com.team.GroundTruth.domain.entity.HazardReport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a user-submitted hazard report with an image.
 */
@Entity
@Table(name = "hazard_reports")
public class HazardReport {
    /**
     * Primary key for the hazard report.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Raw image bytes for the report.
     */
    @Lob
    @Column(name = "image_bytes")
    private byte[] imageBytes;

    /**
     * MIME type for the stored image bytes.
     */
    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    /**
     * Timestamp when the report was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="longitude", updatable = false)
    private Float longitude;

    @Column(name="latitude", updatable = false)
    private Float latitude;

    /**
     * Hazards detected within the report.
     */
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Hazard> hazards = new ArrayList<>();

    /**
     * Creates an empty hazard report for JPA.
     */
    public HazardReport() {}

    /**
     * Sets the creation timestamp on first persist.
     */
    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Returns the hazard report id.
     *
     * @return report id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the hazard report id.
     *
     * @param id report id
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the user who authored the report.
     *
     * @return authoring user
     */

    /**
     * Returns the image bytes associated with the report.
     *
     * @return image bytes
     */
    public byte[] getImageBytes() {
        return imageBytes;
    }

    /**
     * Sets the image bytes associated with the report.
     *
     * @param imageBytes image bytes
     */
    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    /**
     * Returns the image MIME type associated with the report.
     *
     * @return image MIME type
     */
    public String getImageContentType() {
        return imageContentType;
    }

    /**
     * Sets the image MIME type associated with the report.
     *
     * @param imageContentType image MIME type
     */
    public void setImageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
    }

    /**
     * Returns the longitude for the report.
     *
     * @return longitude
     */
    public Float getLongitude() {
        return longitude;
    }

    /**
     * Sets the longitude for the report.
     *
     * @param longitude longitude
     */
    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

    /**
     * Returns the latitude for the report.
     *
     * @return latitude
     */
    public Float getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude for the report.
     *
     * @param latitude latitude
     */
    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    /**
     * Returns the report creation timestamp.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the report creation timestamp.
     *
     * @param createdAt creation timestamp
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns hazards detected in this report.
     *
     * @return hazards list
     */
    public List<Hazard> getHazards() {
        return hazards;
    }

    /**
     * Sets hazards detected in this report.
     *
     * @param hazards hazards list
     */
    public void setHazards(List<Hazard> hazards) {
        this.hazards = hazards;
    }
}
